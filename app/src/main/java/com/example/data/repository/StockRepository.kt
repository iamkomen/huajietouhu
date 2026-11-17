package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.DictionaryMetaEntity
import com.example.data.local.StockDictionaryEntity
import com.example.data.local.TradeJournalEntity
import com.example.data.local.WatchlistEntity
import com.example.data.model.KLinePoint
import com.example.data.model.StockQuote
import com.example.data.model.StockSearchItem
import com.example.data.remote.StockApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap

enum class StockIndexCategory(
    val key: String,
    val title: String,
    val shortName: String,
    val description: String,
    val constituentCountText: String
) {
    CSI_300("CSI_300", "沪深300指数核心池", "沪深300", "A股核心大盘蓝筹龙头，流动性与估值基石", "300只核心蓝筹"),
    SSE_50("SSE_50", "上证50超大盘池", "上证50", "大市值国央企与金融消费顶梁柱核心资产", "50只超大盘"),
    STAR_50("STAR_50", "科创50硬科技池", "科创50", "半导体自主可控、算力AI与高端制造先锋", "50只硬科技"),
    CHINEXT("CHINEXT", "创业板指高成长池", "创业板指", "新能源、光模块通信与高端智能新兴赛道", "100只成长龙头"),
    CSI_500("CSI_500", "中证500中盘白马池", "中证500", "细分制造业与专精特新隐形冠军集合", "500只中盘冠军"),
    NEW_IPOS("NEW_IPOS", "次新及新上市高弹性池", "新股次新", "近期新上市活跃高关注度标的，实时全网动态同步", "全市场新股直连")
}

object StockIndexConstituents {
    // 1. 沪深300 核心权重代表性成分池
    val csi300Symbols = listOf(
        "sh600519", "sz300750", "sh601318", "sz002594", "sh600036", "sh600900", "sz000858", "sz000333",
        "sh601899", "sh600276", "sh600030", "sh601138", "sz002415", "sz300059", "sz300124", "sh603259",
        "sz002352", "sz002714", "sz300274", "sh688981", "sz002475", "sz000725", "sh601012", "sh600309",
        "sh601166", "sh601288", "sh601398", "sh601988", "sz300308", "sh688256", "sh601127", "sh601888",
        "sh600000", "sh600016", "sh600028", "sh601857", "sh600050", "sh601728", "sh600941", "sh601919"
    )

    // 2. 上证50 超大盘顶梁柱池
    val sse50Symbols = listOf(
        "sh600519", "sh601318", "sh600036", "sh600900", "sh601899", "sh600276", "sh600030", "sh601138",
        "sh603259", "sh688981", "sh600309", "sh601166", "sh601288", "sh601398", "sh601988", "sh601888",
        "sh600028", "sh601857", "sh600050", "sh601728", "sh600941", "sh601919", "sh601088", "sh600089",
        "sh600887", "sh600585", "sh601668", "sh601390", "sh601186", "sh601601"
    )

    // 3. 科创50 半导体与硬科技核心池
    val star50Symbols = listOf(
        "sh688981", "sh688256", "sh688041", "sh688012", "sh688111", "sh688008", "sh688385", "sh688036",
        "sh688235", "sh688521", "sh688772", "sh688126", "sh688223", "sh688169", "sh688536", "sh688301",
        "sh688599", "sh688082", "sh688516", "sh688072", "sh688692", "sh688200", "sh688608", "sh688188"
    )

    // 4. 创业板指 新兴赛道高成长核心池
    val chinextSymbols = listOf(
        "sz300750", "sz300308", "sz300124", "sz300059", "sz300274", "sz300015", "sz300502", "sz300433",
        "sz300014", "sz300498", "sz300760", "sz300142", "sz300450", "sz300628", "sz300759", "sz300661",
        "sz300316", "sz300454", "sz300122", "sz300223"
    )

    // 5. 中证500 细分赛道隐形冠军池
    val csi500Symbols = listOf(
        "sz002241", "sz002466", "sz002460", "sh603501", "sh603799", "sh600703", "sh600745", "sh601615",
        "sz002049", "sz002371", "sz002074", "sh603986", "sh600563", "sh600111", "sz002812", "sz002007"
    )

