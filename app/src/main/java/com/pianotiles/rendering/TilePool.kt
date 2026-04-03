package com.pianotiles.rendering

/**
 * Pre-allocated pool of 200 tile data slots.
 * Avoids allocations on the hot rendering path.
 *
 * Tiles are looked up by [eventIdx] (unique per note occurrence),
 * NOT by MIDI note number. This allows the same MIDI note to have
 * multiple simultaneous tiles without slot collisions.
 */
class TilePool {

    enum class HitState { NORMAL, HIT, MISSED }

    data class TileData(
        var eventIdx : Int     = -1,
        var midiNote : Int     = 0,
        var yPx      : Float   = 0f,
        var heightPx : Float   = 0f,
        var color    : Int     = 0,       // ARGB packed
        var active   : Boolean = false,
        var hitState : HitState = HitState.NORMAL,
        var hitAnimMs: Float   = 0f       // ms elapsed since hit/miss
    )

    @PublishedApi internal val pool = Array(MAX_TILES) { TileData() }

    /** Returns the first inactive slot, or null if pool is exhausted */
    fun acquire(): TileData? = pool.firstOrNull { !it.active }?.also {
        it.active    = true
        it.hitState  = HitState.NORMAL
        it.hitAnimMs = 0f
        it.eventIdx  = -1
    }

    fun release(tile: TileData) { tile.active = false }

    /** Find a tile by its unique event index. */
    fun findByEventIdx(idx: Int): TileData? =
        pool.firstOrNull { it.active && it.eventIdx == idx }

    /** Iterate only active tiles */
    inline fun forEachActive(block: (TileData) -> Unit) {
        for (tile in pool) {
            if (tile.active) block(tile)
        }
    }

    fun activeCount(): Int = pool.count { it.active }

    companion object {
        const val MAX_TILES = 200
    }
}
