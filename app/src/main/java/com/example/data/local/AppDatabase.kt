package com.example.data.local

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "quant_factor_configs")
data class QuantFactorConfigEntity(
    @PrimaryKey val factorId: String,
    val factorName: String,
    val category: String, // "MOMENTUM", "VALUE", "VOLATILITY", "MICROSTRUCTURE"
    val defaultWeight: Float,
    val minThreshold: Double,
    val maxThreshold: Double,
    val isEnabled: Boolean,
    val description: String
)

@Entity(tableName = "historical_factor_indicators")
data class HistoricalFactorIndicatorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val date: String,
    val momentum20D: Double,
    val momentum60D: Double,
    val peTtm: Double,
    val pb: Double,
    val historicalVol20D: Double,
    val atr14: Double,
    val chipProfitRatio: Double,
    val institutionalScore: Int
)

@Entity(tableName = "watchlist")
data class WatchlistEntity(
    @PrimaryKey val symbol: String,
    val name: String,
    val addTimestamp: Long,
    val lastCompositeScore: Int = 0,
    val lastValueScore: Int = 0,
    val lastGrowthScore: Int = 0,
    val lastMomentumScore: Int = 0,
    val lastQualityScore: Int = 0,
    val lastRiskControlScore: Int = 0,
    val lastRating: String = "",
    val notifyThresholdDelta: Int = 5,
    val isNotificationEnabled: Boolean = true
)

@Entity(tableName = "factor_score_alerts")
data class FactorScoreAlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val stockName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val oldScore: Int,
    val newScore: Int,
    val delta: Int,
    val oldRating: String,
    val newRating: String,
    val primaryFactorChanged: String,
    val alertMessage: String,
    val isRead: Boolean = false
)

@Entity(tableName = "trade_journal")
data class TradeJournalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val stockName: String,
    val status: String = "PLANNED", // "PLANNED", "OPEN", "CLOSED"
    val entryPrice: Double,
    val stopPrice: Double,
    val targetPrice: Double,
    val shares: Int,
    val plannedRiskRmb: Double,
    val gateVerdict: String,
    val thesis: String,
    val exitPrice: Double? = null,
    val realizedPnlRmb: Double? = null,
    val realizedRMultiple: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null
)

@Entity(tableName = "stock_dictionary")
data class StockDictionaryEntity(
    @PrimaryKey val code: String,
    val market: String,
    val symbol: String,
    val name: String,
    val pinyin: String,
    val updatedAt: Long
)

@Entity(tableName = "dictionary_meta")
data class DictionaryMetaEntity(
    @PrimaryKey val metaKey: String,
    val lastSyncAtMs: Long,
    val entryCount: Int,
    val sourceLabel: String
)

@Dao
interface QuantFactorDao {
    @Query("SELECT * FROM quant_factor_configs")
    fun getAllConfigs(): Flow<List<QuantFactorConfigEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfigs(configs: List<QuantFactorConfigEntity>)

    @Query("SELECT * FROM historical_factor_indicators WHERE symbol = :symbol ORDER BY date DESC LIMIT :limit")
    fun getHistoricalIndicators(symbol: String, limit: Int = 30): Flow<List<HistoricalFactorIndicatorEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoricalIndicator(indicator: HistoricalFactorIndicatorEntity)
}

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist ORDER BY addTimestamp DESC")
    fun getAllWatchlist(): Flow<List<WatchlistEntity>>

    @Query("SELECT * FROM watchlist WHERE symbol = :symbol LIMIT 1")
    suspend fun getWatchlistStock(symbol: String): WatchlistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToWatchlist(item: WatchlistEntity)

    @Query("UPDATE watchlist SET lastCompositeScore = :composite, lastValueScore = :value, lastGrowthScore = :growth, lastMomentumScore = :momentum, lastQualityScore = :quality, lastRiskControlScore = :risk, lastRating = :rating WHERE symbol = :symbol")
    suspend fun updateWatchlistScores(
        symbol: String,
        composite: Int,
        value: Int,
        growth: Int,
        momentum: Int,
        quality: Int,
        risk: Int,
        rating: String
    )

    @Query("UPDATE watchlist SET isNotificationEnabled = :enabled WHERE symbol = :symbol")
    suspend fun toggleNotification(symbol: String, enabled: Boolean)

    @Query("UPDATE watchlist SET notifyThresholdDelta = :delta WHERE symbol = :symbol")
    suspend fun updateThresholdDelta(symbol: String, delta: Int)

    @Query("DELETE FROM watchlist WHERE symbol = :symbol")
    suspend fun removeFromWatchlist(symbol: String)

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE symbol = :symbol)")
    suspend fun isWatchlisted(symbol: String): Boolean
}

