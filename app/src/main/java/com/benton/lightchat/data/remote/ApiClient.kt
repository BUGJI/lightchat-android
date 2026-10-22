package com.benton.lightchat.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class TokenStore {
    @Volatile
    var token: String? = null
}

class ApiClient(private val tokenStore: TokenStore) {

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val token = tokenStore.token
            val request = if (!token.isNullOrBlank() && chain.request().header("Authorization") == null) {
                chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    @Volatile
    private var retrofit: Retrofit? = null

    @Volatile
    var baseUrl: String? = null
        private set

    fun setBaseUrl(url: String?) {
        synchronized(this) {
            if (url == baseUrl) return
            baseUrl = url
            retrofit = url?.let { buildRetrofit(it) }
        }
    }

    val service: ApiService?
        get() = retrofit?.create(ApiService::class.java)

    fun serviceFor(baseUrl: String): ApiService = buildRetrofit(baseUrl).create(ApiService::class.java)

    private fun buildRetrofit(baseUrl: String): Retrofit {
        val converter = json.asConverterFactory("application/json".toMediaType())
        return Retrofit.Builder()
            .baseUrl("${baseUrl.trimEnd('/')}/")
            .client(client)
            .addConverterFactory(converter)
            .build()
    }
}
