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
     * Tutorial mode: player pressed the correct key after [waitMs] ms.
     * [wrongPresses] = how many wrong keys were pressed before getting it right.
     */
    fun onTutorialHit(waitMs: Long, wrongPresses: Int) {
        val absMs = Math.abs(waitMs)  // negative = early press, positive = late; score by distance
        val base = when {
            absMs < 400   -> PERFECT_BASE      // excellent timing
            absMs < 1500  -> GOOD_BASE         // comfortable
            absMs < 4000  -> GOOD_BASE / 2     // slow/early but fine
            else          -> 10                // very off — minimum points
        }
        val penalty = wrongPresses * WRONG_KEY_PENALTY
        combo++   // increment first so multiplier sees updated combo (matches onHit behaviour)
        val points  = maxOf(0, base - penalty) * comboMultiplier()
        score += points
        if (combo > maxCombo) maxCombo = combo
        when {
            absMs < 400  -> perfectCount++
            absMs < 4000 -> goodCount++
            else         -> { missCount++; combo = 0 }   // very off → reset after calculation
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
        const val WRONG_KEY_PENALTY = 20
    }
}
