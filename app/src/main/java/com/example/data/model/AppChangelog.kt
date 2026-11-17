package com.example.data.model

enum class ChangeCategory(val label: String) {
    ALL("全部"),
    FEATURE("新增"),
    FIX("修复"),
    IMPROVEMENT("优化")
}

data class VersionChangeItem(
    val category: ChangeCategory,
    val title: String,
    val detail: String
)

data class AppVersionRelease(
    val versionName: String,
    val versionCode: Int?,
    val title: String,
    val summary: String,
    val isCurrent: Boolean = false,
    val changes: List<VersionChangeItem>
)

/**
 * 项目真实版本演进与修复记录。
 * 严格对应代码库实际已落地的模块、缺陷修复与自动化回归测试，不虚构未上线内容。
 */
object AppChangelogCatalog {

    val releases: List<AppVersionRelease> = listOf(
        AppVersionRelease(
            versionName = "1.4.3",
            versionCode = 8,
            title = "全项目证券级合规审计：多因子口径对齐、A股涨跌停规则补全与输入防覆写",
            summary = "按证券交易软件生产标准完成全代码库核查，修复多因子选股口径不一致、ST 5%涨跌幅缺失、非美区Locale小数点解析异常及盘中轮询覆写止损输入等缺陷。",
            isCurrent = true,
            changes = listOf(
                VersionChangeItem(
                    category = ChangeCategory.FIX,
                    title = "修复策略选股过滤与排序/卡片展示的 K 线因子口径不一致",
                    detail = "统一在 ScreenerScreen 预计算包含日 K 线的 factorScoresBySymbol 并共享至过滤、评分/ROE排序与候选卡片，避免排序与卡片沿用无 K 线简化得分导致的分值错位；同步修正指数平盘 0.00% 被误标红的配色。"
                ),
                VersionChangeItem(
                    category = ChangeCategory.FIX,
                    title = "补全 A 股 ST/*ST 5% 涨跌停风控与北交所 30% 异常跳空边界",
                    detail = "在 T+1 极端跌停测算与前复权连续性检验中补全 ST/*ST ±5% 涨跌幅与北交所 ±30% 边界；统一全市场搜索直连市场推断与 StockApiService.inferMarketFromCode 逻辑，避免 ETF/可转债误判为北交所。"
                ),
                VersionChangeItem(
                    category = ChangeCategory.FIX,
                    title = "修复盘中轮询覆写手输止损价、自选重复告警竞态与全项目 Locale.US 格式化",
                    detail = "为 PreTradeExecutionCard 止损价与风险比增加自定义状态锁，避免 6 秒轮询覆写用户手输参数；跳过主轮询已落库标的的二次自选告警检测；全量将 String.format 固定为 Locale.US，并修复行情缺省涨跌幅、未满年报 ROE/ROIC 年化、OCF 负数反转与“¥¥”重复货币符号等边界缺陷。"
                )
            )
        ),
        AppVersionRelease(
            versionName = "1.4.2",
            versionCode = 7,
            title = "标的检索重构为本地持久化字典与定时同步架构",
            summary = "取消每次输入触发全市场在线检索的卡顿逻辑，改用 Room 持久化本地 A 股字典与 12 小时定时后台更新，实现零延迟毫秒级搜索。",
            isCurrent = false,
            changes = listOf(
                VersionChangeItem(
                    category = ChangeCategory.FIX,
                    title = "彻底消除输入名称或拼音首字母时的全网转圈卡顿",
                    detail = "移除 searchStocks 每次按键触发实时多源在线搜索与等待弹窗机制，改为纯内存与 Room 持久化字典（stock_dictionary）同步匹配，中国海油等标的即时秒出，不再出现卡在“正在全市场穿透检索”的状态。"
                ),
                VersionChangeItem(
                    category = ChangeCategory.IMPROVEMENT,
                    title = "引入 12 小时定时全市场字典同步与多源分页兜底",
                    detail = "数据库升级至 v4，新增 stock_dictionary 与 dictionary_meta 持久化表；后台按 12 小时有效期自动分页拉取东方财富 / 备援主机 / 新浪全 A 股目录，同时保留搜索框右上角「更新字典」手动同步入口与 6 位代码直连自动学习收录。"
                )
            )
        ),
        AppVersionRelease(
            versionName = "1.4.1",
            versionCode = 6,
            title = "修复指数切换后 DCF 选股仍残留原指数成分股的问题",
            summary = "为策略选股中枢引入严格的指数成分池隔离、并发请求抢占取消与东方财富批量行情备援链路。",
            isCurrent = false,
            changes = listOf(
                VersionChangeItem(
                    category = ChangeCategory.FIX,
                    title = "修复切换指数后 DCF 仍筛选原始 13 只沪深300 个股",
                    detail = "在 ViewModel 与 ScreenerScreen 中双重按当前选中指数（沪深300 / 上证50 / 科创50 / 创业板指 / 中证500 / 新股次新）成分股集合严格过滤，并在切换指数时取消过期异步请求、清空非当前指数缓存与重置历史 DCF 曲线目标。"
                ),
                VersionChangeItem(
                    category = ChangeCategory.IMPROVEMENT,
                    title = "加固全指数成分股实时行情链路与加载重试状态",
                    detail = "修复 OkHttp 响应未关闭导致的连接池阻塞，提升并发通道数并新增东方财富批量行情备援补全，同时在切换指数时展示同步中状态与一键重试入口。"
                )
            )
        ),
        AppVersionRelease(
            versionName = "1.4",
            versionCode = 5,
            title = "独立 DCF 估值选股指标：七折击球筛选与安全边际排序",
            summary = "将专业 DCF 现金流折现估值从单股研报与一般价值评分中抽离，成为策略选股中枢的一级独立筛选、排序与展示指标。",
            isCurrent = false,
            changes = listOf(
                VersionChangeItem(
                    category = ChangeCategory.FEATURE,
                    title = "选股器新增 DCF 独立策略筛选与边际排序",
                    detail = "在策略选股中枢新增「DCF七折击球 (边际≥30%)」与「DCF深度低估 (边际≥15%)」独立筛选条件及「DCF边际↓」排序维度，直接按概率加权内在公允价折价空间过滤与排序全指数池。"
                ),
                VersionChangeItem(
                    category = ChangeCategory.FEATURE,
                    title = "候选卡片新增独立 DCF 指标透视条",
                    detail = "每只筛选标的卡片新增独立 DCF 指标条，直观展示 DCF 估值状态、概率加权公允价、安全边际百分比、逆向 DCF 隐含复合增速与猫会计现金流质量评级，优先联动已同步的 F10 三表数据。"
                ),
                VersionChangeItem(
                    category = ChangeCategory.FEATURE,
                    title = "量化选股界面新增历史 DCF 估值曲线组件",
                    detail = "在策略选股顶部新增原生交互式历史 DCF 估值曲线与安全区间图（ScreenerHistoricalDcfChartCard），叠加真实日K线股价、DCF 公允中枢、七折击球安全区与乐观上轨带，支持标的切换与触摸十字线逐日检视安全边际和历史分位。"
                )
            )
        ),
        AppVersionRelease(
            versionName = "1.3",
            versionCode = 4,
            title = "盈利闭环引擎：VCP紧缩形态、市场广度门控、1R定仓与交易账本",
            summary = "吸收系统化波段交易核心盈利风控框架，落地二阶段紧缩突破识别、实盘广度仓位上限、五重开仓闸门与 R 倍数期望复盘闭环。",
            isCurrent = false,
            changes = listOf(
                VersionChangeItem(
                    category = ChangeCategory.FEATURE,
                    title = "Stage-2 趋势模板与 VCP 波动量能紧缩选股",
                    detail = "在个股终端与策略选股中枢新增 VCP 二阶段紧缩突破识别（波幅收敛比、量能萎缩比、MA20 偏离防追高、相对沪深300强弱 RS 及 7 项买点体检），提前捕捉低风险紧缩枢轴。"
                ),
                VersionChangeItem(
                    category = ChangeCategory.FEATURE,
                    title = "1R 以损定仓与五重交易前铁律闸门",
                    detail = "在个股页面新增 PreTradeExecutionCard：根据账户总资金、1R 最大允许亏损比例与结构性止损价自动换算 A 股 100 股整手可买上限及 1R/2R/3R 目标价，并执行广度/趋势/偏离度/基本面排雷/T+1 五重闸门拦截。"
                ),
                VersionChangeItem(
                    category = ChangeCategory.FEATURE,
                    title = "全市场实盘广度门控与 Room 持久化交易账本",
                    detail = "新增市场广度与动态敞口教练（MarketBreadthCoachCard）及数据库 v3 交易账本（trade_journal），支持一键保存交易计划、录入平仓卖出价并自动统计实盘胜率、盈利因子与 R 倍数数学期望 E(R)。"
                )
            )
        ),
        AppVersionRelease(
            versionName = "1.2",
            versionCode = 3,
            title = "交互式 DCF 估值计算器与版本变更日志",
            summary = "落地「猫会计说投资」交互式现金流折现计算器，并在应用内提供真实版本演进档案。",
            isCurrent = false,
            changes = listOf(
                VersionChangeItem(
                    category = ChangeCategory.FEATURE,
                    title = "个股分析页「猫会计」DCF 估值计算器",
                    detail = "在个股终端新增 DcfCalculatorCard，支持直接输入与修改基期自由现金流 FCF₀（亿元）、预测期增速 g₁（%）、折现率 r（%）、净现金调整（亿元），支持 5 年 / 10 年预测期及八折 / 七折 / 六折击球安全边际档位切换，并可一键同步实盘基准与展开逐年折现明细表。"
                ),
                VersionChangeItem(
                    category = ChangeCategory.FEATURE,
                    title = "终值双算法与防溢出风险校验",
                    detail = "支持「永续增长率法」与「退出倍数法」双终值模式；内置 r ≤ g∞ 防溢出保护，并对永续增速高于 3%、显性期增速高于 20%、终值占比超 75% 及基期 FCF ≤ 0 触发风险警示。"
                ),
                VersionChangeItem(
                    category = ChangeCategory.FEATURE,
                    title = "应用内版本演进与修复日志",
                    detail = "在行情中枢顶部版本号入口与因子沉淀页加入版本变更弹窗，按新增 / 修复 / 优化分类如实记录历次功能迭代与缺陷修复。"
                )
            )
        ),
        AppVersionRelease(
            versionName = "1.1",
            versionCode = 2,
            title = "猫会计专业 DCF 体系、日线修复与全市场检索重构",
            summary = "升级 F10 财报排雷审计与三情景 DCF 研报，修复日K线状态异常及拼音/中文检索失效。",
            changes = listOf(
                VersionChangeItem(
                    category = ChangeCategory.IMPROVEMENT,
                    title = "「猫会计说投资」专业 DCF 估值与财报体检",
                    detail = "接入东方财富 F10 财报实盘指标，增加净现比、Capex 吞噬度、账面净现金及大存大贷/商誉排雷，实现 Bear/Base/Bull 概率加权估值与逆向 DCF 隐含增速求解；银行与非银金融股自动切换超额 ROE 剩余收益模型。"
                ),
                VersionChangeItem(
                    category = ChangeCategory.FIX,
                    title = "修复日K线状态异常与 OHLC 脏数据问题",
                    detail = "新增 sanitizeKLines 与 reconcileDailyKLinesWithLiveQuote，过滤无效日期、NaN 及负成交量脏点，修复高低价倒挂与重复日期，并将盘中实时行情正确并入当日最后一根日线，准确区分盘中交易、午间休市、周末休市与盘后停牌状态。"
                ),
                VersionChangeItem(
                    category = ChangeCategory.FIX,
                    title = "修复中文名称与拼音首字母标的检索失效",
                    detail = "重构多源并发联想检索（腾讯 Smartbox Unicode 转义解码、新浪 Suggest、东方财富联想与全A股目录缓存），自研 3755+ 汉字及多音字拼音首字母生成器（toPinyinInitials），修复输入拼音首字母或中文名称无法搜出标的的问题。"
                )
            )
        ),
        AppVersionRelease(
            versionName = "1.0.3",
            versionCode = null,
            title = "WindowSizeClass 响应式竖屏与多密度适配",
            summary = "依据 WindowSizeClass 重构多尺寸屏幕栅格与竖屏防裁剪布局。",
            changes = listOf(
                VersionChangeItem(
                    category = ChangeCategory.IMPROVEMENT,
                    title = "多屏幕尺寸自适应导航与栅格切换",
                    detail = "引入 AdaptiveLayoutSpec，按 Compact / Medium / Expanded 窗口尺寸动态调整列数与内外边距，并在宽屏设备自动由底部导航切换为侧边 NavigationRail。"
                ),
                VersionChangeItem(
                    category = ChangeCategory.FIX,
                    title = "修复极窄竖屏与大字号下的排版溢出裁切",
                    detail = "针对 320dp 窄屏与 1.3x 放大字体优化行情头部指标芯片、K线与分时图高度、波动率热力图列数与华尔街研报卡片布局，消除横向溢出与按钮文字截断。"
                )
            )
        ),
        AppVersionRelease(
            versionName = "1.0.2",
            versionCode = null,
            title = "自选股参数归零修复与点击穿透验收",
            summary = "解决自选列表五维评分参数显示为 0 的同步缺陷，完成交互穿透审计。",
            changes = listOf(
                VersionChangeItem(
                    category = ChangeCategory.FIX,
                    title = "修复自选标的五维评分参数显示为 0 未更新",
                    detail = "补全自选股行情聚合后的异步多因子重算与 Room 数据库回写同步逻辑，确保进入自选池与切换个股后综合分、价值、成长、动量与质量分实时刷新。"
                ),
                VersionChangeItem(
                    category = ChangeCategory.IMPROVEMENT,
                    title = "完善自选管理与异动提醒交互链路",
                    detail = "验证并加固自选增删、异动提醒开关以及 ±3 / ±5 / ±8 / ±10 分告警阈值循环切换的本地持久化生效。"
                )
            )
        ),
        AppVersionRelease(
            versionName = "1.0.1",
            versionCode = null,
            title = "自选波动率热力图与客观数理质检体系",
            summary = "在自选 10 股约束下引入波动率风险热力图，并建立外部数理基准校验。",
            changes = listOf(
                VersionChangeItem(
                    category = ChangeCategory.FEATURE,
                    title = "散户十绝波动率风险热力图",
                    detail = "新增 VolatilityHeatmapCard，对低/中/高/极高四档波动率资产进行颜色编码与风险预算统计，支持按风险等级点击筛选，辅助控制在 10 只持仓上限内的尾部风险。"
                ),
                VersionChangeItem(
                    category = ChangeCategory.IMPROVEMENT,
                    title = "建立外部客观数理实证验证模块",
                    detail = "新增 ObjectiveValidationCard，纳入标准 EMA/MACD/Wilder RSI6/BOLL20 指标核验、年化波动率 HV20、Cornish-Fisher 尾部 VaR、Almgren-Chriss 流动性冲击成本及截面 Spearman Rank IC 显著性检验。"
                )
            )
        ),
        AppVersionRelease(
            versionName = "1.0",
            versionCode = 1,
            title = "初版 A 股实盘量化终端架构上线",
            summary = "搭建五大核心量化终端页面，接入实时行情流与本地持久化数据库。",
            changes = listOf(
                VersionChangeItem(
                    category = ChangeCategory.FEATURE,
                    title = "五大终端功能区与实盘量化引擎",
                    detail = "上线行情中枢、自选标的、策略选股、风险监控与因子沉淀五大模块；直连腾讯财经实时 Level-1 行情与日K线流，内置 Room 数据库（quant_terminal_db）、五维因子雷达、半凯利公式仓位控制、T+1 跌停缓冲与股指期货对冲测算。"
                )
            )
        )
    )
}
