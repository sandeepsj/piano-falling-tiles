package com.pianotiles.midi

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Standalone test for Layer 5 — MIDI File Parser.
 *
 * Loads assets/test.mid if present, otherwise uses a built-in C-major scale.
 * Displays the parsed NoteEvent list and timing summary.
 *
 * Verification checklist:
 *   [ ] "Parsed X notes from Y tracks in Z.Zs" shown on screen
 *   [ ] First few events show correct note names and start times
 *   [ ] Timing is plausible (e.g. 8 notes at 120 BPM → each ~0.5s apart)
 *   [ ] No crash on format-0 and format-1 test files
 */
class MidiParserActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0A1A"))
        }

        val title = TextView(this).apply {
            text = "MIDI Parser — Layer 5 Test"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(16, 32, 16, 16)
        }
        root.addView(title, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val resultView = TextView(this).apply {
            setTextColor(Color.parseColor("#4FC3F7"))
            textSize = 13f
            setPadding(24, 12, 24, 12)
            setBackgroundColor(Color.parseColor("#0D0D20"))
        }
        val scroll = ScrollView(this)
        scroll.addView(resultView)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)

        // Parse on background thread — ktmidi read can block
        Thread {
            val (label, bytes) = loadMidiBytes()
            val sb = StringBuilder()
            sb.appendLine("Source: $label\n")

            try {
                val result = MidiFileParser.parse(bytes)
                sb.appendLine("Parsed ${result.noteCount} notes from " +
                              "${result.trackCount} tracks in " +
                              "%.2fs".format(result.durationSeconds))
                sb.appendLine()

                // Show first 60 events
                result.events.take(60).forEach { ev ->
                    val name = noteName(ev.midiNote)
                    sb.appendLine("%6.3fs  %-4s  dur=%.3fs  vel=%-3d  trk=%d".format(
                        ev.startTimeSeconds, name, ev.durationSeconds, ev.velocity, ev.trackIndex))
                }
                if (result.noteCount > 60) sb.appendLine("... (${result.noteCount - 60} more)")

                Log.d("MIDI_PARSER", "Parsed ${result.noteCount} notes, " +
                      "duration=%.2fs".format(result.durationSeconds))

            } catch (e: Exception) {
                sb.appendLine("ERROR: ${e.message}")
                Log.e("MIDI_PARSER", "Parse failed", e)
            }

            runOnUiThread { resultView.text = sb }
        }.start()
    }

    private fun loadMidiBytes(): Pair<String, ByteArray> {
        return try {
            val bytes = assets.open("test.mid").readBytes()
            "assets/test.mid (${bytes.size} bytes)" to bytes
        } catch (_: Exception) {
            "Built-in C-major scale (fallback)" to generateCMajorScale()
        }
    }

    /**
     * Generates a minimal SMF format-0 file: C-major scale at 120 BPM.
     * C4 D4 E4 F4 G4 A4 B4 C5, each one quarter note, 96 ticks/beat.
     */
    private fun generateCMajorScale(): ByteArray {
        // Track data (75 bytes):
        //   tempo meta (7) + 8 note pairs (64) + end-of-track (4)
        val notes = intArrayOf(60, 62, 64, 65, 67, 69, 71, 72)  // C4–C5
        val trackData = mutableListOf<Byte>()

        // Tempo: 500000 µs/beat = 120 BPM
        trackData += listOf<Byte>(0x00, 0xFF.toByte(), 0x51, 0x03, 0x07, 0xA1.toByte(), 0x20)

        for (note in notes) {
            // Note On: delta=0
            trackData += listOf<Byte>(0x00, 0x90.toByte(), note.toByte(), 0x64)
            // Note Off: delta=96 (one quarter note), 96 < 128 so single var-length byte
            trackData += listOf<Byte>(0x60, 0x80.toByte(), note.toByte(), 0x00)
        }

        // End of track
        trackData += listOf<Byte>(0x00, 0xFF.toByte(), 0x2F, 0x00)

        val len = trackData.size
        return byteArrayOf(
            // MThd
            0x4D, 0x54, 0x68, 0x64.toByte(),
            0x00, 0x00, 0x00, 0x06,
            0x00, 0x00,              // format 0
            0x00, 0x01,              // 1 track
            0x00, 0x60,              // 96 ticks/beat
            // MTrk
            0x4D, 0x54, 0x72, 0x6B.toByte(),
            ((len shr 24) and 0xFF).toByte(),
            ((len shr 16) and 0xFF).toByte(),
            ((len shr  8) and 0xFF).toByte(),
            ( len         and 0xFF).toByte()
        ) + trackData.toByteArray()
    }

    private fun noteName(note: Int): String {
        val names = arrayOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
        return "${names[note % 12]}${(note / 12) - 1}"
    }
}
