package com.pianotiles.rendering

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager

/**
 * Standalone test for Layer 2.
 * Shows: piano keyboard + mock C-major scale tiles falling continuously.
 * No audio, no MIDI, no game engine required.
 *
 * Verification checklist:
 *   [ ] Dark background
 *   [ ] Full 88-key piano keyboard at bottom (white + black keys)
 *   [ ] White hit-zone line above keyboard
 *   [ ] Colored tiles falling, each aligned to correct piano key
 *   [ ] Tiles loop back to top when they reach the bottom
 *   [ ] FPS ≥ 120 in logcat (tag: RENDER)
 *   [ ] Key highlights: post PressKey command and verify key lights up
 */
class RenderingTestActivity : Activity() {

    private lateinit var surfaceView: GameSurfaceView
    private val handler = Handler(Looper.getMainLooper())

    // C major scale across 3 octaves for mock data
    private val testNotes = listOf(
        48, 50, 52, 53, 55, 57, 59,   // C3–B3
        60, 62, 64, 65, 67, 69, 71,   // C4–B4
        72, 74, 76, 77, 79, 81, 83    // C5–B5
    )

    // Mock tile state: note → yPx (falling position)
    private val tileY = mutableMapOf<Int, Float>()
    private val tileH = 80f   // tile height in px
    private var screenH = 1080f
    private var pixelsPerMs = 0.3f   // ~300px/sec at 1080p

    // Track colors (right hand = blue, left hand = green)
    private val trackColors = listOf(0xFF4FC3F7.toInt(), 0xFF81C784.toInt(), 0xFFFFB74D.toInt())
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Request maximum refresh rate — tablet defaults to 60Hz unless asked
        requestMaxRefreshRate()

        surfaceView = GameSurfaceView(this)
        setContentView(surfaceView)

        // Tap anywhere to test key highlight
        surfaceView.setOnClickListener {
            val note = 60  // C4
            surfaceView.renderer.renderCommands.offer(RenderCommand.PressKey(note))
            handler.postDelayed({
                surfaceView.renderer.renderCommands.offer(RenderCommand.ReleaseKey(note))
            }, 300)
        }

        // Wait for surface to be ready, then start mock animation
        handler.postDelayed({ startMockAnimation() }, 500)
    }

    private fun startMockAnimation() {
        // Stagger tiles across the screen at start
        testNotes.forEachIndexed { i, note ->
            tileY[note] = -tileH - i * 120f   // staggered above screen
        }
        running = true
        scheduleTick()
        Log.d("RENDER_TEST", "Mock animation started with ${testNotes.size} tiles")
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            tick()
            handler.postDelayed(this, 16)  // ~60fps tick for command posting
        }
    }

    private fun scheduleTick() = handler.post(tickRunnable)

    private fun tick() {
        val renderer = surfaceView.renderer
        val sh = surfaceView.height.takeIf { it > 0 }?.toFloat() ?: return
        screenH = sh
        // Adjust speed to screen height
        pixelsPerMs = sh * 0.3f / 1000f  // traverse 30% of screen per second

        testNotes.forEachIndexed { i, note ->
            val y = tileY[note] ?: return@forEachIndexed
            val newY = y + pixelsPerMs * 16f
            tileY[note] = if (newY > screenH) -tileH else newY

            val color = trackColors[i % trackColors.size]
            if (newY > screenH) {
                // Respawn at top — use note as a synthetic eventIdx for test purposes
                renderer.renderCommands.offer(
                    RenderCommand.SpawnTile(note, note, -tileH, tileH, color)
                )
            } else {
                renderer.renderCommands.offer(
                    RenderCommand.UpdateTileY(note, newY)
                )
                // Initial spawn
                if (y <= -tileH + 1f) {
                    renderer.renderCommands.offer(
                        RenderCommand.SpawnTile(note, note, newY, tileH, color)
                    )
                }
            }
        }

        // Slowly advance progress bar
        val progress = ((System.currentTimeMillis() % 30_000) / 30_000f)
        renderer.renderCommands.offer(RenderCommand.UpdateProgress(progress))
    }

    private fun requestMaxRefreshRate() {
        // Pick the highest supported refresh rate mode
        val display = windowManager.defaultDisplay
        val modes = display.supportedModes
        val best = modes.maxByOrNull { it.refreshRate } ?: return
        Log.d("RENDER_TEST", "Display modes: ${modes.map { "${it.refreshRate}Hz" }}")
        Log.d("RENDER_TEST", "Requesting mode id: ${best.modeId} (${best.refreshRate}Hz)")
        val params = window.attributes
        // preferredDisplayModeId locks the exact mode (res + refresh rate), not just a hint
        params.preferredDisplayModeId = best.modeId
        window.attributes = params
        // Also keep screen on during test
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
