# 01 — Project Setup

## Purpose

Create the Android project skeleton: Gradle config, CMake config, folder structure,
and placeholder files for every module. This layer contains no business logic.
Its only job is to make the project compile and run an empty screen on the device.

Does NOT include: any rendering, audio, MIDI, or game logic.

---

## Dependencies

None. This is the foundation.

---

## Tasks

### 1.1 Create Android project
- **What**: In Android Studio, create a new project. Template: "Empty Activity". Language: Kotlin. Min SDK: 28 (Android 9 — covers all OnePlus tablets). Package: `com.pianotiles`. Save to `/home/sandeepsj/Developer/piano-falling-tiles`.
- **How to verify**: Project opens without errors in Android Studio.
- **Done when**: Gradle sync completes with 0 errors.

### 1.2 Configure `build.gradle` (app module)
- **What**: Set the following:
  ```gradle
  compileSdk 35
  targetSdk 35
  minSdk 28
  versionCode 1
  versionName "0.1.0"

  ndk { abiFilters 'arm64-v8a' }   // Snapdragon is 64-bit only
  ```
- **How to verify**: Gradle sync succeeds.
- **Done when**: No build errors, `arm64-v8a` is the only ABI.

### 1.3 Add Jetpack Compose dependencies
- **What**: Add to `build.gradle`:
  ```gradle
  implementation platform('androidx.compose:compose-bom:2024.12.01')
  implementation 'androidx.compose.ui:ui'
  implementation 'androidx.compose.material3:material3'
  implementation 'androidx.compose.ui:ui-tooling-preview'
  implementation 'androidx.activity:activity-compose:1.9.3'
  implementation 'androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7'
  implementation 'androidx.navigation:navigation-compose:2.8.4'
  debugImplementation 'androidx.compose.ui:ui-tooling'
  ```
- **How to verify**: Gradle sync succeeds.
- **Done when**: `import androidx.compose.ui.Modifier` compiles without error in `MainActivity.kt`.

### 1.4 Add Oboe dependency
- **What**: Add to `build.gradle`:
  ```gradle
  implementation 'com.google.oboe:oboe:1.9.0'
  ```
- **How to verify**: Gradle sync succeeds.
- **Done when**: `oboe` AAR appears in External Libraries in Android Studio.

### 1.5 Add ktmidi dependency
- **What**: Add to `build.gradle`:
  ```gradle
  implementation 'io.github.atsushieno:ktmidi:0.7.1'
  ```
  Note: check latest stable version at https://github.com/atsushieno/ktmidi/releases
- **How to verify**: Gradle sync succeeds.
- **Done when**: `import dev.atsushieno.ktmidi.*` compiles without error.

### 1.6 Create `CMakeLists.txt` skeleton
- **What**: Create `app/src/main/cpp/CMakeLists.txt`:
  ```cmake
  cmake_minimum_required(VERSION 3.22.1)
  project("piano_audio")

  find_package(oboe REQUIRED CONFIG)

  add_library(
      piano_audio
      SHARED
      jni_bridge.cpp
  )

  target_link_libraries(
      piano_audio
      oboe::oboe
      android
      log
  )

  target_compile_options(piano_audio PRIVATE -O3 -march=armv8-a)
  ```
- **How to verify**: Gradle sync succeeds.
- **Done when**: C++ build step completes with 0 errors in Build output.

### 1.7 Create `jni_bridge.cpp` stub
- **What**: Create `app/src/main/cpp/jni_bridge.cpp`:
  ```cpp
  #include <jni.h>

  // JNI bridge stub — audio functions added in Layer 3
  extern "C" {
      JNIEXPORT void JNICALL
      Java_com_pianotiles_audio_AudioManager_nativeInit(JNIEnv*, jobject) {
          // stub
      }
  }
  ```
- **How to verify**: C++ compiles without error.
- **Done when**: `Build > Make Project` succeeds with 0 C++ errors.

### 1.8 Link CMake in `build.gradle`
- **What**: Inside `android {}` block:
  ```gradle
  externalNativeBuild {
      cmake {
          path "src/main/cpp/CMakeLists.txt"
          version "3.22.1"
      }
  }
  ```
- **How to verify**: Gradle sync + build succeed.
- **Done when**: `.so` file for `piano_audio` appears under `build/intermediates/`.

### 1.9 Create folder structure (empty placeholder files)
- **What**: Create these empty `.kt` files (just package declarations, no logic):
  ```
  app/src/main/java/com/pianotiles/
    audio/AudioManager.kt
    engine/GameEngine.kt
    engine/TileScheduler.kt
    engine/HitDetector.kt
    engine/ScoreSystem.kt
    midi/MidiInputManager.kt
    midi/MidiFilePlayer.kt
    midi/NoteEvent.kt
    rendering/GameSurfaceView.kt
    rendering/GameRenderer.kt
    rendering/TileRenderer.kt
    rendering/PianoKeyRenderer.kt
    ui/screens/HomeScreen.kt
    ui/screens/GameScreen.kt
    ui/screens/ResultsScreen.kt
    ui/screens/SettingsScreen.kt
    ui/components/ScoreHUD.kt
    ui/components/PlaybackControls.kt
  ```
  Each file contains only: `package com.pianotiles.X`
