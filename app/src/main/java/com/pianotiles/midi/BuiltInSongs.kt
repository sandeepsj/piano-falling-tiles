package com.pianotiles.midi

/**
 * Hard-coded note sequences for built-in demo songs.
 * Uses NoteEvent directly — no MIDI file parsing required.
 *
 * 120 BPM: quarter note = 0.5 s, half note = 1.0 s.
 * Note durations are slightly shorter than beat length so there's
 * a small gap between notes (feels natural, avoids chords from overlap).
 */
object BuiltInSongs {

    /**
     * Ode to Joy (Beethoven) — 16 bars, 120 BPM, two hands.
     * Track 0 = right hand (melody), Track 1 = left hand (bass).
     */
    val ODE_TO_JOY: List<NoteEvent> = buildList {
        // beatStart × 0.5 = seconds at 120 BPM
        fun rh(midi: Int, beat: Double, beats: Double) =
            add(NoteEvent(midi, beat * 0.5, beats * 0.5 - 0.05, 80, 0))   // track 0 = right
        fun lh(midi: Int, beat: Double, beats: Double) =
            add(NoteEvent(midi, beat * 0.5, beats * 0.5 - 0.05, 65, 1))   // track 1 = left

        // ── Phrase A (bars 1–4) ────────────────────────────────────────────
        // Right hand melody: E E F G | G F E D | C C D E | E. D D(half)
        rh(64, 0.0,  1.0); rh(64, 1.0,  1.0); rh(65, 2.0, 1.0); rh(67, 3.0, 1.0)  // bar 1
        rh(67, 4.0,  1.0); rh(65, 5.0,  1.0); rh(64, 6.0, 1.0); rh(62, 7.0, 1.0)  // bar 2
        rh(60, 8.0,  1.0); rh(60, 9.0,  1.0); rh(62,10.0, 1.0); rh(64,11.0, 1.0)  // bar 3
        rh(64,12.0,  1.5); rh(62,13.5,  0.5); rh(62,14.0, 2.0)                     // bar 4

        // Left hand bass (half notes, one chord tone per half bar)
        lh(48, 0.0, 2.0); lh(55, 2.0, 2.0)   // bar 1: C3 G3
        lh(48, 4.0, 2.0); lh(55, 6.0, 2.0)   // bar 2: C3 G3
        lh(53, 8.0, 2.0); lh(48,10.0, 2.0)   // bar 3: F3 C3
        lh(55,12.0, 2.0); lh(55,14.0, 2.0)   // bar 4: G3 G3

        // ── Phrase A' (bars 5–8) — same melody, different cadence ─────────
        rh(64,16.0,  1.0); rh(64,17.0, 1.0); rh(65,18.0, 1.0); rh(67,19.0, 1.0)  // bar 5
        rh(67,20.0,  1.0); rh(65,21.0, 1.0); rh(64,22.0, 1.0); rh(62,23.0, 1.0)  // bar 6
        rh(60,24.0,  1.0); rh(60,25.0, 1.0); rh(62,26.0, 1.0); rh(64,27.0, 1.0)  // bar 7
        rh(62,28.0,  1.5); rh(60,29.5, 0.5); rh(60,30.0, 2.0)                     // bar 8

        lh(48,16.0, 2.0); lh(55,18.0, 2.0)   // bar 5
        lh(48,20.0, 2.0); lh(55,22.0, 2.0)   // bar 6
        lh(53,24.0, 2.0); lh(55,26.0, 2.0)   // bar 7
        lh(48,28.0, 4.0)                       // bar 8: C3 whole note (final)

        // ── Phrase B (bars 9–12) ──────────────────────────────────────────
        // D D E C | D E(e)F(e) E C | D E(e)F(e) E D | C D G(half)
        rh(62,32.0,  1.0); rh(62,33.0, 1.0); rh(64,34.0, 1.0); rh(60,35.0, 1.0)  // bar 9
        rh(62,36.0,  1.0); rh(64,37.0, 0.5); rh(65,37.5, 0.5); rh(64,38.0, 1.0); rh(60,39.0, 1.0) // bar 10
        rh(62,40.0,  1.0); rh(64,41.0, 0.5); rh(65,41.5, 0.5); rh(64,42.0, 1.0); rh(62,43.0, 1.0) // bar 11
        rh(60,44.0,  1.0); rh(62,45.0, 1.0); rh(55,46.0, 2.0)                     // bar 12 (G4→G3 descent)

        lh(55,32.0, 2.0); lh(48,34.0, 2.0)   // bar  9: G3 C3
        lh(55,36.0, 2.0); lh(48,38.0, 2.0)   // bar 10: G3 C3
        lh(55,40.0, 2.0); lh(50,42.0, 2.0)   // bar 11: G3 D3
        lh(48,44.0, 2.0); lh(55,46.0, 2.0)   // bar 12: C3 G3

        // ── Phrase A'' (bars 13–16) — full reprise, strong ending ─────────
        rh(64,48.0,  1.0); rh(64,49.0, 1.0); rh(65,50.0, 1.0); rh(67,51.0, 1.0)  // bar 13
        rh(67,52.0,  1.0); rh(65,53.0, 1.0); rh(64,54.0, 1.0); rh(62,55.0, 1.0)  // bar 14
        rh(60,56.0,  1.0); rh(60,57.0, 1.0); rh(62,58.0, 1.0); rh(64,59.0, 1.0)  // bar 15
        rh(62,60.0,  1.5); rh(60,61.5, 0.5); rh(60,62.0, 2.0)                     // bar 16

        lh(48,48.0, 2.0); lh(55,50.0, 2.0)   // bar 13
        lh(48,52.0, 2.0); lh(55,54.0, 2.0)   // bar 14
        lh(53,56.0, 2.0); lh(55,58.0, 2.0)   // bar 15
        lh(48,60.0, 4.0)                       // bar 16: final C3
    }

