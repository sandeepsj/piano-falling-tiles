package com.pianotiles.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pianotiles.ui.GameUiState

/**
 * Play/Pause/Stop controls shown at the bottom centre of the game screen.
 * While paused, a semi-transparent overlay with Resume/Stop buttons is shown.
 */
@Composable
fun PlaybackControls(
    state    : GameUiState,
    onPlay   : () -> Unit,
    onPause  : () -> Unit,
    onResume : () -> Unit,
    onStop   : () -> Unit,
    modifier : Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {

        // Pause button — bottom centre while playing
        if (state.isPlaying) {
            FloatingActionButton(
                onClick           = onPause,
                modifier          = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .size(56.dp),
                shape             = CircleShape,
                containerColor    = Color.White.copy(alpha = 0.15f),
                contentColor      = Color.White
            ) {
                Icon(Icons.Default.Pause, contentDescription = "Pause")
            }
        }

        // Paused overlay — Resume + Stop
        if (state.isPaused) {
            Surface(
                modifier       = Modifier.fillMaxSize(),
                color          = Color.Black.copy(alpha = 0.55f)
            ) {
                Column(
                    modifier            = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("PAUSED", color = Color.White,
                        style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(32.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Button(onClick = onResume) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Resume")
                        }
                        OutlinedButton(
                            onClick = onStop,
                            colors  = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Stop")
                        }
                    }
                }
            }
        }
    }
}