@Dao
interface FactorAlertDao {
    @Query("SELECT * FROM factor_score_alerts ORDER BY timestamp DESC")
    fun getAllAlerts(): Flow<List<FactorScoreAlertEntity>>

    @Query("SELECT COUNT(*) FROM factor_score_alerts WHERE isRead = 0")
    fun getUnreadAlertCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: FactorScoreAlertEntity)

    @Query("UPDATE factor_score_alerts SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllAsRead()

    @Query("DELETE FROM factor_score_alerts")
    suspend fun clearAllAlerts()
}

@Dao
interface TradeJournalDao {
    @Query("SELECT * FROM trade_journal ORDER BY createdAt DESC")
    fun getAllEntries(): Flow<List<TradeJournalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: TradeJournalEntity): Long

    @Query("UPDATE trade_journal SET status = 'OPEN' WHERE id = :id")
    suspend fun markOpened(id: Long)

    @Query("UPDATE trade_journal SET status = 'CLOSED', exitPrice = :exitPrice, realizedPnlRmb = :pnlRmb, realizedRMultiple = :rMultiple, closedAt = :closedAt WHERE id = :id")
    suspend fun closeTrade(
        id: Long,
        exitPrice: Double,
        pnlRmb: Double,
        rMultiple: Double,
        closedAt: Long
    )

    @Query("DELETE FROM trade_journal WHERE id = :id")
    suspend fun deleteEntry(id: Long)
}

@Dao
interface StockDictionaryDao {
    @Query("SELECT * FROM stock_dictionary ORDER BY code ASC")
    suspend fun getAllEntries(): List<StockDictionaryEntity>

    @Query("SELECT COUNT(*) FROM stock_dictionary")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<StockDictionaryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: StockDictionaryEntity)

    @Query("DELETE FROM stock_dictionary")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceDictionary(entries: List<StockDictionaryEntity>) {
        clearAll()
        upsertAll(entries)
    }

    @Query("SELECT * FROM dictionary_meta WHERE metaKey = :key LIMIT 1")
    suspend fun getMeta(key: String = "a_share_all"): DictionaryMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMeta(meta: DictionaryMetaEntity)
}

@Database(
    entities = [
        QuantFactorConfigEntity::class,
        HistoricalFactorIndicatorEntity::class,
        WatchlistEntity::class,
        FactorScoreAlertEntity::class,
        TradeJournalEntity::class,
        StockDictionaryEntity::class,
        DictionaryMetaEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun quantFactorDao(): QuantFactorDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun factorAlertDao(): FactorAlertDao
    abstract fun tradeJournalDao(): TradeJournalDao
    abstract fun stockDictionaryDao(): StockDictionaryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `trade_journal` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `symbol` TEXT NOT NULL,
                        `stockName` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `entryPrice` REAL NOT NULL,
                        `stopPrice` REAL NOT NULL,
                        `targetPrice` REAL NOT NULL,
                        `shares` INTEGER NOT NULL,
                        `plannedRiskRmb` REAL NOT NULL,
                        `gateVerdict` TEXT NOT NULL,
                        `thesis` TEXT NOT NULL,
                        `exitPrice` REAL,
                        `realizedPnlRmb` REAL,
                        `realizedRMultiple` REAL,
                        `createdAt` INTEGER NOT NULL,
                        `closedAt` INTEGER
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `stock_dictionary` (
                        `code` TEXT NOT NULL PRIMARY KEY,
                        `market` TEXT NOT NULL,
                        `symbol` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `pinyin` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `dictionary_meta` (
                        `metaKey` TEXT NOT NULL PRIMARY KEY,
                        `lastSyncAtMs` INTEGER NOT NULL,
                        `entryCount` INTEGER NOT NULL,
                        `sourceLabel` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "quant_terminal_db"
                ).addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
