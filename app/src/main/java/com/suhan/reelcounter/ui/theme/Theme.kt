package com.suhan.reelcounter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalReelColors = staticCompositionLocalOf { LightReelColors }
val LocalReelTypography = staticCompositionLocalOf { ReelTypography() }

object ReelTheme {
    val colors: ReelColors
        @Composable
        @ReadOnlyComposable
        get() = LocalReelColors.current

    val typography: ReelTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalReelTypography.current
}

@Composable
fun ReelCounterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkReelColors else LightReelColors
    val typography = ReelTypography()

    CompositionLocalProvider(
        LocalReelColors provides colors,
        LocalReelTypography provides typography,
        content = content
    )
}
