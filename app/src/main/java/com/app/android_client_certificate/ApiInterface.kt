package com.app.android_client_certificate

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import com.google.gson.GsonBuilder

import retrofit2.converter.scalars.ScalarsConverterFactory


interface ApiInterface {

    @GET(".")
    fun getServerResponse(): Call<String>?


    companion object {

        private val BASE_URL = "https://mobile-sec-test.portal.sgd-cloud.com/"
        private val TIMEOUT = 10

        fun create(
            sslSocketFactory: SSLSocketFactory?,
            trustManagers: X509TrustManager?
        ): ApiInterface {
            val gson = GsonBuilder()
                .setLenient()
                .create()
            val retrofit = Retrofit.Builder()
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(getOkHttpClient(sslSocketFactory, trustManagers))
                .baseUrl(BASE_URL)
                .build()
            return retrofit.create(ApiInterface::class.java)

        }

        private fun getOkHttpClient(
            sslSocketFactory: SSLSocketFactory?,
            trustManagers: X509TrustManager?
        ): OkHttpClient {
            val okHttpClientBuilder = OkHttpClient.Builder()
            okHttpClientBuilder.connectTimeout(TIMEOUT.toLong(), TimeUnit.SECONDS)
            return if (sslSocketFactory != null && trustManagers != null) {
                okHttpClientBuilder.sslSocketFactory(sslSocketFactory, trustManagers).build()
            } else {
                okHttpClientBuilder.build()
            }
        }
    }
}