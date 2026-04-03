package com.pianotiles.rendering

import android.opengl.GLES32
import android.opengl.GLSurfaceView
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ArrayBlockingQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Batched OpenGL ES 3.2 renderer.
 *
 * All draw calls in a frame are accumulated into a single pre-allocated
 * vertex buffer and uploaded in one glBufferData + one glDrawArrays call.
 * This reduces GPU driver overhead from ~150 draw calls/frame to 1.
 *
 * Vertex format: x, y, r, g, b, a  (6 floats per vertex)
 * Each rect:     6 vertices (2 triangles), 36 floats total
 */
class GameRenderer : GLSurfaceView.Renderer {

    // ── Thread-safe command queue ──────────────────────────────────────────
    val renderCommands = ArrayBlockingQueue<RenderCommand>(2000)

    // ── Layout & state ────────────────────────────────────────────────────
    var screenWidth  = 1;  private set
    var screenHeight = 1;  private set
    private lateinit var keyLayout: KeyLayout
    val currentKeyLayout: KeyLayout? get() = if (::keyLayout.isInitialized) keyLayout else null

    /** Called on the main thread once the surface dimensions are known. */
    var onSurfaceReady: (() -> Unit)? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val tilePool    = TilePool()
    /** note → ARGB color — stores which keys are held and in what color */
    private val pressedKeys = mutableMapOf<Int, Int>()
    private var songProgress = 0f
    private var beatLineYs: List<Float> = emptyList()   // weak beat lines
    private var barLineYs : List<Float> = emptyList()   // bar (measure) lines — thicker

    // ── OpenGL resources ──────────────────────────────────────────────────
    private lateinit var shader: ShaderProgram
    private var vbo     = 0
    private var posLoc  = 0
    private var colLoc  = 0
    private val STRIDE  = 6 * 4   // 6 floats × 4 bytes

    // ── Batched vertex buffer ─────────────────────────────────────────────
    // Max rects per frame: 52×5 white + 36×4 black + 200 tiles + 50 particles + 30 grid + 4 misc = ~688
    private val MAX_RECTS   = 700
    private val FLOATS_PER_RECT = 36   // 6 vertices × 6 floats (x,y,r,g,b,a)
    private val batchArray  = FloatArray(MAX_RECTS * FLOATS_PER_RECT)
    private var batchCount  = 0        // number of rects accumulated this frame
    private val batchBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(MAX_RECTS * FLOATS_PER_RECT * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    // ── FPS ───────────────────────────────────────────────────────────────
    private var frameCount  = 0
    private var lastFpsNs   = System.nanoTime()
    private var lastFrameNs = System.nanoTime()

    // ── Particles ─────────────────────────────────────────────────────────
    private val particles = ParticleSystem()

    // ─────────────────────────────────────────────────────────────────────
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES32.glClearColor(0.04f, 0.04f, 0.10f, 1f)
        GLES32.glEnable(GLES32.GL_BLEND)
        GLES32.glBlendFunc(GLES32.GL_SRC_ALPHA, GLES32.GL_ONE_MINUS_SRC_ALPHA)

        // Per-vertex color shader — no uniform color, no matrix needed
        val vertSrc = """
            #version 300 es
            in vec2 aPosition;
            in vec4 aColor;
            out vec4 vColor;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                vColor = aColor;
            }
        """.trimIndent()
        val fragSrc = """
            #version 300 es
            precision mediump float;
            in vec4 vColor;
            out vec4 fragColor;
            void main() { fragColor = vColor; }
        """.trimIndent()

        shader = ShaderProgram(vertSrc, fragSrc)
        posLoc = shader.attrib("aPosition")
        colLoc = shader.attrib("aColor")

        val buf = IntArray(1)
        GLES32.glGenBuffers(1, buf, 0)
        vbo = buf[0]

        Log.d("RENDER", "onSurfaceCreated — batched renderer ready")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES32.glViewport(0, 0, width, height)
        screenWidth  = width
        screenHeight = height
        keyLayout    = KeyLayout(width, height)
        Log.d("RENDER", "Surface ${width}×${height}, keyboardTop=${keyLayout.keyboardTopPx}, strikeZone=${keyLayout.strikeZonePx}")
        mainHandler.post { onSurfaceReady?.invoke() }
    }

