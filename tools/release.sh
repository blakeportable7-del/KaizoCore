#!/usr/bin/env bash
# Build the sideload APK for a release and keep what a crash report from it needs.
#
#   tools/release.sh [--respin]  -> dist/KaizoCore-<versionName>.apk, its .sha256, -mapping.txt.gz and -symbols/,
#                                   and a RELEASE-NOTES.md stub when dist has none
#
# docs/RELEASING.md is the whole checklist; this script is its BUILD step. It refuses a dirty tree, a versionCode that
# did not move in the release commit (a phone on a test build of that code would never be offered the release) and an
# APK that would replace a published one; --respin ships the same code again, on purpose. It runs the full unit suite
# with no build cache, the ROM and RAM-dump tests on the real dumps, and counts the results itself, builds under the
# shared Gradle lock, signs with the release key once it is pinned (Blake types its password when apksigner asks),
# checks the signers and the lineage, checks every native library is 16 KB aligned and liblibretrodroid.so stripped,
# and keeps the R8 mapping and the unstripped libraries (rc32 audit P2 #6, #7, #8, P3 #11, #12, #104, #105). Does not
# tag, push or upload anything: the GitHub release is a deliberate act on the repo page, with the .apk and the .sha256
# attached and the notes pasted in.
#
# Publish it as the LATEST release, never a pre-release (the command is printed at the end). The README's download badge
# and the repo page's Releases card both follow "latest"; while every release was a pre-release they had none, so the
# badge fell through to the long list with each APK folded away under its notes, and a visitor saw no download at all
# (Blake, 2026-10-01). The notes open with the download link for the same reason.
set -euo pipefail
cd "$(dirname "$0")/.."

RESPIN=""
[ "${1:-}" = "--respin" ] && RESPIN="--respin"

VERSION=$(grep -o 'baseVersion = "[^"]*"' app/build.gradle* | head -1 | sed 's/.*"\(.*\)"/\1/')
BUILD_ID=$(git rev-parse --short=8 HEAD 2>/dev/null || echo local)
[ -n "$VERSION" ] || { echo "versionName not found"; exit 1; }
OUT=dist; mkdir -p "$OUT"
APK="$OUT/KaizoCore-$VERSION.apk"

# Every installed KaizoCore trusts one certificate: the debug key in ~/.android/debug.keystore (rc33 audit P1 #3).
# If that file goes missing, Gradle makes a new key without a word (this script builds with -q), and an APK signed by
# any other key cannot update a single phone; the only way round is an uninstall, which deletes the player's games,
# saves and notes. Once Blake's release key is pinned in tools/release-certs.txt (rc32 audit P2 #7, #8), the build comes
# out unsigned and tools/sign_release.py signs it with both keys after the build, apksigner asking for the release key's
# password on this terminal; docs/RELEASING.md, "The release key". So the keys, the lineage and a console for that
# password are checked before the test run, and the APK's signers and lineage before it reaches dist/.
python tools/release_checks.py key || exit 1

# The real games and DS RAM dumps the ROM tests read where they lie (rc32 audit P3 #105). Without them about 25 test
# classes returned early and counted as passes, so the gate never ran run codes, the pinned builds, Play as your Pokemon's
# addresses or the DS pointer on real data. IRONMON_REQUIRE_DUMPS turns each of those returns into a failure, and
# release_checks.py tests refuses a run it did not reach.
export IRONMON_ROMS="${IRONMON_ROMS:-C:/Users/bepor/IronMonOne/.vendor/roms}"
export IRONMON_DUMPS="${IRONMON_DUMPS:-C:/Users/bepor/IronMonOne/.vendor/dumps}"
export IRONMON_REQUIRE_DUMPS=1
for d in "$IRONMON_ROMS" "$IRONMON_DUMPS"; do
  [ -d "$d" ] && [ -n "$(ls -A "$d" 2>/dev/null)" ] \
    || { echo "REFUSED: $d is missing or empty: the release gate runs the ROM and RAM-dump tests on it"; exit 1; }
done
echo "== dumps: $IRONMON_ROMS, $IRONMON_DUMPS"

python tools/release_checks.py pre "$VERSION" $RESPIN

# The shared Gradle lock (several workers build on this PC): taken here, freed on exit only if this script took it.
LOCK=C:/Users/bepor/rogue-gradle.lock
GOT_LOCK=0
release_lock() { if [ "$GOT_LOCK" = 1 ]; then rm -f "$LOCK/owner"; rmdir "$LOCK" 2>/dev/null || true; fi; }
trap release_lock EXIT
for i in $(seq 1 720); do
  if mkdir "$LOCK" 2>/dev/null; then echo "tools/release.sh $VERSION" > "$LOCK/owner"; GOT_LOCK=1; break; fi
  sleep 5
done
[ "$GOT_LOCK" = 1 ] || { echo "REFUSED: the Gradle lock at $LOCK stayed busy for an hour"; exit 1; }

