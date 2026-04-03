# 06 — Game Engine

## Purpose

The brain of the app. Manages the master playback clock, determines which tiles are
visible and where they are on screen, detects when the player hits or misses notes,
and calculates the score.

Handles: Choreographer-synced clock, play/pause/seek, tile position computation,
hit detection with timing windows, combo system, score calculation, speed multiplier.

Does NOT: render anything, play audio, or parse MIDI files. It only produces data
for the Renderer to draw and tells the Audio layer which notes to play.

---

## Dependencies

- Layer 5 (MIDI File) — `NoteEvent` list and `durationSeconds`.
- No rendering, audio, or input layers. All are injected via interfaces.

---

## Tasks

### 6.1 Create `GameClock.kt` — Choreographer-synced clock
- **What**:
  ```kotlin
  class GameClock {
      private var startTimeNs: Long = 0L
      private var pausedAtNs: Long = 0L
      private var totalPausedNs: Long = 0L
      private var isRunning = false
      private var speedMultiplier = 1.0f

      val currentTimeSeconds: Double
          get() {
              if (!isRunning) return pausedAtNs / 1_000_000_000.0
              val elapsed = System.nanoTime() - startTimeNs - totalPausedNs
              return (elapsed * speedMultiplier) / 1_000_000_000.0
          }

      fun play() { ... }
      fun pause() { ... }
      fun seekTo(seconds: Double) { ... }
      fun setSpeed(multiplier: Float) { speedMultiplier = multiplier }
  }
  ```
- **How to verify**: Unit test: `play()`, sleep 1000ms, `pause()` → `currentTimeSeconds` ≈ 1.0 (within 10ms).
- **Done when**: Unit test passes.

### 6.2 Verify clock accuracy at 120fps
- **What**: In a loop running at 120fps (via `Choreographer.postFrameCallback`), sample
  `currentTimeSeconds` every frame. Verify delta between consecutive frames ≈ 8.33ms.
  Log max deviation over 100 frames.
- **How to verify**: Max deviation ≤ 1ms over 100 frames.
- **Done when**: Test logs "Max frame deviation: Xms" where X ≤ 1.

### 6.3 Implement play / pause / seek
- **What**:
  ```kotlin
  fun play() {
      if (isRunning) return
      startTimeNs = System.nanoTime() - (pausedAtNs - totalPausedNs).coerceAtLeast(0)
      isRunning = true
  }
  fun pause() {
      if (!isRunning) return
      pausedAtNs = ((System.nanoTime() - startTimeNs) * speedMultiplier).toLong()
      isRunning = false
  }
  fun seekTo(seconds: Double) {
      pausedAtNs = (seconds / speedMultiplier * 1_000_000_000.0).toLong()
      if (isRunning) { startTimeNs = System.nanoTime() - pausedAtNs.toLong() }
  }
  ```
- **How to verify**: Unit tests for each: play→pause→resume keeps correct time. seek(30.0) → clock reads 30.0.
- **Done when**: All three unit tests pass.

### 6.4 Create `TileScheduler.kt` — compute visible tiles
- **What**:
  ```kotlin
  class TileScheduler(
      private val events: List<NoteEvent>,
      private val screenHeight: Int,
      private val keyboardHeightPx: Int
  ) {
      val lookaheadSeconds = 3.0          // tiles spawn 3 seconds before their hit time
      val pixelsPerSecond = 300f          // adjustable (scroll speed)

      fun getVisibleTiles(currentTimeSec: Double): List<VisibleTile> {
          val windowStart = currentTimeSec
          val windowEnd = currentTimeSec + lookaheadSeconds

          return events
              .filter { it.startTimeSeconds in windowStart..windowEnd ||
                        it.startTimeSeconds + it.durationSeconds > windowStart }
              .map { event ->
                  val secondsUntilHit = event.startTimeSeconds - currentTimeSec
                  val yPx = strikeZoneY - (secondsUntilHit * pixelsPerSecond)
                  val heightPx = event.durationSeconds * pixelsPerSecond
                  VisibleTile(event, yPx.toFloat(), heightPx.toFloat())
              }
      }

      val strikeZoneY: Int = screenHeight - keyboardHeightPx - 8
  }

  data class VisibleTile(
      val event: NoteEvent,
      val yPx: Float,       // top edge of tile in screen pixels
      val heightPx: Float   // height in screen pixels
  )
  ```
