#!/usr/bin/env python3
"""Move feature handlers from game-server-core into mod modules (keep package).

Copies sources into modules/mods/<mod>/src/main/java/... then replaces the core
file with a relocation marker so core no longer imports feature packages.
"""
from __future__ import annotations

import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CORE = ROOT / "modules/game-server-core/src/main/java"
MODS = ROOT / "modules/mods"

MOVES = [
    ("ext/mods/gameserver/handler/admincommandhandlers/AdminFarmEvent.java", "mod-farm-event"),
    ("ext/mods/gameserver/handler/voicedcommandhandlers/skins.java", "mod-dressme"),
    ("ext/mods/gameserver/handler/voicedcommandhandlers/VoicedTour.java", "mod-tour"),
    ("ext/mods/gameserver/handler/voicedcommandhandlers/VoicedTournamentRank.java", "mod-tour"),
    ("ext/mods/gameserver/handler/voicedcommandhandlers/Email.java", "mod-email"),
    ("ext/mods/gameserver/handler/voicedcommandhandlers/RouletteVoiced.java", "mod-roulette"),
    ("ext/mods/gameserver/handler/voicedcommandhandlers/VoicedBossBattle.java", "mod-battle-boss"),
    ("ext/mods/gameserver/handler/voicedcommandhandlers/FarmZoneTeleport.java", "mod-farm-event"),
    ("ext/mods/gameserver/handler/bypasshandlers/FarmZoneTeleportBypass.java", "mod-farm-event"),
    ("ext/mods/gameserver/handler/itemhandlers/CapsuleBox_System.java", "mod-capsule-box"),
    ("ext/mods/gameserver/handler/itemhandlers/ItemMonsterSummon.java", "mod-summon-mob"),
]

MARKER = """// RELOCATED — this handler now lives under modules/mods/{mod}/
// Package remains ext.mods.gameserver.handler.* for AbstractHandler scan when the mod jar is present.
// Intentionally not compiled by game-server-core (see build.gradle.kts sourceSets excludes).
"""


def main() -> None:
    moved = []
    for rel, mod in MOVES:
        src = CORE / rel
        dst = MODS / mod / "src/main/java" / rel
        if not src.exists():
            # Maybe already moved
            if dst.exists():
                print(f"SKIP already moved: {rel}")
                continue
            print(f"MISSING: {src}")
            continue
        # If core still has real content (not marker), copy then mark
        text = src.read_text(encoding="utf-8", errors="replace")
        if "RELOCATED" in text and "modules/mods/" in text:
            print(f"SKIP marker already: {rel}")
            continue
        dst.parent.mkdir(parents=True, exist_ok=True)
        if not dst.exists() or dst.read_text(encoding="utf-8", errors="replace") != text:
            dst.write_text(text, encoding="utf-8")
            print(f"COPIED {rel} -> {mod}")
        src.write_text(MARKER.format(mod=mod), encoding="utf-8")
        print(f"MARKED core {rel}")
        moved.append(rel)
    print(f"Done. Processed {len(moved)} files.")


if __name__ == "__main__":
    main()
