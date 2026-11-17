#!/usr/bin/env python3
"""Buat klip panjang dengan jeda senyap antar ujaran (mendekati percakapan nyata).

Pemakaian: python make_gap.py --work W [--gap 0.8] [--n 20]
Hasil: W/data/long_gap.wav + entri 'long_gap.wav' di refs.json
"""
import argparse
import json
import wave
from pathlib import Path


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--work", required=True)
    p.add_argument("--gap", type=float, default=0.8)
    p.add_argument("--n", type=int, default=20)
    a = p.parse_args()

    data = Path(a.work) / "data"
    refs = json.loads((data / "refs.json").read_text(encoding="utf-8"))
    keys = [k for k in refs if k.startswith("utt/")][: a.n]

    frames, params = [], None
    for k in keys:
        with wave.open(str(data / k), "rb") as w:
            params = w.getparams()
            frames.append(w.readframes(w.getnframes()))
    silence = b"\x00\x00" * int(params.framerate * a.gap)

    out = data / "long_gap.wav"
    with wave.open(str(out), "wb") as w:
        w.setnchannels(params.nchannels)
        w.setsampwidth(params.sampwidth)
        w.setframerate(params.framerate)
        w.writeframes(silence.join(frames))

    refs["long_gap.wav"] = {
        "ref": " ".join(refs[k]["ref"] for k in keys),
        "dur": sum(refs[k]["dur"] for k in keys) + a.gap * (len(keys) - 1),
    }
    (data / "refs.json").write_text(json.dumps(refs, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"{out.name}: {refs['long_gap.wav']['dur']:.0f} detik, {len(keys)} ujaran, jeda {a.gap}s")


if __name__ == "__main__":
    main()
