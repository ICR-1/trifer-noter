#!/usr/bin/env python3
"""Benchmark STT on-device (whisper.cpp) untuk Trifer Noter, tahap M2a.

Alur:
  prep   : ambil sampel FLEURS Indonesia (data uji publik, CC-BY) -> wav 16 kHz + referensi
  push   : kirim binary, model, dan data ke HP lewat adb
  run    : jalankan whisper-cli di HP untuk tiap model x berkas, catat waktu + hasil teks
  report : hitung WER dan RTF per model, cetak tabel markdown

Contoh:
  python bench.py prep   --work W --n 30
  python bench.py push   --work W --binary whisper-cli
  python bench.py run    --work W --models tiny-q5_1 small-q5_1 --threads 4
  python bench.py report --work W
"""
import argparse
import json
import random
import re
import statistics
import subprocess
import sys
import tarfile
import wave
from pathlib import Path

ADB = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
DEV = "/data/local/tmp/bench"


# ---------- util ----------
def adb(*args, check=True):
    r = subprocess.run([ADB, *args], capture_output=True, text=True, encoding="utf-8", errors="replace")
    if check and r.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} gagal: {r.stderr.strip()}")
    return r


def norm(text: str) -> list[str]:
    text = text.lower()
    text = re.sub(r"[^\w\s]", " ", text, flags=re.UNICODE)
    return text.split()


def wer(ref: list[str], hyp: list[str]):
    """Return (kesalahan, panjang_ref) memakai jarak Levenshtein tingkat kata."""
    n, m = len(ref), len(hyp)
    prev = list(range(m + 1))
    for i in range(1, n + 1):
        cur = [i] + [0] * m
        for j in range(1, m + 1):
            cost = 0 if ref[i - 1] == hyp[j - 1] else 1
            cur[j] = min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
        prev = cur
    return prev[m], n


def read_wav(path):
    with wave.open(str(path), "rb") as w:
        return w.getparams(), w.readframes(w.getnframes())


def write_wav(path, params, frames):
    with wave.open(str(path), "wb") as w:
        w.setnchannels(params.nchannels)
        w.setsampwidth(params.sampwidth)
        w.setframerate(params.framerate)
        w.writeframes(frames)


