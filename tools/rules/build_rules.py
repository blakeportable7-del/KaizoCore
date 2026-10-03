"""Build app/src/main/assets/rulesets/<FAMILY>[-NatDex]/<mode>.md from the rulesets' own sources.

Sources, saved beside the settings generator's inputs in tools/upr-settings/:
  IronMon-Rules.md       the IronMON rules gist (valiant-code/adb18d248fa0fae7da6b639e2ee8f9c1),
                         Standard, Ultimate, Kaizo, Survival, saved with `gh gist view --raw`
  official-settings.md   the game-specific rules and settings gist (UTDZac/a147c497424dfbd537d8c4b0c22b5621),
                         per-game "Rules updates" sections and the Kaizo Doubles rules
  SuperKaizo-README.md   PyroMikeGit/SuperKaizoIronMON README, the Super Kaizo rules and per-game pivots
  community/             the community rulesets (fetched 2026-09-29): SurvivalRevival-README.md
                         (Reimittv/SurvivalRevivalIronMON), Ironmon Journey.md (PappyQC's gist),
                         Chaos Kaizo Ironmon Rules.md (UTDZac's gist), evo-kaizo-puyAqPyt.txt (the
                         pastebin), and Nat.-Dex-Ruleset-Changes.md (the Nat. Dex Extension wiki);
                         MaxDex-Ruleset.md holds every version of the Ruleset page of Trip's MaxDex
                         wiki (Tripc423/Maxdex), which was deleted 2026-07-03 (saved 2026-10-02)

A mode's file lists every ruleset it builds on, in order, because each source
says to read the earlier ones first ("this ruleset includes all of the
previous rules", "ALL PREVIOUS Standard, Ultimate, and Kaizo rules apply",
"Journey is based on the original Ironmon ruleset Kaizo difficulty"). Then
the game's own updates for that mode, and for a Nat. Dex build the Nat. Dex
ruleset changes. Nothing is paraphrased: table rows become bullets, HTML list
items become indented sub-bullets, links keep their text and URL, and the
emphasis markers the RULES box cannot draw are dropped. The only words added
are lines starting "In KaizoCore", which say where the app does a step for
the player. A British spelling is written the American way (american(): the
Nat. Dex page's "favourites" becomes "favorites"); only the spelling moves.

Which files are written follows the bundled presets: a mode gets a file for
each family, and each Nat. Dex family, that has a preset for it. MaxDex's own
preset ("FRLG MaxDex Kaizo.rnqs") gets <family>-MaxDex: the Nat. Dex text as
it reads for v1.0.0 to v1.1.3, the version MaxDex is built on (Blake,
2026-10-03: "Max dex is allowed a bst 600 pokemon"), then a MaxDex section
made from every version of Trip's page. KaizoCore's Nat. Dex games are 1.2.1
and keep the page's v1.2.0+ rules instead.

Run:  python tools/rules/build_rules.py   (from the repo root)
"""
import pathlib, re, datetime

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / "tools" / "upr-settings"
COMM = SRC / "community"
OUT = ROOT / "app" / "src" / "main" / "assets" / "rulesets"
PRESETS = ROOT / "app" / "src" / "main" / "assets" / "presets"

