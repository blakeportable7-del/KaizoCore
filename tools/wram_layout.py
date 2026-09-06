"""
Compute WRAM label addresses from a pret Game Boy disassembly without rgbds.

    python tools/wram_layout.py ~/ironmon-ref/pokegold wPartyCount wEnemyMon ...
    python tools/wram_layout.py ~/ironmon-ref/pokecrystal --check

The pret repos place ram/wram.asm's sections by link order (layout.link),
not by explicit addresses, so a label's address is the sum of every ds/db/dw
and struct macro before it. This walks the sources the way rgbasm and
rgblink would, for the subset a RAM layout needs: constants (DEF/EQU, `=`,
`+=`, const_def/const, rsreset/rb/rw, EQUS units, counter macros such as
add_tm), struct macros (party_struct, battle_struct, flag_array ...),
`for`/`endr` with {d:n} interpolation, `if`/`else`/`endc` with DEF(),
UNION/NEXTU/ENDU as the largest branch, SECTION/ENDSECTION, and the link
file's order, `align` and `org`.

`--check` validates it on pokecrystal against addresses already proven for
Crystal in GbcTracker (from the Gen 2 reference tracker) before its numbers
for pokegold are believed. Unresolved constants and unknown directives are
printed to stderr; an empty stderr and a clean check is the bar.
"""
import re
import sys
from pathlib import Path


# ------------------------------------------------------------- constants
class Consts:
    def __init__(self):
        self.vals = {}
        self.strs = {}          # EQUS units such as `palettes` -> "* PAL_SIZE"
        self.pending = {}       # name -> expression text, evaluated lazily

    def define(self, name, expr):
        self.pending[name] = expr
        self.vals.pop(name, None)

    def set(self, name, value):
        self.vals[name] = value
        self.pending.pop(name, None)

    def has(self, name):
        return name in self.vals or name in self.pending

    def get(self, name, depth=0):
        if name in self.vals:
            return self.vals[name]
        if name in self.pending:
            if depth > 60:
                raise RecursionError(name)
            v = self.eval(self.pending[name], depth + 1)   # popped only on success
            self.pending.pop(name, None)
            self.vals[name] = v
            return v
        raise KeyError(name)

    def eval(self, expr, depth=0, env_extra=None):
        e = expr.strip()
        for s, rep in self.strs.items():
            e = re.sub(r"\b%s\b" % re.escape(s), lambda m, rep=rep: rep, e)
        e = re.sub(r"DEF\((\w+)\)", lambda m: "1" if self.has(m.group(1)) or (env_extra and m.group(1) in env_extra) else "0", e, flags=re.I)
        e = re.sub(r"\$([0-9A-Fa-f]+)", lambda m: str(int(m.group(1), 16)), e)
        e = re.sub(r"%([01]+)\b", lambda m: str(int(m.group(1), 2)), e)
        e = e.replace("&&", " and ").replace("||", " or ").replace("!", " not ")
        e = re.sub(r"\bnot\s*=", "!=", e)
        env = dict(env_extra or {})
        for n in set(re.findall(r"[A-Za-z_][A-Za-z_0-9]*", e)):
            if n in ("and", "or", "not") or n in env:
                continue
            env[n] = self.get(n, depth)
        return int(eval(e, {"__builtins__": {}}, env))


