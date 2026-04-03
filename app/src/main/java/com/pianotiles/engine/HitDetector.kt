package com.pianotiles.engine

import com.pianotiles.midi.NoteEvent

/**
 * Matches a player's note-on against the expected NoteEvent list.
 *
 * For each incoming note, finds the nearest unplayed NoteEvent for that
 * MIDI pitch within the search window and classifies the timing error.
 *
 * Thread-safety: call only from the MIDI callback thread (or synchronize externally).
 */
class HitDetector(private val events: List<NoteEvent>) {

    data class HitResult(
        val quality: ScoreSystem.HitQuality,
        val eventIndex: Int,          // index in events list, -1 if no match
        val timingErrorMs: Double     // negative = early, positive = late
    )

    private val played = mutableSetOf<Int>()   // event indices already matched

    /** Call when a note-on arrives from the MIDI keyboard. */
    fun checkHit(midiNote: Int, songTimeSeconds: Double): HitResult {
        var bestIdx      = -1
        var bestErrorMs  = Double.MAX_VALUE

        for (i in events.indices) {
            if (i in played) continue
            val ev = events[i]
            if (ev.midiNote != midiNote) continue

            val errorMs = (songTimeSeconds - ev.startTimeSeconds) * 1000.0
            val absError = Math.abs(errorMs)

            // Only consider notes within the outer search window
            if (absError > SEARCH_WINDOW_MS) continue

            if (absError < Math.abs(bestErrorMs)) {
                bestIdx     = i
                bestErrorMs = errorMs
            }
        }

        if (bestIdx == -1) return HitResult(ScoreSystem.HitQuality.MISS, -1, 0.0)

        played.add(bestIdx)

        val quality = when {
            Math.abs(bestErrorMs) <= PERFECT_WINDOW_MS -> ScoreSystem.HitQuality.PERFECT
            Math.abs(bestErrorMs) <= GOOD_WINDOW_MS    -> ScoreSystem.HitQuality.GOOD
            else                                        -> ScoreSystem.HitQuality.MISS
        }
        return HitResult(quality, bestIdx, bestErrorMs)
    }

    /** Call when advancing time — expire events the player never hit. */
    fun collectMissed(songTimeSeconds: Double): List<Int> {
        val missed = mutableListOf<Int>()
        for (i in events.indices) {
            if (i in played) continue
            val ev = events[i]
            val lateMs = (songTimeSeconds - ev.startTimeSeconds) * 1000.0
            if (lateMs > GOOD_WINDOW_MS) {
                played.add(i)
                missed.add(i)
            }
        }
        return missed
    }

    fun reset() = played.clear()

    companion object {
        const val PERFECT_WINDOW_MS = 50.0
        const val GOOD_WINDOW_MS    = 150.0
        const val SEARCH_WINDOW_MS  = 300.0  // outer limit to find a candidate
    }
}