    // 6. 次新及新上市高弹性股票池
    val newIpoSymbols = listOf(
        "sh603059", "sz301589", "sh688692", "sh688608", "sz301558", "sh688778", "sz301567", "sh603221",
        "sz301529", "sh688720", "sz301519", "sh688707", "sz301509", "sh688667", "sz301489", "sh688656"
    )

    fun getSymbols(category: StockIndexCategory): List<String> = when (category) {
        StockIndexCategory.CSI_300 -> csi300Symbols
        StockIndexCategory.SSE_50 -> sse50Symbols
        StockIndexCategory.STAR_50 -> star50Symbols
        StockIndexCategory.CHINEXT -> chinextSymbols
        StockIndexCategory.CSI_500 -> csi500Symbols
        StockIndexCategory.NEW_IPOS -> newIpoSymbols
    }

    fun getConstituentSet(category: StockIndexCategory): Set<String> =
        getSymbols(category).map { it.lowercase() }.toSet()
}

class StockRepository(private val database: AppDatabase) {

    val popularSymbols = listOf(
        "sh600519", // 贵州茅台
        "sz300750", // 宁德时代
        "sz002594", // 比亚迪
        "sh688981", // 中芯国际
        "sh601318", // 中国平安
        "sh600900", // 长江电力
        "sh601127", // 赛力斯
        "sz300308", // 中际旭创
        "sh688256"  // 寒武纪
    )

    val marketIndexSymbols = listOf(
        "sh000001", // 上证指数
        "sh000300", // 沪深300指数
        "sz399006", // 创业板指
        "sh000688"  // 科创50指数
    )

    val csi300Symbols get() = StockIndexConstituents.csi300Symbols
    val sse50Symbols get() = StockIndexConstituents.sse50Symbols
    val star50Symbols get() = StockIndexConstituents.star50Symbols
    val chinextSymbols get() = StockIndexConstituents.chinextSymbols
    val csi500Symbols get() = StockIndexConstituents.csi500Symbols
    val newIpoSymbols get() = StockIndexConstituents.newIpoSymbols

    fun getIndexConstituents(category: StockIndexCategory): List<String> =
        StockIndexConstituents.getSymbols(category)

    fun getIndexConstituentSet(category: StockIndexCategory): Set<String> =
        StockIndexConstituents.getConstituentSet(category)

    suspend fun getQuotes(symbols: List<String>): List<StockQuote> {
        return StockApiService.getQuotes(symbols)
    }

    suspend fun getDailyKLines(symbol: String, count: Int = 140): List<KLinePoint> {
        return StockApiService.getMultiPeriodKLines(symbol, "day", count)
    }

    suspend fun getMultiPeriodKLines(symbol: String, period: String, count: Int = 140): List<KLinePoint> {
        return StockApiService.getMultiPeriodKLines(symbol, period, count)
    }

    suspend fun getIntradayMinutes(symbol: String): List<com.example.data.model.MinutePoint> {
        return StockApiService.getIntradayMinutes(symbol)
    }

    data class DictionaryStatus(
        val totalCount: Int,
        val lastUpdatedMs: Long,
        val isFullMarketSynced: Boolean
    )

    companion object {
        const val DICTIONARY_META_KEY = "a_share_all"
        const val DICTIONARY_REFRESH_INTERVAL_MS = 12L * 60L * 60L * 1000L
        const val FULL_DICTIONARY_MIN_SIZE = 1000
    }

