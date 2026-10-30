package com.todorpg.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.todorpg.app.R

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

/** Readable pixel font for body text. */
val PixelFont = FontFamily(
    Font(R.font.pixelify_sans, FontWeight.Normal),
    Font(R.font.pixelify_sans_semibold, FontWeight.Bold),
)

/** Chunky 8-bit font for headings and numbers. */
val HeadingFont = FontFamily(Font(R.font.press_start_2p))

private val TextShadow = Shadow(Color(0xC0000000), Offset(2f, 2f), 0f)

private fun body(size: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = PixelFont,
    fontWeight = weight,
    fontSize = size.sp,
    color = RpgColors.Text,
    shadow = TextShadow,
)

private fun heading(size: Int) = TextStyle(
    fontFamily = HeadingFont,
    fontSize = size.sp,
    lineHeight = (size + 6).sp,
    color = RpgColors.Text,
    shadow = TextShadow,
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
            bodyLarge = body(18),
            bodyMedium = body(16),
            bodySmall = body(14),
            titleLarge = heading(16),
            titleMedium = heading(12),
            titleSmall = heading(10),
            labelLarge = body(16, FontWeight.Bold),
            labelMedium = body(14),
            labelSmall = body(12),
            displaySmall = heading(22),
        ),
        content = content,
    )
}