# ----------------------------------------------------------------- engine
class Asm:
    """A line-stream walker shared by the constants pass and the WRAM pass."""

    def __init__(self, c, macros):
        self.c, self.macros = c, macros
        self.sections = {}      # name -> {"size": n, "labels": {name: off}}
        self.cur = None
        self.off = 0
        self.union = []
        self.unknown = set()
        self.missing = set()
        self.cv, self.step = 0, 1       # const_def counter
        self.rs = "0"                   # rgbds _RS as a lazy expression
        self.loopvars = {}              # for-loop variables in scope
        self.mutable = set()            # names assigned with `=`/`+=`: EQU snapshots them at definition

    # ---- control flow -------------------------------------------------
    def run(self, lines):
        i = 0
        n = len(lines)
        while i < n:
            raw = lines[i]
            t = raw.split(";")[0].strip()
            i += 1
            if not t:
                continue
            t = re.sub(r"^(\w+)\?", r"\1", t)      # rgbds `for?`, `rept?`, `MACRO?` spellings
            low = t.lower()
            if low.startswith("macro ") or re.match(r"\w+:\s*macro\b", low):
                _, i = self.block(lines, i, ("macro ",), ("endm",))   # a definition, not code
                continue
            if low.startswith("for "):
                body, i = self.block(lines, i, ("for ",), ("endr",))
                self.for_loop(t[4:], body)
                continue
            if low.startswith(("if ", "if(")):
                branches, i = self.if_block(lines, i, t[2:].strip())
                for cond, body in branches:
                    if cond:
                        self.run(body)
                        break
                continue
            if low.startswith("rept "):
                body, i = self.block(lines, i, ("rept ",), ("endr",))
                try:
                    count = self.c.eval(t[5:], env_extra=self.loopvars)
                except KeyError:
                    count = 0
                for _ in range(count):
                    self.run(body)
                continue
            self.line(t)

    def block(self, lines, i, opens, closes):
        depth, body = 1, []
        while i < len(lines):
            s = re.sub(r"^(\w+)\?", r"\1", lines[i].split(";")[0].strip().lower())
            i += 1
            if s.startswith(opens):
                depth += 1
            elif s.startswith(closes) or s in closes:
                depth -= 1
                if depth == 0:
                    return body, i
            body.append(lines[i - 1])
        return body, i

    def if_block(self, lines, i, cond):
        """Returns [(truth, body)] for if/elif/else and the index after endc."""
        branches, body, depth = [], [], 1
        truth = self.cond(cond)
        while i < len(lines):
            raw = lines[i]
            s = raw.split(";")[0].strip()
            low = s.lower()
            i += 1
            if low.startswith(("if ", "if(")):
                depth += 1
            elif low == "endc":
                depth -= 1
                if depth == 0:
                    branches.append((truth, body))
                    return branches, i
            elif depth == 1 and low.startswith("elif "):
                branches.append((truth, body)); body = []
                truth = self.cond(s[5:])
                continue
            elif depth == 1 and low == "else":
                branches.append((truth, body)); body = []
                truth = not any(b[0] for b in branches)
                continue
            body.append(raw)
        branches.append((truth, body))
        return branches, i

    def cond(self, expr):
        try:
            return self.c.eval(expr, env_extra=self.loopvars) != 0
        except (KeyError, SyntaxError, NameError, TypeError):
            return False

    def for_loop(self, header, body):
        parts = [p.strip() for p in header.split(",")]
        var = parts[0]
        try:
            nums = [self.c.eval(p, env_extra=self.loopvars) for p in parts[1:]]
        except (KeyError, SyntaxError, NameError, TypeError, ValueError):
            return
        if len(nums) == 1:
            rng = range(0, nums[0])
        elif len(nums) == 2:
            rng = range(nums[0], nums[1])
        else:
            rng = range(nums[0], nums[1], nums[2])
        for v in rng:
            self.loopvars[var] = v
            self.run([self.interp(l, var, v) for l in body])
        self.loopvars.pop(var, None)

    @staticmethod
    def interp(line, var, v):
        line = re.sub(r"\{0?(\d*)d:%s\}" % re.escape(var), lambda m: ("%0" + (m.group(1) or "1") + "d") % v, line)
        line = re.sub(r"\{%s\}" % re.escape(var), str(v), line)
        return line

    # ---- one line ----------------------------------------------------
    def line(self, t):
        c = self.c
        m = re.match(r"SECTION\s+(UNION\s+)?\"([^\"]+)\"\s*,\s*(\w+)", t)
        if m:
            self.close_section()
            sec = self.sections.setdefault(m.group(2), {"size": 0, "labels": {}})
            self.cur, self.off = sec, 0
            return
        if t.upper() == "ENDSECTION":
            self.close_section(); self.cur = None; return

        # constants (any file)
        m = re.match(r"(?:DEF\s+)?(\w+)\s+EQUS\s+\"(.*)\"", t, re.I)
        if m:
            if re.match(r"^[\s*+\-/()\w]*$", m.group(2)):
                c.strs[m.group(1)] = m.group(2)
            return
        m = re.match(r"(?:DEF\s+)?(\w+)\s+EQU\s+(.+)", t, re.I)
        if m:
            c.define(m.group(1), self.subst(m.group(2))); return
        m = re.match(r"(?:RE)?DEF\s+(\w+)\s*(\+=|-=|\*=|=)\s*(.+)", t, re.I)
        if m:
            name, op, expr = m.group(1), m.group(2), self.subst(m.group(3))
            self.mutable.add(name)
            try:
                v = c.eval(expr, env_extra=self.loopvars)
                if op == "=":
                    c.set(name, v)
                else:
                    cur = c.get(name)
                    c.set(name, {"+=": cur + v, "-=": cur - v, "*=": cur * v}[op])
            except (KeyError, SyntaxError, NameError, TypeError):
                if op == "=":
                    c.define(name, expr)
            return
        m = re.match(r"const_def(?:\s+(.+?))?(?:\s*,\s*(.+))?$", t)
        if m:
            self.cv = c.eval(m.group(1)) if m.group(1) else 0
            self.step = c.eval(m.group(2)) if m.group(2) else 1
            return
        m = re.match(r"const\s+(\w+)", t)
        if m:
            c.set(m.group(1), self.cv); self.cv += self.step; return
        m = re.match(r"const_skip(?:\s+(.+))?$", t)
        if m:
            self.cv += self.step * (c.eval(m.group(1)) if m.group(1) else 1); return
        m = re.match(r"const_next\s+(.+)", t)
        if m:
            self.cv = c.eval(m.group(1)); return
        m = re.match(r"shift_const\s+(\w+)", t)
        if m:
            c.set(m.group(1), 1 << self.cv); self.cv += 1; return
        if re.match(r"rsreset\b", t, re.I):
            self.rs = "0"; return
        m = re.match(r"rsset\s+(.+)", t, re.I)
        if m:
            self.rs = "(" + self.subst(m.group(1)) + ")"; return
        m = re.match(r"(?:DEF\s+)?(\w+)\s+(rb|rw|rl)\b\s*(.*)", t, re.I)
        if m:
            n = "(" + m.group(3) + ")" if m.group(3).strip() else "1"
            c.define(m.group(1), self.rs)
            self.rs = "(%s + %s * %d)" % (self.rs, n, {"rb": 1, "rw": 2, "rl": 4}[m.group(2).lower()])
            return
        if re.match(r"(MACRO\s+\w+|\w+:\s*MACRO|ENDM|charmap|INCLUDE|PURGE|EXPORT|assert|ASSERT|println|PRINTLN|warn|WARN|fail|FAIL|newcharmap|setcharmap|pushc|popc)\b", t):
            return

        # labels
        m = re.match(r"(\.?\w+)::?\s*(.*)", t)
        if m and not re.match(r"(ds|db|dw|dl|UNION|NEXTU|ENDU|align|ALIGN)\b", t) and m.group(1) not in self.macros \
                and (t[len(m.group(1)):].startswith(":") or not m.group(2)):
            label, rest = m.group(1), m.group(2).strip()
            if self.cur is not None and not label.startswith("."):
                self.cur["labels"][label] = self.off
            if not rest:
                return
            t = rest
        self.directive(t)

    def subst(self, expr):
        if re.search(r"\bconst_value\b", expr):
            expr = re.sub(r"\bconst_value\b", str(self.cv), expr)
        if re.search(r"\b_RS\b", expr):
            expr = re.sub(r"\b_RS\b", self.rs, expr)
        for k in self.mutable:
            if k in self.c.vals:
                expr = re.sub(r"\b%s\b" % re.escape(k), str(self.c.vals[k]), expr)
        for k, v in self.loopvars.items():
            expr = re.sub(r"\b%s\b" % re.escape(k), str(v), expr)
        return expr

    def directive(self, t):
        if t == "UNION":
            self.union.append([self.off, self.off]); return
        if t == "NEXTU":
            st = self.union[-1]; st[1] = max(st[1], self.off); self.off = st[0]; return
        if t == "ENDU":
            st = self.union.pop(); self.off = max(st[1], self.off); return
        m = re.match(r"ds\s+(.+?)(?:\s*,\s*.+)?$", t)
        if m:
            if self.cur is not None:
                try:
                    self.off += self.c.eval(m.group(1), env_extra=self.loopvars)
                except KeyError as e:
                    self.missing.add((str(e).strip("'"), m.group(1)))
            return
        m = re.match(r"(db|dw|dl)\b\s*(.*)", t)
        if m:
            self.off += {"db": 1, "dw": 2, "dl": 4}[m.group(1)] * self.count(m.group(2)); return
        m = re.match(r"align\s+(.+)", t, re.I)
        if m:
            a = 1 << self.c.eval(m.group(1)); self.off = (self.off + a - 1) // a * a; return
        m = re.match(r"(\w+)\s*(.*)", t)
        if m and m.group(1) in self.macros:
            args = split_args(m.group(2))
            body = []
            for line in self.macros[m.group(1)]:
                b = line.replace("\\#", ", ".join(args)).replace("_NARG", str(len(args)))
                for i, a in enumerate(args, 1):
                    b = b.replace("\\%d" % i, a.strip())
                b = re.sub(r"\\[1-9]", "", b)
                body.append(b)
            self.run(body)
            return
        self.unknown.add(t.split()[0])

    @staticmethod
    def count(args):
        return len([a for a in split_args(args) if a.strip()]) if args.strip() else 1

    def close_section(self):
        if self.cur is not None:
            while self.union:
                st = self.union.pop(); self.off = max(st[1], self.off)
            self.cur["size"] = max(self.cur["size"], self.off)


