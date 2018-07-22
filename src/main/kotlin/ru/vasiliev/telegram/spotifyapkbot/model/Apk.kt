package ru.vasiliev.telegram.spotifyapkbot.model;

import ru.vasiliev.telegram.spotifyapkbot.utils.RssDateUtils

import java.io.Serializable;

data class Apk constructor(
        var title: String?,
        var pubDate: String?,
        var link: String?) : Serializable, Comparable<Apk> {

    override fun compareTo(other: Apk): Int {
        try {
            return RssDateUtils.parseToLocalTimeZone(pubDate)
                    .compareTo(RssDateUtils.parseToLocalTimeZone(other.pubDate))
        } catch (t: Throwable) {
        }
        return 0
    }

    override fun toString(): String {
        return "Apk(title=$title, pubDate=$pubDate, link=$link)"
    }
}