# The date is each source's last content change, not the host's activity stamp:
# the rules gist's page says "updated" whenever a comment lands.
SOURCES = {
    "rules": ("IronMON rules gist by valiant-code", "https://gist.github.com/valiant-code/adb18d248fa0fae7da6b639e2ee8f9c1", "rules last changed 2025-02-23"),
    "games": ("game-specific rules and settings gist by UTDZac", "https://gist.github.com/UTDZac/a147c497424dfbd537d8c4b0c22b5621", "last changed 2026-07-05"),
    "super": ("Super Kaizo IronMON by PyroMikeGit", "https://github.com/PyroMikeGit/SuperKaizoIronMON", "README last changed 2026-03-20"),
    "survivalrevival": ("Survival Revival IronMON by SaltyDolphin and Reimi", "https://github.com/Reimittv/SurvivalRevivalIronMON", "README last changed 2026-02-21"),
    "ironmonjourney": ("IronMON Journey rules by PappyQC", "https://gist.github.com/PappyQC/b9e28068ba4abbbdf191dd33625b737d", "last changed 2026-02-23"),
    "chaoskaizo": ("Chaos Kaizo IronMON rules by UTDZac", "https://gist.github.com/UTDZac/c8c3a84553840f8eabb063be80a33ee7", "last changed 2023-09-20"),
    "evokaizo": ("Evo Kaizo rules, a pastebin that names no author", "https://pastebin.com/puyAqPyt", "undated"),
    "natdex": ("Nat. Dex Ruleset Changes, the Nat. Dex Extension wiki by CyanSixFour", "https://github.com/CyanSMP64/NatDexExtension/wiki/Nat.-Dex-Ruleset-Changes", "last changed 2026-06-29"),
    # The page is gone from the wiki; its history is in the wiki's own repository (community/MaxDex-Ruleset.md).
    "maxdex": ("Ruleset page of the MaxDex wiki by Trip (Tripc423), every version", "https://github.com/Tripc423/Maxdex.wiki.git", "made 2026-06-19, last changed and deleted 2026-07-03"),
}

FAMILIES = {   # heading in official-settings.md -> (family tag, label)
    "RED / BLUE / YELLOW": ("RBY", "Red, Blue and Yellow"),
    "GOLD / SILVER / CRYSTAL": ("GSC", "Gold, Silver and Crystal"),
    "FIRE RED / LEAF GREEN": ("FRLG", "FireRed and LeafGreen"),
    "RUBY / SAPPHIRE / EMERALD": ("RSE", "Ruby, Sapphire and Emerald"),
    "HEART GOLD / SOUL SILVER": ("HGSS", "HeartGold and SoulSilver"),
    "DIAMOND / PEARL / PLATINUM": ("DPPt", "Diamond, Pearl and Platinum"),
    "BLACK / WHITE": ("BW", "Black and White"),
    "BLACK 2 / WHITE 2": ("B2W2", "Black 2 and White 2"),
}
TAG_LABEL = {tag: label for tag, label in FAMILIES.values()}
HEADING_MAX = 64   # a "### " heading longer than this reads as a sentence
SUPER_GAME = {"FRLG": "Fire Red / Leaf Green", "RSE": "Emerald", "HGSS": "Heart Gold / Soul Silver", "DPPt": "Platinum"}

MODE_LABEL = {"standard": "Standard", "ultimate": "Ultimate", "kaizo": "Kaizo", "superkaizo": "Super Kaizo",
              "survival": "Survival", "kaizodoubles": "Kaizo Doubles", "survivalrevival": "Survival Revival",
              "ironmonjourney": "IronMON Journey", "chaoskaizo": "Chaos Kaizo", "evokaizo": "Evo Kaizo"}
# What each mode builds on, in reading order.
KAIZO = ["standard", "ultimate", "kaizo"]
CHAIN = {
    "standard": ["standard"],
    "ultimate": ["standard", "ultimate"],
    "kaizo": KAIZO,
    "superkaizo": KAIZO + ["superkaizo"],
    "survival": KAIZO + ["survival"],
    "kaizodoubles": KAIZO + ["kaizodoubles"],
    "survivalrevival": KAIZO + ["survivalrevival"],
    "ironmonjourney": KAIZO + ["ironmonjourney"],
    "chaoskaizo": KAIZO + ["chaoskaizo"],
    "evokaizo": KAIZO + ["evokaizo"],
}
KAIZO_BASED = set(CHAIN) - {"standard", "ultimate"}
# Longest first, as RnqsInfo matches them.
MODE_KEYS = ["survivalrevival", "ironmonjourney", "kaizodoubles", "superkaizo", "chaoskaizo", "evokaizo",
             "standard", "survival", "ultimate", "kaizo"]


