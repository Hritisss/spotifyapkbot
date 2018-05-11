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
            create(ApkReleases)
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

    object ApkReleases : IntIdTable() {
        val releaseId = integer("release_id").uniqueIndex()
        val version = varchar("version", 128)
        val pubDate = varchar("pub_date", 128)
    }

    object Subscribers : IntIdTable() {
        val chatId = long("chat_id")
    }

    class ApkEntity(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<ApkEntity>(ApkReleases)

        var releaseId by ApkReleases.releaseId
        var version by ApkReleases.version
        var pubDate by ApkReleases.pubDate
    }

    class SubEntity(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<SubEntity>(Subscribers)

        var chatId by Subscribers.chatId
    }

    object SqlDebugLogger : SqlLogger {
        private val log: Logger = Logger.getLogger(SqlDebugLogger::class.java)
        override fun log(context: StatementContext, transaction: Transaction) {
            log.debug("SQL: ${context.expandArgs(transaction)}")
        }
    }

    fun saveLatestVersion(version: String?, pubDate: String?) {
        if (version != null && pubDate != null) {
            transaction {
                // Check if index already exist
                val latest = ApkEntity.find { ApkReleases.releaseId.eq(LATEST_VERSION_ID) }
                if (latest.empty()) {
                    // Create if not exist
                    ApkEntity.new {
                        this.releaseId = LATEST_VERSION_ID
                        this.version = version
                        this.pubDate = pubDate
                    }
                } else { // Update
                    latest.forEach { entity ->
                        run {
                            entity.version = version
                            entity.pubDate = pubDate
                        }
                    }
                }
            }
        }
    }

    fun readLatestVersion(): ApkEntity? {
        var apk: ApkEntity? = null
        transaction {
            apk = ApkEntity.findById(1)
        }
        return apk
    }

    fun readAllSubscribers(): List<SubEntity>? {
        var subs: List<SubEntity>? = null
        transaction {
            subs = SubEntity.all().toList()
        }
        return subs
    }

    fun addSubscriber(chatId: Long): Boolean {
        var result = false
        transaction {
            val iter = SubEntity.find { Subscribers.chatId.eq(chatId) }
            if (!iter.empty()) {
                result = false
            } else {
                SubEntity.new { this.chatId = chatId }
                result = true
            }
        }
        return result
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
            DbUtils.ApkEntity.all().forEach {
                println("${it.version} - ${it.pubDate}")
            }
        }
    }
}

fun main(args: Array<String>) {
    val dbUtils = DbUtils.instance
    dbUtils.dump()
    Thread.sleep(3000)
}

