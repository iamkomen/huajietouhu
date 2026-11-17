package com.example.data.remote

import com.example.data.model.KLinePoint
import com.example.data.model.OrderBook
import com.example.data.model.OrderBookTier
import com.example.data.model.StockQuote
import com.example.data.model.StockSearchItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

object StockApiService {
    private val client = OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply {
            maxRequests = 32
            maxRequestsPerHost = 12
        })
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    private val searchClient = OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply {
            maxRequests = 32
            maxRequestsPerHost = 12
        })
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cachedAShareDirectory: List<StockSearchItem> = emptyList()

    @Volatile
    private var lastDirectoryFetchMs: Long = 0L

    /**
     * 批量获取腾讯财经实时行情接口 (GBK编码)，并在主源缺失时自动走东方财富批量行情补全
     */
    suspend fun getQuotes(symbols: List<String>): List<StockQuote> = withContext(Dispatchers.IO) {
        val cleanSymbols = symbols.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
        if (cleanSymbols.isEmpty()) return@withContext emptyList()

        val resultMap = LinkedHashMap<String, StockQuote>()
        for (chunk in cleanSymbols.chunked(30)) {
            val query = chunk.joinToString(",")
            val url = "https://qt.gtimg.cn/q=$query"
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://gu.qq.com/")
                    .build()
                val bytes = client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.bytes() else null
                }
                if (bytes != null) {
                    val text = String(bytes, charset("GBK"))
                    for (q in parseTencentQuotes(text)) {
                        if (q.price > 0.0 || q.yesterdayClose > 0.0) {
                            resultMap[q.symbol.lowercase()] = q
                        }
                    }
                }
            } catch (_: Exception) {
                // Fall back to Eastmoney below for any missing symbols
            }
        }

        val missingSymbols = cleanSymbols.filter { !resultMap.containsKey(it) }
        if (missingSymbols.isNotEmpty()) {
            for (q in fetchEastmoneyBatchQuotes(missingSymbols)) {
                if (!resultMap.containsKey(q.symbol.lowercase())) {
                    resultMap[q.symbol.lowercase()] = q
                }
            }
        }

        cleanSymbols.mapNotNull { resultMap[it] }
    }

    private fun fetchEastmoneyBatchQuotes(symbols: List<String>): List<StockQuote> {
        val secIds = symbols.mapNotNull { sym ->
            val lower = sym.lowercase()
            val code = lower.takeLast(6)
            if (code.length != 6 || !code.all { it.isDigit() }) null
            else {
                val prefix = if (lower.startsWith("sh")) "1" else "0"
                "$prefix.$code" to lower
            }
        }
        if (secIds.isEmpty()) return emptyList()
        val symbolLookup = secIds.associate { (sec, sym) -> sec.substringAfter(".") to sym }
        val results = mutableListOf<StockQuote>()

        for (chunk in secIds.chunked(40)) {
            val joined = chunk.joinToString(",") { it.first }
            val url = "https://push2.eastmoney.com/api/qt/ulist.np/get" +
                "?fltt=2&invt=2&fields=f2,f3,f4,f5,f6,f7,f8,f9,f12,f13,f14,f15,f16,f17,f18,f20,f21,f23" +
                "&secids=$joined"
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://quote.eastmoney.com/")
                    .build()
                val body = client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.string() else null
                } ?: continue
                val diff = org.json.JSONObject(body)
                    .optJSONObject("data")
                    ?.optJSONArray("diff") ?: continue
                for (i in 0 until diff.length()) {
                    val obj = diff.optJSONObject(i) ?: continue
                    val code = obj.optString("f12").trim()
                    if (code.length != 6) continue
                    val mktFlag = obj.optInt("f13", -1)
                    val market = when (mktFlag) {
                        1 -> "sh"
                        0 -> if (code.startsWith("8") || code.startsWith("4") || code.startsWith("920")) "bj" else "sz"
                        else -> inferMarketFromCode(code)
                    }
                    val sym = symbolLookup[code] ?: "$market$code"
                    val name = obj.optString("f14").trim().ifEmpty { sym }
                    val price = optSafeDouble(obj, "f2")
                    val preClose = optSafeDouble(obj, "f18").takeIf { it > 0.0 } ?: price
                    if (price <= 0.0 && preClose <= 0.0) continue
                    val effPrice = if (price > 0.0) price else preClose
                    val chgPct = optSafeDouble(obj, "f3")
                    val chg = optSafeDouble(obj, "f4")
                    val volLots = optSafeDouble(obj, "f5").toLong()
                    val amtTenThousand = optSafeDouble(obj, "f6") / 10000.0
                    val amp = optSafeDouble(obj, "f7")
                    val turnoverRate = optSafeDouble(obj, "f8")
                    val peTtm = optSafeDouble(obj, "f9")
                    val high = optSafeDouble(obj, "f15").takeIf { it > 0.0 } ?: effPrice
                    val low = optSafeDouble(obj, "f16").takeIf { it > 0.0 } ?: effPrice
                    val open = optSafeDouble(obj, "f17").takeIf { it > 0.0 } ?: effPrice
                    val totalCap = optSafeDouble(obj, "f20") / 1e8
                    val circCap = optSafeDouble(obj, "f21") / 1e8
                    val pb = optSafeDouble(obj, "f23")
                    results.add(
                        StockQuote(
                            code = code,
                            market = market,
                            symbol = sym,
                            name = name,
                            price = effPrice,
                            yesterdayClose = preClose,
                            open = open,
                            change = chg,
                            changePercent = chgPct,
                            high = high,
                            low = low,
                            volumeLots = volLots,
                            turnoverTenThousand = amtTenThousand,
                            turnoverRate = turnoverRate,
                            peTtm = peTtm,
                            pb = pb,
                            totalMarketCapHundredMillion = totalCap,
                            circMarketCapHundredMillion = if (circCap > 0.0) circCap else totalCap,
                            amplitude = amp
                        )
                    )
                }
            } catch (_: Exception) {
                // Skip failing batch
            }
        }
        return results
    }

    private suspend fun executeSearchGetBytes(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): ByteArray? = suspendCancellableCoroutine { cont ->
        val builder = Request.Builder().url(url)
        headers.forEach { (k, v) -> builder.header(k, v) }
        val call = searchClient.newCall(builder.build())
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resume(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { res ->
                    val bytes = try {
                        if (res.isSuccessful) res.body?.bytes() else null
                    } catch (_: Exception) {
                        null
                    }
                    if (cont.isActive) cont.resume(bytes)
                }
            }
        })
    }

    fun getCachedAShareDirectory(): List<StockSearchItem> = cachedAShareDirectory

    suspend fun warmUpAShareDirectory(force: Boolean = false): List<StockSearchItem> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && cachedAShareDirectory.size >= 1000 && (now - lastDirectoryFetchMs) < 12 * 60 * 60 * 1000L) {
            return@withContext cachedAShareDirectory
        }
        val fetched = fetchFullAShareDirectory()
        if (fetched.size >= 50) {
            cachedAShareDirectory = fetched
            lastDirectoryFetchMs = now
        }
        return@withContext cachedAShareDirectory
    }

    /**
     * 定时同步全市场A股字典（5300+只标的），供本地毫秒级检索使用，避免每次输入触发在线检索。
     * 1. 主链路：东方财富 clist/get 自动识别单页上限并并发分页拉齐全量 A 股；
     * 2. 备援主机：80.push2.eastmoney.com；
     * 3. 备援数据源：新浪财经 hs_a 分页行情节点。
     */
    suspend fun fetchFullAShareDirectory(): List<StockSearchItem> = withContext(Dispatchers.IO) {
        val eastmoneyHosts = listOf("push2.eastmoney.com", "80.push2.eastmoney.com")
        for (host in eastmoneyHosts) {
            val emList = fetchEastmoneyPaginatedDirectory(host)
            if (emList.size >= 50) {
                return@withContext emList
            }
        }
        val sinaList = fetchSinaPaginatedDirectory()
        if (sinaList.size >= 50) {
            return@withContext sinaList
        }
        cachedAShareDirectory
    }

    private suspend fun fetchEastmoneyPaginatedDirectory(host: String): List<StockSearchItem> {
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Referer" to "https://quote.eastmoney.com/"
        )
        val requestedPageSize = 500
        val firstUrl = "https://$host/api/qt/clist/get" +
            "?pn=1&pz=$requestedPageSize&po=1&np=1&fltt=2&invt=2&fid=f12" +
            "&fs=m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23,m:0+t:81+s:2048&fields=f12,f13,f14"
        val firstBytes = executeSearchGetBytes(firstUrl, headers) ?: return emptyList()
        val firstJson = String(firstBytes, Charsets.UTF_8)
        val (totalCount, firstPageItems) = parseEastmoneyDirectoryPage(firstJson)
        if (firstPageItems.isEmpty()) return emptyList()

        val merged = LinkedHashMap<String, StockSearchItem>(maxOf(totalCount, firstPageItems.size))
        for (item in firstPageItems) {
            merged[item.code] = item
        }
        if (totalCount <= firstPageItems.size) {
            return merged.values.toList()
        }

        val effectivePageSize = firstPageItems.size.coerceAtLeast(50)
        val totalPages = ((totalCount + effectivePageSize - 1) / effectivePageSize).coerceAtMost(80)
        if (totalPages <= 1) return merged.values.toList()

        val pageBatches = (2..totalPages).toList().chunked(6)
        for (batch in pageBatches) {
            val batchItems = coroutineScope {
                batch.map { page ->
                    async {
                        val pageUrl = "https://$host/api/qt/clist/get" +
                            "?pn=$page&pz=$effectivePageSize&po=1&np=1&fltt=2&invt=2&fid=f12" +
                            "&fs=m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23,m:0+t:81+s:2048&fields=f12,f13,f14"
                        val bytes = executeSearchGetBytes(pageUrl, headers) ?: return@async emptyList<StockSearchItem>()
                        parseEastmoneyDirectoryPage(String(bytes, Charsets.UTF_8)).second
                    }
                }.awaitAll()
            }
            for (items in batchItems) {
                for (item in items) {
                    if (!merged.containsKey(item.code)) {
                        merged[item.code] = item
                    }
                }
            }
        }
        return merged.values.toList()
    }

    private suspend fun fetchSinaPaginatedDirectory(): List<StockSearchItem> {
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Referer" to "https://vip.stock.finance.sina.com.cn/"
        )
        val merged = LinkedHashMap<String, StockSearchItem>(6000)
        val pageBatches = (1..60).toList().chunked(6)
        for (batch in pageBatches) {
            val batchResults = coroutineScope {
                batch.map { page ->
                    async {
                        val url = "https://vip.stock.finance.sina.com.cn/quotes_service/api/json_v2.php/" +
                            "Market_Center.getHQNodeData?page=$page&num=100&sort=symbol&asc=1&node=hs_a"
                        val bytes = executeSearchGetBytes(url, headers) ?: return@async emptyList<StockSearchItem>()
                        parseSinaDirectoryArray(String(bytes, Charsets.UTF_8))
                    }
                }.awaitAll()
            }
            var anyNonEmpty = false
            for (list in batchResults) {
                if (list.isNotEmpty()) anyNonEmpty = true
                for (item in list) {
                    if (!merged.containsKey(item.code)) {
                        merged[item.code] = item
                    }
                }
            }
            if (!anyNonEmpty || batchResults.lastOrNull()?.size?.let { it < 100 } == true) {
                break
            }
        }
        return merged.values.toList()
    }

    /**
     * 全市场动态实时检索接口 (涵盖全A股5300+标的与实时新上市次新股)
     * 并行探测：腾讯 Smartbox + 新浪 Suggest + 东方财富 Suggest + 全市场目录缓存 + 6位代码直连
     */
    suspend fun searchStocksOnline(query: String): List<StockSearchItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val encodedUtf8 = java.net.URLEncoder.encode(trimmed, "UTF-8")

        val parallelBatches = coroutineScope {
            val tencentDeferred = async {
                val url = "https://smartbox.gtimg.cn/s3/?t=all&q=$encodedUtf8"
                val bytes = executeSearchGetBytes(
                    url = url,
                    headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                        "Referer" to "https://gu.qq.com/"
                    )
                ) ?: return@async emptyList()
                parseTencentSmartbox(String(bytes, Charset.forName("GBK")))
            }

            val sinaDeferred = async {
                val url = "https://suggest3.sinajs.cn/suggest/type=11,12,13,14,15,81&key=$encodedUtf8"
                val bytes = executeSearchGetBytes(
                    url = url,
                    headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                        "Referer" to "https://finance.sina.com.cn/"
                    )
                ) ?: return@async emptyList()
                parseSinaSuggest(String(bytes, Charset.forName("GBK")))
            }

            val eastmoneyDeferred = async {
                val url = "https://searchapi.eastmoney.com/api/suggest/get" +
                    "?input=$encodedUtf8&type=14&token=D43BF722C8E33BDC906FB84D85E726E8&count=20"
                val bytes = executeSearchGetBytes(
                    url = url,
                    headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                        "Referer" to "https://quote.eastmoney.com/"
                    )
                ) ?: return@async emptyList()
                parseEastmoneySuggest(String(bytes, Charsets.UTF_8))
            }

            val directoryDeferred = async {
                val dir = if (cachedAShareDirectory.size >= 50) cachedAShareDirectory else warmUpAShareDirectory()
                filterAndRankSearchItems(trimmed, dir, limit = 25)
            }

            val directCodeDeferred = async {
                if (trimmed.length == 6 && trimmed.all { it.isDigit() }) {
                    val directMarket = inferMarketFromCode(trimmed)
                    val symbol = "$directMarket$trimmed"
                    try {
                        getQuotes(listOf(symbol)).map { q ->
                            StockSearchItem(
                                code = q.code,
                                market = directMarket,
                                symbol = symbol,
                                name = q.name,
                                pinyin = toPinyinInitials(q.name)
                            )
                        }
                    } catch (_: Exception) {
                        emptyList()
                    }
                } else emptyList()
            }

            awaitAll(
                directCodeDeferred,
                tencentDeferred,
                eastmoneyDeferred,
                sinaDeferred,
                directoryDeferred
            )
        }

        val merged = LinkedHashMap<String, StockSearchItem>()
        for (batch in parallelBatches) {
            for (item in batch) {
                val existing = merged[item.code]
                if (existing == null) {
                    val py = item.pinyin.ifBlank { toPinyinInitials(item.name) }
                    merged[item.code] = item.copy(pinyin = py)
                } else if (existing.pinyin.isBlank() && item.pinyin.isNotBlank()) {
                    merged[item.code] = existing.copy(pinyin = item.pinyin)
                }
            }
        }
        return@withContext filterAndRankSearchItems(trimmed, merged.values.toList(), limit = 40)
    }

    fun inferMarketFromCode(code: String): String = when {
        code.startsWith("60") || code.startsWith("68") -> "sh"
        code.startsWith("00") || code.startsWith("30") -> "sz"
        code.startsWith("8") || code.startsWith("4") || code.startsWith("920") -> "bj"
        else -> "sh"
    }

    fun parseTencentSmartbox(rawText: String): List<StockSearchItem> {
        val hint = rawText.substringAfter("v_hint=\"", "").substringBefore("\"")
        if (hint.isEmpty() || hint == "N") return emptyList()
        val results = mutableListOf<StockSearchItem>()
        val seen = mutableSetOf<String>()
        for (item in hint.split("^")) {
            val parts = item.split("~")
            if (parts.size >= 3) {
                val market = parts[0].trim().lowercase()
                val code = parts[1].trim()
                val rawName = decodeUnicodeEscapes(parts[2].trim())
                val pinyinRaw = if (parts.size >= 4) parts[3].trim().lowercase() else ""
                if (market in listOf("sh", "sz", "bj") && code.length == 6 && code.all { it.isDigit() } && seen.add(code)) {
                    val pinyin = pinyinRaw.ifBlank { toPinyinInitials(rawName) }
                    results.add(
                        StockSearchItem(
                            code = code,
                            market = market,
                            symbol = "$market$code",
                            name = rawName,
                            pinyin = pinyin
                        )
                    )
                }
            }
        }
        return results
    }

    fun parseSinaSuggest(rawText: String): List<StockSearchItem> {
        val rawData = rawText.substringAfter("suggestvalue=\"", "").substringBefore("\"")
        if (rawData.isEmpty()) return emptyList()
        val results = mutableListOf<StockSearchItem>()
        val seen = mutableSetOf<String>()
        for (entry in rawData.split(";")) {
            val parts = entry.split(",")
            if (parts.size >= 5) {
                val typeCode = parts[1].trim()
                val code = parts[2].trim()
                val symbol = parts[3].trim().lowercase()
                val rawName = decodeUnicodeEscapes(parts[4].trim().ifBlank { parts[0].trim() })
                val market = when {
                    symbol.startsWith("sh") -> "sh"
                    symbol.startsWith("sz") -> "sz"
                    symbol.startsWith("bj") -> "bj"
                    else -> ""
                }
                val isAStockOrIndex = typeCode in listOf("11", "12", "13", "14", "15", "81", "")
                if (market.isNotEmpty() && isAStockOrIndex && code.length == 6 && code.all { it.isDigit() } && seen.add(code)) {
                    val pinyin = parts.getOrNull(5)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it.all { ch -> ch in 'a'..'z' || ch in '0'..'9' } }
                        ?: toPinyinInitials(rawName)
                    results.add(
                        StockSearchItem(
                            code = code,
                            market = market,
                            symbol = if (symbol.length >= 8) symbol else "$market$code",
                            name = rawName,
                            pinyin = pinyin
                        )
                    )
                }
            }
        }
        return results
    }

    fun parseEastmoneySuggest(jsonText: String): List<StockSearchItem> {
        return try {
            val root = org.json.JSONObject(jsonText)
            val dataArr = root.optJSONObject("QuotationCodeTable")?.optJSONArray("Data") ?: return emptyList()
            val results = mutableListOf<StockSearchItem>()
            val seen = mutableSetOf<String>()
            for (i in 0 until dataArr.length()) {
                val obj = dataArr.optJSONObject(i) ?: continue
                val code = obj.optString("Code").trim()
                val name = obj.optString("Name").trim()
                val mktNum = obj.optString("MktNum").trim()
                val classify = obj.optString("Classify").trim()
                val securityType = obj.optString("SecurityTypeName").trim()
                val isAShare = classify.equals("AStock", ignoreCase = true) ||
                    securityType.contains("A") ||
                    securityType.contains("科创") ||
                    securityType.contains("创业")
                if (!isAShare && mktNum !in listOf("0", "1")) continue
                if (code.length != 6 || !code.all { it.isDigit() } || name.isEmpty() || !seen.add(code)) continue

                val market = when {
                    mktNum == "1" -> "sh"
                    mktNum == "0" && (code.startsWith("8") || code.startsWith("4") || code.startsWith("920")) -> "bj"
                    mktNum == "0" -> "sz"
                    else -> inferMarketFromCode(code)
                }
                val rawPy = obj.optString("PinYin").trim().lowercase()
                val pinyin = rawPy.ifBlank { toPinyinInitials(name) }
                results.add(
                    StockSearchItem(
                        code = code,
                        market = market,
                        symbol = "$market$code",
                        name = name,
                        pinyin = pinyin
                    )
                )
            }
            results
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parseEastmoneyDirectoryPage(jsonText: String): Pair<Int, List<StockSearchItem>> {
        return try {
            val root = org.json.JSONObject(jsonText)
            val dataObj = root.optJSONObject("data") ?: return 0 to emptyList()
            val total = dataObj.optInt("total", 0)
            val diffArr = dataObj.optJSONArray("diff") ?: return total to emptyList()
            val list = ArrayList<StockSearchItem>(diffArr.length())
            val seen = HashSet<String>(diffArr.length())
            for (i in 0 until diffArr.length()) {
                val obj = diffArr.optJSONObject(i) ?: continue
                val code = obj.optString("f12").trim()
                val mktInt = obj.optInt("f13", -1)
                val name = obj.optString("f14").trim()
                if (code.length != 6 || !code.all { it.isDigit() } || name.isEmpty() || !seen.add(code)) continue
                val market = when {
                    mktInt == 1 -> "sh"
                    mktInt == 0 && (code.startsWith("8") || code.startsWith("4") || code.startsWith("920")) -> "bj"
                    mktInt == 0 -> "sz"
                    else -> inferMarketFromCode(code)
                }
                list.add(
                    StockSearchItem(
                        code = code,
                        market = market,
                        symbol = "$market$code",
                        name = name,
                        pinyin = toPinyinInitials(name)
                    )
                )
            }
            total to list
        } catch (_: Exception) {
            0 to emptyList()
        }
    }

    fun parseEastmoneyDirectory(jsonText: String): List<StockSearchItem> {
        return parseEastmoneyDirectoryPage(jsonText).second
    }

    fun parseSinaDirectoryArray(jsonText: String): List<StockSearchItem> {
        return try {
            val trimmed = jsonText.trim()
            if (!trimmed.startsWith("[")) return emptyList()
            val arr = org.json.JSONArray(trimmed)
            val list = ArrayList<StockSearchItem>(arr.length())
            val seen = HashSet<String>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val symbol = obj.optString("symbol").trim().lowercase()
                val rawCode = obj.optString("code").trim()
                val code = if (rawCode.length == 6 && rawCode.all { it.isDigit() }) rawCode else symbol.takeLast(6)
                val name = obj.optString("name").trim()
                if (code.length != 6 || !code.all { it.isDigit() } || name.isEmpty() || !seen.add(code)) continue
                val market = when {
                    symbol.startsWith("sh") -> "sh"
                    symbol.startsWith("sz") -> "sz"
                    symbol.startsWith("bj") -> "bj"
                    else -> inferMarketFromCode(code)
                }
                list.add(
                    StockSearchItem(
                        code = code,
                        market = market,
                        symbol = "$market$code",
                        name = name,
                        pinyin = toPinyinInitials(name)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun filterAndRankSearchItems(
        query: String,
        candidates: List<StockSearchItem>,
        limit: Int = 40
    ): List<StockSearchItem> {
        val rawQ = query.trim()
        if (rawQ.isEmpty()) return candidates.take(limit)
        val normQ = rawQ.lowercase().replace(" ", "")
        val scored = ArrayList<Pair<Int, StockSearchItem>>()
        for (item in candidates) {
            val normName = item.name.lowercase().replace(" ", "")
            val normCode = item.code.lowercase()
            val normPy = item.pinyin.lowercase().replace(" ", "")
            val normSym = item.symbol.lowercase()
            val score = when {
                normCode == normQ || normSym == normQ -> 100
                normName == normQ -> 98
                normPy.isNotEmpty() && normPy == normQ -> 96
                normCode.startsWith(normQ) -> 90
                normName.startsWith(normQ) -> 88
                normPy.isNotEmpty() && normPy.startsWith(normQ) -> 85
                normSym.startsWith(normQ) -> 80
                normName.contains(normQ) -> 70
                normCode.contains(normQ) -> 68
                normPy.isNotEmpty() && normPy.contains(normQ) -> 65
                normSym.contains(normQ) -> 60
                else -> -1
            }
            if (score > 0) {
                scored.add(score to item)
            }
        }
        return scored.sortedByDescending { it.first }
            .map { it.second }
            .distinctBy { it.code }
            .take(limit)
    }

    fun decodeUnicodeEscapes(input: String): String {
        if (!input.contains("\\u")) return input
        val sb = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '\\' && i + 5 < input.length && (input[i + 1] == 'u' || input[i + 1] == 'U')) {
                val hex = input.substring(i + 2, i + 6)
                val codePoint = hex.toIntOrNull(16)
                if (codePoint != null) {
                    sb.append(codePoint.toChar())
                    i += 6
                    continue
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private val level2PinyinOverrides = mapOf(
        '锂' to 'l', '钴' to 'g', '钼' to 'm', '钛' to 't', '钨' to 'w',
        '镍' to 'n', '铂' to 'b', '稀' to 'x', '芯' to 'x', '晟' to 's',
        '睿' to 'r', '曜' to 'y', '昊' to 'h', '鑫' to 'x', '淼' to 'm',
        '榕' to 'r', '沪' to 'h', '渝' to 'y', '粤' to 'y', '湘' to 'x',
        '赣' to 'g', '闽' to 'm', '皖' to 'w', '浙' to 'z', '晋' to 'j',
        '冀' to 'j', '豫' to 'y', '鄂' to 'e', '蜀' to 's', '滇' to 'd',
        '黔' to 'q', '迈' to 'm', '恒' to 'h', '汇' to 'h', '创' to 'c',
        '鼎' to 'd', '旭' to 'x', '翔' to 'x', '锐' to 'r', '拓' to 't',
        '欣' to 'x', '捷' to 'j', '诺' to 'n', '佳' to 'j', '格' to 'g',
        '铠' to 'k', '钜' to 'j', '钠' to 'n', '钒' to 'f', '锰' to 'm',
        '铬' to 'g', '锑' to 't', '镓' to 'j', '锗' to 'z', '铟' to 'y',
        '璞' to 'p', '昀' to 'y', '祺' to 'q', '孚' to 'f', '兖' to 'y',
        '潞' to 'l', '麒' to 'q', '麟' to 'l', '瀚' to 'h', '宸' to 'c'
    )

    private val gb2312BoundaryCodes = intArrayOf(
        0xB0A1, 0xB0C5, 0xB2C1, 0xB4EE, 0xB6EA, 0xB7A2, 0xB8C1, 0xB9FE,
        0xBBF7, 0xBFA6, 0xC0AC, 0xC2E8, 0xC4C3, 0xC5B6, 0xC5BE, 0xC6DA,
        0xC8BB, 0xC8F6, 0xCBFA, 0xCDDA, 0xCEF4, 0xD1B9, 0xD4D1
    )
    private val gb2312InitialLetters = charArrayOf(
        'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h',
        'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q',
        'r', 's', 't', 'w', 'x', 'y', 'z'
    )

    fun toPinyinInitials(name: String): String {
        if (name.isBlank()) return ""
        val sb = StringBuilder(name.length)
        val gbk = Charset.forName("GBK")
        for (ch in name) {
            when {
                ch in 'a'..'z' || ch in '0'..'9' -> sb.append(ch)
                ch in 'A'..'Z' -> sb.append(ch.lowercaseChar())
                level2PinyinOverrides.containsKey(ch) -> sb.append(level2PinyinOverrides[ch])
                ch.code >= 0x4E00 -> {
                    val bytes = ch.toString().toByteArray(gbk)
                    if (bytes.size == 2) {
                        val b1 = bytes[0].toInt() and 0xFF
                        val b2 = bytes[1].toInt() and 0xFF
                        val code = (b1 shl 8) or b2
                        if (code in 0xB0A1..0xD7F9) {
                            var matched = ' '
                            for (idx in gb2312BoundaryCodes.indices.reversed()) {
                                if (code >= gb2312BoundaryCodes[idx]) {
                                    matched = gb2312InitialLetters[idx]
                                    break
                                }
                            }
                            if (matched != ' ') sb.append(matched)
                        }
                    }
                }
            }
        }
        return sb.toString()
    }


    internal fun parseTencentQuotes(data: String): List<StockQuote> {
        val list = mutableListOf<StockQuote>()
        val lines = data.split(";\n", ";")
        for (line in lines) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("v_")) continue
            val parts = trimmed.split("=")
            if (parts.size < 2) continue
            val symbol = parts[0].substringAfter("v_").trim()
            val raw = parts[1].trim('"', ';')
            val fields = raw.split("~")
            if (fields.size < 46) continue

            val name = (fields.getOrNull(1) ?: symbol).trim()
            val code = (fields.getOrNull(2) ?: symbol.takeLast(6)).trim()
            val price = fields.getOrNull(3)?.toDoubleOrNull() ?: 0.0
            val yesterdayClose = fields.getOrNull(4)?.toDoubleOrNull() ?: price
            val open = fields.getOrNull(5)?.toDoubleOrNull() ?: price
            val volumeLots = fields.getOrNull(6)?.toLongOrNull() ?: 0L
            val change = fields.getOrNull(31)?.toDoubleOrNull() ?: (price - yesterdayClose)
            val changePercent = fields.getOrNull(32)?.toDoubleOrNull()
                ?: if (yesterdayClose > 0.0 && price > 0.0) ((price - yesterdayClose) / yesterdayClose) * 100.0 else 0.0
            val rawHigh = fields.getOrNull(33)?.toDoubleOrNull() ?: price
            val rawLow = fields.getOrNull(34)?.toDoubleOrNull() ?: price
            val safeHigh = if (rawHigh > 0.0) rawHigh else maxOf(price, open.takeIf { it > 0.0 } ?: price)
            val safeLow = if (rawLow > 0.0) rawLow else minOf(price, open.takeIf { it > 0.0 } ?: price)
            val turnover = fields.getOrNull(37)?.toDoubleOrNull() ?: 0.0
            val turnoverRate = fields.getOrNull(38)?.toDoubleOrNull() ?: 0.0
            val peTtm = fields.getOrNull(39)?.toDoubleOrNull() ?: 0.0
            val rawAmplitude = fields.getOrNull(43)?.toDoubleOrNull() ?: 0.0
            val safeAmplitude = if (rawAmplitude > 0.0) {
                rawAmplitude
            } else if (yesterdayClose > 0.0 && safeHigh > safeLow) {
                Math.round(((safeHigh - safeLow) / yesterdayClose) * 10000.0) / 100.0
            } else 0.0
            val circMarketCap = fields.getOrNull(44)?.toDoubleOrNull() ?: 0.0
            val totalMarketCap = fields.getOrNull(45)?.toDoubleOrNull() ?: circMarketCap
            val pb = fields.getOrNull(46)?.toDoubleOrNull() ?: 0.0

            // 5档盘口
            val bids = mutableListOf<OrderBookTier>()
            val asks = mutableListOf<OrderBookTier>()
            // 买一到买五: 9-18
            for (i in 0 until 5) {
                val p = fields.getOrNull(9 + i * 2)?.toDoubleOrNull() ?: 0.0
                val v = fields.getOrNull(10 + i * 2)?.toIntOrNull() ?: 0
                if (p > 0) bids.add(OrderBookTier(p, v))
            }
            // 卖一到卖五: 19-28
            for (i in 0 until 5) {
                val p = fields.getOrNull(19 + i * 2)?.toDoubleOrNull() ?: 0.0
                val v = fields.getOrNull(20 + i * 2)?.toIntOrNull() ?: 0
                if (p > 0) asks.add(OrderBookTier(p, v))
            }

            val parsedMarket = when {
                symbol.startsWith("sh") -> "sh"
                symbol.startsWith("bj") -> "bj"
                else -> "sz"
            }

            list.add(
                StockQuote(
                    code = code,
                    market = parsedMarket,
                    symbol = symbol,
                    name = name,
                    price = price,
                    yesterdayClose = yesterdayClose,
                    open = open,
                    change = change,
                    changePercent = changePercent,
                    high = safeHigh,
                    low = safeLow,
                    volumeLots = volumeLots,
                    turnoverTenThousand = turnover,
                    turnoverRate = turnoverRate,
                    peTtm = peTtm,
                    pb = pb,
                    totalMarketCapHundredMillion = totalMarketCap,
                    circMarketCapHundredMillion = circMarketCap,
                    amplitude = safeAmplitude,
                    orderBook = OrderBook(bids = bids, asks = asks),
                    timestamp = fields.getOrNull(30) ?: ""
                )
            )
        }
        return list
    }

    /**
     * 获取腾讯真实分时行情数据 (09:30 - 15:00 逐分钟价格、VWAP均价与成交量)
     */
    suspend fun getIntradayMinutes(symbol: String): List<com.example.data.model.MinutePoint> = withContext(Dispatchers.IO) {
        val url = "https://web.ifzq.gtimg.cn/appstock/app/minute/query?code=$symbol"
        try {
            val request = Request.Builder().url(url).build()
            val text = client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            } ?: return@withContext emptyList()
            parseTencentMinuteData(text, symbol)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseTencentMinuteData(jsonStr: String, symbol: String): List<com.example.data.model.MinutePoint> {
        val list = mutableListOf<com.example.data.model.MinutePoint>()
        try {
            val root = org.json.JSONObject(jsonStr)
            val codeObj = root.optJSONObject("data")?.optJSONObject(symbol) ?: return emptyList()
            val dataObj = codeObj.optJSONObject("data") ?: return emptyList()
            val dataArr = dataObj.optJSONArray("data") ?: return emptyList()

            var prevVol = 0L
            for (i in 0 until dataArr.length()) {
                val line = dataArr.optString(i) ?: continue
                val parts = line.split(" ")
                if (parts.size >= 4) {
                    val rawTime = parts[0]
                    val formattedTime = if (rawTime.length >= 4) {
                        "${rawTime.substring(0, 2)}:${rawTime.substring(2, 4)}"
                    } else rawTime

                    val price = parts[1].toDoubleOrNull() ?: continue
                    val cumVolLots = parts[2].toLongOrNull() ?: 0L
                    val incVolLots = if (i == 0) cumVolLots else (cumVolLots - prevVol).coerceAtLeast(0L)
                    prevVol = cumVolLots

                    val turnover = parts[3].toDoubleOrNull() ?: 0.0
                    val vwap = if (cumVolLots > 0L) {
                        (turnover / (cumVolLots * 100.0)).coerceAtLeast(0.01)
                    } else price

                    list.add(
                        com.example.data.model.MinutePoint(
                            time = formattedTime,
                            price = price,
                            averagePrice = vwap,
                            volumeLots = incVolLots,
                            turnover = turnover
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Handled gracefully
        }
        return list
    }

    /**
     * 获取多周期真实前复权K线数据 (支持 "day"日K, "week"周K, "month"月K)
     * 3级容错链路：腾讯前复权 -> 东方财富前复权K线 -> 新浪历史日K聚合
     */
    suspend fun getMultiPeriodKLines(symbol: String, period: String = "day", count: Int = 140): List<KLinePoint> = withContext(Dispatchers.IO) {
        val url = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=$symbol,$period,,,$count,qfq"
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://gu.qq.com/")
                .build()
            val text = client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() ?: "" else ""
            }
            val points = if (text.isNotEmpty()) parseTencentKLines(text, symbol, period) else emptyList()
            if (points.isNotEmpty()) {
                return@withContext calculateIndicators(sanitizeKLines(points))
            }
        } catch (_: Exception) {
            // Fall through to Eastmoney K-line feed
        }

        // Fallback 2: 东方财富前复权 K 线接口 (klt: 101=日, 102=周, 103=月)
        val emPoints = fetchEastmoneyKLines(symbol, period, count)
        if (emPoints.isNotEmpty()) {
            return@withContext calculateIndicators(sanitizeKLines(emPoints))
        }

        // Fallback 3: 新浪日K并按周/月聚合
        val dailyFallback = getDailyKLines(symbol, datalen = 220)
        if (dailyFallback.isEmpty()) return@withContext emptyList()
        when (period) {
            "day" -> dailyFallback
            "week", "month" -> aggregateDailyToPeriod(dailyFallback, period)
            else -> dailyFallback
        }
    }

    private fun fetchEastmoneyKLines(symbol: String, period: String, count: Int): List<KLinePoint> {
        val lower = symbol.lowercase()
        val code = lower.takeLast(6)
        if (code.length != 6 || !code.all { it.isDigit() }) return emptyList()
        val secIdPrefix = if (lower.startsWith("sh")) "1" else "0"
        val klt = when (period) {
            "week" -> "102"
            "month" -> "103"
            else -> "101"
        }
        val url = "https://push2his.eastmoney.com/api/qt/stock/kline/get" +
            "?secid=$secIdPrefix.$code&fields1=f1,f2,f3,f4,f5,f6" +
            "&fields2=f51,f52,f53,f54,f55,f56,f57,f58&klt=$klt&fqt=1&end=20500101&lmt=$count"
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://quote.eastmoney.com/")
                .build()
            val body = client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            } ?: return emptyList()
            val klinesArr = org.json.JSONObject(body)
                .optJSONObject("data")
                ?.optJSONArray("klines") ?: return emptyList()
            val list = ArrayList<KLinePoint>(klinesArr.length())
            for (i in 0 until klinesArr.length()) {
                val parts = klinesArr.optString(i).split(",")
                if (parts.size >= 6) {
                    val date = parts[0].trim()
                    val open = parts[1].toDoubleOrNull() ?: Double.NaN
                    val close = parts[2].toDoubleOrNull() ?: Double.NaN
                    val high = parts[3].toDoubleOrNull() ?: Double.NaN
                    val low = parts[4].toDoubleOrNull() ?: Double.NaN
                    val vol = parts[5].toDoubleOrNull() ?: 0.0
                    buildSanitizedBar(date, open, close, high, low, vol)?.let { list.add(it) }
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun sanitizeKLines(raw: List<KLinePoint>): List<KLinePoint> {
        if (raw.isEmpty()) return emptyList()
        val dedup = LinkedHashMap<String, KLinePoint>(raw.size)
        for (p in raw) {
            val clean = buildSanitizedBar(p.date, p.open, p.close, p.high, p.low, p.volume) ?: continue
            dedup[clean.date] = clean
        }
        return dedup.values.sortedBy { it.date }
    }

    private fun buildSanitizedBar(
        date: String,
        open: Double,
        close: Double,
        high: Double,
        low: Double,
        volume: Double
    ): KLinePoint? {
        if (date.isBlank() || !close.isFinite() || close <= 0.0) return null
        val safeOpen = if (open.isFinite() && open > 0.0) open else close
        val rawHigh = if (high.isFinite() && high > 0.0) high else close
        val rawLow = if (low.isFinite() && low > 0.0) low else close
        val safeHigh = maxOf(rawHigh, safeOpen, close)
        val safeLow = minOf(rawLow, safeOpen, close).coerceAtLeast(0.01)
        val safeVol = if (volume.isFinite() && volume >= 0.0) volume else 0.0
        return KLinePoint(
            date = date.trim().take(10),
            open = safeOpen,
            close = close,
            high = safeHigh,
            low = safeLow,
            volume = safeVol
        )
    }

    /**
     * 将日K序列最后一根K线与实时盘口最新成交价对齐（防止盘中或刚收盘时日线滞后于分时/现价）
     */
    fun reconcileDailyKLinesWithLiveQuote(
        dailyKLines: List<KLinePoint>,
        quote: StockQuote?
    ): List<KLinePoint> {
        if (dailyKLines.isEmpty() || quote == null || !quote.price.isFinite() || quote.price <= 0.0) {
            return dailyKLines
        }
        val quoteDate = extractDateFromTimestamp(quote.timestamp) ?: return dailyKLines
        val last = dailyKLines.last()
        val liveOpen = if (quote.open.isFinite() && quote.open > 0.0) quote.open else quote.price
        val liveHigh = maxOf(quote.high.takeIf { it.isFinite() && it > 0.0 } ?: quote.price, liveOpen, quote.price)
        val liveLow = minOf(quote.low.takeIf { it.isFinite() && it > 0.0 } ?: quote.price, liveOpen, quote.price).coerceAtLeast(0.01)
        val liveVol = quote.volumeLots.toDouble().coerceAtLeast(0.0)

        val updatedRaw = when {
            last.date == quoteDate -> {
                dailyKLines.dropLast(1) + last.copy(
                    open = liveOpen,
                    close = quote.price,
                    high = maxOf(last.high, liveHigh),
                    low = minOf(last.low, liveLow),
                    volume = if (liveVol > 0.0) maxOf(last.volume, liveVol) else last.volume
                )
            }
            quoteDate > last.date && quote.volumeLots > 0L -> {
                dailyKLines + KLinePoint(
                    date = quoteDate,
                    open = liveOpen,
                    close = quote.price,
                    high = liveHigh,
                    low = liveLow,
                    volume = liveVol
                )
            }
            else -> return dailyKLines
        }
        return calculateIndicators(updatedRaw)
    }

    private fun extractDateFromTimestamp(ts: String): String? {
        val digits = ts.filter { it.isDigit() }
        if (digits.length >= 8) {
            val y = digits.substring(0, 4).toIntOrNull()
            val m = digits.substring(4, 6).toIntOrNull()
            val d = digits.substring(6, 8).toIntOrNull()
            if (y != null && y in 2020..2099 && m != null && m in 1..12 && d != null && d in 1..31) {
                return "${digits.substring(0, 4)}-${digits.substring(4, 6)}-${digits.substring(6, 8)}"
            }
        }
        val match = Regex("""(20\d{2}-\d{2}-\d{2})""").find(ts)
        return match?.groupValues?.getOrNull(1)
    }

    fun aggregateDailyToPeriod(daily: List<KLinePoint>, period: String): List<KLinePoint> {
        if (daily.isEmpty()) return emptyList()
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val cal = java.util.Calendar.getInstance(java.util.Locale.US).apply {
            firstDayOfWeek = java.util.Calendar.MONDAY
            minimalDaysInFirstWeek = 4
        }
        val grouped = LinkedHashMap<String, MutableList<KLinePoint>>()
        for (bar in daily) {
            val datePrefix = bar.date.take(10)
            val key = if (period == "month") {
                datePrefix.take(7)
            } else {
                try {
                    val d = sdf.parse(datePrefix)
                    if (d != null) {
                        cal.time = d
                        val weekYear = cal.weekYear
                        val weekNum = cal.get(java.util.Calendar.WEEK_OF_YEAR)
                        String.format(java.util.Locale.US, "%04d-W%02d", weekYear, weekNum)
                    } else datePrefix.take(7)
                } catch (_: Exception) {
                    datePrefix.take(7)
                }
            }
            grouped.getOrPut(key) { mutableListOf() }.add(bar)
        }
        val rawPeriods = grouped.values.map { bars ->
            KLinePoint(
                date = bars.last().date,
                open = bars.first().open,
                close = bars.last().close,
                high = bars.maxOf { it.high },
                low = bars.minOf { it.low },
                volume = bars.sumOf { it.volume }
            )
        }
        return calculateIndicators(rawPeriods)
    }

    private fun parseTencentKLines(jsonStr: String, symbol: String, period: String): List<KLinePoint> {
        val list = mutableListOf<KLinePoint>()
        try {
            val root = org.json.JSONObject(jsonStr)
            val codeObj = root.optJSONObject("data")?.optJSONObject(symbol) ?: return emptyList()
            val key = "qfq$period"
            val array = codeObj.optJSONArray(key) ?: codeObj.optJSONArray(period) ?: return emptyList()

            for (i in 0 until array.length()) {
                val item = array.opt(i)
                if (item is org.json.JSONArray && item.length() >= 6) {
                    val date = item.optString(0)
                    val open = item.optDouble(1)
                    val close = item.optDouble(2)
                    val high = item.optDouble(3)
                    val low = item.optDouble(4)
                    val vol = item.optDouble(5)

                    buildSanitizedBar(date, open, close, high, low, vol)?.let { list.add(it) }
                }
            }
        } catch (_: Exception) {
            // Handled gracefully
        }
        return list
    }

    /**
     * 计算符合国际金融工程金标准 (TA-Lib / TradingView / 同花顺 / Wind) 的技术指标系统:
     * - SMA5, SMA10, SMA20, SMA60
     * - 布林带 BOLL(20, 2): 中轨=SMA20, 上下轨=SMA20 ± 2 * StdDev20
     * - 标准 MACD(12, 26, 9): DIF = EMA12 - EMA26, DEA = EMA9(DIF), 柱状体 BAR = 2 * (DIF - DEA)
     * - Wilder 标准 RSI(6): 基于 6 周期 SMMA 平滑涨跌幅
     */
    fun calculateIndicators(raw: List<KLinePoint>): List<KLinePoint> {
        if (raw.isEmpty()) return emptyList()
        val closes = ArrayList<Double>(raw.size)
        val result = ArrayList<KLinePoint>(raw.size)

        val alpha12 = 2.0 / (12.0 + 1.0)
        val alpha26 = 2.0 / (26.0 + 1.0)
        val alpha9 = 2.0 / (9.0 + 1.0)

        var ema12 = raw.first().close
        var ema26 = raw.first().close
        var dea = 0.0

        // Wilder RSI(6) state
        var initGainSum = 0.0
        var initLossSum = 0.0
        var avgGain = 0.0
        var avgLoss = 0.0
        val rsiPeriod = 6

        for (i in raw.indices) {
            val p = raw[i]
            closes.add(p.close)

            val ma5 = if (closes.size >= 5) closes.takeLast(5).average() else closes.average()
            val ma10 = if (closes.size >= 10) closes.takeLast(10).average() else closes.average()
            val ma20 = if (closes.size >= 20) closes.takeLast(20).average() else closes.average()
            val ma60 = if (closes.size >= 60) closes.takeLast(60).average() else closes.average()

            // 20周期布林带 (Sample/Window StdDev)
            val std = if (closes.size >= 20) {
                val window = closes.takeLast(20)
                val mean = ma20
                val variance = window.sumOf { (it - mean) * (it - mean) } / 20.0
                kotlin.math.sqrt(variance)
            } else 0.0
            val bollUpper = ma20 + 2.0 * std
            val bollLower = ma20 - 2.0 * std

            // 标准 MACD(12, 26, 9)
            if (i == 0) {
                ema12 = p.close
                ema26 = p.close
                dea = 0.0
            } else {
                ema12 = alpha12 * p.close + (1.0 - alpha12) * ema12
                ema26 = alpha26 * p.close + (1.0 - alpha26) * ema26
            }
            val dif = ema12 - ema26
            dea = if (i == 0) dif else (alpha9 * dif + (1.0 - alpha9) * dea)
            val macdBar = 2.0 * (dif - dea)

            // Wilder 标准 RSI(6)
            val rsi6 = if (i == 0) {
                50.0
            } else {
                val delta = p.close - raw[i - 1].close
                val gain = if (delta > 0.0) delta else 0.0
                val loss = if (delta < 0.0) -delta else 0.0

                when {
                    i < rsiPeriod -> {
                        initGainSum += gain
                        initLossSum += loss
                        val curAvgGain = initGainSum / i
                        val curAvgLoss = initLossSum / i
                        if (curAvgLoss == 0.0) (if (curAvgGain == 0.0) 50.0 else 100.0)
                        else 100.0 - 100.0 / (1.0 + curAvgGain / curAvgLoss)
                    }
                    i == rsiPeriod -> {
                        initGainSum += gain
                        initLossSum += loss
                        avgGain = initGainSum / rsiPeriod
                        avgLoss = initLossSum / rsiPeriod
                        if (avgLoss == 0.0) (if (avgGain == 0.0) 50.0 else 100.0)
                        else 100.0 - 100.0 / (1.0 + avgGain / avgLoss)
                    }
                    else -> {
                        avgGain = (avgGain * (rsiPeriod - 1) + gain) / rsiPeriod
                        avgLoss = (avgLoss * (rsiPeriod - 1) + loss) / rsiPeriod
                        if (avgLoss == 0.0) (if (avgGain == 0.0) 50.0 else 100.0)
                        else 100.0 - 100.0 / (1.0 + avgGain / avgLoss)
                    }
                }
            }

            result.add(
                p.copy(
                    ma5 = ma5,
                    ma10 = ma10,
                    ma20 = ma20,
                    ma60 = ma60,
                    bollUpper = bollUpper,
                    bollMid = ma20,
                    bollLower = bollLower,
                    macdDif = dif,
                    macdDea = dea,
                    macdBar = macdBar,
                    rsi6 = rsi6
                )
            )
        }
        return result
    }

    /**
     * 新浪日K线数据备用接口 (前复权)
     */
    suspend fun getDailyKLines(symbol: String, scale: Int = 240, datalen: Int = 90): List<KLinePoint> = withContext(Dispatchers.IO) {
        val url = "https://quotes.sina.cn/cn/api/jsonp_v2.php/var%20_${symbol}_${scale}_=/CN_MarketDataService.getKLineData?symbol=$symbol&scale=$scale&ma=no&datalen=$datalen"
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://finance.sina.com.cn/")
                .build()
            val text = client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            } ?: return@withContext emptyList()
            parseSinaKLines(text)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseSinaKLines(raw: String): List<KLinePoint> {
        val jsonStart = raw.indexOf("(")
        val jsonEnd = raw.lastIndexOf(")")
        if (jsonStart < 0 || jsonEnd <= jsonStart) return emptyList()
        val json = raw.substring(jsonStart + 1, jsonEnd).trim()
        if (!json.startsWith("[")) return emptyList()

        val rawPoints = mutableListOf<KLinePoint>()
        val itemPattern = java.util.regex.Pattern.compile("\\{([^}]+)\\}")
        val matcher = itemPattern.matcher(json)

        while (matcher.find()) {
            val content = matcher.group(1) ?: continue
            var day = ""
            var open = 0.0
            var high = 0.0
            var low = 0.0
            var close = 0.0
            var volume = 0.0

            for (pair in content.split(",")) {
                val kv = pair.split(":")
                if (kv.size < 2) continue
                val k = kv[0].trim('"', ' ')
                val v = kv[1].trim('"', ' ')
                when (k) {
                    "day" -> day = v
                    "open" -> open = v.toDoubleOrNull() ?: 0.0
                    "high" -> high = v.toDoubleOrNull() ?: 0.0
                    "low" -> low = v.toDoubleOrNull() ?: 0.0
                    "close" -> close = v.toDoubleOrNull() ?: 0.0
                    "volume" -> volume = (v.toDoubleOrNull() ?: 0.0) / 100.0 // 新浪按股计，统一换算为手
                }
            }
            buildSanitizedBar(day, open, close, high, low, volume)?.let { rawPoints.add(it) }
        }
        return calculateIndicators(sanitizeKLines(rawPoints))
    }

    /**
     * 东方财富数据中心 F10 真实财报三表穿透直连：
     * - 利润表/主要指标 (RPT_F10_FINANCE_MAINFINADATA): 营收、归母净利、扣非净利、ROIC、ROE、毛利率、资产负债率
     * - 现金流量表 (RPT_DMSK_FN_CASHFLOW): 经营活动现金流净额 (OCF)、购建长期资产资本开支 (Capex)
     * - 资产负债表 (RPT_DMSK_FN_BALANCE): 货币资金、有息负债(短借/一年内到期/长借/应付债券)、应收账款、存货、合同负债、商誉
     */
    suspend fun getFinancialFundamentals(symbol: String): com.example.data.model.FinancialStatementSnapshot? = withContext(Dispatchers.IO) {
        val lower = symbol.lowercase()
        // 指数代码无个股三表数据
        if (lower in setOf("sh000001", "sh000300", "sz399006", "sh000688", "sh000016", "sz399001")) {
            return@withContext null
        }
        val code = lower.takeLast(6)
        if (code.length != 6 || !code.all { it.isDigit() }) return@withContext null
        val suffix = when {
            lower.startsWith("sh") -> "SH"
            lower.startsWith("sz") -> "SZ"
            lower.startsWith("bj") -> "BJ"
            else -> return@withContext null
        }
        val secuCode = "$code.$suffix"

        try {
            val mainData = fetchEastmoneyReport(
                reportName = "RPT_F10_FINANCE_MAINFINADATA",
                filter = "(SECUCODE=\"$secuCode\")",
                pageSize = 6
            ) ?: return@withContext null

            val cashData = fetchEastmoneyReport(
                reportName = "RPT_DMSK_FN_CASHFLOW",
                filter = "(SECUCODE=\"$secuCode\")",
                pageSize = 6
            ).orEmpty()

            val balanceData = fetchEastmoneyReport(
                reportName = "RPT_DMSK_FN_BALANCE",
                filter = "(SECUCODE=\"$secuCode\")",
                pageSize = 2
            ).orEmpty()

            if (mainData.isEmpty()) return@withContext null

            // 时期数（营收/利润/OCF/Capex）优先选取最近一期完整年报（12-31），避免单季度波动失真；无年报则按季报比例年化
            val latestMain = mainData.first()
            val annualMain = mainData.firstOrNull { obj ->
                obj.optString("REPORT_DATE").contains("-12-31")
            } ?: latestMain

            val flowDatePrefix = annualMain.optString("REPORT_DATE").take(10)
            val annualFactor = when {
                flowDatePrefix.endsWith("-12-31") -> 1.0
                flowDatePrefix.endsWith("-09-30") -> 4.0 / 3.0
                flowDatePrefix.endsWith("-06-30") -> 2.0
                flowDatePrefix.endsWith("-03-31") -> 4.0
                else -> 1.0
            }

            val matchingCash = cashData.firstOrNull {
                it.optString("REPORT_DATE").take(10) == flowDatePrefix
            } ?: cashData.firstOrNull()
            val cashDatePrefix = matchingCash?.optString("REPORT_DATE")?.take(10) ?: flowDatePrefix
            val cashAnnualFactor = when {
                cashDatePrefix.endsWith("-12-31") -> 1.0
                cashDatePrefix.endsWith("-09-30") -> 4.0 / 3.0
                cashDatePrefix.endsWith("-06-30") -> 2.0
                cashDatePrefix.endsWith("-03-31") -> 4.0
                else -> 1.0
            }

            val toBillion = 1e8 // 元 -> 亿元
            val revenueB = (optSafeDouble(annualMain, "TOTALOPERATEREVE") / toBillion) * annualFactor
            val netProfitB = (optSafeDouble(annualMain, "PARENTNETPROFIT") / toBillion) * annualFactor
            val deductedB = (optSafeDouble(annualMain, "KCFJCXSYJLR") / toBillion) * annualFactor
            val ocfB = if (matchingCash != null) {
                (optSafeDouble(matchingCash, "NETCASH_OPERATE") / toBillion) * cashAnnualFactor
            } else {
                // 若现金流表暂缺，用每股经营现金流 * 非负总股本(|归母净利/EPS|) 推算，避免负利润负每股现金流导致负负得正反转
                val mgjyxj = optSafeDouble(annualMain, "MGJYXJJE")
                val eps = optSafeDouble(annualMain, "EPSJB")
                if (kotlin.math.abs(eps) > 1e-4 && mgjyxj != 0.0) {
                    kotlin.math.abs(netProfitB / eps) * mgjyxj
                } else 0.0
            }
            val capexB = if (matchingCash != null) {
                kotlin.math.abs(optSafeDouble(matchingCash, "CONSTRUCT_LONG_ASSET") / toBillion) * cashAnnualFactor
            } else {
                kotlin.math.max(0.0, revenueB * 0.04)
            }

            // 时点数（货币资金、有息负债、应收、存货、合同负债、商誉、权益）取最新一期资产负债表
            val latestBal = balanceData.firstOrNull()
            val cashReserveB = optSafeDouble(latestBal, "MONETARYFUNDS") / toBillion
            val shortLoanB = optSafeDouble(latestBal, "SHORT_LOAN") / toBillion
            val dueOneYearB = optSafeDouble(latestBal, "NONCURRENT_LIAB_1YEAR") / toBillion
            val longLoanB = optSafeDouble(latestBal, "LONG_LOAN") / toBillion
            val bondPayableB = optSafeDouble(latestBal, "BOND_PAYABLE") / toBillion
            val interestDebtB = shortLoanB + dueOneYearB + longLoanB + bondPayableB
            val netCashB = cashReserveB - interestDebtB

            val accountsReceB = optSafeDouble(latestBal, "ACCOUNTS_RECE") / toBillion
            val inventoryB = optSafeDouble(latestBal, "INVENTORY") / toBillion
            val contractLiabB = (optSafeDouble(latestBal, "CONTRACT_LIAB") + optSafeDouble(latestBal, "ADVANCE_RECEIVABLES")) / toBillion
            val goodwillB = optSafeDouble(latestBal, "GOODWILL") / toBillion
            val parentEquityB = optSafeDouble(latestBal, "TOTAL_PARENT_EQUITY").takeIf { it > 0.0 }?.div(toBillion)
                ?: optSafeDouble(latestBal, "TOTAL_EQUITY").div(toBillion)

            fun extractAnnualizedRatioPct(field: String): Double {
                val primary = optSafeDouble(annualMain, field)
                val (obj, rawVal) = if (primary != 0.0) annualMain to primary else latestMain to optSafeDouble(latestMain, field)
                if (rawVal == 0.0) return 0.0
                val d = obj.optString("REPORT_DATE").take(10)
                val factor = when {
                    d.endsWith("-12-31") -> 1.0
                    d.endsWith("-09-30") -> 4.0 / 3.0
                    d.endsWith("-06-30") -> 2.0
                    d.endsWith("-03-31") -> 4.0
                    else -> 1.0
                }
                return if (factor > 1.0) (rawVal * factor).coerceIn(-80.0, 120.0) else rawVal
            }
            val roicPct = extractAnnualizedRatioPct("ROIC")
            val roePct = extractAnnualizedRatioPct("ROEJQ")
            val grossMarginPct = optSafeDouble(annualMain, "XSMLL").takeIf { it != 0.0 }
                ?: optSafeDouble(latestMain, "XSMLL")
            val debtRatioPct = optSafeDouble(latestMain, "ZCFZL")

            // 维持性资本开支 vs 扩张性资本开支拆分（稳健取营收约 2.8% 或总 Capex 的 55% 作为维持基准上限）
            val maintCapexB = if (capexB > 0.0) {
                kotlin.math.min(capexB, kotlin.math.max(capexB * 0.55, revenueB * 0.025))
            } else 0.0
            val fcfB = ocfB - capexB

            val cashToEarnings = when {
                netProfitB > 0.01 -> ocfB / netProfitB
                netProfitB < -0.01 && ocfB > 0.0 -> 1.0
                else -> 0.0
            }
            val capexToOcfPct = if (ocfB > 0.01) (capexB / ocfB) * 100.0 else if (capexB > 0.0) 100.0 else 0.0

            val reportPeriodName = annualMain.optString("REPORT_DATE_NAME").ifEmpty { flowDatePrefix }
            val wcNote = buildString {
                if (revenueB > 0.01) {
                    val receRatio = (accountsReceB / revenueB) * 100.0
                    val advRatio = (contractLiabB / revenueB) * 100.0
                    if (advRatio >= 8.0 && receRatio <= 10.0) {
                        append("下游先款后货(合同负债占营收${String.format(java.util.Locale.US, "%.1f", advRatio)}%)，强势占用渠道资金")
                    } else if (receRatio >= 28.0) {
                        append("⚠️应收账款占营收${String.format(java.util.Locale.US, "%.1f", receRatio)}%，回款周期偏长")
                    } else {
                        append("应收(${String.format(java.util.Locale.US, "%.1f", receRatio)}%营收)与预收结构健康")
                    }
                } else {
                    append("以最新资产负债表与现金流量表穿透核验")
                }
            }

            if (revenueB <= 0.0 && parentEquityB <= 0.0) return@withContext null

            com.example.data.model.FinancialStatementSnapshot(
                symbol = symbol,
                reportPeriod = reportPeriodName,
                sourceLabel = "东方财富F10财报实盘",
                isLiveAuditedData = true,
                revenueBillion = round2(revenueB),
                netProfitBillion = round2(netProfitB),
                deductedNetProfitBillion = round2(if (deductedB != 0.0) deductedB else netProfitB),
                operatingCashFlowBillion = round2(ocfB),
                capexBillion = round2(capexB),
                maintenanceCapexBillion = round2(maintCapexB),
                freeCashFlowBillion = round2(fcfB),
                monetaryCashBillion = round2(cashReserveB),
                interestBearingDebtBillion = round2(interestDebtB),
                netCashBillion = round2(netCashB),
                accountsReceivableBillion = round2(accountsReceB),
                inventoryBillion = round2(inventoryB),
                contractLiabilityBillion = round2(contractLiabB),
                goodwillBillion = round2(goodwillB),
                equityBillion = round2(parentEquityB),
                roicPercent = round2(roicPct),
                roePercent = round2(roePct),
                grossMarginPercent = round2(grossMarginPct),
                debtToAssetPercent = round2(debtRatioPct),
                cashToEarningsRatio = round2(cashToEarnings),
                capexToOcfPercent = round1(capexToOcfPct),
                workingCapitalQualityNote = wcNote
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchEastmoneyReport(
        reportName: String,
        filter: String,
        pageSize: Int
    ): List<org.json.JSONObject>? {
        val encodedFilter = java.net.URLEncoder.encode(filter, "UTF-8")
        val url = "https://datacenter-web.eastmoney.com/api/data/v1/get" +
            "?reportName=$reportName" +
            "&columns=ALL" +
            "&filter=$encodedFilter" +
            "&pageNumber=1&pageSize=$pageSize" +
            "&sortColumns=REPORT_DATE&sortTypes=-1"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Referer", "https://data.eastmoney.com/")
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        } ?: return null
        val root = org.json.JSONObject(body)
        if (!root.optBoolean("success", false)) return null
        val dataArr = root.optJSONObject("result")?.optJSONArray("data") ?: return null
        val list = ArrayList<org.json.JSONObject>(dataArr.length())
        for (i in 0 until dataArr.length()) {
            dataArr.optJSONObject(i)?.let { list.add(it) }
        }
        return list
    }

    private fun optSafeDouble(obj: org.json.JSONObject?, key: String): Double {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return 0.0
        val v = obj.optDouble(key, Double.NaN)
        return if (v.isNaN() || v.isInfinite()) 0.0 else v
    }

    private fun round1(v: Double): Double = kotlin.math.round(v * 10.0) / 10.0
    private fun round2(v: Double): Double = kotlin.math.round(v * 100.0) / 100.0
}
