"""Renders the game's original chiptune music and sound effects.

Run: python3 tools/make_audio.py  (needs numpy and ffmpeg)
Writes Ogg Vorbis files into app/src/main/res/raw/.
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 22050
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")
NOTE_INDEX = {"C": 0, "C#": 1, "D": 2, "D#": 3, "E": 4, "F": 5, "F#": 6, "G": 7, "G#": 8, "A": 9, "A#": 10, "B": 11}


def freq(name):
    pitch, octave = name[:-1], int(name[-1])
    midi = 12 * (octave + 1) + NOTE_INDEX[pitch]
    return 440.0 * 2 ** ((midi - 69) / 12)


def pulse(f, n, duty, phase0=0.0, vibrato=0.0):
    t = np.arange(n) / SR
    fm = f * (1 + vibrato * np.sin(2 * np.pi * 5.5 * t) * np.clip(t / 0.25, 0, 1))
    phase = (phase0 + np.cumsum(fm) / SR) % 1.0
    return np.where(phase < duty, 1.0, -1.0)


def triangle(f, n):
    phase = (np.arange(n) * f / SR) % 1.0
    # 4-bit stepped triangle for that console flavour.
    tri = 4 * np.abs(phase - 0.5) - 1
    return np.round(tri * 7.5) / 7.5


def noise(n, seed=0):
    return np.random.default_rng(seed).uniform(-1, 1, n)


def envelope(n, attack=0.005, release=0.03, decay=0.0, sustain=1.0):
    env = np.ones(n) * sustain
    a = max(1, int(attack * SR))
    r = max(1, int(release * SR))
    env[:a] = np.linspace(0, 1, a)
    if decay > 0:
        d = int(decay * SR)
        seg = env[a:a + d]
        env[a:a + d] = np.linspace(1, sustain, len(seg))
    if n > r:
        env[-r:] *= np.linspace(1, 0, r)
    return env


def parse(seq):
    """'A4:4 r:2 C5:2' -> [(note|None, sixteenths)]"""
    out = []
    for tok in seq.split():
        note, length = tok.split(":")
        out.append((None if note == "r" else note, int(length)))
    return out


def render_voice(seq, step, kind, vol, duty=0.25, vibrato=0.0, legato=0.85):
    parts = []
    for note, length in parse(seq):
        n = int(round(length * step * SR))
        if note is None:
            parts.append(np.zeros(n))
            continue
        on = int(n * legato)
        f = freq(note)
        if kind == "pulse":
            w = pulse(f, on, duty, vibrato=vibrato if length >= 6 else 0.0)
            w *= envelope(on, decay=0.15, sustain=0.75)
        else:
            w = triangle(f, on) * envelope(on, release=0.02)
        parts.append(np.concatenate([w * vol, np.zeros(n - on)]))
    return np.concatenate(parts)


CHORDS = {
    "C": ["C", "E", "G"], "Dm": ["D", "F", "A"], "Em": ["E", "G", "B"], "F": ["F", "A", "C"],
    "G": ["G", "B", "D"], "Am": ["A", "C", "E"], "E": ["E", "G#", "B"],
}


def chord_notes(chord, octave):
    names = CHORDS[chord]
    root = NOTE_INDEX[names[0]]
    notes = []
    for nm in names:
        o = octave + (1 if NOTE_INDEX[nm] < root else 0)
        notes.append(f"{nm}{o}")
    return notes


def arp_seq(chords, pattern, octave, unit):
    toks = []
    for ch in chords:
        tones = chord_notes(ch, octave)
        tones.append(tones[0][:-1] + str(int(tones[0][-1]) + 1))
        for i in range(16 // unit):
            toks.append(f"{tones[pattern[i % len(pattern)]]}:{unit}")
    return " ".join(toks)


def drums(bars, step, vol):
    bar_len = int(round(16 * step * SR))
    bar = np.zeros(bar_len)
    s16 = int(round(step * SR))

    def hit(pos, length, v, decay, seed, lowpass=False):
        n = int(length * SR)
        w = noise(n, seed) * np.exp(-np.arange(n) / (decay * SR))
        if lowpass:
            w = np.convolve(w, np.ones(8) / 8, mode="same")
            t = np.arange(n) / SR
            w += 0.8 * np.sin(2 * np.pi * (110 - 60 * t / length) * t) * np.exp(-t / decay)
        end = min(bar_len, pos + n)
        bar[pos:end] += w[: end - pos] * v

    for beat in (0, 8):
        hit(beat * s16, 0.12, 1.0, 0.04, 1, lowpass=True)
    for beat in (4, 12):
        hit(beat * s16, 0.15, 0.7, 0.05, 2)
    for eighth in range(0, 16, 2):
        hit(eighth * s16, 0.03, 0.18, 0.008, 3 + eighth)
    return np.tile(bar * vol, bars)


def mix(*tracks):
    n = max(len(t) for t in tracks)
    out = np.zeros(n)
    for t in tracks:
        out[: len(t)] += t
    peak = np.max(np.abs(out))
    if peak > 0.95:
        out *= 0.95 / peak
    return out


def smooth(x, k=3):
    """Gentle low-pass to take the edge off raw square waves."""
    return np.convolve(x, np.ones(k) / k, mode="same")


def write_ogg(name, samples, loop=False):
    pcm = (np.clip(samples, -1, 1) * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        path = tmp.name
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    os.makedirs(OUT, exist_ok=True)
    cmd = ["ffmpeg", "-y", "-loglevel", "error", "-i", path, "-c:a", "libvorbis", "-q:a", "3"]
    if loop:
        # Tells Android's player to loop the file seamlessly.
        cmd += ["-metadata", "ANDROID_LOOP=true"]
    cmd.append(os.path.join(OUT, name + ".ogg"))
    subprocess.run(cmd, check=True)
    os.remove(path)


def camp_theme():
    step = 60 / 88 / 4
    chords = "F G Em Am F G C C Dm Em F G Am F G C".split()
    melody = (
        "A4:4 C5:4 F5:6 E5:2 D5:4 B4:4 G4:8 E5:4 G5:4 B4:6 C5:2 A4:12 r:4 "
        "F5:4 E5:4 C5:4 A4:4 B4:4 D5:4 G5:6 F5:2 E5:8 D5:4 C5:4 C5:12 r:4 "
        "D5:6 F5:2 A5:4 F5:4 G5:6 E5:2 B4:8 A5:4 G5:4 F5:4 C5:4 D5:8 B4:4 D5:4 "
        "C5:4 E5:4 A5:6 G5:2 F5:4 A5:4 C6:8 B5:4 A5:4 G5:4 D5:4 C5:8 E5:4 G4:4"
    )
    bass = " ".join(f"{chord_notes(c, 2)[0]}:8 {chord_notes(c, 2)[2]}:8" for c in chords)
    arp = arp_seq(chords, [0, 1, 2, 3, 2, 1, 0, 1], 4, 2)
    return mix(
        render_voice(melody, step, "pulse", 0.20, duty=0.25, vibrato=0.006, legato=0.92),
        render_voice(arp, step, "pulse", 0.06, duty=0.125, legato=0.7),
        render_voice(bass, step, "tri", 0.32, legato=0.95),
    )


def battle_theme():
    step = 60 / 160 / 4
    chords = "Am Am F G Am Am F E Dm Dm Am Am F G E E".split()
    melody = (
        "A4:2 A4:2 E5:4 D5:2 C5:2 B4:2 C5:2 A4:6 E4:2 A4:4 C5:4 "
        "F5:4 E5:2 D5:2 C5:4 A4:4 B4:4 D5:4 G5:4 F5:2 D5:2 "
        "E5:2 E5:2 A5:4 G5:2 F5:2 E5:2 D5:2 C5:4 B4:2 C5:2 A4:8 "
        "A4:4 C5:4 F5:4 A5:4 G#5:8 B5:4 E5:4 "
        "D5:4 F5:4 A5:6 G5:2 F5:4 E5:2 D5:2 F5:8 "
        "E5:4 A5:4 C6:6 B5:2 A5:8 E5:8 "
        "F5:2 G5:2 A5:4 C6:4 A5:4 B5:2 A5:2 G5:4 D5:4 B4:4 "
        "G#4:4 B4:4 E5:4 G#5:4 B5:8 r:4 E5:2 G#5:2"
    )
    bass = " ".join(" ".join(f"{chord_notes(c, 2)[0]}:2 {chord_notes(c, 3)[0]}:2" for _ in range(4)) for c in chords)
    arp = arp_seq(chords, [0, 1, 2, 3], 4, 1)
    return mix(
        render_voice(melody, step, "pulse", 0.19, duty=0.25, vibrato=0.005, legato=0.88),
        render_voice(arp, step, "pulse", 0.045, duty=0.125, legato=0.6),
        render_voice(bass, step, "tri", 0.34, legato=0.8),
        drums(16, step, 0.22),
    )


def sweep(f0, f1, dur, duty=0.5, vol=0.4):
    n = int(dur * SR)
    f = np.linspace(f0, f1, n)
    phase = np.cumsum(f) / SR % 1.0
    return np.where(phase < duty, 1.0, -1.0) * envelope(n, release=0.02) * vol


def sfx():
    out = {}
    hit_noise = noise(int(0.12 * SR), 7) * np.exp(-np.arange(int(0.12 * SR)) / (0.03 * SR))
    out["sfx_hit"] = mix(sweep(600, 120, 0.1, vol=0.35), hit_noise * 0.5)
    crit_noise = noise(int(0.2 * SR), 8) * np.exp(-np.arange(int(0.2 * SR)) / (0.05 * SR))
    out["sfx_crit"] = mix(np.concatenate([sweep(1200, 200, 0.12, vol=0.4), sweep(900, 150, 0.12, vol=0.3)]), crit_noise * 0.6)
    out["sfx_magic"] = mix(*[np.concatenate([np.zeros(int(i * 0.04 * SR)), sweep(400 + i * 150, 1200 + i * 200, 0.1, 0.25, 0.18)]) for i in range(5)])
    out["sfx_heal"] = render_voice("C6:1 E6:1 G6:1 C7:1 G6:1 C7:3", 0.05, "pulse", 0.25, duty=0.125, legato=0.9)
    out["sfx_complete"] = render_voice("B5:1 E6:4", 0.06, "pulse", 0.3, duty=0.5, legato=0.95)
    out["sfx_cursor"] = render_voice("A6:1", 0.035, "pulse", 0.18, duty=0.25)
    out["sfx_levelup"] = mix(
        render_voice("C5:1 E5:1 G5:1 C6:1 E6:1 G6:1 C7:6", 0.06, "pulse", 0.26, duty=0.25, legato=0.95),
        render_voice("C4:6 G4:6", 0.06, "tri", 0.3),
    )
    fall = noise(int(0.6 * SR), 9) * np.linspace(1, 0, int(0.6 * SR))
    out["sfx_defeat"] = mix(sweep(800, 60, 0.6, duty=0.5, vol=0.25), np.convolve(fall, np.ones(6) / 6, "same") * 0.4)
    step = 60 / 140 / 4
    out["sfx_victory"] = mix(
        render_voice("G4:2 C5:2 E5:2 G5:6 E5:2 G5:2 C6:12", step, "pulse", 0.24, duty=0.25, legato=0.9),
        render_voice("E4:2 G4:2 C5:2 E5:6 C5:2 E5:2 G5:12", step, "pulse", 0.1, duty=0.5, legato=0.9),
        render_voice("C3:6 G3:6 C3:4 C4:12", step, "tri", 0.35),
    )
    out["sfx_coin"] = render_voice("E6:1 B6:3", 0.05, "pulse", 0.22, duty=0.5)
    return out


if __name__ == "__main__":
    write_ogg("music_camp", smooth(camp_theme()), loop=True)
    write_ogg("music_battle", smooth(battle_theme()), loop=True)
    for name, data in sfx().items():
        write_ogg(name, smooth(data))
    print("ok")
