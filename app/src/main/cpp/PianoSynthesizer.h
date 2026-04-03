#pragma once
#include <array>

/**
 * 20-voice sine-wave piano synthesizer.
 * Thread-safe for concurrent noteOn/noteOff from the JNI thread and
 * mixInto from the Oboe audio callback (lock-free per-voice flags).
 */
class PianoSynthesizer {
public:
    static constexpr int   MAX_VOICES      = 20;
    static constexpr float RELEASE_TIME_S  = 0.25f;  // note-off decay
    static constexpr float ATTACK_TIME_S   = 0.005f; // 5 ms attack ramp

    void setSampleRate(float sr);
    void noteOn(int midiNote, int velocity);
    void noteOff(int midiNote);

    /** Called from Oboe audio callback. Adds to (does not clear) outputBuffer. */
    void mixInto(float* outputBuffer, int numFrames);

private:
    struct Voice {
        int   midiNote  = -1;
        float phase     = 0.f;
        float phaseInc  = 0.f;
        float amplitude = 0.f;
        float targetAmp = 0.f;
        bool  active    = false;
        bool  releasing = false;
    };

    std::array<Voice, MAX_VOICES> voices_{};
    float sampleRate_ = 48000.f;
    float releaseRate_ = 1.f / (RELEASE_TIME_S * 48000.f);
    float attackRate_  = 1.f / (ATTACK_TIME_S  * 48000.f);

    static float noteToFreq(int midiNote);
};
