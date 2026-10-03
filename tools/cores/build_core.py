"""Build an emulator core from its pinned source commit and patches, the way it ships in the APK.

    python tools/cores/build_core.py <core>            build, check against the record, install
    python tools/cores/build_core.py <core> --write    build, install, and write the new hashes into the record
    python tools/cores/build_core.py <core> --check    build and compare with the record only; installs nothing
    ... --full-symbols <dir>                             also keep the full debug build (line numbers) in <dir>

The recipe and the record are one file, libretrodroid/cores/<core>/PINNED.txt (key: value lines, see the melonDS one).
The source goes to .vendor/src/<core> (git-ignored): a shallow fetch of exactly the pinned commit, checked out with
the upstream line endings, reset and cleaned on every run, so nothing left from an earlier build can leak into this one.
Then each patch in the record is applied (its sha256 checked first), the build command runs with the NDK the record
names, and for each ABI:

  - the stripped library (what ndk-build puts in libs/) goes to app/src/main/jniLibs/<abi>/<ship-as>, the file the APK
    carries;
  - the library before stripping (obj/local/) goes to libretrodroid/cores/<core>/symbols/<abi>/<ship-as> with its debug
    info taken out and its symbol table kept, so a crash in the core can be read back to function names; release.sh
    puts it in dist/KaizoCore-<version>-symbols/. The full debug build (line numbers, about 70 MB an ABI, too big to
    keep in git) is one run away: --check --full-symbols <dir>; the build is reproducible, so it has the same build id.

Both must have LOAD segments aligned to 16 KB (tools/check_apk_libs.py), and both carry the same build id. The source
directory is mapped to "." in the compiler's paths, so neither binary carries this PC's paths, and the build is
reproducible: two clean builds of the melonDS record gave the same bytes (2026-10-03), which is what lets --check
prove that the libraries in jniLibs are this source. Nothing here downloads a binary: the source is fetched, and only
the NDK's own tools run.

Made for the melonDS core in rc34; rc35 can add mGBA and Gambatte by writing their PINNED.txt the same way.
"""
import argparse
import hashlib
import os
import shlex
import shutil
import struct
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ABIS = ("arm64-v8a", "x86_64")


def run(cmd, cwd=None, **kw):
    print("+", " ".join(cmd) if isinstance(cmd, list) else cmd, flush=True)
    return subprocess.run(cmd, cwd=cwd, check=True, **kw)


def git(src, *args, capture=False):
    r = subprocess.run(["git", "-C", src, *args], check=True, capture_output=capture, text=True)
    return r.stdout.strip() if capture else None


def read_record(core):
    path = os.path.join(ROOT, "libretrodroid", "cores", core, "PINNED.txt")
    keys, order = {}, []
    with open(path, encoding="utf-8") as f:
        lines = f.read().splitlines()
    for line in lines:
        if not line.strip() or line.lstrip().startswith("#") or ":" not in line:
            continue
        k, v = line.split(":", 1)
        k, v = k.strip(), v.strip()
        if k == "patch":
            keys.setdefault("patch", []).append(v)
        else:
            keys[k] = v
        order.append(k)
    return path, lines, keys


def fields(value):
    """'a.patch sha256=ab' -> ('a.patch', {'sha256': 'ab'})"""
    parts = value.split()
    head = parts[0] if parts and "=" not in parts[0] else None
    return head, dict(p.split("=", 1) for p in parts if "=" in p)


