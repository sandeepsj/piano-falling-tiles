#include "PianoSynthesizer.h"
#include <cmath>
#include <cstring>
#include <algorithm>

static constexpr float TWO_PI = 2.f * static_cast<float>(M_PI);

float PianoSynthesizer::noteToFreq(int midiNote) {
    return 440.f * std::pow(2.f, (midiNote - 69) / 12.f);
}

void PianoSynthesizer::setSampleRate(float sr) {
    sampleRate_  = sr;
    releaseRate_ = 1.f / (RELEASE_TIME_S * sr);
    attackRate_  = 1.f / (ATTACK_TIME_S  * sr);
    // Recalculate phaseInc for any running voices
    for (auto& v : voices_) {
        if (v.active) v.phaseInc = TWO_PI * noteToFreq(v.midiNote) / sr;
    }
}

void PianoSynthesizer::noteOn(int midiNote, int velocity) {
    Voice* slot = nullptr;

    // 1. Reuse existing voice for same note (retrigger)
    for (auto& v : voices_) {
        if (v.active && v.midiNote == midiNote) { slot = &v; break; }
    }
    // 2. Free slot
    if (!slot) {
        for (auto& v : voices_) {
            if (!v.active) { slot = &v; break; }
        }
    }
    // 3. Steal a releasing voice
    if (!slot) {
        for (auto& v : voices_) {
            if (v.releasing) { slot = &v; break; }
        }
    }
    if (!slot) return;  // All 20 voices fully active — drop this note

    slot->midiNote  = midiNote;
    slot->phase     = 0.f;
    slot->phaseInc  = TWO_PI * noteToFreq(midiNote) / sampleRate_;
    slot->targetAmp = (velocity / 127.f) * 0.35f;  // max 0.35 to avoid clipping 20 voices
    slot->releasing = false;
    slot->active    = true;
}

void PianoSynthesizer::noteOff(int midiNote) {
    for (auto& v : voices_) {
        if (v.active && !v.releasing && v.midiNote == midiNote)
            v.releasing = true;
    }
}

void PianoSynthesizer::mixInto(float* buf, int numFrames) {
    for (int f = 0; f < numFrames; ++f) {
        float sample = 0.f;

        for (auto& v : voices_) {
            if (!v.active) continue;

            // Envelope
            if (v.releasing) {
                v.amplitude -= releaseRate_;
                if (v.amplitude <= 0.f) { v.amplitude = 0.f; v.active = false; continue; }
            } else {
                v.amplitude = std::min(v.amplitude + attackRate_, v.targetAmp);
            }

            // Sine oscillator
            sample += std::sin(v.phase) * v.amplitude;
            v.phase += v.phaseInc;
            if (v.phase >= TWO_PI) v.phase -= TWO_PI;
        }

        buf[f * 2]     += sample;
        buf[f * 2 + 1] += sample;
    }
}
