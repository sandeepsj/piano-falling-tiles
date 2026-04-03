# 05 — MIDI File Layer

## Purpose

Load a Standard MIDI File (`.mid`), parse it, and produce a sorted list of
`NoteEvent` objects with absolute timestamps in seconds. This is the data
source for the Game Engine's tile scheduler.

Handles: file picking (from device storage), bundled demo songs (from assets),
tick-to-seconds conversion (accounting for tempo changes), multi-track files,
and per-track color assignment (for hand differentiation).

Does NOT: play audio, render tiles, or manage game state.

---

## Dependencies

- Layer 1 (project setup) — `NoteEvent.kt`, `ktmidi` dependency, assets folder.
- No other layers.

---

## Tasks

### 5.1 Add demo MIDI files to assets
- **What**: Download two public-domain MIDI files:
  - `fur_elise.mid` (Beethoven — simple, good for testing)
  - `clair_de_lune.mid` (Debussy — more complex, tests polyphony)
  Place in `app/src/main/assets/midi/`.
  Sources: https://www.midiworld.com/classic.htm (public domain)
- **How to verify**: Files appear in `app/src/main/assets/midi/`.
- **Done when**: Both files present, each < 100KB.

### 5.2 Create `MidiFilePlayer.kt` skeleton
- **What**:
  ```kotlin
  class MidiFilePlayer {
      var events: List<NoteEvent> = emptyList()
          private set
      var durationSeconds: Double = 0.0
          private set
      var trackCount: Int = 0
          private set

      fun loadFromBytes(midiBytes: ByteArray): Boolean { /* task 5.3 */ }
      fun loadFromAssets(context: Context, assetPath: String): Boolean { /* task 5.4 */ }
  }
  ```
- **How to verify**: No compile errors.
- **Done when**: File compiles cleanly.

### 5.3 Parse MIDI bytes with ktmidi
- **What**:
  ```kotlin
  fun loadFromBytes(midiBytes: ByteArray): Boolean {
      return try {
          val music = Midi1Music()
          music.read(midiBytes.toList())

          trackCount = music.tracks.size
          Log.d("MIDI_FILE", "Tracks: $trackCount, Format: ${music.format}, " +
              "Ticks/Quarter: ${music.deltaTimeSpec}")

          events = extractEvents(music)
          durationSeconds = events.maxOfOrNull { it.startTimeSeconds + it.durationSeconds } ?: 0.0
          Log.d("MIDI_FILE", "Events: ${events.size}, Duration: ${durationSeconds}s")
          true
      } catch (e: Exception) {
          Log.e("MIDI_FILE", "Parse failed: ${e.message}")
          false
      }
  }
  ```
- **How to verify**: Load `fur_elise.mid` bytes → logcat shows track count, event count, duration.
- **Done when**: `fur_elise.mid` parses successfully. Event count > 100. Duration > 100s.

### 5.4 Load from assets
- **What**:
  ```kotlin
  fun loadFromAssets(context: Context, assetPath: String): Boolean {
      return try {
          val bytes = context.assets.open(assetPath).readBytes()
          loadFromBytes(bytes)
      } catch (e: IOException) {
          Log.e("MIDI_FILE", "Asset not found: $assetPath")
          false
      }
  }
  ```
- **How to verify**: Call `loadFromAssets(context, "midi/fur_elise.mid")` → returns `true`.
- **Done when**: Returns `true` for both demo files.

### 5.5 Convert MIDI ticks to absolute seconds
- **What**: MIDI time is in ticks. Converting to seconds requires:
  - `ticksPerQuarterNote` from the MIDI header
  - Tempo events (microseconds per quarter note) from Track 0
  - Accumulate time accounting for tempo changes

  ```kotlin
  private fun ticksToSeconds(tick: Long, tempoMap: List<TempoChange>): Double {
      // tempoMap: sorted list of (atTick, microsecondsPerBeat)
      var elapsedUs = 0.0
      var lastTick = 0L
      var currentTempo = 500_000.0  // default: 120 BPM

      for (change in tempoMap) {
          if (change.atTick >= tick) break
          elapsedUs += (change.atTick - lastTick) * currentTempo / ticksPerQuarterNote
          lastTick = change.atTick
          currentTempo = change.microsecondsPerBeat.toDouble()
      }
      elapsedUs += (tick - lastTick) * currentTempo / ticksPerQuarterNote
      return elapsedUs / 1_000_000.0
  }
  ```
