package com.todorpg.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object RpgColors {
    val Night = Color(0xFF080A20)
    val WindowTop = Color(0xFF3C50C8)
    val WindowBottom = Color(0xFF101870)
    val Border = Color(0xFFE8E8F0)
    val BorderShadow = Color(0xFF6070A0)
    val Text = Color(0xFFF8F8F8)
    val Dim = Color(0xFFA8B0D8)
    val Gold = Color(0xFFF8D040)
    val Hp = Color(0xFF48D048)
    val HpLow = Color(0xFFE8C030)
    val HpCrit = Color(0xFFE04040)
    val Exp = Color(0xFF50B8F8)
    val Charge = Color(0xFFF8A030)
    val Disabled = Color(0xFF606888)
}

val PixelFont = FontFamily.Monospace

private fun style(size: Int) = TextStyle(
    fontFamily = PixelFont,
    fontWeight = FontWeight.Bold,
    fontSize = size.sp,
    color = RpgColors.Text,
)

@Composable
fun ToDoRpgTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = RpgColors.Gold,
            onPrimary = Color.Black,
            background = RpgColors.Night,
            surface = RpgColors.WindowBottom,
            onSurface = RpgColors.Text,
            onBackground = RpgColors.Text,
        ),
        typography = Typography(
            bodyLarge = style(16),
            bodyMedium = style(14),
            bodySmall = style(12),
            titleLarge = style(20),
            titleMedium = style(16),
            labelLarge = style(14),
            labelMedium = style(12),
            labelSmall = style(10),
        ),
        content = content,
    )
}
