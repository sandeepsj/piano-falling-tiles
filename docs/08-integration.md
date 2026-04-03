# 08 — Integration

## Purpose

Wire all 7 layers together into a working app. Each connection is made one at a time
and tested after each wiring step. By the end of this layer, the full Synthesia
experience works end-to-end on the OnePlus tablet.

This document describes the integration ORDER and how to test each connection.

---

## Prerequisites

Before starting Layer 8, every layer below must be DONE:
- [ ] Layer 2: `RenderingTestActivity` passes standalone test
- [ ] Layer 3: `AudioTestActivity` passes standalone test
- [ ] Layer 4: `MidiMonitorActivity` passes standalone test
- [ ] Layer 5: `MidiParserActivity` passes standalone test
- [ ] Layer 6: All `GameEngineTest` unit tests pass
- [ ] Layer 7: All 4 screens reachable, no navigation crashes

---

## Tasks

### 8.1 Wire MIDI File → Game Engine
- **What**: In `GameViewModel.loadSong(bytes)`:
  ```kotlin
  fun loadSong(bytes: ByteArray) {
      val parser = MidiFilePlayer()
      if (!parser.loadFromBytes(bytes)) return
      gameEngine = GameEngine(
          events = parser.events,
          screenHeight = screenHeight,
          keyboardHeightPx = keyboardHeightPx
      )
      _songDuration.value = parser.durationSeconds
      Log.d("INTEGRATION", "Song loaded: ${parser.events.size} events")
  }
  ```
- **How to verify**: Load Für Elise → logcat shows "Song loaded: X events". `gameEngine.tick()` returns a non-empty tile list at t=0.
- **Done when**: `getVisibleTiles(0.0)` returns tiles for Für Elise's first few notes.

### 8.2 Wire Game Engine tick → Renderer
- **What**: Set up the game loop: `Choreographer.postFrameCallback` → calls `gameEngine.tick()` → posts `RenderCommand`s to `GameRenderer.renderCommands`.
  ```kotlin
  private val frameCallback = Choreographer.FrameCallback { frameTimeNs ->
      val state = gameEngine.tick()

      // Sync tile renderer with current game state
      state.visibleTiles.forEach { tile ->
          renderer.renderCommands.offer(RenderCommand.SpawnTile(
              note = tile.event.midiNote,
              yPx = tile.yPx,
              heightPx = tile.heightPx,
              color = midiFilePlayer.colorForTrack(tile.event.trackIndex)
          ))
      }

      // Schedule next frame
      Choreographer.getInstance().postFrameCallback(this)
  }
  ```
- **How to verify**: Load Für Elise, press Play → colored tiles fall from top of screen aligned to piano keys.
- **Done when**: Tiles fall continuously for 30 seconds without jitter or misalignment.

### 8.3 Wire Game Engine → Audio (scheduled playback)
- **What**: When playing back a MIDI file (not playing along), schedule audio via Choreographer tick.
  In the frame callback, for each tile that just crossed the strike zone (`yPx` between 0 and `strikeZoneY`), call `audioManager.noteOn(midiNote, velocity)`.
  ```kotlin
  state.visibleTiles
      .filter { isAtStrikeZone(it.yPx) && !it.wasTriggered }
      .forEach { tile ->
          audioManager.noteOn(tile.event.midiNote, tile.event.velocity)
          tile.wasTriggered = true
      }
  ```
- **How to verify**: Load Für Elise, press Play → hear piano notes in sync with falling tiles.
- **Done when**: Audio and visual are in sync. Notes start within ±30ms of tile crossing the strike zone.

### 8.4 Verify audio-visual sync
- **What**: Visual inspection + listening test:
  - Load Für Elise
  - Play from beginning
  - Listen: does the note sound exactly when the tile hits the strike zone line?
  - If ahead: decrease audio scheduling offset
  - If behind: increase audio scheduling offset
  Add an `audioOffset: Long` (milliseconds) to `GameViewModel` that shifts when notes are triggered.
- **How to verify**: Subjective test — tile hits strike zone at same moment the note sounds. 3 people agree.
- **Done when**: Audio-visual sync is perceived as simultaneous. Offset value documented in code.

### 8.5 Wire MIDI Input → Game Engine (hit detection)
- **What**: Register `MidiInputManager` listener in `GameViewModel`:
  ```kotlin
  midiInputManager.addListener { event ->
      if (event.velocity > 0) {
          val result = gameEngine.onNoteOn(event.midiNote)
          renderer.renderCommands.offer(
              if (result.accuracy == HitAccuracy.MISS) RenderCommand.MissTile(event.midiNote)
              else RenderCommand.HitTile(event.midiNote)
          )
          _lastHitAccuracy.value = result.accuracy
      }
  }
  ```
- **How to verify**: Load Für Elise, press Play, play a note on MIDI keyboard → tile flashes and disappears (or turns red on miss).
- **Done when**: Tile hit/miss animations trigger correctly for every key press.

### 8.6 Wire MIDI Input → Audio (real-time sound)
- **What**: MIDI input should also play audio immediately (not wait for scheduled playback):
  ```kotlin
  midiInputManager.addListener { event ->
      if (event.velocity > 0) {
          audioManager.noteOn(event.midiNote, event.velocity)  // immediate
          // Also hit detection (8.5)
      } else {
          audioManager.noteOff(event.midiNote)
      }
  }
  ```