def split_args(s):
    out, depth, cur = [], 0, ""
    for ch in s:
        if ch == "," and depth == 0:
            out.append(cur); cur = ""
        else:
            depth += ch in "(["
            depth -= ch in ")]"
            cur += ch
    if cur.strip():
        out.append(cur)
    return out


def load_macros(files):
    macros = {}
    for f in files:
        lines = f.read_text(encoding="utf-8", errors="replace").splitlines()
        i = 0
        while i < len(lines):
            m = re.match(r"\s*MACRO\??\s+(\w+)", lines[i]) or re.match(r"\s*(\w+):\s*MACRO", lines[i])
            if m:
                body, i = [], i + 1
                while i < len(lines) and not re.match(r"\s*ENDM", lines[i]):
                    body.append(lines[i]); i += 1
                macros[m.group(1)] = body
            i += 1
    return macros


def include_order(root):
    files = []
    for top in ("macros.asm", "constants.asm", "includes.asm"):
        f = root / top
        if f.exists():
            for m in re.finditer(r'INCLUDE\s+"([^"]+)"', f.read_text(encoding="utf-8", errors="replace")):
                p = root / m.group(1)
                if p.exists() and p not in files:
                    files.append(p)
                    # one level of nested includes (macros.asm includes macros/*.asm)
                    for m2 in re.finditer(r'INCLUDE\s+"([^"]+)"', p.read_text(encoding="utf-8", errors="replace")):
                        q = root / m2.group(1)
                        if q.exists() and q not in files:
                            files.append(q)
    if not files:
        files = sorted((root / "macros").glob("*.asm")) + [root / "constants" / "hardware.inc"] + sorted((root / "constants").glob("*.asm"))
    return [f for f in files if f.suffix in (".asm", ".inc") and not str(f.relative_to(root)).replace("\\", "/").startswith("ram/")]


