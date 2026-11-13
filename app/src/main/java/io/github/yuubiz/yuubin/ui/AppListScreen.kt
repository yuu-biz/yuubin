package io.github.yuubiz.yuubin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.yuubiz.yuubin.R
import io.github.yuubiz.yuubin.core.InstallFailure
import io.github.yuubiz.yuubin.core.LookupFailure
import io.github.yuubiz.yuubin.ui.theme.YuubinTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    state: ListUi,
    onRefresh: () -> Unit,
    onAction: (packageName: String, RowAction) -> Unit,
    onToggleNotes: (packageName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    TextButton(onClick = onRefresh, enabled = !state.refreshing) { Text(stringResource(R.string.action_refresh)) }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.rows, key = { it.packageName }) { row ->
                    AppRowItem(row, onAction = { onAction(row.packageName, it) }, onToggleNotes = { onToggleNotes(row.packageName) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun AppRowItem(row: RowUi, onAction: (RowAction) -> Unit, onToggleNotes: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(row.name, style = MaterialTheme.typography.titleMedium)
                row.versions?.let {
                    Text(it.text(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (row.busy == RowBusy.Loading) {
                val loading = stringResource(R.string.loading)
                CircularProgressIndicator(Modifier.size(20.dp).semantics { stateDescription = loading }, strokeWidth = 2.dp)
            }
            row.secondary?.let { TextButton(onClick = { onAction(it) }) { Text(it.label()) } }
            row.primary?.let { Button(onClick = { onAction(it) }) { Text(it.label()) } }
        }
        row.message?.let { MessageText(it) }
        when (val busy = row.busy) {
            null, RowBusy.Loading -> Unit
            is RowBusy.Downloading -> {
                Text(
                    if (busy.percent != null) stringResource(R.string.progress_downloading, busy.percent) else stringResource(R.string.progress_downloading_unknown),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (busy.percent != null) {
                    LinearProgressIndicator(progress = { busy.percent / 100f }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            RowBusy.Working -> LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        row.notes?.let { Notes(it, row.notesExpanded, onToggleNotes) }
    }
}

@Composable
private fun MessageText(message: RowMessage) {
    val isError = when (message) {
        is RowMessage.Failed, RowMessage.SignerMismatch -> true
        RowMessage.UpToDate, is RowMessage.ReleaseUnavailable, RowMessage.Verifying,
        RowMessage.AwaitingConfirmation, RowMessage.Installing, RowMessage.Succeeded -> false
    }
    Text(
        message.text(),
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
    if (message is RowMessage.Failed && message.detail != null) {
        Text(message.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Notes(notes: String, expanded: Boolean, onToggle: () -> Unit) {
    val state = stringResource(if (expanded) R.string.state_expanded else R.string.state_collapsed)
    Row(
        Modifier.clickable(role = Role.Button, onClick = onToggle).semantics { stateDescription = state },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(if (expanded) "▾" else "▸", Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.labelLarge)
        Text(stringResource(R.string.whats_new), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
    if (expanded) Text(notes, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun VersionLine.text(): String = when {
    installed == null ->
        if (latest == null) stringResource(R.string.version_not_installed) else stringResource(R.string.version_not_installed_latest, latest)
    latest == null -> stringResource(R.string.version_installed, installed)
    else -> stringResource(R.string.version_update, installed, latest)
}

@Composable
private fun RowAction.label(): String = stringResource(
    when (this) {
        RowAction.INSTALL -> R.string.action_install
        RowAction.UPDATE -> R.string.action_update
        RowAction.OPEN -> R.string.action_open
        RowAction.APP_INFO -> R.string.action_app_info
        RowAction.ALLOW_INSTALLS -> R.string.action_allow
        RowAction.DISMISS -> R.string.action_dismiss
    },
)

@Composable
private fun RowMessage.text(): String = when (this) {
    RowMessage.UpToDate -> stringResource(R.string.status_up_to_date)
    RowMessage.SignerMismatch -> stringResource(R.string.status_signer_mismatch)
    is RowMessage.ReleaseUnavailable -> when (reason) {
        LookupFailure.NOT_FOUND -> stringResource(R.string.lookup_not_found)
        LookupFailure.RATE_LIMITED -> stringResource(R.string.lookup_rate_limited)
        LookupFailure.NETWORK -> stringResource(R.string.lookup_network)
        LookupFailure.NO_APK_ASSET, LookupFailure.AMBIGUOUS_APK_ASSET, LookupFailure.NO_DIGEST, LookupFailure.MALFORMED ->
            stringResource(R.string.lookup_unusable, reason.name)
    }
    RowMessage.Verifying -> stringResource(R.string.progress_verifying)
    RowMessage.AwaitingConfirmation -> stringResource(R.string.progress_awaiting_confirmation)
    RowMessage.Installing -> stringResource(R.string.progress_installing)
    RowMessage.Succeeded -> stringResource(R.string.progress_succeeded)
    is RowMessage.Failed -> stringResource(
        when (reason) {
            InstallFailure.DOWNLOAD -> R.string.failure_download
            InstallFailure.HASH_MISMATCH -> R.string.failure_hash_mismatch
            InstallFailure.UNREADABLE_APK -> R.string.failure_unreadable_apk
            InstallFailure.PACKAGE_MISMATCH -> R.string.failure_package_mismatch
            InstallFailure.SIGNER_MISMATCH -> R.string.failure_signer_mismatch
            InstallFailure.NOT_NEWER -> R.string.failure_not_newer
            InstallFailure.INSTALL_PERMISSION_MISSING -> R.string.failure_install_permission_missing
            InstallFailure.CANCELLED -> R.string.failure_cancelled
            InstallFailure.CONFLICT -> R.string.failure_conflict
            InstallFailure.INCOMPATIBLE -> R.string.failure_incompatible
            InstallFailure.STORAGE -> R.string.failure_storage
            InstallFailure.BLOCKED -> R.string.failure_blocked
            InstallFailure.OTHER -> R.string.failure_other
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun AppListPreview() {
    val rows = listOf(
        RowUi("a", "Folder Player", VersionLine("1.10.1", "1.10.2"), null, null, RowAction.UPDATE, null, "- Fixes a crash", notesExpanded = true),
        RowUi("b", "Folirel", VersionLine("0.8.0", null), RowMessage.UpToDate, null, RowAction.OPEN, null, null),
        RowUi("c", "Hirari", null, null, RowBusy.Downloading(42), null, null, null),
        RowUi("d", "Yuubin", VersionLine(null, "0.1.0"), RowMessage.Failed(InstallFailure.INSTALL_PERMISSION_MISSING, null), null, RowAction.INSTALL, RowAction.ALLOW_INSTALLS, null),
        RowUi("e", "Other", null, null, RowBusy.Loading, null, null, null),
    )
    YuubinTheme { AppListScreen(ListUi(rows), onRefresh = {}, onAction = { _, _ -> }, onToggleNotes = {}) }
}
