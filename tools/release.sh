#!/usr/bin/env bash
# Build the sideload APK for a release and write its checksum beside it.
#
#   tools/release.sh            -> dist/KaizoCore-<versionName>.apk + .sha256 + RELEASE-NOTES.md stub
#
# Runs the full unit suite first and refuses to build on a red one. Does not
# tag, push or upload anything: the GitHub release is a deliberate act on the
# repo page, with the .apk and the .sha256 attached and the notes pasted in.
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION=$(grep -o 'baseVersion = "[^"]*"' app/build.gradle* | head -1 | sed 's/.*"\(.*\)"/\1/')
BUILD_ID=$(git rev-parse --short=8 HEAD 2>/dev/null || echo local)
[ -n "$VERSION" ] || { echo "versionName not found"; exit 1; }
OUT=dist; mkdir -p "$OUT"
APK="$OUT/KaizoCore-$VERSION.apk"

echo "== tests"
./gradlew test --console=plain -q -PbuildId=$BUILD_ID
echo "== build $VERSION+$BUILD_ID"
./gradlew :app:assembleRelease --console=plain -q -PbuildId=$BUILD_ID
cp app/build/outputs/apk/release/app-release.apk "$APK"
( cd "$OUT" && sha256sum "$(basename "$APK")" > "$(basename "$APK").sha256" )
echo "== $APK"
cat "$APK.sha256"
[ -f "$OUT/RELEASE-NOTES.md" ] || cat > "$OUT/RELEASE-NOTES.md" <<EOF
# KaizoCore $VERSION (beta)

Install: download the .apk, allow installs from your browser or file manager when Android asks, open it.
Verify: the SHA-256 in the .sha256 file must match \`sha256sum KaizoCore-$VERSION.apk\`.
You need your own game dumps. Nothing here contains, links or fetches a ROM.

## Changed

-

## Known issues

-
EOF
echo "== notes stub: $OUT/RELEASE-NOTES.md (fill in Changed / Known issues before attaching)"