# ------------------------------------------------------------------- link
def place(root, sections):
    base, region, addr = {}, None, 0
    for raw in (root / "layout.link").read_text(encoding="utf-8").splitlines():
        t = raw.split(";")[0].strip()
        if not t:
            continue
        m = re.match(r"(WRAM0|WRAMX|ROM0|ROMX|VRAM|SRAM|HRAM|OAM)\b\s*(.*)", t)
        if m:
            region = m.group(1)
            if region == "WRAM0":
                addr = 0xC000
            elif region == "WRAMX":
                addr = 0xD000
            continue
        if region not in ("WRAM0", "WRAMX"):
            continue
        m = re.match(r"align\s+(\d+)", t, re.I)
        if m:
            a = 1 << int(m.group(1)); addr = (addr + a - 1) // a * a; continue
        m = re.match(r"org\s+\$([0-9A-Fa-f]+)", t, re.I)
        if m:
            addr = int(m.group(1), 16); continue
        m = re.match(r"\"([^\"]+)\"", t)
        if m and m.group(1) in sections:
            base[m.group(1)] = addr
            addr += sections[m.group(1)]["size"]
    return base


def layout(root, defines=()):
    root = Path(root).expanduser()
    c = Consts()
    for d in defines:
        c.set(d, 1)
    files = include_order(root)
    # macros/ram.asm (the struct macros) is pulled in by ram.asm, not includes.asm
    macros = load_macros(files + [f for f in sorted((root / "macros").rglob("*.asm")) if f not in files])
    asm = Asm(c, macros)
    for f in files:
        asm.run(f.read_text(encoding="utf-8", errors="replace").splitlines())
    asm.sections.clear(); asm.cur = None
    asm.run((root / "ram" / "wram.asm").read_text(encoding="utf-8", errors="replace").splitlines())
    asm.close_section()
    base = place(root, asm.sections)
    addrs = {}
    for name, sec in asm.sections.items():
        if name in base:
            for label, off in sec["labels"].items():
                addrs[label] = base[name] + off
    if asm.missing:
        print("MISSING CONSTANTS:", sorted(asm.missing), file=sys.stderr)
    unknown = {u for u in asm.unknown if not u.startswith(("MACRO", "ENDM"))}
    if unknown:
        print("UNKNOWN DIRECTIVES:", sorted(unknown), file=sys.stderr)
    return addrs, base


