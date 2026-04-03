# Piano Falling Tiles

A Synthesia-style falling-tiles piano trainer for Android tablets, built for the OnePlus Pad 2/3 (144 Hz) with USB OTG MIDI keyboard support.

![Platform](https://img.shields.io/badge/platform-Android-green)
![Min SDK](https://img.shields.io/badge/minSdk-31-blue)
![Language](https://img.shields.io/badge/language-Kotlin%20%2B%20C%2B%2B-orange)

## Features

- **Listen / Watch mode** — tiles fall automatically, song plays through the app or your piano's own sound
- **Tutorial / Practice mode** — tiles freeze at the strike zone until you press the correct key; early/late presses scored by timing offset
- **Two built-in demo songs** — Twinkle Twinkle Little Star and Ode to Joy (two-hand arrangement)
- **MIDI file support** — pick any `.mid` file from device storage
- **Hand practice** — filter by Both Hands / Right Only / Left Only (tracks 0 and 1)
- **3D bevelled keyboard** — white and black keys with highlight/shadow strips; pressed keys physically shift down
- **Correct black key positions** — C#, D#, F#, G#, A# in their proper places
- **Metronome** — wall-clock accurate, downbeat accent, BPM from MIDI file (drift-free absolute scheduling)
- **Beat grid lines** — visual horizontal lines snapped to each beat and bar
- **BPM control** — single input that scales both song speed and metronome together
- **App audio toggle** — silence the synth so your acoustic/digital piano's own sound is heard
- **Scoring** — PERFECT / GOOD / MISS based on timing distance; combo multiplier; results screen
- **OpenGL ES 3.2 renderer** — single batched draw call per frame, runs at 144 Hz without performance issues

---

## Hardware Requirements

| Item | Tested on |
|------|-----------|
| Android tablet | OnePlus Pad 2 / Pad 3 |
| Android version | 12+ (API 31+) |
| MIDI keyboard | CASIO CT-S series via USB OTG |
| USB cable | USB-C OTG adapter + standard USB-B/A cable |

Any Android 12+ device with a USB port and a class-compliant USB MIDI keyboard should work.

---

## Building from Source

### Prerequisites

- Android Studio Hedgehog or newer (or command-line SDK tools)
- Android NDK r25+ (for the Oboe audio JNI layer)
- CMake 3.22+
- A device or emulator running Android 12+ (API 31)

### 1. Clone the repository

```bash
git clone https://github.com/sandeepsj/piano-falling-tiles.git
cd piano-falling-tiles
```

### 2. Point to your Android SDK

Create `local.properties` in the project root (this file is gitignored):

```
sdk.dir=/path/to/your/Android/Sdk
```

On Linux/macOS this is typically `~/Android/Sdk`.  
On Windows: `C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk`

### 3. Build

```bash
# Debug APK
./gradlew assembleDebug

# Install directly to a connected device
./gradlew installDebug
```

Or open in Android Studio and click **Run**.

### 4. USB OTG MIDI setup

1. Plug your MIDI keyboard into the tablet via a USB OTG adapter.
2. Android will show a permission dialog — tap **Allow**.
3. The app's home screen will show **"MIDI keyboard connected"** in green.
4. If it doesn't appear, check that your cable and adapter support data (not charge-only).

---

## Project Structure

```
app/src/main/
├── cpp/                        # JNI audio bridge (Oboe AAudio synth)
│   ├── AudioEngine.cpp
│   ├── PianoSynthesizer.cpp
│   └── jni_bridge.cpp
└── java/com/pianotiles/
    ├── audio/
    │   └── AudioManager.kt     # Kotlin ↔ JNI bridge
    ├── engine/
    │   ├── GameEngine.kt       # Game loop, tutorial state machine, metronome
    │   ├── ScoreSystem.kt      # PERFECT/GOOD/MISS scoring
    │   ├── TilePool.kt         # Pre-allocated 200-slot tile pool
    │   └── TileScheduler.kt    # Tile Y-position from song time
    ├── midi/
    │   ├── BuiltInSongs.kt     # Hardcoded demo songs (Twinkle, Ode to Joy)
    │   ├── MidiFileParser.kt   # MIDI type-0/1 parser (ktmidi-based)
    │   └── MidiInputManager.kt # android.media.midi USB OTG input
    ├── rendering/
    │   ├── CoordSystem.kt      # Pixel → OpenGL NDC helpers
    │   ├── GameRenderer.kt     # GLSurfaceView renderer, batched draw call
    │   ├── GameSurfaceView.kt  # GLSurfaceView wrapper
    │   ├── KeyLayout.kt        # 88-key pixel position calculator
    │   ├── RenderCommand.kt    # Thread-safe GL command queue messages
    │   └── TilePool.kt         # Tile render state
    └── ui/
        ├── GameViewModel.kt    # AndroidViewModel, state, playback control
        ├── screens/
        │   ├── HomeScreen.kt   # Song picker, mode/hand/BPM/metronome controls
        │   ├── GameScreen.kt   # Full-screen GL view + HUD overlays
        │   └── ResultsScreen.kt
        └── components/
            ├── PlaybackControls.kt
            └── ScoreHUD.kt
```

---

## Architecture Notes

- **Game loop**: `Choreographer.FrameCallback` on the main thread (vsync-accurate)
- **Rendering**: OpenGL ES 3.2 via `GLSurfaceView`; all geometry batched into a single `glDrawArrays` call per frame (~600–700 colored quads at peak)
- **Audio**: Oboe AAudio, 20-voice sine synthesizer, ~10 ms latency
- **Thread model**: MIDI callbacks → `mainHandler.post` for tutorial logic; render commands via `ArrayBlockingQueue<RenderCommand>`
- **Tutorial state machine**: EARLY_WINDOW (accept press up to 400 ms early) → FROZEN (tile waits) → HOLDING (key held, tile falls) → COMPLETE

---

## MIDI File Conventions

- **Track 0** = right hand
- **Track 1** = left hand

The hand filter (Both / Right / Left) on the home screen uses this convention. Standard piano MIDI files from sites like [Musescore](https://musescore.com) or [BITMIDI](https://bitmidi.com) typically follow this layout.

---

## Licence

MIT — see [LICENSE](LICENSE) for details.
