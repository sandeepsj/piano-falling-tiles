package com.pianotiles.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Listens for MIDI note events from the first USB MIDI device found.
 * Call start() in Activity.onResume() and stop() in Activity.onPause().
 *
 * Threading: MidiReceiver callbacks arrive on a dedicated MIDI thread.
 * NoteListener callbacks are dispatched on that same thread — keep them fast.
 */
class MidiInputManager(private val context: Context) {

    interface NoteListener {
        fun onNoteOn(midiNote: Int, velocity: Int)
        fun onNoteOff(midiNote: Int)
    }

    private val midiManager = context.getSystemService(Context.MIDI_SERVICE) as MidiManager
    private val mainHandler  = Handler(Looper.getMainLooper())

    @Volatile private var openDevice:  MidiDevice?     = null
    @Volatile private var outputPort:  MidiOutputPort? = null
    @Volatile private var noteListener: NoteListener?  = null

    /** Invoked on the main thread when a MIDI device is successfully opened. */
    var onDeviceConnected: ((name: String) -> Unit)? = null
    /** Invoked on the main thread when the current MIDI device is closed. */
    var onDeviceDisconnected: (() -> Unit)? = null

    fun setNoteListener(listener: NoteListener?) { noteListener = listener }

    /** Call from Activity.onResume() or after permissions are granted. */
    fun start() {
        midiManager.registerDeviceCallback(deviceCallback, mainHandler)
        // Scan devices already connected before we registered
        midiManager.devices.forEach { tryOpenDevice(it) }
    }

    /** Call from Activity.onPause() or onDestroy(). */
    fun stop() {
        midiManager.unregisterDeviceCallback(deviceCallback)
        closeCurrentDevice()
    }

    // ─────────────────────────────────────────────────────────────────────
    private fun tryOpenDevice(info: MidiDeviceInfo) {
        if (openDevice != null) return          // already have one
        if (info.outputPortCount == 0) return   // no MIDI output (e.g. USB audio only)

        midiManager.openDevice(info, { device ->
            if (device == null) {
                Log.e("MIDI_INPUT", "openDevice returned null for ${info.id}")
                return@openDevice
            }
            val port = device.openOutputPort(0)
            if (port == null) {
                Log.e("MIDI_INPUT", "openOutputPort(0) returned null")
                device.close()
                return@openDevice
            }
            port.connect(midiReceiver)
            openDevice = device
            outputPort = port

            val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "unknown"
            Log.d("MIDI_INPUT", "Opened: $name (id=${info.id}, outputPorts=${info.outputPortCount})")
            mainHandler.post { onDeviceConnected?.invoke(name) }
        }, mainHandler)
    }

    private fun closeCurrentDevice() {
        if (openDevice == null) return
        outputPort?.close()
        openDevice?.close()
        outputPort = null
        openDevice = null
        mainHandler.post { onDeviceDisconnected?.invoke() }
    }

    // ─────────────────────────────────────────────────────────────────────
    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) {
            Log.d("MIDI_INPUT", "Device added: id=${device.id} outPorts=${device.outputPortCount}")
            tryOpenDevice(device)
        }
        override fun onDeviceRemoved(device: MidiDeviceInfo) {
            Log.d("MIDI_INPUT", "Device removed: id=${device.id}")
            closeCurrentDevice()
        }
    }

    // Runs on the MIDI thread — keep fast, no allocations
    private val midiReceiver = object : MidiReceiver() {
        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            var i = offset
            val end = offset + count
            while (i < end) {
                val status   = msg[i].toInt() and 0xFF
                val type     = status and 0xF0
                // Skip non-voice messages (SysEx 0xF0, clock 0xF8, etc.)
                if (status and 0x80 == 0) { i++; continue }

                val bytesNeeded = when (type) {
                    0x80, 0x90, 0xA0, 0xB0, 0xE0 -> 3  // 2 data bytes
                    0xC0, 0xD0                    -> 2  // 1 data byte
                    else                          -> { i++; continue }
                }
                if (i + bytesNeeded > end) break  // incomplete message, drop

                val note     = msg[i + 1].toInt() and 0x7F
                val velocity = if (bytesNeeded >= 3) msg[i + 2].toInt() and 0x7F else 0

                when (type) {
                    0x90 -> if (velocity > 0) noteListener?.onNoteOn(note, velocity)
                            else              noteListener?.onNoteOff(note)
                    0x80 -> noteListener?.onNoteOff(note)
                }
                i += bytesNeeded
            }
        }
    }
}
