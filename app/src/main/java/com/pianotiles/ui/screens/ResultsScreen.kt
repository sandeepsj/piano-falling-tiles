package com.pianotiles.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pianotiles.ui.GameUiState

@Composable
fun ResultsScreen(
    state       : GameUiState,
    onPlayAgain : () -> Unit,
    onHome      : () -> Unit
) {
    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Song Complete!", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(state.songTitle, fontSize = 18.sp, color = Color.White.copy(alpha = 0.6f))

            Spacer(Modifier.height(16.dp))

            // Final score
            Text(
                text       = "%,d".format(state.score),
                fontSize   = 64.sp,
                fontWeight = FontWeight.Bold,
                color      = Color(0xFF4FC3F7)
            )
            Text("FINAL SCORE", fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))

            Spacer(Modifier.height(8.dp))

            // Accuracy
            Text(
                text     = "%.1f%%  Accuracy".format(state.accuracy * 100),
                fontSize = 22.sp,
                color    = accuracyColor(state.accuracy)
            )
            Text("Max Combo  ${state.combo}×", fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f))

            Spacer(Modifier.height(8.dp))

            // Breakdown
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                StatBox("PERFECT", state.perfectCount, Color(0xFF4FC3F7))
                StatBox("GOOD",    state.goodCount,    Color(0xFF81C784))
                StatBox("MISS",    state.missCount,    Color(0xFFFF5252))
            }

            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = onPlayAgain,
                    colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FC3F7),
                                                          contentColor   = Color.Black)
                ) {
                    Icon(Icons.Default.Replay, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Play Again")
                }
                OutlinedButton(
                    onClick = onHome,
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Home, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Home")
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
    }
}

private fun accuracyColor(acc: Double): Color = when {
    acc >= 0.95 -> Color(0xFF4FC3F7)
    acc >= 0.80 -> Color(0xFF81C784)
    acc >= 0.60 -> Color(0xFFFFB74D)
    else        -> Color(0xFFFF5252)
}
