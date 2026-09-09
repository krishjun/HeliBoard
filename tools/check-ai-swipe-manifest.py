#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Fail CI when a cloud/offline build crosses its declared Android package boundary."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def main() -> None:
    if len(sys.argv) != 2 or sys.argv[1] not in ("true", "false"):
        raise SystemExit("usage: check-ai-swipe-manifest.py true|false")
    cloud = sys.argv[1] == "true"
    paths = list(Path("app/build/intermediates/merged_manifests/debugNoMinify").rglob("AndroidManifest.xml"))
    if len(paths) != 1:
        raise SystemExit(f"Expected exactly one debugNoMinify merged manifest, found {len(paths)}")
    root = ET.parse(paths[0]).getroot()
    android = "{http://schemas.android.com/apk/res/android}"
    package = "helium314.keyboard.ai.debug" if cloud else "helium314.keyboard.debug"
    assert root.get("package") == package, "Unexpected package ID"
    permissions = {p.get(android + "name") for p in root.findall("uses-permission")}
    assert ("android.permission.INTERNET" in permissions) == cloud, "Network permission boundary violated"
    app = root.find("application")
    assert app is not None
    providers = {p.get(android + "name"): p for p in app.findall("provider")}
    assert "com.google.firebase.provider.FirebaseInitProvider" not in providers, "Firebase starts before consent"
    for name, suffix in {
        "helium314.keyboard.latin.database.ClipboardContentProvider": ".clipprovider",
        "helium314.keyboard.settings.screens.gesturedata.GestureFileProvider": ".provider",
    }.items():
        assert providers[name].get(android + "authorities") == package + suffix, "Conflicting file provider authority"
    if cloud:
        assert app.get(android + "usesCleartextTraffic") == "false"
        metadata = {m.get(android + "name"): m.get(android + "value") for m in app.findall("meta-data")}
        assert metadata.get("firebase_data_collection_default_enabled") == "false"
    assert root.find("uses-sdk").get(android + "minSdkVersion") == ("23" if cloud else "21")
    print(f"Verified {package}: permission, startup, provider authorities and minimum SDK")


if __name__ == "__main__":
    main()