def lf_bytes(path):
    """A text file's bytes with CRLF made LF: a Windows checkout of the repo has CRLF, git stores LF."""
    with open(path, "rb") as f:
        return f.read().replace(b"\r\n", b"\n")


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def elf_facts(data):
    """(lowest LOAD alignment, GNU build id) of a 64-bit little-endian ELF."""
    if data[:4] != b"\x7fELF" or data[4] != 2 or data[5] != 1:
        raise ValueError("not a 64-bit little-endian ELF")
    e_phoff, = struct.unpack_from("<Q", data, 0x20)
    e_phentsize, e_phnum = struct.unpack_from("<HH", data, 0x36)
    aligns, build_id = [], None
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        p_type, = struct.unpack_from("<I", data, off)
        p_offset, = struct.unpack_from("<Q", data, off + 0x08)
        p_filesz, = struct.unpack_from("<Q", data, off + 0x20)
        p_align, = struct.unpack_from("<Q", data, off + 0x30)
        if p_type == 1:          # PT_LOAD
            aligns.append(p_align)
        elif p_type == 4:        # PT_NOTE
            pos, end = p_offset, p_offset + p_filesz
            while pos + 12 <= end:
                namesz, descsz, ntype = struct.unpack_from("<III", data, pos)
                name = data[pos + 12:pos + 12 + namesz]
                desc_at = pos + 12 + ((namesz + 3) & ~3)
                if ntype == 3 and name.rstrip(b"\0") == b"GNU":   # NT_GNU_BUILD_ID
                    build_id = data[desc_at:desc_at + descsz].hex()
                pos = desc_at + ((descsz + 3) & ~3)
    return (min(aligns) if aligns else 0), build_id


def sdk_dir():
    props = os.path.join(ROOT, "local.properties")
    if os.path.isfile(props):
        for line in open(props, encoding="utf-8"):
            if line.startswith("sdk.dir="):
                return line.split("=", 1)[1].strip().replace("\\\\", "\\").replace("\\:", ":")
    return os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")


