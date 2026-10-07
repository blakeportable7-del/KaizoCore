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
the player. Heart & Soul's book adds more of them, because no ruleset is written
for it: a list at the top of what its KaizoCore build does in every IronMON mode
(HNS_GAME), and under a rule, a line where Heart & Soul differs from the game
or settings that rule was written for (HNS_AFTER, HNS_EVO_KAIZO), each checked
against Heart & Soul's own source. A British spelling is written the American way (american(): the
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
# Heart & Soul's engine does it itself: HnsGame.writeTrainers sets the trainer's smart flags (aiFlags | 7) when the file asks.
NOTE_SMART_AI_HNS = ("In KaizoCore, Heart & Soul's Super Kaizo settings make every trainer pick its moves the smart way, "
                     "and no patch is needed.")
# Heart & Soul's book (main()): its folder and title, and the line that says whose rules it is held to.
HNS_TAG = "HnS"
HNS_LABEL = "Heart & Soul"
HNS_INTRO = ["In KaizoCore, Heart & Soul is held to the rules of the game it retells and the build it runs on. It is HeartGold "
             "and SoulSilver's story, Johto and then Kanto with sixteen badges, so HeartGold and SoulSilver's game rules below "
             "apply to it. It runs the Emerald Nat. Dex settings files, so a run with the Nat. Dex pool (Pokémon through "
             "Gen 9) is held to the Nat. Dex ruleset changes below as well. A run with the Vanilla pool (Gen 1 to 3 Pokémon) "
             "is held to the same rules without those changes, as Emerald is: the Gen 3 BST limits and three favorites.", "",
             "In KaizoCore, the item that starts in your PC is in the trash can in Elm's lab instead, and it is random "
             "in every mode that randomizes field items, as the other Kaizo games' PC item is.", ""]
# What KaizoCore's Heart & Soul build (tools/hns/patches, the comfort build of rc36.1) and HnsEngine do in every IronMON
# mode, said once, at the top of the book, where a player looks before the rules. Every IronMON run of Heart & Soul is a
# "Kaizo run" to the build (HnsEngine.writePreset, Preset.KAIZO; KAIZOCORE_KAIZO_RUN in patch 0034): only a Nuzlocke run
# differs, and Nuzlocke has no book here. Each line names the patch or the code that does it.
HNS_GAME = [
    "### What Heart & Soul does in KaizoCore", "",
    "In KaizoCore, Heart & Soul plays every IronMON mode this way:",
    # 0030, 0034: CanPlayerForfeitNormalTrainerBattle is FALSE in a Kaizo run. 0006: QUICK RUN on B, never from a shiny.
    "- You cannot run from a trainer battle or forfeit it: RUN is refused, as in Emerald. From a wild battle you can run "
    "as usual, or hold B as it starts, though never from a shiny.",
    # rc38 (0041-0044, Blake 2026-10-06): a Kaizo run plays trainer doubles as singles; HNS_PRESET_KAIZO_DOUBLES keeps them.
    "- Trainers who would battle you two at once (twins, couples, the gym pairs) battle you in a single battle, one of "
    "their Pokémon at a time, whatever your party: no second Pokémon is needed. In Kaizo Doubles every trainer "
    "battle is a double, as that mode asks.",
    # 0029: expOnCatch off in a Kaizo run.
    "- Catching a Pokémon gives no EXP.",
    # 0028: KaizoCore_CycleSlot, one cursor per wild header and table kind.
    "- Wild Pokémon come in turn: each list (grass, water, Rock Smash and each fishing rod) gives every species it has "
    "once, in order, before any comes again. A route with five species shows all five in five encounters.",
    # HnsRandomizer: hmMoves are never rolled into a learnset, evolution and level 1 moves included; trainers use learnsets.
    "- No Pokémon learns an HM move by level or knows one when you meet it, yours or a trainer's, in either pool.",
    # HnsRandomizer.randomizeTMs (rc38.1, Blake: "apply the vanilla rule to natl dex"): hmMoves never go on a TM.
    "- No TM teaches an HM move, in either pool, as in vanilla Emerald.",
    # 0033: KaizoCore_IsFirstRivalBattle, the three TRAINER_RIVAL_*_1_HNS. 0045: a trainer's Pokemon never flees.
    "- The lab fight these rules name is your first rival battle, outside Cherrygrove City. It plays by FireRed's "
    "first-battle rules: your damaging moves never miss, neither side lands a critical hit, and the rival battles as "
    "FireRed's first rival does. A trainer's Pokémon never runs from a battle, the rival's included.",
    # 0034: TryRestoreHeldItems.
    "- A held item that gets used up in battle stays used up. Only an item taken from you comes back after the battle.",
    # 0034: Route34_DayCare_EventScript_KaizoCoreRefuse.
    "- The Day Care on Route 34 takes no Pokémon.",
    # 0034: IsGen6ExpShareEnabled.
    "- The Exp. Share that gives EXP to the whole party is off, so a Pokémon that does not battle, such as an HM friend, gains none.",
    # 0034: Cmd_pickup skips a party member that was not sent out.
    "- Only a Pokémon that was sent out in the battle can find an item with Pickup.",
    # HnsEngine.writePreset: REUSABLE TMS OFF, NATURE MINTS OFF, SHINY CHANCE 1/8192, ITEM DROP OFF, all locked.
    "- A TM breaks after one use, as in Emerald, and there are no Nature Mints: the shop that sells them is closed.",
    "- Shiny odds are Gen 3's 1 in 8,192, and a wild Pokémon you defeat drops nothing. Neither can be changed.",
    # HnsRandomizer: a hidden item is never a TM (TM41, TM69, TM88 are hidden in Heart & Soul); item balls keep UPR's rule.
    "- A hidden item is never a TM. The TMs Heart & Soul hides become other items; a TM lying in the open is still a TM.",
    # 0004, 0018: the start menu's HM entry.
    "- The start menu has an HM entry. Once an HM is in your bag and you have the badge it needs, you can use it from there "
    "with no Pokémon knowing the move, so no field move needs an HM friend.",
    # 0020, 0025, 0026, 0032.
    "- Every Pokémon Center has a Name Rater: the Meowth standing near the counter.", ""]
