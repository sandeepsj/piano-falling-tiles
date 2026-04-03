package com.pianotiles.rendering

/**
 * Computes pixel positions for all 88 piano keys (MIDI 21–108).
 * Pure Kotlin, no GL state. Used by both renderer and game engine.
 *
 * Standard piano layout:
 *   Octave pattern: C  C# D  D# E  F  F# G  G# A  A# B
 *   White keys: C D E F G A B  (7 per octave)
 *   Black keys: C# D# F# G# A# (5 per octave)
 */
class KeyLayout(val screenWidth: Int, val screenHeight: Int) {

    val keyboardHeightPx: Int = (screenHeight * 0.20f).toInt()  // 20% of screen height
    val keyboardTopPx: Int    = screenHeight - keyboardHeightPx

    // White key dimensions
    val whiteKeyWidthPx: Float  = screenWidth.toFloat() / WHITE_KEY_COUNT
    val whiteKeyHeightPx: Float = keyboardHeightPx.toFloat()

    // Black key dimensions (proportional to white keys)
    val blackKeyWidthPx: Float  = whiteKeyWidthPx * 0.65f
    val blackKeyHeightPx: Float = whiteKeyHeightPx * 0.60f

    // Strike zone — where tiles hit the keyboard
    val strikeZonePx: Int = keyboardTopPx - 4

    companion object {
        const val MIDI_MIN = 21    // A0
        const val MIDI_MAX = 108   // C8
        const val KEY_COUNT = 88
        const val WHITE_KEY_COUNT = 52

        // Standard MIDI: note % 12 gives position in octave starting from C
        // Index: 0=C 1=C# 2=D 3=D# 4=E 5=F 6=F# 7=G 8=G# 9=A 10=A# 11=B
        private val WHITE_PATTERN = booleanArrayOf(
            true,  false, true,  false, true,  // C C# D D# E
            true,  false, true,  false, true,  // F F# G G# A
            false, true                         // A# B
        )

        fun isWhiteKey(midiNote: Int): Boolean = WHITE_PATTERN[midiNote % 12]

        // Count white keys before this MIDI note (from MIDI_MIN)
        fun whiteKeysBefore(midiNote: Int): Int {
            var count = 0
            for (n in MIDI_MIN until midiNote) {
                if (isWhiteKey(n)) count++
            }
            return count
        }
    }

    /** Left edge x-position of a key in screen pixels */
    fun keyLeftPx(midiNote: Int): Float {
        return if (isWhiteKey(midiNote)) {
            whiteKeysBefore(midiNote) * whiteKeyWidthPx
        } else {
            // Black key: centered on the boundary between adjacent white keys.
            // whiteKeysBefore counts all white keys up to (not including) this note,
            // so prevWhiteCount * w is exactly the right-edge of the preceding white key.
            val prevWhiteCount = whiteKeysBefore(midiNote)
            prevWhiteCount * whiteKeyWidthPx - blackKeyWidthPx / 2f
        }
    }

    /** Width of a key in pixels */
    fun keyWidthPx(midiNote: Int): Float =
        if (isWhiteKey(midiNote)) whiteKeyWidthPx else blackKeyWidthPx

    /** Height of a key in pixels */
    fun keyHeightPx(midiNote: Int): Float =
        if (isWhiteKey(midiNote)) whiteKeyHeightPx else blackKeyHeightPx

    /** Center x of a key in screen pixels (useful for particle effects) */
    fun keyCenterXPx(midiNote: Int): Float =
        keyLeftPx(midiNote) + keyWidthPx(midiNote) / 2f
}
