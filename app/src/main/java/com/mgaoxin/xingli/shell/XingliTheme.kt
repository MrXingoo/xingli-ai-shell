package com.mgaoxin.xingli.shell

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 星黎主题色：蓝橙二次元风格，对齐 Hermes Studio 深色主视觉
private val LightColors = lightColorScheme(
    primary = Color(0xFF3B6FE0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = Color(0xFF00184B),
    secondary = Color(0xFFE07B39),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBC8),
    onSecondaryContainer = Color(0xFF3A1A00),
    background = Color(0xFFF7F7F4),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFF7F7F4),
    onSurface = Color(0xFF1A1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9EBBFF),
    onPrimary = Color(0xFF002D66),
    primaryContainer = Color(0xFF21447F),
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFFFFB784),
    onSecondary = Color(0xFF5E2D00),
    secondaryContainer = Color(0xFF7A4216),
    onSecondaryContainer = Color(0xFFFFDBC8),
    background = Color(0xFF1A1A1A),
    onBackground = Color(0xFFE6E1E9),
    surface = Color(0xFF1A1A1A),
    onSurface = Color(0xFFE6E1E9),
)

@Composable
fun XingliTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
