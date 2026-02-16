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

# Téléchargement de GSON (persistance JSON)
GSON_JAR="$LIB/gson-2.10.1.jar"
if [ ! -f "$GSON_JAR" ]; then
    echo "Téléchargement de GSON..."
    curl -L -s -o "$GSON_JAR" "https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar"
fi

# Nettoyage des anciens artefacts
find "$CLASSES" -type f -name '*.class' -delete
rm -f "$LIB/interfaces.jar" "$LIB/server.jar" "$LIB/client.jar" "$LIB/common.jar" "$LIB/ihm.jar" || true

# Compilation
echo "Compilation de tous les packages (common, interfaces, server, client, ihm)..."
# javac compile tout ce qu'il trouve dans src, incluant le nouveau dossier ihm
javac -d "$CLASSES" -cp "$GSON_JAR" $(find "$SRC" -name "*.java")

# Création des JARs par préoccupation
echo "Création des archives JAR..."
cd "$CLASSES"

# JAR pour les messages (partagé)
jar --create --file "$LIB/common.jar" common/*.class

# JAR pour les interfaces RMI
jar --create --file "$LIB/interfaces.jar" interfaces/**/*.class 2>/dev/null || jar --create --file "$LIB/interfaces.jar" interfaces/server/*.class interfaces/client/*.class

# JAR pour la logique serveur
jar --create --file "$LIB/server.jar" server/*.class

# JAR pour la logique client
jar --create --file "$LIB/client.jar" client/*.class

# JAR pour l'Interface Homme-Machine (Swing)
jar --create --file "$LIB/ihm.jar" ihm/*.class

cd "$BASE"

# Préparation du Classpath final (inclut TOUS les JARs + le dossier classes)
# L'ordre importe peu ici tant que tout y est.
CP="$CLASSES:$GSON_JAR:$LIB/common.jar:$LIB/interfaces.jar:$LIB/server.jar:$LIB/client.jar:$LIB/ihm.jar"

# Redémarrage du rmiregistry
echo "Redémarrage de rmiregistry sur le port 6090..."
pkill -f "rmiregistry 6090" || true
sleep 1

# Lancement du registre RMI depuis le dossier classes pour la résolution des stubs
cd "$CLASSES"
CLASSPATH="$CP" nohup rmiregistry 6090 > /dev/null 2>&1 &
cd "$BASE"

echo "--------------------------------------------------"
echo "Build complete."
echo "1. Lancer le serveur :"
echo "   java -cp \"$CP\" server.HelloServer 6090"
echo ""
echo "2. Lancer le client (Mode TEXTE) :"
echo "   java -cp \"$CP\" client.HelloClient2 localhost 6090 Lisa 0"
echo ""
echo "3. Lancer le client (Mode GRAPHIQUE) :"
echo "   java -cp \"$CP\" client.HelloClient2 localhost 6090 Lisa 0 --gui"
echo "--------------------------------------------------"