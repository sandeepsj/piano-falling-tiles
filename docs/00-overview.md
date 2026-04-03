# 00 — Project Overview

## What We Are Building

A Synthesia-style piano falling-tiles app for **Android tablets only**.
Target device: **OnePlus Pad 2 / Pad 3** (Snapdragon 8 Gen 3 / Elite, 144Hz display).
Input: USB OTG MIDI keyboard. Output: tablet speaker or USB audio.

---

## Core Experience

1. User loads a `.mid` file (or picks from bundled songs).
2. Colored tiles fall from top of screen toward a piano keyboard at the bottom.
3. Each tile corresponds to a piano note, timed to the MIDI file.
4. User plays along on their MIDI keyboard (connected via USB OTG).
5. App detects hits, plays piano sounds, shows score.

---

## Architecture — Layer Map

```
┌──────────────────────────────────────────────────────────────────┐
│  LAYER 7 — Jetpack Compose UI                                    │
│  HomeScreen  GameScreen  ResultsScreen  SettingsScreen  HUD      │
└───────────────────────────┬──────────────────────────────────────┘
                            │ AndroidView
┌───────────────────────────▼──────────────────────────────────────┐
│  LAYER 2 — Rendering (GLSurfaceView + OpenGL ES 3.2)             │
│  TileRenderer  PianoKeyRenderer  ParticleSystem  ObjectPool       │
└───────────────────────────┬──────────────────────────────────────┘
                            │ reads game state (lock-free)
┌───────────────────────────▼──────────────────────────────────────┐
│  LAYER 6 — Game Engine                                           │
│  GameClock  TileScheduler  HitDetector  ScoreSystem              │
└──────────┬────────────────────────────────┬──────────────────────┘
           │                                │
┌──────────▼──────────────┐  ┌─────────────▼────────────────────┐
│  LAYER 3 — Audio        │  │  LAYER 4 — MIDI Input            │
│  Oboe C++ stream        │  │  android.media.midi              │
│  Piano sample playback  │  │  USB OTG device listener         │
│  20-voice polyphony     │  │  NoteOn/NoteOff events           │
└─────────────────────────┘  └──────────────────────────────────┘
                                            │
                             ┌──────────────▼───────────────────┐
                             │  LAYER 5 — MIDI File             │
                             │  ktmidi parser                   │
                             │  TileEvent[] list                │
                             └──────────────────────────────────┘
┌─────────────────────────────────────────────────────────────────┐
│  LAYER 1 — Project Setup (foundation for all layers above)      │
└─────────────────────────────────────────────────────────────────┘
```

---

## Dependency Graph (strict build order)

```
Layer 1 (Setup)
    └─► Layer 2 (Rendering)      — needs: project structure only
    └─► Layer 3 (Audio)          — needs: project structure + CMake
    └─► Layer 4 (MIDI Input)     — needs: project structure only
    └─► Layer 5 (MIDI File)      — needs: project structure only
    └─► Layer 6 (Game Engine)    — needs: Layer 5 (TileEvent type)
    └─► Layer 7 (UI)             — needs: Layer 2 (GLSurfaceView)
    └─► Layer 8 (Integration)    — needs: ALL layers above
```

Layers 2–5 can be built in **any order** after Layer 1.
Layer 6 needs Layer 5's data types.
Layer 7 needs Layer 2's view.
Layer 8 requires everything.

---

## Development Principles

1. **Standalone first.** Every layer has a test Activity that runs without other layers.
   No layer touches another layer's code until both standalone tests pass.

2. **Smallest possible tasks.** Each task is one thing with one verification step.
   If you can't verify it in under 2 minutes, split the task.

3. **Done means done.** A task is not done until its "Done when" criterion passes on
   the physical OnePlus tablet, not on the emulator.

4. **No forward progress on failure.** If a task's criterion fails, fix it before
   moving to the next task. Never accumulate technical debt mid-layer.

5. **Performance gates.** Each layer has a performance target. If the target is not
   met, the layer is not integrated. Profiling happens per layer, not at the end.

---

## Tech Stack Summary

| Layer | Technology |
|---|---|
| Language | Kotlin (app) + C++ (audio hot path) |
| UI | Jetpack Compose |
| Rendering | GLSurfaceView + OpenGL ES 3.2 |
| Audio | Oboe 1.9 (C++, JNI bridge) |
| MIDI input | android.media.midi (native Android API) |
| MIDI parsing | ktmidi 0.21 (pure Kotlin) |
| State | ViewModel + StateFlow + Kotlin Coroutines |
| Build | Gradle 8 + CMake 3.22 |

---

## Target Performance (non-negotiable before shipping)

| Metric | Target |
|---|---|
| Rendering frame rate | 120fps sustained (144fps if display allows) |
| Frame time (p99) | < 10ms |
| Audio output latency | < 20ms (Oboe AAudio exclusive) |
| MIDI input → audio | < 20ms round-trip |
| App cold start | < 2 seconds to first frame |
| Sample loading | < 3 seconds (progress bar shown) |

---

## File Naming Conventions

```
app/src/main/
  cpp/                    C++ source files (snake_case)
  java/com/pianotiles/
    audio/                AudioManager.kt
    engine/               GameEngine.kt, TileScheduler.kt, etc.
    midi/                 MidiInputManager.kt, MidiFilePlayer.kt
    rendering/            GameSurfaceView.kt, GameRenderer.kt, etc.
    ui/screens/           HomeScreen.kt, GameScreen.kt, etc.
    ui/components/        ScoreHUD.kt, PlaybackControls.kt, etc.
  assets/
    samples/piano/        C1.ogg … C8.ogg (88 files, 48kHz mono)
    midi/                 demo songs
```

---

## Glossary

| Term | Meaning |
|---|---|
| TileEvent | A scheduled note: midi note number, start time (seconds), duration (seconds) |
| Strike zone | The horizontal line at the bottom where tiles meet the piano keys |
| Burst size | Oboe's minimum audio callback buffer (typically 128–256 frames at 48kHz) |
| Tick | MIDI time unit; converted to seconds using tempo (BPM) |
| Polyphony | Number of simultaneous notes the audio engine can play (target: 20 voices) |
| JNI bridge | The Kotlin ↔ C++ interface for audio calls |
