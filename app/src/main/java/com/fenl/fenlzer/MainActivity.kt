package com.fenl.fenlzer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.tooling.preview.Preview
import com.fenl.fenlzer.data.settings.AppSettings
import com.fenl.fenlzer.data.settings.InMemoryAppSettingsRepository
import com.fenl.fenlzer.ui.FenlzerApp
import com.fenl.fenlzer.ui.theme.FenlzerTheme

class MainActivity : ComponentActivity() {
    private val appGraph: AppGraph
        get() = (application as FenlzerApplication).appGraph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings = appGraph.settingsRepository.settings.collectAsStateWithLifecycle()

            FenlzerTheme(themeMode = settings.value.themeMode) {
                FenlzerApp(appGraph = appGraph)
            }
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
