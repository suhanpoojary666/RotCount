package com.suhan.reelcounter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suhan.reelcounter.ui.theme.ReelTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ReelHeatmap(
    heatmapData: Map<LocalDate, Int>,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now() }
    val currentYear = today.year
    var pressedDate by remember { mutableStateOf<LocalDate?>(null) }

    val activeDays = remember(heatmapData) { heatmapData.count { it.value > 0 } }

    // Display months from January up to current month (future months are excluded)
    val months = remember(currentYear, today.monthValue) {
        (1..today.monthValue).map { YearMonth.of(currentYear, it) }
    }

    val horizontalScrollState = rememberScrollState()
    val density = LocalDensity.current

    val boxSize = 21.dp
    val boxSpacing = 4.dp
    val monthSpacing = 16.dp

    // Auto-scroll tape to current month on initial launch
    LaunchedEffect(months.size) {
        // Approximate width per month: ~5 weeks * (21dp + 4dp) + 16dp = ~141dp
        if (months.size > 2) {
            val targetMonthIndex = (months.size - 1).coerceAtLeast(0)
            val targetDp = 141.dp * (targetMonthIndex - 1)
            val scrollPx = with(density) { targetDp.roundToPx() }
            horizontalScrollState.animateScrollTo(scrollPx)
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
            // 1. Header: Reel Activity & Active Days on left, pressed info tab on right (opposite to Reel Activity)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Reel Activity",
                        style = ReelTheme.typography.body.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = ReelTheme.colors.text
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "$activeDays active ${if (activeDays == 1) "day" else "days"}",
                        style = ReelTheme.typography.footnote,
                        color = ReelTheme.colors.textSecondary
                    )
                }

                // Info tab that shows up when a day is pressed, placed opposite to Reel Activity heading
                pressedDate?.let { date ->
                    val count = heatmapData[date] ?: 0
                    val dateLabel = date.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))
                    val label = "$dateLabel: $count ${if (count == 1) "reel" else "reels"}"

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ReelTheme.colors.text)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
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

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Horizontally Scrollable Months Tape (Jan..current month, 7 rows Mon..Sun)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(horizontalScrollState),
                horizontalArrangement = Arrangement.spacedBy(monthSpacing)
            ) {
                months.forEach { yearMonth ->
                    MonthHeatmapBlock(
                        yearMonth = yearMonth,
                        today = today,
                        heatmapData = heatmapData,
                        pressedDate = pressedDate,
                        boxSize = boxSize,
                        boxSpacing = boxSpacing,
                        onDatePressed = { date ->
                            pressedDate = date
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthHeatmapBlock(
    yearMonth: YearMonth,
    today: LocalDate,
    heatmapData: Map<LocalDate, Int>,
    pressedDate: LocalDate?,
    boxSize: androidx.compose.ui.unit.Dp,
    boxSpacing: androidx.compose.ui.unit.Dp,
    onDatePressed: (LocalDate?) -> Unit
) {
    val firstDay = yearMonth.atDay(1)
    val daysInMonth = yearMonth.lengthOfMonth()
    val startDayOfWeek = firstDay.dayOfWeek.value // 1 = Monday, 7 = Sunday
    val leadingEmpty = startDayOfWeek - 1
    val totalSlots = leadingEmpty + daysInMonth
    val numColumns = (totalSlots + 6) / 7

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 7 Rows of Boxes for this month
        Row(horizontalArrangement = Arrangement.spacedBy(boxSpacing)) {
            for (c in 0 until numColumns) {
                Column(verticalArrangement = Arrangement.spacedBy(boxSpacing)) {
                    for (r in 0..6) {
                        val slotIndex = c * 7 + r
                        if (slotIndex < leadingEmpty || slotIndex >= totalSlots) {
                            // Blank empty space outside this month
                            Spacer(modifier = Modifier.size(boxSize))
                        } else {
                            val dayOfMonth = slotIndex - leadingEmpty + 1
                            val date = yearMonth.atDay(dayOfMonth)
                            val isFuture = date.isAfter(today)
                            val count = heatmapData[date] ?: 0
                            val isPressed = date == pressedDate

                            val cellColor = getHeatmapCellColor(count, isFuture)

                            Box(
                                modifier = Modifier
                                    .size(boxSize)
                                    .clip(RoundedCornerShape(4.dp))
                                    .then(
                                        if (isPressed) {
                                            Modifier.border(1.5.dp, Color.White, RoundedCornerShape(4.dp))
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .background(cellColor)
                                    .pointerInput(date, isFuture) {
                                        if (!isFuture) {
                                            detectTapGestures(
                                                onPress = {
                                                    onDatePressed(date)
                                                    tryAwaitRelease()
                                                    onDatePressed(null)
                                                }
                                            )
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Month Name Label at the BOTTOM (Centered under this month's block)
        val monthShort = yearMonth.month.getDisplayName(TextStyle.SHORT, Locale.US)
        val isCurrentMonth = yearMonth.year == today.year && yearMonth.monthValue == today.monthValue

        Text(
            text = monthShort,
            fontSize = 12.sp,
            fontWeight = if (isCurrentMonth) FontWeight.Bold else FontWeight.Normal,
            color = if (isCurrentMonth) ReelTheme.colors.accent else ReelTheme.colors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

private fun getHeatmapCellColor(count: Int, isFuture: Boolean): Color {
    if (isFuture) return Color(0xFF1E1E1E)
    return when {
        count <= 0 -> Color(0xFF262626)
        count in 1..5 -> Color(0xFF0A84FF).copy(alpha = 0.35f)
        count in 6..15 -> Color(0xFF0A84FF).copy(alpha = 0.60f)
        count in 16..35 -> Color(0xFF0A84FF).copy(alpha = 0.82f)
        else -> Color(0xFF0A84FF)
    }
}
