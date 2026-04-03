package com.pianotiles.rendering

/**
 * Thread-safe command objects posted into GameRenderer.renderCommands.
 * Only way other layers (Game Engine, MIDI Input) write to renderer state.
 * Renderer drains this queue at the start of each frame on the GL thread.
 *
 * Tile commands are keyed by [eventIdx] (unique per note occurrence),
 * NOT by MIDI note. This prevents pool-slot collisions when the same
 * note appears multiple times in a song.
 */
sealed class RenderCommand {
    /** Spawn a new falling tile for the given note occurrence */
    data class SpawnTile(
        val eventIdx : Int,
        val note     : Int,
        val yPx      : Float,
        val heightPx : Float,
        val color    : Int      // ARGB
    ) : RenderCommand()

    /** Update Y position of a specific tile (identified by event index) */
    data class UpdateTileY(val eventIdx: Int, val yPx: Float) : RenderCommand()

    /** Tile was hit — flash white then fade */
    data class HitTile(val eventIdx: Int, val note: Int) : RenderCommand()

    /** Tile was missed — turn red then fade */
    data class MissTile(val eventIdx: Int) : RenderCommand()

    /** Silently remove a tile (no animation) — used when tile exits the viewport */
    data class ReleaseTile(val eventIdx: Int) : RenderCommand()

    /** Highlight a piano key. [color] defaults to accent blue; LISTEN mode passes track color. */
    data class PressKey(val note: Int, val color: Int = 0xFF4FC3F7.toInt()) : RenderCommand()

    /** Remove key highlight */
    data class ReleaseKey(val note: Int) : RenderCommand()

    /** Update the song progress bar (0.0–1.0) */
    data class UpdateProgress(val progress: Float) : RenderCommand()

    /** Clear all active tiles (song reset/seek) */
    object ClearTiles : RenderCommand()

    /** Beat grid lines to draw in the tile area this frame. barYs = bar lines (thicker). */
    data class UpdateBeatGrid(val beatYs: List<Float>, val barYs: List<Float>) : RenderCommand()
}
