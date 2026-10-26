#!/usr/bin/env python3
"""Run the production HTML reader on one million generated messages at -Xmx64m.
Requires JDK 17 and JSOUP_JAR pointing at the project's jsoup 1.21.2 dependency.
No Android runtime or private exports are used.
"""
import os
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
jar = Path(os.environ["JSOUP_JAR"]).resolve()
if not jar.is_file():
    raise SystemExit("JSOUP_JAR must point at jsoup-1.21.2.jar")
with tempfile.TemporaryDirectory(prefix="archive-stream-") as classes:
    subprocess.run([
        "javac", "-encoding", "UTF-8", "-cp", str(jar), "-d", classes,
        str(root / "app/src/main/java/org/openarchive/telegram/TelegramHtmlStream.java"),
        str(root / "qa/host_tests/StreamingHtmlStress.java"),
    ], check=True)
    subprocess.run([
        "java", "-Xmx64m", "-cp", classes + os.pathsep + str(jar),
        "org.openarchive.telegram.StreamingHtmlStress", "1000000",
    ], check=True)
