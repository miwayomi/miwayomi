package miwayomi.extension

import android.compat.CompatRuntime
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.SourceFactory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import miwayomi.source.AnimeSourceManager
import miwayomi.source.MangaSourceManager
import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile

@Serializable
data class SourceJarMeta(
    val pkgName: String,
    val name: String,
    val versionName: String = "0",
    val versionCode: Long = 0,
    val isNsfw: Boolean = false,
    val isAnime: Boolean = false,
    val sourceClasses: List<String> = emptyList(),
    val factoryClass: String? = null,
)

class ExtensionManager(
    private val mangaSourceManager: MangaSourceManager,
    private val animeSourceManager: AnimeSourceManager,
) {

    data class LoadedExtension(
        val meta: ExtensionMeta,
        val apk: File,
        val jar: File,
        val classLoader: URLClassLoader,
        val manga: Int,
        val anime: Int,
        val sources: List<Any> = emptyList(),
    )

    private val extensions = ConcurrentHashMap<String, LoadedExtension>()

    private val sourceOwner = ConcurrentHashMap<Long, String>()

    private companion object {
        const val MARKER = "META-INF/miwayomi-extension.json"
    }

    val loaded: List<LoadedExtension>
        get() = extensions.values.toList()

    fun pkgOf(sourceId: Long): String? = sourceOwner[sourceId]

    fun loadAll(): Int {
        val dir = CompatRuntime.baseDir.resolve("extensions")
        if (!dir.exists()) {
            println("[miwayomi] $dir does not exist, creating...")
            dir.mkdirs()
            return 0
        }
        val apks = dir.listFiles { f -> f.isFile && f.extension.equals("apk", ignoreCase = true) }
            ?.sortedBy { it.name }
            .orEmpty()

        var ok = 0
        apks.forEach { apk ->
            try {
                load(apk)
                ok++
            } catch (e: Throwable) {
                System.err.println("[miwayomi] Error loading $apk: ${e}")
                e.printStackTrace()
            }
        }

        // Extensions compiled from source (no APK): jars with a marker
        val jars = dir.listFiles { f ->
            f.isFile && f.extension.equals("jar", ignoreCase = true) && hasSourceJarMarker(f)
        }?.sortedBy { it.name }.orEmpty()
        jars.forEach { jar ->
            try {
                loadSourceJar(jar)
                ok++
            } catch (e: Throwable) {
                System.err.println("[miwayomi] Error loading source jar $jar: ${e}")
                e.printStackTrace()
            }
        }
        return ok
    }

    private fun hasSourceJarMarker(jar: File): Boolean = try {
        JarFile(jar).use { it.getJarEntry(MARKER) != null }
    } catch (e: Exception) {
        false
    }

    fun readSourceJarMeta(jar: File): SourceJarMeta? = try {
        JarFile(jar).use { jf ->
            val entry = jf.getJarEntry(MARKER) ?: return null
            val text = jf.getInputStream(entry).bufferedReader().use { it.readText() }
            Json { ignoreUnknownKeys = true }.decodeFromString<SourceJarMeta>(text)
        }
    } catch (e: Exception) {
        System.err.println("[miwayomi] Invalid marker in $jar: $e")
        null
    }

    /**
     * Loads an extension compiled from source (Kotlin -> JVM), without dex2jar.
     * The jar must contain META-INF/miwayomi-extension.json with the metadata.
     */
    fun loadSourceJar(jar: File): LoadedExtension {
        val meta = readSourceJarMeta(jar)
            ?: throw IllegalStateException("No miwayomi marker in $jar")
        val extMeta = ExtensionMeta(
            pkgName = meta.pkgName,
            versionName = meta.versionName,
            versionCode = meta.versionCode,
            isNsfw = meta.isNsfw,
            isAnime = meta.isAnime,
            sourceClasses = meta.sourceClasses,
            factoryClass = meta.factoryClass,
            name = meta.name,
        )

        val classLoader = ChildFirstURLClassLoader(
            arrayOf(jar.toURI().toURL()),
            ExtensionManager::class.java.classLoader,
        )

        val instances = mutableListOf<Any>()
        val classesToLoad = meta.sourceClasses + listOfNotNull(meta.factoryClass)
        for (className in classesToLoad) {
            try {
                val clazz = Class.forName(className, false, classLoader)
                val instance = clazz.getDeclaredConstructor().newInstance()
                when (instance) {
                    is SourceFactory -> instances.addAll(instance.createSources())
                    is AnimeSourceFactory -> instances.addAll(instance.createSources())
                    else -> instances.add(instance)
                }
            } catch (e: Throwable) {
                System.err.println("[miwayomi] Error instantiating $className in $jar: ${e}")
                e.printStackTrace(System.err)
            }
        }

        val loaded = registerInstances(extMeta, apk = jar, jar = jar, classLoader = classLoader, instances = instances)
        extensions[extMeta.pkgName] = loaded
        println("[miwayomi] Extension (source) loaded: ${extMeta.name} (${extMeta.pkgName}) - manga: ${loaded.manga}, anime: ${loaded.anime}")
        return loaded
    }

    fun load(apk: File): LoadedExtension {
        val meta = PackageTools.parseMeta(apk)
            ?: throw IllegalStateException("Could not read manifest of $apk")

        val jar = File(apk.parentFile, apk.nameWithoutExtension + ".jar")
        if (!jar.exists() || jar.lastModified() < apk.lastModified()) {
            println("[miwayomi] converting ${apk.name} (dex -> jar)...")
            PackageTools.dex2jar(apk, jar)
        }

        // dex2jar output (and repo-published jars) can contain invalid
        // `invokespecial <init>` owners (e.g. `new ArrayList; ...; invokespecial
        // Filter$Select.<init>`), which throws VerifyError when the source builds
        // its filters during search. Apply the bytecode fix to every jar that
        // hasn't been fixed yet (marked via META-INF/miwayomi-jarfixed) so the
        // corruption never reaches the classloader.
        if (!JarFixer.isFixed(jar)) {
            println("[miwayomi] fixing bytecode of ${jar.name}...")
            JarFixer.fixStackmapFrames(jar)
        }

        val classLoader = ChildFirstURLClassLoader(
            arrayOf(jar.toURI().toURL()),
            ExtensionManager::class.java.classLoader,
        )

        val instances = mutableListOf<Any>()

        for (className in meta.sourceClasses) {
            val fqcn = PackageTools.resolveClassName(className, meta.pkgName)
            try {
                val clazz = Class.forName(fqcn, false, classLoader)
                val instance = clazz.getDeclaredConstructor().newInstance()

                when (instance) {
                    is SourceFactory -> instances.addAll(instance.createSources())
                    is AnimeSourceFactory -> instances.addAll(instance.createSources())
                    else -> instances.add(instance)
                }
            } catch (e: Throwable) {
                System.err.println("[miwayomi] Error instantiating $fqcn: ${e}")

                var cause = e
                while (cause is java.lang.reflect.InvocationTargetException && cause.cause != null) {
                    cause = cause.cause!!
                    System.err.println("[miwayomi]   cause: ${cause}")
                }
                e.printStackTrace(System.err)
            }
        }

        if (instances.isEmpty() && meta.factoryClass != null) {
            val fqcn = PackageTools.resolveClassName(meta.factoryClass, meta.pkgName)
            try {
                val instance = Class.forName(fqcn, false, classLoader)
                    .getDeclaredConstructor()
                    .newInstance()
                when (instance) {
                    is SourceFactory -> instances.addAll(instance.createSources())
                    is AnimeSourceFactory -> instances.addAll(instance.createSources())
                    else -> instances.add(instance)
                }
            } catch (e: Throwable) {
                System.err.println("[miwayomi] Error in factory $fqcn: ${e}")
            }
        }

        val loaded = registerInstances(meta, apk, jar, classLoader, instances)
        extensions[meta.pkgName] = loaded
        println("[miwayomi] Extension loaded: ${meta.name} (${meta.pkgName}) - manga: ${loaded.manga}, anime: ${loaded.anime}")
        return loaded
    }

    private fun registerInstances(
        meta: ExtensionMeta,
        apk: File,
        jar: File,
        classLoader: URLClassLoader,
        instances: List<Any>,
    ): LoadedExtension {
        var manga = 0
        var anime = 0
        val registered = mutableListOf<Any>()
        for (instance in instances) {
            when (instance) {
                is MangaSource -> {
                    mangaSourceManager.register(instance)
                    registered.add(instance)
                    sourceOwner[instance.id] = meta.pkgName
                    manga++
                }
                is AnimeSource -> {
                    animeSourceManager.register(instance)
                    registered.add(instance)
                    sourceOwner[instance.id] = meta.pkgName
                    anime++
                }
            }
        }
        return LoadedExtension(meta, apk, jar, classLoader, manga, anime, registered)
    }

    fun uninstall(pkg: String): LoadedExtension? {
        val ext = extensions.remove(pkg) ?: return null
        runCatching {
            for (s in ext.sources) {
                when (s) {
                    is MangaSource -> {
                        mangaSourceManager.unregister(s)
                        sourceOwner.remove(s.id)
                    }
                    is AnimeSource -> {
                        animeSourceManager.unregister(s)
                        sourceOwner.remove(s.id)
                    }
                }
            }
        }
        runCatching { ext.classLoader.close() }
        runCatching { ext.apk.delete() }
        runCatching { ext.jar.delete() }
        println("[miwayomi] Extension uninstalled: ${ext.meta.name} (${ext.meta.pkgName})")
        return ext
    }
}
