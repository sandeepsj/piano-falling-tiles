package com.pianotiles.engine

import com.pianotiles.rendering.CoordSystem
import com.pianotiles.rendering.KeyLayout
import org.junit.Assert.*
import org.junit.Test

class CoordSystemTest {

    @Test
    fun `pxToNdcX left edge is minus one`() {
        assertEquals(-1f, CoordSystem.pxToNdcX(0f, 1920), 0.001f)
    }

    @Test
    fun `pxToNdcX right edge is plus one`() {
        assertEquals(1f, CoordSystem.pxToNdcX(1920f, 1920), 0.001f)
    }

    @Test
    fun `pxToNdcX center is zero`() {
        assertEquals(0f, CoordSystem.pxToNdcX(960f, 1920), 0.001f)
    }

    @Test
    fun `pxToNdcY top edge is plus one`() {
        assertEquals(1f, CoordSystem.pxToNdcY(0f, 1200), 0.001f)
    }

    @Test
    fun `pxToNdcY bottom edge is minus one`() {
        assertEquals(-1f, CoordSystem.pxToNdcY(1200f, 1200), 0.001f)
    }

    @Test
    fun `rectToVertices returns 8 floats`() {
        val v = CoordSystem.rectToVertices(0f, 0f, 100f, 100f, 1920, 1200)
        assertEquals(8, v.size)
    }
}

class KeyLayoutTest {

    private val layout = KeyLayout(1920, 1200)

    @Test
    fun `88 keys total`() {
        assertEquals(88, KeyLayout.KEY_COUNT)
    }

    @Test
    fun `52 white keys`() {
        assertEquals(52, KeyLayout.WHITE_KEY_COUNT)
    }

    @Test
    fun `C4 is a white key`() {
        assertTrue(KeyLayout.isWhiteKey(60))
    }

    @Test
    fun `C sharp 4 is a black key`() {
        assertFalse(KeyLayout.isWhiteKey(61))
    }

    @Test
    fun `all 88 white key left edges are within screen width`() {
        for (note in KeyLayout.MIDI_MIN..KeyLayout.MIDI_MAX) {
            if (!KeyLayout.isWhiteKey(note)) continue
            val left  = layout.keyLeftPx(note)
            val right = left + layout.keyWidthPx(note)
            assertTrue("Key $note left $left < 0", left >= -1f)
            assertTrue("Key $note right $right > ${layout.screenWidth}", right <= layout.screenWidth + 1f)
        }
    }

    @Test
    fun `keyboard fills full screen width`() {
        // Last white key (C8 = note 108) right edge should be ~screenWidth
        val lastWhite = (KeyLayout.MIDI_MIN..KeyLayout.MIDI_MAX).last { KeyLayout.isWhiteKey(it) }
        val right = layout.keyLeftPx(lastWhite) + layout.whiteKeyWidthPx
        assertEquals(layout.screenWidth.toFloat(), right, 2f)
    }

    @Test
    fun `strike zone is above keyboard`() {
        assertTrue(layout.strikeZonePx < layout.keyboardTopPx)
    }

    @Test
    fun `MIDI A0 is the first note`() {
        assertEquals(21, KeyLayout.MIDI_MIN)
    }

    @Test
    fun `MIDI C8 is the last note`() {
        assertEquals(108, KeyLayout.MIDI_MAX)
    }
}
