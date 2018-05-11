package ru.vasiliev.telegram.spotifyapkbot.model

data class RssFeed constructor(
        var lastBuildDate: String? = null,
        var apkList: List<Apk>? = null) {

    override fun toString(): String {
        return "RssFeed(lastBuildDate=$lastBuildDate, apkList=$apkList)"
    }
}