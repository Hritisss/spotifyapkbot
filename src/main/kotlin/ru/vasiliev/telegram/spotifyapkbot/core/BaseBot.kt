package ru.vasiliev.telegram.spotifyapkbot.core

import org.telegram.telegrambots.api.methods.send.SendMessage
import org.telegram.telegrambots.api.objects.Message
import org.telegram.telegrambots.api.objects.Update
import org.telegram.telegrambots.bots.TelegramLongPollingBot


abstract class BaseBot : TelegramLongPollingBot() {

    override fun getBotToken(): String {
        return BotConfig.API_TOKEN
    }

    override fun getBotUsername(): String {
        return BotConfig.BOT_NAME
    }

    override fun onUpdateReceived(update: Update?) {
        if (update?.hasMessage()!!) {
            val message = update.message
            if (message.hasText() || message.hasLocation()) {
                handleIncomingMessage(message)
            }
        }
    }

    fun createTextMessage(chatId: Long, text: String?): SendMessage {
        val message = SendMessage()
        message.enableMarkdown(true)
        message.disableWebPagePreview()
        message.setChatId(chatId)
        message.text = text
        return message
    }

    abstract fun handleIncomingMessage(message: Message)
}

