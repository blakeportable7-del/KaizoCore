"""Build app/src/main/assets/rulesets/<FAMILY>/<mode>.md from the official sources.

Sources, all kept beside this script's inputs in tools/upr-settings/:
  IronMon-Rules.md       the IronMON rules gist (valiant-code/adb18d248fa0fae7da6b639e2ee8f9c1),
                         Standard, Ultimate, Kaizo, Survival, saved with `gh gist view --raw`
  official-settings.md   the game-specific rules and settings gist (UTDZac/a147c497424dfbd537d8c4b0c22b5621),
                         per-game "Rules updates" sections and the Kaizo Doubles rules
  SuperKaizo-README.md   PyroMikeGit/SuperKaizoIronMON README, the Super Kaizo rules and per-game pivots

A mode's file lists every ruleset it builds on, in order, because each gist
section says "this ruleset includes all of the previous rules ... read those
first". Then the game's own updates for that mode. Nothing is paraphrased:
table rows become bullets, HTML list items become sub-bullets, links keep
their text and URL.

Run:  python tools/rules/build_rules.py   (from the repo root)
"""
import pathlib, re, sys, datetime

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / "tools" / "upr-settings"
OUT = ROOT / "app" / "src" / "main" / "assets" / "rulesets"
PRESETS = ROOT / "app" / "src" / "main" / "assets" / "presets"