- **How to verify**: Unit test: event at t=2.0s, current time=0.0s, pixelsPerSecond=300 → `yPx` = `strikeZoneY - 600`.
- **Done when**: Unit test passes. Formula verified.

### 6.5 Verify tile positions at various scroll speeds
- **What**: Unit tests:
  - At 1x speed (300 px/s): tile hits strike zone exactly at its scheduled time.
  - At 0.5x speed: same tile takes 2x as long to reach strike zone.
  - At 1.5x speed: tile reaches strike zone in 2/3 of the time.
- **How to verify**: All three unit tests pass.
- **Done when**: All pass.

### 6.6 Define timing windows
- **What**: Create `TimingWindows.kt`:
  ```kotlin
  object TimingWindows {
      const val PERFECT_MS = 30.0    // ±30ms = Perfect
      const val GOOD_MS = 80.0       // ±80ms = Good
      const val MISS_MS = 150.0      // > 150ms = Miss (tile passes by)

      fun evaluate(hitOffsetMs: Double): HitAccuracy = when {
          hitOffsetMs.absoluteValue <= PERFECT_MS -> HitAccuracy.PERFECT
          hitOffsetMs.absoluteValue <= GOOD_MS    -> HitAccuracy.GOOD
          else                                     -> HitAccuracy.MISS
      }
  }

  enum class HitAccuracy { PERFECT, GOOD, MISS, IGNORED }
  ```
- **How to verify**: Unit tests: `evaluate(0.0)` == PERFECT, `evaluate(50.0)` == GOOD, `evaluate(100.0)` == MISS.
- **Done when**: All unit tests pass.

### 6.7 Create `HitDetector.kt`
- **What**:
  ```kotlin
  class HitDetector(private val scheduler: TileScheduler) {
      // Called when player presses a key (from MIDI input)
      fun onNoteOn(midiNote: Int, currentTimeSec: Double): HitResult {
          val closestTile = findClosestScheduledNote(midiNote, currentTimeSec)
              ?: return HitResult(midiNote, HitAccuracy.IGNORED, 0.0)

          val offsetMs = (currentTimeSec - closestTile.startTimeSeconds) * 1000.0
          val accuracy = TimingWindows.evaluate(offsetMs)
          return HitResult(midiNote, accuracy, offsetMs)
      }

      private fun findClosestScheduledNote(note: Int, time: Double): NoteEvent? {
          return scheduler.events
              .filter { it.midiNote == note &&
                        abs(it.startTimeSeconds - time) < TimingWindows.MISS_MS / 1000.0 }
              .minByOrNull { abs(it.startTimeSeconds - time) }
      }
  }

  data class HitResult(
      val midiNote: Int,
      val accuracy: HitAccuracy,
      val offsetMs: Double    // positive = late, negative = early
  )
  ```
- **How to verify**: Unit test: schedule note at t=1.000s, call `onNoteOn(note, 1.020)` → GOOD (20ms late). Call at `1.000` → PERFECT. Call at `1.100` → MISS.
- **Done when**: All three unit tests pass.

### 6.8 Handle early note detection (note before tile reaches strike zone)
- **What**: If player presses a note when the tile is still approaching (early hit),
  it should still register if within timing window. Negative `offsetMs` = early.
  Update `findClosestScheduledNote` to look forward in time, not just backward.
- **How to verify**: Unit test: call `onNoteOn(note, 0.990)` when note is at `1.000s` → evaluates as PERFECT (10ms early).
- **Done when**: Early hit unit test passes.

