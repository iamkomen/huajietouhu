package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.StockQuote
import com.example.data.remote.StockApiService
import com.example.data.repository.StockRepository
import com.example.ui.viewmodel.StockTerminalViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StockSearchRegressionTest {

    @Test
    fun toPinyinInitials_generatesAccurateInitialsForChineseAndMixedNames() {
        assertEquals("jxty", StockApiService.toPinyinInitials("江西铜业"))
        assertEquals("gzmt", StockApiService.toPinyinInitials("贵州茅台"))
        assertEquals("jdfa", StockApiService.toPinyinInitials("京东方A"))
        assertEquals("lymy", StockApiService.toPinyinInitials("洛阳钼业"))
        assertEquals("tqly", StockApiService.toPinyinInitials("天齐锂业"))
    }

    @Test
    fun parseTencentSmartbox_decodesUnicodeEscapesAndFiltersAShares() {
        val payload = """v_hint="sh~600362~\u6c5f\u897f\u94dc\u4e1a~jxty~GP-A^hk~00358~\u6c5f\u897f\u94dc\u4e1a\u80a1\u4efd~jxtygf~GP^sz~300750~宁德时代~ndsd~GP-A-CYB";"""
        val items = StockApiService.parseTencentSmartbox(payload)

        assertEquals(2, items.size)
        assertEquals("600362", items[0].code)
        assertEquals("sh", items[0].market)
        assertEquals("江西铜业", items[0].name)
        assertEquals("jxty", items[0].pinyin)

        assertEquals("300750", items[1].code)
        assertEquals("sz", items[1].market)
        assertEquals("宁德时代", items[1].name)

        assertTrue(StockApiService.parseTencentSmartbox("""v_hint="N";""").isEmpty())
    }

    @Test
    fun parseSinaSuggest_filtersNonAShareWithoutMislabelingBeijing() {
        val payload = """var suggestvalue="江西铜业,11,600362,sh600362,江西铜业,,江西铜业,99,1,,;江西铜业股份,31,00358,hk00358,江西铜业股份,,;京东方Ａ,11,000725,sz000725,京东方Ａ,jdfa;";"""
        val items = StockApiService.parseSinaSuggest(payload)

        assertEquals(2, items.size)
        assertEquals("sh600362", items[0].symbol)
        assertEquals("江西铜业", items[0].name)
        assertEquals("jxty", items[0].pinyin)
        assertFalse(items.any { it.code == "00358" || it.code == "000358" })
        assertEquals("sz000725", items[1].symbol)
    }

    @Test
    fun parseEastmoneySuggestAndDirectory_parseASharesAndGeneratePinyin() {
        val suggestJson = """
            {
              "QuotationCodeTable": {
                "Data": [
                  {"Code": "600362", "Name": "江西铜业", "PinYin": "JXTY", "MktNum": "1", "Classify": "AStock", "SecurityTypeName": "沪A"},
                  {"Code": "00358", "Name": "江西铜业股份", "PinYin": "", "MktNum": "116", "Classify": "HK", "SecurityTypeName": "港股"}
                ]
              }
            }
        """.trimIndent()
        val suggestItems = StockApiService.parseEastmoneySuggest(suggestJson)
        assertEquals(1, suggestItems.size)
        assertEquals("sh600362", suggestItems[0].symbol)
        assertEquals("jxty", suggestItems[0].pinyin)

        val dirJson = """
            {
              "data": {
                "diff": [
                  {"f12": "601919", "f13": 1, "f14": "中远海控"},
                  {"f12": "835185", "f13": 0, "f14": "贝特瑞"}
                ]
              }
            }
        """.trimIndent()
        val dirItems = StockApiService.parseEastmoneyDirectory(dirJson)
        assertEquals(2, dirItems.size)
        assertEquals("sh601919", dirItems[0].symbol)
        assertEquals("zyhk", dirItems[0].pinyin)
        assertEquals("bj835185", dirItems[1].symbol)
    }

    @Test
    fun repositoryAndViewModelSearch_findStocksImmediatelyByPinyinNameOrCode() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = AppDatabase.getDatabase(app)
        val repo = StockRepository(db)

        val dynamicQuote = StockQuote(
            code = "601919",
            market = "sh",
            symbol = "sh601919",
            name = "中远海控",
            price = 14.2,
            yesterdayClose = 14.0,
            open = 14.1,
            change = 0.2,
            changePercent = 1.43,
            high = 14.4,
            low = 14.0,
            volumeLots = 10000L,
            turnoverTenThousand = 14200.0
        )

        // Core pool matching by pinyin (both lower/upper), Chinese name, and code
        assertTrue(repo.searchLocalStocks("jxty").any { it.symbol == "sh600362" && it.name == "江西铜业" })
        assertTrue(repo.searchLocalStocks("JXTY").any { it.symbol == "sh600362" })
        assertTrue(repo.searchLocalStocks("江西铜业").any { it.symbol == "sh600362" })
        assertTrue(repo.searchLocalStocks("600362").any { it.symbol == "sh600362" })

        // Dynamic quote matching by auto-generated pinyin initials & Chinese substring
        assertTrue(repo.searchLocalStocks("zyhk", listOf(dynamicQuote)).any { it.symbol == "sh601919" })
        assertTrue(repo.searchLocalStocks("中远", listOf(dynamicQuote)).any { it.symbol == "sh601919" })

        val vm = StockTerminalViewModel(app)
        vm.searchStocks("jxty")
        assertFalse(vm.uiState.value.isSearching)
        assertTrue(vm.uiState.value.searchResults.any { it.symbol == "sh600362" })

        vm.searchStocks("江西铜业")
        assertFalse(vm.uiState.value.isSearching)
        assertTrue(vm.uiState.value.searchResults.any { it.symbol == "sh600362" })

        // Instant lookup for 中国海油 (name, pinyin initials, code) without spinner/network wait
        vm.searchStocks("中国海油")
        assertFalse(vm.uiState.value.isSearching)
        assertTrue(vm.uiState.value.searchResults.any { it.symbol == "sh600938" && it.code == "600938" })

        vm.searchStocks("zghy")
        assertFalse(vm.uiState.value.isSearching)
        assertTrue(vm.uiState.value.searchResults.any { it.symbol == "sh600938" })

        vm.searchStocks("600938")
        assertFalse(vm.uiState.value.isSearching)
        assertTrue(vm.uiState.value.searchResults.any { it.symbol == "sh600938" })
    }

    @Test
    fun dictionaryPersistenceAndScheduledTtl_cachesInRoomAndSkipsRedundantNetworkFetches() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = AppDatabase.getDatabase(app)
        db.stockDictionaryDao().clearAll()
        val repo = StockRepository(db)

        // Generate 1050 mock A-share dictionary entries to satisfy full-market threshold (>= 1000)
        val fullMarketBatch = (1..1050).map { idx ->
            val code = (600000 + idx).toString()
            com.example.data.model.StockSearchItem(
                code = code,
                market = "sh",
                symbol = "sh$code",
                name = if (idx == 938) "中国海油" else "测试股份$idx",
                pinyin = if (idx == 938) "zghy" else "csgf$idx"
            )
        }

        var fetchCount = 0
        val t0 = 1_700_000_000_000L
        val status1 = repo.syncDictionaryIfNeeded(
            force = false,
            nowMs = t0,
            fetcher = {
                fetchCount++
                fullMarketBatch
            }
        )
        assertEquals(1, fetchCount)
        assertTrue(status1.isFullMarketSynced)
        assertTrue(repo.searchLocalStocks("600999").any { it.code == "600999" })

        // Within 12h TTL: must reuse Room dictionary and NOT call fetcher
        val status2 = repo.syncDictionaryIfNeeded(
            force = false,
            nowMs = t0 + 3L * 60L * 60L * 1000L,
            fetcher = {
                fetchCount++
                fullMarketBatch
            }
        )
        assertEquals(1, fetchCount)
        assertEquals(t0, status2.lastUpdatedMs)

        // After 12h TTL expires: scheduled update refreshes dictionary
        val tExpired = t0 + StockRepository.DICTIONARY_REFRESH_INTERVAL_MS + 1000L
        val status3 = repo.syncDictionaryIfNeeded(
            force = false,
            nowMs = tExpired,
            fetcher = {
                fetchCount++
                fullMarketBatch
            }
        )
        assertEquals(2, fetchCount)
        assertEquals(tExpired, status3.lastUpdatedMs)
    }
}
