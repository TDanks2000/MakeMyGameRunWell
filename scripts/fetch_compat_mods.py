"""Fetch pinned upstream releases for local compatibility testing. Does not package them in this mod.

Usage: python scripts/fetch_compat_mods.py [profile ...]   (default: every profile)
Each profile is downloaded to .bootstrap/compat/<profile>/<minecraft version>/<loader>/ and used by
`gradlew :<loader>-<minecraft version>:runClient -PsmokeTest -PcompatTest=<profile>`.
"""
import hashlib
import json
import sys
import urllib.parse
from pathlib import Path
from urllib.request import Request, urlopen

DESTINATION = Path(__file__).resolve().parents[1] / ".bootstrap" / "compat"
GAME_VERSION = "26.2"
LOADERS = ("fabric", "neoforge")

# Modrinth project slug -> exact version_number per loader. A loader missing here is skipped for that project.
PROJECTS = {
    "sodium": {"fabric": "mc26.2-0.9.2-fabric", "neoforge": "mc26.2-0.9.2-neoforge"},
    "lithium": {"fabric": "mc26.2-0.25.3-fabric", "neoforge": "mc26.2-0.25.3-neoforge"},
    "sodium-extra": {"fabric": "mc26.2-0.9.4+fabric", "neoforge": "mc26.2-0.9.4+neoforge"},
    "reeses-sodium-options": {"fabric": "mc26.2-2.2.4+fabric", "neoforge": "mc26.2-2.2.4+neoforge"},
    "iris": {"fabric": "1.11.4+26.2-fabric", "neoforge": "1.11.4+26.2-neoforge"},
    "ferrite-core": {"fabric": "9.0.0-fabric", "neoforge": "9.0.0-neoforge"},
    "immediatelyfast": {"fabric": "1.16.5+26.2-fabric", "neoforge": "1.16.5+26.2-neoforge"},
    "entityculling": {"fabric": "1.11.3", "neoforge": "1.11.3"},
    "moreculling": {"fabric": "1.8.1", "neoforge": "1.8.1"},
    "badoptimizations": {"fabric": "2.4.1", "neoforge": "2.4.1"},
    "dynamic-fps": {"fabric": "3.11.9", "neoforge": "3.11.9"},
    "scalablelux": {"fabric": "0.2.1+fabric.2b08348", "neoforge": "0.2.1+neoforge.729d9d3"},
    "cloth-config": {"fabric": "26.2.155+fabric", "neoforge": "26.2.155+neoforge"},
    "yacl": {"fabric": "3.9.7+26.2-fabric", "neoforge": "3.9.7+26.2-neoforge"},
    "configured-defaults": {"fabric": "26.2.0", "neoforge": "26.2.0"},
    "modmenu": {"fabric": "20.0.3"},
    "placeholder-api": {"fabric": "3.1.0-beta.1+26.2"},
    "distanthorizons": {"fabric": "3.3.3-26.2", "neoforge": "3.3.3-26.2"},
    "entitytexturefeatures": {"fabric": "7.2.6-fabric-26.2", "neoforge": "7.2.6-neoforge-26.2"},
    "entity-model-features": {"fabric": "3.3.12-fabric-26.2", "neoforge": "3.3.12-neoforge-26.2"},
    "continuity": {"fabric": "3.0.1+26.2"},
    "lambdynamiclights": {"fabric": "4.12.4+26.2"},
    "not-enough-animations": {"fabric": "1.12.5", "neoforge": "1.12.5"},
    "bobby": {"fabric": "5.2.15+mc26.2"},
    "geckolib": {"fabric": "5.5.5", "neoforge": "5.5.6"},
    "xaeros-minimap": {"fabric": "fabric-26.2-26.6.0", "neoforge": "neoforge-26.2-26.6.0"},
    "xaeros-world-map": {"fabric": "fabric-26.2-1.47.0", "neoforge": "neoforge-26.2-1.47.0"},
    "journeymap": {"fabric": "26.2-6.0.10+fabric", "neoforge": "26.2-6.0.10+neoforge"},
    "appleskin": {"fabric": "3.0.10+mc26.2", "neoforge": "3.0.10+mc26.2"},
    "jade": {"fabric": "26.2.11+fabric", "neoforge": "26.2.10+neoforge"},
    "jei": {"fabric": "30.42.0.236", "neoforge": "30.42.0.236"},
    "mezzconfig": {"fabric": "0.6.8", "neoforge": "0.6.8"},
    "mouse-tweaks": {"fabric": "26.2-2.31-fabric", "neoforge": "26.2-2.31-neoforge"},
    "controlling": {"fabric": "26.2.4", "neoforge": "26.2.4"},
    "searchables": {"fabric": "1.0.1", "neoforge": "1.0.1"},
    "no-chat-reports": {"fabric": "Fabric-26.2-v2.20.2", "neoforge": "NeoForge-26.2-v2.20.2"},
    "language-reload": {"fabric": "1.7.8+26.2"},
    "betterf3": {"fabric": "19.0.1", "neoforge": "19.0.1"},
    "chat-heads": {"fabric": "1.3.2", "neoforge": "1.3.2"},
    "freecam": {"fabric": "1.5.0", "neoforge": "1.5.0"},
    "carry-on": {"fabric": "2.11.2", "neoforge": "2.11.2"},
    "architectury-api": {"fabric": "21.1.12+fabric", "neoforge": "21.1.12+neoforge"},
    "simple-voice-chat": {"fabric": "fabric-2.6.22+26.2", "neoforge": "neoforge-2.6.22+26.2"},
    "malilib": {"fabric": "0.29.6"},
    "litematica": {"fabric": "0.28.8"},
    "tweakeroo": {"fabric": "0.29.5"},
    "minihud": {"fabric": "0.40.7"},
    "replaymod": {"fabric": "26.2-2.6.28"},
}

