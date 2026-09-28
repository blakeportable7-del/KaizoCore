"""Regenerate editor/src/main/resources/upr-help.tsv from the desktop randomizer.

Supersedes the HELP block of tools/extract_upr.py (2026-09-27, audit), which
stripped the backslash from every "\\n" (leaving a literal "n" mid-sentence),
ran HTML lists together, kept desktop-layout words ("to the right", "slider
below") and gave each mode enum only its Unchanged button's tooltip.

What this writes, per settings field:
  - the tooltip of the control that sets it, line breaks kept (written as the
    two characters backslash + n; SettingsReflector turns them back),
  - HTML list items as bullet lines,
  - for a mode enum: one line saying what the setting is, then one bullet per
    value with that radio button's own tooltip,
  - phone wording for desktop-layout references (PHONE below),
  - "Pokémon" spelled with its accent.

Run from the repo root:  python editor/src/test/tools/extract_upr_help.py
"""
import re

ZX = ".vendor/upr-zx-461/src/com/dabomstew/pkrandom"
ND = ".vendor/upr-natdex/src/com/dabomstew/pkrandom"
OUT = "editor/src/main/resources/upr-help.tsv"


def bundle(path):
    """Java .properties, with its escapes resolved (\\n, \\uXXXX, \\x)."""
    out = {}
    for line in open(path, encoding="latin-1"):
        line = line.rstrip("\n").rstrip("\r")
        m = re.match(r"([\w.]+?)\.?=(.*)", line)
        if not m:
            continue
        v = m.group(2)
        v = re.sub(r"\\u([0-9a-fA-F]{4})", lambda u: chr(int(u.group(1), 16)), v)
        v = v.replace("\\n", "\n").replace("\\t", " ")
        v = re.sub(r"\\(.)", r"\1", v)
        out[m.group(1)] = v
    return out


B = bundle(ND + "/newgui/Bundle.properties")
B.update(bundle(ZX + "/newgui/Bundle.properties"))   # ZX wording wins where both have it


def tip(comp):
    for k in ("GUI.%s.toolTipText", "GUI.%s.tooltipText"):
        if (k % comp) in B:
            return B[k % comp]
    return None


def plain(html):
    t = html.replace("\r", "")
    t = re.sub(r"<li>", "\n• ", t, flags=re.I)
    t = re.sub(r"</?(ul|ol)>|</li>", "\n", t, flags=re.I)
    t = re.sub(r"<br\s*/?>", "\n", t, flags=re.I)
    t = re.sub(r"<[^>]+>", "", t)
    t = (t.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
          .replace("&gt;", ">").replace("&quot;", '"'))
    lines = [" ".join(l.split()) for l in t.split("\n")]
    # "*" bullets written as text become real bullets.
    lines = [re.sub(r"^\* ?", "• ", l) for l in lines]
    out = []
    for l in lines:
        if l == "" and (not out or out[-1] == ""):
            continue
        out.append(l)
    while out and out[-1] == "":
        out.pop()
    return "\n".join(out)


