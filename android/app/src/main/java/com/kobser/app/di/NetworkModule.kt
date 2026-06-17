package com.kobser.app.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.kobser.app.BuildConfig
import com.kobser.app.data.api.KobserApi
import com.kobser.app.data.api.SearchResponse
import com.kobser.app.data.api.SearchResponseDeserializer
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
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder()
        .registerTypeAdapter(SearchResponse::class.java, SearchResponseDeserializer())
        .create()

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        }
    }

    @Provides
    @Singleton
    @Named("auth")
    fun provideAuthInterceptor(prefs: PreferencesRepository): Interceptor {
        return Interceptor { chain ->
            val sessionId = prefs.cachedSessionId
            val request = chain.request().newBuilder()
            if (sessionId.isNotEmpty()) {
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
            val serverUrl = prefs.cachedServerUrl
            if (serverUrl.isNotBlank()) {
                try {
                    val normalized = if (serverUrl.contains("://")) serverUrl else "http://$serverUrl"
                    val base = okhttp3.HttpUrl.get(normalized)
                    val newUrl = request.url.newBuilder()
                        .scheme(base.scheme())
                        .host(base.host())
                        .port(base.port())
                        .build()
                    request = request.newBuilder().url(newUrl).build()
                } catch (e: Exception) {
                    // Fallback if parsing fails
                }
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
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideKobserApi(okHttpClient: OkHttpClient, gson: Gson): KobserApi {
        // Use a stable base URL or a placeholder since the interceptor handles the actual routing
        val initialUrl = "http://placeholder/"

        return Retrofit.Builder()
            .baseUrl(initialUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(KobserApi::class.java)
    }
}
