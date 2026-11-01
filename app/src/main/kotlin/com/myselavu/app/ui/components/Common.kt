@file:OptIn(ExperimentalMaterial3Api::class)

package com.myselavu.app.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myselavu.app.R
import com.myselavu.core.budget.PaymentMethod
import com.myselavu.core.money.IndianFormat
import com.myselavu.core.money.Money
import com.myselavu.core.rules.ApplyFrom
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Colours offered for categories. */
val CategoryColors: List<Long> = listOf(
    0xFFE07A2E, 0xFF2E86AB, 0xFF8E5BB5, 0xFF2E7D32, 0xFFC62828,
    0xFF00897B, 0xFFF9A825, 0xFF6D4C41, 0xFFD81B60, 0xFF546E7A,
)

val CategoryEmojis: List<String> = listOf(
    "🍛", "🛒", "🛺", "🚌", "⛽", "🛍️", "💊", "🎬", "📚", "👪", "🎁", "🏦", "🚑", "🏠", "📱", "☕", "💡", "✈️",
)

/** Shows money; when it changes, the number counts smoothly to the new value. */
@Composable
fun AnimatedMoneyText(
    money: Money,
    style: TextStyle,
    modifier: Modifier = Modifier,
    hidden: Boolean = false,
    color: Color = Color.Unspecified,
) {
    var from by remember { mutableStateOf(money) }
    val progress = remember { Animatable(1f) }
    var target by remember { mutableStateOf(money) }
    LaunchedEffect(money) {
        if (money != target) {
            val p = progress.value
            from = Money(from.paise + ((target.paise - from.paise) * p).toLong())
            target = money
            progress.snapTo(0f)
            progress.animateTo(1f, tween(450))
        }
    }
    val p = progress.value
    val shown = if (p >= 1f) target else Money(from.paise + ((target.paise - from.paise) * p).toLong())
    Text(shown.display(hidden), style = style, modifier = modifier, color = color)
}

/** A text field for typing rupees. */
@Composable
fun MoneyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onValueChange(v.filter { it.isDigit() || it == '.' }.take(12)) },
        label = { Text(label) },
        prefix = { Text(IndianFormat.RUPEE) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

fun parseMoneyOrZero(text: String): Money = IndianFormat.parse(text) ?: Money.ZERO

/** Money as editable text: 1500.00 → "1500", 12.5 → "12.50". */
fun Money.toInputText(): String =
    if (paise % 100 == 0L) (paise / 100).toString() else IndianFormat.format(this, withSymbol = false).replace(",", "")

/** "Apply from this month or from next month?" */
@Composable
fun ApplyFromDialog(onChoose: (ApplyFrom) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.apply_from_title)) },
        text = { Text(stringResource(R.string.apply_from_text)) },
        confirmButton = {
            TextButton(onClick = { onChoose(ApplyFrom.THIS_PERIOD) }) { Text(stringResource(R.string.apply_this_month)) }
        },
        dismissButton = {
            TextButton(onClick = { onChoose(ApplyFrom.NEXT_PERIOD) }) { Text(stringResource(R.string.apply_next_month)) }
        },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Friendly empty state with one clear message. */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier, emoji: String = "🪙") {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(emoji, style = MaterialTheme.typography.displaySmall)
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier.padding(top = 8.dp).semantics { heading() })
}

@Composable
fun CategoryAvatar(emoji: String, color: Long, modifier: Modifier = Modifier) {
    Box(
        modifier.size(40.dp).clip(CircleShape).background(Color(color).copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, style = MaterialTheme.typography.titleMedium) }
}

/** Standard screen frame: title bar with the menu button, snackbar space, optional + button. */
@Composable
fun ScreenScaffold(
    title: String,
    onMenu: () -> Unit,
    snackbar: SnackbarHostState,
    actions: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onMenu) { Icon(AppIcons.Menu, contentDescription = stringResource(R.string.open_menu)) }
                },
                actions = { actions() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = floatingActionButton,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding -> Box(Modifier.fillMaxSize()) { content(padding) } }
}

@Composable
fun methodLabel(m: PaymentMethod): String = stringResource(
    when (m) {
        PaymentMethod.UPI -> R.string.method_upi
        PaymentMethod.CASH -> R.string.method_cash
        PaymentMethod.DEBIT_CARD -> R.string.method_debit
        PaymentMethod.CREDIT_CARD -> R.string.method_credit
        PaymentMethod.OTHER -> R.string.method_other
    }
)

fun methodEmoji(m: PaymentMethod): String = when (m) {
    PaymentMethod.UPI -> "📲"
    PaymentMethod.CASH -> "💵"
    PaymentMethod.DEBIT_CARD -> "💳"
    PaymentMethod.CREDIT_CARD -> "🏧"
    PaymentMethod.OTHER -> "🔖"
}

@Composable
fun formatMonth(ym: YearMonth): String {
    val locale = LocalConfiguration.current.locales[0]
    return ym.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
}

@Composable
fun formatDate(date: LocalDate, pattern: String = "d MMM yyyy"): String {
    val locale = LocalConfiguration.current.locales[0]
    return date.format(DateTimeFormatter.ofPattern(pattern, locale))
}

/** A gentle haptic tick (used after saving). */
fun View.tick() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
    )
}

@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor)
    }
}
