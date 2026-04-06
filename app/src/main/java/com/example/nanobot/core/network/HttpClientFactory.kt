package com.example.nanobot.core.network

import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Singleton
class HttpClientFactory @Inject constructor() {
    private val baseClient: OkHttpClient = OkHttpClient.Builder().build()

    fun llmClient(configure: OkHttpClient.Builder.() -> Unit = {}): OkHttpClient {
        return baseClient.newBuilder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .apply(configure)
            .build()
    }

    fun audioClient(configure: OkHttpClient.Builder.() -> Unit = {}): OkHttpClient {
        return baseClient.newBuilder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
            .apply(configure)
            .build()
    }
}
