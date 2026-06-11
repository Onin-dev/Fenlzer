package com.fenl.fenlzer.data.remote

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit

object FenlzerApiFactory {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun create(
        baseUrl: String,
        tokenProvider: () -> String
    ): FenlzerApiService {
        val authInterceptor = Interceptor { chain ->
            val token = tokenProvider()
            val request = chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
            chain.proceed(request)
        }

        val okHttp = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl.ensureTrailingSlash())
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(FenlzerApiService::class.java)
    }

    private fun String.ensureTrailingSlash(): String =
        if (endsWith("/")) this else "$this/"
}
