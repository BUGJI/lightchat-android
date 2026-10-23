package com.benton.lightchat.data.local

import android.net.Uri
import coil.intercept.Interceptor
import coil.request.ImageResult

class AttachmentCacheInterceptor(
    private val cache: AttachmentCache,
) : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val data = chain.request.data
        val url = when (data) {
            is String -> data
            is Uri -> data.toString()
            else -> null
        }
        if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
            cache.cachedFile(url)?.let { file ->
                return chain.proceed(chain.request.newBuilder().data(file).build())
            }
        }
        return chain.proceed(chain.request)
    }
}