    override fun onDrawFrame(gl: GL10?) {
        drainCommands()
        GLES32.glClear(GLES32.GL_COLOR_BUFFER_BIT)
        if (!::shader.isInitialized || !::keyLayout.isInitialized) return

        // Build entire scene into batchArray
        batchCount = 0
        buildScene()

        // One upload + one draw call for the whole frame
        val floatCount = batchCount * FLOATS_PER_RECT
        batchBuffer.position(0)
        batchBuffer.put(batchArray, 0, floatCount)
        batchBuffer.position(0)

        shader.use()
        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, vbo)
        GLES32.glBufferData(GLES32.GL_ARRAY_BUFFER, floatCount * 4, batchBuffer, GLES32.GL_DYNAMIC_DRAW)

        // aPosition: offset 0, stride 24 bytes
        GLES32.glEnableVertexAttribArray(posLoc)
        GLES32.glVertexAttribPointer(posLoc, 2, GLES32.GL_FLOAT, false, STRIDE, 0)
        // aColor: offset 8 bytes (after x,y), stride 24 bytes
        GLES32.glEnableVertexAttribArray(colLoc)
        GLES32.glVertexAttribPointer(colLoc, 4, GLES32.GL_FLOAT, false, STRIDE, 8)

        GLES32.glDrawArrays(GLES32.GL_TRIANGLES, 0, batchCount * 6)

        GLES32.glDisableVertexAttribArray(posLoc)
        GLES32.glDisableVertexAttribArray(colLoc)

