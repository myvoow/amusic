#!/usr/bin/env python3
# Navic AutoMix analysis, GPL-3.0-only. Uses Essentia (AGPL), run privately on the server only.
"""Tempo, beat grid start, key and cue points for every Navidrome track -> automix.json.

Incremental: a track is re-analysed only when its path, size or mtime changed.
Usage: analyze.py            analyse everything new or changed
       analyze.py ID [ID..]  analyse only these Navidrome ids (quality check), print a table
"""
import hashlib
import json
import os
import shutil
import sqlite3
import subprocess
import sys
import time
from datetime import datetime, timezone
from concurrent.futures import ThreadPoolExecutor, as_completed

import essentia
import essentia.standard as es
import numpy as np

essentia.log.infoActive = False
essentia.log.warningActive = False

MUSIC = "/music"
OUT = "/out/automix.json"
SR = 44100
WORKERS = int(os.environ.get("WORKERS", "2"))
# starting values, tune by ear
SILENCE_DB = -50.0  # 50 ms frame RMS below this is silence
STEADY_SHARE = 0.8  # share of beat intervals within 5 % of the median needed for "steady"
OUTRO_DROP = 0.5    # outro = every remaining bar below half the body RMS (about -6 dB)
OUTRO_MAX_S = 40.0  # never start the exit earlier than this before the end of sound
MIN_TAIL_S = 6.0    # leave at least the shortest overlap after cueOut

PITCH = {"C": 0, "C#": 1, "Db": 1, "D": 2, "D#": 3, "Eb": 3, "E": 4, "F": 5, "F#": 6, "Gb": 6,
         "G": 7, "G#": 8, "Ab": 8, "A": 9, "A#": 10, "Bb": 10, "B": 11}


def camelot(key, scale):
    pc = PITCH[key]
    if scale == "minor":
        pc = (pc + 3) % 12  # a minor key shares its number with its relative major
    return f"{(pc * 7 + 7) % 12 + 1}{'A' if scale == 'minor' else 'B'}"


def analyse(path):
    # ffmpeg decodes every format in the library (Essentia's bundled loader rejects some Ogg files)
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-f", "f32le", "-ac", "1", "-ar", str(SR), "-"],
                         capture_output=True, check=True).stdout
    audio = np.frombuffer(raw, np.float32).copy()
    duration = len(audio) / SR
    hop = SR // 20
    n = len(audio) // hop
    rms = np.sqrt(np.mean(audio[: n * hop].reshape(n, hop) ** 2, axis=1) + 1e-12)
    loud = np.flatnonzero(20 * np.log10(rms) > SILENCE_DB)
    if not len(loud):
        raise ValueError("silent file")
    sound_start, sound_end = loud[0] / 20, (loud[-1] + 1) / 20

    bpm, beats, conf, _, intervals = es.RhythmExtractor2013(method="multifeature")(audio)
    factor = 1.0
    while 0 < bpm < 70:
        bpm, factor = bpm * 2, factor * 2
    while bpm > 180:
        bpm, factor = bpm / 2, factor / 2
    med = np.median(intervals) if len(intervals) > 8 else 0
    steady = bool(med) and float(np.mean(np.abs(intervals - med) < .05 * med)) >= STEADY_SHARE

    after = beats[beats >= sound_start - 0.05]
    cue_in = float(after[0]) if len(after) else float(sound_start)
    # ponytail: the first beat after the opening silence is taken as the downbeat, no downbeat tracker
    first_beat = cue_in

    cue_out = float(sound_end)
    if bpm:
        # ponytail: constant tempo assumed, the grid is firstBeat + k * 60/bpm
        bar = 4 * 60.0 / bpm
        bars = np.arange(first_beat, sound_end, bar)
        energy = np.array([np.sqrt(np.mean(audio[int(b * SR):int((b + bar) * SR)] ** 2)) for b in bars])
        body = np.median(energy[int(len(bars) * .2):int(len(bars) * .7)]) if len(bars) > 10 else 0
        outro = [b for i, b in enumerate(bars) if i >= len(bars) * .7 and body
                 and np.all(energy[i:] < body * OUTRO_DROP) and MIN_TAIL_S <= sound_end - b <= OUTRO_MAX_S]
        eights = [b for b in bars[::8] if sound_end - b >= MIN_TAIL_S]
        cue_out = float(outro[0] if outro else eights[-1] if eights else sound_end)

    key, scale, strength = es.KeyExtractor()(audio)
    return {
        "bpm": round(float(bpm), 2),
        "bpmConfidence": round(min(max(float(conf), 0) / 5.32, 1.0), 2),  # multifeature confidence is 0..5.32
        "bpmFactor": factor,
        "firstBeat": round(first_beat, 3),
        "steady": steady,
        "key": camelot(key, scale),
        "keyConfidence": round(float(strength), 2),
        "cueIn": round(cue_in, 3),
        "cueOut": round(cue_out, 3),
        "end": round(float(sound_end), 3),
        "duration": round(duration, 3),
    }