    /**
     * 内置高频核心种子字典：保证首次冷启动或离线状态下仍可毫秒级直查主流核心标的
     */
    private val coreCandidatePool = listOf(
        StockSearchItem("600519", "sh", "sh600519", "贵州茅台", "gzmt"),
        StockSearchItem("300750", "sz", "sz300750", "宁德时代", "ndsd"),
        StockSearchItem("002594", "sz", "sz002594", "比亚迪", "byd"),
        StockSearchItem("601127", "sh", "sh601127", "赛力斯", "sls"),
        StockSearchItem("688256", "sh", "sh688256", "寒武纪", "hwj"),
        StockSearchItem("300308", "sz", "sz300308", "中际旭创", "zjxc"),
        StockSearchItem("688981", "sh", "sh688981", "中芯国际", "zxgj"),
        StockSearchItem("600036", "sh", "sh600036", "招商银行", "zsyh"),
        StockSearchItem("601318", "sh", "sh601318", "中国平安", "zgpa"),
        StockSearchItem("600900", "sh", "sh600900", "长江电力", "cjdl"),
        StockSearchItem("600938", "sh", "sh600938", "中国海油", "zghy"),
        StockSearchItem("601857", "sh", "sh601857", "中国石油", "zgsy"),
        StockSearchItem("600028", "sh", "sh600028", "中国石化", "zgsh"),
        StockSearchItem("600941", "sh", "sh600941", "中国移动", "zgyd"),
        StockSearchItem("601728", "sh", "sh601728", "中国电信", "zgdx"),
        StockSearchItem("600050", "sh", "sh600050", "中国联通", "zglt"),
        StockSearchItem("601088", "sh", "sh601088", "中国神华", "zgsh"),
        StockSearchItem("600150", "sh", "sh600150", "中国船舶", "zgcb"),
        StockSearchItem("601766", "sh", "sh601766", "中国中车", "zgzc"),
        StockSearchItem("601985", "sh", "sh601985", "中国核电", "zghd"),
        StockSearchItem("003816", "sz", "sz003816", "中国广核", "zggh"),
        StockSearchItem("601919", "sh", "sh601919", "中远海控", "zyhk"),
        StockSearchItem("601888", "sh", "sh601888", "中国中免", "zgzm"),
        StockSearchItem("601668", "sh", "sh601668", "中国建筑", "zgjz"),
        StockSearchItem("601800", "sh", "sh601800", "中国交建", "zgjj"),
        StockSearchItem("601186", "sh", "sh601186", "中国铁建", "zgtj"),
        StockSearchItem("601390", "sh", "sh601390", "中国中铁", "zgzt"),
        StockSearchItem("601669", "sh", "sh601669", "中国电建", "zgdj"),
        StockSearchItem("601988", "sh", "sh601988", "中国银行", "zgyh"),
        StockSearchItem("601600", "sh", "sh601600", "中国铝业", "zgly"),
        StockSearchItem("601601", "sh", "sh601601", "中国太保", "zgtb"),
        StockSearchItem("601628", "sh", "sh601628", "中国人寿", "zgrs"),
        StockSearchItem("000858", "sz", "sz000858", "五粮液", "wly"),
        StockSearchItem("000333", "sz", "sz000333", "美的集团", "mdjt"),
        StockSearchItem("601899", "sh", "sh601899", "紫金矿业", "zjky"),
        StockSearchItem("600362", "sh", "sh600362", "江西铜业", "jxty"),
        StockSearchItem("603993", "sh", "sh603993", "洛阳钼业", "lymy"),
        StockSearchItem("600111", "sh", "sh600111", "北方稀土", "bfxt"),
        StockSearchItem("000831", "sz", "sz000831", "中国稀土", "zgxt"),
        StockSearchItem("601225", "sh", "sh601225", "陕西煤业", "sxmy"),
        StockSearchItem("600188", "sh", "sh600188", "兖矿能源", "ykny"),
        StockSearchItem("600019", "sh", "sh600019", "宝钢股份", "bggf"),
        StockSearchItem("600547", "sh", "sh600547", "山东黄金", "sdhj"),
        StockSearchItem("600489", "sh", "sh600489", "中金黄金", "zjhj"),
        StockSearchItem("600988", "sh", "sh600988", "赤峰黄金", "cfhj"),
        StockSearchItem("600030", "sh", "sh600030", "中信证券", "zxzq"),
        StockSearchItem("300059", "sz", "sz300059", "东方财富", "dfcf"),
        StockSearchItem("601138", "sh", "sh601138", "工业富联", "gyfl"),
        StockSearchItem("300502", "sz", "sz300502", "新易盛", "xys"),
        StockSearchItem("300394", "sz", "sz300394", "天孚通信", "tftx"),
        StockSearchItem("002463", "sz", "sz002463", "沪电股份", "hdgf"),
        StockSearchItem("300476", "sz", "sz300476", "胜宏科技", "shkj"),
        StockSearchItem("600276", "sh", "sh600276", "恒瑞医药", "hryy"),
        StockSearchItem("603259", "sh", "sh603259", "药明康德", "ymkd"),
        StockSearchItem("300760", "sz", "sz300760", "迈瑞医疗", "mryl"),
        StockSearchItem("688271", "sh", "sh688271", "联影医疗", "lyyl"),
        StockSearchItem("600436", "sh", "sh600436", "片仔癀", "pzh"),
        StockSearchItem("000538", "sz", "sz000538", "云南白药", "ynby"),
        StockSearchItem("300015", "sz", "sz300015", "爱尔眼科", "aeyk"),
        StockSearchItem("002475", "sz", "sz002475", "立讯精密", "lxjm"),
        StockSearchItem("002415", "sz", "sz002415", "海康威视", "hkws"),
        StockSearchItem("000725", "sz", "sz000725", "京东方A", "jdfa"),
        StockSearchItem("688012", "sh", "sh688012", "中微公司", "zwgs"),
        StockSearchItem("688041", "sh", "sh688041", "海光信息", "hgxx"),
        StockSearchItem("688111", "sh", "sh688111", "金山办公", "jsbg"),
        StockSearchItem("002371", "sz", "sz002371", "北方华创", "bfhc"),
        StockSearchItem("603501", "sh", "sh603501", "韦尔股份", "wegf"),
        StockSearchItem("688008", "sh", "sh688008", "澜起科技", "lqkj"),
        StockSearchItem("603986", "sh", "sh603986", "兆易创新", "zycx"),
        StockSearchItem("688047", "sh", "sh688047", "龙芯中科", "lxzk"),
        StockSearchItem("603019", "sh", "sh603019", "中科曙光", "zksg"),
        StockSearchItem("000977", "sz", "sz000977", "浪潮信息", "lcxx"),
        StockSearchItem("000063", "sz", "sz000063", "中兴通讯", "zxtx"),
        StockSearchItem("002230", "sz", "sz002230", "科大讯飞", "kdxf"),
        StockSearchItem("300033", "sz", "sz300033", "同花顺", "ths"),
        StockSearchItem("600570", "sh", "sh600570", "恒生电子", "hsdz"),
        StockSearchItem("300124", "sz", "sz300124", "汇川技术", "hcjs"),
        StockSearchItem("300274", "sz", "sz300274", "阳光电源", "ygdy"),
        StockSearchItem("601012", "sh", "sh601012", "隆基绿能", "ljln"),
        StockSearchItem("600406", "sh", "sh600406", "国电南瑞", "gdnr"),
        StockSearchItem("600089", "sh", "sh600089", "特变电工", "tbdg"),
        StockSearchItem("600031", "sh", "sh600031", "三一重工", "syzg"),
        StockSearchItem("002241", "sz", "sz002241", "歌尔股份", "gegf"),
        StockSearchItem("002460", "sz", "sz002460", "赣锋锂业", "gfly"),
        StockSearchItem("002466", "sz", "sz002466", "天齐锂业", "tqly"),
        StockSearchItem("600309", "sh", "sh600309", "万华化学", "whhx"),
        StockSearchItem("601398", "sh", "sh601398", "工商银行", "gsyh"),
        StockSearchItem("601288", "sh", "sh601288", "农业银行", "nyyh"),
        StockSearchItem("601939", "sh", "sh601939", "建设银行", "jsyh"),
        StockSearchItem("601328", "sh", "sh601328", "交通银行", "jtyh"),
        StockSearchItem("601166", "sh", "sh601166", "兴业银行", "xyyh"),
        StockSearchItem("600690", "sh", "sh600690", "海尔智家", "hezj"),
        StockSearchItem("000651", "sz", "sz000651", "格力电器", "gldq"),
        StockSearchItem("600887", "sh", "sh600887", "伊利股份", "ylgf"),
        StockSearchItem("000568", "sz", "sz000568", "泸州老窖", "lzlj"),
        StockSearchItem("600809", "sh", "sh600809", "山西汾酒", "sxfj"),
        StockSearchItem("603288", "sh", "sh603288", "海天味业", "htwy"),
        StockSearchItem("002352", "sz", "sz002352", "顺丰控股", "sfkg"),
        StockSearchItem("002714", "sz", "sz002714", "牧原股份", "mygf"),
        StockSearchItem("600660", "sh", "sh600660", "福耀玻璃", "fybl"),
        StockSearchItem("600585", "sh", "sh600585", "海螺水泥", "hlsn"),
        StockSearchItem("601633", "sh", "sh601633", "长城汽车", "ccqc"),
        StockSearchItem("000625", "sz", "sz000625", "长安汽车", "caqc"),
        StockSearchItem("600760", "sh", "sh600760", "中航沈飞", "zhsf"),
        StockSearchItem("600893", "sh", "sh600893", "航发动力", "hfdl"),
        StockSearchItem("002179", "sz", "sz002179", "中航光电", "zhgd"),
        StockSearchItem("600048", "sh", "sh600048", "保利发展", "blfz"),
        StockSearchItem("000002", "sz", "sz000002", "万科A", "wka"),
        StockSearchItem("601816", "sh", "sh601816", "京沪高铁", "jhgt")
    )

