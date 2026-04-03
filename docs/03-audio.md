# 03 — Audio Layer

## Purpose

Play piano note samples with minimum latency using the Oboe C++ audio library.
Handles: opening the audio stream, loading 88 piano samples, 20-voice polyphony mixing,
velocity scaling, note-on/note-off, and a JNI bridge so Kotlin can trigger notes.

Does NOT: know about MIDI files, game state, scoring, or rendering. It only responds
to `noteOn(midiNote, velocity)` and `noteOff(midiNote)` calls.

---

## Dependencies

- Layer 1 (project setup) — CMakeLists.txt, JNI stub, assets folder.
- No other layers.

---

## Tasks

### 3.1 Add piano samples to assets
- **What**: Source the Salamander Grand Piano sample set (open license).
  Download URL: https://freepats.zenvoid.org/Piano/SalamanderGrandPiano.tar.xz
  Convert to 88 individual `.ogg` files (one per MIDI note 21–108), mono, 48000Hz.
  Use the naming scheme: `A0.ogg`, `A#0.ogg`, `B0.ogg`, `C1.ogg`, ... `C8.ogg`.
  Place in `app/src/main/assets/samples/piano/`.
  If the full set is too large, use every 3rd note and let Oboe pitch-shift adjacent notes.
- **How to verify**: `assets/samples/piano/` contains at least 29 files (every 3rd note).
- **Done when**: All sample files are present, each ≤ 500KB, total ≤ 15MB.

### 3.2 Create `AudioEngine.h`
- **What**:
  ```cpp
  #pragma once
  #include <oboe/Oboe.h>
  #include <vector>
  #include <array>

  class AudioEngine : public oboe::AudioStreamDataCallback {
  public:
      bool openStream();
      void closeStream();
      void noteOn(int midiNote, int velocity);
      void noteOff(int midiNote);
      bool loadSample(int midiNote, const float* data, int numFrames);

      // Oboe callback
      oboe::DataCallbackResult onAudioReady(
          oboe::AudioStream* stream,
          void* audioData,
          int32_t numFrames) override;

  private:
      oboe::ManagedStream stream_;
      static constexpr int MAX_VOICES = 20;
      static constexpr int SAMPLE_RATE = 48000;
      // ... voice structs, sample buffers
  };
  ```
- **How to verify**: File compiles without errors.
- **Done when**: `#include "AudioEngine.h"` succeeds in `jni_bridge.cpp`.

### 3.3 Implement `openStream()` with Oboe builder
- **What**: In `AudioEngine.cpp`:
  ```cpp
  bool AudioEngine::openStream() {
      oboe::AudioStreamBuilder builder;
      builder.setDataCallback(this)
             ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
             ->setSharingMode(oboe::SharingMode::Exclusive)
             ->setFormat(oboe::AudioFormat::Float)
             ->setSampleRate(SAMPLE_RATE)
             ->setChannelCount(oboe::ChannelCount::Stereo);

      oboe::Result result = builder.openManagedStream(stream_);
      if (result != oboe::Result::OK) {
          __android_log_print(ANDROID_LOG_ERROR, "AUDIO",
              "Failed to open stream: %s", oboe::convertToText(result));
          return false;
      }
      __android_log_print(ANDROID_LOG_DEBUG, "AUDIO",
          "Stream opened. BufferSize: %d frames, Latency: %.1f ms",
          stream_->getBufferSizeInFrames(),
          stream_->getBufferSizeInFrames() * 1000.0 / SAMPLE_RATE);
      return true;
  }
  ```
- **How to verify**: `adb logcat | grep AUDIO` shows "Stream opened. BufferSize: X frames".
- **Done when**: Stream opens without error. Latency logged.