### 6.9 Create `ScoreSystem.kt`
- **What**:
  ```kotlin
  class ScoreSystem {
      var score = 0
          private set
      var combo = 0
          private set
      var maxCombo = 0
          private set

      private val perfectPoints = 100
      private val goodPoints = 50

      fun onHit(accuracy: HitAccuracy) {
          when (accuracy) {
              HitAccuracy.PERFECT -> {
                  combo++
                  score += perfectPoints * comboMultiplier()
              }
              HitAccuracy.GOOD -> {
                  combo++
                  score += goodPoints * comboMultiplier()
              }
              HitAccuracy.MISS -> { combo = 0 }
              HitAccuracy.IGNORED -> {}
          }
          maxCombo = maxOf(maxCombo, combo)
      }

      private fun comboMultiplier(): Int = when {
          combo >= 50 -> 4
          combo >= 20 -> 3
          combo >= 10 -> 2
          else -> 1
      }
  }
  ```
- **How to verify**: Unit test: 10 PERFECT hits → score = 100 × 10 = 1000 (no multiplier until combo ≥ 10). 11th PERFECT hit → score += 100 × 2 = 200.
- **Done when**: Score unit tests pass including multiplier breakpoints.

### 6.10 Verify combo reset on miss
- **How to verify**: Unit test: 15 PERFECT hits (combo=15, multiplier=2), then MISS → combo=0, multiplier back to 1.
- **Done when**: Unit test passes.

### 6.11 Create `GameEngine.kt` — ties it together
- **What**:
  ```kotlin
  class GameEngine(
      private val events: List<NoteEvent>,
      private val screenHeight: Int,
      private val keyboardHeightPx: Int
  ) {
      val clock = GameClock()
      val scheduler = TileScheduler(events, screenHeight, keyboardHeightPx)
      val hitDetector = HitDetector(scheduler)
      val score = ScoreSystem()

      // Called every frame by the render loop
      fun tick(): GameState {
          val time = clock.currentTimeSeconds
          return GameState(
              currentTimeSec = time,
              visibleTiles = scheduler.getVisibleTiles(time),
              score = score.score,
              combo = score.combo
          )
      }

      // Called by MIDI input layer
      fun onNoteOn(midiNote: Int): HitResult {
          val result = hitDetector.onNoteOn(midiNote, clock.currentTimeSeconds)
          score.onHit(result.accuracy)
          return result
      }
  }

  data class GameState(
      val currentTimeSec: Double,
      val visibleTiles: List<VisibleTile>,
      val score: Int,
      val combo: Int
  )
  ```
- **How to verify**: Unit test: construct `GameEngine` with 5 test events, call `tick()` at time=0 → returns 5 visible tiles.
- **Done when**: `tick()` unit test passes.

### 6.12 Speed multiplier — scale tile positions
- **What**: Add `setSpeed(multiplier: Float)` to `GameEngine` that:
  1. Sets `clock.speedMultiplier`
  2. Adjusts `scheduler.pixelsPerSecond` proportionally so tiles always look the same
     distance from strike zone regardless of speed.
- **How to verify**: Unit test: at 0.5x speed, tile at t=2.0s appears same distance from strike zone as at 1x speed at t=1.0s.
- **Done when**: Unit test passes.

### 6.13 Auto-miss detection — tiles that pass the strike zone
- **What**: In `tick()`, check for tiles whose `yPx > strikeZoneY + 20` (tile has passed).
  For each such tile that hasn't been hit, call `score.onHit(HitAccuracy.MISS)` once.
  Track which tiles have been processed to avoid double-counting.
- **How to verify**: Mock: event at t=0.5s, let clock run past t=0.7s (tile has passed). Score shows a miss, combo reset.
- **Done when**: Auto-miss fires exactly once per missed tile.

### 6.14 Song completion detection
- **What**: In `tick()`, if `currentTimeSec > durationSeconds + 2.0` (2s buffer after last note):
  set `gameState = FINISHED`. Expose `isFinished: Boolean`.