def applies(title, mode):
    """Which modes a per-game "Rules updates for X" heading of the settings gist reaches."""
    t = title.lower()
    if "general" in t or "standard & up" in t: return True
    if "standard & ultimate" in t: return mode in ("standard", "ultimate")
    if "ultimate & up" in t: return mode != "standard"
    if "super kaizo" in t: return mode == "superkaizo"
    if "kaizo & up" in t or "kaizo & survival" in t or "kaizo/survival" in t: return mode in KAIZO_BASED
    if "survival" in t: return mode == "survival"   # Survival's own; Survival Revival does not take them
    return False


# ------------------------------------------------------------------ cleaning

def plain(s):
    """Drop **bold** and *italic* markers the RULES box cannot draw. A marker must
    open on a letter, digit, bracket, parenthesis or quote, so footnote stars ("Sing
    **", "*: You may use") survive; _underscores_ stay, the Nat. Dex ban list's key."""
    s = re.sub(r"(?<![*\w])\*\*([A-Za-z0-9(\[\"'][^*\n]*?)\*\*(?!\*)", r"\1", s)
    s = re.sub(r"(?<![*\w])\*([A-Za-z0-9(\[\"'][^*\n]*?)\*(?!\*)", r"\1", s)
    return s

# British spellings and their American ones (Blake, 2026-10-03: every word a player reads is
# American English, the rulesets' own words included). AmericanSpellingTest reads what this writes.
AMERICAN = [
    (re.compile(r"\b([Ff])avour"), r"\1avor"),                 # favourite, favourites, favour
    (re.compile(r"\b([Cc])olour"), r"\1olor"),
    (re.compile(r"\b([Bb])ehaviour"), r"\1ehavior"),
    (re.compile(r"\b([Hh])onour"), r"\1onor"),
    (re.compile(r"\b([Nn])eighbour"), r"\1eighbor"),
    (re.compile(r"\b([Cc])entre(s?)\b"), r"\1enter\2"),        # Survival Revival's Pokemon Centre
    (re.compile(r"\b([Cc])entred\b"), r"\1entered"),
    (re.compile(r"\b([Ll])icence"), r"\1icense"),
    (re.compile(r"\b([Dd])efence"), r"\1efense"),
    (re.compile(r"\b([Rr])ecognis"), r"\1ecogniz"),
    (re.compile(r"\b([Gg])rey(s|ed|ing)?\b"), r"\1ray\2"),
]
URL = re.compile(r"https?://\S+")

def american(s):
    """[s] with each British spelling in AMERICAN written the American way. Links stay as they are."""
    out, at = [], 0
    for m in list(URL.finditer(s)) + [None]:
        part = s[at:m.start() if m else len(s)]
        for pat, us in AMERICAN: part = pat.sub(us, part)
        out.append(part)
        if m: out.append(m.group(0)); at = m.end()
    return "".join(out)

def clean(s):
    s = s.replace("—", ", ").replace("–", "-")
    s = re.sub(r"<h3>(.*?)</h3>", r"\1", s)
    s = re.sub(r"</?b>", "", s)
    s = re.sub(r"<\s*/?\s*br\s*/?\s*>", " ", s)                        # <br>, <br/>, </br>
    s = re.sub(r"!\[[^\]]*\]\([^)]*\)", "", s)                         # images: nothing to show
    s = re.sub(r"\[([^\]]+)\]\(<?(https?://[^)>]+)>?\)", r"\1 (\2)", s)
    s = re.sub(r"\[([^\]]+)\]\(#[^)]*\)", r"\1", s)                    # in-page anchors: the text
    s = re.sub(r"</li>(?!\s*(<li>|</ul>|$))", "</li>\n    ", s)         # text after a list goes under it
    s = re.sub(r"<li>(.*?)</li>", r"\n    - \1", s)
    s = re.sub(r"<ul>", "", s)
    s = re.sub(r"</ul>\s*", "\n    ", s)
    s = s.replace("`", "")
    s = re.sub(r"(?<=\S)[ \t]{2,}", " ", s)                             # inner runs only: indents stay
    s = plain(s)
    return "\n".join(l.rstrip() for l in s.split("\n") if l.strip()).strip()