- **How to verify**: Project tree shows all files.
- **Done when**: Full build succeeds (no missing references).

### 1.10 Create `NoteEvent.kt` data class
- **What**: This is the shared data type used by Layers 4, 5, and 6. Define it now:
  ```kotlin
  package com.pianotiles.midi

  data class NoteEvent(
      val midiNote: Int,          // 0–127 (piano range: 21–108)
      val startTimeSeconds: Double,
      val durationSeconds: Double,
      val velocity: Int,          // 0–127
      val trackIndex: Int         // for hand color assignment
  )
  ```
- **How to verify**: Compiles without error.
- **Done when**: Other files can `import com.pianotiles.midi.NoteEvent` without error.

### 1.11 Configure `AndroidManifest.xml`
- **What**: Add:
  ```xml
  <!-- USB OTG MIDI keyboard support -->
  <uses-feature android:name="android.hardware.usb.host" android:required="false"/>

  <!-- Keep screen on during playback -->
  <uses-permission android:name="android.permission.WAKE_LOCK"/>

  <!-- Lock to landscape for tablet gameplay -->
  <activity
      android:name=".MainActivity"
      android:screenOrientation="sensorLandscape"
      android:configChanges="orientation|screenSize|keyboardHidden"
      ...>
  ```
- **How to verify**: App launches in landscape mode on the tablet.
- **Done when**: OnePlus tablet shows the app locked to landscape orientation.

### 1.12 Create `assets/` folder structure
- **What**:
  ```
  app/src/main/assets/
    samples/piano/   ← (empty for now, .ogg files added in Layer 3)
    midi/            ← (empty for now, .mid files added in Layer 5)
  ```
  Add a placeholder `README.txt` in each folder so git tracks them:
  `samples/piano/README.txt`: "Piano samples go here: C1.ogg to C8.ogg at 48kHz mono"
  `midi/README.txt`: "Demo MIDI files go here"
- **How to verify**: Folders appear in `app/src/main/assets/`.
- **Done when**: `assets/` tree is visible in Android Studio.

### 1.13 Run on device — empty screen
- **What**: Replace `MainActivity.kt` with a minimal Compose screen that shows "Piano Tiles — Setup OK":
  ```kotlin
  class MainActivity : ComponentActivity() {
      override fun onCreate(savedInstanceState: Bundle?) {
          super.onCreate(savedInstanceState)
          setContent {
              Text("Piano Tiles — Setup OK", fontSize = 32.sp)
          }
      }
  }
  ```
- **How to verify**: App installs and launches on OnePlus tablet.
- **Done when**: Text "Piano Tiles — Setup OK" is visible on the tablet in landscape mode.

### 1.14 Verify USB OTG MIDI device detection (smoke test)
- **What**: In `MainActivity`, temporarily add:
  ```kotlin
  val usbManager = getSystemService(USB_SERVICE) as UsbManager
  val devices = usbManager.deviceList
  Log.d("SETUP", "USB devices: ${devices.keys.joinToString()}")
  ```
  Connect MIDI keyboard via USB OTG.
- **How to verify**: `adb logcat | grep SETUP` shows the MIDI keyboard's device name.
- **Done when**: Keyboard appears in logcat when connected. (Remove the log after verification.)

### 1.15 Verify Oboe loads (smoke test)
- **What**: In `AudioManager.kt`, add a minimal init that loads the JNI lib:
  ```kotlin
  class AudioManager {
      companion object {
          init { System.loadLibrary("piano_audio") }
      }
  }
  ```
  Instantiate `AudioManager()` in `MainActivity`.
- **How to verify**: App launches without `UnsatisfiedLinkError`.
- **Done when**: No crash on launch, logcat shows no JNI errors.

---

## Standalone Test

**Activity**: `MainActivity` (the default entry point)
**Steps**:
1. Connect OnePlus tablet via USB to development machine
2. `adb devices` — verify tablet is listed
3. Run app from Android Studio
4. Verify: landscape orientation, "Setup OK" text visible
5. Connect MIDI keyboard via USB OTG
6. `adb logcat | grep SETUP` — verify keyboard appears
7. Verify: no crashes in logcat for 30 seconds

**Expected result**: App is stable, USB OTG device detected, Oboe loads.

---

## Performance Target

N/A for this layer. Setup only.

---

## Integration Points

This layer provides:
- Package structure (`com.pianotiles.*`)
- Shared data type: `NoteEvent` (used by Layers 4, 5, 6)
- JNI library loading (`System.loadLibrary("piano_audio")`)
- `AndroidManifest.xml` with correct permissions and orientation

All other layers import from this foundation.
