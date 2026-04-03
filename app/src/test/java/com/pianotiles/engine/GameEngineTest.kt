package com.pianotiles.engine

import com.pianotiles.midi.NoteEvent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

// ─────────────────────────────────────────────────────────────────────────────
// ScoreSystem — Tutorial mode tests
// ─────────────────────────────────────────────────────────────────────────────

class ScoreSystemTutorialTest {

    private lateinit var score: ScoreSystem

    @Before fun setUp() { score = ScoreSystem() }

    // onTutorialHit — timing thresholds

    @Test fun `tutorial perfect under 400ms`() {
        score.onTutorialHit(200L, 0)
        assertEquals(ScoreSystem.PERFECT_BASE, score.score)
        assertEquals(1, score.perfectCount)
    }

    @Test fun `tutorial good between 400ms and 1500ms`() {
        score.onTutorialHit(800L, 0)
        assertEquals(ScoreSystem.GOOD_BASE, score.score)
        assertEquals(1, score.goodCount)
    }

    @Test fun `tutorial ok between 1500ms and 4000ms`() {
        score.onTutorialHit(2000L, 0)
        assertEquals(ScoreSystem.GOOD_BASE / 2, score.score)
        assertEquals(1, score.goodCount)
    }

    @Test fun `tutorial minimum points over 4000ms`() {
        score.onTutorialHit(5000L, 0)
        assertEquals(10, score.score)
        assertEquals(1, score.missCount)
    }

    @Test fun `tutorial combo resets on very late hit`() {
        score.onTutorialHit(200L, 0)   // perfect, combo = 1
        score.onTutorialHit(5000L, 0)  // very late → miss, combo resets
        assertEquals(0, score.combo)
    }

    @Test fun `tutorial wrong key penalty deducted`() {
        score.onTutorialHit(200L, wrongPresses = 2)
        val expected = ScoreSystem.PERFECT_BASE - 2 * ScoreSystem.WRONG_KEY_PENALTY
        assertEquals(expected, score.score)
    }

    @Test fun `tutorial score never goes below zero from penalties`() {
        score.onTutorialHit(200L, wrongPresses = 10)  // 10 × 20 = 200 penalty on 100 base
        assertTrue(score.score >= 0)
    }

    @Test fun `tutorial combo multiplier applies`() {
        repeat(10) { score.onTutorialHit(200L, 0) }   // 10 perfects: 9×100 + 1×200 = 1100
        assertEquals(1100, score.score)
    }

    // onWrongKey

    @Test fun `wrong key deducts penalty`() {
        score.onTutorialHit(200L, 0)   // gain 100 pts first
        val before = score.score
        score.onWrongKey()
        assertEquals(before - ScoreSystem.WRONG_KEY_PENALTY, score.score)
    }

    @Test fun `wrong key resets combo`() {
        repeat(5) { score.onTutorialHit(200L, 0) }
        score.onWrongKey()
        assertEquals(0, score.combo)
    }

    @Test fun `wrong key score clamped to zero`() {
        score.onWrongKey()   // score already 0
        assertEquals(0, score.score)
    }

