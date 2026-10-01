"""Ordinary wild slots of the Generation 4 games, per tracker area name (2026-09-29), for gen4_statics.py.

A static row is "a wild battle at PLACE at exactly LEVEL". If an ordinary wild slot of the same area can also produce that
level, an ordinary encounter would be read as the static and would not use up the area's first encounter. This module
lists those slots so the statics file can stay honest about it:

  * Platinum: pret pokeplatinum res/field/encounters/<table>.json, the table named by each map header's
    wildEncountersArchiveID (include/data/map_headers.h), the header ids from generated/map_headers.txt.
  * HeartGold and SoulSilver: pret pokeheartgold files/fielddata/encountdata/gs_enc_data.json (both versions in one
    file), the table named by each map's wildEncounterBank, and files/arc/headbutt.json (Headbutt trees), the map ids
    and zone codes from include/constants/maps.h.
  * Diamond and Pearl: the wild-encounter member (424 bytes) of fielddata/encountdata/d_enc_data.narc and
    p_enc_data.narc in the clean Diamond dump (both versions' tables are on every DS cartridge), the table index being the
    wild_encounter_bank of the header in arm9's map header table. The 424-byte layout is the one pokeplatinum's JSON
    is built from; it reproduces that JSON exactly for all Platinum members (checked by the parity test at the bottom).

An entry is (kind, species id, lowest level, highest level, method text). kind is "walk" for anything that is met by
walking or interacting on the ground (grass and cave floors, swarms, the radio, the Poke Radar, the dual-slot slots,
Rock Smash, Headbutt) and "water" for surfing and fishing.
"""
import io
import json
import os
import re
import struct
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen4_common as C  # noqa: E402
import gen4_nds as N  # noqa: E402

# slot groups of a 12-slot Gen 4 land table
SWARM_SLOTS = (0, 1)
TOD_SLOTS = (2, 3)
RADAR_SLOTS = (4, 5, 10, 11)
DUAL_SLOTS = (8, 9)