- **How to verify**: Construct engine with 1-second song, run clock to 3.5s → `isFinished == true`.
- **Done when**: Unit test passes.

### 6.15 Results summary
- **What**: Add `fun getResults(): GameResults`:
  ```kotlin
  data class GameResults(
      val score: Int,
      val maxCombo: Int,
      val perfectCount: Int,
      val goodCount: Int,
      val missCount: Int,
      val accuracy: Float  // (perfect + good) / total notes
  )
  ```
- **How to verify**: Unit test: 10 perfect, 3 good, 2 miss → accuracy = 0.867 (13/15).
- **Done when**: Unit test passes.

### 6.16 Practice mode — tiles wait at strike zone
- **What**: Add `isPracticeMode: Boolean` flag to `GameEngine`.
  When true: tiles that reach the strike zone pause there (their `yPx` is clamped to `strikeZoneY`).
  Clock pauses automatically when any tile is waiting. Resumes when the correct note is played.
- **How to verify**: Enable practice mode, let a tile reach strike zone → clock pauses. Press correct note → clock resumes, tile disappears.
- **Done when**: Practice mode pauses and resumes correctly for sequential notes.

### 6.17 Scroll speed configuration
- **What**: Add `pixelsPerSecond: Float` as a configurable property on `TileScheduler`.
  Valid range: 100–800. Default: 300.
  Expose as `setScrollSpeed(Float)` on `GameEngine`.
- **How to verify**: Change scroll speed → tiles fall faster/slower visually.
- **Done when**: Range 100–800 works without visual artifacts.

### 6.18 Compute statistics per-tick for HUD
- **What**: `GameState` should include current accuracy percentage (rolling last 20 hits):
  ```kotlin
  val rollingAccuracy: Float  // 0.0–1.0, last 20 hits
  ```
- **How to verify**: Play 20 perfect notes → `rollingAccuracy == 1.0`. Then 20 misses → `rollingAccuracy == 0.0`.
- **Done when**: Rolling accuracy updates correctly.

---

## Standalone Test

**Test class**: `GameEngineTest` (JUnit unit tests — no Activity needed)

**Tests to run**:
```
./gradlew :app:testDebugUnitTest --tests "com.pianotiles.engine.*"
```

**Test coverage checklist**:
- [ ] Clock accuracy: 1 second elapsed = 1.0 ± 0.01s
- [ ] Clock play/pause/seek
- [ ] Tile y-position formula at 1x, 0.5x, 1.5x speed
- [ ] TimingWindows: perfect, good, miss boundaries
- [ ] HitDetector: early hit, on-time hit, late hit, miss
- [ ] ScoreSystem: combo multiplier breakpoints
- [ ] ScoreSystem: combo reset on miss
- [ ] Auto-miss: fires once per missed tile
- [ ] Song completion: `isFinished` triggers correctly
- [ ] Results accuracy calculation

**Expected result**: All unit tests pass. Zero failures.

---

## Performance Target

| Metric | Target |
|---|---|
| `tick()` execution time | < 0.5ms (called every frame at 120fps) |
| `getVisibleTiles()` for 100 events | < 0.1ms |
| `onNoteOn()` hit detection | < 0.1ms |

These must be measured with Android Profiler / microbenchmarks.

---

## Integration Points

`GameEngine` exposes:
```kotlin
fun tick(): GameState                         // called each frame by render loop
fun onNoteOn(midiNote: Int): HitResult        // called by MIDI input layer
fun setSpeed(multiplier: Float)
fun setScrollSpeed(pixelsPerSecond: Float)
fun isPracticeMode: Boolean  (settable)
fun getResults(): GameResults
val isFinished: Boolean
val clock: GameClock                          // for play/pause/seek from UI
```

`GameState` (returned by `tick()`) is consumed by:
- Layer 2 (Rendering): reads `visibleTiles` to post `RenderCommand`s
- Layer 7 (UI): reads `score`, `combo`, `rollingAccuracy` for HUD
