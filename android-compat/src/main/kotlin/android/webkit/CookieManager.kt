package android.webkit

class CookieManager private constructor() {

    fun setAcceptCookie(accept: Boolean) {}
    fun setAcceptThirdPartyCookies(webView: WebView?, accept: Boolean) {}
    fun setCookie(url: String?, value: String) {}
    fun getCookie(url: String?): String? = null
    fun removeAllCookie() {}
    fun removeSessionCookie() {}
    fun flush() {}
    fun acceptCookie(): Boolean = false

    companion object {
        @Volatile
        private var instance: CookieManager? = null

        @JvmStatic
        fun getInstance(): CookieManager {
            val existing = instance
            if (existing != null) return existing
            synchronized(this) {
                return instance ?: CookieManager().also { instance = it }
            }
        }
    }
}
