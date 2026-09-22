#!/bin/bash
set -x
export JAVA_HOME=/opt/jdk
SDK=/opt/android-sdk
mkdir -p "$SDK" /tmp/dl
ARCH=$(uname -m)   # aarch64

echo "=== [1/4] JDK 17 for $ARCH ==="
if [ ! -x "$JAVA_HOME/bin/java" ] || ! "$JAVA_HOME/bin/java" -version 2>/dev/null; then
  rm -rf /opt/jdk; mkdir -p /opt/jdk; cd /tmp/dl
  curl -L --retry 3 --retry-delay 5 -o jdk.tar.gz \
    "https://api.adoptium.net/v3/binary/latest/17/ga/linux/aarch64/jdk/hotspot/normal/eclipse"
  tar xzf jdk.tar.gz -C /opt/jdk --strip-components=1
fi
"$JAVA_HOME/bin/java" -version

echo "=== [2/4] cmdline-tools (arch-independent) ==="
export PATH="$JAVA_HOME/bin:$PATH"
"$SDK/cmdline-tools/latest/bin/sdkmanager" --version || echo "sdkmanager broken"

echo "=== [3/4] licenses ==="
yes 2>/dev/null | timeout 300 "$SDK/cmdline-tools/latest/bin/sdkmanager" \
  --sdk_root="$SDK" --licenses > /tmp/dl/lic.log 2>&1
tail -3 /tmp/dl/lic.log

echo "=== [4/4] platform + build-tools ==="
timeout 1800 "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" \
  "platform-tools" "platforms;android-34" "build-tools;34.0.0" > /tmp/dl/sdk.log 2>&1
echo "sdkmanager exit=$?"
tail -8 /tmp/dl/sdk.log
echo "--- installed ---"
ls "$SDK"
echo "--- aapt2 present? ---"
find "$SDK/build-tools" -name 'aapt2*' -o -name 'd8' 2>/dev/null | head
file "$SDK"/build-tools/34.0.0/aapt2 2>/dev/null
echo "=== DONE ==="
