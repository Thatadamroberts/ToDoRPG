package com.todorpg.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.todorpg.app.ui.theme.RpgColors

private val WindowShape = RoundedCornerShape(6.dp)

/** The classic blue gradient menu window with a light double border. */
@Composable
fun RpgWindow(
    modifier: Modifier = Modifier,
    padding: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(WindowShape)
            .background(Brush.verticalGradient(listOf(RpgColors.WindowTop, RpgColors.WindowBottom)))
            .border(3.dp, RpgColors.Border, WindowShape)
            .padding(3.dp)
            .border(1.dp, RpgColors.BorderShadow, RoundedCornerShape(4.dp))
            .padding(padding),
        content = content,
    )
}

/** A menu entry with the pointing-hand cursor when selected. */
@Composable
fun MenuButton(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (selected) "▶" else " ",
            color = RpgColors.Gold,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text,
            color = if (enabled) RpgColors.Text else RpgColors.Disabled,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A small framed button. */
@Composable
fun RpgButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (enabled) RpgColors.WindowTop else RpgColors.WindowBottom)
            .border(2.dp, if (enabled) RpgColors.Border else RpgColors.Disabled, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) RpgColors.Text else RpgColors.Disabled,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** HP / EXP style bar. */
@Composable
fun StatBar(
    value: Int,
    max: Int,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
) {
    val fraction = if (max <= 0) 0f else (value.toFloat() / max).coerceIn(0f, 1f)
    Box(
        modifier
            .height(height)
            .background(Color(0xFF000020))
            .border(1.dp, RpgColors.BorderShadow)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(height)
                .background(color)
        )
    }
}

fun hpColor(hp: Int, max: Int): Color = when {
    hp * 4 <= max -> RpgColors.HpCrit
    hp * 2 <= max -> RpgColors.HpLow
    else -> RpgColors.Hp
}

/** Little orange pips showing banked battle turns. */
@Composable
fun ChargePips(count: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(count.coerceAtMost(9)) {
            Box(
                Modifier
                    .size(width = 5.dp, height = 8.dp)
                    .background(RpgColors.Charge)
            )
        }
    }
}

@Composable
fun RpgDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        RpgWindow(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), padding = 14.dp) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = RpgColors.Gold)
            androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
fun RpgTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .background(Color(0xFF080C40))
            .border(1.dp, RpgColors.BorderShadow)
            .padding(10.dp)
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = RpgColors.Dim, style = MaterialTheme.typography.bodyMedium)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = RpgColors.Text),
            cursorBrush = SolidColor(RpgColors.Gold),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