def depths(lines):
    """A document's own indent steps (two spaces in one source, three or six in
    another) as nesting depths, 0 to 2: the RULES box indents 4 spaces a level."""
    inds = sorted({len(l) - len(l.lstrip()) for l in lines if l.strip() and not l.lstrip().startswith(("|", "#"))})
    return {ind: min(i, 2) for i, ind in enumerate(inds)}

def indented(line, levels=None):
    """A source line as a RULES line, at its depth in [levels] (any indent is one level without)."""
    raw = line.rstrip()
    ind = len(raw) - len(raw.lstrip())
    body = raw.strip()
    if body.startswith(">"): body = body.lstrip(">").strip()
    body = clean(body)
    if not body: return None
    depth = levels.get(ind, 1 if ind else 0) if levels is not None else (1 if ind else 0)
    return "    " * depth + body

def block(text):
    """Every line of [text] as RULES lines, nesting kept."""
    ls = text.splitlines()
    lv = depths(ls)
    return [l for l in (indented(x, lv) for x in ls) if l]

# ------------------------------------------------------------------ tables

def split_row(s):
    return [c.strip() for c in s.strip().strip("|").split("|")]

def is_sep(s):
    return s.startswith("|") and set(s.replace("|", "").strip()) <= set("-: ")

def render_table(header, rows):
    """Rule tables (Rule | Details | Notes) and two-column tables are one bullet per
    row; a table of independent columns (Chaos Kaizo's banned lists) is one bullet
    per column, so a Pokémon is never paired with the move beside it."""
    out = []
    h = [c.lower() for c in header]
    if h and h[0] in ("rule", "rules") or len(header) == 2:
        for cells in rows:
            rule = clean(cells[0]) if cells else ""
            details = clean(cells[1]) if len(cells) > 1 else ""
            notes = clean(cells[2]) if len(cells) > 2 else ""
            if not rule and "<h3>" in (cells[1] if len(cells) > 1 else ""):
                out += ["", f"### {details}"]; continue
            if not rule and not details: continue
            # A cell that is itself a list starts on the next line, under its rule.
            if details.startswith("- "): out += [f"- {rule}:" if rule else "-", "    " + details]
            else: out.append(f"- {rule}: {details}" if rule else f"- {details}")
            if notes.startswith("- "): out += ["    - Note:", "    " + notes]
            elif notes: out.append(f"    - Note: {notes}")
    else:
        for j, name in enumerate(header):
            items = [clean(r[j]) for r in rows if j < len(r) and clean(r[j])]
            if items: out.append(f"- {clean(name)}: " + ", ".join(items))
    return out

def render_md(text, skip=lambda heading: False):
    """Markdown with tables, lists and headings, as RULES lines. Headings become
    "### " lines; one straight after another joins it while the two fit on a
    line ("Rule #1: Choose Wisely"), and a heading too long for one is a
    sentence and stays a plain line. [skip] drops a heading's whole section,
    its deeper headings included."""
    out, lines, i = [], text.splitlines(), 0
    levels = depths(lines)
    skip_level, pending = None, []
    def emit(t):
        out.extend(["", f"### {t}"] if len(t) <= HEADING_MAX else ["", t])
    def flush():
        cur = ""
        for t in pending:
            if cur and len(cur) + 2 + len(t) <= HEADING_MAX: cur += ": " + t; continue
            if cur: emit(cur)
            cur = t
        if cur: emit(cur)
        pending.clear()
    while i < len(lines):
        s = lines[i].strip()
        m = re.match(r"^(#{1,6})\s+(.*)$", s)
        if m:
            level, title = len(m.group(1)), clean(m.group(2))
            if skip_level is not None and level > skip_level: i += 1; continue
            skip_level = level if skip(title) else None
            if skip_level is None and title: pending.append(title)
            i += 1; continue
        if skip_level is not None or not s or s.startswith("---") or s.startswith("```"):
            i += 1; continue
        if s.startswith("|") and i + 1 < len(lines) and is_sep(lines[i + 1].strip()):
            header = split_row(s); i += 2; rows = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                rows.append(split_row(lines[i])); i += 1
            flush(); out += render_table(header, rows); continue
        flush()
        l = indented(lines[i], levels)
        if l: out.append(l)
        i += 1
    flush()
    return out