    private val dictionaryMap = ConcurrentHashMap<String, StockSearchItem>().apply {
        coreCandidatePool.forEach { put(it.code, it) }
    }

    @Volatile
    private var dictionaryLastSyncAtMs: Long = 0L

    @Volatile
    private var persistedDictionaryCount: Int = 0

    @Volatile
    private var dictionaryLoadedFromDb: Boolean = false

    fun getDictionaryStatus(): DictionaryStatus {
        return DictionaryStatus(
            totalCount = dictionaryMap.size,
            lastUpdatedMs = dictionaryLastSyncAtMs,
            isFullMarketSynced = persistedDictionaryCount >= FULL_DICTIONARY_MIN_SIZE
        )
    }

    suspend fun loadPersistedDictionary(): DictionaryStatus {
        val dao = database.stockDictionaryDao()
        val entries = dao.getAllEntries()
        val meta = dao.getMeta(DICTIONARY_META_KEY)
        for (entity in entries) {
            val existing = dictionaryMap[entity.code]
            val item = StockSearchItem(
                code = entity.code,
                market = entity.market,
                symbol = entity.symbol,
                name = entity.name,
                pinyin = entity.pinyin.ifBlank { StockApiService.toPinyinInitials(entity.name) }
            )
            if (existing == null) {
                dictionaryMap[entity.code] = item
            } else if (existing.name != item.name || existing.pinyin.isBlank()) {
                dictionaryMap[entity.code] = item
            }
        }
        persistedDictionaryCount = entries.size
        dictionaryLastSyncAtMs = meta?.lastSyncAtMs ?: dictionaryLastSyncAtMs
        dictionaryLoadedFromDb = true
        return getDictionaryStatus()
    }

