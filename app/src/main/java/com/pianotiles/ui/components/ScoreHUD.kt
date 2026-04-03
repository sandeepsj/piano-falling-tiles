package com.pianotiles.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pianotiles.ui.GameUiState

/**
 * Transparent HUD overlay drawn on top of the game surface.
 * Shows score (top-left), combo (top-right), and accuracy (top-right below combo).
 */
@Composable
fun ScoreHUD(state: GameUiState, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {

        // Score — top left
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 24.dp, top = 16.dp)
        ) {
            Text(
                text       = "%,d".format(state.score),
                color      = Color.White,
                fontSize   = 32.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text     = "SCORE",
                color    = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
        }

        // Combo + accuracy — top right
        Column(
            modifier          = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 24.dp, top = 16.dp),
            horizontalAlignment = Alignment.End
        ) {
            if (state.combo >= 2) {
                Text(
                    text       = "${state.combo}×",
                    color      = comboColor(state.combo),
                    fontSize   = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text     = "COMBO",
                    color    = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text     = "%.1f%%".format(state.accuracy * 100),
                color    = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp
            )
        }
    }
}

private fun comboColor(combo: Int): Color = when {
    combo >= 50 -> Color(0xFFFFD700)  // gold
    combo >= 20 -> Color(0xFFFF6B6B)  // red-orange
    combo >= 10 -> Color(0xFF4FC3F7)  // blue
    else        -> Color.White
}