# Evo Kaizo on Heart & Soul. Blake ruled (2026-10-06) that it has no evo loops, as the Nat. Dex rules say; the engine
# change that makes it so is on its own branch (HnsRandomizer.randomizeEvolutionsEveryLevel gave each species one fixed
# evolution, which loops). The Nat. Dex part replaces rules 4 and 11 for the Nat. Dex pool, as everywhere in this book.
HNS_EVO_KAIZO = [
    "In KaizoCore, Heart & Soul's Evo Kaizo has no evo loops, in either pool, as the Nat. Dex ruleset changes say. So on "
    "both pools there is no loop to break (rule 10) and no checkpoint to pivot to (rule 11): pivots are banned, no "
    "exceptions, in place of rule 4. With the Nat. Dex pool, the rest of the Evo Kaizo part of the Nat. Dex ruleset "
    "changes below applies as well.", ""]
# Lines that belong under one rule of the book, where a player reads that rule: (the rule line's start, the modes it is
# written into, the line). The anchor must be found, so a source that rewords its rule fails here, not silently.
# The levels: Standard's "increased by 50%" is the rule for every mode, but Heart & Soul runs the Emerald Nat. Dex v1.2
# settings files, which raise trainer and wild levels 60% for Kaizo and every mode built on it and 50% for Standard and
# Ultimate (read from the .rnqs files; HnsRulesTest checks each page against its own file).
# The HeartGold and SoulSilver lines below were checked against Heart & Soul's own source (pokehns-expansion, the
# kaizocore-speed2 branch of rc36.1, and tools/hns/patches) on 2026-10-06; each note says where Heart & Soul differs.
ALL_MODES = set(CHAIN)
ULTIMATE_UP = ALL_MODES - {"standard"}
HNS_AFTER = [
    ("- Randomize the game:", KAIZO_BASED,
     "In KaizoCore, Heart & Soul's settings files for Kaizo and every mode built on it raise trainer and wild Pokémon "
     "levels by 60%, not 50%."),
    # Blue's badge sets VAR_OAK_MT_SILVER_CALL; patch 0027 opens Mt. Silver from Oak's Viridian call.
    ("1. To win Ironmon you must defeat Red", ALL_MODES,
     "In KaizoCore, beating Blue for your sixteenth badge brings a call from Oak that opens Mt. Silver."),
    # Week siblings ask for their day (multichoice MULTI_DAYS_OF_WEEK); Union Cave's Lapras any day; the contest daily.
    ("2. You are allowed to change to game's time", ALL_MODES,
     "In KaizoCore, no Heart & Soul event waits for a day of the week: the Week siblings ask you to guess their day, the "
     "Union Cave Lapras is there any day, and the Bug-Catching Contest is open every day, once a day."),
    # Both MtSilver_Summit maps are WEATHER_SNOW; battles get the Gen 9 Snow (B_OVERWORLD_SNOW), never Hail.
    ("3. In many cases, you'll fight Red without hail", ALL_MODES,
     "In KaizoCore, the top of Mt. Silver always snows, whatever the date or time, so Red's battle always starts in snow: "
     "Heart & Soul's newer form of hail, which does no damage and raises the Defense of Ice types."),
    # setwildbattleshiny GYARADOS; FLAG_HIDE_LAKE_OF_RAGE_GYARADOS is cleared by no Hall of Fame script.
    ("5. You cannot defeat the Shiny Gyarados", ALL_MODES,
     "In KaizoCore, Heart & Soul's red Gyarados never comes back once you beat or catch it."),
    # rc38 (Blake, 2026-10-06: "just cut those short cuts then"): the pharmacist's delivery is gone; CianwoodShop_hns
    # gives the Secret Potion as the game does, and Jasmine takes it at the top (OlivineLighthouse).
    ("1. You can only fight trainers in the Lighthouse", ULTIMATE_UP,
     "In KaizoCore, the second climb is back, as in the game: the Cianwood pharmacist gives you the Secret Potion and "
     "you carry it up the Lighthouse to Jasmine."),
    # Patch 0027: the Basement Key lands at the Underground's entrance, out of every trainer's sight. rc38: the Card Key
    # no longer moves you; you walk back out past the Underground's rooms.
    ("2. Each building of the Radio Tower", ULTIMATE_UP,
     "In KaizoCore, the Basement Key takes you straight to the Underground's entrance; after the Card Key you walk back "
     "out the way you came. You can still walk out and heal between the buildings."),
    # SeafoamIslands 1F and B1F: no trainerbattle; Blaine is the gym's only trainer.
    ("3. Seafoam Islands is treated as two", ULTIMATE_UP,
     "In KaizoCore, Heart & Soul's Seafoam cave has items but no trainers, and Blaine is the only trainer in his gym."),
    # OlivineCity_PokemonCenter_hns: no trainerbattle.
    ("4. The Pokémon Center in Olivine City", ULTIMATE_UP,
     "In KaizoCore, Heart & Soul's Olivine Pokémon Center has no trainer in it."),
    # Only Dark Cave requires_flash; the reward rooms check the party's species (RuinsOfAlph_PuzzleAndRewardChambers_hns).
    ("1. You cannot use Flash to get the Ruins of Alph items", KAIZO_BASED,
     "In KaizoCore, Heart & Soul's Ruins of Alph are not dark, so Flash plays no part there: each item room opens when a "
     "Pokémon in your party is Ho-Oh, Aerodactyl, Kabuto or Omanyte. Only Dark Cave is dark."),
    # Super Kaizo's HeartGold and SoulSilver section.
    ("    - The pivot must be done in one of the Bug Catching Contest", {"superkaizo"},
     "In KaizoCore, Heart & Soul's Bug-Catching Contest is open every day, once a day, so no time change and no Celebi is needed."),
    ("    - The TM on Route 42", {"superkaizo"},
     "In KaizoCore, Heart & Soul's Route 42 TM lies by Mt. Mortar's north entrance."),
    ("    - The TM inside Burned Tower", {"superkaizo"},
     "In KaizoCore, Heart & Soul's Burned Tower TM is in the basement's southwest corner, beside a Strength boulder."),
    ("    6. Must fight Red with hail weather active", {"superkaizo"},
     "In KaizoCore, the top of Mt. Silver always snows, Heart & Soul's newer form of hail, so Red is always fought in it "
     "and no clock change is needed."),
    # Free heals (Blake asked, 2026-10-06; the official rules answer it). Survival's limit counts "a PokeCenter (or
    # equivalent NPC)", so every free whole-party heal outside a dungeon is one of the heals: NewBarkTown_Lab_hns's heal
    # machine (any time after the first Pokemon), the National Park teacher (NationalPark_Normal_hns, every talk), any
    # other. Patch 0031's heal with the balls comes after the first rival battle and before any other trainer, so before
    # Survival's limit starts. Kaizo's "No Healing Stations in Dungeons" bans an NPC or bed inside a dungeon, story heals
    # aside; National Park is outdoors, and Elm's lab has no trainers, so neither is a dungeon.
    ("- 10 Heal Limit:", {"survival"},
     "In KaizoCore, every free heal of the whole party outside a dungeon counts as one of these heals, as a Pokémon Center "
     "does: the heal machine in Elm's lab, the teacher in National Park, and any other person who heals your party. The "
     "heal Elm's aide gives with the Poké Balls comes before the limit starts."),
    ("- Limited Healthcare:", {"survivalrevival"},
     "In KaizoCore, every free heal of the whole party outside a dungeon counts as one of these Pokémon Center visits: the "
     "heal machine in Elm's lab, the teacher in National Park, and any other person who heals your party."),
    ("- No Healing Stations in Dungeons:", KAIZO_BASED,
     "In KaizoCore, a person or bed that heals you inside a Heart & Soul dungeon is off limits, apart from the heals the "
     "story makes. National Park is outdoors and Elm's lab has no trainers, so neither is a dungeon."),
]
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
NATDEX_NOTE_HNS = ("(On Heart & Soul, this section is for a run with the Nat. Dex pool only; a Vanilla pool run skips it. "
                   "Rules the page marks \"v1.0.0 to v1.1.3 only\" are left out: KaizoCore's Nat. Dex is 1.2.1.)")
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
    by_chain = lambda m: list(CHAIN).index(m) if m in CHAIN else 99

    def place_after(out, prefix, line):
        """[line] as a sub-bullet at the end of the rule that starts with [prefix] (the rule and the lines indented under it)."""
        at = next((i for i, l in enumerate(out) if l.startswith(prefix)), None)
        assert at is not None, ("no rule starts with", prefix)
        indent = len(out[at]) - len(out[at].lstrip())
        end = at + 1
        while end < len(out) and out[end].strip() and len(out[end]) - len(out[end].lstrip()) > indent: end += 1
        out.insert(end, " " * (indent + 4) + "- " + line)

    def book(mode, family, fam_label, label, folder, updates, natdex_build, build, intro=(), sk_family=None, after=()):
        """One mode's file: the chain, the game's own updates, the Nat. Dex changes, MaxDex's section, the sources.
        [after] puts a line under one rule (see HNS_AFTER)."""
        out = [f"# {label}: {MODE_LABEL[mode]}", "",
               "Every ruleset builds on the ones before it, so they are all here in order, then this game's own updates.", ""]
        out += list(intro)
        used = ["rules", "games"]
        for step in CHAIN[mode]:
            if step in base:
                title, lines = base[step]
                out += [f"## {title}", ""] + lines + [""]
                if step == "ultimate" and family == "FRLG":
                    out += ["### FireRed and LeafGreen, Ultimate", ""] + frlg_ultimate + [""]
            elif step == "superkaizo":
                out += ["## Super Kaizo IronMON", "", *sk_disclaimer, "", *sk_general, ""]
                g = sk_game(sk_family or family)
                if g: out += [f"### Super Kaizo, {fam_label}", ""] + g + [""]
                if family == HNS_TAG: out += [NOTE_SMART_AI_HNS, ""]
                elif natdex_build: out += [NOTE_SMART_AI_NATDEX, ""]
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
                # Heart & Soul takes it only with the Nat. Dex pool, and its engine's evolutions can loop (HNS_EVO_KAIZO).
                if step == "evokaizo" and family == HNS_TAG: out += HNS_EVO_KAIZO
                elif step == "evokaizo" and natdex_build: out += ["On a Nat. Dex build, the Evo Kaizo part of the Nat. Dex ruleset changes below replaces rules 4 and 11: pivots are banned, with no checkpoints.", ""]
                used.append(step)
        own = [(t, ls) for t, ls in updates if t == "Settings notes" or applies(t, mode)]
        if own:
            out += [f"## {fam_label}: game-specific rules", ""]
            for t, ls in own:
                out += [f"### {t}", ""] + ls + [""]
        if natdex_build:
            changes = natdex_maxdex if build == "MaxDex" else natdex
            # Heart & Soul's Vanilla pool reads past this section, so its head says so where a Vanilla player reads it.
            if family == HNS_TAG: changes = [NATDEX_NOTE_HNS] + changes[1:]
            out += ["## Nat. Dex ruleset changes", ""] + changes + [""]
            used.append("natdex")
        if build == "MaxDex":
            out += ["## MaxDex", ""] + maxdex + [""]
            used.append("maxdex")
        for prefix, line in after: place_after(out, prefix, line)
        out += ["## Sources", ""]
        for k in used:
            n, u, d = SOURCES[k]; out.append(f"- {n}: {u} ({d})")
        out += ["", f"Generated {today} by tools/rules/build_rules.py. Rulesets get revised; if this reads behind a source, regenerate."]
        text = american(re.sub(r"\n{3,}", "\n\n", "\n".join(out)).rstrip() + "\n")
        assert "—" not in text, (family, mode)
        d = OUT / folder
        d.mkdir(parents=True, exist_ok=True)
        (d / f"{mode}.md").write_text(text, encoding="utf-8")
        written.append(f"{d.name}/{mode}")

    for heading, (family, fam_label) in FAMILIES.items():
        # The game, its Nat. Dex build, and MaxDex (FireRed's, from Trip's own preset): Nat. Dex plus its own section.
        for build in ("", "NatDex", "MaxDex"):
            natdex_build = build != ""
            updates = game_updates(games_md, heading, natdex_build)
            modes = sorted(matrix.get((family, build), set()), key=by_chain)
            label = fam_label + {"": "", "NatDex": ", Nat. Dex", "MaxDex": ", MaxDex"}[build]
            for mode in modes:
                if mode not in CHAIN: continue
                book(mode, family, fam_label, label, family + ("-" + build if build else ""), updates, natdex_build, build)

    # Heart & Soul (docs/HNS-KAIZO.md): HeartGold and SoulSilver's story, Johto then Kanto with sixteen badges, on Emerald's
    # engine with the modern Pokemon and type chart, run with the Emerald Nat. Dex v1.2 settings files (HnsEngine). No
    # ruleset is written for it, so its book is each mode's chain, HeartGold and SoulSilver's game rules, and the Nat. Dex
    # ruleset changes, with a KaizoCore line that says so. Its modes are the files the app offers it (RulesetCatalog).
    hgss_heading = next(h for h, (f, _) in FAMILIES.items() if f == "HGSS")
    hgss_updates = [(t, ls) for t, ls in game_updates(games_md, hgss_heading, True) if t != "Settings notes"]
    hns_modes = sorted(matrix.get(("RSE", "NatDex"), set()) | matrix.get((HNS_TAG, ""), set()), key=by_chain)
    for mode in hns_modes:
        if mode not in CHAIN: continue
        book(mode, HNS_TAG, TAG_LABEL["HGSS"], HNS_LABEL, HNS_TAG, hgss_updates, True, "NatDex", intro=HNS_INTRO + HNS_GAME,
             sk_family="HGSS", after=[(p, l) for p, modes, l in HNS_AFTER if mode in modes])
    print(len(written), "files:", ", ".join(written))

if __name__ == "__main__":
    main()
