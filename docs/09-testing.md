# 09 — Testing Strategy

## Philosophy

Every layer has its own test before integration. No assumptions.
Tests run on the physical OnePlus tablet, not the emulator, for all performance tests.

---

## Test Types

### 1. Unit Tests (JUnit, no device needed)
Run with: `./gradlew :app:testDebugUnitTest`

| Layer | Test Class | What's Covered |
|---|---|---|
| Layer 5 | `MidiFilePlayerTest` | Tick→seconds conversion, event extraction, tempo map |
| Layer 6 | `GameClockTest` | Accuracy, play/pause/seek |
| Layer 6 | `TileSchedulerTest` | Tile y-position formula, speed multipliers |
| Layer 6 | `TimingWindowsTest` | Perfect/good/miss boundaries |
| Layer 6 | `HitDetectorTest` | Early/late/miss detection, edge cases |
| Layer 6 | `ScoreSystemTest` | Combo multiplier, score calculation, reset |
| Layer 2 | `CoordSystemTest` | Pixel → NDC conversion |
| Layer 2 | `KeyLayoutTest` | Key positions, 88 keys fit screen, no overflow |

**Target**: 100% pass rate. Zero failures before any integration step.

---

### 2. Instrumented Tests (on-device, Espresso/Compose Test)
Run with: `./gradlew :app:connectedDebugAndroidTest`

| Layer | Test | What's Covered |
|---|---|---|
| Layer 3 | `AudioStreamTest` | Oboe opens, latency ≤ 20ms, no underruns for 60s |
| Layer 4 | `MidiInputTest` | USB device detected, events fired (manual keyboard needed) |
| Layer 7 | `NavigationTest` | All 4 screens reachable, no crash |
| Layer 7 | `SettingsTest` | Settings persist across Activity restart |

---

### 3. Standalone Activity Tests (manual, on-device)
Each layer has a test Activity. Run each before integration.

| Activity | Layer | Manual Steps | Pass Criteria |
|---|---|---|---|
| `RenderingTestActivity` | 2 | Launch, observe animation | 120fps, no glitches |
| `AudioTestActivity` | 3 | Load samples, tap keys | Correct pitches, < 20ms lag |
| `MidiMonitorActivity` | 4 | Plug keyboard, play notes | Events logged, reconnect works |
| `MidiParserActivity` | 5 | Load both demo songs | Events extracted, sorted by time |

---

### 4. Performance Tests (Android Profiler + GPU Inspector)

#### 4.1 Rendering performance
**Tool**: Android GPU Inspector

**Steps**:
1. Connect tablet in developer mode
2. Open GPU Inspector, start capture on `RenderingTestActivity`
3. Let 200 tiles be active simultaneously (stress test)
4. Capture 300 frames

**Pass criteria**:
- Frame time (p50): ≤ 6.9ms at 144Hz
- Frame time (p99): ≤ 8.3ms
- GPU utilization: < 40%
- Zero dropped frames in 300-frame window

#### 4.2 Audio latency verification
**Tool**: Logcat + manual listening

**Steps**:
1. Launch `AudioTestActivity`
2. `adb logcat | grep AUDIO`
3. Note the "Estimated output latency: Xms" value

**Pass criteria**:
- Logged latency ≤ 20ms
- Subjective test: key press → sound delay is imperceptible

#### 4.3 Memory leak test
**Tool**: Android Profiler (Memory tab)

**Steps**:
1. Launch full integrated app
2. Load Für Elise
3. Play for 10 minutes continuously
4. Open Profiler → Memory → take heap dump
5. Force GC, check for retained objects

**Pass criteria**:
- Memory usage stable (not growing) after 5 minutes
- No `Activity` or `Fragment` leaks in heap dump
- Total app memory < 200MB after 10 minutes

#### 4.4 CPU usage during playback
**Tool**: Android Profiler (CPU tab)

**Steps**:
1. Start full playback with Choreographer game loop running
2. Profile for 60 seconds

**Pass criteria**:
- Main thread: < 30% CPU (should be mostly waiting for vsync)
- Audio thread (Oboe): < 10% CPU
- Render thread: < 20% CPU
- Total app CPU: < 50%

---

### 5. Integration Test Checklist (from `08-integration.md`)

Must complete all 17 items in the checklist before the app is considered "done".
See `08-integration.md` for the full list.

---

## Test Data

### MIDI Files for Testing

| File | Purpose | Notes |
|---|---|---|
| `fur_elise.mid` | Primary test song | Known structure, not too long |
| `clair_de_lune.mid` | Multi-tempo test | Has many tempo changes |
| `single_note.mid` | Minimal test | 1 note at t=2.000s for hit detection tests |
| `c_major_scale.mid` | Sequential notes | 8 notes, 0.5s apart, all white keys |
| `chord_test.mid` | Polyphony test | 20 simultaneous notes at t=1.0s |
| `empty.mid` | Edge case | Valid MIDI header, 0 notes |
| `corrupt.mid` | Error handling | Truncated file, should not crash |

Create `single_note.mid`, `c_major_scale.mid`, `chord_test.mid`, `empty.mid`, and `corrupt.mid`
programmatically in test setup code using ktmidi's writer API.

---

## Regression Test Triggers

Run the full test suite whenever:
- Any C++ file in `cpp/` changes (audio layer)
- `NoteEvent.kt` changes (used by 3 layers)
- `GameEngine.kt` changes
- `TileScheduler.kt` changes

These files are the most interconnected — a change in any of them can break multiple layers.

---

## Known Limitations / Won't Test

- **USB OTG with 3+ keyboards simultaneously**: Out of scope, but app should not crash.
- **MIDI files > 10MB**: Not supported, show user-friendly error message.
- **Bluetooth MIDI**: Not in scope for v1.0 (USB OTG only).
- **Non-piano MIDI tracks** (drums, etc.): Filtered out during parsing (notes outside 21–108).

---

## Bug Report Template

When a bug is found during testing:

```
Layer:          [which layer, e.g., "Layer 3 Audio"]
Task:           [which task, e.g., "3.9 Mix active voices"]
Device:         OnePlus Pad 2 / Pad 3
Android OS:     [version]
Repro steps:    [numbered steps]
Expected:       [what should happen]
Actual:         [what actually happens]
Logcat snippet: [relevant lines]
Profiler data:  [if performance issue]
```

---

## Definition of Done

The app is ready for use when:

1. All JUnit unit tests pass (`testDebugUnitTest`)
2. All instrumented tests pass (`connectedDebugAndroidTest`)
3. All 4 standalone Activity tests pass manually
4. All 17 integration checklist items checked
5. All 4 performance benchmarks pass
6. No crashes in 10 minutes of continuous use
7. Audio latency ≤ 20ms verified on OnePlus tablet
8. FPS ≥ 120 sustained during full song playback
