package com.pianotiles.midi

/**
 * Shared data type representing a single piano note.
 * Used by: MidiInputManager (live input), MidiFilePlayer (file playback),
 *           GameEngine (hit detection), TileScheduler (tile positions).
 *
 * For LIVE input: startTimeSeconds = 0, durationSeconds = 0, velocity = 0 means note-off.
 * For FILE playback: all fields populated from parsed MIDI.
 */
data class NoteEvent(
    val midiNote: Int,              // MIDI note number 0–127 (piano: 21–108)
    val startTimeSeconds: Double,   // absolute time in song (0.0 for live input)
    val durationSeconds: Double,    // note length (0.0 for live input)
    val velocity: Int,              // 0–127 (0 = note-off for live input)
    val trackIndex: Int             // MIDI track index, used for hand color assignment
)
