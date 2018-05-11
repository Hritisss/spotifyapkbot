package ru.vasiliev.bitcoinspy.api.coindesk

import io.reactivex.Single
import okhttp3.ResponseBody
import retrofit2.Retrofit

class ApkMirrorApiImpl(private val retrofit: Retrofit) {
    fun getSpotifyRssFeed(): Single<ResponseBody> {
        return retrofit.create(ApkMirrorApi::class.java).getSpotifyRssFeed()
    }
}