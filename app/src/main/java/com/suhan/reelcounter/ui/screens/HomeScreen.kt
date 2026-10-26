package com.suhan.reelcounter.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suhan.reelcounter.R
import com.suhan.reelcounter.ui.components.ReelChart
import com.suhan.reelcounter.ui.components.ReelHeatmap
import com.suhan.reelcounter.ui.components.ReelSegmentedControl
import com.suhan.reelcounter.ui.theme.ReelTheme
import com.suhan.reelcounter.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedSegment by viewModel.selectedSegment.collectAsState()
    val dayState by viewModel.dayUiState.collectAsState()
    val weekState by viewModel.weekUiState.collectAsState()
    val heatmapData by viewModel.heatmapData.collectAsState()

    val pagerState = rememberPagerState(initialPage = selectedSegment) { 3 }
    val coroutineScope = rememberCoroutineScope()

    // Sync swipe gestures with ViewModel segment
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != selectedSegment) {
            viewModel.selectSegment(pagerState.currentPage)
        }
    }

    // Sync segmented control taps with Pager
    LaunchedEffect(selectedSegment) {
        if (pagerState.currentPage != selectedSegment) {
            pagerState.animateScrollToPage(selectedSegment)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ReelTheme.colors.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(horizontal = 20.dp)
                .padding(top = 28.dp)
        ) {
            // 1. Header: Clean, centered "RotCount" title with settings icon on the right
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(
                    text = "RotCount",
                    style = ReelTheme.typography.largeTitle.copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = ReelTheme.colors.text,
                    modifier = Modifier.align(Alignment.Center)
                )

                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(48.dp)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onNavigateToSettings
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_settings),
                        contentDescription = "Settings",
                        modifier = Modifier.size(26.dp),
                        colorFilter = ColorFilter.tint(ReelTheme.colors.text)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Segmented control: "Day", "Week", "Year"
            ReelSegmentedControl(
                selectedIndex = pagerState.currentPage,
                onSegmentSelected = { page ->
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(page)
                    }
                },
                items = listOf("Day", "Week", "Year")
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 3. Swipeable Horizontal Pager for Day (0), Week (1), and Year (2)
            // Weight(1f) ensures pager extends all the way to the bottom border of the screen,
            // so swiping anywhere (including from the bottom) switches smoothly between Day, Week, and Year.
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.Top
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 32.dp)
                ) {
                    when (page) {
                        0 -> {
                            // Sub-heading: Today
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Today",
                                    style = ReelTheme.typography.body,
                                    color = ReelTheme.colors.textSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.height(26.dp))

                            // Hero count & caption
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = dayState.todayCount.toString(),
                                    style = ReelTheme.typography.heroNumber.copy(fontSize = 72.sp),
                                    color = ReelTheme.colors.text,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = "reels today",
                                    style = ReelTheme.typography.body,
                                    color = ReelTheme.colors.textSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.height(34.dp))

                            // Day Histogram
                            ReelChart(
                                values = dayState.hourlyBars,
                                isDayMode = true,
                                isPastWeek = false,
                                currentHighlightIndex = dayState.currentHour
                            )
                        }
                        1 -> {
                            // Sub-heading: Week range with chevrons
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val leftInteraction = remember { MutableInteractionSource() }
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .alpha(if (weekState.canGoLeft) 1f else 0.3f)
                                        .clickable(
                                            interactionSource = leftInteraction,
                                            indication = null,
                                            enabled = weekState.canGoLeft
                                        ) {
                                            viewModel.changeWeek(-1)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_chevron_left),
                                        contentDescription = "Previous week",
                                        modifier = Modifier.size(22.dp),
                                        colorFilter = ColorFilter.tint(ReelTheme.colors.accent)
                                    )
                                }

                                Text(
                                    text = weekState.label,
                                    style = ReelTheme.typography.body,
                                    color = ReelTheme.colors.textSecondary,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center
                                )

                                val rightInteraction = remember { MutableInteractionSource() }
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .alpha(if (weekState.canGoRight) 1f else 0.3f)
                                        .clickable(
                                            interactionSource = rightInteraction,
                                            indication = null,
                                            enabled = weekState.canGoRight
                                        ) {
                                            viewModel.changeWeek(1)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_chevron_right),
                                        contentDescription = "Next week",
                                        modifier = Modifier.size(22.dp),
                                        colorFilter = ColorFilter.tint(ReelTheme.colors.accent)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(26.dp))

                            // Hero count & caption
                            val captionText = if (weekState.isCurrentWeek) "reels this week" else "reels that week"
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = weekState.weekTotal.toString(),
                                    style = ReelTheme.typography.heroNumber.copy(fontSize = 72.sp),
                                    color = ReelTheme.colors.text,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = captionText,
                                    style = ReelTheme.typography.body,
                                    color = ReelTheme.colors.textSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.height(34.dp))

                            // Week Histogram
                            ReelChart(
                                values = weekState.dayBars,
                                isDayMode = false,
                                isPastWeek = !weekState.isCurrentWeek,
                                currentHighlightIndex = weekState.currentDayIndex
                            )
                        }
                        2 -> {
                            // Sub-heading: This year
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "This year",
                                    style = ReelTheme.typography.body,
                                    color = ReelTheme.colors.textSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.height(26.dp))

                            // Hero count & caption
                            val yearTotal = heatmapData.values.sum()
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = yearTotal.toString(),
                                    style = ReelTheme.typography.heroNumber.copy(fontSize = 72.sp),
                                    color = ReelTheme.colors.text,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = "reels this year",
                                    style = ReelTheme.typography.body,
                                    color = ReelTheme.colors.textSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.height(34.dp))

                            // Year Heatmap
                            ReelHeatmap(
                                heatmapData = heatmapData
                            )
                        }
                    }
                }
            }
        }
    }
}