class Encounters:
    def __init__(self, dp_rom=None):
        self.by_const, self.rev_const = C.species_by_const()
        self._pt_headers = None
        self._hg_headers = None
        self._hg_data = None
        self._dp = None
        self._dp_rom = dp_rom

    # ---------------------------------------------------------------- Platinum
    def _pt(self):
        if self._pt_headers is None:
            ids = [l.strip() for l in C.pret_text("pokeplatinum", "generated/map_headers.txt").split("\n") if l.strip()]
            text = C.pret_text("pokeplatinum", "include/data/map_headers.h")
            blocks = dict(re.findall(r"\[(MAP_HEADER_\w+)\]\s*=\s*\{(.*?)\n    \},", text, re.S))
            enc = {}
            for i, c in enumerate(ids):
                m = re.search(r"\.wildEncountersArchiveID\s*=\s*(\w+)", blocks.get(c, ""))
                enc[i] = m.group(1) if m else None
            self._pt_headers = enc
        return self._pt_headers

    def pt_entries_for(self, table):
        d = C.pret_json("pokeplatinum", "res/field/encounters/%s.json" % table)
        sid = self.by_const
        out = []
        land = d["land_encounters"]
        for i, e in enumerate(land):
            if e["species"] != "SPECIES_NONE" and e["level"]:
                out.append(("walk", sid[e["species"]], e["level"], e["level"], "land slot %d" % i))

        def repl(key, slots, label):
            for k, s in enumerate(d[key]):
                if k < len(slots) and s != "SPECIES_NONE":
                    lv = land[slots[k]]["level"]
                    out.append(("walk", sid[s], lv, lv, "%s (replaces land slot %d)" % (label, slots[k])))
        repl("swarms", SWARM_SLOTS, "swarm")
        repl("day", TOD_SLOTS, "day")
        repl("night", TOD_SLOTS, "night")
        repl("radar", RADAR_SLOTS, "poke radar")
        for g in ("ruby", "sapphire", "emerald", "firered", "leafgreen"):
            repl(g, DUAL_SLOTS, "dual-slot " + g)
        for key, label in (("surf_encounters", "surf"), ("old_rod_encounters", "old rod"),
                           ("good_rod_encounters", "good rod"), ("super_rod_encounters", "super rod")):
            for i, e in enumerate(d.get(key, [])):
                if e["species"] != "SPECIES_NONE" and e["level_max"]:
                    out.append(("water", sid[e["species"]], e["level_min"], e["level_max"], "%s slot %d" % (label, i)))
        return out

    def pt(self, place, level, tracker):
        res = []
        for hid, enc in self._pt().items():
            if tracker.get(hid) != place or not enc or enc == "ENCOUNTERS_NONE":
                continue
            for e in self.pt_entries_for(enc):
                if e[2] <= level <= e[3]:
                    res.append((hid,) + e)
        return res

    # ---------------------------------------------------------------- HeartGold and SoulSilver
    def _hg(self):
        if self._hg_headers is None:
            maps = {}
            codes = {}
            for line in C.pret_text("pokeheartgold", "include/constants/maps.h").split("\n"):
                m = re.match(r"#define\s+(MAP_\w+)\s+(\d+)\s*(?://\s*MAP_(\w+))?", line)
                if m and m.group(1) != "MAP_ID_MAX":
                    maps[m.group(1)] = int(m.group(2))
                    codes[int(m.group(2))] = m.group(3)
            text = C.pret_text("pokeheartgold", "src/data/map_headers.h")
            parts = re.split(r"\n\s*\[(MAP_\w+)\]\s*=\s*\{", text)
            enc = {}
            for i in range(1, len(parts), 2):
                c, body = parts[i], parts[i + 1]
                body = body.split("\n    },")[0] if "\n    }," in body else body
                m = re.search(r"\.wildEncounterBank\s*=\s*([^,\n]+)", body)
                if c in maps:
                    enc[maps[c]] = (m.group(1).strip() if m else None, codes.get(maps[c]))
            self._hg_headers = enc
            self._hg_data = (
                {e["map"]: e for e in C.pret_json("pokeheartgold",
                                                  "files/fielddata/encountdata/gs_enc_data.json")["encounters"]},
                {t["Map"]: t for t in C.pret_json("pokeheartgold", "files/arc/headbutt.json")["tables"]},
            )
        return self._hg_headers, self._hg_data

    @staticmethod
    def _ver(v, ver):
        if isinstance(v, dict):
            if "HEARTGOLD" in v:
                return v["HEARTGOLD" if ver == "hg" else "SOULSILVER"]
            if "gold" in v:
                return v["gold" if ver == "hg" else "silver"]
        return v

    def hg_entries_for(self, code, ver, data):
        e = data[code]
        sid = self.by_const
        V = self._ver
        out = []
        land = e["land"]["mons"]
        lv = [V(m["level"], ver) for m in land]
        for i, m in enumerate(land):
            for tod in ("morn", "day", "nite"):
                s = V(m["species"][tod], ver)
                if s and s != "SPECIES_NONE":
                    out.append(("walk", sid[s], lv[i], lv[i], "land slot %d %s" % (i, tod)))
        for key, label in (("hoenn", "hoenn radio"), ("sinnoh", "sinnoh radio")):
            for k, s in enumerate(e.get(key, [])):
                s = V(s, ver)
                for slot in ((2, 3) if k == 0 else (4, 5)):
                    if s and s != "SPECIES_NONE" and slot < len(lv):
                        out.append(("walk", sid[s], lv[slot], lv[slot], "%s (replaces land slot %d)" % (label, slot)))
        ls = V(e.get("landSwarm"), ver) if e.get("landSwarm") else None
        if ls and ls != "SPECIES_NONE":
            for slot in (0, 1):
                out.append(("walk", sid[ls], lv[slot], lv[slot], "land swarm (replaces land slot %d)" % slot))

        def mons(seq):
            r = []
            for m in seq:
                l = m["level"]
                r.append((V(m["species"], ver), V(l["min"], ver), V(l["max"], ver)))
            return r
        surf = mons(e["surf"]["mons"])
        for i, (s, lo, hi) in enumerate(surf):
            if s != "SPECIES_NONE":
                out.append(("water", sid[s], lo, hi, "surf slot %d" % i))
        ss = V(e.get("surfSwarm"), ver) if e.get("surfSwarm") else None
        if ss and ss != "SPECIES_NONE" and surf:
            out.append(("water", sid[ss], surf[0][1], surf[0][2], "surf swarm (replaces surf slot 0)"))
        for i, (s, lo, hi) in enumerate(mons(e["rock_smash"]["mons"])):
            if s != "SPECIES_NONE":
                out.append(("walk", sid[s], lo, hi, "rock smash slot %d" % i))
        fish = e["fishing"]
        rods = {}
        for key, label in (("old_rod", "old rod"), ("good_rod", "good rod"), ("super_rod", "super rod")):
            rods[label] = mons(fish[key]["mons"])
            for i, (s, lo, hi) in enumerate(rods[label]):
                if s != "SPECIES_NONE":
                    out.append(("water", sid[s], lo, hi, "%s slot %d" % (label, i)))
        nf = V(e.get("nightFish"), ver) if e.get("nightFish") else None
        if nf and nf != "SPECIES_NONE":
            if len(rods["good rod"]) > 3:
                out.append(("water", sid[nf], rods["good rod"][3][1], rods["good rod"][3][2],
                            "night fishing (replaces good rod slot 3)"))
            if len(rods["super rod"]) > 1:
                out.append(("water", sid[nf], rods["super rod"][1][1], rods["super rod"][1][2],
                            "night fishing (replaces super rod slot 1)"))
        fsw = V(e.get("fishSwarm"), ver) if e.get("fishSwarm") else None
        if fsw and fsw != "SPECIES_NONE":
            for label, idxs in (("old rod", (2,)), ("good rod", (0, 2, 3)), ("super rod", (0, 1, 2, 3, 4))):
                for i in idxs:
                    if i < len(rods[label]):
                        out.append(("water", sid[fsw], rods[label][i][1], rods[label][i][2],
                                    "fishing swarm (replaces %s slot %d)" % (label, i)))
        return out

    def hg_headbutt_for(self, zone, ver, hb):
        t = hb.get(zone)
        if not t or not t["Trees"]:
            return []
        out = []
        for key, label in (("CommonMons", "headbutt common"), ("RareMons", "headbutt rare"),
                           ("SecretMons", "headbutt secret")):
            for i, m in enumerate(t[key]):
                out.append(("walk", self.by_const[self._ver(m["species"], ver)], m["minLevel"], m["maxLevel"],
                            "%s slot %d" % (label, i)))
        return out

    def hg(self, place, level, tracker, versions):
        headers, (data, hb) = self._hg()
        res = []
        for mid, (enc, code) in headers.items():
            if tracker.get(mid) != place:
                continue
            for ver in versions:
                ents = []
                if enc and enc.startswith("ENCDATA_") and enc != "ENCDATA_NA":
                    c = enc[len("ENCDATA_"):]
                    if c in data:
                        ents += self.hg_entries_for(c, ver, data)
                if code in hb:
                    ents += self.hg_headbutt_for(code, ver, hb)
                for e in ents:
                    if e[2] <= level <= e[3]:
                        res.append((mid, ver) + e)
        return res

    # ---------------------------------------------------------------- Diamond and Pearl (Diamond dump)
    @staticmethod
    def parse_member(b):
        assert len(b) == 424
        o = {"land": [struct.unpack_from("<II", b, 4 + 8 * i) for i in range(12)],
             "swarms": list(struct.unpack_from("<2I", b, 100)),
             "day": list(struct.unpack_from("<2I", b, 108)),
             "night": list(struct.unpack_from("<2I", b, 116)),
             "radar": list(struct.unpack_from("<4I", b, 124)),
             "dual": {k: list(struct.unpack_from("<2I", b, 164 + 8 * i))
                      for i, k in enumerate(("ruby", "sapphire", "emerald", "firered", "leafgreen"))}}

        def water(off):
            slots = []
            for i in range(5):
                mx, mn, _p, sp = struct.unpack_from("<BBHI", b, off + 4 + 8 * i)
                slots.append((sp, mn, mx))
            return slots
        o["surf"], o["rock"], o["old"], o["good"], o["super"] = (water(204), water(248), water(292), water(336),
                                                                 water(380))
        return o

    @staticmethod
    def dp_entries_for(o):
        out = []
        land = o["land"]
        for i, (lv, s) in enumerate(land):
            if s and lv:
                out.append(("walk", s, lv, lv, "land slot %d" % i))

        def repl(seq, slots, label):
            for k, s in enumerate(seq):
                if k < len(slots) and s:
                    lv = land[slots[k]][0]
                    out.append(("walk", s, lv, lv, "%s (replaces land slot %d)" % (label, slots[k])))
        repl(o["swarms"], SWARM_SLOTS, "swarm")
        repl(o["day"], TOD_SLOTS, "day")
        repl(o["night"], TOD_SLOTS, "night")
        repl(o["radar"], RADAR_SLOTS, "poke radar")
        for k, seq in o["dual"].items():
            repl(seq, DUAL_SLOTS, "dual-slot " + k)
        for key, label, kind in (("surf", "surf", "water"), ("rock", "rock smash", "walk"), ("old", "old rod", "water"),
                                 ("good", "good rod", "water"), ("super", "super rod", "water")):
            for i, (s, lo, hi) in enumerate(o[key]):
                if s and hi:
                    out.append((kind, s, lo, hi, "%s slot %d" % (label, i)))
        return out

    def _dp_tables(self):
        if self._dp is None:
            rom = self._dp_rom or N.NdsRom(C.rom_path("NZ_ROM_DIAMOND", N.DIAMOND_ROM))
            headers = N.dp_map_headers(rom)
            tables = {v: [self.parse_member(f) for f in N.Narc(rom.read("fielddata/encountdata/%s_enc_data.narc" % v)).files]
                      for v in ("d", "p")}
            self._dp = (headers, tables)
        return self._dp

    def dp(self, place, level, tracker, versions=("d", "p")):
        headers, tables = self._dp_tables()
        res = []
        for hid, h in enumerate(headers):
            if tracker.get(hid) != place or h[8] == 0xFFFF:
                continue
            for v in versions:
                for e in self.dp_entries_for(tables[v][h[8]]):
                    if e[2] <= level <= e[3]:
                        res.append((hid, v) + e)
        return res

    # ---------------------------------------------------------------- the one entry point statics uses
    def collisions(self, game, place, level):
        """[(header id, kind, species id, lo, hi, method)] of ordinary slots at exactly `level` in the area `place`."""
        if game == "pt":
            return [(a, b, c, d, e, f) for (a, b, c, d, e, f) in self.pt(place, level, C.location_names("pt"))]
        if game in ("hgss", "hg", "ss"):
            vers = ("hg", "ss") if game == "hgss" else (game,)
            return [(a, c, d, e, f, g) for (a, _v, c, d, e, f, g) in
                    self.hg(place, level, C.location_names("hgss"), vers)]
        if game == "dp":
            return [(a, c, d, e, f, g) for (a, _v, c, d, e, f, g) in self.dp(place, level, C.location_names("pt"))]
        raise ValueError(game)


