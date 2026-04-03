# 04 — MIDI Input Layer

## Purpose

Detect and read from a MIDI keyboard connected via USB OTG cable. Converts raw
MIDI bytes into typed `NoteEvent` objects and delivers them to registered listeners.

Handles: USB device connect/disconnect, permission requests, multiple MIDI devices,
raw MIDI byte parsing (NoteOn, NoteOff, sustain pedal CC64).

Does NOT: play audio, update game state, parse MIDI files, or render anything.
It only produces a stream of `NoteEvent` objects for whoever is listening.

---

## Dependencies

- Layer 1 (project setup) — `NoteEvent.kt`, `AndroidManifest.xml` with USB feature.
- No other layers.

---

## Tasks

### 4.1 Add USB MIDI permission to `AndroidManifest.xml`
- **What**: Add inside `<application>`:
  ```xml
  <receiver android:name=".midi.UsbMidiReceiver"
      android:exported="true">
      <intent-filter>
          <action android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED"/>
      </intent-filter>
      <meta-data
          android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED"
          android:resource="@xml/device_filter"/>
  </receiver>
  ```
  Create `res/xml/device_filter.xml`:
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <resources>
      <usb-device />  <!-- match all USB devices -->
  </resources>
  ```
- **How to verify**: Connect MIDI keyboard via USB OTG — Android shows permission dialog automatically.
- **Done when**: Permission dialog appears when keyboard is plugged in.

### 4.2 Create `UsbMidiReceiver.kt` — BroadcastReceiver
- **What**:
  ```kotlin
  class UsbMidiReceiver : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
          if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
              val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
              Log.d("MIDI_INPUT", "USB device attached: ${device?.deviceName}")
              // Forward to MidiInputManager
              MidiInputManager.getInstance(context).onDeviceAttached(device)
          }
      }
  }
  ```
- **How to verify**: Plug in keyboard → `adb logcat | grep MIDI_INPUT` shows device name.
- **Done when**: Device name logged on plug-in.

### 4.3 Create `MidiInputManager.kt` skeleton
- **What**:
  ```kotlin
  class MidiInputManager private constructor(private val context: Context) {
      private val midiManager = context.getSystemService(Context.MIDI_SERVICE) as MidiManager
      private var openDevice: MidiDevice? = null
      private val listeners = mutableListOf<(NoteEvent) -> Unit>()

      fun addListener(listener: (NoteEvent) -> Unit) { listeners.add(listener) }
      fun removeListener(listener: (NoteEvent) -> Unit) { listeners.remove(listener) }

      fun onDeviceAttached(usbDevice: UsbDevice?) { /* task 4.4 */ }
      fun onDeviceDetached() { /* task 4.8 */ }

      companion object {
          @Volatile private var instance: MidiInputManager? = null
          fun getInstance(context: Context) = instance ?: synchronized(this) {
              instance ?: MidiInputManager(context.applicationContext).also { instance = it }
          }
      }
  }
  ```
- **How to verify**: No compile errors.
- **Done when**: File compiles, singleton accessible from any Activity.

### 4.4 Request USB permission and open the MIDI device
- **What**: In `onDeviceAttached()`:
  ```kotlin
  fun onDeviceAttached(usbDevice: UsbDevice?) {
      usbDevice ?: return
      val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

      if (!usbManager.hasPermission(usbDevice)) {
          val permIntent = PendingIntent.getBroadcast(
              context, 0,
              Intent(ACTION_USB_PERMISSION),
              PendingIntent.FLAG_IMMUTABLE
          )
          usbManager.requestPermission(usbDevice, permIntent)
          return
      }
      openMidiDevice(usbDevice)
  }
  ```
  Register a second `BroadcastReceiver` for `ACTION_USB_PERMISSION` that calls `openMidiDevice()` on grant.
- **How to verify**: Permission dialog appears. After granting, logcat shows "Permission granted".
- **Done when**: `openMidiDevice()` is called after user grants permission.

### 4.5 Open MIDI input port
- **What**:
  ```kotlin
  private fun openMidiDevice(usbDevice: UsbDevice) {
      midiManager.openDevice(
          midiManager.devices.first { it.properties.getString(MidiDeviceInfo.PROPERTY_NAME)
              ?.contains(usbDevice.deviceName) == true },
          { device ->
              openDevice = device
              val port = device.openOutputPort(0)   // MIDI output port (sends TO us)
              port.connect(MidiFramer(MidiByteParser { event -> dispatchEvent(event) }))
              Log.d("MIDI_INPUT", "MIDI port opened: ${device.info}")
          },
          null
      )
  }
  ```
  Note: "output port" from device's perspective = input from app's perspective.
- **How to verify**: Logcat shows "MIDI port opened" with device info.
- **Done when**: Port opens without error.

### 4.6 Parse raw MIDI bytes — NoteOn, NoteOff
- **What**: Create `MidiByteParser.kt`:
  ```kotlin
  class MidiByteParser(private val onEvent: (NoteEvent) -> Unit) : MidiReceiver() {
      override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
          if (count < 3) return
          val status = msg[offset].toInt() and 0xF0
          val note = msg[offset + 1].toInt() and 0x7F
          val velocity = msg[offset + 2].toInt() and 0x7F

          when (status) {
              0x90 -> if (velocity > 0) {
                  // NoteOn
                  onEvent(NoteEvent(note, 0.0, 0.0, velocity, 0))
              } else {
                  // NoteOn with velocity 0 = NoteOff
                  onEvent(NoteEvent(note, 0.0, 0.0, 0, 0))
              }
              0x80 -> onEvent(NoteEvent(note, 0.0, 0.0, 0, 0))  // NoteOff
          }
      }
  }
  ```
  Note: `startTimeSeconds` and `durationSeconds` are 0 for live input; only `midiNote` and `velocity` matter.
- **How to verify**: Press C4 on keyboard → logcat shows NoteEvent(midiNote=60, velocity=X).
- **Done when**: Every key press produces a correctly parsed `NoteEvent` in logcat.

### 4.7 Dispatch `NoteEvent` to all listeners
- **What**: In `dispatchEvent()`:
  ```kotlin
  private fun dispatchEvent(event: NoteEvent) {
      Log.d("MIDI_INPUT", "Note: ${event.midiNote} vel: ${event.velocity}")
      listeners.forEach { it(event) }
  }
  ```
- **How to verify**: Register a test listener that logs the event. Press key → event logged.
- **Done when**: Listener receives every keypress within 5ms of physical key press.

### 4.8 Handle device disconnect gracefully
- **What**: Register for `ACTION_USB_DEVICE_DETACHED`:
  ```kotlin
  fun onDeviceDetached() {
      openDevice?.close()
      openDevice = null
      Log.d("MIDI_INPUT", "MIDI device disconnected")
  }
  ```
- **How to verify**: Unplug keyboard → logcat shows "MIDI device disconnected". No crash.
- **Done when**: App continues running after disconnect. Reconnecting works.

### 4.9 Handle CC64 (sustain pedal)
- **What**: In `MidiByteParser`, add:
  ```kotlin
  0xB0 -> {
      val controller = msg[offset + 1].toInt() and 0x7F
      val value = msg[offset + 2].toInt() and 0x7F
      if (controller == 64) {
          onSustainChange(value >= 64)  // true = pedal down
      }
  }
  ```
  Add `onSustainChange: (Boolean) -> Unit` callback to parser.
- **How to verify**: Press sustain pedal → logcat shows "Sustain: true/false".
- **Done when**: Sustain pedal state correctly toggled.

### 4.10 Handle multiple MIDI devices
- **What**: Change `openDevice: MidiDevice?` to `openDevices: MutableList<MidiDevice>`.
  When a device is attached, add it to the list. Detach removes from list.
  All connected devices contribute to the same event stream.
- **How to verify**: Connect two MIDI keyboards → events from both appear in logcat.
- **Done when**: Both devices work simultaneously without interference.

### 4.11 Show connected device name in UI (for test Activity)
- **What**: Expose `connectedDeviceName: StateFlow<String?>` from `MidiInputManager`.
  Updated when devices connect/disconnect.
- **How to verify**: Connect keyboard → `connectedDeviceName.value` = keyboard's name.
- **Done when**: StateFlow updates correctly on connect/disconnect.

### 4.12 MIDI timestamp jitter compensation
- **What**: Android MIDI API provides a hardware timestamp (nanoseconds) with each message.
  Store this timestamp in an extended version of the parse callback for use by hit detection
  (Layer 6). For now, log the jitter: measure delta between MIDI timestamp and `System.nanoTime()`.
  ```kotlin
  val jitterMs = (System.nanoTime() - timestamp) / 1_000_000.0
  Log.d("MIDI_INPUT", "MIDI jitter: ${jitterMs}ms")
  ```
- **How to verify**: Play scales rapidly → logcat shows jitter values. All should be < 5ms.
- **Done when**: Jitter consistently < 5ms for USB MIDI (wired is very low latency).

---

## Standalone Test

**Activity**: `MidiMonitorActivity`

**Setup**: A Compose screen showing:
- "Connected Device: [name or None]"
- A scrolling list of recent MIDI events (last 20)
- Each event: note name, octave, velocity, timestamp
- Sustain pedal indicator (ON/OFF)

**Steps**:
1. Launch `MidiMonitorActivity`.
2. Verify: "Connected Device: None" shown.
3. Plug in MIDI keyboard via USB OTG.
4. Verify: permission dialog appears.
5. Grant permission.
6. Verify: device name appears in the screen.
7. Press C4 → verify event appears: "C4 (60) vel: 80".
8. Press and hold 10 keys simultaneously → verify all 10 events appear.
9. Press sustain pedal → verify sustain indicator changes.
10. Unplug keyboard → verify "Connected Device: None", no crash.
11. Replug keyboard → verify reconnects and events work again.

**Expected result**: All key presses logged correctly, reconnection works, no crashes.

---

## Performance Target

| Metric | Target |
|---|---|
| MIDI event delivery (USB) | < 5ms from key press to listener callback |
| Jitter | < 5ms (USB MIDI is wired, very consistent) |
| Memory overhead | < 1MB for this layer |

---

## Integration Points

`MidiInputManager` exposes:
```kotlin
fun addListener(listener: (NoteEvent) -> Unit)
fun removeListener(listener: (NoteEvent) -> Unit)
val connectedDeviceName: StateFlow<String?>
val sustainPedalDown: StateFlow<Boolean>
```

Layer 3 (Audio) and Layer 6 (Game Engine) both register as listeners.
`NoteEvent.velocity == 0` means note-off.
`NoteEvent.startTimeSeconds` is always 0 for live input (not used for scheduling).
