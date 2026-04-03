package com.pianotiles.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Switch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pianotiles.engine.GameMode
import com.pianotiles.engine.HandFilter
import com.pianotiles.ui.GameUiState

@Composable
fun HomeScreen(
    state                : GameUiState,
    onPickFile           : (android.net.Uri) -> Unit,
    onLoadDemo           : () -> Unit,
    onLoadOdeToJoy       : () -> Unit,
    onLoadInterstellar   : () -> Unit,
    onStart              : () -> Unit,
    onSetMode       : (GameMode) -> Unit,
    onSetHandFilter : (HandFilter) -> Unit,
    onSetTargetBpm  : (Double) -> Unit,
    onSetAudio      : (Boolean) -> Unit,
    onSetMetronome  : (Boolean) -> Unit
) {
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { onPickFile(it) } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title
            Text(
                text       = "Piano Tiles",
                fontSize   = 48.sp,
                fontWeight = FontWeight.Bold,
                color      = Color.White
            )
            Text(
                text     = "Synthesia for your CASIO",
                fontSize = 16.sp,
                color    = Color.White.copy(alpha = 0.5f)
            )

            Spacer(Modifier.height(8.dp))

            // Demo song buttons
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onLoadDemo(); onStart() },
                    modifier = Modifier.height(52.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4FC3F7),
                        contentColor   = Color.Black
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Twinkle Twinkle", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { onLoadOdeToJoy(); onStart() },
                    modifier = Modifier.height(52.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF81C784),
                        contentColor   = Color.Black
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Ode to Joy", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { onLoadInterstellar(); onStart() },
                    modifier = Modifier.height(52.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFB39DDB),
                        contentColor   = Color.Black
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Interstellar", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Text(
                text     = "— or pick your own file —",
                color    = Color.White.copy(alpha = 0.3f),
                fontSize = 12.sp
            )

            // MIDI file picker
            OutlinedButton(
                onClick = { filePicker.launch(arrayOf("*/*")) },
                colors  = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF4FC3F7))
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.songTitle.isNotEmpty()) state.songTitle else "Pick MIDI File")
            }

            // Load error
            state.loadError?.let {
                Text(text = "Error: $it", color = Color(0xFFFF5252), fontSize = 13.sp)
            }

            // MIDI device status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Usb,
                    contentDescription = null,
                    tint = if (state.midiConnected) Color(0xFF81C784) else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text  = if (state.midiConnected) "MIDI keyboard connected" else "No MIDI keyboard",
                    color = if (state.midiConnected) Color(0xFF81C784) else Color.Gray,
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(4.dp))

            // ── Mode selector ─────────────────────────────────────────────
            Text("Mode", fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ModeButton(
                    label    = "Listen / Watch",
                    subtitle = "Tiles fall automatically",
                    selected = state.gameMode == GameMode.LISTEN,
                    onClick  = { onSetMode(GameMode.LISTEN) }
                )
                ModeButton(
                    label    = "Tutorial / Practice",
                    subtitle = "Tiles wait for your key",
                    selected = state.gameMode == GameMode.TUTORIAL,
                    onClick  = { onSetMode(GameMode.TUTORIAL) }
                )
            }

            Spacer(Modifier.height(4.dp))

            // ── Hand filter ───────────────────────────────────────────────
            Text("Practice Hand", fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    HandFilter.BOTH       to "Both Hands",
                    HandFilter.RIGHT_HAND to "Right Only",
                    HandFilter.LEFT_HAND  to "Left Only"
                ).forEach { (filter, label) ->
                    val selected = state.handFilter == filter
                    OutlinedButton(
                        onClick = { onSetHandFilter(filter) },
                        colors  = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) Color(0xFF4FC3F7) else Color.Transparent,
                            contentColor   = if (selected) Color.Black       else Color(0xFF4FC3F7)
                        ),
                        border  = androidx.compose.foundation.BorderStroke(
                            1.dp, if (selected) Color.Transparent else Color(0xFF4FC3F7)
                        ),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── App audio toggle ──────────────────────────────────────────
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("App Audio", fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
                Switch(
                    checked         = state.audioEnabled,
                    onCheckedChange = onSetAudio,
                    colors          = SwitchDefaults.colors(
                        checkedThumbColor  = Color(0xFF4FC3F7),
                        checkedTrackColor  = Color(0xFF4FC3F7).copy(alpha = 0.4f)
                    )
                )
                Text(
                    text     = if (state.audioEnabled) "On (piano from app)" else "Off (use your piano's sound)",
                    fontSize = 12.sp,
                    color    = Color.White.copy(alpha = 0.35f)
                )
            }

            // ── BPM input + Metronome toggle ──────────────────────────────
            var bpmText by remember(state.targetBpm) {
                mutableStateOf("${state.targetBpm.toInt()}")
            }
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("BPM", fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
                OutlinedTextField(
                    value         = bpmText,
                    onValueChange = { text ->
                        bpmText = text
                        text.toDoubleOrNull()?.let { v ->
                            if (v in 20.0..400.0) onSetTargetBpm(v)
                        }
                    },
                    singleLine      = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier        = Modifier.width(100.dp),
                    colors          = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Color(0xFF4FC3F7),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedTextColor     = Color.White,
                        unfocusedTextColor   = Color.White
                    ),
                    textStyle = LocalTextStyle.current.copy(fontSize = 16.sp)
                )
                Text("Metronome", fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
                Switch(
                    checked         = state.metronomeEnabled,
                    onCheckedChange = onSetMetronome,
                    colors          = SwitchDefaults.colors(
                        checkedThumbColor  = Color(0xFF4FC3F7),
                        checkedTrackColor  = Color(0xFF4FC3F7).copy(alpha = 0.4f)
                    )
                )
            }

            Spacer(Modifier.height(4.dp))

            // Start button
            Button(
                onClick  = onStart,
                enabled  = state.isLoaded,
                modifier = Modifier
                    .width(200.dp)
                    .height(52.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4FC3F7),
                    contentColor   = Color.Black
                )
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Start", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            if (!state.isLoaded && state.songTitle.isEmpty()) {
                Text(
                    text     = "Pick a .mid file to begin",
                    color    = Color.White.copy(alpha = 0.35f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun ModeButton(
    label    : String,
    subtitle : String,
    selected : Boolean,
    onClick  : () -> Unit
) {
    val containerColor = if (selected) Color(0xFF4FC3F7) else Color.Transparent
    val contentColor   = if (selected) Color.Black       else Color(0xFF4FC3F7)
    val borderColor    = Color(0xFF4FC3F7)

    OutlinedButton(
        onClick  = onClick,
        colors   = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor   = contentColor
        ),
        border   = androidx.compose.foundation.BorderStroke(
            1.dp, if (selected) Color.Transparent else borderColor
        ),
        modifier = Modifier.width(170.dp).height(64.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label,    fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 10.sp,
                color = if (selected) Color.Black.copy(alpha = 0.6f)
                        else Color(0xFF4FC3F7).copy(alpha = 0.7f))
        }
    }
}
