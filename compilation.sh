# ...existing code...
#!/bin/bash
set -euo pipefail

BASE="$(cd "$(dirname "$0")" && pwd)"
SRC="$BASE/src"
CLASSES="$BASE/classes"
LIB="$BASE/lib"

mkdir -p "$CLASSES" "$LIB"

# clean previous build artifacts
rm -f "$CLASSES"/*.class "$LIB"/*.jar || true

# compile all sources
javac -d "$CLASSES" -classpath "$CLASSES":lib/* "$SRC"/*.java

# create jars (only include files that exist)
cd "$CLASSES"
jar --create --file "$LIB/Hello.jar"  Hello.class Info_itf.class Registry_itf.class Accounting_itf.class 2>/dev/null || true
jar --create --file "$LIB/HelloImpl.jar" HelloImpl.class Hello2Impl.class 2>/dev/null || true
jar --create --file "$LIB/Hello2.jar" Hello2.class Hello2Impl.class 2>/dev/null || true
cd "$BASE"

# export classpath for running servers/clients
export CLASSPATH="$CLASSES:$LIB/*${CLASSPATH:+:$CLASSPATH}"

# restart rmiregistry on port 6090
pkill -f "rmiregistry 6090" || true
sleep 1
# start rmiregistry with project classpath
CLASSPATH="$CLASSES:$LIB/*" nohup rmiregistry 6090 >/dev/null 2>&1 &

echo "Build complete."
# ...existing code...