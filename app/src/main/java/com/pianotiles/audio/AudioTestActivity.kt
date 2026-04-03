package com.pianotiles.audio

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.view.WindowManager

/**
 * Standalone test for Layer 3 — Audio.
 * Shows two rows of piano buttons (C3–B4). Touch to play, release to stop.
 *
 * Verification checklist:
 *   [ ] logcat AUDIO: "Stream ready — api=AAudio sr=48000 bufFrames=... latencyMs=~X"
 *   [ ] latencyMs ≤ 20ms (target ≤ 10ms on Snapdragon 8 Gen 3)
 *   [ ] Pressing a button plays a clear sine tone
 *   [ ] Releasing stops it (with short 250ms decay)
 *   [ ] Holding all buttons in a row plays a 12-note chord without glitches
 *   [ ] Rapidly tapping same note many times causes no crash or silence
 */
class AudioTestActivity : Activity() {

    // Two octaves: C3 (48) through B4 (71)
    private val notes = (48..71).toList()

    // Track which pointer owns which note (multi-touch)
    private val pointerToNote = mutableMapOf<Int, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0A1A"))
            gravity = Gravity.CENTER
        }

        // Title
        val title = TextView(this).apply {
            text = "Audio Layer 3 Test — Touch keys to play"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 24)
        }
        root.addView(title, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Two rows of 12 buttons each
        for (octaveStart in listOf(48, 60)) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(8, 8, 8, 8)
            }
            for (note in octaveStart until octaveStart + 12) {
                row.addView(makeNoteButton(note))
            }
            root.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        // Chord test button
        val chordBtn = Button(this).apply {
            text = "C Major Chord (C-E-G)"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1E5799"))
            setPadding(24, 16, 24, 16)
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        listOf(60, 64, 67).forEach { AudioManager.nativeNoteOn(it, 100) }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        listOf(60, 64, 67).forEach { AudioManager.nativeNoteOff(it) }
                    }
                }
                true
            }
        }
        val btnParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 24
        }
        root.addView(chordBtn, btnParams)

        setContentView(root)

        // Init audio engine
        val ok = AudioManager.nativeInit()
        Log.d("AUDIO_TEST", "AudioManager.nativeInit() = $ok")
    }

    private fun makeNoteButton(midiNote: Int): View {
        val noteName = NOTE_NAMES[midiNote % 12]
        val isBlack  = noteName.contains("#")
        val octave   = (midiNote / 12) - 1

        return Button(this).apply {
            text = "$noteName$octave"
            textSize = 11f
            setTextColor(if (isBlack) Color.WHITE else Color.BLACK)
            setBackgroundColor(if (isBlack) Color.parseColor("#222222") else Color.parseColor("#F0F0F0"))

            val w = 110   // px — adjust if buttons look cramped
            val h = 140
            val params = LinearLayout.LayoutParams(w, h).apply { setMargins(2, 2, 2, 2) }
            layoutParams = params

            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        setBackgroundColor(Color.parseColor("#4FC3F7"))
                        AudioManager.nativeNoteOn(midiNote, 100)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        setBackgroundColor(
                            if (isBlack) Color.parseColor("#222222") else Color.parseColor("#F0F0F0"))
                        AudioManager.nativeNoteOff(midiNote)
                    }
                }
                true
            }
        }
    }

    companion object {
        private val NOTE_NAMES = arrayOf(
            "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
        )
    }
}
