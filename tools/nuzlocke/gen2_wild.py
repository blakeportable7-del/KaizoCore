"""Ordinary wild encounter levels per map for Generation 2 (2026-09-29), read from the pret disassemblies.

Used by gen2_statics.py to make sure a static row (place, level) cannot also describe an ordinary wild encounter of
that place: the readers of statics-gen2.tsv match a wild battle on its place and level alone.

    python tools/nuzlocke/gen2_wild.py gs gold ROUTE_36        # the levels seen on one map, by source
    python tools/nuzlocke/gen2_wild.py c crystal UNION_CAVE_B2F

Variants: pokegold builds Gold (IF DEF(_GOLD)) and Silver (ELIF DEF(_SILVER)) from one tree; pokecrystal has one.
Sources read: grass (Johto and Kanto tables), surf (water tables), swarm grass and water, fishing (the map header's
fishing group, its rods and the time-of-day rows), Headbutt trees and Rock Smash. The Bug-Catching Contest is not
here: its battles have their own battle type.
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen2_common as C  # noqa: E402
import gen2_areas as A  # noqa: E402


def preprocess(lines, variant):
    """Resolve IF DEF(_GOLD) / ELIF DEF(_SILVER) / ELSE / ENDC for a variant in ('gold', 'silver', 'crystal')."""
    defined = {"_GOLD"} if variant == "gold" else {"_SILVER"} if variant == "silver" else set()
    out = []
    stack = []      # per open IF: (parent active, this branch taken already, this branch active)
    active = True
    for raw in lines:
        s = C.strip_comment(raw)
        m = re.match(r"^(IF|ELIF)\s+(!?)DEF\((\w+)\)$", s, re.I)
        if m:
            kind, neg, sym = m.group(1).upper(), m.group(2) == "!", m.group(3)
            cond = (sym in defined) != neg
            if kind == "IF":
                stack.append([active, cond, active and cond])
            else:
                top = stack[-1]
                top[2] = top[0] and not top[1] and cond
                top[1] = top[1] or cond
            active = stack[-1][2]
            continue
        if re.match(r"^ELSE$", s, re.I):
            top = stack[-1]
            top[2] = top[0] and not top[1]
            top[1] = True
            active = top[2]
            continue
        if re.match(r"^ENDC$", s, re.I):
            stack.pop()
            active = stack[-1][2] if stack else True
            continue
        if active:
            out.append(s)
    if stack:
        C.die("unbalanced IF/ENDC in wild data")
    return out


def read_pp(root, name, variant):
    return preprocess(C.lines_of(root + "/data/wild/" + name), variant)


def table_levels(root, name, variant, header_re):
    """{MAP: [(level, SPECIES)]} for a table file whose maps start with a line matching header_re (group 1 = the map
    constant). Every `db LEVEL, SPECIES` line until the next header counts; `db N percent` rate lines are not entries."""
    out = {}
    cur = None
    for s in read_pp(root, name, variant):
        m = re.match(header_re, s)
        if m:
            cur = m.group(1)
            out.setdefault(cur, [])
            continue
        if s.startswith("end_") or s.startswith("db -1"):
            cur = None if s.startswith("end_") else cur
            continue
        m = re.match(r"^db\s+(\d+)\s*,\s*([A-Z_0-9]+)$", s)
        if m and cur is not None:
            out[cur].append((int(m.group(1)), m.group(2)))
    return out


def fishing_levels(root, variant):
    """{fishing group constant index (1-based): set of (level, SPECIES)} over every rod and both times of day."""
    lines = read_pp(root, "fish.asm", variant)
    groups = []                 # [(old label, good label, super label)]
    labels = {}
    cur = None
    timegroups = []
    in_time = False
    for s in lines:
        m = re.match(r"^fishgroup\s+.+?,\s*(\.\w+),\s*(\.\w+),\s*(\.\w+)$", s)
        if m:
            groups.append(m.groups())
            continue
        m = re.match(r"^(\.\w+):$", s)
        if m:
            cur = m.group(1)
            labels.setdefault(cur, [])
            continue
        if s.startswith("TimeFishGroups:"):
            in_time = True
            cur = None
            continue
        if in_time:
            m = re.match(r"^db\s+(\w+)\s*,\s*(\d+)\s*,\s*(\w+)\s*,\s*(\d+)$", s)
            if m:
                timegroups.append({(int(m.group(2)), m.group(1)), (int(m.group(4)), m.group(3))})
            continue
        if cur is not None:
            m = re.match(r"^db\s+[^,]+,\s*([A-Z_0-9]+)\s*,\s*(\d+)$", s)
            if m:
                labels[cur].append(("level", (int(m.group(2)), m.group(1))))
                continue
            m = re.match(r"^db\s+[^,]+,\s*time_group\s+(\d+)$", s)
            if m:
                labels[cur].append(("time", int(m.group(1))))
    out = {}
    for i, trio in enumerate(groups, 1):
        levels = set()
        for lab in trio:
            for kind, v in labels[lab]:
                if kind == "level":
                    levels.add(v)
                else:
                    levels |= timegroups[v]
        out[i] = levels
    return out


def treemon_levels(root, variant):
    """({map: set of (level, SPECIES)} for Headbutt trees, {map: set} for Rock Smash) from treemons.asm and treemon_maps.asm."""
    sets = {}
    cur = None
    for s in read_pp(root, "treemons.asm", variant):
        m = re.match(r"^(TreeMonSet_\w+):$", s)
        if m:
            cur = m.group(1)
            sets[cur] = set()
            continue
        m = re.match(r"^db\s+\d+\s*,\s*([A-Z_0-9]+)\s*,\s*(\d+)$", s)
        if m and cur:
            sets[cur].add((int(m.group(2)), m.group(1)))
    order = []
    for s in read_pp(root, "treemons.asm", variant):
        m = re.match(r"^dw\s+(TreeMonSet_\w+)$", s)
        if m:
            order.append(m.group(1))
    head, rock = {}, {}
    part = None
    for raw in C.lines_of(root + "/data/wild/treemon_maps.asm"):
        s = C.strip_comment(raw)
        if s == "TreeMonMaps:":
            part = head
        elif s == "RockMonMaps:":
            part = rock
        m = re.match(r"^treemon_map\s+(\w+),\s*TREEMON_SET_(\w+)$", s)
        if m and part is not None:
            name = "TreeMonSet_" + m.group(2).title().replace("_", "")
            part[m.group(1)] = sets.get(name, set())
    return head, rock


# ---------------------------------------------------------------------------------- where there is water
def collision_water_values(root):
    """The set of COLL_* values whose entry in CollisionPermissionTable is a water tile (WATER_TILE, with or without
    TALK): the tiles surfing and fishing work on."""
    expr = {"LAND_TILE": 0, "WATER_TILE": 1, "WALL_TILE": 0x0F, "TALK": 0x10}
    perms = []
    started = False
    for raw in C.lines_of(root + "/data/collision/collision_permissions.asm"):
        s = C.strip_comment(raw)
        if s.startswith("CollisionPermissionTable"):
            started = True
            continue
        m = re.match(r"^db\s+(.+)$", s)
        if started and m:
            perms.append(sum(expr[t.strip()] for t in m.group(1).split("|")))
    if len(perms) != 256:
        C.die("CollisionPermissionTable has %d entries" % len(perms))
    return {i for i, p in enumerate(perms) if (p & 0x0F) == 1}


def collision_names(root):
    """{NAME: value} for `DEF COLL_NAME EQU $xx` in constants/collision_constants.asm."""
    out = {}
    for raw in C.lines_of(root + "/constants/collision_constants.asm"):
        m = re.match(r"^\s*DEF\s+COLL_(\w+)\s+EQU\s+\$([0-9A-Fa-f]+)", raw)
        if m:
            out[m.group(1)] = int(m.group(2), 16)
    return out


def tileset_water_blocks(root):
    """{TILESET_CONST: set of block numbers that hold a water tile}: block i is line i of the tileset's
    collision file (four collision values per block)."""
    water = collision_water_values(root)
    names = collision_names(root)
    consts = []
    for raw in C.lines_of(root + "/constants/tileset_constants.asm"):
        m = re.match(r"^\s*const\s+(TILESET_\w+)", C.strip_comment(raw))
        if m:
            consts.append(m.group(1))
    order = []
    for raw in C.lines_of(root + "/data/tilesets.asm"):
        m = re.match(r"^\s*tileset\s+(\w+)$", C.strip_comment(raw))
        if m and m.group(1) != "Tileset0":
            order.append(m.group(1))
    coll_file = {}
    pending = []
    for raw in C.lines_of(root + "/gfx/tilesets.asm"):
        s = C.strip_comment(raw)
        m = re.match(r"^(\w+)Coll::$", s)
        if m:
            pending.append(m.group(1))
            continue
        m = re.match(r'^INCLUDE\s+"(data/tilesets/[^"]+_collision\.asm)"$', s)
        if m and pending:
            for lab in pending:
                coll_file[lab] = m.group(1)
            pending = []
    out = {}
    for const, label in zip(consts, order):
        blocks = set()
        for i, raw in enumerate(l for l in C.lines_of(root + "/" + coll_file[label]) if l.strip().startswith("tilecoll")):
            args = [a.strip() for a in C.strip_comment(raw).replace("tilecoll", "", 1).split(",")]
            if any(names[a] in water for a in args):
                blocks.add(i)
        out[const] = blocks
    return out


def water_maps(game):
    """{MAP_CONST} of the maps whose block data uses at least one water block of their tileset."""
    root = C.REPO[game]
    tsets = tileset_water_blocks(root)
    label_file = {}
    pending = []                # several labels can stand in front of one INCBIN (maps that share a block file)
    for raw in C.lines_of(root + "/data/maps/blocks.asm"):
        m = re.match(r"^(\w+)_Blocks:$", raw.strip())
        if m:
            pending.append(m.group(1))
        m = re.match(r'^\s*INCBIN\s+"(maps/[^"]+\.blk)"', raw)
        if m:
            for lab in pending:
                label_file[lab] = m.group(1)
            pending = []
    heads = {}
    for raw in C.lines_of(root + "/data/maps/maps.asm"):
        m = re.match(r"^\s*map\s+(\w+),\s*(TILESET_\w+),", raw)
        if m:
            heads[m.group(1)] = m.group(2)
    out = set()
    for mp in A.game_maps(game):
        camel = mp["camel"]
        path = root + "/" + label_file[camel]
        with open(path, "rb") as f:
            data = f.read()
        blocks = tsets[heads[camel]]
        if any(b in blocks for b in data):
            out.add(mp["const"])
    return out


def map_levels(game, variant):
    """{MAP_CONST: {source: set((level, SPECIES))}} for every map of a game in one variant."""
    root = C.REPO[game]
    out = {}

    def add(m, src, levels):
        if levels:
            out.setdefault(m, {}).setdefault(src, set()).update(levels)

    for name, src in (("johto_grass.asm", "grass"), ("kanto_grass.asm", "grass")):
        for m, lv in table_levels(root, name, variant, r"^def_grass_wildmons\s+(\w+)$").items():
            add(m, src, lv)
    for name in ("johto_water.asm", "kanto_water.asm"):
        for m, lv in table_levels(root, name, variant, r"^def_water_wildmons\s+(\w+)$").items():
            add(m, "surf", lv)
    for m, lv in table_levels(root, "swarm_grass.asm", variant, r"^map_id\s+(\w+)$").items():
        add(m, "swarm", lv)
    for m, lv in table_levels(root, "swarm_water.asm", variant, r"^map_id\s+(\w+)$").items():
        add(m, "swarm", lv)
    fish = fishing_levels(root, variant)
    wet = water_maps(game)
    for mp in A.game_maps(game):
        fg = mp["fishgroup"]
        if fg != "FISHGROUP_NONE" and mp["const"] in wet:
            idx = FISHGROUPS[fg]
            add(mp["const"], "fishing", fish[idx])
    head, rock = treemon_levels(root, variant)
    for m, lv in head.items():
        add(m, "headbutt", lv)
    for m, lv in rock.items():
        add(m, "rocksmash", lv)
    return out


FISHGROUPS = {"FISHGROUP_SHORE": 1, "FISHGROUP_OCEAN": 2, "FISHGROUP_LAKE": 3, "FISHGROUP_POND": 4, "FISHGROUP_DRATINI": 5,
              "FISHGROUP_QWILFISH_SWARM": 6, "FISHGROUP_REMORAID_SWARM": 7, "FISHGROUP_GYARADOS": 8, "FISHGROUP_DRATINI_2": 9,
              "FISHGROUP_WHIRL_ISLANDS": 10, "FISHGROUP_QWILFISH": 11, "FISHGROUP_REMORAID": 12,
              "FISHGROUP_QWILFISH_NO_SWARM": 13}


def place_levels(game, variant):
    """{place name: {(source, map const): set((level, SPECIES))}} for the places of areas-gen2's landmark names."""
    maps = {m["const"]: m for m in A.game_maps(game)}
    lv = map_levels(game, variant)
    out = {}
    for const, srcs in lv.items():
        if const not in maps:
            continue
        place = maps[const]["place"]
        for src, levels in srcs.items():
            out.setdefault(place, {})[(src, const)] = levels
    return out


if __name__ == "__main__":
    game, variant, const = sys.argv[1:4]
    lv = map_levels(game, variant).get(const, {})
    for src, pairs in sorted(lv.items()):
        print(src, sorted(pairs))
