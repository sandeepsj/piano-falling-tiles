# 02 — Rendering Layer

## Purpose

Draw everything on screen at 120/144fps using a dedicated OpenGL ES 3.2 render thread.
Responsibilities: piano keyboard (88 keys), falling tile rectangles, hit zone indicator,
tile hit/miss animations, particle burst effects, FPS counter (debug).

Does NOT: know about MIDI files, game logic, scoring, or audio. Receives only
pre-computed draw data (tile positions, key highlight states) from the Game Engine.

---

## Dependencies

- Layer 1 (project setup) — folder structure only.
- No other layers. All input data is mocked during standalone testing.

---

## Tasks

### 2.1 Create `GameSurfaceView.kt` — GLSurfaceView subclass
- **What**:
  ```kotlin
  class GameSurfaceView(context: Context) : GLSurfaceView(context) {
      private val renderer = GameRenderer()
      init {
          setEGLContextClientVersion(3)   // OpenGL ES 3.2
          setRenderer(renderer)
          renderMode = RENDERMODE_CONTINUOUSLY
      }
  }
  ```
- **How to verify**: No compile errors.
- **Done when**: File compiles cleanly.

### 2.2 Create `GameRenderer.kt` — clears screen to black
- **What**: Implement `GLSurfaceView.Renderer`:
  ```kotlin
  class GameRenderer : GLSurfaceView.Renderer {
      override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
          GLES32.glClearColor(0.05f, 0.05f, 0.1f, 1.0f)  // near-black
      }
      override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
          GLES32.glViewport(0, 0, width, height)
      }
      override fun onDrawFrame(gl: GL10?) {
          GLES32.glClear(GLES32.GL_COLOR_BUFFER_BIT)
      }
  }
  ```
- **How to verify**: Screen shows solid dark blue-black color.
- **Done when**: App runs, screen is dark, no OpenGL errors in logcat.

### 2.3 Add `GameSurfaceView` to a test Activity
- **What**: Create `RenderingTestActivity.kt` that sets content view to `GameSurfaceView`:
  ```kotlin
  class RenderingTestActivity : AppCompatActivity() {
      override fun onCreate(savedInstanceState: Bundle?) {
          super.onCreate(savedInstanceState)
          setContentView(GameSurfaceView(this))
      }
  }
  ```
  Register in `AndroidManifest.xml`.
- **How to verify**: Launch activity, see dark screen.
- **Done when**: Dark screen visible on tablet with no GL errors.

### 2.4 Add FPS counter (debug only)
- **What**: Track frame time with `System.nanoTime()` in `onDrawFrame`. Every 60 frames, log average FPS:
  ```kotlin
  private var frameCount = 0
  private var lastFpsTime = System.nanoTime()

  // In onDrawFrame:
  frameCount++
  if (frameCount >= 60) {
      val now = System.nanoTime()
      val fps = 60_000_000_000L / (now - lastFpsTime)
      Log.d("RENDER", "FPS: $fps")
      lastFpsTime = now
      frameCount = 0
  }
  ```
- **How to verify**: `adb logcat | grep RENDER` shows FPS values.
- **Done when**: Logcat shows FPS ≥ 120 on the OnePlus tablet.

### 2.5 Create coordinate system helper
- **What**: Create `CoordSystem.kt` with pure functions (no GL calls):
  ```kotlin
  object CoordSystem {
      // Screen pixels → OpenGL NDC (-1 to +1)
      fun pxToNdcX(px: Float, screenWidth: Int): Float = (px / screenWidth) * 2f - 1f
      fun pxToNdcY(px: Float, screenHeight: Int): Float = 1f - (px / screenHeight) * 2f

      // Rect in screen pixels → 4 NDC vertices (triangle strip order)
      fun rectToVertices(
          left: Float, top: Float, right: Float, bottom: Float,
          screenWidth: Int, screenHeight: Int
      ): FloatArray { ... }
  }
  ```
- **How to verify**: Unit test: `pxToNdcX(0, 1920)` == -1f, `pxToNdcX(1920, 1920)` == 1f.
- **Done when**: Unit tests pass on device (`./gradlew :app:testDebugUnitTest`).

### 2.6 Create vertex + fragment shader for solid colored rectangles
- **What**: Create `res/raw/tile_vert.glsl` and `res/raw/tile_frag.glsl`:
  ```glsl
  // tile_vert.glsl
  #version 300 es
  in vec2 aPosition;
  uniform mat4 uMVP;
  void main() { gl_Position = uMVP * vec4(aPosition, 0.0, 1.0); }

  // tile_frag.glsl
  #version 300 es
  precision mediump float;
  uniform vec4 uColor;
  out vec4 fragColor;
  void main() { fragColor = uColor; }
  ```
- **How to verify**: Shader compiles at runtime (log GL shader compile status).
- **Done when**: No `GL_COMPILE_STATUS` errors in logcat.