### 3.4 Set buffer size to 2× burst size
- **What**: After opening the stream, set buffer to 2 bursts:
  ```cpp
  stream_->requestStart();
  auto setBufferResult = stream_->setBufferSizeInFrames(
      stream_->getFramesPerBurst() * 2);
  __android_log_print(ANDROID_LOG_DEBUG, "AUDIO",
      "Buffer size: %d frames (burst: %d)",
      stream_->getBufferSizeInFrames(),
      stream_->getFramesPerBurst());
  ```
- **How to verify**: Logcat shows `Buffer size: N frames (burst: N/2)`. N ≤ 512.
- **Done when**: Buffer frames ≤ 512 at 48kHz (= ≤ 10.67ms buffering).

### 3.5 Implement silent data callback
- **What**:
  ```cpp
  oboe::DataCallbackResult AudioEngine::onAudioReady(
      oboe::AudioStream* stream, void* audioData, int32_t numFrames) {
      auto* outputBuffer = static_cast<float*>(audioData);
      memset(outputBuffer, 0, sizeof(float) * numFrames * 2);  // stereo silence
      return oboe::DataCallbackResult::Continue;
  }
  ```
- **How to verify**: App runs for 60 seconds, no audio underruns in logcat.
- **Done when**: `adb logcat | grep "Oboe\|AUDIO"` shows no underrun/xrun errors after 60s.

### 3.6 Load a single `.ogg` sample into memory (from JNI)
- **What**: In `jni_bridge.cpp`, add a JNI method that reads a `.ogg` file from
  the Android `AssetManager` and decodes it to raw `float[]` PCM using
  the Android NDK's `SLAndroidSimpleBufferQueue` or `AMediaCodec`:
  ```cpp
  JNIEXPORT jboolean JNICALL
  Java_com_pianotiles_audio_AudioManager_nativeLoadSample(
      JNIEnv* env, jobject, jint midiNote, jbyteArray oggData, jint length) {
      // Decode OGG to float[] PCM, call audioEngine.loadSample(midiNote, pcm, frames)
  }
  ```
  Use `dr_libs/dr_mp3.h` or `stb_vorbis.h` (header-only, include in cpp/).
  Alternatively use Android's `AMediaExtractor` + `AMediaCodec`.
- **How to verify**: Log "Loaded sample for note X: N frames" in logcat.
- **Done when**: C4 (note 60) sample loads, frame count logged (should be ~48000 frames for 1 second).

### 3.7 Load sample from Kotlin via `AudioManager.kt`
- **What**:
  ```kotlin
  class AudioManager(private val context: Context) {
      external fun nativeInit(): Boolean
      external fun nativeLoadSample(midiNote: Int, oggData: ByteArray, length: Int): Boolean
      external fun nativeNoteOn(midiNote: Int, velocity: Int)
      external fun nativeNoteOff(midiNote: Int)

      fun loadSample(midiNote: Int, assetPath: String): Boolean {
          val bytes = context.assets.open(assetPath).readBytes()
          return nativeLoadSample(midiNote, bytes, bytes.size)
      }

      companion object { init { System.loadLibrary("piano_audio") } }
  }
  ```
- **How to verify**: Call `audioManager.loadSample(60, "samples/piano/C4.ogg")` — returns `true`.
- **Done when**: Returns `true`, sample data in C++ memory, no JNI crash.

### 3.8 Implement `noteOn()` — play a loaded sample
- **What**: Add voice struct:
  ```cpp
  struct Voice {
      const float* sampleData = nullptr;
      int32_t totalFrames = 0;
      int32_t currentFrame = 0;
      float volume = 1.0f;
      bool active = false;
      int midiNote = -1;
  };
  std::array<Voice, MAX_VOICES> voices_;
  ```
  `noteOn()` finds an inactive voice, assigns sample data, sets volume from velocity.
- **How to verify**: N/A — no audio yet (callback still silent until task 3.9).
- **Done when**: `noteOn()` runs without crash, voice slot assigned.

