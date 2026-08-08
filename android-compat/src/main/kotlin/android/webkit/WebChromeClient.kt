package android.webkit

open class WebChromeClient {
    open fun onProgressChanged(view: WebView, newProgress: Int) {}
    open fun onConsoleMessage(consoleMessage: Any?): Boolean = false
    open fun onJsAlert(view: WebView?, url: String?, message: String?, result: Any?): Boolean = false
}
