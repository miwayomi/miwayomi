package android.webkit

import android.content.Context
import android.util.AttributeSet
import android.widget.AbsoluteLayout

open class WebView(context: Context?) : AbsoluteLayout(context) {

    constructor(context: Context?, attrs: AttributeSet?) : this(context)

    private val settings = WebSettings()
    private var webViewClient: WebViewClient? = null
    private var webChromeClient: WebChromeClient? = null
    private var currentUrl: String? = null

    open fun getSettings(): WebSettings = settings

    open fun setWebViewClient(client: WebViewClient?) {
        webViewClient = client
    }

    open fun setWebChromeClient(client: WebChromeClient?) {
        webChromeClient = client
    }

    open fun loadUrl(url: String) {
        currentUrl = url
        fireFinished(url)
    }

    open fun loadUrl(url: String, extraHeaders: Map<String, String>) {
        currentUrl = url
        fireFinished(url)
    }

    open fun loadDataWithBaseURL(
        baseUrl: String?,
        data: String,
        mimeType: String?,
        encoding: String?,
        historyUrl: String?,
    ) {
        currentUrl = baseUrl
        fireFinished(baseUrl ?: "about:blank")
    }

    private fun fireFinished(url: String?) {
        val client = webViewClient
        if (client == null) return
        Thread {
            try {
                Thread.sleep(1500)
            } catch (_: InterruptedException) {
            }
            runCatching { client.onPageFinished(this, url) }
        }.start()
    }

    open fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
        resultCallback?.onReceiveValue(null)
    }

    open fun addJavascriptInterface(obj: Any?, interfaceName: String) {}

    open fun destroy() {}

    open fun stopLoading() {}

    open fun reload() {}

    open fun goBack() {}

    open fun canGoBack(): Boolean = false

    open fun getUrl(): String? = currentUrl

    open fun onPause() {}

    open fun onResume() {}

    companion object {
        @JvmStatic
        fun setWebContentsDebuggingEnabled(enabled: Boolean) {}
    }
}
