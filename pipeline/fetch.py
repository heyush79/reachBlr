"""Download the pinned source data into pipeline/raw/.

Every file is pinned to a git commit, so re-running this gives the same bytes.
The SHA-256 check catches a changed or broken download.

Usage:  python pipeline/fetch.py            (skips files already downloaded)
        python pipeline/fetch.py --force
"""
import hashlib
import sys
import zipfile
from pathlib import Path

import requests

RAW = Path(__file__).resolve().parent / "raw"
UA = {"User-Agent": "bengaluru-commute-map-pipeline/1.0"}

SOURCES = [
    dict(
        name="bmrcl",
        desc="Namma Metro GTFS (unofficial, Vonter/bmrcl-gtfs), 17 Aug 2026",
        url="https://raw.githubusercontent.com/Vonter/bmrcl-gtfs/"
            "f402c4c69a5d314320024f84620fa83e909c4188/gtfs/bmrcl.zip",
        file="bmrcl.zip",
        sha256="c19f56f32ad53525c71191125409dc8dded12dd2cebfc576ad9a4db23a62b8a7",
        unzip=True,
    ),
    dict(
        name="bmtc",
        desc="BMTC bus GTFS (unofficial, Vonter/bmtc-gtfs), 9 Nov 2026",
        url="https://raw.githubusercontent.com/Vonter/bmtc-gtfs/"
            "4ced54ed79c7e33bb66fef87f1f1e79d21709a5c/gtfs/bmtc.zip",
        file="bmtc.zip",
        sha256="812c7d472ff116024ca1e0a7ff6e28662f762ca09265da8d5c7e9f6241cf8a45",
        unzip=True,
    ),
    dict(
        name="wards",
        desc="BBMP 243 wards (DataMeet Municipal_Spatial_Data, CC BY 4.0)",
        url="https://raw.githubusercontent.com/datameet/Municipal_Spatial_Data/"
            "0c3a2e3dd2e87c514817378d6c73a9dd2ffb8f69/Bangalore/BBMP.geojson",
        file="BBMP.geojson",
        sha256="4a1186d15da9396c259a2c01b135ee5e903948d45de6e91c49e0ddd72f359a7b",
        unzip=False,
    ),
]


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def download(url: str, dest: Path) -> None:
    tmp = dest.with_suffix(dest.suffix + ".part")
    with requests.get(url, headers=UA, stream=True, timeout=60) as r:
        r.raise_for_status()
        total = int(r.headers.get("content-length", 0))
        done = 0
        with open(tmp, "wb") as f:
            for chunk in r.iter_content(1 << 20):
                f.write(chunk)
                done += len(chunk)
                if total:
                    print(f"\r  {done / 1e6:6.1f} / {total / 1e6:.1f} MB", end="", flush=True)
    print()
    tmp.rename(dest)


def main() -> int:
    force = "--force" in sys.argv
    ok = True
    for s in SOURCES:
        folder = RAW / s["name"]
        folder.mkdir(parents=True, exist_ok=True)
        dest = folder / s["file"]
        print(f"{s['name']}: {s['desc']}")
        if force or not dest.exists():
            download(s["url"], dest)
        got = sha256(dest)
        if s["sha256"] and got != s["sha256"]:
            print(f"  HASH MISMATCH\n  expected {s['sha256']}\n  got      {got}")
            ok = False
            continue
        print(f"  sha256 {got}" + ("" if s["sha256"] else "  (no pinned hash yet)"))
        if s["unzip"]:
            with zipfile.ZipFile(dest) as z:
                for member in z.namelist():
                    # GTFS files are flat; refuse anything with a path in it
                    if "/" in member or "\\" in member or member.startswith("."):
                        continue
                    z.extract(member, folder)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