### 3.9 Mix active voices in the audio callback
- **What**: Replace `memset(0)` in `onAudioReady` with actual mixing:
  ```cpp
  // Clear output
  memset(outputBuffer, 0, sizeof(float) * numFrames * 2);

  for (auto& voice : voices_) {
      if (!voice.active) continue;
      for (int i = 0; i < numFrames; i++) {
          if (voice.currentFrame >= voice.totalFrames) {
              voice.active = false;
              break;
          }
          float sample = voice.sampleData[voice.currentFrame++] * voice.volume;
          outputBuffer[i * 2]     += sample;  // left
          outputBuffer[i * 2 + 1] += sample;  // right (mono → stereo)
      }
  }
  ```
- **How to verify**: Call `nativeNoteOn(60, 100)` from a button in the test Activity — hear C4 piano note.
- **Done when**: Audible piano sound plays when button is pressed. No distortion.

### 3.10 Implement `noteOff()` — immediate or with release
- **What**: Simple version: mark voice inactive immediately.
  Better version: add a `releasing` flag and ramp volume to 0 over 200ms before deactivating.
  Implement the better version.
- **How to verify**: Hold button → sound plays. Release button → sound fades out in ~200ms.
- **Done when**: Note fades naturally, no click or pop on release.

### 3.11 Load all 88 samples on a background thread
- **What**: In `AudioManager.kt`, add:
  ```kotlin
  suspend fun loadAllSamples(
      onProgress: (Int, Int) -> Unit  // (loaded, total)
  ) = withContext(Dispatchers.IO) {
      val notes = (21..108).toList()
      notes.forEachIndexed { index, note ->
          val path = noteToAssetPath(note)   // e.g., "samples/piano/C4.ogg"
          loadSample(note, path)
          onProgress(index + 1, notes.size)
      }
  }
  ```
  If only every-3rd-note samples exist, load the closest available sample for each note
  and record the pitch shift ratio needed (for future pitch-shifting, currently play at normal pitch).
- **How to verify**: All 88 calls to `nativeLoadSample` succeed. Progress goes 1→88.
- **Done when**: All samples loaded, no OOM crash, logcat shows "All samples loaded".

### 3.12 Measure memory used by samples
- **What**: After loading all samples, log total bytes allocated:
  ```cpp
  __android_log_print(ANDROID_LOG_DEBUG, "AUDIO",
      "Total sample memory: %.1f MB", totalSampleBytes_ / 1024.0 / 1024.0);
  ```
- **How to verify**: Logcat shows memory usage.
- **Done when**: Total ≤ 50MB (OnePlus tablet has ≥ 8GB RAM, so this is fine).

### 3.13 Test polyphony — play 10 simultaneous notes
- **What**: In the test Activity, add a button "Play Chord". When pressed, call `noteOn` for notes 60, 62, 64, 65, 67, 69, 71, 72, 74, 76 (C major scale) simultaneously.
- **How to verify**: Hear 10 notes playing together without distortion.
- **Done when**: Chord sounds clean, no clipping, no dropped voices.

### 3.14 Test polyphony — play 20 simultaneous notes
- **What**: Add button "Play Full Chord" — call noteOn for 20 notes spanning 4 octaves simultaneously.
- **How to verify**: All 20 notes audible, no distortion.
- **Done when**: 20-note chord plays cleanly.

### 3.15 Implement voice stealing for > 20 voices
- **What**: If all 20 voice slots are active and a new `noteOn` arrives, steal the voice
  that has been playing the longest (simple FIFO policy):
  ```cpp
  // In noteOn(), if no free voice found:
  int oldestVoiceIdx = findOldestActiveVoice();
  voices_[oldestVoiceIdx] = newVoice;
  ```
- **How to verify**: Play 25 rapid noteOn calls — all succeed without crash, no audio artifacts.
- **Done when**: No crash with 25+ simultaneous notes.

