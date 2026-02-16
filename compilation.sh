#!/bin/bash
set -euo pipefail

# --- VÉRIFICATION DES PRÉREQUIS ---
for cmd in javac curl jar; do
  if ! command -v $cmd &> /dev/null; then
    echo "Erreur : $cmd n'est pas installé."
    exit 1
  fi
done

BASE="$(cd "$(dirname "$0")" && pwd)"
SRC="$BASE/src"
CLASSES="$BASE/classes"
LIB="$BASE/lib"

mkdir -p "$CLASSES" "$LIB"

# Téléchargement de GSON
GSON_JAR="$LIB/gson-2.10.1.jar"
if [ ! -f "$GSON_JAR" ]; then
    echo "Téléchargement de GSON..."
    curl -L -s -o "$GSON_JAR" "https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar"
fi

# Nettoyage
find "$CLASSES" -type f -name '*.class' -delete
rm -f "$LIB/interfaces.jar" "$LIB/server.jar" "$LIB/client.jar" "$LIB/common.jar" || true

# Compilation
echo "Compilation..."
# Utilisation de find compatible macOS et Linux
javac -d "$CLASSES" -cp "$GSON_JAR" $(find "$SRC" -name "*.java")

# Création des JARs
cd "$CLASSES"
jar --create --file "$LIB/common.jar" common/*.class
jar --create --file "$LIB/interfaces.jar" interfaces/**/*.class 2>/dev/null || jar --create --file "$LIB/interfaces.jar" interfaces/server/*.class interfaces/client/*.class
jar --create --file "$LIB/server.jar" server/*.class
jar --create --file "$LIB/client.jar" client/*.class
cd "$BASE"

# Préparation du Classpath final
# On utilise un format qui supporte Linux/Mac
CP="$CLASSES:$GSON_JAR:$LIB/common.jar:$LIB/interfaces.jar:$LIB/server.jar:$LIB/client.jar"

# Redémarrage du rmiregistry
echo "Redémarrage de rmiregistry sur le port 6090..."
pkill -f "rmiregistry 6090" || true
sleep 1

# ASTUCE RMI : On lance le registre depuis le dossier classes pour qu'il trouve les stubs
cd "$CLASSES"
CLASSPATH="$CP" nohup rmiregistry 6090 > /dev/null 2>&1 &
cd "$BASE"

echo "--------------------------------------------------"
echo "Build complete."
echo "Pour lancer le serveur : java -cp \"$CP\" server.HelloServer 6090"
echo "Pour lancer le client  : java -cp \"$CP\" client.HelloClient2 localhost 6090 Lisa 0"
echo "--------------------------------------------------"