### 2.7 Create `ShaderProgram.kt` — compile and link shaders
- **What**: Utility class:
  ```kotlin
  class ShaderProgram(vertSrc: String, fragSrc: String) {
      val programId: Int = ...  // compile, link, check status
      fun use() { GLES32.glUseProgram(programId) }
      fun getAttribLocation(name: String): Int
      fun getUniformLocation(name: String): Int
  }
  ```
  Log any compile/link errors with `GLES32.glGetShaderInfoLog`.
- **How to verify**: Log prints "Shader program linked OK".
- **Done when**: Program links without error in logcat.

### 2.8 Draw a single colored rectangle
- **What**: In `onDrawFrame`, after clearing, draw one hardcoded rectangle (e.g., red, center of screen, 100×200px) using a VBO:
  ```kotlin
  // vertices: 4 corners in NDC
  // draw with glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
  ```
- **How to verify**: A red rectangle appears in the center of the screen.
- **Done when**: Rectangle visible, no GL errors.

### 2.9 Compute piano key layout
- **What**: Create `KeyLayout.kt` — pure Kotlin, no GL:
  ```kotlin
  class KeyLayout(screenWidth: Int, screenHeight: Int) {
      val keyboardHeightPx: Int = (screenHeight * 0.20).toInt()  // 20% of screen
      val keyboardYPx: Int = screenHeight - keyboardHeightPx

      // Returns left-edge x position in pixels for each MIDI note 21–108
      fun keyXPx(midiNote: Int): Float { ... }
      fun keyWidthPx(midiNote: Int): Float { ... }   // white: wider, black: narrower
      fun isBlackKey(midiNote: Int): Boolean { ... }
  }
  ```
  Standard piano layout: 52 white keys, 36 black keys in octave pattern (C D E F G A B).
- **How to verify**: Unit test: 88 keys fit within `screenWidth` exactly.
- **Done when**: Unit test passes. No pixel overflow.

### 2.10 Draw the piano keyboard (white keys)
- **What**: In `onDrawFrame`, iterate MIDI notes 21–108, draw white key rectangles using `KeyLayout`. White keys: light gray (`0.9, 0.9, 0.9`). Add 1px black border between keys.
- **How to verify**: 52 white keys fill the bottom 20% of the screen.
- **Done when**: On tablet: keyboard visible, all white keys same height, evenly spaced.

### 2.11 Draw black keys on top of white keys
- **What**: Draw black key rectangles over the white keys (draw order: white first, black on top). Black keys: dark gray (`0.1, 0.1, 0.1`), 60% height of white keys, 65% width.
- **How to verify**: Full 88-key piano keyboard visible with correct black/white key pattern.
- **Done when**: Pattern matches a real piano (C D E F G A B repeating). Visually verified.

### 2.12 Create `TilePool.kt` — pre-allocated tile draw data
- **What**: Pre-allocate 200 tile slots. Each slot holds: `midiNote`, `yPx`, `heightPx`, `color`, `active` flag. No GL objects here — just data:
  ```kotlin
  class TilePool {
      val tiles = Array(200) { TileData() }
      fun acquire(): TileData?   // returns first inactive slot
      fun release(tile: TileData)
  }
  data class TileData(
      var midiNote: Int = 0,
      var yPx: Float = 0f,
      var heightPx: Float = 0f,
      var color: Int = 0,        // ARGB packed int
      var active: Boolean = false
  )
  ```
- **How to verify**: Unit test: acquire 200 tiles, verify 200th succeeds. Acquire 201st, verify returns null.
- **Done when**: Unit tests pass.

### 2.13 Draw falling tiles from mock data
- **What**: In `GameRenderer`, create a `TilePool` and populate it with mock data (C major scale, staggered start positions). In `onDrawFrame`, iterate active tiles, draw each using `KeyLayout.keyXPx(tile.midiNote)` for x position, `tile.yPx` for top, `tile.heightPx` for height.
- **How to verify**: 8 colored rectangles appear above the piano keyboard.
- **Done when**: Tiles visible, each aligned with its correct piano key column.

### 2.14 Animate tiles falling (mock clock)
- **What**: In `onDrawFrame`, for each active tile: `tile.yPx += pixelsPerFrame` where `pixelsPerFrame = (PIXELS_PER_SECOND / displayRefreshRate)`. Use a constant `PIXELS_PER_SECOND = 400`. When tile's `yPx > keyboardYPx`, deactivate and re-spawn at top.
- **How to verify**: Tiles fall smoothly from top to bottom in a continuous loop.
- **Done when**: Motion is smooth, no jitter, tiles stay aligned to their piano key columns.

### 2.15 Draw the hit zone line
- **What**: Draw a horizontal bright line (width: full screen, height: 4px) at `keyboardYPx - 8px`. Color: white with 80% alpha.
- **How to verify**: Bright horizontal line visible just above the piano keyboard.
- **Done when**: Line visible and correctly positioned.

