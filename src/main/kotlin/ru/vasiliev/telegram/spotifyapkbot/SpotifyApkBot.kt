package ru.vasiliev.telegram.spotifyapkbot

import io.reactivex.Observable
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
                    message.text.startsWith(Command.SUB_STATUS) -> printSubscriptionStatus(message.chatId)
                    message.text.startsWith(Command.SUB_RELEASE) -> sub_release(message.chatId)
                    message.text.startsWith(Command.SUB_BETA) -> sub_beta(message.chatId)
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
            val relese = getLatestRelease(it.apkList)
            val beta = getLatestBeta(it.apkList)
            checkUpdateAvailableAndNotify(relese, beta)
        })
    }

    private fun printLatestApk(chatId: Long) {
        log.debug("printLatestApk()")
        api.getSpotifyRssFeed().subscribeOn(Schedulers.io()).flatMap { body ->
            Single.just(SAXReader().read(body.byteStream()))
        }.flatMap { doc -> Single.just(convertXml(doc)) }.subscribeBy(onError = {
            sendMessage(createTextMessage(chatId, it.message))
        }, onSuccess = {
            val release = getLatestRelease(it.apkList)
            val beta = getLatestBeta(it.apkList)
            val storedRelease = db.readLatestRelease()
            val storedBeta = db.readLatestBeta()
            checkUpdateAvailable(release, beta)
            if (release != null || storedRelease != null) {
                val latest = release ?: storedRelease
                log.debug("Latest release: $latest")
                sendMessage(createTextMessage(chatId, "Последняя *release* версия: ${latest!!.title}\n" +
                        "Дата релиза: ${RssDateUtils.toHumanReadable(latest.pubDate)}\n" +
                        "`[`__[Скачать](${latest.link.plus("download")}" + ")__`]`"))
            } else {
                sendMessage(createTextMessage(chatId, "Последняя *release* версия: нет данных"))
            }
            if (beta != null || storedBeta != null) {
                val latest = beta ?: storedBeta
                log.debug("Latest release: $latest")
                sendMessage(createTextMessage(chatId, "Последняя *beta* версия: ${latest!!.title}\n" +
                        "Дата релиза: ${RssDateUtils.toHumanReadable(latest.pubDate)}\n" +
                        "`[`__[Скачать](${latest.link.plus("download")}" + ")__`]`"))
            } else {
                sendMessage(createTextMessage(chatId, "Последняя *beta* версия: нет данных"))
            }
        })
    }

    private fun printSubscriptionStatus(chatId: Long) {
        val subs = db.readAllSubscribers()
        var subscriber: DbUtils.SubEntity? = null
        if (subs != null && !subs.isEmpty()) {
            subs.forEach { sub ->
                if (subscriber == null && sub.chatId == chatId) {
                    subscriber = sub
                }
            }
        }
        if (subscriber != null) {
            sendMessage(createTextMessage(chatId, "Статус подписки:\n" +
                    "*release* версии: _${subscriber!!.release}_\n" +
                    "*beta* версии: _${subscriber!!.beta}_"))
        } else {
            sendMessage(createTextMessage(chatId, "Вы не подписаны на уведомления"))
        }
    }

    private fun sub_release(chatId: Long) {
        log.debug("sub_release($chatId)")
        if (db.addReleaseSubscriber(chatId)) {
            sendMessage(createTextMessage(chatId, "Уведомления об обновлениях (*release*) включены"))
        }
    }

    private fun sub_beta(chatId: Long) {
        log.debug("sub_beta($chatId)")
        if (db.addBetaSubscriber(chatId)) {
            sendMessage(createTextMessage(chatId, "Уведомления об обновлениях (*beta*) включены"))
        }
    }

    private fun unsub(chatId: Long) {
        log.debug("unsub($chatId)")
        if (db.removeSubscriber(chatId)) {
            sendMessage(createTextMessage(chatId, "Все уведомления выключены"))
        } else {
            sendMessage(createTextMessage(chatId, "Вы не подписаны на уведомления"))
        }
    }

    private fun checkUpdateAvailable(release: Apk?, beta: Apk?): Pair<Boolean, Boolean> {
        var releaseUpdateAvailable = false
        var betaUpdateAvailable = false
        val storedRelease = db.readLatestRelease()
        val storedBeta = db.readLatestBeta()
        if (release != null && (storedRelease == null || release.pubDate != storedRelease.pubDate)) {
            log.debug("Release update available: $release")
            db.saveLatestRelease(release)
            releaseUpdateAvailable = true
        }
        if (beta != null && (storedBeta == null || beta.pubDate != storedBeta.pubDate)) {
            log.debug("Beta update available: $beta")
            db.saveLatestBeta(beta)
            betaUpdateAvailable = true
        }
        return Pair(releaseUpdateAvailable, betaUpdateAvailable)
    }

    private fun checkUpdateAvailableAndNotify(release: Apk?, beta: Apk?) {
        val result = checkUpdateAvailable(release, beta)
        if (result.first || result.second) {
            val subs = db.readAllSubscribers()
            subs?.forEach { sub ->
                if (sub.release && result.first) {
                    sendMessage(createTextMessage(sub.chatId, "*Release* обновление: ${release!!.title}\n" +
                            "Дата релиза: ${RssDateUtils.toHumanReadable(release.pubDate)}\n" +
                            "`[`__[Скачать](${release.link.plus("download")}" + ")__`]`"))
                } else if (sub.beta && result.second) {
                    sendMessage(createTextMessage(sub.chatId, "*Beta* обновление: ${beta!!.title}\n" +
                            "Дата релиза: ${RssDateUtils.toHumanReadable(beta.pubDate)}\n" +
                            "`[`__[Скачать](${beta.link.plus("download")}" + ")__`]`"))
                } else {
                    sendMessage(createTextMessage(sub.chatId, "Доступны обновления: \n" +
                            "*Release*: ${release!!.title}\n" +
                            "Дата релиза: ${RssDateUtils.toHumanReadable(release.pubDate)}\n" +
                            "`[`__[Скачать](${release.link.plus("download")}" + ")__`]`" +
                            "*Beta*: ${beta!!.title}\n" +
                            "Дата релиза: ${RssDateUtils.toHumanReadable(beta.pubDate)}\n" +
                            "`[`__[Скачать](${beta.link.plus("download")}" + ")__`]`"))
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
            apkList.add(Apk(itemNode.selectSingleNode("title").stringValue,
                    itemNode.selectSingleNode("pubDate").stringValue,
                    itemNode.selectSingleNode("link").stringValue))
        }

        return RssFeed(lastBuildDate, apkList)
    }

    private fun getLatestRelease(apkList: List<Apk>?): Apk? {
        var release: Apk? = null
        apkList?.forEach { t: Apk ->
            if (!t.title!!.contains("beta")) {
                if (release == null) {
                    release = t
                } else if (t > release!!) {
                    release = t
                }
            }
        }
        return release
    }

    private fun getLatestBeta(apkList: List<Apk>?): Apk? {
        var beta: Apk? = null
        apkList?.forEach { t ->
            if (t.title!!.contains("beta")) {
                if (beta == null) {
                    beta = t
                } else if (t > beta!!) {
                    beta = t
                }
            }
        }
        return beta
    }
}