- **How to verify**: For a 120 BPM file with no tempo changes, tick 480 (= 1 quarter note at 480 tpqn) → 0.5 seconds.
- **Done when**: Unit test passes: `ticksToSeconds(480, emptyList())` == 0.5 for 120 BPM, 480 tpqn.

### 5.6 Extract `NoteEvent` list from parsed MIDI
- **What**: Iterate all tracks. For each track, match NoteOn/NoteOff pairs to compute duration:
  ```kotlin
  private fun extractEvents(music: Midi1Music): List<NoteEvent> {
      val tempoMap = buildTempoMap(music)
      val events = mutableListOf<NoteEvent>()

      music.tracks.forEachIndexed { trackIdx, track ->
          val pendingNotes = mutableMapOf<Int, Pair<Long, Int>>()  // note → (startTick, velocity)
          var currentTick = 0L

          for (message in track.messages) {
              currentTick += message.deltaTime
              val ev = message.event

              when {
                  ev.eventType == MidiEventType.NOTE_ON && ev.value != 0 -> {
                      pendingNotes[ev.msb] = Pair(currentTick, ev.value)
                  }
                  ev.eventType == MidiEventType.NOTE_OFF ||
                  (ev.eventType == MidiEventType.NOTE_ON && ev.value == 0) -> {
                      pendingNotes.remove(ev.msb)?.let { (startTick, velocity) ->
                          events.add(NoteEvent(
                              midiNote = ev.msb,
                              startTimeSeconds = ticksToSeconds(startTick, tempoMap),
                              durationSeconds = ticksToSeconds(currentTick, tempoMap)
                                  - ticksToSeconds(startTick, tempoMap),
                              velocity = velocity,
                              trackIndex = trackIdx
                          ))
                      }
                  }
              }
          }
      }
      return events.sortedBy { it.startTimeSeconds }
  }
  ```
- **How to verify**: Log first 5 events from Für Elise. Verify they are in ascending time order, all durations > 0.
- **Done when**: Events are sorted by time, all have positive duration, notes in range 21–108.

### 5.7 Build tempo map
- **What**: Scan Track 0 for `META_TEMPO` events, build sorted list of `TempoChange(atTick, microsPerBeat)`:
  ```kotlin
  private fun buildTempoMap(music: Midi1Music): List<TempoChange> {
      val tempos = mutableListOf(TempoChange(0L, 500_000))  // default 120 BPM
      var tick = 0L
      music.tracks[0].messages.forEach { msg ->
          tick += msg.deltaTime
          if (msg.event.eventType == MidiEventType.META &&
              msg.event.metaType == MidiMetaType.TEMPO) {
              tempos.add(TempoChange(tick, msg.event.tempo))
          }
      }
      return tempos.sortedBy { it.atTick }
  }
  ```
- **How to verify**: Log tempo map for `clair_de_lune.mid` — Debussy has multiple tempo changes.
- **Done when**: Tempo map has ≥ 1 entry (default 120 BPM). Clair de Lune has > 1 entry.

### 5.8 File picker — open .mid from device storage
- **What**: Create `MidiFilePicker.kt` using `ActivityResultContracts.OpenDocument`:
  ```kotlin
  class MidiFilePicker(registry: ActivityResultRegistry, callback: (ByteArray?) -> Unit) {
      private val launcher = registry.register(
          "midi_picker",
          ActivityResultContracts.OpenDocument()
      ) { uri ->
          // Read bytes from uri using contentResolver
          callback(uri?.let { readBytes(it) })
      }
      fun launch() = launcher.launch(arrayOf("audio/midi", "audio/x-midi", "*/*"))
  }
  ```
- **How to verify**: Tap "Open File" in test Activity → Android file picker opens → select a .mid → bytes returned.
- **Done when**: Bytes returned from file picker, `loadFromBytes()` succeeds on user-selected file.