### 2.16 Highlight a piano key (press state)
- **What**: Add `pressedKeys: Set<Int>` to `GameRenderer` (updated from outside). In `onDrawFrame`, if a key's MIDI note is in `pressedKeys`, draw it with a highlight color (e.g., blue for white keys, cyan for black keys).
- **How to verify**: Manually set `pressedKeys = setOf(60)` (C4) — C4 key glows blue.
- **Done when**: Key highlight is visible and correctly positioned.

### 2.17 Tile hit animation — flash + fade
- **What**: Add `hitState` field to `TileData` (enum: NORMAL, HIT, MISSED). When `hitState == HIT`: tile color transitions from its normal color → white → transparent over 200ms. When `MISSED`: tile color → red → transparent.
- **How to verify**: Mock: set one tile's `hitState = HIT`. Tile flashes white then disappears.
- **Done when**: Hit animation plays in ≤ 200ms, smooth fade visible.

### 2.18 Particle burst effect
- **What**: Create `ParticleSystem.kt` — maintains up to 50 particle structs (x, y, vx, vy, life, color). Method `emit(x, y, color)` spawns 12 particles at position with random velocities. `update(deltaMs)` moves particles and decreases life. In `onDrawFrame`, draw particles as small squares (6×6px).
- **How to verify**: Mock: call `emit(960, 800, Color.GREEN)`. Small green squares scatter from that point and fade.
- **Done when**: Particles are visible, move outward, and disappear within 500ms.

### 2.19 Measure rendering performance with Android GPU Inspector
- **What**: Connect tablet, open Android GPU Inspector, start a capture while the mock animation is running.
- **How to verify**: GPU Inspector shows frame time and GPU load.
- **Done when**: Frame time ≤ 8.3ms at 120Hz (= 120fps), GPU utilization < 40%.

### 2.20 Thread safety — expose a render command queue
- **What**: `GameRenderer` exposes a `renderCommands: ArrayBlockingQueue<RenderCommand>` where other threads can post tile updates, key presses, etc. In `onDrawFrame`, drain the queue and apply updates before drawing. This is the only way other layers write to renderer state.
  ```kotlin
  sealed class RenderCommand {
      data class SpawnTile(val note: Int, val yPx: Float, val heightPx: Float, val color: Int) : RenderCommand()
      data class PressKey(val note: Int) : RenderCommand()
      data class ReleaseKey(val note: Int) : RenderCommand()
      data class HitTile(val note: Int) : RenderCommand()
      data class MissTile(val note: Int) : RenderCommand()
  }
  ```
- **How to verify**: Post commands from a background thread, verify renderer applies them without crash.
- **Done when**: No `ConcurrentModificationException`, no dropped frames when commands are posted at 120Hz.

### 2.21 Round corners on tiles (visual polish)
- **What**: Update tile fragment shader to clip pixels outside a rounded rectangle:
  ```glsl
  // Compute distance from rounded rect edge in fragment shader
  // Discard pixels outside corner radius (4px)
  ```
- **How to verify**: Tiles have visibly rounded corners.
- **Done when**: Corners are rounded, no GL errors.

### 2.22 Display song progress bar (thin line at top)
- **What**: Draw a horizontal progress bar at the very top of the screen (full width, 6px tall). Width is proportional to playback progress (0.0–1.0). Accept `progress: Float` via `RenderCommand.UpdateProgress`.
- **How to verify**: Mock: set progress 0.0 → 1.0 over time. Bar grows from left to right.
- **Done when**: Progress bar visible and animates correctly.

---

## Standalone Test

**Activity**: `RenderingTestActivity`

**Steps**:
1. Launch `RenderingTestActivity` on the OnePlus tablet.
2. Verify: dark background, full 88-key piano keyboard at the bottom.
3. Verify: colored tiles falling continuously above the keyboard.
4. Verify: hit zone line visible.
5. Tap anywhere on screen → post `PressKey(60)` command → verify C4 key highlights.
6. `adb logcat | grep RENDER` → verify FPS ≥ 120.
7. Run Android GPU Inspector capture → verify frame time ≤ 8.3ms.

**Expected result**: Smooth 120fps animation, piano keyboard correctly rendered,
all tiles fall to their correct column, no GL errors in logcat.

---

## Performance Target

| Metric | Target |
|---|---|
| Frame rate | ≥ 120fps sustained |
| Frame time (p99) | ≤ 8.3ms |
| GPU utilization | < 40% with 100 active tiles |
| Tile spawn + draw | 200 tiles rendered with no frame drop |

These must be verified with Android GPU Inspector before Layer 6 integration.

---

## Integration Points

`GameRenderer` exposes:
```kotlin
val renderCommands: ArrayBlockingQueue<RenderCommand>  // thread-safe command queue
fun setScreenSize(width: Int, height: Int)             // called on surface change
```

`GameSurfaceView` exposes:
```kotlin
val renderer: GameRenderer
```

`KeyLayout` exposes:
```kotlin
fun keyXPx(midiNote: Int): Float
fun keyWidthPx(midiNote: Int): Float
fun keyboardYPx: Int
```

No other layer calls GL functions directly. All rendering goes through `RenderCommand`.
