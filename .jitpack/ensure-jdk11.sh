#!/usr/bin/env bash
# JitPack's latest image still exports JAVA_HOME=/usr/lib/jvm/jdk-11, but that
# directory is no longer on the image. Gradle 6.7 refuses to start until the
# path contains a JDK 11. This project cannot move to the image JDK (17):
# Gradle 6.7.1 and Android Gradle Plugin 4.1.2 run on JDK 11.
set -euo pipefail

if [ -z "${JAVA_HOME:-}" ] || [ -x "${JAVA_HOME}/bin/java" ]; then
  exit 0
fi

parent="$(dirname "$JAVA_HOME")"
mkdir -p "$parent"
workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT

curl -fsSL -o "$workdir/jdk11.tar.gz" \
  "https://api.adoptium.net/v3/binary/latest/11/ga/linux/x64/jdk/hotspot/normal/eclipse?project=jdk"
tar -xzf "$workdir/jdk11.tar.gz" -C "$workdir"

extracted=""
for dir in "$workdir"/jdk-*; do
  if [ -d "$dir" ] && [ -x "$dir/bin/java" ]; then
    extracted="$dir"
    break
  fi
done

if [ -z "$extracted" ]; then
  echo "Temurin 11 archive did not contain a JDK." >&2
  exit 1
fi

rm -rf "$JAVA_HOME"
mv "$extracted" "$JAVA_HOME"
"$JAVA_HOME/bin/java" -version
