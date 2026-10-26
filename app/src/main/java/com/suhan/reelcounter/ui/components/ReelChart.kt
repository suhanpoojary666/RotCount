package com.suhan.reelcounter.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suhan.reelcounter.ui.theme.ReelTheme

@Composable
fun ReelChart(
    values: List<Int>,
    isDayMode: Boolean,
    isPastWeek: Boolean,
    currentHighlightIndex: Int,
    modifier: Modifier = Modifier
) {
    // Meaningful Y-axis scaling: minimum scale of 10 so 1 or 2 reels don't blow up to 100% full height
    val highestCount = remember(values) { values.maxOrNull() ?: 0 }
    val maxVal = remember(highestCount) { highestCount.coerceAtLeast(10) }
    val isEmpty = remember(values) { values.all { it == 0 } }

    var selectedBarIndex by remember { mutableStateOf<Int?>(null) }

    // Smooth entry animation
    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(isEmpty) {
        if (!isEmpty) {
            animationProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ReelTheme.colors.surface)
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (isEmpty) {
                // Empty state: height 215dp with baseline, then Footnote under
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(215.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(ReelTheme.colors.separator)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "No reels counted yet.",
                    style = ReelTheme.typography.footnote,
                    color = ReelTheme.colors.textSecondary
                )
            } else {
                // Active Chart Area: 215dp height
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(215.dp)
                ) {
                    // Baseline
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(ReelTheme.colors.separator)
                    )

                    // Bars Row with numbers above each bar
                    val barSpacing = if (isDayMode) 2.dp else 8.dp
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(barSpacing),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        values.forEachIndexed { index, value ->
                            val isAccent = if (isPastWeek) true else (index == currentHighlightIndex)
                            val barColor = if (isAccent) ReelTheme.colors.accent else ReelTheme.colors.barMuted

                            // Bar height calculation: up to 92% of chart height
                            val heightFraction = if (value > 0) {
                                val frac = (value.toFloat() / maxVal.toFloat()) * 0.92f * animationProgress.value
                                frac.coerceIn(0.04f, 0.92f)
                            } else {
                                0f
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                // The bar itself (numbers above removed per user request; tooltips on press show exact details)
                                if (value > 0 && heightFraction > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight(fraction = heightFraction)
                                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                            .background(barColor)
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onPress = {
                                                        selectedBarIndex = index
                                                        tryAwaitRelease()
                                                        selectedBarIndex = null
                                                    }
                                                )
                                            }
                                    )
                                } else {
                                    // Empty hour baseline
                                    Spacer(modifier = Modifier.height(1.dp))
                                }
                            }
                        }
                    }

                    // Floating Tooltip when a bar is pressed
                    selectedBarIndex?.let { idx ->
                        val count = values.getOrNull(idx) ?: 0
                        val label = if (isDayMode) {
                            val h = idx
                            val period = if (h < 12) "AM" else "PM"
                            val displayH = if (h == 0) 12 else if (h > 12) h - 12 else h
                            "$displayH $period: $count reels"
                        } else {
                            val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                            "${days.getOrElse(idx) { "" }}: $count reels"
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .clip(RoundedCornerShape(6.dp))
                                .background(ReelTheme.colors.text)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = label,
                                color = ReelTheme.colors.background,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // X-Axis Labels Row below the baseline
                Spacer(modifier = Modifier.height(10.dp))
                if (isDayMode) {
                    // Full-width, unclipped 24-hour timeline with complete, visible timestamps
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                    ) {
                        val totalW = maxWidth

                        // 12 AM (Start, hour 0)
                        Text(
                            text = "12 AM",
                            style = ReelTheme.typography.axisLabel,
                            color = ReelTheme.colors.textSecondary,
                            modifier = Modifier.align(Alignment.CenterStart),
                            softWrap = false
                        )

                        // 6 AM (Hour 6, 25%)
                        Text(
                            text = "6 AM",
                            style = ReelTheme.typography.axisLabel,
                            color = ReelTheme.colors.textSecondary,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = totalW * 0.25f - 14.dp),
                            softWrap = false
                        )

                        // 12 PM (Hour 12, 50%)
                        Text(
                            text = "12 PM",
                            style = ReelTheme.typography.axisLabel,
                            color = ReelTheme.colors.textSecondary,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = totalW * 0.50f - 14.dp),
                            softWrap = false
                        )

                        // 6 PM (Hour 18, 75%)
                        Text(
                            text = "6 PM",
                            style = ReelTheme.typography.axisLabel,
                            color = ReelTheme.colors.textSecondary,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = totalW * 0.75f - 14.dp),
                            softWrap = false
                        )

                        // 11 PM (End, hour 23)
                        Text(
                            text = "11 PM",
                            style = ReelTheme.typography.axisLabel,
                            color = ReelTheme.colors.textSecondary,
                            modifier = Modifier.align(Alignment.CenterEnd),
                            softWrap = false
                        )
                    }
                } else {
                    // Week labels: M T W T F S S centered beneath each bar
                    val weekLabels = listOf("M", "T", "W", "T", "F", "S", "S")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        weekLabels.forEachIndexed { i, label ->
                            val isToday = (i == currentHighlightIndex && !isPastWeek)
                            Text(
                                text = label,
                                modifier = Modifier.weight(1f),
                                style = ReelTheme.typography.axisLabel,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                color = if (isToday) ReelTheme.colors.accent else ReelTheme.colors.textSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