    /**
     * Interstellar Theme (simplified) — key of G, 60 BPM, two hands.
     * Inspired by Hans Zimmer's Cornfield Chase / main theme.
     * Track 0 = right hand (melody), Track 1 = left hand (bass).
     * 60 BPM: beat = 1.0 s, half note = 2.0 s, whole note = 4.0 s.
     */
    val INTERSTELLAR_THEME: List<NoteEvent> = buildList {
        fun rh(midi: Int, beat: Double, beats: Double) =
            add(NoteEvent(midi, beat, beats - 0.05, 75, 0))
        fun lh(midi: Int, beat: Double, beats: Double) =
            add(NoteEvent(midi, beat, beats - 0.05, 58, 1))

        // ── Phrase A (bars 1–4) ────────────────────────────────────────────
        rh(67,  0.0, 2.0); rh(69,  2.0, 2.0)  // bar 1:  G4(h) A4(h)
        rh(71,  4.0, 2.0); rh(67,  6.0, 2.0)  // bar 2:  B4(h) G4(h)
        rh(64,  8.0, 4.0)                       // bar 3:  E4(w)
        rh(62, 12.0, 2.0); rh(64, 14.0, 2.0)  // bar 4:  D4(h) E4(h)

        lh(43,  0.0, 4.0)                       // bar 1:  G2
        lh(43,  4.0, 4.0)                       // bar 2:  G2
        lh(48,  8.0, 4.0)                       // bar 3:  C3
        lh(50, 12.0, 4.0)                       // bar 4:  D3

        // ── Phrase A' (bars 5–8) ──────────────────────────────────────────
        rh(67, 16.0, 2.0); rh(69, 18.0, 2.0)  // bar 5:  G4(h) A4(h)
        rh(71, 20.0, 2.0); rh(72, 22.0, 2.0)  // bar 6:  B4(h) C5(h)
        rh(71, 24.0, 3.0); rh(69, 27.0, 1.0)  // bar 7:  B4(d.h) A4(q)
        rh(67, 28.0, 4.0)                       // bar 8:  G4(w)

        lh(43, 16.0, 4.0)                       // bar 5:  G2
        lh(48, 20.0, 4.0)                       // bar 6:  C3
        lh(50, 24.0, 4.0)                       // bar 7:  D3
        lh(43, 28.0, 4.0)                       // bar 8:  G2

        // ── Phrase B (bars 9–12) ──────────────────────────────────────────
        rh(69, 32.0, 2.0); rh(71, 34.0, 2.0)  // bar 9:  A4(h) B4(h)
        rh(72, 36.0, 2.0); rh(71, 38.0, 2.0)  // bar 10: C5(h) B4(h)
        rh(69, 40.0, 4.0)                       // bar 11: A4(w)
        rh(67, 44.0, 4.0)                       // bar 12: G4(w)

        lh(45, 32.0, 4.0)                       // bar 9:  A2
        lh(52, 36.0, 4.0)                       // bar 10: E3
        lh(45, 40.0, 4.0)                       // bar 11: A2
        lh(43, 44.0, 4.0)                       // bar 12: G2

        // ── Phrase C (bars 13–16) — descent to end ───────────────────────
        rh(76, 48.0, 2.0); rh(74, 50.0, 2.0)  // bar 13: E5(h) D5(h)
        rh(72, 52.0, 2.0); rh(71, 54.0, 2.0)  // bar 14: C5(h) B4(h)
        rh(69, 56.0, 2.0); rh(67, 58.0, 2.0)  // bar 15: A4(h) G4(h)
        rh(67, 60.0, 4.0)                       // bar 16: G4(w)

        lh(48, 48.0, 4.0)                       // bar 13: C3
        lh(43, 52.0, 4.0)                       // bar 14: G2
        lh(50, 56.0, 4.0)                       // bar 15: D3
        lh(43, 60.0, 4.0)                       // bar 16: G2
    }