SOURCES = {
    "rules": ("IronMON rules gist by valiant-code", "https://gist.github.com/valiant-code/adb18d248fa0fae7da6b639e2ee8f9c1", "gist updated 2026-08-31"),
    "games": ("game-specific rules gist by UTDZac", "https://gist.github.com/UTDZac/a147c497424dfbd537d8c4b0c22b5621", "saved 2026-09-05"),
    "super": ("Super Kaizo IronMON by PyroMikeGit", "https://github.com/PyroMikeGit/SuperKaizoIronMON", "repository pushed 2026-03-20"),
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
SUPER_GAME = {"FRLG": "Fire Red / Leaf Green", "RSE": "Emerald", "HGSS": "Heart Gold / Soul Silver", "DPPt": "Platinum"}

MODE_LABEL = {"standard": "Standard", "ultimate": "Ultimate", "kaizo": "Kaizo", "superkaizo": "Super Kaizo",
              "survival": "Survival", "kaizodoubles": "Kaizo Doubles"}
# What each mode builds on, in reading order (each gist section says "read those first").
CHAIN = {
    "standard": ["standard"],
    "ultimate": ["standard", "ultimate"],
    "kaizo": ["standard", "ultimate", "kaizo"],
    "superkaizo": ["standard", "ultimate", "kaizo", "superkaizo"],
    "survival": ["standard", "ultimate", "kaizo", "survival"],
    "kaizodoubles": ["standard", "ultimate", "kaizo", "kaizodoubles"],
}
# Which modes a per-game "Rules updates for X" heading applies to.
def applies(title, mode):
    t = title.lower()
    if "general" in t or "standard & up" in t: return True
    if "standard & ultimate" in t: return mode in ("standard", "ultimate")
    if "ultimate & up" in t: return mode in ("ultimate", "kaizo", "superkaizo", "survival", "kaizodoubles")
    if "super kaizo" in t: return mode == "superkaizo"
    if "kaizo & up" in t or "kaizo & survival" in t or "kaizo/survival" in t: return mode in ("kaizo", "superkaizo", "survival", "kaizodoubles")
    if "survival" in t: return mode == "survival"
    return False

def clean(s):
    s = s.replace("—", ", ").replace("–", "-")
    s = re.sub(r"<h3>(.*?)</h3>", r"\1", s)
    s = re.sub(r"</?b>", "", s)
    s = re.sub(r"<br\s*/?>", " ", s)
    s = re.sub(r"\[([^\]]+)\]\((https?://[^)]+)\)", r"\1 (\2)", s)
    s = re.sub(r"<li>(.*?)</li>", r"\n    - \1", s)
    s = re.sub(r"</?ul>", "", s)
    s = re.sub(r"[ \t]+", " ", s)
    return s.strip()

def gist_tables(text):
    """'## Heading' -> list of blocks: ('row', rule, details, notes) | ('sub', text) | ('line', text)."""
    out, cur = {}, None
    for line in text.splitlines():
        if line.startswith("## "):
            cur = line[3:].strip(); out[cur] = []; continue
        if cur is None: continue
        s = line.strip()
        if s.startswith("|"):
            cells = [c.strip() for c in s.strip("|").split("|")]
            if not cells or set("".join(cells)) <= set("-: ") or cells[0] in ("Rule",): continue
            rule = cells[0]; details = cells[1] if len(cells) > 1 else ""; notes = cells[2] if len(cells) > 2 else ""
            if not rule and "<h3>" in details: out[cur].append(("sub", clean(details))); continue
            if not rule and not details: continue
            out[cur].append(("row", clean(rule), clean(details), clean(notes)))
        elif s and not s.startswith("#") and not s.startswith("---"):
            out[cur].append(("line", clean(s)))
    return out

def render_blocks(blocks, skip_leading_prose=False):
    lines = []
    for b in blocks:
        if b[0] == "row":
            _, rule, details, notes = b
            lines.append(f"- {rule}: {details}" if rule else f"- {details}")
            if notes: lines.append(f"    - Note: {notes}")
        elif b[0] == "sub":
            lines.append(""); lines.append(f"### {b[1]}")
        else:
            lines.append(b[1])
    return lines

def section(text, start_re, end_re):
    m = re.search(start_re, text, re.M); assert m, start_re
    rest = text[m.end():]
    e = re.search(end_re, rest, re.M)
    return rest[:e.start()] if e else rest

def game_updates(games_md, family_heading):
    body = section(games_md, r"^## " + re.escape(family_heading) + r"\s*$", r"^## ")
    subs = re.split(r"^#### ", body, flags=re.M)[1:]
    out = []
    for sub in subs:
        title, _, content = sub.partition("\n")
        if "settings strings" in title.lower(): continue
        # blockquoted settings strings stay, as plain lines, so a numbered workaround keeps its string
        out.append((title.strip(), [clean(l.strip().lstrip(">").strip().strip("`")) for l in content.splitlines() if l.strip() and l.strip().strip("> ").strip("`")]))
    return out

def preset_matrix():
    fam = {}
    for f in PRESETS.glob("*.rnqs"):
        n = f.stem
        if "PART 2" in n: continue
        tag = n.split()[0]
        key = re.sub(r"natdex v[\d.]+", "", n.lower()).replace(tag.lower(), "", 1).replace(" ", "")
        for k in ("survivalrevival", "kaizodoubles", "superkaizo", "standard", "survival", "ultimate", "kaizo"):
            if k in key: key = k; break
        fam.setdefault(tag, set()).add(key)
    return fam

def main():
    rules = gist_tables((SRC / "IronMon-Rules.md").read_text(encoding="utf-8"))
    games_md = (SRC / "official-settings.md").read_text(encoding="utf-8")
    sk = (SRC / "SuperKaizo-README.md").read_text(encoding="utf-8")
    base = {
        "standard": ("Standard IronMON", render_blocks(rules["Standard IronMon Ruleset"])),
        "ultimate": ("Ultimate IronMON", render_blocks([b for b in rules["Ultimate IronMon Ruleset"] if not (b[0] == "line" and re.match(r"^(FIRE RED/LEAF GREEN SPECIFIC|[A-C]\))", b[1]))])),
        "kaizo": ("Kaizo IronMON", render_blocks(rules["Kaizo IronMon Ruleset"])),
        "survival": ("Survival IronMON", render_blocks(rules["Survival IronMon Ruleset"])),
    }
    frlg_ultimate = [b[1] for b in rules["Ultimate IronMon Ruleset"] if b[0] == "line" and re.match(r"^(FIRE RED/LEAF GREEN SPECIFIC|[A-C]\))", b[1])]
    doubles = [clean(l) for l in section(games_md, r"^## KAIZO DOUBLES RULES\s*$", r"^## ").splitlines() if l.strip()]
    sk_general = [clean(l) for l in section(sk, r"^## General Rules for All Games\s*$", r"^## ").splitlines() if l.strip()]
    sk_disclaimer = [clean(l) for l in section(sk, r"^## VERY LARGE DISCLAIMER from iateyourpie\s*$", r"^## ").splitlines() if l.strip()]
    def sk_game(family):
        h = SUPER_GAME.get(family)
        if not h: return []
        return [clean(l) for l in section(sk, r"^### " + re.escape(h) + r"\s*$", r"^##").splitlines() if l.strip()]

    matrix = preset_matrix()
    today = datetime.date.today().isoformat()
    written = []
    for heading, (family, label) in FAMILIES.items():
        modes = sorted(matrix.get(family, set()), key=lambda m: list(CHAIN).index(m) if m in CHAIN else 99)
        updates = game_updates(games_md, heading)
        for mode in modes:
            if mode not in CHAIN: continue
            out = [f"# {label}: {MODE_LABEL[mode]}", "",
                   "Every ruleset builds on the ones before it, so they are all here in order, then this game's own updates.", ""]
            for step in CHAIN[mode]:
                if step in base:
                    title, lines = base[step]
                    out += [f"## {title}", ""] + lines + [""]
                    if step == "ultimate" and family == "FRLG":
                        out += ["### FireRed and LeafGreen, Ultimate", ""] + frlg_ultimate + [""]
                elif step == "superkaizo":
                    out += ["## Super Kaizo IronMON", "", *sk_disclaimer, "", *sk_general, ""]
                    g = sk_game(family)
                    if g: out += [f"### Super Kaizo, {label}", ""] + g + [""]
                elif step == "kaizodoubles":
                    out += ["## Kaizo Doubles", ""] + doubles + [""]
            own = [(t, ls) for t, ls in updates if applies(t, mode)]
            if own:
                out += [f"## {label}: game-specific rules", ""]
                for t, ls in own:
                    out += [f"### {t}", ""] + ls + [""]
            out += ["## Sources", ""]
            for k in ("rules", "games") + (("super",) if mode == "superkaizo" else ()):
                n, u, d = SOURCES[k]; out.append(f"- {n}: {u} ({d})")
            out += ["", f"Generated {today} by tools/rules/build_rules.py. Rulesets get revised; if this reads behind the gist, regenerate."]
            text = "\n".join(out).rstrip() + "\n"
            assert "—" not in text
            d = OUT / family; d.mkdir(parents=True, exist_ok=True)
            (d / f"{mode}.md").write_text(text, encoding="utf-8"); written.append(f"{family}/{mode}")
    print(len(written), "files:", ", ".join(written))

if __name__ == "__main__":
    main()