### 5.9 Validate MIDI file (basic sanity checks)
- **What**: After parsing, validate:
  - At least 1 event exists
  - All `midiNote` values in range 21–108 (piano range)
  - Duration > 0 seconds
  - No negative timestamps
  ```kotlin
  fun validate(): List<String> {
      val errors = mutableListOf<String>()
      if (events.isEmpty()) errors.add("No notes found")
      if (events.any { it.midiNote !in 21..108 }) errors.add("Notes outside piano range")
      if (events.any { it.durationSeconds <= 0 }) errors.add("Zero-duration notes found")
      if (events.any { it.startTimeSeconds < 0 }) errors.add("Negative timestamps")
      return errors
  }
  ```
- **How to verify**: Validate both demo files → empty error list for valid files.
- **Done when**: Both demo files pass validation with no errors.

### 5.10 Track color assignment
- **What**: Assign a display color to each track (for tile coloring in Layer 2):
  ```kotlin
  val trackColors = listOf(
      0xFF4FC3F7.toInt(),  // light blue  (right hand)
      0xFF81C784.toInt(),  // light green (left hand)
      0xFFFFB74D.toInt(),  // orange
      0xFFBA68C8.toInt(),  // purple
  )

  fun colorForTrack(trackIndex: Int): Int =
      trackColors[trackIndex % trackColors.size]
  ```
- **How to verify**: `colorForTrack(0)` returns blue, `colorForTrack(1)` returns green.
- **Done when**: Returns a valid ARGB color for any track index.

### 5.11 Log summary of loaded song
- **What**: After successful load, log a human-readable summary:
  ```kotlin
  fun logSummary() {
      Log.i("MIDI_FILE", """
          Song loaded:
            Tracks: $trackCount
            Total events: ${events.size}
            Duration: ${durationSeconds.toInt() / 60}m ${durationSeconds.toInt() % 60}s
            Note range: MIDI ${events.minOf { it.midiNote }}–${events.maxOf { it.midiNote }}
            Max simultaneous notes: ${computeMaxPolyphony()}
            Tempo changes: ...
      """.trimIndent())
  }
  ```
- **How to verify**: Logcat shows well-formatted summary for Für Elise.
- **Done when**: Summary logged correctly. Max polyphony ≤ 20 for Für Elise (it is).

### 5.12 Handle edge cases — format 0, format 1, format 2
- **What**: MIDI format 0: all events in one track. Format 1: multi-track (most common).
  Format 2: rarely used. Ensure `extractEvents()` handles all three.
  Test: load a Format 0 file and verify events are extracted.
- **How to verify**: Parse a Format 0 MIDI file → events correctly extracted.
- **Done when**: Format 0 and Format 1 both produce correct event lists.

---

## Standalone Test

**Activity**: `MidiParserActivity`

**UI**:
- Two buttons: "Load Für Elise" and "Load Clair de Lune"
- "Open File..." button (file picker)
- Song summary card (tracks, events, duration, note range)
- Scrollable list of first 50 events (note name, time, duration, velocity, track)

**Steps**:
1. Launch `MidiParserActivity`.
2. Tap "Load Für Elise".
3. Verify summary: ~2 tracks, ~500+ events, ~3:30 duration.
4. Verify first event: near time 0.0s, valid note number.
5. Verify events are in ascending time order in the list.
6. Tap "Open File..." → pick a .mid file from Downloads.
7. Verify: summary shown for user's file.
8. Verify: no crash on any valid .mid file.

**Expected result**: Both demo songs parse correctly. User files parse without crash.
Event list is sorted, timestamps are positive, durations are positive.

---

## Performance Target

| Metric | Target |
|---|---|
| Parse time (Für Elise ~70KB) | < 100ms |
| Parse time (large file ~500KB) | < 500ms |
| Memory per loaded song | < 5MB |

---

## Integration Points

`MidiFilePlayer` exposes:
```kotlin
val events: List<NoteEvent>          // sorted by startTimeSeconds
val durationSeconds: Double
val trackCount: Int
fun loadFromBytes(bytes: ByteArray): Boolean
fun loadFromAssets(context: Context, path: String): Boolean
fun colorForTrack(trackIndex: Int): Int
fun validate(): List<String>
```

Layer 6 (Game Engine) reads `events` and `durationSeconds`.
Layer 2 (Rendering) reads `colorForTrack()` for tile colors.
