package com.fenl.fenlzer.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object FenlzerApiFactory {
    @OptIn(ExperimentalSerializationApi::class)
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @OptIn(ExperimentalSerializationApi::class)
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
            .baseUrl(normalizeBaseUrl(baseUrl))
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(FenlzerApiService::class.java)
    }

    fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        val withoutVersion = if (trimmed.endsWith("/v1")) {
            trimmed.removeSuffix("/v1")
        } else {
            trimmed
        }
        return "$withoutVersion/"
    }
}
