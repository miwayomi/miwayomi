package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

class IgnoreGzipInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        if (request.header("Accept-Encoding") == "gzip") {
            request = request.newBuilder().removeHeader("Accept-Encoding").build()
        }
        return chain.proceed(request)
    }
}