# ------------------------------------------------------------------ the rules gist

def gist_tables(text):
    """'## Heading' -> its RULES lines: the prose and the rule table in order."""
    out, cur, buf = {}, None, []
    def close():
        if cur is not None: out[cur] = render_md("\n".join(buf))
    for line in text.splitlines():
        if line.startswith("## "):
            close(); cur = line[3:].strip(); buf = []; continue
        buf.append(line)
    close()
    return out

def section(text, start_re, end_re):
    m = re.search(start_re, text, re.M); assert m, start_re
    rest = text[m.end():]
    e = re.search(end_re, rest, re.M)
    return rest[:e.start()] if e else rest

# The 60% levels are done by the app; the rules text says where.
LEVEL_BOOST = re.compile(r"level boost")
NOTE_60 = ("In KaizoCore, the \"Official 60% levels\" switch on RUN runs this first pass for you. It is on by "
           "default for the official settings of Kaizo and every mode built on it, and yours to switch.")
NOTE_60_NATDEX = "In KaizoCore, the Nat. Dex settings files for Kaizo and harder already raise levels by 60%, so there is no first pass."
# Super Kaizo's README asks for a smart AI patch; the Nat. Dex randomizer has it built in (rules check, 2026-10-01).
NOTE_SMART_AI_NATDEX = ("In KaizoCore, the Nat. Dex Super Kaizo settings file turns on the Nat. Dex randomizer's Smart AI Mode, "
                        "so every trainer has smart AI and no patch is needed.")
# Where the settings page's notes on the growth patch meet the app.
PATCH_NOTES = {
    "RED / BLUE / YELLOW": "In KaizoCore, PREPARE's pseudo-fluctuating growth patch is the first way, and a run on the patched game takes PART 1 only. "
                           "On a game without it, runs take PART 2 as well, the second way. PART 2 is a switch on RUN, yours either way.",
    "GOLD / SILVER / CRYSTAL": "In KaizoCore, PREPARE offers the pseudo-fluctuating growth patch for Gold, Silver and Crystal.",
}

def game_updates(games_md, family_heading, natdex=False):
    """The settings gist's "Rules updates" sections for one family, and the prose
    above its settings strings (the Gen 1 and Crystal patch notes), strings dropped."""
    body = section(games_md, r"^## " + re.escape(family_heading) + r"\s*$", r"^## ")
    out = []
    for sub in re.split(r"^#### ", body, flags=re.M)[1:]:
        title, _, content = sub.partition("\n")
        title = title.strip()
        if "settings strings" in title.lower():
            ls = block(content.split("```", 1)[0])
            if ls and family_heading in PATCH_NOTES: ls.append(PATCH_NOTES[family_heading])
            if ls and "4.4.0" not in title: out.append(("Settings notes", ls))
            continue
        ls = block("\n".join(x for x in content.splitlines() if x.strip().strip("> ").strip("`")))
        if any(LEVEL_BOOST.search(l) for l in ls): ls.append(NOTE_60_NATDEX if natdex else NOTE_60)
        out.append((title, ls))
    return out

def preset_matrix():
    """(family, build) -> the modes it has a bundled preset for; the build is "", "NatDex" or "MaxDex"."""
    fam = {}
    for f in PRESETS.glob("*.rnqs"):
        n = f.stem
        if "PART 2" in n or "PRE-PASS" in n: continue   # run by the app around a preset, never a mode
        tag = n.split()[0]
        natdex = "natdex" in n.lower()
        maxdex = "maxdex" in n.lower()   # MaxDex's own file: its own folder, never the plain game's
        key = re.sub(r"[^a-z0-9]", "", re.sub(r"natdex v[\d.]+", "", n.lower()).replace(tag.lower(), "", 1))
        for k in MODE_KEYS:
            if k in key: key = k; break
        fam.setdefault((tag, "MaxDex" if maxdex else "NatDex" if natdex else ""), set()).add(key)
    return fam

