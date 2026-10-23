package com.benton.lightchat.data.remote

import com.benton.lightchat.BuildConfig
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

    val httpClient: OkHttpClient = OkHttpClient.Builder()
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
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    },
                )
            }
        }
        .build()

    private val client: OkHttpClient get() = httpClient

    @Volatile
    private var retrofit: Retrofit? = null

    @Volatile
    private var cachedService: ApiService? = null

    @Volatile
    var baseUrl: String? = null
        private set

    fun setBaseUrl(url: String?) {
        synchronized(this) {
            if (url == baseUrl) return
            baseUrl = url
            val built = url?.let { buildRetrofit(it) }
            retrofit = built
            cachedService = built?.create(ApiService::class.java)
        }
    }

    val service: ApiService?
        get() = cachedService

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