    /**
     * 定时更新本地股票字典：默认每 12 小时自动刷新一次全市场 A 股字典并落盘至 Room，
     * 未过期时直接复用本地磁盘/内存字典，不触发任何网络请求。
     */
    suspend fun syncDictionaryIfNeeded(
        force: Boolean = false,
        nowMs: Long = System.currentTimeMillis(),
        fetcher: suspend () -> List<StockSearchItem> = { StockApiService.fetchFullAShareDirectory() }
    ): DictionaryStatus {
        if (!dictionaryLoadedFromDb) {
            loadPersistedDictionary()
        }
        val dao = database.stockDictionaryDao()
        val meta = dao.getMeta(DICTIONARY_META_KEY)
        val currentPersisted = persistedDictionaryCount
        val lastSync = meta?.lastSyncAtMs ?: dictionaryLastSyncAtMs
        val isFresh = currentPersisted >= FULL_DICTIONARY_MIN_SIZE &&
            lastSync > 0L &&
            (nowMs - lastSync) < DICTIONARY_REFRESH_INTERVAL_MS

        if (!force && isFresh) {
            dictionaryLastSyncAtMs = lastSync
            return getDictionaryStatus()
        }

        val fetched = try {
            fetcher()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }

        if (fetched.size >= 50) {
            val entities = fetched.distinctBy { it.code }.map { item ->
                val pinyin = item.pinyin.ifBlank { StockApiService.toPinyinInitials(item.name) }
                StockDictionaryEntity(
                    code = item.code,
                    market = item.market,
                    symbol = item.symbol,
                    name = item.name,
                    pinyin = pinyin,
                    updatedAt = nowMs
                )
            }
            dao.replaceDictionary(entities)
            dao.saveMeta(
                DictionaryMetaEntity(
                    metaKey = DICTIONARY_META_KEY,
                    lastSyncAtMs = nowMs,
                    entryCount = entities.size,
                    sourceLabel = "A_SHARE_FULL"
                )
            )
            for (entity in entities) {
                dictionaryMap[entity.code] = StockSearchItem(
                    code = entity.code,
                    market = entity.market,
                    symbol = entity.symbol,
                    name = entity.name,
                    pinyin = entity.pinyin
                )
            }
            persistedDictionaryCount = entities.size
            dictionaryLastSyncAtMs = nowMs
        }
        return getDictionaryStatus()
    }