# ------------------------------------------------------------------ the community rulesets

def revival_rules():
    t = (COMM / "SurvivalRevival-README.md").read_text(encoding="utf-8")
    general = render_md(section(t, r"^## General Rules for All Games\s*$", r"^## "))
    m = re.search(r"^## (Changes as of .*)$", t, re.M)
    changes = render_md("### " + m.group(1) + "\n" + section(t, r"^## Changes as of .*$", r"^## "))
    frlg = render_md(section(t, r"^### Fire Red / Leaf Green\s*$", r"^## "))
    return general, changes, {"FRLG": frlg}

def journey_rules():
    t = (COMM / "Ironmon Journey.md").read_text(encoding="utf-8")
    return render_md(t, skip=lambda h: h.startswith("Randomizer Strings")), {}

def chaos_rules():
    t = (COMM / "Chaos Kaizo Ironmon Rules.md").read_text(encoding="utf-8")
    return render_md(t, skip=lambda h: h.startswith("Randomizer Settings String") or h == "Chaos Kaizo Ironmon"), {}

def evo_rules():
    t = (COMM / "evo-kaizo-puyAqPyt.txt").read_text(encoding="utf-8")
    t = re.sub(r"(?m)^Setting String if you want to try.*$", "", t)
    return render_md(t), {}

# What the page marks as one version's own: a bullet with its sub-bullets, or a heading with its section.
V113_ONLY = "v1.0.0 to v1.1.3 only"
V120_ONLY = "v1.2.0+ only"

def natdex_changes(drop=V113_ONLY):
    """The wiki page without the rules it marks [drop]. KaizoCore's Nat. Dex is 1.2.1, so its books leave out what
    applies to v1.0.0 to v1.1.3 only; MaxDex's book leaves out the v1.2.0+ rules instead (see NOTE_MAXDEX)."""
    t = (COMM / "Nat.-Dex-Ruleset-Changes.md").read_text(encoding="utf-8")
    kept, drop_below = [], None
    for line in t.splitlines():
        ind = len(line) - len(line.lstrip())
        if drop_below is not None:
            if line.strip() and ind <= drop_below and not line.lstrip().startswith("#"):
                drop_below = None
            elif line.lstrip().startswith("#"):
                drop_below = None
            else:
                continue
        if drop in line:
            drop_below = ind if not line.lstrip().startswith("#") else -1
            continue
        kept.append(line)
    return render_md("\n".join(kept))

NATDEX_NOTE = "(Rules the page marks \"v1.0.0 to v1.1.3 only\" are left out: KaizoCore's Nat. Dex is 1.2.1.)"
# The Nat. Dex page's note, as it reads on MaxDex, which is built on Nat. Dex 1.1.3 (Blake, 2026-10-03: "Max dex is
# allowed a bst 600 pokemon").
NATDEX_NOTE_MAXDEX = ("(Rules the page marks \"v1.2.0+ only\" are left out: MaxDex is built on Nat. Dex 1.1.3, so the ones marked "
                      "\"v1.0.0 to v1.1.3 only\" are its own. See MaxDex, below.)")
# Which rules the app holds a MaxDex run to: BstRule and FavoriteBall take MaxDex's 1.1.3 lines, Favorites its nine.
NOTE_MAXDEX = ("In KaizoCore, MaxDex is held to the Nat. Dex rules above for v1.0.0 to v1.1.3, the version it is built on: a starter "
               "may have up to 600 BST, any other Pokémon must be under 600, a Pokémon may evolve to 600 or more unless it becomes "
               "a legendary of 601 or more, and each of your up to 9 favorites may have up to 600 BST. Trip's page names no version.")
MAXDEX_LISTS = ["Extra Abilities Banned", "Extra Banned Moves"]

def maxdex_versions():
    """Every saved version of the MaxDex wiki's Ruleset page, oldest first: (date, text), text None where it was deleted."""
    t = (COMM / "MaxDex-Ruleset.md").read_text(encoding="utf-8")
    out = []
    for part in re.split(r"(?m)^## ", t)[1:]:
        head, _, body = part.partition("\n")
        body = "\n".join(l for l in body.splitlines() if not l.startswith("<!--")).strip()
        out.append((head.split()[1], None if body == "(deleted)" else body))
    return out