echo "== tests, uncached: a test that comes back from the cache proves nothing about this tree"
# cleanTest does not reach the app's two unit-test variants (its test task only runs them), so they are cleaned by name:
# a variant left up to date from an earlier run, the dumps unset, would be counted with its early returns (P3 #105).
./gradlew --no-build-cache cleanTest :app:cleanTestDebugUnitTest :app:cleanTestReleaseUnitTest test --console=plain -q -PbuildId=$BUILD_ID || true
python tools/release_checks.py tests
echo "== build $VERSION+$BUILD_ID"
OUTPUTS=app/build/outputs/apk/release
BUILT=$OUTPUTS/app-release.apk
# Either name may be left from an earlier build signed the other way; neither may be taken for this build's.
rm -f "$BUILT" "$OUTPUTS/app-release-unsigned.apk"
./gradlew :app:assembleRelease --console=plain -q -PbuildId=$BUILD_ID
[ "$(git rev-parse --short=8 HEAD)" = "$BUILD_ID" ] || { echo "REFUSED: HEAD moved during the build; the APK says $BUILD_ID"; exit 1; }
python tools/release_checks.py clean
# Gradle is done. The lock goes back now, not at exit: the signing step can wait a long time for Blake's password.
release_lock; GOT_LOCK=0

# Gradle signs with the debug key until the release key is pinned; from then on the APK comes out unsigned, for Blake.
if [ -f "$OUTPUTS/app-release-unsigned.apk" ]; then
  python tools/sign_release.py "$OUTPUTS/app-release-unsigned.apk" "$BUILT" || { echo "REFUSED: the release APK was not signed"; exit 1; }
fi
[ -f "$BUILT" ] || { echo "REFUSED: Gradle wrote no release APK"; exit 1; }
python tools/release_checks.py signing "$BUILT" || exit 1
python tools/check_apk_libs.py "$BUILT" || { echo "REFUSED: a native library above would fail on a phone"; exit 1; }
cp "$BUILT" "$APK"
( cd "$OUT" && sha256sum "$(basename "$APK")" > "$(basename "$APK").sha256" )

# What reading a crash report from this build needs: R8's mapping for the Kotlin stack, and the libraries before
# stripping for a native one (rc32 audit P3 #12). Checked against the APK's own map id, so it is this build's.
gzip -c app/build/outputs/mapping/release/mapping.txt > "$OUT/KaizoCore-$VERSION-mapping.txt.gz"
python tools/release_checks.py mapping "$APK" "$OUT/KaizoCore-$VERSION-mapping.txt.gz"
SYMS=app/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib
[ -d "$SYMS" ] || { echo "REFUSED: no unstripped native libraries at $SYMS"; exit 1; }
rm -rf "$OUT/KaizoCore-$VERSION-symbols"
mkdir -p "$OUT/KaizoCore-$VERSION-symbols"
for abi in arm64-v8a x86_64; do cp -r "$SYMS/$abi" "$OUT/KaizoCore-$VERSION-symbols/"; done
# A core built here (tools/cores/build_core.py) ships stripped; its symbol table is kept beside its record, with the same
# build id, and goes in instead of the stripped copy above (the melonDS core since rc34).
for sym in libretrodroid/cores/*/symbols/*/*.so; do
  [ -f "$sym" ] || continue
  abi=$(basename "$(dirname "$sym")")
  cp "$sym" "$OUT/KaizoCore-$VERSION-symbols/$abi/"
done
echo "== $APK"
cat "$APK.sha256"
echo "== kept: $OUT/KaizoCore-$VERSION-mapping.txt.gz and $OUT/KaizoCore-$VERSION-symbols/ (never attach them)"

REPO=blakeportable7-del/KaizoCore
DL="https://github.com/$REPO/releases/download/v$VERSION"
# In MiB, the unit GitHub's own asset list shows.
SIZE_MB=$(( $(stat -c %s "$APK") / 1048576 ))
if [ ! -f "$OUT/RELEASE-NOTES.md" ]; then
cat > "$OUT/RELEASE-NOTES.md" <<EOF
# KaizoCore $VERSION (beta)

### [Download KaizoCore-$VERSION.apk]($DL/KaizoCore-$VERSION.apk) ($SIZE_MB MB)

Or get it from [willowcreek.group/kaizocore](https://willowcreek.group/kaizocore). Checksum: [KaizoCore-$VERSION.apk.sha256]($DL/KaizoCore-$VERSION.apk.sha256)

Install: download the .apk, allow installs from your browser or file manager when Android asks, open it.
Verify: the SHA-256 in the .sha256 file must match \`sha256sum KaizoCore-$VERSION.apk\`.
You need your own game dumps. Nothing here contains, links or fetches a ROM.

## Changed

-

## Known issues

-
EOF
echo "== notes stub: $OUT/RELEASE-NOTES.md (fill in Changed / Known issues before attaching)"
fi
echo "== publish, on Blake's go (--latest, and no --prerelease: see the top of this file):"
echo "gh release create v$VERSION \"$APK\" \"$APK.sha256\" --repo $REPO --title \"KaizoCore $VERSION (beta)\" --notes-file \"$OUT/RELEASE-NOTES.md\" --latest"