    suspend fun warmUpSearchDirectory(force: Boolean = false): DictionaryStatus {
        return try {
            syncDictionaryIfNeeded(force = force)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            getDictionaryStatus()
        }
    }

    suspend fun learnStockIntoDictionary(quote: StockQuote) {
        if (quote.code.length != 6 || quote.name.isBlank()) return
        val pinyin = StockApiService.toPinyinInitials(quote.name)
        val item = StockSearchItem(
            code = quote.code,
            market = quote.market,
            symbol = quote.symbol.lowercase(),
            name = quote.name,
            pinyin = pinyin
        )
        val existing = dictionaryMap[quote.code]
        if (existing == null || existing.name != item.name) {
            dictionaryMap[quote.code] = item
            try {
                database.stockDictionaryDao().upsertEntry(
                    StockDictionaryEntity(
                        code = item.code,
                        market = item.market,
                        symbol = item.symbol,
                        name = item.name,
                        pinyin = item.pinyin,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } catch (_: Exception) {
                // Best-effort dictionary enrichment
            }
        }
    }

    /**
     * 纯本地毫秒级字典检索：不触发实时网络请求，直接检索内存/持久化股票字典与已知行情池。
     */
    fun searchLocalStocks(query: String, extraQuotes: List<StockQuote> = emptyList()): List<StockSearchItem> {
        val q = query.trim()
        if (q.isEmpty()) {
            return coreCandidatePool.take(20)
        }
        val merged = LinkedHashMap<String, StockSearchItem>(dictionaryMap.size + extraQuotes.size + 16)
        for (item in coreCandidatePool) {
            merged[item.code] = item
        }
        for ((code, item) in dictionaryMap) {
            if (!merged.containsKey(code)) {
                merged[code] = item
            }
        }
        for (quote in extraQuotes) {
            if (quote.code.length == 6 && !merged.containsKey(quote.code)) {
                merged[quote.code] = StockSearchItem(
                    code = quote.code,
                    market = quote.market,
                    symbol = quote.symbol,
                    name = quote.name,
                    pinyin = StockApiService.toPinyinInitials(quote.name)
                )
            }
        }
        for (item in StockApiService.getCachedAShareDirectory()) {
            val existing = merged[item.code]
            if (existing == null) {
                merged[item.code] = item
            } else if (existing.pinyin.isBlank() && item.pinyin.isNotBlank()) {
                merged[item.code] = existing.copy(pinyin = item.pinyin)
            }
        }
        return StockApiService.filterAndRankSearchItems(q, merged.values.toList(), limit = 40)
    }

    suspend fun searchStocks(query: String, extraQuotes: List<StockQuote> = emptyList()): List<StockSearchItem> {
        return searchLocalStocks(query, extraQuotes)
    }

    fun getWatchlist(): Flow<List<String>> {
        return database.watchlistDao().getAllWatchlist().map { list -> list.map { it.symbol } }
    }

    fun getWatchlistEntities(): Flow<List<WatchlistEntity>> {
        return database.watchlistDao().getAllWatchlist()
    }

    fun getFactorAlerts(): Flow<List<com.example.data.local.FactorScoreAlertEntity>> {
        return database.factorAlertDao().getAllAlerts()
    }

    fun getUnreadAlertCount(): Flow<Int> {
        return database.factorAlertDao().getUnreadAlertCount()
    }

    suspend fun toggleWatchlistNotification(symbol: String, enabled: Boolean) {
        database.watchlistDao().toggleNotification(symbol, enabled)
    }

    suspend fun updateWatchlistThreshold(symbol: String, delta: Int) {
        database.watchlistDao().updateThresholdDelta(symbol, delta)
    }

    suspend fun clearAllAlerts() {
        database.factorAlertDao().clearAllAlerts()
    }

    suspend fun markAlertsAsRead() {
        database.factorAlertDao().markAllAsRead()
    }

    suspend fun insertAlert(alert: com.example.data.local.FactorScoreAlertEntity) {
        database.factorAlertDao().insertAlert(alert)
    }

    suspend fun updateWatchlistScores(
        symbol: String,
        composite: Int,
        value: Int,
        growth: Int,
        momentum: Int,
        quality: Int,
        risk: Int,
        rating: String
    ) {
        database.watchlistDao().updateWatchlistScores(
            symbol = symbol,
            composite = composite,
            value = value,
            growth = growth,
            momentum = momentum,
            quality = quality,
            risk = risk,
            rating = rating
        )
    }

    suspend fun removeFromWatchlist(symbol: String) {
        database.watchlistDao().removeFromWatchlist(symbol)
    }

    suspend fun addToWatchlist(symbol: String, name: String) {
        database.watchlistDao().addToWatchlist(WatchlistEntity(symbol, name, System.currentTimeMillis()))
    }

    suspend fun toggleWatchlist(symbol: String, name: String) {
        val dao = database.watchlistDao()
        if (dao.isWatchlisted(symbol)) {
            dao.removeFromWatchlist(symbol)
        } else {
            dao.addToWatchlist(WatchlistEntity(symbol, name, System.currentTimeMillis()))
        }
    }

    suspend fun isWatchlisted(symbol: String): Boolean {
        return database.watchlistDao().isWatchlisted(symbol)
    }

    private val f10Cache = java.util.concurrent.ConcurrentHashMap<String, com.example.data.model.FinancialStatementSnapshot>()

    suspend fun getFinancialFundamentals(
        symbol: String,
        forceRefresh: Boolean = false
    ): com.example.data.model.FinancialStatementSnapshot? {
        if (!forceRefresh) {
            f10Cache[symbol]?.let { return it }
        }
        val fetched = StockApiService.getFinancialFundamentals(symbol)
        if (fetched != null) {
            f10Cache[symbol] = fetched
        }
        return fetched
    }

    fun getCachedFinancialSnapshots(): Map<String, com.example.data.model.FinancialStatementSnapshot> {
        return f10Cache.toMap()
    }

    fun getTradeJournalEntries(): Flow<List<TradeJournalEntity>> {
        return database.tradeJournalDao().getAllEntries()
    }

    suspend fun insertTradeJournalEntry(entry: TradeJournalEntity): Long {
        return database.tradeJournalDao().insertEntry(entry)
    }

    suspend fun markTradeOpened(id: Long) {
        database.tradeJournalDao().markOpened(id)
    }

    suspend fun closeTradeEntry(
        id: Long,
        exitPrice: Double,
        pnlRmb: Double,
        rMultiple: Double
    ) {
        database.tradeJournalDao().closeTrade(
            id = id,
            exitPrice = exitPrice,
            pnlRmb = pnlRmb,
            rMultiple = rMultiple,
            closedAt = System.currentTimeMillis()
        )
    }

    suspend fun deleteTradeEntry(id: Long) {
        database.tradeJournalDao().deleteEntry(id)
    }
}
