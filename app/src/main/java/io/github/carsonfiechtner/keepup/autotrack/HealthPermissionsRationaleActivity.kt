package io.github.carsonfiechtner.keepup.autotrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.carsonfiechtner.keepup.ui.theme.KeepUpTheme

/**
 * Shown by Health Connect when the user asks why KeepUp wants access ("privacy policy"). Required
 * for Health Connect permissions, even for a sideloaded app.
 */
class HealthPermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KeepUpTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .safeDrawingPadding()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("How KeepUp uses health data", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "KeepUp reads your daily step count from Health Connect to fill in step habits " +
                                "automatically. It only reads steps, never writes health data, and everything " +
                                "stays on this phone. Nothing is sent to any server. You can revoke access at " +
                                "any time in Health Connect.",
                        )
                        Button(onClick = ::finish) { Text("Close") }
                    }
                }
            }
        }
    }
}
