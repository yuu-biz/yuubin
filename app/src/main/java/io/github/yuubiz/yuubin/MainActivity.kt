package io.github.yuubiz.yuubin

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.yuubiz.yuubin.install.Confirmation
import io.github.yuubiz.yuubin.ui.AppListScreen
import io.github.yuubiz.yuubin.ui.MainViewModel
import io.github.yuubiz.yuubin.ui.RowAction
import io.github.yuubiz.yuubin.ui.theme.YuubinTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            // Only a resumed activity may start the install dialog; otherwise the system can drop it silently.
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.pendingConfirmation.collect { confirmation -> confirmation?.let(::showConfirmation) }
            }
        }
        setContent {
            YuubinTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                AppListScreen(
                    state = state,
                    onRefresh = viewModel::refresh,
                    onAction = ::onAction,
                    onToggleNotes = viewModel::toggleNotes,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reconcile()
    }

    private fun showConfirmation(confirmation: Confirmation) {
        when (val error = startSafely(confirmation.intent)) {
            null -> viewModel.confirmationShown(confirmation)
            else -> viewModel.confirmationFailed(confirmation, error.message ?: "install dialog did not open")
        }
    }

    private fun onAction(packageName: String, action: RowAction) {
        when (action) {
            RowAction.INSTALL, RowAction.UPDATE -> viewModel.install(packageName)
            RowAction.DISMISS -> viewModel.dismiss(packageName)
            RowAction.OPEN -> packageManager.getLaunchIntentForPackage(packageName)?.let(::startSafely)
            RowAction.APP_INFO -> startSafely(packageIntent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageName))
            RowAction.ALLOW_INSTALLS -> startSafely(packageIntent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, this.packageName))
        }
    }

    private fun packageIntent(action: String, packageName: String) =
        Intent(action, Uri.fromParts("package", packageName, null))

    /** Returns null once [intent] started, or the exception that stopped it. */
    private fun startSafely(intent: Intent): Exception? =
        try {
            startActivity(intent)
            null
        } catch (e: Exception) {
            Log.w(TAG, "Could not start ${intent.action}", e)
            e
        }

    private companion object {
        const val TAG = "MainActivity"
    }
}
