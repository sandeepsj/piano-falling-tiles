package com.pianotiles.rendering

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Simple fixed-array particle system.
 * 50 particles max. Each particle is a small colored square.
 * Spawned on tile hit, fade out over LIFETIME_MS.
 */
class ParticleSystem {

    internal data class Particle(
        var x: Float = 0f, var y: Float = 0f,
        var vx: Float = 0f, var vy: Float = 0f,
        var life: Float = 0f,   // 0.0 = dead, 1.0 = just spawned
        var color: Int = 0,
        var active: Boolean = false
    )

    @PublishedApi internal val particles = Array(MAX_PARTICLES) { Particle() }

    fun emit(x: Int, y: Int, color: Int) {
        repeat(BURST_COUNT) {
            val slot = particles.firstOrNull { !it.active } ?: return@repeat
            val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
            val speed = 1f + Random.nextFloat() * 3f
            slot.x      = x.toFloat()
            slot.y      = y.toFloat()
            slot.vx     = cos(angle) * speed
            slot.vy     = sin(angle) * speed - 2f  // slight upward bias
            slot.life   = 1f
            slot.color  = color
            slot.active = true
        }
    }

    fun update(deltaMs: Float) {
        val decay = deltaMs / LIFETIME_MS
        for (p in particles) {
            if (!p.active) continue
            p.x    += p.vx * deltaMs * 0.1f
            p.y    += p.vy * deltaMs * 0.1f
            p.life -= decay
            if (p.life <= 0f) p.active = false
        }
    }

    /**
     * Iterate active particles. Callback receives (x, y, color, alpha).
     * alpha is 0.0–1.0.
     */
    fun forEachActive(block: (x: Float, y: Float, color: Int, alpha: Float) -> Unit) {
        for (p in particles) {
            if (p.active) block(p.x, p.y, p.color, p.life.coerceIn(0f, 1f))
        }
    }

    companion object {
        const val MAX_PARTICLES = 50
        const val BURST_COUNT   = 12
        const val LIFETIME_MS   = 500f
    }
}