# Desktop-layout references, rewritten for a phone list (checked by
# UprHelpGateTest: none of the desktop phrases may survive).
PHONE = [
    ("chosen with the settings to the right", "chosen with the setting after this one"),
    ("unless you check some boxes to the right", "unless you turn on some of the evolution settings"),
    ("unless you check some boxes below", "unless you turn on some of the evolution settings"),
    ("Set amount of guaranteed moves with the slider to the right", "Set how many in the next setting"),
    ("Set amount of guaranteed moves with the slider below", "Set how many in the next setting"),
    ("can be set using the slider to the right", "is the next setting"),
    ("can be set using the slider below", "is the next setting"),
    ("all other modifiers below", "all the other evolution settings"),
    ("in the generation chosen to the right", "in the generation chosen in the next setting"),
    ("the generation chosen to the right", "the generation chosen in the next setting"),
    ("chosen to the right", "chosen in the next setting"),
    ("to the right", "in the next setting"),
    ("the selected EXP curve below", "the selected EXP curve"),
    ("use the slider below to select", "set"),
    ("Use this slider to select", "Sets"),
    ("Use this slider to set", "Sets"),
    ("Use this to set", "Sets"),
    ("for the option above", "for the setting before this one"),
    ("if said option is checked above", "if that setting is on"),
    ("the level you select below", "the level set in the next setting"),
    ("(select amount of new Pokemon below)", "(the number is the setting itself; 0 is off)"),
    ("Set amount of guaranteed moves with the slider", "Set how many in the next setting"),
    ("can be set using the slider", "is the next setting"),
    ("The percentages below", "The percentages here"),
    ("modifiers below", "other evolution settings"),
    ("Checking this checkbox", "Turning this on"),
    ("Checking this", "Turning this on"),
    ("If this is checked", "When this is on"),
    ("If this box isn't checked", "When this is off"),
    ("When this is checked", "When this is on"),
    ("When selected", "When on"),
    ("Check this to", "Turn this on to"),
    ("Select this to", "Turn this on to"),
    ("Selecting this option will", "Turning this on will"),
    ("Selecting this will", "Turning this on will"),
    ("If you select this option", "When this is on"),
    ("If you select this", "When this is on"),
    ("When this is selected", "When this is on"),
    ("If this is selected", "When this is on"),
    ("If this is turned on", "When this is on"),
    ("Enabling this setting", "Turning this on"),
    ("Selecting this", "Turning this on"),
    ("is selected", "is chosen"),
    # Last: the generic words, once the phrases around them are rewritten.
    ("slider", "setting"),
    ("checkbox", "setting"),
]


def phone(t):
    for a, b in PHONE:
        t = t.replace(a, b)
    t = re.sub(r"\bPokemon\b", "Pokémon", t)
    t = re.sub(r"\bpokemon\b", "Pokémon", t)
    t = t.replace("Poke Ball", "Poké Ball")
    return t


# ---------------------------------------------------------------- the join
src = open(ZX + "/newgui/NewRandomizerGUI.java", encoding="utf-8", errors="replace").read()
nsrc = open(ND + "/newgui/NewRandomizerGUI.java", encoding="utf-8", errors="replace").read()
CONTROL = r"\b([a-z]\w*(?:CheckBox|Checkbox|RadioButton|ComboBox|Slider|Spinner))\b"


def setters(s):
    i = s.index("private Settings createSettingsFromState")
    body = s[i:s.index("\n    }", i)]
    return re.findall(r"settings\.set(\w+)\(([^;]*?)\);", body, re.S)


fields = {}
for name, args in setters(src) + setters(nsrc):
    f = name[0].lower() + name[1:]
    if f not in fields:
        fields[f] = list(dict.fromkeys(re.findall(CONTROL, args)))

# Controls whose tooltip key does not follow the binding name.
ALIAS = {
    "peRemoveTimeBasedEvolutionsCheckBox": "peRemoveTimeBasedEvolutions",
    "shGuaranteeXItemsCheckBox": "shGuaranteeXItemsCheckbox",
    "stpFixMusicCheckBox": "stpFixMusicAllCheckBox",
    "totpAuraRandomRadioButton": "totpAuraRandomRadioButton",
}

