package ru.vasiliev.telegram.spotifyapkbot.core

import org.telegram.telegrambots.ApiContextInitializer
import org.telegram.telegrambots.TelegramBotsApi
import org.telegram.telegrambots.exceptions.TelegramApiException
import org.telegram.telegrambots.logging.BotLogger
import org.telegram.telegrambots.logging.BotsFileHandler
import ru.vasiliev.telegram.spotifyapkbot.SpotifyApkBot
import java.util.logging.ConsoleHandler
import java.util.logging.Level

fun main(args: Array<String>) {
    BotLogger.setLevel(Level.INFO)
    BotLogger.registerLogger(ConsoleHandler())

    try {
        BotLogger.registerLogger(BotsFileHandler())
    } catch (e: Throwable) {
        BotLogger.severe(BotConfig.LOGTAG, e)
    }

    try {
        ApiContextInitializer.init()
        val telegramBotsApi = TelegramBotsApi()
        try {

            telegramBotsApi.registerBot(SpotifyApkBot())
        } catch (e: TelegramApiException) {
            BotLogger.error(BotConfig.LOGTAG, e)
        }

    } catch (e: Exception) {
        BotLogger.error(BotConfig.LOGTAG, e)
    }
}