# GbcTracker's Crystal constants, proven against the Gen 2 reference tracker
# (bank-1 offsets there, +0xC000 here).
CRYSTAL_KNOWN = {
    "wPartyCount": 0xDCD7, "wPartySpecies": 0xDCD8, "wPartyMon1": 0xDCDF, "wEnemyMon": 0xD206,
    # The reference's eMove 0xC608 is wEnemyMoveStruct (its first byte is the
    # move id), not wCurEnemyMove (0xC6E4); GbcTracker's comment names the wrong label.
    "wBattleMode": 0xD22D, "wEnemyMoveStruct": 0xC608, "wJohtoBadges": 0xD857, "wKantoBadges": 0xD858,
    "wNumItems": 0xD892, "wItems": 0xD893,
}

if __name__ == "__main__":
    args = sys.argv[1:]
    check = "--check" in args
    defines = [a[2:] for a in args if a.startswith("-D")]
    args = [a for a in args if a != "--check" and not a.startswith("-D")]
    addrs, base = layout(args[0], defines)
    if check:
        bad = 0
        for k, v in CRYSTAL_KNOWN.items():
            got = addrs.get(k)
            bad += got != v
            print("%-16s expected %04X got %s %s" % (k, v, ("%04X" % got) if got is not None else "-", "ok" if got == v else "MISMATCH"))
        sys.exit(1 if bad else 0)
    for k in args[1:] or sorted(addrs):
        print("%-28s %04X" % (k, addrs[k]) if k in addrs else "%-28s ?" % k)
