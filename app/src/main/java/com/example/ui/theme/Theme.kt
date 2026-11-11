package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

val LocalIsDarkTheme = compositionLocalOf { true }

private val DarkColorScheme = darkColorScheme(
    primary = GoldPrimary,
    onPrimary = TitaniumBlack,
    secondary = GoldLight,
    onSecondary = TitaniumBlack,
    tertiary = SilverPrimary,
    background = TitaniumBlack,
    surface = TitaniumDarkSurface,
    onBackground = TextWhitePrimary,
    onSurface = TextWhitePrimary
)

private val LightColorScheme = lightColorScheme(
    primary = GoldDark,
    onPrimary = TextWhitePrimary,
    secondary = GoldPrimary,
    onSecondary = TitaniumBlack,
    tertiary = SilverDark,
    background = TitaniumLightBackground,
    surface = TitaniumLightSurface,
    onBackground = TitaniumLightTextPrimary,
    onSurface = TitaniumLightTextPrimary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

@Composable
fun KrsBullionTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MyApplicationTheme(darkTheme = darkTheme, content = content)
}

