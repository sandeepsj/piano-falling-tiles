package com.pianotiles.engine

/**
 * Tracks score, combo, and accuracy for one song run.
 * Pure logic — no Android dependencies. Fully unit-testable.
 */
class ScoreSystem {

    enum class HitQuality { PERFECT, GOOD, MISS }

    var score        = 0; private set
    var combo        = 0; private set
    var maxCombo     = 0; private set
    var perfectCount = 0; private set
    var goodCount    = 0; private set
    var missCount    = 0; private set

    val totalNotes get() = perfectCount + goodCount + missCount
    val accuracy   get() = if (totalNotes == 0) 1.0
                           else (perfectCount + goodCount * 0.5) / totalNotes

    fun onHit(quality: HitQuality) {
        when (quality) {
            HitQuality.PERFECT -> { combo++; score += PERFECT_BASE * comboMultiplier(); perfectCount++ }
            HitQuality.GOOD    -> { combo++; score += GOOD_BASE    * comboMultiplier(); goodCount++    }
            HitQuality.MISS    -> { combo = 0; missCount++ }
        }
        if (combo > maxCombo) maxCombo = combo
    }

    /**
     * Tutorial mode: player completed a note.
     *
     * [waitMs]        — press timing offset in ms (negative = early, positive = late).
     * [wrongPresses]  — wrong keys pressed before the correct one.
     * [releaseDeltaMs]— how far before the note's end the key was released (≥ 0).
     *                   Pass 0 when the note completed by time (player held to the end).
     *
     * Score formula: smooth linear decay so every millisecond of timing error is felt.
     *   press  : PERFECT_BASE → MIN_PRESS_SCORE  over MAX_PRESS_MS
     *   release: RELEASE_BASE → 0                over MAX_RELEASE_MS  (bonus on top)
     *   penalty: WRONG_KEY_PENALTY per wrong press, subtracted before combo multiply.
     */
    fun onTutorialHit(waitMs: Long, wrongPresses: Int, releaseDeltaMs: Long = 0L) {
        val pressAbsMs = Math.abs(waitMs)

        // Smooth press score (linear decay)
        val pressScore = (PERFECT_BASE * (1.0 - pressAbsMs.toDouble() / MAX_PRESS_MS))
            .toInt().coerceAtLeast(MIN_PRESS_SCORE)

        // Smooth release bonus (linear decay; 0 when player held to natural end or time completed)
        val releaseScore = (RELEASE_BASE * (1.0 - releaseDeltaMs.toDouble() / MAX_RELEASE_MS))
            .toInt().coerceAtLeast(0)

        val penalty = wrongPresses * WRONG_KEY_PENALTY
        combo++
        val points  = maxOf(0, pressScore + releaseScore - penalty) * comboMultiplier()
        score += points
        if (combo > maxCombo) maxCombo = combo

        when {
            pressAbsMs < PERFECT_WINDOW_MS -> perfectCount++
            pressAbsMs < GOOD_WINDOW_MS    -> goodCount++
            else                           -> { missCount++; combo = 0 }
        }
    }

    /** Tutorial mode: player pressed a wrong key. */
    fun onWrongKey() {
        score = maxOf(0, score - WRONG_KEY_PENALTY)
        combo = 0
        missCount++
    }

    fun reset() {
        score = 0; combo = 0; maxCombo = 0
        perfectCount = 0; goodCount = 0; missCount = 0
    }

    private fun comboMultiplier(): Int = when {
        combo >= 50 -> 4
        combo >= 20 -> 3
        combo >= 10 -> 2
        else        -> 1
    }

    companion object {
        const val PERFECT_BASE      = 100
        const val GOOD_BASE         = 50
        const val RELEASE_BASE      = 40    // max bonus for an accurate release
        const val WRONG_KEY_PENALTY = 20

        // Smooth decay limits
        const val MAX_PRESS_MS      = 2000.0   // press delta at which score reaches MIN_PRESS_SCORE
        const val MAX_RELEASE_MS    = 800.0    // release delta beyond which release bonus = 0
        const val MIN_PRESS_SCORE   = 5        // floor so any press-in-window scores something

        // Quality classification thresholds (display only — don't affect score formula)
        const val PERFECT_WINDOW_MS = 250L
        const val GOOD_WINDOW_MS    = 1000L
    }
}