    @Test fun `wrong key increments miss count`() {
        score.onWrongKey()
        assertEquals(1, score.missCount)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GameEngine pure helpers
// ─────────────────────────────────────────────────────────────────────────────

class GameEnginePureHelpersTest {

    private fun note(midi: Int, startSec: Double, dur: Double = 0.5) =
        NoteEvent(midi, startSec, dur, 80, 0)

    // exitedThisFrame

    @Test fun `exits returns indices that left viewport`() {
        val prev    = setOf(0, 1, 2)
        val current = setOf(1, 2)
        val spawned = setOf(0, 1, 2)
        val exited  = GameEngine.exitedThisFrame(prev, current, spawned)
        assertEquals(listOf(0), exited)
    }

    @Test fun `exits ignores indices not yet spawned`() {
        val prev    = setOf(0, 1)
        val current = setOf(1)
        val spawned = setOf(1)        // 0 was never spawned
        val exited  = GameEngine.exitedThisFrame(prev, current, spawned)
        assertTrue(exited.isEmpty())
    }

    @Test fun `exits empty when nothing changed`() {
        val visible = setOf(0, 1, 2)
        val exited  = GameEngine.exitedThisFrame(visible, visible, visible)
        assertTrue(exited.isEmpty())
    }

    // notesInWindow

    @Test fun `notes in window returns notes whose start time is in half-open interval`() {
        val events = listOf(note(60, 1.0), note(62, 2.0), note(64, 3.0))
        val ready  = GameEngine.notesInWindow(events, prevT = 0.9, currentT = 2.1)
        assertEquals(2, ready.size)
        assertEquals(60, ready[0].midiNote)
        assertEquals(62, ready[1].midiNote)
    }

    @Test fun `notes in window excludes note exactly at prevT`() {
        val events = listOf(note(60, 1.0))
        val ready  = GameEngine.notesInWindow(events, prevT = 1.0, currentT = 2.0)
        assertTrue(ready.isEmpty())
    }

    @Test fun `notes in window includes note exactly at currentT`() {
        val events = listOf(note(60, 2.0))
        val ready  = GameEngine.notesInWindow(events, prevT = 1.0, currentT = 2.0)
        assertEquals(1, ready.size)
    }

    @Test fun `notes in window returns empty when no notes in range`() {
        val events = listOf(note(60, 5.0))
        val ready  = GameEngine.notesInWindow(events, prevT = 0.0, currentT = 1.0)
        assertTrue(ready.isEmpty())
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ScoreSystem tests
// ─────────────────────────────────────────────────────────────────────────────

class ScoreSystemTest {

    private lateinit var score: ScoreSystem

    @Before fun setUp() { score = ScoreSystem() }

    @Test fun `perfect adds 100 points`() {
        score.onHit(ScoreSystem.HitQuality.PERFECT)
        assertEquals(100, score.score)
    }

    @Test fun `good adds 50 points`() {
        score.onHit(ScoreSystem.HitQuality.GOOD)
        assertEquals(50, score.score)
    }

    @Test fun `miss adds 0 points`() {
        score.onHit(ScoreSystem.HitQuality.MISS)
        assertEquals(0, score.score)
    }

    @Test fun `perfect increments combo`() {
        score.onHit(ScoreSystem.HitQuality.PERFECT)
        assertEquals(1, score.combo)
    }

    @Test fun `miss resets combo`() {
        repeat(5) { score.onHit(ScoreSystem.HitQuality.PERFECT) }
        score.onHit(ScoreSystem.HitQuality.MISS)
        assertEquals(0, score.combo)
    }

    @Test fun `miss does not reset maxCombo`() {
        repeat(5) { score.onHit(ScoreSystem.HitQuality.PERFECT) }
        score.onHit(ScoreSystem.HitQuality.MISS)
        assertEquals(5, score.maxCombo)
    }

    @Test fun `combo x2 multiplier at 10`() {
        repeat(9) { score.onHit(ScoreSystem.HitQuality.PERFECT) }
        val beforeCombo10 = score.score   // 9 × 100 = 900
        score.onHit(ScoreSystem.HitQuality.PERFECT)
        assertEquals(900 + 200, score.score)  // 10th hit: 100 × 2
    }

    @Test fun `accuracy 1 0 with all perfects`() {
        repeat(5) { score.onHit(ScoreSystem.HitQuality.PERFECT) }
        assertEquals(1.0, score.accuracy, 0.001)
    }

    @Test fun `accuracy 0 0 with all misses`() {
        repeat(5) { score.onHit(ScoreSystem.HitQuality.MISS) }
        assertEquals(0.0, score.accuracy, 0.001)
    }

    @Test fun `accuracy 0 75 mixed perfect and miss`() {
        repeat(3) { score.onHit(ScoreSystem.HitQuality.PERFECT) }
        score.onHit(ScoreSystem.HitQuality.MISS)
        assertEquals(0.75, score.accuracy, 0.001)
    }

    @Test fun `reset clears all state`() {
        repeat(5) { score.onHit(ScoreSystem.HitQuality.PERFECT) }
        score.reset()
        assertEquals(0, score.score)
        assertEquals(0, score.combo)
        assertEquals(0, score.maxCombo)
        assertEquals(0, score.totalNotes)
    }

    @Test fun `total notes counts all types`() {
        score.onHit(ScoreSystem.HitQuality.PERFECT)
        score.onHit(ScoreSystem.HitQuality.GOOD)
        score.onHit(ScoreSystem.HitQuality.MISS)
        assertEquals(3, score.totalNotes)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HitDetector tests
// ─────────────────────────────────────────────────────────────────────────────

class HitDetectorTest {

    private fun note(midiNote: Int, startSec: Double) =
        NoteEvent(midiNote, startSec, 0.5, 80, 0)

    @Test fun `perfect hit within 50ms`() {
        val det = HitDetector(listOf(note(60, 1.0)))
        val r   = det.checkHit(60, 1.030)  // 30ms late
        assertEquals(ScoreSystem.HitQuality.PERFECT, r.quality)
    }

    @Test fun `good hit within 150ms`() {
        val det = HitDetector(listOf(note(60, 1.0)))
        val r   = det.checkHit(60, 1.100)  // 100ms late
        assertEquals(ScoreSystem.HitQuality.GOOD, r.quality)
    }

    @Test fun `miss outside 150ms but inside search window`() {
        val det = HitDetector(listOf(note(60, 1.0)))
        val r   = det.checkHit(60, 1.200)  // 200ms late
        assertEquals(ScoreSystem.HitQuality.MISS, r.quality)
    }

    @Test fun `wrong note returns miss with no event`() {
        val det = HitDetector(listOf(note(60, 1.0)))
        val r   = det.checkHit(61, 1.0)
        assertEquals(ScoreSystem.HitQuality.MISS, r.quality)
        assertEquals(-1, r.eventIndex)
    }

    @Test fun `same note not matched twice`() {
        val det = HitDetector(listOf(note(60, 1.0)))
        det.checkHit(60, 1.0)
        val r2 = det.checkHit(60, 1.0)
        assertEquals(-1, r2.eventIndex)
    }

    @Test fun `early hit is negative timing error`() {
        val det = HitDetector(listOf(note(60, 1.0)))
        val r   = det.checkHit(60, 0.980)  // 20ms early
        assertTrue(r.timingErrorMs < 0)
    }

    @Test fun `collect missed events past good window`() {
        val det = HitDetector(listOf(note(60, 1.0), note(62, 2.0)))
        val missed = det.collectMissed(1.5)  // 500ms past note 60
        assertEquals(1, missed.size)
        assertEquals(0, missed[0])  // index 0
    }

    @Test fun `reset allows re-matching same event`() {
        val events = listOf(note(60, 1.0))
        val det    = HitDetector(events)
        det.checkHit(60, 1.0)
        det.reset()
        val r = det.checkHit(60, 1.0)
        assertEquals(ScoreSystem.HitQuality.PERFECT, r.quality)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TileScheduler tests
// ─────────────────────────────────────────────────────────────────────────────

class TileSchedulerTest {

    private val strikeZone = 800
    private val screenH    = 1000
    private val sched      = TileScheduler(strikeZone, screenH, lookAheadSec = 3.0)

    private fun note(midiNote: Int, startSec: Double) =
        NoteEvent(midiNote, startSec, 0.5, 80, 0)

    @Test fun `tile bottom edge at strike zone when song time equals note time`() {
        // Synthesia convention: BOTTOM of tile hits strike zone at note start time
        val tiles = sched.getVisibleTiles(listOf(note(60, 3.0)), 3.0)
        assertEquals(1, tiles.size)
        val yBottom = tiles[0].yPx + tiles[0].heightPx
        assertEquals(strikeZone.toFloat(), yBottom, 1f)
    }

    @Test fun `tile bottom edge at top of screen exactly one lookahead before hit`() {
        // One lookAheadSec before note time, tile's bottom edge is just entering from top
        val tiles = sched.getVisibleTiles(listOf(note(60, 3.0)), 0.0)
        assertEquals(1, tiles.size)
        val yBottom = tiles[0].yPx + tiles[0].heightPx
        assertEquals(0f, yBottom, 1f)
    }

    @Test fun `tile not visible before lookahead window`() {
        val tiles = sched.getVisibleTiles(listOf(note(60, 10.0)), 0.0)
        assertTrue(tiles.isEmpty())
    }

    @Test fun `tile not visible after passing keyboard`() {
        // Note at t=1.0, screenH=1000. Tile well past keyboard at t=8.0.
        val tiles = sched.getVisibleTiles(listOf(note(60, 1.0)), 8.0)
        assertTrue(tiles.isEmpty())
    }

    @Test fun `height proportional to duration`() {
        val ev    = NoteEvent(60, 3.0, 1.5, 80, 0)  // 1.5s = half of 3s lookahead
        val tiles = sched.getVisibleTiles(listOf(ev), 3.0)
        val pixelsPerSec = strikeZone / 3.0
        val expectedH = (1.5 * pixelsPerSec).toFloat()
        assertEquals(expectedH, tiles[0].heightPx, 1f)
    }

    @Test fun `minimum height enforced for very short notes`() {
        val ev    = NoteEvent(60, 3.0, 0.001, 80, 0)  // near-zero duration
        val tiles = sched.getVisibleTiles(listOf(ev), 3.0)
        assertTrue(tiles[0].heightPx >= TileScheduler.MIN_HEIGHT_PX)
    }
}