- **How to verify**: With no song loaded, connect MIDI keyboard, press keys → hear immediate piano sound with no perceptible delay.
- **Done when**: Key press latency imperceptible (< 20ms). No noticeable delay between pressing key and hearing sound.

### 8.7 Wire Game Engine → Key Highlight (renderer)
- **What**: When a tile is at/near the strike zone for note N, highlight piano key N:
  ```kotlin
  state.visibleTiles
      .filter { it.yPx >= strikeZoneY - 20 && it.yPx <= strikeZoneY + tileHeight }
      .forEach { tile ->
          renderer.renderCommands.offer(RenderCommand.PressKey(tile.event.midiNote))
      }
  ```
  When tile passes, release:
  ```kotlin
  renderer.renderCommands.offer(RenderCommand.ReleaseKey(midiNote))
  ```
- **How to verify**: Play Für Elise → piano keys light up exactly as each tile reaches the strike zone.
- **Done when**: Key highlights match tile timing. No keys stuck highlighted.

### 8.8 Wire Score → UI HUD
- **What**: `gameEngine.tick()` returns `GameState` which includes score, combo, accuracy.
  Emit these via `StateFlow` in `GameViewModel`. `ScoreHUD` collects and displays them.
- **How to verify**: Play along and hit notes → score increases in real-time in HUD.
- **Done when**: Score updates within 1 frame of hit detection.

### 8.9 Wire Song End → Results Screen
- **What**: In the frame callback, check `gameEngine.isFinished`. When true:
  ```kotlin
  if (gameEngine.isFinished) {
      val results = gameEngine.getResults()
      _gameResults.value = results
      navController.navigate(Screen.Results.route)
  }
  ```
- **How to verify**: Let a song play to completion → Results screen appears automatically.
- **Done when**: Results screen shows correctly after song finishes. Scores/accuracy are accurate.

### 8.10 Wire Settings → Game Engine + Renderer
- **What**: When user changes settings (scroll speed, practice mode, etc.) in `SettingsScreen`,
  these flow through `GameViewModel` → `GameEngine`:
  ```kotlin
  fun setScrollSpeed(pixelsPerSecond: Float) {
      gameEngine.setScrollSpeed(pixelsPerSecond)
      // No renderer change needed — tile positions are computed by GameEngine
  }
  fun setPracticeMode(enabled: Boolean) {
      gameEngine.isPracticeMode = enabled
  }
  ```
- **How to verify**: Open settings mid-song (pause first), change scroll speed → tiles fall at new speed on resume.
- **Done when**: All 5 settings in SettingsScreen take effect immediately in game.

### 8.11 End-to-end test — auto-playback (no MIDI keyboard)
- **What**: Manual test:
  1. Launch app, wait for samples to load.
  2. Select "Für Elise".
  3. Press Play.
  4. Watch for 60 seconds.
  5. Verify: tiles fall continuously, audio plays, keys highlight, no frame drops.
  6. Let it reach the end → Results screen appears.
- **How to verify**: All 5 points verified. Zero logcat errors. FPS ≥ 120 throughout.
- **Done when**: Full Für Elise playback works flawlessly from start to Results screen.

### 8.12 End-to-end test — play-along with MIDI keyboard
- **What**: Manual test:
  1. Connect MIDI keyboard via USB OTG.
  2. Launch app, load Für Elise.
  3. Press Play.
  4. Play along (attempt the melody).
  5. Verify: hitting correct notes flashes tiles and increases score.
  6. Missing notes turns tiles red and resets combo.
  7. Song ends → Results screen shows accurate stats.
- **How to verify**: Score reflects actual played notes. Accuracy percentage makes sense.
- **Done when**: Full play-along cycle works without any crashes or audio glitches.

---

## Integration Test Checklist

Each row must be checked off on the physical OnePlus tablet before shipping.

| # | Test | Pass |
|---|---|---|
| 1 | App launches, samples load in < 3s | ☐ |
| 2 | HomeScreen shows 2 songs + file picker button | ☐ |
| 3 | Load Für Elise → tiles visible on game screen | ☐ |
| 4 | Press Play → audio + tiles in sync | ☐ |
| 5 | Connect keyboard → device name shown in HUD | ☐ |
| 6 | Press keyboard key → immediate sound (< 20ms) | ☐ |
| 7 | Hit tile at correct time → PERFECT label + score | ☐ |
| 8 | Miss tile → MISS label + combo reset | ☐ |
| 9 | Song completes → Results screen auto-shown | ☐ |
| 10 | Results show correct score/accuracy | ☐ |
| 11 | Play Again → reloads same song | ☐ |
| 12 | Open custom .mid file → loads and plays | ☐ |
| 13 | Settings changes take effect in game | ☐ |
| 14 | Disconnect + reconnect keyboard mid-game → no crash | ☐ |
| 15 | 10 min sustained play → no memory leak (check Android Profiler) | ☐ |
| 16 | FPS ≥ 120 throughout entire Für Elise playback | ☐ |
| 17 | Audio latency imperceptible during play-along | ☐ |

---

## Performance Target

Full integrated app must meet ALL of:

| Metric | Target |
|---|---|
| FPS during play | ≥ 120fps sustained |
| Audio latency (key → sound) | ≤ 20ms |
| Audio-visual sync | ≤ 30ms perceived offset |
| Memory usage after 10 min | < 200MB |
| App size (APK) | < 50MB |
| Cold start → first tile rendered | < 3 seconds |
