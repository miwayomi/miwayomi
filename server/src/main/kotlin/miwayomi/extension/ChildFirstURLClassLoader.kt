package miwayomi.extension

import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.net.URLClassLoader
import java.util.Enumeration

class ChildFirstURLClassLoader(
    urls: Array<URL>,
    parent: ClassLoader? = null,
) : URLClassLoader(urls, parent) {

    private val systemClassLoader: ClassLoader? = getSystemClassLoader()

    override fun loadClass(name: String?, resolve: Boolean): Class<*> {
        var c = findLoadedClass(name)

        if (c == null && systemClassLoader != null) {
            try {
                c = systemClassLoader.loadClass(name)
            } catch (_: ClassNotFoundException) {
            }
        }

        if (c == null) {
            c = try {
                findClass(name)
            } catch (_: ClassNotFoundException) {
                null
            }
        }

        if (c == null) {
            c = super.loadClass(name, false)
        }

        if (resolve) resolveClass(c)
        return c
    }

    override fun getResource(name: String): URL? {
        return systemClassLoader?.getResource(name) ?: findResource(name)
    }

    override fun getResources(name: String): Enumeration<URL> {
        val systemUrls = systemClassLoader?.getResources(name) ?: return findResources(name)
        val urls = ArrayList<URL>()
        while (systemUrls.hasMoreElements()) urls.add(systemUrls.nextElement())
        val child = findResources(name)
        while (child.hasMoreElements()) urls.add(child.nextElement())
        return java.util.Collections.enumeration(urls)
    }
}
