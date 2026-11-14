package io.github.yuubiz.yuubin.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.yuubiz.yuubin.YuubinApp
import io.github.yuubiz.yuubin.core.InstallProgress
import io.github.yuubiz.yuubin.install.Confirmation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ListUi(val rows: List<RowUi> = emptyList(), val refreshing: Boolean = false)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val distributor = (application as YuubinApp).container.distributor
    private val packageManager = application.packageManager
    private val refreshing = MutableStateFlow(false)
    private val expandedNotes = MutableStateFlow(emptySet<String>())

    val state: StateFlow<ListUi> = combine(distributor.rows, expandedNotes, refreshing) { rows, expanded, busy ->
        ListUi(
            rows = rows.map { row ->
                row.toRowUi { packageManager.getLaunchIntentForPackage(it) != null }
                    .let { it.copy(notesExpanded = it.packageName in expanded) }
            },
            refreshing = busy,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListUi())

    val pendingConfirmation: StateFlow<Confirmation?> = distributor.pendingConfirmation

    init {
        refresh()
    }

    fun refresh() {
        if (!refreshing.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                distributor.refresh()
            } finally {
                refreshing.value = false
            }
            // A finished install is acknowledged by the user's next refresh; the list then shows the new state alone.
            distributor.rows.value.filter { it.progress == InstallProgress.Succeeded }.forEach { distributor.dismiss(it.app) }
        }
    }

    fun install(packageName: String) {
        appOf(packageName)?.let(distributor::install)
    }

    fun dismiss(packageName: String) {
        appOf(packageName)?.let(distributor::dismiss)
    }

    private fun appOf(packageName: String) = distributor.rows.value.firstOrNull { it.app.packageName == packageName }?.app

    fun toggleNotes(packageName: String) {
        expandedNotes.update { if (packageName in it) it - packageName else it + packageName }
    }

    fun reconcile() = distributor.reconcile()

    fun confirmationShown(confirmation: Confirmation) = distributor.confirmationShown(confirmation)

    fun confirmationFailed(confirmation: Confirmation, detail: String?) = distributor.confirmationFailed(confirmation, detail)
}
