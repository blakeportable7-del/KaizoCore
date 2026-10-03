"""Check engine-maxdex against Trip's MaxDex-Randomizer.jar by bytecode, without running the jar.

    python tools/maxdex/compare_jar.py <MaxDex-Randomizer.jar> <randomizer-1.1.3.jar> <upstream clone at d53e0824>

Run from the repo root with a JDK on PATH (javac, javap). It:

1. reads both jars as zip files into a temporary folder (nothing in them is executed);
2. compiles the upstream tree and engine-maxdex/src with javac --release 8 (the jars are Java 8 bytecode);
3. compares every class method by method after normalising what a compiler or a decompile round trip changes
   on its own: local slots, branch layout and inversions, gotos, dup/pop, checkcasts, exception tables,
   switch jump offsets, synthetic accessor numbers, ldc versus ldc_w, List versus ArrayList call sites, and
   the package rename (pkrandommd and md-prefixed siblings);
4. sorts each method that differs between Trip's jar and the port: SAME AS BASELINE when the difference is
   hunk for hunk the one between the official 1.1.3 jar and the plain upstream build (the two compilers);
   otherwise LOOK, which is printed in full to be read by eye.

His Gen3RomHandler was decompiled and recompiled, so the LOOK list is never empty. On 2026-10-02 it held eleven
methods, all read by eye and listed in engine-maxdex/PINNED.txt: RandomSource.reset (KaizoCore's own change), a
synthetic constructor his decompile added, and nine rewrites of code neither side changed (a ternary for an
if/else, hoisted temporaries, a dropped catch-and-rethrow, string-switch order). Anything else is a difference
between the port and his jar.
"""
import concurrent.futures
import difflib
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

SIBS = ['compressors', 'cuecompressors', 'launcher', 'pptxt', 'thenewpoketext']
DROP = re.compile(r'^(goto(_w)?|[ailfd](load|store)(_\d| \d+)?|dup\w*|pop2?|checkcast|nop|swap|'
                  r'if\w*|tableswitch|lookupswitch|iinc|monitor\w+|jsr|ret|i2b|i2c|i2s)\b')
SIB_RE = re.compile(r'md(' + '|'.join(SIBS) + r')(?=[/.$;,()<> ]|$)')


def norm(ins):
    s = re.sub(r'\s+', ' ', ins).strip()
    s = s.replace('pkrandommd', 'pkrandom')
    s = SIB_RE.sub(lambda m: m.group(1), s)
    s = re.sub(r'#\d+(,\s*\d+)?', '', s).strip()
    s = s.replace('ldc_w', 'ldc').replace('ldc2_w', 'ldc')
    s = re.sub(r'access\$\d+', 'access$N', s)
    s = re.sub(r'lambda\$(\w+)\$\d+', r'lambda$\1$N', s)
    for kind, iface in (('ArrayList', 'List'), ('HashMap', 'Map'), ('TreeMap', 'Map'), ('HashSet', 'Set'), ('TreeSet', 'Set')):
        s = s.replace('java/util/%s.' % kind, 'java/util/%s.' % iface)
    s = re.sub(r'^invoke(virtual|interface)\s', 'invoke ', s)
    s = re.sub(r'//\s*(Interface)?Method\s+', '', s)
    s = re.sub(r'//\s*Field\s+', '', s)
    s = re.sub(r'//\s*(class|String|int|long|float|double)\s+', r'\1 ', s)
    m = re.match(r'^iconst_(m1|\d)$', s)
    if m:
        return 'int ' + ('-1' if m.group(1) == 'm1' else m.group(1))
    m = re.match(r'^(bipush|sipush) (-?\d+)$', s) or re.match(r'^ldc int (-?\d+)$', s)
    if m:
        return 'int ' + m.group(m.lastindex)
    return s


