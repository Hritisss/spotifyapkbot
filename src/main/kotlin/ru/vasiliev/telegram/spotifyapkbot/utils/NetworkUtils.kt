package ru.vasiliev.bitcoinspy.utils

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import ru.vasiliev.telegram.spotifyapkbot.core.BotConfig


class NetworkUtils {
    fun getRetrofit(): Retrofit {
        return Retrofit.Builder()
                .baseUrl(BotConfig.API_BASE_URL)
                .client(getOkHttpClient())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build()
    }

    fun getOkHttpClient(): OkHttpClient {
        return OkHttpClient().newBuilder().build()
    }
}