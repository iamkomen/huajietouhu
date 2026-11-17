package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig
import com.example.data.model.AppChangelogCatalog
import com.example.data.model.AppVersionRelease
import com.example.data.model.ChangeCategory
import com.example.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VersionChangelogDialog(
    onDismiss: () -> Unit,
    releases: List<AppVersionRelease> = AppChangelogCatalog.releases
) {
    var selectedCategory by remember { mutableStateOf(ChangeCategory.ALL) }
    val listState = rememberLazyListState()

    LaunchedEffect(selectedCategory) {
        listState.scrollToItem(0)
    }

    val totalCountByCategory = remember(releases) {
        val allItems = releases.flatMap { it.changes }
        mapOf(
            ChangeCategory.ALL to allItems.size,
            ChangeCategory.FEATURE to allItems.count { it.category == ChangeCategory.FEATURE },
            ChangeCategory.FIX to allItems.count { it.category == ChangeCategory.FIX },
            ChangeCategory.IMPROVEMENT to allItems.count { it.category == ChangeCategory.IMPROVEMENT }
        )
    }

    val filteredReleases = remember(releases, selectedCategory) {
        if (selectedCategory == ChangeCategory.ALL) {
            releases
        } else {
            releases.mapNotNull { release ->
                val matching = release.changes.filter { it.category == selectedCategory }
                if (matching.isEmpty()) null else release.copy(changes = matching)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 620.dp)
                .heightIn(max = 660.dp)
                .testTag("version_changelog_dialog"),
            shape = RoundedCornerShape(18.dp),
            color = TerminalCard,
            border = BorderStroke(1.dp, TerminalBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(WallStreetGoldSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "版本演进与修复日志",
                                tint = WallStreetGold,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "版本演进与修复记录",
                                color = TextPrimary,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "当前版本 v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) · 按实际交付整理",
                                color = TextMuted,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("changelog_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭版本日志",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Category Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ChangeCategory.entries.forEach { category ->
                        val selected = selectedCategory == category
                        val count = totalCountByCategory[category] ?: 0
                        val accentColor = when (category) {
                            ChangeCategory.ALL -> WallStreetGold
                            ChangeCategory.FEATURE -> QuantCyan
                            ChangeCategory.FIX -> SuccessGreen
                            ChangeCategory.IMPROVEMENT -> WarningAmber
                        }
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minHeight = 36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) accentColor.copy(alpha = 0.16f) else TerminalSurfaceElevated)
                                .border(
                                    width = 1.dp,
                                    color = if (selected) accentColor else TerminalBorder,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedCategory = category }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("changelog_filter_${category.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${category.label} ($count)",
                                color = if (selected) accentColor else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Timeline List
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .testTag("changelog_release_list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredReleases, key = { it.versionName }) { release ->
                        ReleaseCard(release = release)
                    }

                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(TerminalBg.copy(alpha = 0.7f))
                                .border(0.75.dp, TerminalBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "说明：以上每条更新均严格对应项目实际落地的算法引擎、界面修复与自动化回归测试记录，不含任何虚构或未实现功能。",
                                color = TextMuted,
                                fontSize = 10.sp,
                                lineHeight = 14.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReleaseCard(release: AppVersionRelease) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TerminalSurfaceElevated)
            .border(
                width = if (release.isCurrent) 1.dp else 0.75.dp,
                color = if (release.isCurrent) WallStreetGold.copy(alpha = 0.45f) else TerminalBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(10.dp)
            .testTag("changelog_release_${release.versionName}"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(WallStreetGoldSoft)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "v${release.versionName}",
                    color = WallStreetGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
            if (release.versionCode != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(TerminalCard)
                        .border(0.6.dp, TerminalBorder, RoundedCornerShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Build ${release.versionCode}",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            if (release.isCurrent) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(SuccessGreenSoft)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "当前版本",
                        color = SuccessGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Text(
            text = release.title,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = release.summary,
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 15.5.sp
        )

        HorizontalDivider(color = TerminalBorder.copy(alpha = 0.6f), thickness = 0.75.dp)

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            release.changes.forEach { change ->
                val (badgeBg, badgeColor) = when (change.category) {
                    ChangeCategory.FEATURE -> QuantCyanSoft to QuantCyan
                    ChangeCategory.FIX -> SuccessGreenSoft to SuccessGreen
                    ChangeCategory.IMPROVEMENT, ChangeCategory.ALL -> WallStreetGoldSoft to WarningAmber
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeBg)
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        Text(
                            text = change.category.label,
                            color = badgeColor,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Spacer(modifier = Modifier.width(7.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = change.title,
                            color = TextPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = change.detail,
                            color = TextMuted,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}
