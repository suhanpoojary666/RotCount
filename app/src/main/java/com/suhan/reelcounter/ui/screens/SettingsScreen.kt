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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suhan.reelcounter.R
import com.suhan.reelcounter.ui.components.IosSwitch
import com.suhan.reelcounter.ui.theme.ReelTheme
import com.suhan.reelcounter.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val showCounter by viewModel.showCounter.collectAsState()
    val countEnabled by viewModel.countEnabled.collectAsState()
    val isAccessibilityGranted by viewModel.isAccessibilityGranted.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ReelTheme.colors.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 48.dp)
        ) {
            // Back navigation button: chevron labeled "Reels"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val backInteraction = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier
                        .clickable(
                            interactionSource = backInteraction,
                            indication = null,
                            onClick = onNavigateBack
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_chevron_left),
                        contentDescription = "Back",
                        modifier = Modifier.size(24.dp),
                        colorFilter = ColorFilter.tint(ReelTheme.colors.accent)
                    )
                    Text(
                        text = "Back",
                        style = ReelTheme.typography.body,
                        color = ReelTheme.colors.accent
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Large title: "Settings"
            Text(
                text = "Settings",
                style = ReelTheme.typography.largeTitle.copy(
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = ReelTheme.colors.text
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 1. Show counter
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ReelTheme.colors.surface)
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Show counter",
                        style = ReelTheme.typography.body,
                        color = ReelTheme.colors.text
                    )
                    IosSwitch(
                        checked = showCounter,
                        onCheckedChange = { viewModel.setShowCounter(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Shows the floating counter on screen while reels are playing.",
                style = ReelTheme.typography.footnote,
                color = ReelTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = 6.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Count
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ReelTheme.colors.surface)
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Counter",
                        style = ReelTheme.typography.body,
                        color = ReelTheme.colors.text
                    )
                    IosSwitch(
                        checked = countEnabled,
                        onCheckedChange = { viewModel.setCountEnabled(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Counts reels as you watch them.",
                style = ReelTheme.typography.footnote,
                color = ReelTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = 6.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Accessibility access
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ReelTheme.colors.surface)
                    .padding(horizontal = 16.dp)
            ) {
                val accessInteraction = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clickable(
                            interactionSource = accessInteraction,
                            indication = null,
                            onClick = { viewModel.openAccessibilitySettings(context) }
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Accessibility access",
                        style = ReelTheme.typography.body,
                        color = ReelTheme.colors.text
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isAccessibilityGranted) {
                            Text(
                                text = "Enabled",
                                style = ReelTheme.typography.body,
                                color = ReelTheme.colors.accent
                            )
                        } else {
                            Text(
                                text = "Not enabled",
                                style = ReelTheme.typography.body,
                                color = ReelTheme.colors.textSecondary
                            )
                            Image(
                                painter = painterResource(id = R.drawable.ic_chevron_right),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                colorFilter = ColorFilter.tint(ReelTheme.colors.textSecondary)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Required to detect reels and show the counter.",
                style = ReelTheme.typography.footnote,
                color = ReelTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
    }
}
