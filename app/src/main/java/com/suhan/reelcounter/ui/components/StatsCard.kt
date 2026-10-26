package com.suhan.reelcounter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.suhan.reelcounter.ui.theme.ReelTheme

@Composable
fun StatsCard(
    row1Label: String,
    row1Value: String,
    row2Label: String,
    row2Value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ReelTheme.colors.surface)
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Row 1
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = row1Label,
                    style = ReelTheme.typography.body,
                    color = ReelTheme.colors.text
                )
                Text(
                    text = row1Value,
                    style = ReelTheme.typography.body,
                    color = ReelTheme.colors.textSecondary
                )
            }

            // Separator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ReelTheme.colors.separator)
            )

            // Row 2
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = row2Label,
                    style = ReelTheme.typography.body,
                    color = ReelTheme.colors.text
                )
                Text(
                    text = row2Value,
                    style = ReelTheme.typography.body,
                    color = ReelTheme.colors.textSecondary
                )
            }
        }
    }
}