### 3.16 Measure actual output latency
- **What**: After stream opens, log:
  ```cpp
  double latencyMs = (stream_->getBufferSizeInFrames() +
                      stream_->getFramesPerBurst()) * 1000.0 / SAMPLE_RATE;
  __android_log_print(ANDROID_LOG_INFO, "AUDIO",
      "Estimated output latency: %.1f ms", latencyMs);
  ```
- **How to verify**: Logcat shows latency value.
- **Done when**: Estimated latency ≤ 20ms on OnePlus tablet. If > 20ms, investigate buffer size.

### 3.17 Handle audio stream disconnect (headphones unplugged etc.)
- **What**: Implement `onErrorAfterClose` callback:
  ```cpp
  void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override {
      // Re-open stream on the new default output device
      openStream();
  }
  ```
- **How to verify**: Plug/unplug headphones while audio plays — no crash, audio resumes.
- **Done when**: Audio recovers automatically after device change.

### 3.18 Add master volume control
- **What**: Add `setMasterVolume(float volume)` (0.0–1.0) that scales all voice output.
  Applied as a multiplier in the audio callback.
- **How to verify**: Call `setMasterVolume(0.5f)` — audio is audibly quieter.
- **Done when**: Volume change takes effect within one callback period (< 5ms).

### 3.19 Create `AudioTestActivity` with on-screen piano keyboard
- **What**: A Compose Activity with:
  - Row of 12 buttons (one octave: C3–B3)
  - "Load Samples" button (shows progress)
  - "Play All" button (plays all 12 notes simultaneously)
  - Latency value displayed from logcat
- **How to verify**: Tap individual keys → hear correct notes.
- **Done when**: Each of 12 keys produces its correct pitch. No noticeable lag.

### 3.20 Startup: load samples asynchronously, show progress
- **What**: When `AudioManager` is initialized, immediately start loading samples
  in background. Expose `loadingProgress: StateFlow<Float>` (0.0–1.0) for the UI.
- **How to verify**: `AudioTestActivity` shows a loading progress bar that fills to 100%.
- **Done when**: Progress bar fills completely, then piano buttons become interactive.

---

## Standalone Test

**Activity**: `AudioTestActivity`

**Steps**:
1. Launch `AudioTestActivity`.
2. Wait for loading progress bar to reach 100%.
3. Tap C3, D3, E3, F3, G3, A3, B3 buttons one by one.
   → Each tap produces a distinct, recognizable piano pitch with no perceptible delay.
4. Tap "Play All" → hear a full C major chord, no distortion.
5. `adb logcat | grep AUDIO` → verify:
   - "Stream opened" message
   - Buffer size ≤ 512 frames
   - Estimated latency ≤ 20ms
   - "All samples loaded"
6. Plug in headphones → audio switches to headphones.
7. Unplug headphones → audio returns to speaker without crash.

**Expected result**: Clean piano sound, all 12 notes work, latency ≤ 20ms logged,
no crashes on device change.

---

## Performance Target

| Metric | Target |
|---|---|
| Output latency | ≤ 20ms (logged by Oboe) |
| Audio callback duration | ≤ 2ms (checked with systrace) |
| Sample loading time | ≤ 3 seconds for all 88 notes |
| Total sample memory | ≤ 50MB |
| Polyphony | 20 simultaneous notes, no distortion |

---

## Integration Points

`AudioManager` (Kotlin facade) exposes:
```kotlin
suspend fun loadAllSamples(onProgress: (Int, Int) -> Unit)
fun noteOn(midiNote: Int, velocity: Int)
fun noteOff(midiNote: Int)
fun setMasterVolume(volume: Float)
val loadingProgress: StateFlow<Float>
```

`AudioEngine` (C++) exposes to JNI:
```cpp
void noteOn(int midiNote, int velocity)
void noteOff(int midiNote)
bool openStream()
void closeStream()
```

No other layer calls audio functions directly. All audio goes through `AudioManager`.
