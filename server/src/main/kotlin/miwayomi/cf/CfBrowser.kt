package miwayomi.cf

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import java.util.concurrent.TimeUnit

class CfBrowser(
    private val chromePath: String?,
    private val dataDir: File,
    private val debugPort: Int = 9222,
) {

    data class CdpCookie(
        val name: String,
        val value: String,
        val domain: String,
        val path: String,
        val expires: Long,
        val httpOnly: Boolean,
        val secure: Boolean,
    )

    private val http = HttpClient.newHttpClient()
    private var process: Process? = null
    private val cdp = CdpClient(debugPort)

    val resolvedUserAgent: String =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36"

    private fun resolveChrome(): String {
        chromePath?.takeIf { File(it).canExecute() }?.let { return it }
        System.getenv("CHROME_PATH")?.takeIf { File(it).canExecute() }?.let { return it }
        val candidates = listOf(

            "/home/asking/Escritorio/miwayomi/flaresolverr/_internal/chrome/chrome",
            "/tmp/flaresolverr/_internal/chrome/chrome",
            "/usr/bin/google-chrome", "/usr/bin/google-chrome-stable",
            "/usr/bin/chromium", "/usr/bin/chromium-browser",
        )
        return candidates.firstOrNull { File(it).canExecute() }
            ?: error("No se encontró Chrome/Chromium. Usa --chrome <ruta> o CHROME_PATH.")
    }

    @Synchronized
    fun start() {
        if (process?.isAlive == true) return
        val exe = resolveChrome()
        val profile = File(dataDir, "cf-chrome").apply { mkdirs() }
        val logFile = File(dataDir, "cf-chrome.log")
        val cmd = listOf(
            exe,
            "--headless=new",
            "--remote-debugging-port=$debugPort",
            "--no-sandbox", "--disable-gpu", "--disable-dev-shm-usage", "--no-zygote",
            "--disable-search-engine-choice-screen",
            "--disable-blink-features=AutomationControlled",
            "--user-agent=$resolvedUserAgent",
            "--user-data-dir=${profile.absolutePath}",
            "--window-size=1280,900",
            "about:blank",
        )
        val pb = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
        process = pb.start()

        val versionUrl = "http://127.0.0.1:$debugPort/json/version"
        var ok = false
        repeat(50) {
            try {
                val resp = http.send(
                    HttpRequest.newBuilder(URI(versionUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
                if (resp.statusCode() == 200) { ok = true; return@repeat }
            } catch (_: Exception) {

            }
            Thread.sleep(200)
        }
        if (!ok) {
            process?.destroyForcibly()
            process = null
            throw RuntimeException("Chrome no arrancó (¿puerto $debugPort ocupado?). Revisa ${logFile.absolutePath}")
        }
    }

    @Synchronized
    fun stop() {
        cdp.close()
        process?.let {
            runCatching { it.destroy() }
            runCatching { it.waitFor(3, TimeUnit.SECONDS) }
            if (it.isAlive) it.destroyForcibly()
        }
        process = null
    }

    fun open(url: String) {
        cdp.command("Page.navigate", buildJsonObject { put("url", url) })
        Thread.sleep(1200)
    }

    fun setUserAgent(ua: String?) {
        if (ua.isNullOrBlank()) return
        cdp.command("Network.setUserAgentOverride", buildJsonObject { put("userAgent", ua) })
    }

    fun screenshot(): ByteArray {
        val res = cdp.command("Page.captureScreenshot", buildJsonObject { put("format", "png") })
        val data = res["result"]?.jsonObject?.get("data")?.jsonPrimitive?.contentOrNull
            ?: throw RuntimeException("No se pudo capturar pantalla")
        return Base64.getDecoder().decode(data)
    }

    fun click(x: Int, y: Int) {
        cdp.command("Input.dispatchMouseEvent", buildJsonObject {
            put("type", "mouseMoved"); put("x", x); put("y", y)
        })
        cdp.command("Input.dispatchMouseEvent", buildJsonObject {
            put("type", "mousePressed"); put("x", x); put("y", y); put("button", "left"); put("clickCount", 1)
        })
        cdp.command("Input.dispatchMouseEvent", buildJsonObject {
            put("type", "mouseReleased"); put("x", x); put("y", y); put("button", "left"); put("clickCount", 1)
        })
    }

    fun key(key: String) {
        val k = key.trim().lowercase()
        val (keyName, code, vk) = when (k) {
            "tab" -> Triple("Tab", "Tab", 9)
            "enter", "return" -> Triple("Enter", "Enter", 13)
            " ", "space" -> Triple(" ", "Space", 32)
            "arrowdown", "down" -> Triple("ArrowDown", "ArrowDown", 40)
            "arrowup", "up" -> Triple("ArrowUp", "ArrowUp", 38)
            else -> return
        }
        fun ev(type: String) = cdp.command("Input.dispatchKeyEvent", buildJsonObject {
            put("type", type)
            put("key", keyName)
            put("code", code)
            put("windowsVirtualKeyCode", vk)
            put("nativeVirtualKeyCode", vk)
        })
        ev("keyDown")
        ev("keyUp")
    }

    fun currentUrl(): String {
        val res = cdp.command("Runtime.evaluate", buildJsonObject {
            put("expression", "location.href")
            put("returnByValue", true)
        })
        return res["result"]?.jsonObject?.get("result")?.jsonObject?.get("value")?.jsonPrimitive?.contentOrNull ?: ""
    }

    fun cookies(): List<CdpCookie> {
        val res = cdp.command("Network.getCookies")
        return res["result"]?.jsonObject?.get("cookies")?.jsonArray?.mapNotNull { c ->
            val o = c.jsonObject
            val domain = o["domain"]?.jsonPrimitive?.contentOrNull?.trimStart('.') ?: return@mapNotNull null
            CdpCookie(
                name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                value = o["value"]?.jsonPrimitive?.contentOrNull ?: "",
                domain = domain,
                path = o["path"]?.jsonPrimitive?.contentOrNull ?: "/",
                expires = o["expires"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toLong() ?: -1L,
                httpOnly = o["httpOnly"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
                secure = o["secure"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
            )
        } ?: emptyList()
    }
}
