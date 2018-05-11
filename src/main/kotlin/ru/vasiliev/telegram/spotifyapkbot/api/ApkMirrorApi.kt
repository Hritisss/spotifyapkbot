package ru.vasiliev.bitcoinspy.api.coindesk

import io.reactivex.Single
import okhttp3.ResponseBody
import retrofit2.http.GET

interface ApkMirrorApi {
    @GET("/apk/spotify-ltd/spotify/variant-%7B%22arches_slug%22%3A%5B%22armeabi-v7a%22%5D%7D/feed/")
    fun getSpotifyRssFeed(): Single<ResponseBody>
}