def listed(text, label):
    """The names on a version's "Label: a, b, c" line, in order."""
    m = re.search(r"(?m)^" + re.escape(label) + r":(.*)$", text)
    return [x.strip() for x in m.group(1).split(",") if x.strip()] if m else []

def maxdex_rules():
    """MaxDex's section: the page's last version in full, then what each version changed, worked out from the saved
    versions so no count is kept by hand, then where the app stands."""
    vs = maxdex_versions()
    pages = [text for _, text in vs if text is not None]
    out = [f"Trip's MaxDex wiki had a Ruleset page from {vs[0][0]} to {vs[-1][0]}, when it was deleted. Its last version, in full:"]
    out += render_md(pages[-1])
    out.append("What each version of the page said:")
    prev = None
    for date, text in vs:
        if text is None:
            out.append(f"- {date}: the page was deleted."); continue
        now = {label: listed(text, label) for label in MAXDEX_LISTS}
        if prev is None:
            first = re.sub(r"\s*\(https?://[^)]*\)", "", clean(text.splitlines()[0]))
            extra = [f"{label} ({len(v)})" for label, v in now.items() if v]
            out.append(f"- {date}: {first}" + (", plus " + " and ".join(extra) if extra else "") + ".")
        else:
            said = []
            for label in MAXDEX_LISTS:
                gone = [x for x in prev[label] if x not in now[label]]
                if gone: said.append(f"{label} came off" if not now[label] else f"{', '.join(gone)} came off {label}")
            out.append(f"- {date}: " + ("; ".join(said) if said else "no change to its lists") + ".")
        prev = now
    return out + [NOTE_MAXDEX]

# ------------------------------------------------------------------ writing

