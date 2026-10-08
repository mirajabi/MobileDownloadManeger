#!/usr/bin/env bash
# JitPack still exports JAVA_HOME=/usr/lib/jvm/jdk-11. That directory is not on
# the image, and the build user cannot create it (mv: Permission denied).
# Gradle 6.7.1 and Android Gradle Plugin 4.1.2 need JDK 11. The image JDK is
# 17, which those tools refuse. Install Temurin 11 under $HOME. gradlew switches
# to it when the exported JAVA_HOME has no java binary. Exports from this
# script do not reach JitPack's later gradle commands, so the wrapper has to
# look at this path itself.
set -euo pipefail

dest="${HOME}/.jitpack-jdk11"

if [ -d /build ]; then
  git config --global --add safe.directory /build || true
fi

if [ -x "$dest/bin/java" ]; then
  "$dest/bin/java" -version
  exit 0
fi

if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
  exit 0
fi

if [ "$(uname -s)" != "Linux" ]; then
  echo "Temurin 11 is installed only on the JitPack Linux image." >&2
  exit 0
fi

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

rm -rf "$dest"
mv "$extracted" "$dest"
"$dest/bin/java" -version
