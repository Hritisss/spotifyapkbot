package ru.vasiliev.telegram.spotifyapkbot.utils;

import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.Hours
import org.joda.time.Minutes
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.DateTimeFormatterBuilder
import org.joda.time.format.DateTimeParser
import java.util.*


class RssDateUtils {

    companion object {
        private val DATE_FORMATTER_REGULAR: DateTimeFormatter = DateTimeFormat.forPattern("dd MMM HH:mm")

        private val DATE_FORMATTER_HHMM: DateTimeFormatter = DateTimeFormat.forPattern("HH:mm")

        private val GMT_PARSER: DateTimeParser = DateTimeFormat.forPattern("ZZZ").parser

        private val OFFSET_PARSER: DateTimeParser = DateTimeFormat.forPattern("Z").parser

        private val FORMATTER: DateTimeFormatter = DateTimeFormatterBuilder()
                .appendPattern("EEE, dd MMM yyyy HH:mm:ss ") // Common pattern
                .appendOptional(GMT_PARSER)    // Optional parser for GMT
                .appendOptional(OFFSET_PARSER) // Optional parser for +0000
                .toFormatter().withOffsetParsed().withLocale(Locale.US)

        private const val LATEST_HOURS_INTERVAL: Int = 6

        private const val PM_HACK_MARKER: Int = 1

        fun parseToLocalTimeZone(date: String?): DateTime {
            return FORMATTER.parseDateTime(date).withZone(DateTimeZone.getDefault())
        }

        fun toHumanReadable(date: String?): String? {
            try {
                val dateTime = parseToLocalTimeZone(date)

                if (isToday(dateTime)) {
                    return String.format(Locale.forLanguageTag("ru"), "Сегодня, %s",
                            DATE_FORMATTER_HHMM.print(dateTime))
                }
                return if (isYesterday(dateTime)) {
                    String.format(Locale.forLanguageTag("ru"), "Вчера, %s",
                            DATE_FORMATTER_HHMM.print(dateTime))
                } else DATE_FORMATTER_REGULAR.withLocale(Locale.forLanguageTag("ru")).print(dateTime)

            } catch (t: Throwable) {
                return date
            }
        }

        fun isToday(time: DateTime): Boolean {
            return DateTime.now().compareTo(time) == 0
        }

        fun isTomorrow(time: DateTime): Boolean {
            return DateTime.now().plusDays(1).compareTo(DateTime(time)) == 0
        }

        fun isYesterday(time: DateTime): Boolean {
            return DateTime.now().minusDays(1).compareTo(DateTime(time)) == 0
        }

        fun withinLatestHours(targetDate: String): Boolean {
            try {
                return withinLatestHours(parseToLocalTimeZone(targetDate))
            } catch (t: Throwable) {
                return false
            }

        }

        fun withinLatestHours(targetDate: DateTime): Boolean {
            return withinHours(targetDate) <= LATEST_HOURS_INTERVAL
        }

        fun withinHours(targetDate: DateTime): Int {
            return Math.abs(Hours.hoursBetween(DateTime.now(), targetDate).hours)
        }

        fun withinMinutes(targetDate: DateTime): Int {
            return Math.abs(Minutes.minutesBetween(DateTime.now(), targetDate).minutes)
        }
    }
}