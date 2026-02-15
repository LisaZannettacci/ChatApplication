#!/bin/bash
set -euo pipefail

BASE="$(cd "$(dirname "$0")" && pwd)"
SRC="$BASE/src"
CLASSES="$BASE/classes"
LIB="$BASE/lib"

mkdir -p "$CLASSES" "$LIB"

# Clean previous build artifacts.
find "$CLASSES" -type f -name '*.class' -delete
rm -f "$LIB"/*.jar || true

# Compile every Java source recursively.
javac -d "$CLASSES" -classpath "$CLASSES:$LIB/*" $(find "$SRC" -type f -name '*.java' | sort)

# Create jars by concern.
cd "$CLASSES"
jar --create --file "$LIB/interfaces.jar" interfaces/client/*.class interfaces/server/*.class 2>/dev/null || true
jar --create --file "$LIB/server.jar" server/*.class 2>/dev/null || true
jar --create --file "$LIB/client.jar" client/*.class 2>/dev/null || true
cd "$BASE"

# Export classpath for running servers/clients.
export CLASSPATH="$CLASSES:$LIB/*${CLASSPATH:+:$CLASSPATH}"

# Restart rmiregistry on port 6090.
pkill -f "rmiregistry 6090" || true
sleep 1
CLASSPATH="$CLASSES:$LIB/*" nohup rmiregistry 6090 >/dev/null 2>&1 &

echo "Build complete."
