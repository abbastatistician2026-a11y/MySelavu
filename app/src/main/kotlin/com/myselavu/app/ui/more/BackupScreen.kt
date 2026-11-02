@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.myselavu.app.ui.more

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myselavu.app.R
import com.myselavu.app.data.BackupPreview
import com.myselavu.app.ui.AppViewModel
import com.myselavu.app.ui.components.AppIcons
import com.myselavu.app.ui.components.ConfirmDialog
import com.myselavu.app.ui.components.LabeledValue
import com.myselavu.app.ui.components.ScreenScaffold
import com.myselavu.app.ui.components.SectionTitle
import com.myselavu.app.ui.components.formatDate
import com.myselavu.app.ui.theme.LocalStatusColors
import com.myselavu.core.backup.BackupCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

@Composable
fun BackupScreen(
    vm: AppViewModel,
    keepYears: Int,
    lastBackupAt: Long,
    snackbar: SnackbarHostState,
    onMenu: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storage by vm.storage.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadStorage() }

    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var clearYears by remember { mutableStateOf<Int?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)!!.use { out -> vm.createBackup(out, password) }
                }
            }
            busy = false
        }
    }

    ScreenScaffold(title = stringResource(R.string.backup_and_storage), onMenu = onMenu, snackbar = snackbar) { padding ->
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- Storage ----
            SectionTitle(stringResource(R.string.storage_title))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val st = storage
                    LabeledValue(stringResource(R.string.storage_used), st?.let { Formatter.formatShortFileSize(context, it.sizeBytes) } ?: "…")
                    LabeledValue(stringResource(R.string.storage_expenses), st?.info?.expenseCount?.toString() ?: "…")
                    LabeledValue(stringResource(R.string.storage_months), st?.info?.monthCount?.toString() ?: "…")
                    st?.info?.oldest?.let { LabeledValue(stringResource(R.string.storage_since), formatDate(it)) }
                    Text(stringResource(R.string.storage_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ---- Backup ----
            HorizontalDivider()
            SectionTitle(stringResource(R.string.backup_title))
            Text(stringResource(R.string.backup_intro), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = password, onValueChange = { password = it.take(64) },
                label = { Text(stringResource(R.string.backup_password)) },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(if (showPassword) AppIcons.VisibilityOff else AppIcons.Visibility,
                            stringResource(if (showPassword) R.string.hide_password else R.string.show_password))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(if (password.isEmpty()) R.string.backup_no_password_warning else R.string.backup_password_warning),
                style = MaterialTheme.typography.bodySmall,
                color = if (password.isEmpty()) LocalStatusColors.current.careful else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                enabled = !busy,
                onClick = { saveLauncher.launch("MySelavu-backup-${vm.today()}.myselavu") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Icon(AppIcons.Download, null); Spacer(Modifier.width(8.dp))
                Text(stringResource(if (busy) R.string.backup_saving else R.string.backup_save))
            }
            Text(
                if (lastBackupAt > 0) stringResource(R.string.backup_last,
                    formatDate(Instant.ofEpochMilli(lastBackupAt).atZone(ZoneId.systemDefault()).toLocalDate()))
                else stringResource(R.string.backup_never),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ---- Restore ----
            HorizontalDivider()
            SectionTitle(stringResource(R.string.restore_title))
            Text(stringResource(R.string.restore_intro), style = MaterialTheme.typography.bodyMedium)
            RestoreButton(vm, Modifier.fillMaxWidth())

            // ---- Clear history ----
            HorizontalDivider()
            SectionTitle(stringResource(R.string.clear_title))
            Text(stringResource(R.string.clear_intro), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to R.string.clear_1y, 2 to R.string.clear_2y, 5 to R.string.clear_5y, 0 to R.string.clear_all_past).forEach { (y, label) ->
                    OutlinedButton(onClick = { clearYears = y }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(label)) }
                }
            }

            Text(stringResource(R.string.keep_history_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.keep_history_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 3, 5, 10).forEach { y ->
                    FilterChip(
                        selected = keepYears == y, onClick = { vm.setKeepHistoryYears(y) },
                        label = { Text(if (y == 0) stringResource(R.string.keep_forever) else stringResource(R.string.keep_n_years, y)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }

            HorizontalDivider()
            OutlinedButton(onClick = { confirmDeleteAll = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(AppIcons.Delete, null, tint = LocalStatusColors.current.danger); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.delete_everything))
            }
            Spacer(Modifier.padding(8.dp))
        }
    }

    clearYears?.let { y ->
        ConfirmDialog(
            title = stringResource(R.string.clear_title),
            text = stringResource(if (y == 0) R.string.clear_confirm_all else R.string.clear_confirm_years, y),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { vm.clearHistory(y); clearYears = null },
            onDismiss = { clearYears = null },
        )
    }
    if (confirmDeleteAll) {
        ConfirmDialog(
            title = stringResource(R.string.delete_everything),
            text = stringResource(R.string.delete_everything_confirm),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { vm.deleteEverything(); confirmDeleteAll = false },
            onDismiss = { confirmDeleteAll = false },
        )
    }
}

/**
 * "Restore from backup file": pick the file, type the password if it is locked,
 * see what's inside, and confirm "This will replace your current data".
 */
@Composable
fun RestoreButton(vm: AppViewModel, modifier: Modifier = Modifier, label: Int = R.string.restore_button) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    var askPassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var preview by remember { mutableStateOf<BackupPreview?>(null) }

    fun tryOpen(pw: String?) {
        val b = bytes ?: return
        scope.launch {
            try {
                preview = vm.previewBackup(b, pw)
                askPassword = false
                error = null
            } catch (e: BackupCodec.WrongPasswordException) {
                error = R.string.restore_wrong_password; askPassword = true
            } catch (e: BackupCodec.PasswordNeededException) {
                askPassword = true
            } catch (e: Exception) {
                error = R.string.restore_not_backup; askPassword = false; bytes = null
            }
        }
    }

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val b = runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { it.readBytes() } }
            }.getOrNull()
            if (b == null) { error = R.string.restore_not_backup; return@launch }
            bytes = b
            password = ""
            if (vm.backupIsLocked(b)) askPassword = true else tryOpen(null)
        }
    }

    OutlinedButton(onClick = { error = null; openLauncher.launch(arrayOf("*/*")) }, modifier = modifier.heightIn(min = 52.dp)) {
        Text(stringResource(label))
    }
    if (error != null && !askPassword) {
        Text(stringResource(error!!), color = LocalStatusColors.current.danger, style = MaterialTheme.typography.bodyMedium)
    }

    if (askPassword) {
        AlertDialog(
            onDismissRequest = { askPassword = false; bytes = null },
            title = { Text(stringResource(R.string.restore_password_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        password, { password = it.take(64) }, label = { Text(stringResource(R.string.backup_password)) },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    error?.let { Text(stringResource(it), color = LocalStatusColors.current.danger) }
                }
            },
            confirmButton = { TextButton(enabled = password.isNotEmpty(), onClick = { tryOpen(password) }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { askPassword = false; bytes = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    preview?.let { p ->
        ConfirmDialog(
            title = stringResource(R.string.restore_confirm_title),
            text = stringResource(
                R.string.restore_confirm_text,
                formatDate(Instant.ofEpochMilli(p.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()),
                p.expenseCount, p.monthCount,
            ),
            confirmLabel = stringResource(R.string.restore_replace),
            onConfirm = { vm.restoreBackup(p); preview = null; bytes = null },
            onDismiss = { preview = null; bytes = null },
        )
    }
}