def methods(path):
    r = subprocess.run(['javap', '-c', '-p', '-constants', str(path)], capture_output=True, text=True,
                       encoding='utf-8', errors='replace')
    out, cur = {}, None
    for ln in r.stdout.splitlines():
        if re.match(r'^  \S', ln) and ln.rstrip().endswith(';') and '(' in ln:
            cur = re.sub(r'\s+', ' ', ln.strip()).replace('pkrandommd', 'pkrandom')
            cur = SIB_RE.sub(lambda m: m.group(1), cur)
            cur = re.sub(r'lambda\$(\w+)\$\d+', r'lambda$\1$N', cur)
            cur = re.sub(r'access\$\d+', 'access$N', cur)
            # A decompile round trip can change a member's visibility; the code is what counts.
            cur = re.sub(r'^(public |private |protected )', '', cur)
            out.setdefault(cur, [])
            continue
        if re.match(r'^  \S', ln):
            cur = None
            continue
        if cur is None:
            continue
        m = re.match(r'^\s*\d+:\s+(.*)$', ln)
        if not m or DROP.match(m.group(1)) or m.group(1).strip().lstrip('-').isdigit():
            continue
        out[cur].append(norm(m.group(1)))
    return out


def diffs(a, b):
    A, B = methods(a), methods(b)
    return {m: list(difflib.unified_diff(A.get(m, []), B.get(m, []), lineterm='', n=1))[2:]
            for m in set(A) | set(B) if A.get(m) != B.get(m)}


def counterpart(root, rel):
    parts = list(rel.parts)
    if parts[:3] == ['com', 'dabomstew', 'pkrandom']:
        parts[2] = 'pkrandommd'
    elif parts[0] in SIBS:
        parts[0] = 'md' + parts[0]
    return root.joinpath(*parts)


def compile_tree(src, out):
    files = [str(p) for p in pathlib.Path(src).rglob('*.java')]
    lst = out.parent / (out.name + '.txt')
    lst.write_text('\n'.join(files), encoding='utf-8')
    r = subprocess.run(['javac', '-encoding', 'UTF-8', '--release', '8', '-nowarn', '-Xlint:none', '-d', str(out), '@' + str(lst)],
                       capture_output=True, text=True)
    assert r.returncode == 0, r.stderr[-2000:]


def main():
    trip_jar, official_jar, upstream = map(pathlib.Path, sys.argv[1:4])
    port_src = pathlib.Path('engine-maxdex/src')
    assert port_src.is_dir(), 'run from the repo root'
    tmp = pathlib.Path(tempfile.mkdtemp(prefix='maxdex-cmp-'))
    try:
        trip, official, up, port = (tmp / n for n in ('trip', 'official', 'up', 'port'))
        for jar, dest in ((trip_jar, trip), (official_jar, official)):
            with zipfile.ZipFile(jar) as z:
                z.extractall(dest)
        # The official 1.1.3 jar was built before upstream bumped Version.java to 905: it reports 904, as
        # Trip's does. The baseline is built the same way, so the version constants javac inlines into
        # the GUI classes do not show up as differences.
        shutil.copytree(upstream / 'src', tmp / 'upsrc')
        version = tmp / 'upsrc/com/dabomstew/pkrandom/Version.java'
        v = version.read_bytes().decode('utf-8').replace('\r\n', '\n')
        for old, new in (('VERSION = 905;', 'VERSION = 904;'), ('"4.6.0-END113"', '"4.6.0-END112"'),
                         ('        map.put(904, "4.6.0-END112");\n', '')):
            assert v.count(old) == 1, old
            v = v.replace(old, new)
        version.write_bytes(v.encode('utf-8'))
        compile_tree(tmp / 'upsrc', up)
        compile_tree(port_src, port)
        classes = sorted(p.relative_to(trip) for p in trip.rglob('*.class') if (official / p.relative_to(trip)).exists()
                         and counterpart(port, p.relative_to(trip)).exists() and (up / p.relative_to(trip)).exists())
        changed_by_trip = [c for c in classes if (trip / c).read_bytes() != (official / c).read_bytes()]
        print('classes', len(classes), 'changed in his jar:', [str(c) for c in changed_by_trip])

        def check(rel):
            port_vs_trip = diffs(trip / rel, counterpart(port, rel))
            compiler = diffs(official / rel, up / rel)
            rows = []
            for m, h in sorted(port_vs_trip.items()):
                rows.append(('SAME AS BASELINE' if compiler.get(m) == h else 'LOOK', m, h))
            return rel, rows

        look = 0
        with concurrent.futures.ThreadPoolExecutor(8) as ex:
            for rel, rows in ex.map(check, classes):
                for verdict, m, h in rows:
                    if verdict == 'LOOK':
                        look += 1
                        print('LOOK', rel, m)
                        print('\n'.join('      ' + x for x in h))
        print('methods to read by eye:', look)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


if __name__ == '__main__':
    main()