def fetch_source(src, repo, commit):
    """Exactly `commit`, with upstream line endings, reset and cleaned."""
    if not os.path.isdir(os.path.join(src, ".git")):
        os.makedirs(src, exist_ok=True)
        git(src, "init", "-q")
        git(src, "config", "core.autocrlf", "false")
        git(src, "remote", "add", "origin", repo)
    git(src, "config", "core.autocrlf", "false")
    have = subprocess.run(["git", "-C", src, "cat-file", "-e", commit + "^{commit}"], capture_output=True).returncode == 0
    if not have:
        git(src, "fetch", "-q", "--depth", "1", "origin", commit)
    git(src, "checkout", "-q", "--detach", commit)
    git(src, "reset", "-q", "--hard", commit)
    git(src, "clean", "-q", "-fdx")
    head = git(src, "rev-parse", "HEAD", capture=True)
    if head != commit:
        raise SystemExit(f"REFUSED: {src} is at {head}, the record pins {commit}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("core")
    mode = ap.add_mutually_exclusive_group()
    mode.add_argument("--write", action="store_true", help="install and write the new hashes into PINNED.txt")
    mode.add_argument("--check", action="store_true", help="build and compare with the record; install nothing")
    ap.add_argument("--ndk", help="the NDK to build with (default: the version the record names, under sdk.dir)")
    ap.add_argument("--full-symbols", metavar="DIR", help="also copy the full debug build (DWARF, about 70 MB an ABI) here")
    ap.add_argument("-j", type=int, default=os.cpu_count() or 4)
    args = ap.parse_args()

    record_path, lines, rec = read_record(args.core)
    core_dir = os.path.dirname(record_path)
    commit, repo = rec["commit"], rec["repo"]
    ndk = args.ndk or os.path.join(sdk_dir() or "", "ndk", rec["ndk"])
    ndk_build = os.path.join(ndk, "ndk-build.cmd" if os.name == "nt" else "ndk-build")
    if not os.path.isfile(ndk_build):
        raise SystemExit(f"REFUSED: no ndk-build at {ndk_build} (the record builds with NDK {rec['ndk']})")

    src = os.path.join(ROOT, ".vendor", "src", args.core)
    fetch_source(src, repo, commit)

    for p in rec.get("patch", []):
        name, f = fields(p)
        data = lf_bytes(os.path.join(core_dir, name))
        if f.get("sha256") and f["sha256"] != sha256(data) and not args.write:
            raise SystemExit(f"REFUSED: {name} is not the patch the record pins (sha256 {sha256(data)})")
        run(["git", "-C", src, "apply", "--whitespace=nowarn", "-"], input=data)

    # The build command once per ABI (an APP_ABI list with a space in it does not survive ndk-build.cmd's quoting),
    # with the source directory mapped to "." so no path of this PC is compiled in.
    mapped = "-ffile-prefix-map=" + src.replace("\\", "/") + "=."
    for abi in ABIS:
        cmd = [ndk_build] + shlex.split(rec["build"]) + [f"APP_ABI={abi}", f"APP_CFLAGS={mapped}",
                                                         f"APP_ASFLAGS={mapped}", f"-j{args.j}"]
        run(cmd, cwd=src)

    module, ship_as = rec["module"], rec["ship-as"]
    strip = os.path.join(ndk, "toolchains", "llvm", "prebuilt",
                         "windows-x86_64" if os.name == "nt" else "linux-x86_64", "bin",
                         "llvm-strip" + (".exe" if os.name == "nt" else ""))
    results, bad = {}, []
    for abi in ABIS:
        stripped = os.path.join(src, "libs", abi, module)
        full = os.path.join(src, "obj", "local", abi, module)
        symbols = os.path.join(src, "obj", "local", abi, "symtab-" + module)
        run([strip, "--strip-debug", "-o", symbols, full])
        sdata, ydata = open(stripped, "rb").read(), open(symbols, "rb").read()
        salign, sid = elf_facts(sdata)
        yalign, yid = elf_facts(ydata)
        if min(salign, yalign) < 16384:
            bad.append(f"{abi}: LOAD aligned to {min(salign, yalign)}, a 16 KB page phone cannot load it")
        if sid != yid:
            bad.append(f"{abi}: the shipped and the symbols build ids differ ({sid}, {yid})")
        results[abi] = {"sha256": sha256(sdata), "build-id": sid, "symbols-sha256": sha256(ydata),
                        "size": len(sdata), "symbols-size": len(ydata), "stripped": stripped, "symbols": symbols}
        print(f"{abi}: {len(sdata)} bytes sha256={sha256(sdata)} build-id={sid} align={salign}; "
              f"symbols {len(ydata)} bytes sha256={sha256(ydata)}")
    for b in bad:
        print("FAIL", b)
    if bad:
        return 1

    mismatched = []
    for abi in ABIS:
        _, want = fields(rec.get(abi, ""))
        for k in ("sha256", "build-id", "symbols-sha256"):
            if want.get(k) != results[abi][k]:
                mismatched.append(f"{abi} {k}: record {want.get(k)}, built {results[abi][k]}")
    if args.full_symbols:
        for abi in ABIS:
            dest = os.path.join(args.full_symbols, abi, ship_as)
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            shutil.copyfile(os.path.join(src, "obj", "local", abi, module), dest)
            print(f"full debug build: {dest}")
    if args.check:
        for m in mismatched:
            print("DIFFERS", m)
        print("matches the record" if not mismatched else "does NOT match the record")
        return 1 if mismatched else 0
    if mismatched and not args.write:
        for m in mismatched:
            print("DIFFERS", m)
        raise SystemExit("REFUSED: the build differs from the record; run with --write to take it on purpose")

    for abi in ABIS:
        dest = os.path.join(ROOT, "app", "src", "main", "jniLibs", abi, ship_as)
        sym_dest = os.path.join(core_dir, "symbols", abi, ship_as)
        os.makedirs(os.path.dirname(sym_dest), exist_ok=True)
        shutil.copyfile(results[abi]["stripped"], dest)
        shutil.copyfile(results[abi]["symbols"], sym_dest)
        print(f"installed {dest} and {sym_dest}")

    if args.write:
        out = []
        for line in lines:
            k = line.split(":", 1)[0].strip() if ":" in line else None
            if k in ABIS:
                r = results[k]
                line = f"{k}: sha256={r['sha256']} build-id={r['build-id']} symbols-sha256={r['symbols-sha256']}"
            elif k == "patch":
                name, _ = fields(line.split(":", 1)[1])
                line = f"patch: {name} sha256={sha256(lf_bytes(os.path.join(core_dir, name)))}"
            out.append(line)
        with open(record_path, "w", encoding="utf-8", newline="\n") as f:
            f.write("\n".join(out) + "\n")
        print("wrote", record_path)
    return 0


if __name__ == "__main__":
    sys.exit(main())