        val deltaMs = updateFps()
        animateTiles(deltaMs)
        particles.update(deltaMs)
    }

    // ─────────────────────────────────────────────────────────────────────
    // Scene building — accumulate all rects for this frame
    // ─────────────────────────────────────────────────────────────────────

    private fun buildScene() {
        addProgressBar()
        addBeatGrid()
        addTiles()
        addHitZone()
        addWhiteKeys()
        addBlackKeys()
        addParticles()
    }

    private fun addBeatGrid() {
        if (!::keyLayout.isInitialized) return
        val w = screenWidth.toFloat()
        // Weak beat lines — very faint
        for (y in beatLineYs)
            addRect(0f, y - 1f, w, y + 1f, 0x22FFFFFF)
        // Bar (measure) lines — slightly more visible
        for (y in barLineYs)
            addRect(0f, y - 1f, w, y + 2f, 0x55FFFFFF)
    }

    private fun addProgressBar() {
        if (songProgress <= 0f) return
        addRect(0f, 0f, screenWidth * songProgress, 6f, 0xFF4FC3F7.toInt())
    }

    private fun addTiles() {
        tilePool.forEachActive { tile ->
            val left = keyLayout.keyLeftPx(tile.midiNote)
            val w    = keyLayout.keyWidthPx(tile.midiNote) - GAP_PX
            val top  = tile.yPx
            val bot  = tile.yPx + tile.heightPx

            when (tile.hitState) {
                TilePool.HitState.NORMAL -> {
                    // Synthesia gradient: transparent at top → full color at bottom
                    val topColor = (tile.color and 0x00FFFFFF) or (0x88 shl 24)  // ~53% alpha
                    addGradientRect(left, top, left + w, bot, topColor, tile.color)
                }
                TilePool.HitState.HIT, TilePool.HitState.MISSED -> {
                    val t     = (tile.hitAnimMs / HIT_ANIM_MS).coerceIn(0f, 1f)
                    val alpha = (255 * (1f - t)).toInt().coerceIn(0, 255)
                    val base  = if (tile.hitState == TilePool.HitState.HIT)
                                    blendToWhite(tile.color, t)
                                else
                                    blendToRed(tile.color, t)
                    val color = (base and 0x00FFFFFF) or (alpha shl 24)
                    addRect(left, top, left + w, bot, color)
                }
            }
        }
    }

    private fun addHitZone() {
        val y = keyLayout.strikeZonePx.toFloat()
        // Synthesia-like glowing strike line: three overlapping layers
        addRect(0f, y - 10f, screenWidth.toFloat(), y + 10f, 0x18FFFFFF)   // wide outer glow
        addRect(0f, y -  4f, screenWidth.toFloat(), y +  4f, 0x55FFFFFF)   // mid glow
        addRect(0f, y -  1f, screenWidth.toFloat(), y +  1f, 0xEEFFFFFF.toInt())  // bright center
    }

    private fun addWhiteKeys() {
        for (note in KeyLayout.MIDI_MIN..KeyLayout.MIDI_MAX) {
            if (!KeyLayout.isWhiteKey(note)) continue
            val left   = keyLayout.keyLeftPx(note)
            val top    = keyLayout.keyboardTopPx.toFloat()
            val right  = left + keyLayout.whiteKeyWidthPx
            val bottom = screenHeight.toFloat()
            val pressColor = pressedKeys[note]
            // 1. Left divider border (always same colour)
            addRect(left, top, left + 1f, bottom, 0xFF777777.toInt())
            if (pressColor == null) {
                // 2. Face — slightly brighter base than old flat grey
                addRect(left + 1f, top,         right - 2f, bottom,   0xFFF0F0F0.toInt())
                // 3. Top highlight — bright white strip, light source from above
                addRect(left + 1f, top,         right - 2f, top + 2f, 0xFFFFFFFF.toInt())
                // 4. Right shadow — depth cue, light comes from left
                addRect(right - 2f, top,        right,      bottom,   0xFFBBBBBB.toInt())
                // 5. Bottom accent — grounding shadow at key foot
                addRect(left + 1f, bottom - 2f, right - 2f, bottom,   0xFF999999.toInt())
            } else {
                // Pressed — key body shifts down 3px; dark gap at top simulates physical depression
                addRect(left + 1f, top,         right - 2f, top + 3f, 0xFF222222.toInt()) // shadow gap
                addRect(left + 1f, top + 3f,    right - 2f, bottom,   pressColor)          // face (shifted)
                addRect(right - 2f, top + 3f,   right,      bottom,   darken(pressColor, 0.80f)) // right shadow
                addRect(left + 1f, bottom - 2f, right - 2f, bottom,   darken(pressColor, 0.75f)) // bottom
            }
        }
    }

    private fun addBlackKeys() {
        for (note in KeyLayout.MIDI_MIN..KeyLayout.MIDI_MAX) {
            if (KeyLayout.isWhiteKey(note)) continue
            val left  = keyLayout.keyLeftPx(note)
            val top   = keyLayout.keyboardTopPx.toFloat()
            val right = left + keyLayout.blackKeyWidthPx
            val bot   = top  + keyLayout.blackKeyHeightPx
            val pressColor = pressedKeys[note]
            if (pressColor == null) {
                addRect(left,        top, right,       bot, 0xFF1A1A1A.toInt())  // face
                addRect(left,        top, left + 2f,   bot, 0xFF2D2D2D.toInt())  // left bevel (lighter)
                addRect(right - 2f,  top, right,       bot, 0xFF0D0D0D.toInt())  // right bevel (darker)
                addRect(left + 2f,   top, right - 2f,  top + 2f, 0xFF272727.toInt()) // top highlight
            } else {
                // Pressed — body shifts down 2px; dark gap at top = physically depressed
                addRect(left, top,       right, top + 2f,  0xFF111111.toInt())           // shadow gap
                addRect(left, top + 2f,  right, bot,       darken(pressColor, 0.75f))    // face (shifted)
                addRect(left, bot - 2f,  right, bot,       0xFF0A0A0A.toInt())           // bottom shadow
            }
        }
    }

    private fun addParticles() {
        particles.forEachActive { px, py, color, alpha ->
            val a = (alpha * 255).toInt().coerceIn(0, 255)
            addRect(px - 3f, py - 3f, px + 3f, py + 3f, (color and 0x00FFFFFF) or (a shl 24))
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Core batch primitive — writes 36 floats (6 verts × 6 floats) per rect
    // ─────────────────────────────────────────────────────────────────────

    private fun addRect(left: Float, top: Float, right: Float, bottom: Float, argb: Int) {
        if (batchCount >= MAX_RECTS) return

        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr  8) and 0xFF) / 255f
        val b = ((argb       ) and 0xFF) / 255f
        val a = ((argb shr 24) and 0xFF) / 255f

        // Convert pixel coords to NDC
        val x0 = CoordSystem.pxToNdcX(left,  screenWidth)
        val x1 = CoordSystem.pxToNdcX(right, screenWidth)
        val y0 = CoordSystem.pxToNdcY(top,   screenHeight)  // top in NDC = larger y
        val y1 = CoordSystem.pxToNdcY(bottom, screenHeight) // bottom in NDC = smaller y

        val i = batchCount * FLOATS_PER_RECT
        // Triangle 1: TL, TR, BL
        batchArray[i+0]  = x0; batchArray[i+1]  = y0; batchArray[i+2]  = r; batchArray[i+3]  = g; batchArray[i+4]  = b; batchArray[i+5]  = a
        batchArray[i+6]  = x1; batchArray[i+7]  = y0; batchArray[i+8]  = r; batchArray[i+9]  = g; batchArray[i+10] = b; batchArray[i+11] = a
        batchArray[i+12] = x0; batchArray[i+13] = y1; batchArray[i+14] = r; batchArray[i+15] = g; batchArray[i+16] = b; batchArray[i+17] = a
        // Triangle 2: TR, BR, BL
        batchArray[i+18] = x1; batchArray[i+19] = y0; batchArray[i+20] = r; batchArray[i+21] = g; batchArray[i+22] = b; batchArray[i+23] = a
        batchArray[i+24] = x1; batchArray[i+25] = y1; batchArray[i+26] = r; batchArray[i+27] = g; batchArray[i+28] = b; batchArray[i+29] = a
        batchArray[i+30] = x0; batchArray[i+31] = y1; batchArray[i+32] = r; batchArray[i+33] = g; batchArray[i+34] = b; batchArray[i+35] = a

        batchCount++
    }

    // ─────────────────────────────────────────────────────────────────────
    // Command queue
    // ─────────────────────────────────────────────────────────────────────

    private fun drainCommands() {
        var cmd = renderCommands.poll()
        while (cmd != null) {
            when (cmd) {
                is RenderCommand.SpawnTile -> {
                    // Always acquire a fresh slot — never reuse by note.
                    // Tiles are now uniquely identified by eventIdx.
                    val tile = tilePool.acquire() ?: break
                    tile.eventIdx  = cmd.eventIdx
                    tile.midiNote  = cmd.note
                    tile.yPx       = cmd.yPx
                    tile.heightPx  = cmd.heightPx
                    tile.color     = cmd.color
                }
                is RenderCommand.UpdateTileY ->
                    tilePool.findByEventIdx(cmd.eventIdx)?.yPx = cmd.yPx
                is RenderCommand.HitTile ->
                    tilePool.findByEventIdx(cmd.eventIdx)?.let {
                        it.hitState  = TilePool.HitState.HIT
                        it.hitAnimMs = 0f
                        if (::keyLayout.isInitialized)
                            particles.emit(keyLayout.keyCenterXPx(cmd.note).toInt(),
                                           keyLayout.strikeZonePx, it.color)
                    }
                is RenderCommand.MissTile ->
                    tilePool.findByEventIdx(cmd.eventIdx)?.let {
                        it.hitState  = TilePool.HitState.MISSED
                        it.hitAnimMs = 0f
                    }
                is RenderCommand.ReleaseTile ->
                    tilePool.findByEventIdx(cmd.eventIdx)?.let { tilePool.release(it) }
                is RenderCommand.PressKey       -> pressedKeys[cmd.note] = cmd.color
                is RenderCommand.ReleaseKey     -> pressedKeys.remove(cmd.note)
                is RenderCommand.UpdateProgress -> songProgress = cmd.progress
                is RenderCommand.ClearTiles     -> tilePool.forEachActive { tilePool.release(it) }
                is RenderCommand.UpdateBeatGrid -> { beatLineYs = cmd.beatYs; barLineYs = cmd.barYs }
            }
            cmd = renderCommands.poll()
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private fun animateTiles(deltaMs: Float) {
        tilePool.forEachActive { tile ->
            if (tile.hitState != TilePool.HitState.NORMAL) {
                tile.hitAnimMs += deltaMs
                if (tile.hitAnimMs >= HIT_ANIM_MS) tilePool.release(tile)
            }
        }
    }

    private fun updateFps(): Float {
        val now   = System.nanoTime()
        val delta = now - lastFrameNs
        lastFrameNs = now
        frameCount++
        if (frameCount >= 120) {
            val fps = 120_000_000_000L / (now - lastFpsNs)
            Log.d("RENDER", "FPS: $fps  rects: $batchCount  tiles: ${tilePool.activeCount()}")
            lastFpsNs  = now
            frameCount = 0
        }
        return delta / 1_000_000f
    }

    /**
     * Draws a rect with a vertical color gradient.
     * [topArgb] is the color at the top edge, [bottomArgb] at the bottom edge.
     * The GPU interpolates between them across the rect.
     */
    private fun addGradientRect(left: Float, top: Float, right: Float, bottom: Float,
                                topArgb: Int, bottomArgb: Int) {
        if (batchCount >= MAX_RECTS) return

        fun components(argb: Int) = floatArrayOf(
            ((argb shr 16) and 0xFF) / 255f,
            ((argb shr  8) and 0xFF) / 255f,
            ((argb       ) and 0xFF) / 255f,
            ((argb shr 24) and 0xFF) / 255f
        )
        val (tr, tg, tb, ta) = components(topArgb)
        val (br, bg, bb, ba) = components(bottomArgb)

        val x0 = CoordSystem.pxToNdcX(left,   screenWidth)
        val x1 = CoordSystem.pxToNdcX(right,  screenWidth)
        val y0 = CoordSystem.pxToNdcY(top,    screenHeight)  // top in NDC = larger y
        val y1 = CoordSystem.pxToNdcY(bottom, screenHeight)  // bottom in NDC = smaller y

        val i = batchCount * FLOATS_PER_RECT
        // Triangle 1: TL, TR, BL
        batchArray[i+0]  = x0; batchArray[i+1]  = y0; batchArray[i+2]  = tr; batchArray[i+3]  = tg; batchArray[i+4]  = tb; batchArray[i+5]  = ta
        batchArray[i+6]  = x1; batchArray[i+7]  = y0; batchArray[i+8]  = tr; batchArray[i+9]  = tg; batchArray[i+10] = tb; batchArray[i+11] = ta
        batchArray[i+12] = x0; batchArray[i+13] = y1; batchArray[i+14] = br; batchArray[i+15] = bg; batchArray[i+16] = bb; batchArray[i+17] = ba
        // Triangle 2: TR, BR, BL
        batchArray[i+18] = x1; batchArray[i+19] = y0; batchArray[i+20] = tr; batchArray[i+21] = tg; batchArray[i+22] = tb; batchArray[i+23] = ta
        batchArray[i+24] = x1; batchArray[i+25] = y1; batchArray[i+26] = br; batchArray[i+27] = bg; batchArray[i+28] = bb; batchArray[i+29] = ba
        batchArray[i+30] = x0; batchArray[i+31] = y1; batchArray[i+32] = br; batchArray[i+33] = bg; batchArray[i+34] = bb; batchArray[i+35] = ba

        batchCount++
    }

    private fun darken(argb: Int, factor: Float): Int {
        val r = ((argb shr 16) and 0xFF)
        val g = ((argb shr  8) and 0xFF)
        val b = ((argb       ) and 0xFF)
        val a = ((argb shr 24) and 0xFF)
        return (a shl 24) or
               ((r * factor).toInt().coerceIn(0, 255) shl 16) or
               ((g * factor).toInt().coerceIn(0, 255) shl  8) or
               ((b * factor).toInt().coerceIn(0, 255))
    }

    private fun blendToWhite(color: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        fun lerp(a: Int, b: Int) = (a + (b - a) * f).toInt().coerceIn(0, 255)
        return 0xFF000000.toInt() or (lerp((color shr 16) and 0xFF, 255) shl 16) or
               (lerp((color shr 8) and 0xFF, 255) shl 8) or lerp(color and 0xFF, 255)
    }

    private fun blendToRed(color: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        fun lerp(a: Int, b: Int) = (a + (b - a) * f).toInt().coerceIn(0, 255)
        return 0xFF000000.toInt() or (lerp((color shr 16) and 0xFF, 220) shl 16) or
               (lerp((color shr 8) and 0xFF, 40) shl 8) or lerp(color and 0xFF, 40)
    }

    companion object {
        const val HIT_ANIM_MS = 200f
        const val GAP_PX      = 2f
    }
}
