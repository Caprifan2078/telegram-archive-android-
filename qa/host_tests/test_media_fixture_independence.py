#!/usr/bin/env python3
"""Offline regression: ordinary and round MP4 decoder tests must use distinct media.

Assets are generated/synthetic. This is a fixture-integrity check, NOT
verification of Android's codecs (which requires instrumentation).
"""
from pathlib import Path
import hashlib

root=Path(__file__).resolve().parents[2]
assets=root/"app"/"src"/"androidTest"/"assets"
round_video=(assets/"short-round.mp4").read_bytes()
regular_video=(assets/"regular-video.mp4").read_bytes()
assert round_video != regular_video, "Ordinary MP4 fixture duplicates the round video"
for label, data in (("round", round_video), ("regular", regular_video)):
    assert len(data) >= 1000, f"{label} fixture too small/truncated"
    assert data[4:8] == b"ftyp", f"{label} fixture not a valid MP4 header"
    assert b"moov" in data and b"mdat" in data, f"{label} MP4 missing required boxes"
assert b"avc1" in regular_video, "Expected synthetic H.264 video stream in ordinary fixture"
test=(root/"app/src/androidTest/java/org/openarchive/telegram/MediaAssetsRegressionTest.java").read_text(encoding="utf-8")
assert 'verifyMp4("regular-video.mp4")' in test, "Android decoder test uses wrong ordinary fixture"
assert 'verifyMp4("short-round.mp4")' in test, "Android decoder test lost round fixture"
print("PASS independent synthetic MP4 fixtures")
print("round sha256:", hashlib.sha256(round_video).hexdigest())
print("regular sha256:", hashlib.sha256(regular_video).hexdigest())