def work(item):
    # ponytail: one process per file (~1 s Essentia import) so a native crash or hang only loses that file
    tid, rel, sig = item
    try:
        p = subprocess.run([sys.executable, __file__, "--one", os.path.join(MUSIC, rel)],
                           capture_output=True, text=True, timeout=900)
        if p.returncode:
            return tid, sig, None, f"{rel}: exit {p.returncode} {p.stderr.strip()[-200:]}"
        return tid, sig, json.loads(p.stdout.strip().splitlines()[-1]), None
    except Exception as e:  # timeout or unreadable output: skip, retried next run
        return tid, sig, None, f"{rel}: {e}"


def write(tracks):
    tmp = OUT + ".tmp"
    with open(tmp, "w") as f:
        json.dump({"version": 1, "generatedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
                   "tracks": tracks}, f, separators=(",", ":"))
    os.replace(tmp, OUT)


def main(only):
    # ponytail: copy of the live db (db + wal), a write mid-copy just fails this run, the next one retries
    for ext in ("", "-wal"):
        if os.path.exists("/nd/navidrome.db" + ext):
            shutil.copy("/nd/navidrome.db" + ext, "/tmp/nd.db" + ext)
    rows = sqlite3.connect("/tmp/nd.db").execute(
        "select id, path, bpm from media_file where missing = 0").fetchall()
    old = json.load(open(OUT))["tracks"] if os.path.exists(OUT) else {}

    tracks, todo, tags = {}, [], {}
    for tid, rel, tag in rows:
        try:
            st = os.stat(os.path.join(MUSIC, rel))
        except OSError:
            continue
        sig = hashlib.md5(f"{rel}|{st.st_size}|{int(st.st_mtime)}".encode()).hexdigest()[:12]
        if old.get(tid, {}).get("sig") == sig and not (only and tid in only):
            tracks[tid] = old[tid]
        elif not only or tid in only:
            todo.append((tid, rel, sig))
            tags[tid] = tag
        elif tid in old:
            tracks[tid] = old[tid]

    print(f"{len(rows)} tracks, {len(tracks)} up to date, {len(todo)} to analyse", flush=True)
    started, errors = time.time(), 0
    with ThreadPoolExecutor(WORKERS) as pool:
        for i, fut in enumerate(as_completed([pool.submit(work, t) for t in todo]), 1):
            tid, sig, res, err = fut.result()
            if err:
                errors += 1
                print("ERROR", err, flush=True)
            else:
                tracks[tid] = {**res, "sig": sig}
                tag = tags[tid]
                if tag and not any(abs(tag * f - res["bpm"]) / res["bpm"] < .04 for f in (.5, 1, 2)):
                    print(f"BPM tag {tag} vs analysed {res['bpm']}: {tid}", flush=True)
            if i % 50 == 0 or i == len(todo):
                write(tracks)
                eta = (time.time() - started) / i * (len(todo) - i)
                print(f"{i}/{len(todo)} done, {errors} errors, eta {eta / 60:.0f} min", flush=True)
    if not todo:
        write(tracks)
    if only:
        for tid in only:
            t = tracks.get(tid)
            print(tid, t and {k: t[k] for k in ("bpm", "bpmFactor", "bpmConfidence", "steady", "key",
                                                  "cueIn", "cueOut", "end", "duration")})


if __name__ == "__main__":
    if sys.argv[1:2] == ["--one"]:
        print(json.dumps(analyse(sys.argv[2])))
    else:
        main(set(sys.argv[1:]))