VIDEO_SETTINGS = ["sodium", "sodium-extra", "reeses-sodium-options", "iris"]
PERFORMANCE = ["lithium", "ferrite-core", "immediatelyfast", "entityculling", "moreculling", "cloth-config",
               "badoptimizations", "dynamic-fps", "scalablelux"]
CONFIG_LIBRARIES = ["modmenu", "placeholder-api", "cloth-config", "yacl", "configured-defaults"]

RENDERING_EXTRAS = ["sodium", "entitytexturefeatures", "entity-model-features", "continuity", "lambdynamiclights",
                    "not-enough-animations", "bobby", "geckolib"]
UI_AND_UTILITY = ["xaeros-minimap", "xaeros-world-map", "journeymap", "appleskin", "jade", "jei", "mezzconfig",
                  "mouse-tweaks", "controlling", "searchables", "no-chat-reports", "language-reload", "betterf3",
                  "cloth-config", "chat-heads", "freecam", "carry-on", "architectury-api", "simple-voice-chat"]
TECHNICAL = ["malilib", "litematica", "tweakeroo", "minihud", "replaymod"]

PROFILES = {
    "base": ["sodium", "lithium"],
    "video-settings": VIDEO_SETTINGS,
    "performance": ["sodium"] + PERFORMANCE,
    "config-libraries": CONFIG_LIBRARIES,
    "distant-horizons": ["distanthorizons"],
    "modpack": list(dict.fromkeys(VIDEO_SETTINGS + PERFORMANCE + CONFIG_LIBRARIES)),
    "rendering-extras": RENDERING_EXTRAS,
    "ui-and-utility": UI_AND_UTILITY,
    "technical": TECHNICAL,
    "kitchen-sink": list(dict.fromkeys(VIDEO_SETTINGS + PERFORMANCE + CONFIG_LIBRARIES + RENDERING_EXTRAS + UI_AND_UTILITY)),
}


def fetch(url):
    request = Request(url, headers={"User-Agent": "MakeMyGameRunWell/0.1 compatibility-test"})
    with urlopen(request, timeout=60) as response:
        return response.read()


def find_release(project, loader, version_number):
    query = urllib.parse.urlencode({"loaders": json.dumps([loader]), "game_versions": json.dumps([GAME_VERSION])})
    versions = json.loads(fetch(f"https://api.modrinth.com/v2/project/{project}/version?{query}"))
    for version in versions:
        if version["version_number"] == version_number:
            return version
    raise ValueError(f"{project} {version_number} for {loader} {GAME_VERSION} was not found")


def download(project, loader, directory):
    release = find_release(project, loader, PROJECTS[project][loader])
    file = next(file for file in release["files"] if file["primary"])
    target = directory / file["filename"]
    if target.exists() and hashlib.sha512(target.read_bytes()).hexdigest() == file["hashes"]["sha512"]:
        print(f"Cached   {target.relative_to(DESTINATION)}")
        return
    contents = fetch(file["url"])
    if hashlib.sha512(contents).hexdigest() != file["hashes"]["sha512"]:
        raise ValueError(f"Checksum mismatch: {file['filename']}")
    target.write_bytes(contents)
    print(f"Verified {target.relative_to(DESTINATION)}")


def main(requested):
    unknown = [name for name in requested if name not in PROFILES]
    if unknown:
        raise SystemExit(f"Unknown profile {unknown}; choose from {sorted(PROFILES)}")
    for profile in requested or PROFILES:
        for loader in LOADERS:
            projects = [project for project in PROFILES[profile] if loader in PROJECTS[project]]
            if not projects:
                continue
            directory = DESTINATION / profile / GAME_VERSION / loader
            directory.mkdir(parents=True, exist_ok=True)
            for project in projects:
                download(project, loader, directory)


if __name__ == "__main__":
    main(sys.argv[1:])