def summarize(entries, names):
    """'walk: Gastly x2; water: Magikarp x1' style text of the colliding slots (species names, counts)."""
    kinds = {}
    for (_hid, kind, sp, _lo, _hi, _m) in entries:
        kinds.setdefault(kind, {}).setdefault(C.pretty_species(names[sp]), 0)
        kinds[kind][C.pretty_species(names[sp])] += 1
    return kinds


def parity_with_pret_json(enc):
    """Proves the DPPt binary member parser: it must reproduce pokeplatinum's JSON for every Platinum member."""
    pt = N.NdsRom(C.rom_path("NZ_ROM_PLATINUM", N.PLATINUM_ROM))
    narc = N.Narc(pt.read("fielddata/encountdata/pl_enc_data.narc"))
    order = [l.strip() for l in C.pret_text("pokeplatinum", "res/field/encounters/encounters.order").split("\n")
             if l.strip()]
    sid = enc.by_const
    bad = 0
    for idx, name in enumerate(order):
        j = C.pret_json("pokeplatinum", "res/field/encounters/%s.json" % name)
        o = enc.parse_member(narc.files[idx])
        ok = all((o["land"][i][0], o["land"][i][1]) == (e["level"], sid.get(e["species"], 0))
                 for i, e in enumerate(j["land_encounters"]))
        for k in ("swarms", "day", "night", "radar"):
            ok = ok and o[k] == [sid.get(s, 0) for s in j[k]]
        for key, jk in (("surf", "surf"), ("old", "old_rod"), ("good", "good_rod"), ("super", "super_rod")):
            for i, e in enumerate(j[jk + "_encounters"]):
                ok = ok and o[key][i] == (sid.get(e["species"], 0), e["level_min"], e["level_max"])
        if not ok:
            bad += 1
    return len(order), bad
