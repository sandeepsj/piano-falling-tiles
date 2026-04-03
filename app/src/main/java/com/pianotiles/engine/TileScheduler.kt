package com.pianotiles.engine

import com.pianotiles.midi.NoteEvent

/**
 * Converts NoteEvents into screen-space tile positions for the current song time.
 *
 * Tile travels from y=0 (top of screen) to y=strikeZonePx over LOOK_AHEAD_SECONDS.
 * A tile is "visible" while its top edge is above the keyboard (y < screenHeight).
 *
 * Pure calculation — no state, no Android deps. Fully unit-testable.
 */
class TileScheduler(
    val strikeZonePx : Int,
    val screenHeight : Int,
    val lookAheadSec : Double = DEFAULT_LOOK_AHEAD_SEC
) {
    data class TileState(
        val event      : NoteEvent,
        val eventIndex : Int,
        val yPx        : Float,    // top edge of tile in screen pixels
        val heightPx   : Float,    // pixel height of tile
        val color      : Int       // ARGB
    )

    /**
     * Returns all tiles that should be visible at [songTimeSec].
     *
     * Synthesia convention: the BOTTOM edge of a tile reaches [strikeZonePx]
     * exactly when the note should be played (timeUntilHit == 0).
     * The tile then continues falling, appearing to "enter" the piano key.
     *
     * yBottom = strikeZonePx when timeUntilHit = 0
     * yBottom = 0            when timeUntilHit = lookAheadSec (tile enters top of screen)
     */
    fun getVisibleTiles(events: List<NoteEvent>, songTimeSec: Double): List<TileState> {
        val result = mutableListOf<TileState>()
        val pixelsPerSec = strikeZonePx / lookAheadSec

        for ((i, ev) in events.withIndex()) {
            val timeUntilHit = ev.startTimeSeconds - songTimeSec

            // Bottom edge position: at strikeZonePx when it's time to play
            val yBottom = (strikeZonePx - timeUntilHit * pixelsPerSec).toFloat()

            // Tile height proportional to note duration (min 20px so short notes stay visible)
            val height  = (ev.durationSeconds * pixelsPerSec).toFloat().coerceAtLeast(MIN_HEIGHT_PX)

            val yTop = yBottom - height

            // Skip tiles entirely above screen (bottom hasn't entered yet)
            if (yBottom < 0f) continue
            // Skip tiles whose top has fully passed the keyboard bottom
            if (yTop > screenHeight) continue

            result.add(TileState(
                event      = ev,
                eventIndex = i,
                yPx        = yTop,
                heightPx   = height,
                color      = trackColor(ev.trackIndex)
            ))
        }
        return result
    }

    companion object {
        const val DEFAULT_LOOK_AHEAD_SEC = 3.0
        const val MIN_HEIGHT_PX          = 20f

        // Track colors: right hand = blue, left hand = green, others = orange/purple
        val TRACK_COLORS = intArrayOf(
            0xFF4FC3F7.toInt(),   // track 0 — blue
            0xFF81C784.toInt(),   // track 1 — green
            0xFFFFB74D.toInt(),   // track 2 — orange
            0xFFCE93D8.toInt(),   // track 3 — purple
        )

        fun trackColor(trackIndex: Int): Int =
            TRACK_COLORS[trackIndex.coerceIn(0, TRACK_COLORS.size - 1)]
    }
}
