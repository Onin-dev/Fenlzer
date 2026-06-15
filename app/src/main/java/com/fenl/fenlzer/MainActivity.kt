package com.fenl.fenlzer

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.tooling.preview.Preview
import com.fenl.fenlzer.data.settings.AppSettings
import com.fenl.fenlzer.data.settings.InMemoryAppSettingsRepository
import com.fenl.fenlzer.ui.FenlzerApp
import com.fenl.fenlzer.ui.theme.FenlzerTheme
import com.fenl.fenlzer.importing.ImportNotificationController

class MainActivity : ComponentActivity() {
    private var activeImportsRequest by mutableIntStateOf(0)
    private val appGraph: AppGraph
        get() = (application as FenlzerApplication).appGraph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            val settings = appGraph.settingsRepository.settings.collectAsStateWithLifecycle()

            FenlzerTheme(themeMode = settings.value.themeMode) {
                FenlzerApp(
                    appGraph = appGraph,
                    activeImportsRequest = activeImportsRequest
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ImportNotificationController.ACTION_OPEN_ACTIVE_IMPORTS) {
            activeImportsRequest += 1
            intent.action = null
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FenlzerAppPreview() {
    val previewGraph = AppGraph(
        settingsRepository = InMemoryAppSettingsRepository(AppSettings())
    )

    FenlzerTheme(themeMode = AppSettings().themeMode) {
        FenlzerApp(appGraph = previewGraph)
    }
}
