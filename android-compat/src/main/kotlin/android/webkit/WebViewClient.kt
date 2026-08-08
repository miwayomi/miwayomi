package android.webkit

open class WebViewClient {
    open fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = false

    open fun onPageStarted(view: WebView, url: String?, favicon: Any?) {}

    open fun onPageFinished(view: WebView, url: String?) {}

    open fun onReceivedError(view: WebView, errorCode: Int, description: String?, failingUrl: String?) {}

    open fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {}
}
