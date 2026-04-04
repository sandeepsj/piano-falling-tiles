package com.pianotiles.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.media.ToneGenerator
import com.pianotiles.audio.AudioManager
import com.pianotiles.engine.GameEngine
import com.pianotiles.engine.GameMode
import com.pianotiles.engine.HandFilter
import com.pianotiles.midi.BuiltInSongs
import com.pianotiles.midi.MidiFileParser
import com.pianotiles.midi.MidiInputManager
import com.pianotiles.midi.NoteEvent
import com.pianotiles.rendering.GameRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GameUiState(
    val songTitle    : String   = "",
    val isLoaded     : Boolean  = false,
    val isPlaying    : Boolean  = false,
    val isPaused     : Boolean  = false,
    val isFinished   : Boolean  = false,
    val score        : Int      = 0,
    val combo        : Int      = 0,
    val accuracy     : Double   = 1.0,
    val perfectCount : Int      = 0,
    val goodCount    : Int      = 0,
    val missCount    : Int      = 0,
    val totalNotes   : Int      = 0,
    val midiConnected    : Boolean  = false,
    val loadError        : String?  = null,
    val gameMode         : GameMode = GameMode.LISTEN,
    val audioEnabled     : Boolean    = true,
    val midiOutputEnabled: Boolean    = false,
    val metronomeEnabled : Boolean    = false,
    val bpm              : Double     = 120.0,        // song's original BPM from MIDI file
    val targetBpm        : Double     = 120.0,        // user-set playback BPM
    val beatsPerBar      : Int        = 4,
    val handFilter       : HandFilter = HandFilter.BOTH
)

