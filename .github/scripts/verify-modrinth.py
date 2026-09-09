#!/usr/bin/env python3
"""Checks that every version of this release is actually installable on Modrinth.

Names alone are not enough: a version can be created with its file, loader or game
version missing and still be listed, which is how a release once went out that Fabric
players could not install.
"""
import json
import sys

EXPECTED = {
    "mc1.20.1-fabric":    ("fabric",   "1.20.1"),
    "mc1.21.1-fabric":    ("fabric",   "1.21.1"),
    "mc1.21.11-fabric":   ("fabric",   "1.21.11"),
    "mc1.21.1-neoforge":  ("neoforge", "1.21.1"),
    "mc1.21.11-neoforge": ("neoforge", "1.21.11"),
    "mc26.1.2-neoforge":  ("neoforge", "26.1.2"),
    "mc26.2-neoforge":    ("neoforge", "26.2"),
}


def main(version, path):
    with open(path, encoding="utf-8") as f:
        published = {v["version_number"]: v for v in json.load(f)}

    broken = []
    for suffix, (loader, game) in EXPECTED.items():
        name = f"{version}+{suffix}"
        got = published.get(name)
        if got is None:
            broken.append(f"{name}: missing entirely")
            continue
        problems = []
        if not got.get("files"):
            problems.append("no file")
        if loader not in got.get("loaders", []):
            problems.append(f"loaders={got.get('loaders')}")
        if game not in got.get("game_versions", []):
            problems.append(f"game_versions={got.get('game_versions')}")
        if problems:
            broken.append(f"{name}: " + ", ".join(problems))
        else:
            print(f"ok: {name}")

    if broken:
        for line in broken:
            print(f"BROKEN: {line}")
        print("::error::Modrinth accepted the upload but these versions are not usable. "
              "Delete them on Modrinth, then re-run this workflow with force_publish.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
