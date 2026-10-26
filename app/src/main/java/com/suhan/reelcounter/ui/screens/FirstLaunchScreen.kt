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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suhan.reelcounter.R
import com.suhan.reelcounter.ui.theme.ReelTheme
import com.suhan.reelcounter.viewmodel.MainViewModel

@Composable
fun FirstLaunchScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasReturnedFromSettings by viewModel.hasReturnedFromSettings.collectAsState()

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
                .padding(top = 80.dp, bottom = 32.dp)
        ) {
            // Content in available space
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_rotcount_logo),
                        contentDescription = "RotCount logo",
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Text(
                        text = "RotCount",
                        style = ReelTheme.typography.largeTitle,
                        color = ReelTheme.colors.text
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Count your reels",
                    style = ReelTheme.typography.largeTitle.copy(fontSize = 28.sp),
                    color = ReelTheme.colors.text
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(id = R.string.accessibility_service_description),
                    style = ReelTheme.typography.body,
                    color = ReelTheme.colors.textSecondary
                )
            }

            // Primary button pinned to the bottom:
            // 50 dp high, 14 dp corners, accent fill, white 17 semibold text
            val buttonInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ReelTheme.colors.accent)
                    .clickable(
                        interactionSource = buttonInteraction,
                        indication = null,
                        onClick = { viewModel.openAccessibilitySettings(context) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Open Settings",
                    style = ReelTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White
                )
            }

            // Footnote line under the button when user returns and access is still off
            if (hasReturnedFromSettings) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "On Android 13 or newer, open App info, tap the three-dot menu, then choose Allow restricted settings.",
                    style = ReelTheme.typography.footnote,
                    color = ReelTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
