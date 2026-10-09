#!/usr/bin/env python3
"""Fetch pinned dependencies; Linux/macOS counterpart of fetch-model.ps1."""
import hashlib
import json
from pathlib import Path
import urllib.request

root = Path(__file__).resolve().parent.parent
items = json.loads((root / "android/dependencies.json").read_text())
for item in items:
    target = (root / item["path"]).resolve()
    if root not in target.parents:
        raise ValueError("Dependency path leaves project")
    target.parent.mkdir(parents=True, exist_ok=True)
    if not target.exists():
        temporary = target.with_suffix(target.suffix + ".download")
        with urllib.request.urlopen(item["url"], timeout=120) as response:
            temporary.write_bytes(response.read())
        if hashlib.sha256(temporary.read_bytes()).hexdigest() != item["sha"]:
            temporary.unlink()
            raise ValueError("SHA256 mismatch: " + item["path"])
        temporary.replace(target)
    if hashlib.sha256(target.read_bytes()).hexdigest() != item["sha"]:
        raise ValueError("SHA256 mismatch: " + item["path"])
    print("Verified", item["path"])