    /** Twinkle Twinkle Little Star — key of C, one octave, 120 BPM. */
    val TWINKLE_TWINKLE: List<NoteEvent> = buildList {
        // Helper: add a note at the given beat (0.5 s per beat)
        fun note(midi: Int, beatStart: Double, beats: Double) =
            add(NoteEvent(midi, beatStart * 0.5, beats * 0.5 - 0.05, 80, 0))

        // C  C  G  G  A  A  G(half)
        note(60, 0.0, 1.0); note(60, 1.0, 1.0)
        note(67, 2.0, 1.0); note(67, 3.0, 1.0)
        note(69, 4.0, 1.0); note(69, 5.0, 1.0)
        note(67, 6.0, 2.0)

        // F  F  E  E  D  D  C(half)
        note(65, 8.0,  1.0); note(65, 9.0,  1.0)
        note(64, 10.0, 1.0); note(64, 11.0, 1.0)
        note(62, 12.0, 1.0); note(62, 13.0, 1.0)
        note(60, 14.0, 2.0)

        // G  G  F  F  E  E  D(half)
        note(67, 16.0, 1.0); note(67, 17.0, 1.0)
        note(65, 18.0, 1.0); note(65, 19.0, 1.0)
        note(64, 20.0, 1.0); note(64, 21.0, 1.0)
        note(62, 22.0, 2.0)

        // G  G  F  F  E  E  D(half)
        note(67, 24.0, 1.0); note(67, 25.0, 1.0)
        note(65, 26.0, 1.0); note(65, 27.0, 1.0)
        note(64, 28.0, 1.0); note(64, 29.0, 1.0)
        note(62, 30.0, 2.0)

        // C  C  G  G  A  A  G(half)
        note(60, 32.0, 1.0); note(60, 33.0, 1.0)
        note(67, 34.0, 1.0); note(67, 35.0, 1.0)
        note(69, 36.0, 1.0); note(69, 37.0, 1.0)
        note(67, 38.0, 2.0)

        // F  F  E  E  D  D  C(whole)
        note(65, 40.0, 1.0); note(65, 41.0, 1.0)
        note(64, 42.0, 1.0); note(64, 43.0, 1.0)
        note(62, 44.0, 1.0); note(62, 45.0, 1.0)
        note(60, 46.0, 4.0)
    }
}
