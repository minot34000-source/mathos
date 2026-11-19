#!/usr/bin/env bash
# Essai automatique sur un téléphone simulé : l'application s'installe, démarre, affiche l'accueil,
# garde des données et sait exporter une sauvegarde. Usage : essai.sh <copie d'essai de l'APK>
set -euo pipefail
APK="${1:?fichier APK}"
PAQUET=io.github.minot34000_source.mathos
adb install -r "$APK"
adb logcat -c
adb shell am start -W -n "$PAQUET/.MainActivity" --ez essai true
pret=0
for i in $(seq 1 30); do
  sleep 3
  adb logcat -d -s MATHOS:I > journal.txt || true
  if grep -q 'page: ' journal.txt && grep -q 'enregistrer: ' journal.txt; then pret=1; break; fi
done
adb exec-out screencap -p > apercu.png || true
cat journal.txt
if [ "$pret" != 1 ]; then echo "ECHEC : l'application n'a pas répondu"; adb logcat -d | tail -200; exit 1; fi
grep -q 'Bienvenue dans MATHOS' journal.txt || { echo "ECHEC : l'accueil ne s'affiche pas"; exit 1; }
grep -q 'stockage true' journal.txt || { echo "ECHEC : les données ne sont pas gardées"; exit 1; }
grep -q 'enregistrer: MATHOS_essai.json (14 caracteres)' journal.txt || { echo "ECHEC : l'export ne fonctionne pas"; exit 1; }
echo "ESSAI REUSSI"
