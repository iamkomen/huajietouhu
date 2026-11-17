package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.FactorScoreAlertEntity
import com.example.data.local.TradeJournalEntity
import com.example.data.local.WatchlistEntity
import com.example.data.model.*
import com.example.data.remote.StockApiService
import com.example.data.repository.ProfitabilityEngine
import com.example.data.repository.QuantEngine
import com.example.data.repository.StockIndexCategory
import com.example.data.repository.StockRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class TerminalUiState(
    val currentSymbol: String = "sh600519",
    val quote: StockQuote? = null,
    val kLines: List<KLinePoint> = emptyList(),
    val minutePoints: List<MinutePoint> = emptyList(),
    val weeklyKLines: List<KLinePoint> = emptyList(),
    val monthlyKLines: List<KLinePoint> = emptyList(),
    val selectedChartPeriod: ChartPeriod = ChartPeriod.INTRADAY,
    val factorScores: QuantFactorScores? = null,
    val winRateStats: HistoricalWinRateStats? = null,
    val horizonYears: String = "5年全周期",
    val isWatchlisted: Boolean = false,
    val watchlistQuotes: List<StockQuote> = emptyList(),
    val watchlistEntities: List<WatchlistEntity> = emptyList(),
    val factorAlerts: List<FactorScoreAlertEntity> = emptyList(),
    val unreadAlertCount: Int = 0,
    val activePushAlert: FactorScoreAlertEntity? = null,
    val isAlertsDialogOpen: Boolean = false,
    val marketIndexQuotes: List<StockQuote> = emptyList(),
    val selectedIndexCategory: StockIndexCategory = StockIndexCategory.CSI_300,
    val screenerQuotes: List<StockQuote> = emptyList(),
    val isScreenerLoading: Boolean = false,
    val screenerErrorMessage: String? = null,
    val selectedScreenerFilter: String = "ALL",
    val searchQuery: String = "",
    val searchResults: List<StockSearchItem> = emptyList(),
    val isLoading: Boolean = false,
    val isKLineLoading: Boolean = false,
    val kLineErrorMessage: String? = null,
    val isSearching: Boolean = false,
    val isSearchDialogOpen: Boolean = false,
    val marketRegime: MarketRegime = MarketRegime.BULL_MOMENTUM,
    val objectiveAuditReport: ObjectiveQuantAuditReport? = null,
    val selectedRetailStrategy: String = "BALANCED",
    val retailTop10Portfolio: RetailTop10Portfolio? = null,
    val retailUniverseQuotes: List<StockQuote> = emptyList(),
    val retailUniverseKLines: Map<String, List<KLinePoint>> = emptyMap(),
    val benchmarkKLines: List<KLinePoint> = emptyList(),
    val currentFinancialSnapshot: FinancialStatementSnapshot? = null,
    val cachedFinancialSnapshots: Map<String, FinancialStatementSnapshot> = emptyMap(),
    val marketBreadth: MarketBreadthSnapshot? = null,
    val tradeJournalEntries: List<TradeJournalEntity> = emptyList(),
    val journalStats: JournalPerformanceStats = ProfitabilityEngine.computeJournalStats(emptyList()),
    val dictionarySize: Int = 0,
    val dictionaryLastUpdatedMs: Long = 0L,
    val isDictionarySyncing: Boolean = false
)

class StockTerminalViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = StockRepository(database)

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var screenerLoadJob: Job? = null
    private var dictionarySyncJob: Job? = null
    private var dictionarySchedulerJob: Job? = null
    private val cachedIndexQuotes = mutableMapOf<StockIndexCategory, List<StockQuote>>()

    init {
        // Collect watchlist entities from Room
        viewModelScope.launch {
            repository.getWatchlistEntities().collect { entities ->
                val symbols = entities.map { it.symbol }
                val current = _uiState.value.currentSymbol
                _uiState.update {
                    it.copy(
                        isWatchlisted = symbols.contains(current),
                        watchlistEntities = entities
                    )
                }
                loadWatchlistQuotes(symbols)
            }
        }

        // Collect alerts from Room
        viewModelScope.launch {
            repository.getFactorAlerts().collect { alerts ->
                _uiState.update { it.copy(factorAlerts = alerts) }
            }
        }

        // Collect unread alert count from Room
        viewModelScope.launch {
            repository.getUnreadAlertCount().collect { count ->
                _uiState.update { it.copy(unreadAlertCount = count) }
            }
        }

        // Collect persistent trade journal entries & recompute realized expectancy
        viewModelScope.launch {
            repository.getTradeJournalEntries().collect { entries ->
                val stats = ProfitabilityEngine.computeJournalStats(entries)
                _uiState.update { it.copy(tradeJournalEntries = entries, journalStats = stats) }
            }
        }

        // Initialize default stock & indices & local dictionary + scheduled updates
        loadStock(_uiState.value.currentSymbol)
        loadMarketIndices()
        loadScreenerStocks(_uiState.value.selectedIndexCategory)
        loadRetailTop10Portfolio(_uiState.value.selectedRetailStrategy)
        initAndScheduleDictionary()
        startPolling()
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(6000) // Poll real market quotes every 6 seconds
                val current = _uiState.value.currentSymbol
                try {
                    val quotes = repository.getQuotes(listOf(current))
                    if (quotes.isNotEmpty()) {
                        val newQuote = quotes.first()
                        val currentState = _uiState.value
                        val baseK = if (currentState.kLines.isEmpty()) {
                            repository.getDailyKLines(current)
                        } else {
                            currentState.kLines
                        }
                        val reconciledK = StockApiService.reconcileDailyKLinesWithLiveQuote(baseK, newQuote)
                        val scores = QuantEngine.evaluateFactors(newQuote, reconciledK)
                        val winRate = QuantEngine.computeHistoricalWinRateStats(
                            quote = newQuote,
                            horizonYears = currentState.horizonYears,
                            factorScores = scores,
                            kLines = reconciledK
                        )
                        val universeQuotes = (currentState.screenerQuotes + currentState.retailUniverseQuotes + currentState.watchlistQuotes + listOf(newQuote))
                            .distinctBy { it.symbol }
                        val universeKLines = if (reconciledK.isNotEmpty()) {
                            currentState.retailUniverseKLines + mapOf(current to reconciledK)
                        } else {
                            currentState.retailUniverseKLines
                        }
                        val audit = QuantEngine.computeObjectiveQuantAuditReport(
                            quote = newQuote,
                            kLines = reconciledK,
                            factorScores = scores,
                            universeQuotes = universeQuotes,
                            universeKLines = universeKLines,
                            benchmarkKLines = currentState.benchmarkKLines
                        )
                        val newMinutes = if (currentState.selectedChartPeriod == ChartPeriod.INTRADAY) {
                            repository.getIntradayMinutes(current)
                        } else emptyList()

                        val didPersistCurrent = currentState.isWatchlisted
                        if (didPersistCurrent) {
                            persistScoresAndCheckAlerts(newQuote, scores)
                        }

                        _uiState.update { state ->
                            if (state.currentSymbol != current) return@update state
                            val updatedWatchlistQuotes = if (state.watchlistQuotes.any { it.symbol == current }) {
                                state.watchlistQuotes.map { if (it.symbol == current) newQuote else it }
                            } else state.watchlistQuotes
                            state.copy(
                                quote = newQuote,
                                kLines = reconciledK,
                                retailUniverseKLines = universeKLines,
                                factorScores = scores,
                                winRateStats = winRate,
                                objectiveAuditReport = audit,
                                watchlistQuotes = updatedWatchlistQuotes,
                                minutePoints = if (newMinutes.isNotEmpty()) newMinutes else state.minutePoints,
                                kLineErrorMessage = if (state.selectedChartPeriod == ChartPeriod.DAY && reconciledK.isNotEmpty()) null else state.kLineErrorMessage
                            )
                        }

                        // Poll watchlist quotes & market indices (skip alert/persist re-eval for current which was just persisted)
                        val watchedSymbols = _uiState.value.watchlistEntities.map { it.symbol }
                        if (watchedSymbols.isNotEmpty()) {
                            loadWatchlistQuotes(
                                symbols = watchedSymbols,
                                skipPersistSymbols = if (didPersistCurrent) setOf(current) else emptySet()
                            )
                        }
                    } else {
                        val watchedSymbols = _uiState.value.watchlistEntities.map { it.symbol }
                        if (watchedSymbols.isNotEmpty()) {
                            loadWatchlistQuotes(watchedSymbols)
                        }
                    }
                    loadMarketIndices()
                } catch (e: Exception) {
                    // Handled gracefully in background polling
                }
            }
        }
    }

    private fun loadMarketIndices() {
        viewModelScope.launch {
            try {
                val indexQuotes = repository.getQuotes(repository.marketIndexSymbols)
                if (indexQuotes.isNotEmpty()) {
                    _uiState.update { it.copy(marketIndexQuotes = indexQuotes) }
                }
            } catch (e: Exception) {
                // Ignore transient network errors
            }
        }
    }

    private fun findCachedQuote(symbol: String, state: TerminalUiState): StockQuote? {
        return state.watchlistQuotes.firstOrNull { it.symbol == symbol }
            ?: state.screenerQuotes.firstOrNull { it.symbol == symbol }
            ?: state.retailUniverseQuotes.firstOrNull { it.symbol == symbol }
            ?: state.marketIndexQuotes.firstOrNull { it.symbol == symbol }
            ?: state.retailTop10Portfolio?.stocks?.firstOrNull { it.quote.symbol == symbol }?.quote
    }

    fun selectStock(symbol: String) {
        val state = _uiState.value
        if (symbol == state.currentSymbol && state.quote != null) {
            _uiState.update { it.copy(isSearchDialogOpen = false) }
            return
        }

        val cachedQuote = findCachedQuote(symbol, state)
        val cachedKLines = state.retailUniverseKLines[symbol].orEmpty()
        val provisionalScores = if (cachedQuote != null) QuantEngine.evaluateFactors(cachedQuote, cachedKLines) else null
        val provisionalWinRate = if (cachedQuote != null && provisionalScores != null) {
            QuantEngine.computeHistoricalWinRateStats(cachedQuote, state.horizonYears, provisionalScores, cachedKLines)
        } else null
        val provisionalAudit = if (cachedQuote != null && provisionalScores != null) {
            val allQ = (state.screenerQuotes + state.retailUniverseQuotes + state.watchlistQuotes + listOf(cachedQuote))
                .distinctBy { it.symbol }
            QuantEngine.computeObjectiveQuantAuditReport(
                quote = cachedQuote,
                kLines = cachedKLines,
                factorScores = provisionalScores,
                universeQuotes = allQ,
                universeKLines = state.retailUniverseKLines,
                benchmarkKLines = state.benchmarkKLines
            )
        } else null

        _uiState.update {
            it.copy(
                currentSymbol = symbol,
                isSearchDialogOpen = false,
                quote = cachedQuote,
                kLines = cachedKLines,
                isKLineLoading = cachedKLines.isEmpty(),
                kLineErrorMessage = null,
                factorScores = provisionalScores,
                winRateStats = provisionalWinRate,
                objectiveAuditReport = provisionalAudit,
                isWatchlisted = it.watchlistEntities.any { w -> w.symbol == symbol },
                minutePoints = emptyList(),
                weeklyKLines = emptyList(),
                monthlyKLines = emptyList(),
                currentFinancialSnapshot = null
            )
        }
        loadStock(symbol)
    }

    fun setChartPeriod(period: ChartPeriod) {
        _uiState.update {
            it.copy(
                selectedChartPeriod = period,
                kLineErrorMessage = null,
                isKLineLoading = when (period) {
                    ChartPeriod.INTRADAY -> false
                    ChartPeriod.DAY -> it.kLines.isEmpty()
                    ChartPeriod.WEEK -> it.weeklyKLines.isEmpty()
                    ChartPeriod.MONTH -> it.monthlyKLines.isEmpty()
                }
            )
        }
        val sym = _uiState.value.currentSymbol
        viewModelScope.launch {
            try {
                when (period) {
                    ChartPeriod.INTRADAY -> {
                        if (_uiState.value.minutePoints.isEmpty()) {
                            val m = repository.getIntradayMinutes(sym)
                            if (_uiState.value.currentSymbol == sym) {
                                _uiState.update { it.copy(minutePoints = m, isKLineLoading = false) }
                            }
                        }
                    }
                    ChartPeriod.WEEK -> {
                        if (_uiState.value.weeklyKLines.isEmpty()) {
                            val wk = repository.getMultiPeriodKLines(sym, "week")
                            if (_uiState.value.currentSymbol == sym) {
                                _uiState.update {
                                    it.copy(
                                        weeklyKLines = wk,
                                        isKLineLoading = false,
                                        kLineErrorMessage = if (wk.isEmpty()) "周K线暂未返回数据，请点击重试" else null
                                    )
                                }
                            }
                        }
                    }
                    ChartPeriod.MONTH -> {
                        if (_uiState.value.monthlyKLines.isEmpty()) {
                            val mo = repository.getMultiPeriodKLines(sym, "month")
                            if (_uiState.value.currentSymbol == sym) {
                                _uiState.update {
                                    it.copy(
                                        monthlyKLines = mo,
                                        isKLineLoading = false,
                                        kLineErrorMessage = if (mo.isEmpty()) "月K线暂未返回数据，请点击重试" else null
                                    )
                                }
                            }
                        }
                    }
                    ChartPeriod.DAY -> {
                        val existing = _uiState.value.kLines
                        val fetched = if (existing.isEmpty()) repository.getDailyKLines(sym) else existing
                        if (_uiState.value.currentSymbol != sym) return@launch
                        val currentState = _uiState.value
                        val reconciled = StockApiService.reconcileDailyKLinesWithLiveQuote(fetched, currentState.quote)
                        val quote = currentState.quote
                        if (quote != null && reconciled.isNotEmpty() && existing.isEmpty()) {
                            val scores = QuantEngine.evaluateFactors(quote, reconciled)
                            val winRate = QuantEngine.computeHistoricalWinRateStats(
                                quote = quote,
                                horizonYears = currentState.horizonYears,
                                factorScores = scores,
                                kLines = reconciled
                            )
                            val updatedMap = currentState.retailUniverseKLines + mapOf(sym to reconciled)
                            val universeQuotes = (currentState.screenerQuotes + currentState.retailUniverseQuotes + currentState.watchlistQuotes + listOf(quote))
                                .distinctBy { it.symbol }
                            val audit = QuantEngine.computeObjectiveQuantAuditReport(
                                quote = quote,
                                kLines = reconciled,
                                factorScores = scores,
                                universeQuotes = universeQuotes,
                                universeKLines = updatedMap,
                                benchmarkKLines = currentState.benchmarkKLines
                            )
                            _uiState.update {
                                it.copy(
                                    kLines = reconciled,
                                    factorScores = scores,
                                    winRateStats = winRate,
                                    objectiveAuditReport = audit,
                                    retailUniverseKLines = updatedMap,
                                    isKLineLoading = false,
                                    kLineErrorMessage = null
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    kLines = reconciled,
                                    isKLineLoading = false,
                                    kLineErrorMessage = if (reconciled.isEmpty()) "日线K线暂未返回数据，请点击重试" else null
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                if (_uiState.value.currentSymbol == sym) {
                    _uiState.update {
                        it.copy(
                            isKLineLoading = false,
                            kLineErrorMessage = "K线数据同步异常，请点击重试"
                        )
                    }
                }
            }
        }
    }

    fun retryKLines() {
        val sym = _uiState.value.currentSymbol
        val period = _uiState.value.selectedChartPeriod
        _uiState.update { it.copy(isKLineLoading = true, kLineErrorMessage = null) }
        viewModelScope.launch {
            try {
                when (period) {
                    ChartPeriod.INTRADAY -> {
                        val m = repository.getIntradayMinutes(sym)
                        if (_uiState.value.currentSymbol == sym) {
                            _uiState.update { it.copy(minutePoints = m, isKLineLoading = false) }
                        }
                    }
                    ChartPeriod.DAY -> {
                        val raw = repository.getDailyKLines(sym)
                        if (_uiState.value.currentSymbol != sym) return@launch
                        val currentState = _uiState.value
                        val reconciled = StockApiService.reconcileDailyKLinesWithLiveQuote(raw, currentState.quote)
                        val quote = currentState.quote
                        if (quote != null && reconciled.isNotEmpty()) {
                            val scores = QuantEngine.evaluateFactors(quote, reconciled)
                            val winRate = QuantEngine.computeHistoricalWinRateStats(
                                quote = quote,
                                horizonYears = currentState.horizonYears,
                                factorScores = scores,
                                kLines = reconciled
                            )
                            val updatedMap = currentState.retailUniverseKLines + mapOf(sym to reconciled)
                            val universeQuotes = (currentState.screenerQuotes + currentState.retailUniverseQuotes + currentState.watchlistQuotes + listOf(quote))
                                .distinctBy { it.symbol }
                            val audit = QuantEngine.computeObjectiveQuantAuditReport(
                                quote = quote,
                                kLines = reconciled,
                                factorScores = scores,
                                universeQuotes = universeQuotes,
                                universeKLines = updatedMap,
                                benchmarkKLines = currentState.benchmarkKLines
                            )
                            _uiState.update {
                                it.copy(
                                    kLines = reconciled,
                                    factorScores = scores,
                                    winRateStats = winRate,
                                    objectiveAuditReport = audit,
                                    retailUniverseKLines = updatedMap,
                                    isKLineLoading = false,
                                    kLineErrorMessage = null
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    kLines = reconciled,
                                    isKLineLoading = false,
                                    kLineErrorMessage = if (reconciled.isEmpty()) "日线K线暂未返回数据，请点击重试" else null
                                )
                            }
                        }
                    }
                    ChartPeriod.WEEK -> {
                        val wk = repository.getMultiPeriodKLines(sym, "week")
                        if (_uiState.value.currentSymbol == sym) {
                            _uiState.update {
                                it.copy(
                                    weeklyKLines = wk,
                                    isKLineLoading = false,
                                    kLineErrorMessage = if (wk.isEmpty()) "周K线暂未返回数据，请点击重试" else null
                                )
                            }
                        }
                    }
                    ChartPeriod.MONTH -> {
                        val mo = repository.getMultiPeriodKLines(sym, "month")
                        if (_uiState.value.currentSymbol == sym) {
                            _uiState.update {
                                it.copy(
                                    monthlyKLines = mo,
                                    isKLineLoading = false,
                                    kLineErrorMessage = if (mo.isEmpty()) "月K线暂未返回数据，请点击重试" else null
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                if (_uiState.value.currentSymbol == sym) {
                    _uiState.update {
                        it.copy(
                            isKLineLoading = false,
                            kLineErrorMessage = "K线数据同步异常，请点击重试"
                        )
                    }
                }
            }
        }
    }

    fun refreshCurrentStock() {
        loadStock(_uiState.value.currentSymbol, forceRefreshFundamentals = true)
        loadMarketIndices()
        val watchedSymbols = _uiState.value.watchlistEntities.map { it.symbol }
        if (watchedSymbols.isNotEmpty()) {
            loadWatchlistQuotes(watchedSymbols)
        }
    }

    private fun loadStock(symbol: String, forceRefreshFundamentals: Boolean = false) {
        viewModelScope.launch {
            try {
                val f10Snapshot = repository.getFinancialFundamentals(
                    symbol = symbol,
                    forceRefresh = forceRefreshFundamentals
                )
                if (_uiState.value.currentSymbol == symbol && f10Snapshot != null) {
                    _uiState.update {
                        it.copy(
                            currentFinancialSnapshot = f10Snapshot,
                            cachedFinancialSnapshots = repository.getCachedFinancialSnapshots()
                        )
                    }
                } else if (f10Snapshot != null) {
                    _uiState.update { it.copy(cachedFinancialSnapshots = repository.getCachedFinancialSnapshots()) }
                }
            } catch (_: Exception) {
                // Gracefully retain DuPont calibration when F10 network is unavailable
            }
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isKLineLoading = it.kLines.isEmpty(),
                    kLineErrorMessage = null
                )
            }
            try {
                val activePeriod = _uiState.value.selectedChartPeriod
                val (quotes, dailyKLines, minutes, periodExtra) = coroutineScope {
                    val quotesDeferred = async { repository.getQuotes(listOf(symbol)) }
                    val kLinesDeferred = async { repository.getDailyKLines(symbol) }
                    val minutesDeferred = async { repository.getIntradayMinutes(symbol) }
                    val extraDeferred = async {
                        when (activePeriod) {
                            ChartPeriod.WEEK -> repository.getMultiPeriodKLines(symbol, "week")
                            ChartPeriod.MONTH -> repository.getMultiPeriodKLines(symbol, "month")
                            else -> emptyList()
                        }
                    }
                    Quad(
                        quotesDeferred.await(),
                        kLinesDeferred.await(),
                        minutesDeferred.await(),
                        extraDeferred.await()
                    )
                }

                // 若用户已快速切换至另一只股票，丢弃过期响应，避免错配
                if (_uiState.value.currentSymbol != symbol) return@launch

                val currentState = _uiState.value
                val quote = quotes.firstOrNull() ?: findCachedQuote(symbol, currentState)
                val effectiveKLines = if (dailyKLines.isNotEmpty()) dailyKLines else currentState.retailUniverseKLines[symbol].orEmpty()
                val reconciledKLines = StockApiService.reconcileDailyKLinesWithLiveQuote(effectiveKLines, quote)
                val isSaved = repository.isWatchlisted(symbol)

                val currentActivePeriod = _uiState.value.selectedChartPeriod
                val hasActiveKLineSeries = when (currentActivePeriod) {
                    ChartPeriod.INTRADAY -> true
                    ChartPeriod.DAY -> reconciledKLines.isNotEmpty()
                    ChartPeriod.WEEK -> periodExtra.isNotEmpty() || currentState.weeklyKLines.isNotEmpty()
                    ChartPeriod.MONTH -> periodExtra.isNotEmpty() || currentState.monthlyKLines.isNotEmpty()
                }
                val kErr = if (!hasActiveKLineSeries && currentActivePeriod != ChartPeriod.INTRADAY) {
                    "${currentActivePeriod.label}K线暂未返回数据，请点击重试"
                } else null

                if (quote != null) {
                    repository.learnStockIntoDictionary(quote)
                    val scores = QuantEngine.evaluateFactors(quote, reconciledKLines)
                    val winRate = QuantEngine.computeHistoricalWinRateStats(
                        quote = quote,
                        horizonYears = currentState.horizonYears,
                        factorScores = scores,
                        kLines = reconciledKLines
                    )
                    val universeQuotes = (currentState.screenerQuotes + currentState.retailUniverseQuotes + currentState.watchlistQuotes + listOf(quote))
                        .distinctBy { it.symbol }
                    val updatedKLinesMap = if (reconciledKLines.isNotEmpty()) {
                        currentState.retailUniverseKLines + mapOf(symbol to reconciledKLines)
                    } else {
                        currentState.retailUniverseKLines
                    }
                    val audit = QuantEngine.computeObjectiveQuantAuditReport(
                        quote = quote,
                        kLines = reconciledKLines,
                        factorScores = scores,
                        universeQuotes = universeQuotes,
                        universeKLines = updatedKLinesMap,
                        benchmarkKLines = currentState.benchmarkKLines
                    )

                    if (isSaved) {
                        persistScoresAndCheckAlerts(quote, scores)
                    }

                    _uiState.update { state ->
                        val updatedWatchlistQuotes = if (state.watchlistQuotes.any { it.symbol == symbol }) {
                            state.watchlistQuotes.map { if (it.symbol == symbol) quote else it }
                        } else state.watchlistQuotes

                        state.copy(
                            quote = quote,
                            kLines = reconciledKLines,
                            minutePoints = minutes,
                            weeklyKLines = if (activePeriod == ChartPeriod.WEEK && periodExtra.isNotEmpty()) periodExtra else state.weeklyKLines,
                            monthlyKLines = if (activePeriod == ChartPeriod.MONTH && periodExtra.isNotEmpty()) periodExtra else state.monthlyKLines,
                            factorScores = scores,
                            winRateStats = winRate,
                            objectiveAuditReport = audit,
                            watchlistQuotes = updatedWatchlistQuotes,
                            retailUniverseKLines = updatedKLinesMap,
                            isWatchlisted = isSaved,
                            isLoading = false,
                            isKLineLoading = false,
                            kLineErrorMessage = kErr
                        )
                    }
                } else {
                    _uiState.update { state ->
                        state.copy(
                            kLines = reconciledKLines,
                            minutePoints = if (minutes.isNotEmpty()) minutes else state.minutePoints,
                            weeklyKLines = if (activePeriod == ChartPeriod.WEEK && periodExtra.isNotEmpty()) periodExtra else state.weeklyKLines,
                            monthlyKLines = if (activePeriod == ChartPeriod.MONTH && periodExtra.isNotEmpty()) periodExtra else state.monthlyKLines,
                            isLoading = false,
                            isKLineLoading = false,
                            kLineErrorMessage = kErr
                        )
                    }
                }
            } catch (e: Exception) {
                if (_uiState.value.currentSymbol == symbol) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isKLineLoading = false,
                            kLineErrorMessage = if (it.kLines.isEmpty()) "日线数据同步异常，请点击重试" else null
                        )
                    }
                }
            }
        }
    }

    private suspend fun persistScoresAndCheckAlerts(quote: StockQuote, scores: QuantFactorScores) {
        try {
            val entity = _uiState.value.watchlistEntities.firstOrNull { it.symbol == quote.symbol }
            if (entity != null && entity.lastCompositeScore > 0 && entity.isNotificationEnabled) {
                val delta = scores.compositeScore - entity.lastCompositeScore
                if (kotlin.math.abs(delta) >= entity.notifyThresholdDelta) {
                    val sign = if (delta >= 0) "+$delta" else "$delta"
                    val primaryChange = listOf(
                        "价值" to kotlin.math.abs(scores.valueScore - entity.lastValueScore),
                        "成长" to kotlin.math.abs(scores.growthScore - entity.lastGrowthScore),
                        "动量" to kotlin.math.abs(scores.momentumScore - entity.lastMomentumScore),
                        "质量" to kotlin.math.abs(scores.qualityScore - entity.lastQualityScore)
                    ).maxByOrNull { it.second }?.first ?: "综合量化分"
                    val msg = "${quote.name}(${quote.code}) 综合因子评分从 ${entity.lastCompositeScore}分 变动至 ${scores.compositeScore}分 ($sign 分)，主导异动维度: $primaryChange。"
                    val alert = FactorScoreAlertEntity(
                        symbol = quote.symbol,
                        stockName = quote.name,
                        oldScore = entity.lastCompositeScore,
                        newScore = scores.compositeScore,
                        delta = delta,
                        oldRating = entity.lastRating,
                        newRating = scores.rating,
                        primaryFactorChanged = primaryChange,
                        alertMessage = msg,
                        isRead = false
                    )
                    repository.insertAlert(alert)
                    _uiState.update { it.copy(activePushAlert = alert) }
                }
            }

            repository.updateWatchlistScores(
                symbol = quote.symbol,
                composite = scores.compositeScore,
                value = scores.valueScore,
                growth = scores.growthScore,
                momentum = scores.momentumScore,
                quality = scores.qualityScore,
                risk = scores.riskControlScore,
                rating = scores.rating
            )
        } catch (_: Exception) {
            // Room update guard
        }
    }

    fun toggleWatchlist() {
        val state = _uiState.value
        val q = state.quote ?: return
        val kLines = state.kLines
        viewModelScope.launch {
            val wasWatchlisted = repository.isWatchlisted(q.symbol)
            repository.toggleWatchlist(q.symbol, q.name)
            val updated = !wasWatchlisted
            if (updated) {
                val scores = state.factorScores ?: QuantEngine.evaluateFactors(q, kLines)
                repository.updateWatchlistScores(
                    symbol = q.symbol,
                    composite = scores.compositeScore,
                    value = scores.valueScore,
                    growth = scores.growthScore,
                    momentum = scores.momentumScore,
                    quality = scores.qualityScore,
                    risk = scores.riskControlScore,
                    rating = scores.rating
                )
            }
            _uiState.update { it.copy(isWatchlisted = updated) }
        }
    }

    fun toggleStockWatchlist(symbol: String, name: String) {
        viewModelScope.launch {
            val wasWatchlisted = repository.isWatchlisted(symbol)
            repository.toggleWatchlist(symbol, name)
            val updated = !wasWatchlisted
            if (updated) {
                val state = _uiState.value
                val q = findCachedQuote(symbol, state)
                if (q != null) {
                    val kl = state.retailUniverseKLines[symbol].orEmpty()
                    val scores = QuantEngine.evaluateFactors(q, kl)
                    repository.updateWatchlistScores(
                        symbol = q.symbol,
                        composite = scores.compositeScore,
                        value = scores.valueScore,
                        growth = scores.growthScore,
                        momentum = scores.momentumScore,
                        quality = scores.qualityScore,
                        risk = scores.riskControlScore,
                        rating = scores.rating
                    )
                }
            }
            if (_uiState.value.currentSymbol == symbol) {
                _uiState.update { it.copy(isWatchlisted = updated) }
            }
        }
    }

    fun removeStockFromWatchlist(symbol: String) {
        viewModelScope.launch {
            repository.removeFromWatchlist(symbol)
            if (_uiState.value.currentSymbol == symbol) {
                _uiState.update { it.copy(isWatchlisted = false) }
            }
        }
    }

    fun setMarketRegime(regime: MarketRegime) {
        _uiState.update { state ->
            val updated = state.copy(marketRegime = regime)
            updated.copy(marketBreadth = computeBreadthForState(updated))
        }
    }

    private fun computeBreadthForState(state: TerminalUiState): MarketBreadthSnapshot {
        val universe = (state.screenerQuotes + state.retailUniverseQuotes + state.watchlistQuotes)
            .distinctBy { it.symbol }
        return ProfitabilityEngine.computeEmpiricalMarketBreadth(
            indexQuotes = state.marketIndexQuotes,
            universeQuotes = universe,
            universeKLines = state.retailUniverseKLines,
            fallbackRegime = state.marketRegime
        )
    }

    fun setHorizonYears(horizon: String) {
        val q = _uiState.value.quote ?: return
        val scores = _uiState.value.factorScores ?: return
        val kLines = _uiState.value.kLines
        val winRate = QuantEngine.computeHistoricalWinRateStats(
            quote = q,
            horizonYears = horizon,
            factorScores = scores,
            kLines = kLines
        )
        _uiState.update {
            it.copy(
                horizonYears = horizon,
                winRateStats = winRate
            )
        }
    }

    private fun loadWatchlistQuotes(
        symbols: List<String>,
        skipPersistSymbols: Set<String> = emptySet()
    ) {
        if (symbols.isEmpty()) {
            _uiState.update { it.copy(watchlistQuotes = emptyList()) }
            return
        }
        viewModelScope.launch {
            try {
                val quotes = repository.getQuotes(symbols)
                if (quotes.isNotEmpty()) {
                    _uiState.update { it.copy(watchlistQuotes = quotes) }

                    // 同步缺失的日K线用于精准计算HV20/MACD/均线多因子
                    val currentKMap = _uiState.value.retailUniverseKLines.toMutableMap()
                    val missingK = symbols.take(12).filter { (currentKMap[it]?.size ?: 0) < 25 }
                    if (missingK.isNotEmpty()) {
                        val fetchedK = coroutineScope {
                            missingK.map { sym ->
                                async {
                                    try {
                                        sym to repository.getDailyKLines(sym, 70)
                                    } catch (_: Exception) {
                                        sym to emptyList()
                                    }
                                }
                            }.awaitAll()
                        }
                        for ((sym, kl) in fetchedK) {
                            if (kl.isNotEmpty()) currentKMap[sym] = kl
                        }
                        _uiState.update { it.copy(retailUniverseKLines = currentKMap.toMap()) }
                    }

                    // 为所有自选标的刷新真实五因子得分并持久化至 Room（跳过刚在主轮询处理过的标的，防并发重复告警）
                    for (q in quotes) {
                        if (q.symbol in skipPersistSymbols) continue
                        val kl = currentKMap[q.symbol].orEmpty()
                        val scores = QuantEngine.evaluateFactors(q, kl)
                        persistScoresAndCheckAlerts(q, scores)
                    }
                }
            } catch (e: Exception) {
                // Ignore transient network errors
            }
        }
    }

    /**
     * 按指数分类加载核心标的池 (严格按选定指数成分股过滤，禁止跨指数残留串池)
     */
    fun selectIndexCategory(category: StockIndexCategory) {
        val constituentSet = repository.getIndexConstituentSet(category)
        val cachedForCategory = cachedIndexQuotes[category].orEmpty()
        val provisionalForCategory = if (cachedForCategory.isNotEmpty()) {
            cachedForCategory
        } else {
            collectKnownQuotes().filter { it.symbol.lowercase() in constituentSet }
        }
        screenerLoadJob?.cancel()
        _uiState.update { state ->
            val updated = state.copy(
                selectedIndexCategory = category,
                screenerQuotes = provisionalForCategory,
                isScreenerLoading = true,
                screenerErrorMessage = null
            )
            updated.copy(marketBreadth = computeBreadthForState(updated))
        }
        loadScreenerStocks(category)
    }

    fun retryScreenerStocks() {
        val category = _uiState.value.selectedIndexCategory
        _uiState.update { it.copy(isScreenerLoading = true, screenerErrorMessage = null) }
        loadScreenerStocks(category)
    }

    private fun loadScreenerStocks(category: StockIndexCategory) {
        screenerLoadJob?.cancel()
        screenerLoadJob = viewModelScope.launch {
            val symbols = repository.getIndexConstituents(category)
            val constituentSet = symbols.map { it.lowercase() }.toSet()
            try {
                val quotes = repository.getQuotes(symbols)
                    .filter { it.symbol.lowercase() in constituentSet }
                    .distinctBy { it.symbol.lowercase() }

                if (_uiState.value.selectedIndexCategory != category) return@launch

                if (quotes.isNotEmpty()) {
                    cachedIndexQuotes[category] = quotes
                    _uiState.update { state ->
                        if (state.selectedIndexCategory != category) return@update state
                        val updated = state.copy(
                            screenerQuotes = quotes,
                            isScreenerLoading = false,
                            screenerErrorMessage = null
                        )
                        updated.copy(marketBreadth = computeBreadthForState(updated))
                    }
                } else {
                    val fallbackForCategory = cachedIndexQuotes[category].orEmpty()
                        .ifEmpty {
                            collectKnownQuotes(_uiState.value).filter { it.symbol.lowercase() in constituentSet }
                        }
                    _uiState.update { state ->
                        if (state.selectedIndexCategory != category) return@update state
                        val updated = state.copy(
                            screenerQuotes = fallbackForCategory,
                            isScreenerLoading = false,
                            screenerErrorMessage = if (fallbackForCategory.isEmpty()) {
                                "【${category.shortName}】成分股实时行情暂未返回，请点击重试"
                            } else null
                        )
                        updated.copy(marketBreadth = computeBreadthForState(updated))
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                if (_uiState.value.selectedIndexCategory == category) {
                    val fallbackForCategory = cachedIndexQuotes[category].orEmpty()
                    _uiState.update { state ->
                        if (state.selectedIndexCategory != category) return@update state
                        state.copy(
                            screenerQuotes = fallbackForCategory,
                            isScreenerLoading = false,
                            screenerErrorMessage = if (fallbackForCategory.isEmpty()) {
                                "【${category.shortName}】成分股行情同步异常，请点击重试"
                            } else null
                        )
                    }
                }
            }
        }
    }

    fun updateScreenerFilter(filter: String) {
        _uiState.update { it.copy(selectedScreenerFilter = filter) }
    }

    fun ensureScreenerTargetHistory(symbol: String) {
        val state = _uiState.value
        val hasEnoughK = (state.retailUniverseKLines[symbol]?.size ?: 0) >= 30
        val hasF10 = state.cachedFinancialSnapshots.containsKey(symbol)
        if (hasEnoughK && hasF10) return

        viewModelScope.launch {
            try {
                if (!hasEnoughK) {
                    val kl = repository.getDailyKLines(symbol, 140)
                    val quote = findCachedQuote(symbol, _uiState.value)
                    val reconciled = StockApiService.reconcileDailyKLinesWithLiveQuote(kl, quote)
                    if (reconciled.isNotEmpty()) {
                        _uiState.update { current ->
                            current.copy(retailUniverseKLines = current.retailUniverseKLines + mapOf(symbol to reconciled))
                        }
                    }
                }
                if (!hasF10) {
                    val snap = repository.getFinancialFundamentals(symbol)
                    if (snap != null) {
                        _uiState.update { current ->
                            current.copy(cachedFinancialSnapshots = repository.getCachedFinancialSnapshots())
                        }
                    }
                }
            } catch (_: Exception) {
                // Keep UI graceful if network is unavailable
            }
        }
    }

    private fun collectKnownQuotes(state: TerminalUiState = _uiState.value): List<StockQuote> {
        return (state.watchlistQuotes +
            state.screenerQuotes +
            state.retailUniverseQuotes +
            state.marketIndexQuotes +
            listOfNotNull(state.quote) +
            (state.retailTop10Portfolio?.stocks?.map { it.quote } ?: emptyList()))
            .distinctBy { it.symbol }
    }

    private fun initAndScheduleDictionary() {
        val initialStatus = repository.getDictionaryStatus()
        _uiState.update {
            it.copy(
                dictionarySize = initialStatus.totalCount,
                dictionaryLastUpdatedMs = initialStatus.lastUpdatedMs
            )
        }
        viewModelScope.launch {
            try {
                val loaded = repository.loadPersistedDictionary()
                _uiState.update {
                    it.copy(
                        dictionarySize = loaded.totalCount,
                        dictionaryLastUpdatedMs = loaded.lastUpdatedMs
                    )
                }
            } catch (_: Exception) {
                // Keep seed dictionary intact
            }
            syncDictionaryBackground(force = false)
        }
        dictionarySchedulerJob?.cancel()
        dictionarySchedulerJob = viewModelScope.launch {
            while (isActive) {
                delay(6L * 60L * 60L * 1000L) // Check every 6h; syncDictionaryIfNeeded enforces 12h TTL
                syncDictionaryBackground(force = false)
            }
        }
    }

    private fun syncDictionaryBackground(force: Boolean) {
        if (!force && dictionarySyncJob?.isActive == true) return
        if (force) {
            dictionarySyncJob?.cancel()
        }
        dictionarySyncJob = viewModelScope.launch {
            _uiState.update { it.copy(isDictionarySyncing = true) }
            try {
                val status = repository.syncDictionaryIfNeeded(force = force)
                _uiState.update { state ->
                    val refreshedResults = if (state.isSearchDialogOpen) {
                        repository.searchLocalStocks(state.searchQuery, collectKnownQuotes(state))
                    } else state.searchResults
                    state.copy(
                        dictionarySize = status.totalCount,
                        dictionaryLastUpdatedMs = status.lastUpdatedMs,
                        isDictionarySyncing = false,
                        searchResults = refreshedResults
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(isDictionarySyncing = false) }
            }
        }
    }

    fun refreshStockDictionary() {
        syncDictionaryBackground(force = true)
    }

    fun openSearchDialog() {
        val defaultItems = repository.searchLocalStocks("", collectKnownQuotes())
        val status = repository.getDictionaryStatus()
        _uiState.update {
            it.copy(
                isSearchDialogOpen = true,
                searchQuery = "",
                searchResults = defaultItems,
                isSearching = false,
                dictionarySize = status.totalCount,
                dictionaryLastUpdatedMs = status.lastUpdatedMs
            )
        }
        syncDictionaryBackground(force = false)
    }

    fun closeSearchDialog() {
        _uiState.update { it.copy(isSearchDialogOpen = false, isSearching = false) }
    }

    fun searchStocks(query: String) {
        val instantLocal = repository.searchLocalStocks(query, collectKnownQuotes())
        _uiState.update {
            it.copy(
                searchQuery = query,
                searchResults = instantLocal,
                isSearching = false
            )
        }
    }

    fun openAlertsDialog() {
        _uiState.update { it.copy(isAlertsDialogOpen = true) }
        viewModelScope.launch {
            repository.markAlertsAsRead()
        }
    }

    fun closeAlertsDialog() {
        _uiState.update { it.copy(isAlertsDialogOpen = false) }
    }

    fun dismissPushAlert() {
        _uiState.update { it.copy(activePushAlert = null) }
    }

    fun clearAllAlerts() {
        viewModelScope.launch {
            repository.clearAllAlerts()
        }
    }

    fun toggleWatchlistNotification(symbol: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleWatchlistNotification(symbol, enabled)
        }
    }

    fun updateWatchlistThreshold(symbol: String, delta: Int) {
        viewModelScope.launch {
            repository.updateWatchlistThreshold(symbol, delta)
        }
    }

    fun setRetailStrategy(strategyKey: String) {
        _uiState.update { it.copy(selectedRetailStrategy = strategyKey) }
        loadRetailTop10Portfolio(strategyKey)
    }

    private fun loadRetailTop10Portfolio(strategyKey: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val cachedQuotes = (state.watchlistQuotes + state.screenerQuotes + state.retailUniverseQuotes + listOfNotNull(state.quote))
                .distinctBy { it.symbol }
            var quotesMap = cachedQuotes.associateBy { it.symbol }
            var kLinesMap = state.retailUniverseKLines.toMutableMap()
            if (state.quote != null && state.kLines.isNotEmpty()) {
                kLinesMap[state.currentSymbol] = state.kLines
            }
            var benchKLines = state.benchmarkKLines

            // 先用已有缓存即时渲染一次，保证首屏零等待
            val initialPortfolio = QuantEngine.generateRetailTop10Portfolio(strategyKey, quotesMap, kLinesMap, benchKLines)
            _uiState.update { it.copy(retailTop10Portfolio = initialPortfolio) }

            try {
                // 1. 补全 28 只候选宇宙全量实时行情
                val allUniverseSymbols = QuantEngine.getRetailUniverseSymbols()
                val missingQuoteSymbols = allUniverseSymbols.filter { !quotesMap.containsKey(it) }
                if (missingQuoteSymbols.isNotEmpty()) {
                    val freshQuotes = repository.getQuotes(missingQuoteSymbols)
                    if (freshQuotes.isNotEmpty()) {
                        quotesMap = quotesMap + freshQuotes.associateBy { it.symbol }
                    }
                }

                // 2. 拉取沪深300基准日K线 (用于真实 Beta 与同步回测对比)
                if (benchKLines.size < 30) {
                    val fetchedBench = repository.getDailyKLines("sh000300", 70)
                    if (fetchedBench.isNotEmpty()) {
                        benchKLines = fetchedBench
                    }
                }

                // 基于实时行情先筛选一次，确保对入选及候选头部标的拉取真实70日K线
                val interimPortfolio = QuantEngine.generateRetailTop10Portfolio(strategyKey, quotesMap, kLinesMap, benchKLines)
                _uiState.update {
                    it.copy(
                        retailTop10Portfolio = interimPortfolio,
                        retailUniverseQuotes = quotesMap.values.toList(),
                        benchmarkKLines = benchKLines
                    )
                }

                // 3. 并发拉取入选10只 + 候选前4只缺失的70日K线，支撑真实 HV20、Beta 与 10x10 马科维茨协方差矩阵
                val symbolsNeedingKLines = (interimPortfolio.stocks.map { it.quote.symbol } + allUniverseSymbols.take(6))
                    .distinct()
                    .filter { (kLinesMap[it]?.size ?: 0) < 30 }

                if (symbolsNeedingKLines.isNotEmpty()) {
                    val fetchedPairs = coroutineScope {
                        symbolsNeedingKLines.map { sym ->
                            async {
                                try {
                                    sym to repository.getDailyKLines(sym, 70)
                                } catch (e: Exception) {
                                    sym to emptyList()
                                }
                            }
                        }.awaitAll()
                    }
                    for ((sym, kl) in fetchedPairs) {
                        if (kl.isNotEmpty()) {
                            kLinesMap[sym] = kl
                        }
                    }
                }

                // 4. 基于全套真实行情 + 真实K线 + 沪深300基准最终严格求解
                val finalPortfolio = QuantEngine.generateRetailTop10Portfolio(strategyKey, quotesMap, kLinesMap, benchKLines)
                _uiState.update { current ->
                    val updatedAudit = if (current.quote != null && current.factorScores != null) {
                        val allQ = (current.screenerQuotes + quotesMap.values + current.watchlistQuotes + listOf(current.quote))
                            .distinctBy { it.symbol }
                        QuantEngine.computeObjectiveQuantAuditReport(
                            quote = current.quote,
                            kLines = current.kLines,
                            factorScores = current.factorScores,
                            universeQuotes = allQ,
                            universeKLines = kLinesMap,
                            benchmarkKLines = benchKLines
                        )
                    } else current.objectiveAuditReport

                    val next = current.copy(
                        retailTop10Portfolio = finalPortfolio,
                        retailUniverseQuotes = quotesMap.values.toList(),
                        retailUniverseKLines = kLinesMap.toMap(),
                        benchmarkKLines = benchKLines,
                        objectiveAuditReport = updatedAudit
                    )
                    next.copy(marketBreadth = computeBreadthForState(next))
                }
            } catch (e: Exception) {
                // 网络异常时优雅降级保留当前已计算的组合与约束校验
            }
        }
    }

    fun batchAddTop10ToWatchlist() {
        val state = _uiState.value
        val portfolio = state.retailTop10Portfolio ?: return
        viewModelScope.launch {
            for (item in portfolio.stocks) {
                repository.addToWatchlist(item.quote.symbol, item.quote.name)
                val kl = state.retailUniverseKLines[item.quote.symbol].orEmpty()
                val scores = QuantEngine.evaluateFactors(item.quote, kl)
                repository.updateWatchlistScores(
                    symbol = item.quote.symbol,
                    composite = scores.compositeScore,
                    value = scores.valueScore,
                    growth = scores.growthScore,
                    momentum = scores.momentumScore,
                    quality = scores.qualityScore,
                    risk = scores.riskControlScore,
                    rating = scores.rating
                )
            }
        }
    }

    fun saveTradePlan(
        symbol: String,
        stockName: String,
        entryPrice: Double,
        stopPrice: Double,
        targetPrice: Double,
        shares: Int,
        plannedRiskRmb: Double,
        gateVerdict: String,
        thesis: String
    ) {
        viewModelScope.launch {
            repository.insertTradeJournalEntry(
                TradeJournalEntity(
                    symbol = symbol,
                    stockName = stockName,
                    status = "PLANNED",
                    entryPrice = entryPrice,
                    stopPrice = stopPrice,
                    targetPrice = targetPrice,
                    shares = shares,
                    plannedRiskRmb = plannedRiskRmb,
                    gateVerdict = gateVerdict,
                    thesis = thesis.ifBlank { "1R 纪律止损与目标盈亏比执行计划" }
                )
            )
        }
    }

    fun markTradeOpened(id: Long) {
        viewModelScope.launch {
            repository.markTradeOpened(id)
        }
    }

    fun closeTradeEntry(
        id: Long,
        entryPrice: Double,
        stopPrice: Double,
        shares: Int,
        exitPrice: Double
    ) {
        viewModelScope.launch {
            val effectiveShares = shares.coerceAtLeast(100)
            val pnl = kotlin.math.round((exitPrice - entryPrice) * effectiveShares * 100.0) / 100.0
            val riskPerShare = (entryPrice - stopPrice).coerceAtLeast(0.01)
            val rMultiple = kotlin.math.round(((exitPrice - entryPrice) / riskPerShare) * 100.0) / 100.0
            repository.closeTradeEntry(
                id = id,
                exitPrice = exitPrice,
                pnlRmb = pnl,
                rMultiple = rMultiple
            )
        }
    }

    fun deleteTradeEntry(id: Long) {
        viewModelScope.launch {
            repository.deleteTradeEntry(id)
        }
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
