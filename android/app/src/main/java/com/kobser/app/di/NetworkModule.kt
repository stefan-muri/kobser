package com.kobser.app.di

import com.google.gson.Gson
import com.kobser.app.data.api.KobserApi
import com.kobser.app.data.repository.PreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    @Provides
    @Singleton
    @Named("auth")
    fun provideAuthInterceptor(prefs: PreferencesRepository): Interceptor {
        return Interceptor { chain ->
            val sessionId = runBlocking { prefs.sessionId.first() }
            val request = chain.request().newBuilder()
            if (sessionId != null) {
                request.addHeader("X-Session-Id", sessionId)
            }
            chain.proceed(request.build())
        }
    }

    @Provides
    @Singleton
    @Named("dynamicBaseUrl")
    fun provideDynamicBaseUrlInterceptor(prefs: PreferencesRepository): Interceptor {
        return Interceptor { chain ->
            var request = chain.request()
            val serverUrl = runBlocking { prefs.serverUrl.first() }
            if (!serverUrl.isNullOrBlank()) {
                val newUrl = request.url.newBuilder()
                    .scheme(if (serverUrl.startsWith("https")) "https" else "http")
                    .host(serverUrl.substringAfter("://").substringBefore(":").substringBefore("/"))
                    .port(if (serverUrl.contains(":")) serverUrl.substringAfterLast(":").substringBefore("/").toInt() else request.url.port)
                    .build()
                request = request.newBuilder().url(newUrl).build()
            }
            chain.proceed(request)
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        @Named("auth") authInterceptor: Interceptor,
        @Named("dynamicBaseUrl") dynamicBaseUrlInterceptor: Interceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(dynamicBaseUrlInterceptor)
            .addInterceptor(loggingInterceptor)
            .addInterceptor(authInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideKobserApi(okHttpClient: OkHttpClient, prefs: PreferencesRepository): KobserApi {
        // Since the base URL can change, we'll use a dynamic Retrofit approach or a placeholder
        // and update it in the repository. For now, we'll create it with a placeholder.
        val baseUrl = runBlocking { prefs.serverUrl.first() } ?: "http://localhost/"
        
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(KobserApi::class.java)
    }
}