# What each mode enum IS, before its values. The desktop only ever had a
# tooltip per radio button, so without this line the editor described
# "Base stats" as "Don't change Pokémon stats from the base at all".
ENUM_INTRO = {
    "baseStatisticsMod": "How each Pokémon's base stats change.",
    "expCurveMod": "Which Pokémon keep the Slow EXP curve when EXP curves are standardized.",
    "typesMod": "How each Pokémon's types change.",
    "abilitiesMod": "Whether each Pokémon gets new abilities.",
    "evolutionsMod": "What each Pokémon evolves into.",
    "startersMod": "Which three Pokémon are offered as starters.",
    "staticPokemonMod": "What the fixed encounters, gifts and purchases become.",
    "inGameTradesMod": "Which Pokémon the in-game trades ask for and give.",
    "movesetsMod": "Which moves each Pokémon learns by level.",
    "trainersMod": "Which Pokémon the trainers use.",
    "totemPokemonMod": "Which species the Totem Pokémon are.",
    "allyPokemonMod": "Which Pokémon the Totems call as allies.",
    "auraMod": "Which stat boosts the Totem Pokémon's auras give.",
    "wildPokemonMod": "Which Pokémon appear in the wild, and how they are placed.",
    "wildPokemonRestrictionMod": "An extra rule for the wild Pokémon chosen.",
    "tmsMod": "Which move each TM teaches.",
    "tmsHmsCompatibilityMod": "Which TMs and HMs each Pokémon can learn.",
    "moveTutorMovesMod": "Which move each move tutor teaches.",
    "moveTutorsCompatibilityMod": "Which move tutor moves each Pokémon can learn.",
    "fieldItemsMod": "The items in item balls and hidden spots.",
    "shopItemsMod": "The items sold in the special (non-main) shops.",
    "pickupItemsMod": "The items found with the Pickup ability.",
}
# The button text of each radio, same order as the enum constants.
TRAINER_KEYS = ["tpMain0Unchanged", "tpMain1Random", "tpMain2RandomEvenDistribution",
                "tpMain3RandomEvenDistributionMainGame", "tpMain4TypeThemed",
                "tpMain5TypeThemedEliteFourGyms"]

# Help written here, for fields the desktop gives no tooltip or a misleading one.
EXTRA = {
    "wildBSTLimit": "Which legendaries \"No legendaries\" keeps out of the wild:\n"
                    "• All legendaries, Mythicals, Ultra Beasts and Paradox Pokémon\n"
                    "• All legendaries and Mythicals\n"
                    "• Only strong legendaries and Mythicals",
    "wildPokemonBSTLimit": "An upper base stat total for random wild Pokémon, from 307 to 780. 0 is off.",
    "updateBaseStatsToGeneration": "The generation whose base stats \"Update base stats\" uses.",
    "updateMovesToGeneration": "The generation whose move stats \"Update moves\" uses.",
    "eliteFourUniquePokemonNumber": None,   # keep the tooltip, see below
}

rows = []
order = [l.split("\t")[0] for l in open("editor/src/main/resources/upr-order.tsv", encoding="utf-8")
         if l.strip() and not l.startswith("#")]
seen = set()
for f in order + sorted(fields):
    if f in seen or f not in fields:
        continue
    seen.add(f)
    comps = [ALIAS.get(c, c) for c in fields[f]]
    text = None
    if f in EXTRA and EXTRA[f]:
        text = EXTRA[f]
    elif f == "trainersMod":
        text = ENUM_INTRO[f] + "\n" + "\n".join(
            "• %s: %s" % (B["GUI.%s.text" % k], plain(tip(k) or "")) for k in TRAINER_KEYS)
    elif f in ENUM_INTRO and len(comps) > 1:
        parts = [ENUM_INTRO[f]]
        for c in comps:
            label = B.get("GUI.%s.text" % c, "").rstrip(":").replace("&&", "&")
            t = tip(c)
            if label and t:
                parts.append("• %s: %s" % (label, plain(t).replace("\n", " ")))
            elif label:
                parts.append("• %s" % label)
        text = "\n".join(parts)
    else:
        # A number set by a slider/spinner: that control's tooltip first.
        for c in sorted(comps, key=lambda c: 0 if c.endswith(("Slider", "Spinner")) else 1):
            t = tip(c)
            if t:
                text = plain(t)
                break
    if f == "selectedEXPCurve":
        text = None   # the desktop has no tooltip; the editor hides it anyway
    if text:
        rows.append((f, phone(text)))

with open(OUT, "w", encoding="utf-8", newline="\n") as o:
    o.write("# GENERATED by editor/src/test/tools/extract_upr_help.py - the randomizer's OWN tooltip text,\n")
    o.write("# line breaks written as \\n, desktop-layout words rewritten for a phone.\n")
    o.write("# settingsField\tdescription\n")
    for f, t in rows:
        o.write(f + "\t" + t.replace("\t", " ").replace("\n", "\\n") + "\n")
print("upr-help.tsv: %d descriptions" % len(rows))