class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(GameUiState())

    // Downbeat (beat 1) sounds louder/different to mark the bar
    private val downbeatGen = ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 90)
    private val weakBeatGen = ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 55)
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    // Parsed note events — available after loadMidiFile()
    private var songEvents: List<NoteEvent> = emptyList()

    // Set once the GL surface reports its dimensions (onSurfaceChanged)
    private var renderer: GameRenderer? = null

    val midiInput = MidiInputManager(app)

    // GameEngine is created once; its render queue is wired to the surface renderer
    private var engine: GameEngine? = null

    init {
        AudioManager.nativeInit()

        midiInput.setNoteListener(object : MidiInputManager.NoteListener {
            override fun onNoteOn(midiNote: Int, velocity: Int) {
                if (_uiState.value.audioEnabled) AudioManager.nativeNoteOn(midiNote, velocity)
                engine?.onNoteOn(midiNote, velocity)
            }
            override fun onNoteOff(midiNote: Int) {
                if (_uiState.value.audioEnabled) AudioManager.nativeNoteOff(midiNote)
                engine?.onNoteOff(midiNote)
            }
        })

        midiInput.onDeviceConnected    = { _uiState.update { it.copy(midiConnected = true) } }
        midiInput.onDeviceDisconnected = { _uiState.update { it.copy(midiConnected = false) } }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Called by GameScreen when AndroidView creates the surface view
    // ─────────────────────────────────────────────────────────────────────

    fun onRendererReady(r: GameRenderer) {
        renderer = r
        engine?.stop()   // cancel old engine's metronome before replacing
        val eng = GameEngine(r.renderCommands)
        eng.onScoreUpdate = { pushScore(eng) }
        eng.onSongComplete = {
            _uiState.update { it.copy(isPlaying = false, isFinished = true) }
        }
        engine = eng
        // Wire MIDI keyboard to engine
        midiInput.setNoteListener(object : MidiInputManager.NoteListener {
            override fun onNoteOn(midiNote: Int, velocity: Int) {
                if (_uiState.value.audioEnabled) AudioManager.nativeNoteOn(midiNote, velocity)
                eng.onNoteOn(midiNote, velocity)
            }
            override fun onNoteOff(midiNote: Int) {
                if (_uiState.value.audioEnabled) AudioManager.nativeNoteOff(midiNote)
                eng.onNoteOff(midiNote)
            }
        })
        // Auto-play audio in LISTEN mode — Oboe synth or MIDI output to keyboard
        eng.onAutoNoteOn  = { note, vel ->
            val s = _uiState.value
            if (s.midiOutputEnabled) midiInput.sendNoteOn(note, vel)
            else if (s.audioEnabled) AudioManager.nativeNoteOn(note, vel)
        }
        eng.onAutoNoteOff = { note ->
            val s = _uiState.value
            if (s.midiOutputEnabled) midiInput.sendNoteOff(note)
            else if (s.audioEnabled) AudioManager.nativeNoteOff(note)
        }
        // Metronome: loud accent on downbeat (beat 1), soft click on weak beats
        eng.onBeatClick = { isDownbeat ->
            if (isDownbeat) downbeatGen.startTone(ToneGenerator.TONE_PROP_ACK,  40)
            else            weakBeatGen.startTone(ToneGenerator.TONE_PROP_BEEP, 25)
        }

        // play() once the GL surface has reported its dimensions
        r.onSurfaceReady = ::play
    }

    // ─────────────────────────────────────────────────────────────────────
    // File loading
    // ─────────────────────────────────────────────────────────────────────

    fun loadMidiFile(uri: Uri) {
        val title = resolveDisplayName(uri)
        _uiState.update { it.copy(isLoaded = false, loadError = null, songTitle = title) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes  = getApplication<Application>().contentResolver.openInputStream(uri)
                    ?.readBytes() ?: throw Exception("Cannot open file")
                val result = MidiFileParser.parse(bytes)
                songEvents = result.events
                _uiState.update { it.copy(
                    isLoaded   = true,
                    totalNotes  = result.noteCount,
                    songTitle   = title,
                    bpm         = result.bpm,
                    targetBpm   = result.bpm,
                    beatsPerBar = result.beatsPerBar
                )}
            } catch (e: Exception) {
                _uiState.update { it.copy(loadError = e.message) }
            }
        }
    }

    fun loadBuiltInSong(name: String, events: List<NoteEvent>, bpm: Double = 120.0) {
        songEvents = events
        _uiState.update { it.copy(
            isLoaded   = true,
            loadError  = null,
            songTitle  = name,
            totalNotes = events.size,
            bpm        = bpm,
            targetBpm  = bpm
        )}
    }

    private fun resolveDisplayName(uri: Uri): String {
        return try {
            getApplication<Application>().contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) c.getString(0)
                        .removeSuffix(".mid").removeSuffix(".MID")
                    else null
                }
        } catch (_: Exception) { null }
            ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Unknown"
    }

    // ─────────────────────────────────────────────────────────────────────
    // Mode & speed
    // ─────────────────────────────────────────────────────────────────────

    fun setMode(mode: GameMode) {
        _uiState.update { it.copy(gameMode = mode) }
    }

    fun setTargetBpm(bpm: Double) {
        _uiState.update { it.copy(targetBpm = bpm.coerceIn(20.0, 400.0)) }
    }

    fun setHandFilter(filter: HandFilter) {
        _uiState.update { it.copy(handFilter = filter) }
    }

    fun setAudio(enabled: Boolean) {
        _uiState.update { it.copy(audioEnabled = enabled) }
    }

    fun setMidiOutput(enabled: Boolean) {
        _uiState.update { it.copy(midiOutputEnabled = enabled) }
    }

    fun setMetronome(enabled: Boolean) {
        _uiState.update { it.copy(metronomeEnabled = enabled) }
        engine?.metronomeEnabled = enabled
    }

    // ─────────────────────────────────────────────────────────────────────
    // Playback control
    // ─────────────────────────────────────────────────────────────────────

    fun play() {
        val r   = renderer ?: return
        val eng = engine   ?: return
        val kl  = r.currentKeyLayout ?: return

        val songBpm   = _uiState.value.bpm.coerceAtLeast(1.0)
        val targetBpm = _uiState.value.targetBpm.coerceAtLeast(1.0)
        val speedMult = (targetBpm / songBpm).toFloat()
        val events = when (_uiState.value.handFilter) {
            HandFilter.BOTH       -> songEvents
            HandFilter.RIGHT_HAND -> songEvents.filter { it.trackIndex == 0 }
            HandFilter.LEFT_HAND  -> songEvents.filter { it.trackIndex == 1 }
        }
        eng.gameMode         = _uiState.value.gameMode
        eng.metronomeEnabled = _uiState.value.metronomeEnabled
        eng.metronomeBpm     = targetBpm
        eng.beatsPerBar      = _uiState.value.beatsPerBar
        eng.load(events, kl.strikeZonePx, r.screenHeight, speedMult)
        eng.play()
        _uiState.update { it.copy(isPlaying = true, isPaused = false, isFinished = false) }
    }

    fun pause() {
        engine?.pause()
        _uiState.update { it.copy(isPlaying = false, isPaused = true) }
    }

    fun resume() {
        engine?.play()
        _uiState.update { it.copy(isPlaying = true, isPaused = false) }
    }

    fun stop() {
        engine?.stop()
        _uiState.update { it.copy(isPlaying = false, isPaused = false, isFinished = false) }
        resetScoreDisplay()
    }

    /** Call before navigating back to the game screen so LaunchedEffect(isFinished) doesn't bounce. */
    fun prepareReplay() {
        _uiState.update { it.copy(isFinished = false) }
    }

    fun resetForHome() {
        stop()
        _uiState.update { GameUiState(
            midiConnected = _uiState.value.midiConnected,
            gameMode      = _uiState.value.gameMode
        )}
        songEvents = emptyList()
    }

    // ─────────────────────────────────────────────────────────────────────

    private fun pushScore(eng: GameEngine) {
        val s = eng.score
        _uiState.update { it.copy(
            score        = s.score,
            combo        = s.combo,
            accuracy     = s.accuracy,
            perfectCount = s.perfectCount,
            goodCount    = s.goodCount,
            missCount    = s.missCount
        )}
    }

    private fun resetScoreDisplay() {
        _uiState.update { it.copy(score = 0, combo = 0, accuracy = 1.0,
            perfectCount = 0, goodCount = 0, missCount = 0) }
    }

    override fun onCleared() {
        midiInput.stop()
        engine?.stop()
        downbeatGen.release()
        weakBeatGen.release()
        super.onCleared()
    }
}