# ---------- prep ----------
def cmd_prep(a):
    work = Path(a.work)
    raw = work / "fleurs_raw"
    out = work / "data"
    (out / "utt").mkdir(parents=True, exist_ok=True)
    (out / "short").mkdir(exist_ok=True)

    rows = []
    for line in (raw / "test.tsv").read_text(encoding="utf-8").splitlines():
        c = line.split("\t")
        if len(c) < 6:
            continue
        # id, file_name, raw_transcription, transcription, characters, num_samples, gender
        try:
            dur = int(c[5]) / 16000
        except ValueError:
            continue
        rows.append({"file": c[1], "ref": c[3], "dur": dur})
    cand = [r for r in rows if 6 <= r["dur"] <= 20]
    random.Random(42).shuffle(cand)
    chosen = cand[: a.n]
    want = {r["file"]: r for r in chosen}

    found = {}
    with tarfile.open(raw / "test.tar.gz", "r:gz") as tf:
        for m in tf:
            base = Path(m.name).name
            if base in want and m.isfile():
                tmp = out / "utt" / ("_raw_" + base)
                tmp.write_bytes(tf.extractfile(m).read())
                # FLEURS berformat float32; ubah ke PCM 16-bit mono 16 kHz
                subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(tmp), "-ac", "1",
                                "-ar", "16000", "-c:a", "pcm_s16le", str(out / "utt" / base)], check=True)
                tmp.unlink()
                found[base] = want[base]
                if len(found) == len(want):
                    break

    refs = {}
    for base, r in found.items():
        p = out / "utt" / base
        params, _ = read_wav(p)
        assert params.framerate == 16000 and params.nchannels == 1 and params.sampwidth == 2, (base, params)
        refs[f"utt/{base}"] = {"ref": r["ref"], "dur": r["dur"]}

    # klip pendek 3 detik (simulasi jendela "draft" live)
    for k, base in enumerate(list(found)[:10]):
        params, frames = read_wav(out / "utt" / base)
        n = 3 * params.framerate * params.sampwidth
        write_wav(out / "short" / f"s{k}.wav", params, frames[:n])
        refs[f"short/s{k}.wav"] = {"ref": None, "dur": 3.0}

    # klip panjang gabungan (uji kecepatan berkelanjutan)
    bases = list(found)[: a.long_n]
    params0, _ = read_wav(out / "utt" / bases[0])
    frames_all = b"".join(read_wav(out / "utt" / b)[1] for b in bases)
    write_wav(out / "long.wav", params0, frames_all)
    refs["long.wav"] = {
        "ref": " ".join(found[b]["ref"] for b in bases),
        "dur": sum(found[b]["dur"] for b in bases),
    }

    (out / "refs.json").write_text(json.dumps(refs, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"{len(found)} ujaran, {sum(v['dur'] for k, v in refs.items() if k.startswith('utt/')):.0f} detik; "
          f"klip panjang {refs['long.wav']['dur']:.0f} detik")


# ---------- push ----------
def cmd_push(a):
    work = Path(a.work)
    adb("shell", f"mkdir -p {DEV}/models {DEV}/data")
    adb("push", a.binary, f"{DEV}/whisper-cli")
    adb("shell", f"chmod +x {DEV}/whisper-cli")
    adb("push", str(work / "data") + "/.", f"{DEV}/data/")
    for m in a.models:
        f = work / "models" / f"ggml-{m}.bin"
        have = adb("shell", f"ls {DEV}/models/ggml-{m}.bin", check=False).returncode == 0
        if not have:
            print("push", f.name)
            adb("push", str(f), f"{DEV}/models/")
    print("selesai push")


# ---------- run ----------
TIMING = re.compile(r"(load time|mel time|sample time|encode time|decode time|total time)\s*=\s*([\d.]+)\s*ms")


def battery_temp():
    out = adb("shell", "dumpsys battery", check=False).stdout
    mt = re.search(r"temperature:\s*(\d+)", out)
    return int(mt.group(1)) / 10 if mt else None


def run_one(model, rel, threads, beam, ac=0, vad=False):
    ac_flag = f" -ac {ac}" if ac > 0 else ""
    vad_flag = " --vad -vm models/ggml-silero-v5.1.2.bin" if vad else ""
    cmd = (f"cd {DEV} && ./whisper-cli -m models/ggml-{model}.bin -f data/{rel} -l id "
           f"-t {threads} -nt -bs {beam} -bo {beam}{ac_flag}{vad_flag}")
    r = adb("shell", cmd, check=False)
    text_out = r.stdout + "\n" + r.stderr
    t = {k: float(v) for k, v in TIMING.findall(text_out)}
    lines = []
    for ln in r.stdout.splitlines():
        s = ln.strip()
        if not s or re.match(r"^(whisper_|ggml_|system_info|main:|read_audio_data|\[|load_backend|register_backend)", s):
            continue
        lines.append(s)
    return " ".join(lines), t


def cmd_run(a):
    work = Path(a.work)
    refs = json.loads((work / "data" / "refs.json").read_text(encoding="utf-8"))
    keys = [k for k in refs if (a.which == "all" or k.startswith(a.which))]
    if a.limit:
        keys = keys[: a.limit]
    res_path = work / "results.jsonl"
    for model in a.models:
        t0 = battery_temp()
        for k in keys:
            if a.ac == "auto":  # jendela encoder sebanding dengan durasi (50 frame/detik) + margin
                ac = min(1500, int(refs[k]["dur"] * 50) + 100)
            else:
                ac = int(a.ac)
            text, t = run_one(model, k, a.threads, a.beam, ac, a.vad)
            proc = (t.get("total time", 0) - t.get("load time", 0)) / 1000
            rec = {"model": model + ("+vad" if a.vad else ""), "file": k, "threads": a.threads, "beam": a.beam, "ac": a.ac,
                   "dur": refs[k]["dur"], "proc_s": proc, "load_s": t.get("load time", 0) / 1000,
                   "encode_s": t.get("encode time", 0) / 1000, "decode_s": t.get("decode time", 0) / 1000,
                   "text": text, "temp_c": battery_temp()}
            with res_path.open("a", encoding="utf-8") as f:
                f.write(json.dumps(rec, ensure_ascii=False) + "\n")
            print(f"{model:22s} {k:14s} dur={rec['dur']:5.1f}s proc={proc:5.2f}s RTF={proc / rec['dur']:.2f} "
                  f"T={rec['temp_c']}C", flush=True)
        print(f"== {model}: suhu baterai {t0} -> {battery_temp()} C", flush=True)


# ---------- sherpa-onnx (streaming) ----------
SHERPA = {
    "sherpa-multi": ("multi", "--encoder=multi/encoder-epoch-75-avg-11-chunk-16-left-128.int8.onnx "
                     "--decoder=multi/decoder-epoch-75-avg-11-chunk-16-left-128.onnx "
                     "--joiner=multi/joiner-epoch-75-avg-11-chunk-16-left-128.int8.onnx --tokens=multi/tokens.txt"),
    "sherpa-multi-vad": ("multi", "--encoder=multi/encoder-epoch-75-avg-11-chunk-16-left-128.int8.onnx "
                         "--decoder=multi/decoder-epoch-75-avg-11-chunk-16-left-128.onnx "
                         "--joiner=multi/joiner-epoch-75-avg-11-chunk-16-left-128.int8.onnx --tokens=multi/tokens.txt"),
    "sherpa-bookbot": ("bookbot", "--encoder=bookbot/encoder-epoch-30-avg-9-with-averaged-model.int8.onnx "
                       "--decoder=bookbot/decoder-epoch-30-avg-9-with-averaged-model.int8.onnx "
                       "--joiner=bookbot/joiner-epoch-30-avg-9-with-averaged-model.int8.onnx --tokens=bookbot/tokens.txt"),
}


def cmd_sherpa(a):
    work = Path(a.work)
    refs = json.loads((work / "data" / "refs.json").read_text(encoding="utf-8"))
    keys = [k for k in refs if k.startswith(a.which)]
    if a.limit:
        keys = keys[: a.limit]
    res_path = work / "results.jsonl"
    _, model_args = SHERPA[a.model]
    vad = a.model.endswith("-vad")
    for k in keys:
        if vad:  # VAD (Silero) memotong ucapan, lalu ASR streaming per segmen
            cmd = (f"cd /data/local/tmp/sherpa && LD_LIBRARY_PATH=lib ./bin/sherpa-onnx-vad-with-online-asr "
                   f"--silero-vad-model=silero_vad.onnx {model_args} --num-threads={a.threads} "
                   f"/data/local/tmp/bench/data/{k}")
        else:
            cmd = (f"cd /data/local/tmp/sherpa && LD_LIBRARY_PATH=lib ./bin/sherpa-onnx {model_args} "
                   f"--num-threads={a.threads} --decoding-method=greedy_search /data/local/tmp/bench/data/{k}")
        r = adb("shell", cmd, check=False)
        out = r.stdout + "\n" + r.stderr  # sherpa-onnx menulis hasil ke stderr
        el = re.search(r"Elapsed seconds:\s*([\d.]+)", out)
        if vad:
            segs = re.findall(r"vad segment\(\d+:[\d.]+-[\d.]+\) results:\s*(.*?)(?=vad segment\(|num threads:)", out, flags=re.S)
            words = " ".join(segs).split()
            # model multibahasa kadang memuntahkan aksara asing di awal segmen: buang token non-Latin
            text = " ".join(w for w in words if re.fullmatch(r"[A-Za-z0-9'\-]+", w))
        else:
            tx = re.search(r'"text":\s*"(.*?)",\s*"tokens"', out, flags=re.S)
            text = tx.group(1) if tx else ""
        rec = {"model": a.model, "file": k, "threads": a.threads, "beam": 1, "ac": "-",
               "dur": refs[k]["dur"], "proc_s": float(el.group(1)) if el else 0.0, "load_s": 0, "encode_s": 0,
               "decode_s": 0, "text": text, "temp_c": battery_temp()}
        with res_path.open("a", encoding="utf-8") as f:
            f.write(json.dumps(rec, ensure_ascii=False) + "\n")
        print(f"{a.model:16s} {k:30s} dur={rec['dur']:5.1f}s proc={rec['proc_s']:.1f}s | {rec['text'][:70]}", flush=True)


# ---------- report ----------
def cmd_report(a):
    work = Path(a.work)
    refs = json.loads((work / "data" / "refs.json").read_text(encoding="utf-8"))
    rows = [json.loads(l) for l in (work / "results.jsonl").read_text(encoding="utf-8-sig").splitlines() if l]
    groups = {}
    for r in rows:
        groups.setdefault((r["model"], r["threads"], r["beam"], str(r.get("ac", "0"))), []).append(r)

    print("| Model | Thread | Beam | AC | WER (ujaran) | RTF median | RTF p90 | Proses klip 3 dtk (median) | Proses ujaran (median) |")
    print("|---|---|---|---|---|---|---|---|---|")
    for (model, th, beam, ac), rs in sorted(groups.items()):
        utt = [r for r in rs if r["file"].startswith("utt/")]
        short = [r for r in rs if r["file"].startswith("short/")]
        errs = tot = 0
        for r in utt:
            e, n = wer(norm(refs[r["file"]]["ref"]), norm(r["text"]))
            errs += e
            tot += n
        w = f"{100 * errs / tot:.1f}%" if tot else "-"
        rtf = [r["proc_s"] / r["dur"] for r in utt] or [float("nan")]
        rtf_med = statistics.median(rtf)
        rtf_p90 = sorted(rtf)[int(0.9 * (len(rtf) - 1))]
        s3 = f"{statistics.median(r['proc_s'] for r in short):.2f} s" if short else "-"
        u12 = f"{statistics.median(r['proc_s'] for r in utt):.2f} s" if utt else "-"
        print(f"| {model} | {th} | {beam} | {ac} | {w} | {rtf_med:.2f} | {rtf_p90:.2f} | {s3} | {u12} |")

    longs = [r for r in rows if r["file"] in ("long.wav", "long_gap.wav")]
    if longs:
        print("\n| Klip panjang | Model | Thread | Durasi | Proses | RTF | WER | Suhu |")
        print("|---|---|---|---|---|---|---|---|")
        for r in longs:
            e, n = wer(norm(refs[r["file"]]["ref"]), norm(r["text"]))
            print(f"| {r['file']} | {r['model']} | {r['threads']} | {r['dur']:.0f} s | {r['proc_s']:.0f} s | "
                  f"{r['proc_s'] / r['dur']:.2f} | {100 * e / n:.1f}% | {r['temp_c']} C |")


def main():
    p = argparse.ArgumentParser()
    sub = p.add_subparsers(dest="cmd", required=True)
    for name in ("prep", "push", "run", "report", "sherpa"):
        s = sub.add_parser(name)
        s.add_argument("--work", required=True)
        if name == "prep":
            s.add_argument("--n", type=int, default=30)
            s.add_argument("--long-n", type=int, default=20)
        if name == "push":
            s.add_argument("--binary", required=True)
            s.add_argument("--models", nargs="+", required=True)
        if name == "run":
            s.add_argument("--models", nargs="+", required=True)
            s.add_argument("--threads", type=int, default=4)
            s.add_argument("--beam", type=int, default=1)
            s.add_argument("--ac", default="0", help="audio context encoder: 0 (penuh 1500) | angka | auto")
            s.add_argument("--which", default="all", help="all | utt | short | long")
            s.add_argument("--limit", type=int, default=0, help="batasi jumlah berkas (0 = semua)")
            s.add_argument("--vad", action="store_true", help="pakai VAD Silero bawaan whisper.cpp")
        if name == "sherpa":
            s.add_argument("--model", required=True, choices=list(SHERPA))
            s.add_argument("--threads", type=int, default=4)
            s.add_argument("--which", default="utt")
            s.add_argument("--limit", type=int, default=0)
    a = p.parse_args()
    {"prep": cmd_prep, "push": cmd_push, "run": cmd_run, "report": cmd_report, "sherpa": cmd_sherpa}[a.cmd](a)


if __name__ == "__main__":
    sys.exit(main())
