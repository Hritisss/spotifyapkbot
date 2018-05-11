package ru.vasiliev.telegram.spotifyapkbot

import io.reactivex.Single
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import org.apache.log4j.Logger
import org.dom4j.Document
import org.dom4j.Node
import org.dom4j.io.SAXReader
import org.telegram.telegrambots.api.objects.Message
import org.telegram.telegrambots.logging.BotLogger
import ru.vasiliev.bitcoinspy.api.coindesk.ApkMirrorApiImpl
import ru.vasiliev.bitcoinspy.utils.NetworkUtils
import ru.vasiliev.telegram.spotifyapkbot.core.BaseBot
import ru.vasiliev.telegram.spotifyapkbot.core.BotConfig
import ru.vasiliev.telegram.spotifyapkbot.core.Command
import ru.vasiliev.telegram.spotifyapkbot.model.Apk
import ru.vasiliev.telegram.spotifyapkbot.model.RssFeed
import ru.vasiliev.telegram.spotifyapkbot.utils.DbUtils
import ru.vasiliev.telegram.spotifyapkbot.utils.RssDateUtils
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SpotifyApkBot : BaseBot() {

    private val api: ApkMirrorApiImpl = ApkMirrorApiImpl(NetworkUtils().getRetrofit())

    private val log: Logger = Logger.getLogger(SpotifyApkBot::class.java)

    private val db: DbUtils = DbUtils.instance

    init {
        val executor = Executors.newSingleThreadScheduledExecutor()
        executor.scheduleAtFixedRate({ checkForUpdates() }, 0, 1, TimeUnit.HOURS)
    }

    override fun handleIncomingMessage(message: Message) {
        try {
            if (message.hasText()) {
                when {
                    message.text.startsWith(Command.APK) -> printLatestApk(message.chatId)
                    message.text.startsWith(Command.SUB) -> sub(message.chatId)
                    message.text.startsWith(Command.UNSUB) -> unsub(message.chatId)
                }
            }
        } catch (e: Exception) {
            BotLogger.error(BotConfig.LOGTAG, e)
            sendMessage(createTextMessage(message.chatId, e.message))
        }
    }

    private fun checkForUpdates() {
        log.debug("checkForUpdates()")
        api.getSpotifyRssFeed().subscribeOn(Schedulers.io()).flatMap { body ->
            Single.just(SAXReader().read(body.byteStream()))
        }.flatMap { doc -> Single.just(convertXml(doc)) }.subscribeBy(onError = {
            log.error("", it)
        }, onSuccess = {
            val latest = getLatest(it.apkList)
            if (latest != null) {
                checkUpdateAvailableAndNotify(latest)
            }
        })
    }

    private fun printLatestApk(chatId: Long) {
        log.debug("printLatestApk()")
        api.getSpotifyRssFeed().subscribeOn(Schedulers.io()).flatMap { body ->
            Single.just(SAXReader().read(body.byteStream()))
        }.flatMap { doc -> Single.just(convertXml(doc)) }.subscribeBy(onError = {
            sendMessage(createTextMessage(chatId, it.message))
        }, onSuccess = {
            val latest = getLatest(it.apkList)
            if (latest != null) {
                log.debug("Latest: $latest")
                checkUpdateAvailable(latest)
                sendMessage(createTextMessage(chatId, "Последняя версия: ${latest.title}\n" +
                        "Дата релиза: ${RssDateUtils.toHumanReadable(latest.pubDate)}\n" +
                        latest.link.plus("download")))
            } else {
                sendMessage(createTextMessage(chatId, "Что-то пошло не так :["))
            }
        })
    }

    private fun sub(chatId: Long) {
        log.debug("sub($chatId)")
        if (db.addSubscriber(chatId)) {
            sendMessage(createTextMessage(chatId, "Уведомления об обновлениях включены"))
        }
    }

    private fun unsub(chatId: Long) {
        log.debug("unsub($chatId)")
        if (db.removeSubscriber(chatId)) {
            sendMessage(createTextMessage(chatId, "Уведомления об обновлениях выключены"))
        } else {
            sendMessage(createTextMessage(chatId, "Вы не подписаны на уведомления"))
        }
    }

    private fun checkUpdateAvailable(apk: Apk): Boolean {
        val latest = db.readLatestVersion()
        if (latest == null || latest.pubDate != apk.pubDate) {
            log.debug("Update available: $apk")
            db.saveLatestVersion(apk.title, apk.pubDate)
            return true
        }
        return false
    }

    private fun checkUpdateAvailableAndNotify(apk: Apk) {
        if (checkUpdateAvailable(apk)) {
            val subs = db.readAllSubscribers()
            if (subs != null && !subs.isEmpty()) {
                subs.forEach { sub ->
                    sendMessage(createTextMessage(sub.chatId, "Доступна новая версия: ${apk.title}\n" +
                            "Дата релиза: ${RssDateUtils.toHumanReadable(apk.pubDate)}\n" +
                            apk.link.plus("download")))
                }
            }
        }
    }

    private fun convertXml(doc: Document): RssFeed {
        val channel = doc.selectSingleNode("//rss//channel")
        val apkNodeList = channel.selectNodes("item")

        val lastBuildDate = channel.selectSingleNode("lastBuildDate").stringValue
        val apkList = ArrayList<Apk>()

        val iter = apkNodeList.iterator()
        while (iter.hasNext()) {
            val itemNode = iter.next() as Node
            apkList.add(Apk(itemNode.selectSingleNode("title").stringValue, itemNode.selectSingleNode("link")
                    .stringValue, itemNode.selectSingleNode("pubDate").stringValue))
        }

        return RssFeed(lastBuildDate, apkList)
    }

    private fun getLatest(apkList: List<Apk>?): Apk? {
        if (apkList != null && apkList.isNotEmpty()) {
            var last = apkList[0]
            apkList.forEach { t ->
                if (t > last) {
                    last = t
                }
            }
            return last
        }
        return null
    }
}

