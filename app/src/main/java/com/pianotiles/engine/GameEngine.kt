package com.pianotiles.engine

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import com.pianotiles.midi.MidiInputManager
import com.pianotiles.midi.NoteEvent
import com.pianotiles.rendering.RenderCommand
import java.util.concurrent.ArrayBlockingQueue

enum class GameMode   { LISTEN, TUTORIAL }
enum class HandFilter { BOTH, RIGHT_HAND, LEFT_HAND }

/**
 * Game loop coordinator.
 *
 * LISTEN:   tiles fall automatically; song audio plays automatically via [onAutoNoteOn]/[onAutoNoteOff].
 *           MIDI keyboard presses light up keys and make sound but don't affect tiles.
 * TUTORIAL: time freezes when a tile arrives at the strike zone; the player must press the correct
 *           key to unfreeze. Scoring is based on reaction time + wrong key presses.
 *
 * All Choreographer calls happen on the main thread.
 * MIDI thread may call [onNoteOn]/[onNoteOff]; tutorial logic is posted to mainHandler.
 */
class GameEngine(
    private val renderCommands: ArrayBlockingQueue<RenderCommand>
) : MidiInputManager.NoteListener {

    val score = ScoreSystem()

    var onScoreUpdate : (() -> Unit)? = null
    var onSongComplete: (() -> Unit)? = null

    /** LISTEN mode: called on main thread when a note should start playing. */
    var onAutoNoteOn : ((note: Int, velocity: Int) -> Unit)? = null
    /** LISTEN mode: called on main thread when a note should stop playing. */
    var onAutoNoteOff: ((note: Int) -> Unit)? = null

    var gameMode: GameMode = GameMode.LISTEN

    private val mainHandler     = Handler(Looper.getMainLooper())
    private val pendingNoteOffs = mutableListOf<Runnable>()
    private val activeAutoNotes = mutableSetOf<Int>()  // notes currently sounding in LISTEN mode

    private var events    : List<NoteEvent> = emptyList()
    private var scheduler : TileScheduler?  = null

    @Volatile private var isPlaying   = false
    private var startNs               = 0L
    private var pausedAtSec           = 0.0

    private val songTimeSec get() =
        if (!isPlaying) pausedAtSec
        else pausedAtSec + (System.nanoTime() - startNs) / 1_000_000_000.0

    private val spawnedTiles        = mutableSetOf<Int>()   // event indices already spawned
    private val prevVisibleIndices  = mutableSetOf<Int>()   // for detecting tiles that exit viewport

    // ── Metronome ─────────────────────────────────────────────────────────
    var metronomeEnabled: Boolean = false
    var metronomeBpm    : Double  = 120.0
    var beatsPerBar     : Int     = 4
    /** Called with isDownbeat=true on beat 1 of each bar, false on all other beats. */
    var onBeatClick: ((isDownbeat: Boolean) -> Unit)? = null

    private var beatCount   = 0    // incremented each beat; resets on play()
    private var beatStartNs = 0L   // System.nanoTime() when beat 0 fired

    /**
     * Fires on wall-clock time — completely independent of game/tutorial/pause state.
     * Schedules each beat against an absolute origin so rounding errors never accumulate.
     */
    private val beatRunnable: Runnable = object : Runnable {
        override fun run() {
            if (!metronomeEnabled) return
            val isDownbeat = (beatCount % beatsPerBar.coerceAtLeast(1)) == 0
            onBeatClick?.invoke(isDownbeat)
            beatCount++
            val beatIntervalNs = (60_000_000_000.0 / metronomeBpm.coerceAtLeast(1.0)).toLong()
            val nextBeatNs     = beatStartNs + beatCount * beatIntervalNs
            val delayMs        = ((nextBeatNs - System.nanoTime()) / 1_000_000L).coerceAtLeast(0L)
            mainHandler.postDelayed(this, delayMs)
        }
    }

    // ── LISTEN auto-play ──────────────────────────────────────────────────
    private var autoPlayPtr = 0   // index into events of next note to auto-play

    // ── TUTORIAL state ────────────────────────────────────────────────────
    private var tutorialPaused         = false  // time frozen waiting for keypress
    private var tutorialEarlyWindow    = false  // note approaching — accept early press
    private val tutorialPendingIndices = mutableListOf<Int>()  // event indices in current chord
    private val tutorialRequiredNotes  = mutableSetOf<Int>()   // distinct MIDI notes needed
    private val tutorialPressedNotes   = mutableSetOf<Int>()   // which required notes pressed so far
    private var tutorialWrongPresses   = 0      // wrong-key presses + early releases so far
    private var tutorialKeyHeld        = -1     // MIDI note currently held; -1 = not held
    private var tutorialPressWaitMs    = 0L     // recorded press delta for final scoring at release
    private val tutorialCleared        = mutableSetOf<Int>()

    // ─────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────

    fun load(noteEvents: List<NoteEvent>, strikeZonePx: Int, screenHeight: Int,
             speedMultiplier: Float = 1.0f) {
        stop()
        // Scale note timestamps so the song actually plays at the requested speed.
        // 0.5× → all times doubled (slow), 2× → halved (fast). Tiles, audio, and
        // metronome all derive from these times, so they stay in sync automatically.
        events = if (speedMultiplier == 1.0f) noteEvents else noteEvents.map { ev ->
            ev.copy(
                startTimeSeconds = ev.startTimeSeconds / speedMultiplier,
                durationSeconds  = ev.durationSeconds  / speedMultiplier
            )
        }
        scheduler = TileScheduler(strikeZonePx, screenHeight,
                                  lookAheadSec = TileScheduler.DEFAULT_LOOK_AHEAD_SEC / speedMultiplier)
        spawnedTiles.clear()
        prevVisibleIndices.clear()
        tutorialCleared.clear()
        autoPlayPtr = 0
        score.reset()
        pausedAtSec = 0.0
        Log.d("GAME_ENGINE", "Loaded ${noteEvents.size} notes  mode=$gameMode  speed=${speedMultiplier}x")
    }

    fun play() {
        if (isPlaying) return
        isPlaying   = true
        startNs     = System.nanoTime()
        beatCount   = 0
        beatStartNs = startNs
        Choreographer.getInstance().postFrameCallback(frameCallback)
        if (metronomeEnabled) {
            mainHandler.removeCallbacks(beatRunnable)
            mainHandler.post(beatRunnable)   // first beat immediately, then self-reschedules
        }
        Log.d("GAME_ENGINE", "play() at %.3fs".format(songTimeSec))
    }

    fun pause() {
        if (!isPlaying) return
        pausedAtSec = songTimeSec
        isPlaying   = false
        Log.d("GAME_ENGINE", "pause() at %.3fs".format(pausedAtSec))
    }

    fun stop() {
        isPlaying          = false
        tutorialPaused     = false
        tutorialEarlyWindow = false
        tutorialPendingIndices.clear()
        tutorialRequiredNotes.clear()
        tutorialPressedNotes.clear()
        tutorialKeyHeld     = -1
        tutorialPressWaitMs = 0L
        pausedAtSec         = 0.0
        autoPlayPtr         = 0
        mainHandler.removeCallbacks(beatRunnable)
        spawnedTiles.clear()
        prevVisibleIndices.clear()
        tutorialCleared.clear()
        // Stop any notes currently sounding
        for (note in activeAutoNotes) {
            onAutoNoteOff?.invoke(note)
            renderCommands.offer(RenderCommand.ReleaseKey(note))
        }
        activeAutoNotes.clear()
        // Cancel any pending note-off callbacks
        for (r in pendingNoteOffs) mainHandler.removeCallbacks(r)
        pendingNoteOffs.clear()
        renderCommands.offer(RenderCommand.ClearTiles)
        renderCommands.offer(RenderCommand.UpdateProgress(0f))
    }

    // ─────────────────────────────────────────────────────────────────────
    // MIDI input — called from MIDI thread
    // ─────────────────────────────────────────────────────────────────────

    override fun onNoteOn(midiNote: Int, velocity: Int) {
        renderCommands.offer(RenderCommand.PressKey(midiNote))

        // LISTEN: keyboard lights up keys only; audio handled by ViewModel directly
        if (gameMode == GameMode.LISTEN) return

        // TUTORIAL: post to main thread so we can safely call Choreographer
        if (gameMode == GameMode.TUTORIAL && (tutorialPaused || tutorialEarlyWindow)) {
            val note = midiNote
            mainHandler.post { handleTutorialKeyPress(note) }
        }
    }

    override fun onNoteOff(midiNote: Int) {
        renderCommands.offer(RenderCommand.ReleaseKey(midiNote))
        // TUTORIAL: fire release whenever the held note is lifted (paused OR actively running)
        if (gameMode == GameMode.TUTORIAL && midiNote == tutorialKeyHeld) {
            mainHandler.post { handleTutorialKeyRelease() }
        }
    }

    /**
     * Player pressed a key while waiting for input (frozen) or in the early-press window.
     *
     * Timing offset:
     *   waitMs = (songTimeSec − noteStartSec) × 1000
     *   Negative = pressed early (before tile hits strike zone).
     *   Positive = pressed late (after freeze began).
     *   Both are scored by |waitMs|.
     *
     * Correct key, single pitch  → start holding; tile falls through strike zone.
     * Correct key, true chord    → flash tiles and advance immediately.
     * Wrong key                  → penalty, stay frozen / stay in early window.
     */
    private fun handleTutorialKeyPress(midiNote: Int) {
        if (!tutorialPaused && !tutorialEarlyWindow) return
        if (midiNote in tutorialPressedNotes) return

        if (midiNote in tutorialRequiredNotes) {
            tutorialPressedNotes.add(midiNote)

            if (tutorialPressedNotes.containsAll(tutorialRequiredNotes)) {
                // Compute timing offset relative to the note's actual start time.
                // Negative = early press; positive = late press after freeze.
                val firstNoteStart = tutorialPendingIndices
                    .mapNotNull { events.getOrNull(it)?.startTimeSeconds }
                    .minOrNull() ?: songTimeSec
                val waitMs = ((songTimeSec - firstNoteStart) * 1000).toLong()

                if (tutorialRequiredNotes.size == 1) {
                    // Single pitch — tile continues falling while key is held.
                    // Store the press delta; final score is computed at release (includes release accuracy).
                    tutorialPressWaitMs = waitMs
                    tutorialKeyHeld    = midiNote
                    tutorialPaused     = false
                    tutorialEarlyWindow = false
                    Log.d("GAME_ENGINE", "TUTORIAL press note=$midiNote waitMs=$waitMs")
                    if (!isPlaying) {
                        isPlaying = true
                        startNs   = System.nanoTime()
                        Choreographer.getInstance().postFrameCallback(frameCallback)
                    }
                    // If already playing (early-window path), clock keeps running — no restart needed.
                } else {
                    // True chord — flash tiles and advance immediately (no hold required).
                    // Score immediately with press delta only (no release component for chords).
                    for (idx in tutorialPendingIndices)
                        renderCommands.offer(RenderCommand.HitTile(idx, events[idx].midiNote))
                    score.onTutorialHit(waitMs, tutorialWrongPresses, 0L)
                    onScoreUpdate?.invoke()
                    tutorialEarlyWindow = false
                    clearAndResumeTutorial()
                }
            }
        } else {
            score.onWrongKey()
            tutorialWrongPresses++
            onScoreUpdate?.invoke()
            Log.d("GAME_ENGINE", "TUTORIAL wrong key=$midiNote expected=$tutorialRequiredNotes")
        }
    }

    /**
     * Player released the held key.
     * If within [RELEASE_TOLERANCE_SEC] of the note's end → accept as complete (no penalty).
     * Otherwise re-freeze so the player must press and hold again (penalty).
     */
    private fun handleTutorialKeyRelease() {
        if (tutorialKeyHeld == -1) return

        val endTime = tutorialPendingIndices
            .mapNotNull { events.getOrNull(it)?.let { ev -> ev.startTimeSeconds + ev.durationSeconds } }
            .maxOrNull() ?: 0.0
        val timeRemaining = endTime - songTimeSec

        if (timeRemaining <= RELEASE_TOLERANCE_SEC) {
            // Close enough to the end — score now using both press and release deltas.
            val releaseDeltaMs = (timeRemaining.coerceAtLeast(0.0) * 1000).toLong()
            score.onTutorialHit(tutorialPressWaitMs, tutorialWrongPresses, releaseDeltaMs)
            onScoreUpdate?.invoke()
            Log.d("GAME_ENGINE", "TUTORIAL release OK pressMs=$tutorialPressWaitMs releaseMs=$releaseDeltaMs")
            clearAndResumeTutorial()
        } else {
            // Too early — re-freeze + penalty
            pausedAtSec    = songTimeSec
            isPlaying      = false
            tutorialPaused = true
            tutorialKeyHeld = -1
            tutorialPressedNotes.clear()
            tutorialWrongPresses++
            score.onWrongKey()
            onScoreUpdate?.invoke()
            Log.d("GAME_ENGINE", "TUTORIAL early release ${timeRemaining * 1000}ms early — re-frozen")
        }
    }

    /** Marks all pending chord indices as cleared and resumes the song clock. */
    private fun clearAndResumeTutorial() {
        for (idx in tutorialPendingIndices) tutorialCleared.add(idx)
        tutorialPendingIndices.clear()
        tutorialRequiredNotes.clear()
        tutorialPressedNotes.clear()
        tutorialKeyHeld     = -1
        tutorialPressWaitMs = 0L
        tutorialPaused      = false
        if (!isPlaying) {
            // Transitioning from paused state (e.g. chord advance) — start the clock.
            isPlaying = true
            startNs   = System.nanoTime()
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
        // If already playing (note-completion path from doFrame), leave the clock untouched.
    }

    // ─────────────────────────────────────────────────────────────────────
    // Frame callback — runs on main thread
    // ─────────────────────────────────────────────────────────────────────

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            // Keep rendering during tutorial pause so the frozen tile stays visible
            if (!isPlaying && !tutorialPaused) return

            val t     = songTimeSec
            val sched = scheduler ?: run {
                Choreographer.getInstance().postFrameCallback(this); return
            }

            val totalDuration = events.lastOrNull()
                ?.let { it.startTimeSeconds + it.durationSeconds } ?: 1.0

            renderCommands.offer(RenderCommand.UpdateProgress(
                (t / totalDuration).toFloat().coerceIn(0f, 1f)))

            if (gameMode == GameMode.TUTORIAL) {
                when {
                    isPlaying && tutorialPendingIndices.isEmpty() -> {
                        // No active chord yet — check if one has arrived (freeze) or is approaching (early window)
                        val chord = findChordAtOrBefore(t)
                        if (chord.isNotEmpty()) {
                            // Chord arrived — freeze
                            pausedAtSec = t; isPlaying = false
                            tutorialPaused = true; tutorialEarlyWindow = false
                            tutorialPendingIndices.clear(); tutorialPendingIndices.addAll(chord)
                            tutorialRequiredNotes.clear(); tutorialRequiredNotes.addAll(chord.map { events[it].midiNote })
                            tutorialPressedNotes.clear()
                            tutorialWrongPresses = 0; tutorialKeyHeld = -1
                            Log.d("GAME_ENGINE", "TUTORIAL frozen chord=${tutorialRequiredNotes} indices=${chord.size}")
                        } else {
                            // Check for approaching note in early-press window
                            val earlyChord = findChordInEarlyWindow(t)
                            if (earlyChord.isNotEmpty()) {
                                tutorialEarlyWindow = true
                                tutorialPendingIndices.clear(); tutorialPendingIndices.addAll(earlyChord)
                                tutorialRequiredNotes.clear(); tutorialRequiredNotes.addAll(earlyChord.map { events[it].midiNote })
                                tutorialPressedNotes.clear()
                                tutorialWrongPresses = 0; tutorialKeyHeld = -1
                                Log.d("GAME_ENGINE", "TUTORIAL early window chord=${tutorialRequiredNotes}")
                            }
                        }
                    }
                    isPlaying && tutorialEarlyWindow && tutorialKeyHeld == -1 -> {
                        // In early window, player hasn't pressed yet — check if note has now arrived
                        val firstStart = tutorialPendingIndices
                            .mapNotNull { events.getOrNull(it)?.startTimeSeconds }
                            .minOrNull() ?: Double.MAX_VALUE
                        if (t >= firstStart) {
                            // Note arrived without early press — freeze normally
                            tutorialEarlyWindow = false
                            pausedAtSec = t; isPlaying = false; tutorialPaused = true
                            tutorialPressedNotes.clear(); tutorialWrongPresses = 0
                            Log.d("GAME_ENGINE", "TUTORIAL early window expired, now frozen")
                        }
                    }
                    isPlaying && tutorialKeyHeld != -1 -> {
                        // Key is held — check if the note has fully played through
                        val endTime = tutorialPendingIndices
                            .mapNotNull { events.getOrNull(it)?.let { ev -> ev.startTimeSeconds + ev.durationSeconds } }
                            .maxOrNull() ?: 0.0
                        if (t >= endTime) {
                            // Player held to the natural end — perfect release (delta = 0)
                            score.onTutorialHit(tutorialPressWaitMs, tutorialWrongPresses, 0L)
                            onScoreUpdate?.invoke()
                            Log.d("GAME_ENGINE", "TUTORIAL note complete at t=${t}s end=${endTime}s")
                            clearAndResumeTutorial()
                        }
                    }
                }
            }

            // Compute visible tiles for this frame
            val visibleTiles = sched.getVisibleTiles(events, t)
            val currentVisible = HashSet<Int>(visibleTiles.size)

            for (tile in visibleTiles) {
                currentVisible.add(tile.eventIndex)
                if (tile.eventIndex !in spawnedTiles) {
                    spawnedTiles.add(tile.eventIndex)
                    renderCommands.offer(RenderCommand.SpawnTile(
                        tile.eventIndex, tile.event.midiNote, tile.yPx, tile.heightPx, tile.color))
                } else {
                    renderCommands.offer(RenderCommand.UpdateTileY(tile.eventIndex, tile.yPx))
                }
            }

            // Silently release tiles that have exited the viewport so the pool stays clean
            for (idx in prevVisibleIndices) {
                if (idx !in currentVisible) {
                    renderCommands.offer(RenderCommand.ReleaseTile(idx))
                }
            }

            if (gameMode == GameMode.LISTEN) {
                // Auto-play audio + key highlight for notes reaching their start time
                while (autoPlayPtr < events.size && t >= events[autoPlayPtr].startTimeSeconds) {
                    val ev = events[autoPlayPtr]
                    val trackColor = TileScheduler.trackColor(ev.trackIndex)
                    activeAutoNotes.add(ev.midiNote)
                    onAutoNoteOn?.invoke(ev.midiNote, 80)
                    renderCommands.offer(RenderCommand.PressKey(ev.midiNote, trackColor))
                    val durMs = (ev.durationSeconds * 1000).toLong().coerceIn(50, 8000)
                    var r: Runnable? = null
                    r = Runnable {
                        activeAutoNotes.remove(ev.midiNote)
                        onAutoNoteOff?.invoke(ev.midiNote)
                        renderCommands.offer(RenderCommand.ReleaseKey(ev.midiNote))
                        pendingNoteOffs.remove(r)
                    }
                    pendingNoteOffs.add(r)
                    mainHandler.postDelayed(r, durMs)
                    autoPlayPtr++
                }
            }

            prevVisibleIndices.clear()
            prevVisibleIndices.addAll(currentVisible)

            // Beat grid lines — send Y positions to renderer each frame
            if (metronomeBpm > 0) {
                val pxPerSec     = sched.strikeZonePx / sched.lookAheadSec
                val beatSec      = 60.0 / metronomeBpm
                val firstBeatIdx = Math.floor(t / beatSec).toLong() - 1
                val beatYs   = mutableListOf<Float>()
                val barYs    = mutableListOf<Float>()
                for (i in firstBeatIdx..(firstBeatIdx + (sched.lookAheadSec / beatSec).toLong() + 2)) {
                    val beatTime = i * beatSec
                    val y = (sched.strikeZonePx - (beatTime - t) * pxPerSec).toFloat()
                    if (y < 0f || y > sched.strikeZonePx) continue
                    if ((i % beatsPerBar.coerceAtLeast(1)) == 0L) barYs.add(y) else beatYs.add(y)
                }
                renderCommands.offer(RenderCommand.UpdateBeatGrid(beatYs, barYs))
            }

            onScoreUpdate?.invoke()

            // Song-complete check
            val done = when (gameMode) {
                GameMode.LISTEN   -> t >= totalDuration + 1.0
                GameMode.TUTORIAL -> tutorialCleared.size >= events.size && !tutorialPaused
            }

            if (done && isPlaying) {
                isPlaying = false
                mainHandler.removeCallbacks(beatRunnable)   // stop metronome when song ends
                Log.d("GAME_ENGINE", "Song complete — score=${score.score} " +
                      "accuracy=%.1f%%".format(score.accuracy * 100))
                onScoreUpdate?.invoke()
                onSongComplete?.invoke()
                return
            }

            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    /**
     * Finds uncleared notes whose start time falls in the half-open interval (t, t + EARLY_WINDOW_SEC].
     * Returns the earliest such group (treated as a chord) so the player can press slightly early.
     */
    private fun findChordInEarlyWindow(t: Double): List<Int> {
        var firstTime = Double.MAX_VALUE
        for (i in events.indices) {
            if (i in tutorialCleared) continue
            val st = events[i].startTimeSeconds
            if (st > t && st <= t + EARLY_WINDOW_SEC && st < firstTime) firstTime = st
        }
        if (firstTime == Double.MAX_VALUE) return emptyList()
        return events.indices.filter { i ->
            i !in tutorialCleared &&
            Math.abs(events[i].startTimeSeconds - firstTime) <= CHORD_TOLERANCE_SEC
        }
    }

    /**
     * Finds all uncleared events that form the "current chord":
     *   1. Find the earliest uncleared note at or before [t] → its start time is [firstTime].
     *   2. Return all uncleared events within [CHORD_TOLERANCE_SEC] of [firstTime].
     *      This groups notes on separate tracks that are musically simultaneous
     *      (same note on multiple tracks, or a true chord with slightly staggered timestamps).
     */
    private fun findChordAtOrBefore(t: Double): List<Int> {
        var firstTime = Double.MAX_VALUE
        for (i in events.indices) {
            if (i in tutorialCleared) continue
            val st = events[i].startTimeSeconds
            if (st <= t && st < firstTime) firstTime = st
        }
        if (firstTime == Double.MAX_VALUE) return emptyList()

        return events.indices.filter { i ->
            i !in tutorialCleared &&
            Math.abs(events[i].startTimeSeconds - firstTime) <= CHORD_TOLERANCE_SEC
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Pure helpers — no Android deps, unit-testable
    // ─────────────────────────────────────────────────────────────────────

    companion object {
        /** Notes within this window of the trigger note are treated as one chord. */
        const val CHORD_TOLERANCE_SEC = 0.15
        /** How many seconds before a note's end the player may release without penalty. */
        const val RELEASE_TOLERANCE_SEC = 0.6
        /** How far in advance the player may press a note early (negative timing offset). */
        const val EARLY_WINDOW_SEC = 0.8

        /**
         * Returns event indices that were visible in [prevVisible] but are
         * absent from [currentVisible] and have been spawned.
         * Used to detect tiles that exited the viewport this frame.
         */
        fun exitedThisFrame(
            prevVisible  : Set<Int>,
            currentVisible: Set<Int>,
            spawned      : Set<Int>
        ): List<Int> = prevVisible.filter { it !in currentVisible && it in spawned }

        /**
         * Returns events whose start time falls in the half-open interval (prevT, currentT].
         * Used to determine which notes should auto-play in LISTEN mode.
         */
        fun notesInWindow(
            events     : List<NoteEvent>,
            prevT      : Double,
            currentT   : Double
        ): List<NoteEvent> = events.filter { it.startTimeSeconds > prevT && it.startTimeSeconds <= currentT }
    }
}
