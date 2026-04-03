package com.pianotiles.audio

/**
 * JNI bridge to the native Oboe audio engine.
 * Use as a singleton — call init() once before any noteOn/noteOff calls.
 */
object AudioManager {

    init {
        System.loadLibrary("piano_audio")
    }

    /** Opens the Oboe stream. Returns true on success. */
    external fun nativeInit(): Boolean

    /** Trigger a note (velocity 0–127). */
    external fun nativeNoteOn(midiNote: Int, velocity: Int)

    /** Release a note. */
    external fun nativeNoteOff(midiNote: Int)

    /** Load a decoded PCM sample for a specific MIDI note (Layer 5+). */
    external fun nativeLoadSample(midiNote: Int, oggData: ByteArray, length: Int): Boolean
}