def main():
    rules = gist_tables((SRC / "IronMon-Rules.md").read_text(encoding="utf-8"))
    games_md = (SRC / "official-settings.md").read_text(encoding="utf-8")
    sk = (SRC / "SuperKaizo-README.md").read_text(encoding="utf-8")
    ult = rules["Ultimate IronMon Ruleset"]
    frlg_at = next((i for i, l in enumerate(ult) if l.startswith("FIRE RED/LEAF GREEN SPECIFIC")), len(ult))
    base = {
        "standard": ("Standard IronMON", rules["Standard IronMon Ruleset"]),
        "ultimate": ("Ultimate IronMON", ult[:frlg_at]),
        "kaizo": ("Kaizo IronMON", rules["Kaizo IronMon Ruleset"]),
        "survival": ("Survival IronMON", rules["Survival IronMon Ruleset"]),
    }
    frlg_ultimate = ult[frlg_at:]
    doubles = block(section(games_md, r"^## KAIZO DOUBLES RULES\s*$", r"^## "))
    sk_general = block(section(sk, r"^## General Rules for All Games\s*$", r"^## "))
    sk_disclaimer = block(section(sk, r"^## VERY LARGE DISCLAIMER from iateyourpie\s*$", r"^## "))
    def sk_game(family):
        h = SUPER_GAME.get(family)
        if not h: return []
        return block(section(sk, r"^### " + re.escape(h) + r"\s*$", r"^##"))
    rv_general, rv_changes, rv_games = revival_rules()
    community = {
        "survivalrevival": ("Survival Revival IronMON", rv_changes + [""] + rv_general, rv_games),
        "ironmonjourney": ("IronMON Journey",) + journey_rules(),
        "chaoskaizo": ("Chaos Kaizo IronMON",) + chaos_rules(),
        "evokaizo": ("Evo Kaizo IronMON",) + evo_rules(),
    }
    natdex = [NATDEX_NOTE] + natdex_changes()
    natdex_maxdex = [NATDEX_NOTE_MAXDEX] + natdex_changes(drop=V120_ONLY)
    maxdex = maxdex_rules()

    matrix = preset_matrix()
    today = datetime.date.today().isoformat()
    written = []
    for heading, (family, fam_label) in FAMILIES.items():
        # The game, its Nat. Dex build, and MaxDex (FireRed's, from Trip's own preset): Nat. Dex plus its own section.
        for build in ("", "NatDex", "MaxDex"):
            natdex_build = build != ""
            updates = game_updates(games_md, heading, natdex_build)
            modes = sorted(matrix.get((family, build), set()), key=lambda m: list(CHAIN).index(m) if m in CHAIN else 99)
            label = fam_label + {"": "", "NatDex": ", Nat. Dex", "MaxDex": ", MaxDex"}[build]
            for mode in modes:
                if mode not in CHAIN: continue
                out = [f"# {label}: {MODE_LABEL[mode]}", "",
                       "Every ruleset builds on the ones before it, so they are all here in order, then this game's own updates.", ""]
                used = ["rules", "games"]
                for step in CHAIN[mode]:
                    if step in base:
                        title, lines = base[step]
                        out += [f"## {title}", ""] + lines + [""]
                        if step == "ultimate" and family == "FRLG":
                            out += ["### FireRed and LeafGreen, Ultimate", ""] + frlg_ultimate + [""]
                    elif step == "superkaizo":
                        out += ["## Super Kaizo IronMON", "", *sk_disclaimer, "", *sk_general, ""]
                        g = sk_game(family)
                        if g: out += [f"### Super Kaizo, {fam_label}", ""] + g + [""]
                        if natdex_build: out += [NOTE_SMART_AI_NATDEX, ""]
                        used.append("super")
                    elif step == "kaizodoubles":
                        out += ["## Kaizo Doubles", ""] + doubles + [""]
                    elif step in community:
                        title, lines, per_game = community[step]
                        out += [f"## {title}", ""] + lines + [""]
                        g = per_game.get(family)
                        if g: out += [f"### {MODE_LABEL[step]}, {fam_label}", ""] + g + [""]
                        elif per_game: out += [f"Its game-specific rules are written for {' and '.join(TAG_LABEL[t] for t in per_game)} only.", ""]
                        # Evo Kaizo's source has no game sections, but its gyms and checkpoints are FireRed's (rules check, 2026-10-01).
                        elif step == "evokaizo" and family != "FRLG": out += ["Its rules are written for FireRed and LeafGreen: the gyms and places it names are in those games.", ""]
                        # The Nat. Dex page has an Evo Kaizo part of its own: no evo loops, so no checkpoint pivots (Blake asked, 2026-10-01).
                        if step == "evokaizo" and natdex_build: out += ["On a Nat. Dex build, the Evo Kaizo part of the Nat. Dex ruleset changes below replaces rules 4 and 11: pivots are banned, with no checkpoints.", ""]
                        used.append(step)
                own = [(t, ls) for t, ls in updates if t == "Settings notes" or applies(t, mode)]
                if own:
                    out += [f"## {fam_label}: game-specific rules", ""]
                    for t, ls in own:
                        out += [f"### {t}", ""] + ls + [""]
                if natdex_build:
                    out += ["## Nat. Dex ruleset changes", ""] + (natdex_maxdex if build == "MaxDex" else natdex) + [""]
                    used.append("natdex")
                if build == "MaxDex":
                    out += ["## MaxDex", ""] + maxdex + [""]
                    used.append("maxdex")
                out += ["## Sources", ""]
                for k in used:
                    n, u, d = SOURCES[k]; out.append(f"- {n}: {u} ({d})")
                out += ["", f"Generated {today} by tools/rules/build_rules.py. Rulesets get revised; if this reads behind a source, regenerate."]
                text = american(re.sub(r"\n{3,}", "\n\n", "\n".join(out)).rstrip() + "\n")
                assert "—" not in text, (family, mode)
                d = OUT / (family + ("-" + build if build else ""))
                d.mkdir(parents=True, exist_ok=True)
                (d / f"{mode}.md").write_text(text, encoding="utf-8")
                written.append(f"{d.name}/{mode}")
    print(len(written), "files:", ", ".join(written))

if __name__ == "__main__":
    main()
