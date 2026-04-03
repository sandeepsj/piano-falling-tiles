package com.pianotiles.midi

import dev.atsushieno.ktmidi.Midi1CompoundMessage
import dev.atsushieno.ktmidi.Midi1Music
import dev.atsushieno.ktmidi.read   // top-level extension function

/**
 * Parses a standard MIDI file (SMF) into a flat, sorted list of NoteEvents.
 *
 * Supports format 0 (single track) and format 1 (multi-track).
 * Handles tempo changes; defaults to 120 BPM if no tempo meta event present.
 * Note-On with velocity = 0 is treated as Note-Off (standard MIDI running-status trick).
 */
object MidiFileParser {

    // MIDI status byte constants (Byte type — upper nibble of status byte)
    private const val NOTE_OFF     : Byte = 0x80.toByte()
    private const val NOTE_ON      : Byte = 0x90.toByte()
    private const val META         : Byte = 0xFF.toByte()
    private const val TEMPO_META   : Byte = 0x51.toByte()
    private const val TIME_SIG_META: Byte = 0x58.toByte()

    data class ParseResult(
        val events: List<NoteEvent>,
        val durationSeconds: Double,
        val trackCount: Int,
        val noteCount: Int,
        val bpm: Double,
        val beatsPerBar: Int   // time signature numerator (4 for 4/4, 3 for 3/4, 6 for 6/8…)
    )

    fun parse(midiBytes: ByteArray): ParseResult {
        val music = Midi1Music()
        music.read(midiBytes.toList())      // extension fun from Midi1ReaderWriterKt

        val tpq = music.deltaTimeSpec       // ticks per quarter note
        val tempoMap = buildTempoMap(music)
        val events = mutableListOf<NoteEvent>()

        music.tracks.forEachIndexed { trackIdx, track ->
            val pending = mutableMapOf<Int, Pair<Long, Int>>()  // note → (startTick, velocity)
            var tick = 0L

            for (ev in track.events) {
                tick += ev.deltaTime
                val msg = ev.message
                val type = msg.eventType   // Byte

                when {
                    type == NOTE_ON && msg.lsb.toInt() != 0 -> {
                        val note = msg.msb.toInt() and 0x7F
                        val vel  = msg.lsb.toInt() and 0x7F
                        pending[note] = tick to vel
                    }
                    type == NOTE_OFF || (type == NOTE_ON && msg.lsb.toInt() == 0) -> {
                        val note = msg.msb.toInt() and 0x7F
                        pending.remove(note)?.let { (startTick, velocity) ->
                            val startSec = ticksToSeconds(startTick, tempoMap, tpq)
                            val endSec   = ticksToSeconds(tick,      tempoMap, tpq)
                            events.add(NoteEvent(
                                midiNote         = note,
                                startTimeSeconds = startSec,
                                durationSeconds  = (endSec - startSec).coerceAtLeast(0.0),
                                velocity         = velocity,
                                trackIndex       = trackIdx
                            ))
                        }
                    }
                }
            }
        }

        val sorted   = events.sortedBy { it.startTimeSeconds }
        val duration = sorted.lastOrNull()?.let { it.startTimeSeconds + it.durationSeconds } ?: 0.0

        // Use the last tempo entry that applies at tick 0 (overrides the default 120 BPM if present)
        val initUsPerBeat = tempoMap.filter { it.atTick == 0L }.lastOrNull()?.usPerBeat ?: 500_000
        val bpm = 60_000_000.0 / initUsPerBeat
        val beatsPerBar = parseBeatsPerBar(music)

        return ParseResult(
            events          = sorted,
            durationSeconds = duration,
            trackCount      = music.tracks.size,
            noteCount       = sorted.size,
            bpm             = bpm,
            beatsPerBar     = beatsPerBar
        )
    }

    /** Reads the first time-signature meta event (0xFF 0x58) from track 0. Defaults to 4. */
    private fun parseBeatsPerBar(music: Midi1Music): Int {
        for (ev in (music.tracks.firstOrNull()?.events ?: emptyList())) {
            val msg = ev.message
            if (msg.eventType == META && msg.metaType == TIME_SIG_META) {
                val compound = msg as? Midi1CompoundMessage ?: continue
                val d = compound.extraData ?: continue
                if (compound.extraDataLength < 1) continue
                val numerator = d[compound.extraDataOffset].toInt() and 0xFF
                if (numerator > 0) return numerator
            }
        }
        return 4  // default 4/4
    }

    // ─────────────────────────────────────────────────────────────────────
    private data class TempoEntry(val atTick: Long, val usPerBeat: Int)

    private fun buildTempoMap(music: Midi1Music): List<TempoEntry> {
        val entries = mutableListOf(TempoEntry(0L, 500_000))  // default = 120 BPM
        var tick = 0L

        // Tempo changes are always in track 0 for format 0 and format 1
        for (ev in (music.tracks.firstOrNull()?.events ?: emptyList())) {
            tick += ev.deltaTime
            val msg = ev.message
            if (msg.eventType == META && msg.metaType == TEMPO_META) {
                val compound = msg as? Midi1CompoundMessage ?: continue
                val d = compound.extraData ?: continue
                val o = compound.extraDataOffset
                if (compound.extraDataLength < 3) continue
                val us = ((d[o    ].toInt() and 0xFF) shl 16) or
                         ((d[o + 1].toInt() and 0xFF) shl  8) or
                          (d[o + 2].toInt() and 0xFF)
                entries.add(TempoEntry(tick, us))
            }
        }
        return entries.sortedBy { it.atTick }
    }

    private fun ticksToSeconds(tick: Long, tempoMap: List<TempoEntry>, tpq: Int): Double {
        var elapsedUs = 0.0
        var prevTick  = 0L
        var prevUs    = tempoMap[0].usPerBeat

        for (entry in tempoMap) {
            if (entry.atTick >= tick) break
            elapsedUs += (entry.atTick - prevTick).toDouble() * prevUs / tpq
            prevTick  = entry.atTick
            prevUs    = entry.usPerBeat
        }
        elapsedUs += (tick - prevTick).toDouble() * prevUs / tpq
        return elapsedUs / 1_000_000.0
    }
}
