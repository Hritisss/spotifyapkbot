package ru.vasiliev.telegram.spotifyapkbot.utils

import org.apache.log4j.Logger
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.SqlLogger
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.statements.expandArgs
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import ru.vasiliev.telegram.spotifyapkbot.model.Apk
import java.sql.Connection


/**
 * DAO singleton
 */
class DbUtils private constructor(private val log: Logger = Logger.getLogger(DbUtils::class.java)) {
    init {
        Database.connect("jdbc:sqlite:spotifyapk.db", driver = "org.sqlite.JDBC")
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE

        transaction {
            logger.addLogger(SqlDebugLogger)
            create(ApkRelease)
            create(ApkBeta)
            create(Subscribers)
        }
    }

    private object Holder {
        val INSTANCE = DbUtils()
    }

    companion object {
        val instance: DbUtils by lazy { Holder.INSTANCE }
        const val LATEST_VERSION_ID = 1
    }

    object ApkRelease : IntIdTable() {
        val releaseId = integer("release_id").uniqueIndex()
        val title = varchar("title", 128)
        val pubDate = varchar("pub_date", 128)
        val link = varchar("link", 1024)
    }

    object ApkBeta : IntIdTable() {
        val betaId = integer("release_id").uniqueIndex()
        val title = varchar("title", 128)
        val pubDate = varchar("pub_date", 128)
        val link = varchar("link", 1024)
    }

    object Subscribers : IntIdTable() {
        val chatId = long("chat_id")
        val beta = bool("beta")
        val release = bool("release")
    }

    class ApkReleaseEntity(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<ApkReleaseEntity>(ApkRelease)

        var releaseId by ApkRelease.releaseId
        var title by ApkRelease.title
        var pubDate by ApkRelease.pubDate
        var link by ApkRelease.link
    }

    class ApkBetaEntity(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<ApkBetaEntity>(ApkBeta)

        var betaId by ApkBeta.betaId
        var title by ApkBeta.title
        var pubDate by ApkBeta.pubDate
        var link by ApkBeta.link
    }

    class SubEntity(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<SubEntity>(Subscribers)

        var chatId by Subscribers.chatId
        var beta by Subscribers.beta
        var release by Subscribers.release
    }

    object SqlDebugLogger : SqlLogger {
        private val log: Logger = Logger.getLogger(SqlDebugLogger::class.java)
        override fun log(context: StatementContext, transaction: Transaction) {
            log.debug("SQL: ${context.expandArgs(transaction)}")
        }
    }

    fun saveLatestRelease(release: Apk?) {
        if (release != null) {
            transaction {
                // Check if index already exist
                val latest = ApkReleaseEntity.find { ApkRelease.releaseId.eq(LATEST_VERSION_ID) }
                if (latest.empty()) {
                    // Create if not exist
                    ApkReleaseEntity.new {
                        this.releaseId = LATEST_VERSION_ID
                        this.title = release.title!!
                        this.pubDate = release.pubDate!!
                        this.link = release.link!!
                    }
                } else { // Update
                    latest.forEach { entity ->
                        run {
                            entity.title = release.title!!
                            entity.pubDate = release.pubDate!!
                            entity.link = release.link!!
                        }
                    }
                }
            }
        }
    }

    fun saveLatestBeta(beta: Apk?) {
        if (beta != null) {
            transaction {
                // Check if index already exist
                val latest = ApkBetaEntity.find { ApkBeta.betaId.eq(LATEST_VERSION_ID) }
                if (latest.empty()) {
                    // Create if not exist
                    ApkBetaEntity.new {
                        this.betaId = LATEST_VERSION_ID
                        this.title = beta.title!!
                        this.pubDate = beta.pubDate!!
                        this.link = beta.link!!
                    }
                } else { // Update
                    latest.forEach { entity ->
                        run {
                            entity.title = beta.title!!
                            entity.pubDate = beta.pubDate!!
                            entity.link = beta.link!!
                        }
                    }
                }
            }
        }
    }

    fun readLatestRelease(): Apk? {
        var apk: ApkReleaseEntity? = null
        transaction {
            apk = ApkReleaseEntity.findById(1)
        }
        if (apk != null) {
            return Apk(apk!!.title, apk!!.pubDate, apk!!.link)
        }
        return null
    }

    fun readLatestBeta(): Apk? {
        var apk: ApkBetaEntity? = null
        transaction {
            apk = ApkBetaEntity.findById(1)
        }
        if (apk != null) {
            return Apk(apk!!.title, apk!!.pubDate, apk!!.link)
        }
        return null
    }

    fun readAllSubscribers(): List<SubEntity>? {
        var subs: List<SubEntity>? = null
        transaction {
            subs = SubEntity.all().toList()
        }
        return subs
    }

    fun addReleaseSubscriber(chatId: Long): Boolean {
        var subscribed = false
        transaction {
            val iter = SubEntity.find { Subscribers.chatId.eq(chatId) }
            if (!iter.empty()) {
                var found = false
                iter.forEach {
                    if (!it.release) {
                        it.release = true
                        found = true
                    }
                }
                subscribed = found
            } else {
                SubEntity.new { this.chatId = chatId; this.beta = false; this.release = true }
                subscribed = true
            }
        }
        return subscribed
    }

    fun addBetaSubscriber(chatId: Long): Boolean {
        var subscribed = false
        transaction {
            val iter = SubEntity.find { Subscribers.chatId.eq(chatId) }
            if (!iter.empty()) {
                var found = false
                iter.forEach {
                    if (!it.beta) {
                        it.beta = true
                        found = true
                    }
                }
                subscribed = found
            } else {
                SubEntity.new { this.chatId = chatId; this.beta = true; this.release = false }
                subscribed = true
            }
        }
        return subscribed
    }

    fun removeSubscriber(chatId: Long): Boolean {
        var result = false
        transaction {
            val iter = SubEntity.find { Subscribers.chatId.eq(chatId) }
            if (!iter.empty()) {
                iter.forEach { sub: SubEntity? -> sub?.delete() }
                result = true
            } else {
                result = false
            }
        }
        return result
    }

    fun dump() {
        transaction {
            print("Release APK: ")
            DbUtils.ApkReleaseEntity.all().forEach {
                println("${it.title} - ${it.pubDate}")
            }
            print("Beta APK: ")
            DbUtils.ApkBetaEntity.all().forEach {
                println("${it.title} - ${it.pubDate}")
            }
            println("Subscribers:")
            DbUtils.SubEntity.all().forEach {
                println("chatId: ${it.chatId}, beta: ${it.beta}, release: ${it.release}")
            }
        }
    }
}

fun main(args: Array<String>) {
    val dbUtils = DbUtils.instance
    dbUtils.dump()
    Thread.sleep(3000)
}

