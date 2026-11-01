package com.myselavu.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.myselavu.app.R
import com.myselavu.app.ui.theme.LocalStatusColors
import com.myselavu.core.warning.WarningLevel
import com.myselavu.core.warning.WarningReason
import com.myselavu.core.warning.WarningResult

data class LevelStyle(val color: Color, val container: Color, val icon: ImageVector)

@Composable
fun levelStyle(level: WarningLevel): LevelStyle {
    val c = LocalStatusColors.current
    return when (level) {
        WarningLevel.GREEN -> LevelStyle(c.good, c.goodContainer, AppIcons.CheckCircle)
        WarningLevel.YELLOW -> LevelStyle(c.careful, c.carefulContainer, AppIcons.Warning)
        WarningLevel.RED -> LevelStyle(c.danger, c.dangerContainer, AppIcons.Error)
    }
}

@Composable
fun levelLabel(level: WarningLevel): String = stringResource(
    when (level) {
        WarningLevel.GREEN -> R.string.status_on_track
        WarningLevel.YELLOW -> R.string.status_careful
        WarningLevel.RED -> R.string.status_overspending
    }
)

/** The friendly sentence that always accompanies a warning colour. */
@Composable
fun warningSentence(result: WarningResult, hidden: Boolean = false): String {
    val perDay = result.perDayLeft.display(hidden)
    val days = result.daysLeft
    return when {
        result.reason == WarningReason.OVERSPENT ->
            stringResource(R.string.warning_overspent, (-result.remaining).display(hidden))
        result.level == WarningLevel.GREEN ->
            pluralStringResource(R.plurals.warning_green, days, perDay, days)
        result.level == WarningLevel.YELLOW ->
            pluralStringResource(R.plurals.warning_yellow, days, perDay, days)
        else -> pluralStringResource(R.plurals.warning_red, days, perDay, days)
    }
}

/** A small pill: icon + word (never colour alone). */
@Composable
fun StatusChip(level: WarningLevel, modifier: Modifier = Modifier) {
    val style = levelStyle(level)
    Surface(color = style.container, contentColor = style.color, shape = CircleShape, modifier = modifier) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(style.icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(levelLabel(level), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Icon + sentence line under a category. */
@Composable
fun WarningLine(result: WarningResult, hidden: Boolean, modifier: Modifier = Modifier) {
    val style = levelStyle(result.level)
    Row(
        modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(style.icon, contentDescription = levelLabel(result.level), tint = style.color, modifier = Modifier.size(20.dp))
        Text(warningSentence(result, hidden), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
