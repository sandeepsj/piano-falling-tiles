package com.pianotiles.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.pianotiles.engine.GameMode
import com.pianotiles.rendering.GameSurfaceView
import com.pianotiles.ui.GameUiState
import com.pianotiles.ui.GameViewModel
import com.pianotiles.ui.components.PlaybackControls
import com.pianotiles.ui.components.ScoreHUD

/**
 * Full-screen game view: GLSurfaceView + transparent HUD overlays.
 *
 * The AndroidView creates the GameSurfaceView and notifies the ViewModel once
 * the renderer is available so the GameEngine can be wired up.
 */
@Composable
fun GameScreen(
    state    : GameUiState,
    viewModel: GameViewModel,
    onStop   : () -> Unit
) {
    // Stop engine (cancels audio) before popping back to home
    BackHandler {
        viewModel.stop()
        onStop()
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // GL surface — fills entire screen
        AndroidView(
            factory = { ctx ->
                GameSurfaceView(ctx).also { view ->
                    viewModel.onRendererReady(view.renderer)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Score / combo overlay (always visible)
        ScoreHUD(state = state)

        // Mode pill — top centre
        val modeLabel = if (state.gameMode == GameMode.LISTEN) "LISTEN" else "TUTORIAL"
        val modeColor = if (state.gameMode == GameMode.LISTEN) Color(0xFF81C784) else Color(0xFF4FC3F7)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .background(modeColor.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Text(modeLabel, fontSize = 11.sp, color = modeColor)
        }

        // Play / pause controls
        PlaybackControls(
            state    = state,
            onPlay   = { /* play is triggered from HomeScreen → navigation */ },
            onPause  = viewModel::pause,
            onResume = viewModel::resume,
            onStop   = {
                viewModel.stop()
                onStop()
            }
        )
    }
}
