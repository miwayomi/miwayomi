package miwayomi.extension

import android.compat.CompatRuntime
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.SourceFactory
import miwayomi.source.AnimeSourceManager
import miwayomi.source.MangaSourceManager
import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.ConcurrentHashMap

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

    val loaded: List<LoadedExtension>
        get() = extensions.values.toList()

    fun pkgOf(sourceId: Long): String? = sourceOwner[sourceId]

    fun loadAll(): Int {
        val dir = CompatRuntime.baseDir.resolve("extensions")
        if (!dir.exists()) {
            println("[miwayomi] No existe $dir, creando...")
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
                System.err.println("[miwayomi] Error cargando $apk: ${e}")
                e.printStackTrace()
            }
        }
        return ok
    }

    fun load(apk: File): LoadedExtension {
        val meta = PackageTools.parseMeta(apk)
            ?: throw IllegalStateException("No se pudo leer el manifest de $apk")

        val jar = File(apk.parentFile, apk.nameWithoutExtension + ".jar")
        if (!jar.exists() || jar.lastModified() < apk.lastModified()) {
            println("[miwayomi] convirtiendo ${apk.name} (dex -> jar)...")
            PackageTools.dex2jar(apk, jar)

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
                System.err.println("[miwayomi] Error instanciando $fqcn: ${e}")

                var cause = e
                while (cause is java.lang.reflect.InvocationTargetException && cause.cause != null) {
                    cause = cause.cause!!
                    System.err.println("[miwayomi]   causa: ${cause}")
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
                System.err.println("[miwayomi] Error en factory $fqcn: ${e}")
            }
        }

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

        val loaded = LoadedExtension(meta, apk, jar, classLoader, manga, anime, registered)
        extensions[meta.pkgName] = loaded
        println("[miwayomi] Extensión cargada: ${meta.name} (${meta.pkgName}) - manga: $manga, anime: $anime")
        return loaded
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
        println("[miwayomi] Extensión desinstalada: ${ext.meta.name} (${ext.meta.pkgName})")
        return ext
    }
}
