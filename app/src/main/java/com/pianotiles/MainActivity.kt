package com.pianotiles

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pianotiles.engine.HandFilter
import com.pianotiles.midi.BuiltInSongs
import com.pianotiles.ui.GameViewModel
import com.pianotiles.ui.screens.GameScreen
import com.pianotiles.ui.screens.HomeScreen
import com.pianotiles.ui.screens.ResultsScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color    = Color(0xFF0A0A14)
            ) {
                PianoTilesApp()
            }
        }
    }
}

@Composable
fun PianoTilesApp() {
    val navController = rememberNavController()
    val viewModel: GameViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Start MIDI scanning when app is foreground
    DisposableEffect(Unit) {
        viewModel.midiInput.start()
        onDispose { viewModel.midiInput.stop() }
    }

    NavHost(navController = navController, startDestination = "home") {

        composable("home") {
            HomeScreen(
                state          = state,
                onPickFile      = { uri -> viewModel.loadMidiFile(uri) },
                onLoadDemo      = { viewModel.loadBuiltInSong("Twinkle Twinkle", BuiltInSongs.TWINKLE_TWINKLE) },
                onLoadOdeToJoy  = { viewModel.loadBuiltInSong("Ode to Joy", BuiltInSongs.ODE_TO_JOY) },
                onStart         = { navController.navigate("game") },
                onSetMode       = viewModel::setMode,
                onSetHandFilter = viewModel::setHandFilter,
                onSetTargetBpm  = viewModel::setTargetBpm,
                onSetAudio       = viewModel::setAudio,
                onSetMidiOutput  = viewModel::setMidiOutput,
                onSetMetronome   = viewModel::setMetronome
            )
        }

        composable("game") {
            GameScreen(
                state     = state,
                viewModel = viewModel,
                onStop    = { navController.popBackStack() }
            )

            // Navigate to results when song finishes
            LaunchedEffect(state.isFinished) {
                if (state.isFinished) navController.navigate("results")
            }
        }

        composable("results") {
            ResultsScreen(
                state       = state,
                onPlayAgain = {
                    viewModel.prepareReplay()
                    navController.popBackStack("game", inclusive = false)
                },
                onHome      = {
                    viewModel.resetForHome()
                    navController.popBackStack("home", inclusive = false)
                }
            )
        }
    }
}
