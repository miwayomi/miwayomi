package android.webkit

class WebSettings {

    fun setJavaScriptEnabled(flag: Boolean) {}
    fun setJavaScriptCanOpenWindowsAutomatically(flag: Boolean) {}
    fun setDomStorageEnabled(flag: Boolean) {}
    fun setDatabaseEnabled(flag: Boolean) {}
    fun setUserAgentString(ua: String?) {}
    fun getUserAgentString(): String? = null
    fun setLoadWithOverviewMode(flag: Boolean) {}
    fun setUseWideViewPort(flag: Boolean) {}
    fun setLoadsImagesAutomatically(flag: Boolean) {}
    fun setMediaPlaybackRequiresUserGesture(flag: Boolean) {}
    fun setCacheMode(mode: Int) {}
    fun setMixedContentMode(mode: Int) {}
    fun setAllowFileAccess(flag: Boolean) {}
    fun setAllowContentAccess(flag: Boolean) {}
    fun setAllowFileAccessFromFileURLs(flag: Boolean) {}
    fun setAllowUniversalAccessFromFileURLs(flag: Boolean) {}
    fun setBlockNetworkLoads(flag: Boolean) {}
    fun setBuiltInZoomControls(flag: Boolean) {}
    fun setDisplayZoomControls(flag: Boolean) {}
    fun setSupportZoom(flag: Boolean) {}
    fun setTextZoom(zoom: Int) {}
    fun setDefaultTextEncodingName(encoding: String) {}
    fun setSupportMultipleWindows(flag: Boolean) {}
    fun setJavaScriptCanOpenWindowsAutomatically() {}

    companion object {
        const val LOAD_DEFAULT = -1
        const val LOAD_NORMAL = 0
        const val LOAD_CACHE_ELSE_NETWORK = 1
        const val LOAD_NO_CACHE = 2
        const val LOAD_CACHE_ONLY = 3
        const val MIXED_CONTENT_NEVER_ALLOW = 0
        const val MIXED_CONTENT_ALWAYS_ALLOW = 1
        const val MIXED_CONTENT_COMPATIBILITY_MODE = 2
    }
}
