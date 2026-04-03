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
import com.pianotiles.audio.AudioManager

/**
 * Standalone test for Layer 4 — USB OTG MIDI Input.
 * Connect MIDI keyboard, play notes, verify logcat + on-screen log.
 *
 * Verification checklist:
 *   [ ] logcat MIDI_INPUT: "Opened: <keyboard name>"
 *   [ ] Each key press shows "Note ON  C4 vel=xx" in the on-screen log
 *   [ ] Each key release shows "Note OFF C4"
 *   [ ] You hear the note through the speaker (AudioManager connected)
 *   [ ] Unplug keyboard: logcat shows "Device removed"
 *   [ ] Replug keyboard: logcat shows "Opened: <name>" again
 */
class MidiMonitorActivity : Activity() {

    private lateinit var midiInput: MidiInputManager
    private lateinit var logView: TextView
    private lateinit var statusView: TextView
    private var logLines = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0A1A"))
        }

        val title = TextView(this).apply {
            text = "MIDI Monitor — Layer 4\nConnect USB MIDI keyboard and play"
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(16, 32, 16, 8)
        }
        root.addView(title, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        statusView = TextView(this).apply {
            text = "Scanning for MIDI devices..."
            setTextColor(Color.parseColor("#FFB74D"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(16, 8, 16, 16)
        }
        root.addView(statusView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        logView = TextView(this).apply {
            setTextColor(Color.parseColor("#4FC3F7"))
            textSize = 13f
            setPadding(24, 12, 24, 12)
            setBackgroundColor(Color.parseColor("#0D0D20"))
            fontFeatureSettings = "tnum"
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0D0D20"))
        }
        scroll.addView(logView)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)

        // Init audio so played notes are audible
        AudioManager.nativeInit()

        midiInput = MidiInputManager(this)
        midiInput.setNoteListener(object : MidiInputManager.NoteListener {
            override fun onNoteOn(midiNote: Int, velocity: Int) {
                AudioManager.nativeNoteOn(midiNote, velocity)
                val name = noteName(midiNote)
                Log.d("MIDI_INPUT", "Note ON  $name ($midiNote) vel=$velocity")
                appendLog("▶ ON   $name  vel=$velocity")
            }
            override fun onNoteOff(midiNote: Int) {
                AudioManager.nativeNoteOff(midiNote)
                val name = noteName(midiNote)
                Log.d("MIDI_INPUT", "Note OFF $name ($midiNote)")
                appendLog("■ OFF  $name")
            }
        })
    }

    override fun onResume() {
        super.onResume()
        midiInput.start()
    }

    override fun onPause() {
        midiInput.stop()
        super.onPause()
    }

    private fun appendLog(line: String) {
        runOnUiThread {
            logLines++
            logView.append("$line\n")
            // Keep log from growing unbounded
            if (logLines > 200) {
                val text = logView.text.toString()
                logView.text = text.substring(text.indexOf('\n') + 1)
                logLines--
            }
        }
    }

    private fun noteName(note: Int): String {
        val names = arrayOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
        return "${names[note % 12]}${(note / 12) - 1}"
    }
}
