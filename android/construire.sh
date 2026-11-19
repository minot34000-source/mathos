#!/usr/bin/env bash
# Fabrique l'application Android de MATHOS, sans Gradle : la page du site est embarquée dans l'APK.
# Usage : construire.sh <dossier de sortie> <numéro de fabrication> <fichier de clés> <mot de passe des clés>
set -euo pipefail
ICI="$(cd "$(dirname "$0")" && pwd)"
RACINE="$(dirname "$ICI")"
SORTIE="${1:?dossier de sortie}"; CODE="${2:?numéro de fabrication}"; CLES="${3:?fichier de clés}"; MDP="${4:?mot de passe}"
VERSION="0.2"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
OUTILS="$SDK/build-tools/$(ls "$SDK/build-tools" | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' | sort -V | tail -1)"
PLATEFORME=""
for n in 36 35 34; do
  if [ -f "$SDK/platforms/android-$n/android.jar" ]; then PLATEFORME="$SDK/platforms/android-$n/android.jar"; break; fi
done
if [ -z "$PLATEFORME" ] || [ ! -x "$OUTILS/aapt2" ]; then
  echo "Outils Android introuvables dans $SDK" >&2; ls "$SDK/build-tools" "$SDK/platforms" >&2; exit 1
fi
echo "Outils : $OUTILS"; echo "Plateforme : $PLATEFORME"

rm -rf "$SORTIE"
mkdir -p "$SORTIE/res/values" "$SORTIE/res/values-night" "$SORTIE/res/drawable" "$SORTIE/res/mipmap-anydpi-v26" "$SORTIE/res/mipmap-xxxhdpi" "$SORTIE/assets/icons" "$SORTIE/classes"

# 1. Le contenu : la page du site, telle qu'elle est en ligne
cp "$RACINE/index.html" "$RACINE/manifest.webmanifest" "$SORTIE/assets/"
cp "$RACINE"/icons/*.png "$SORTIE/assets/icons/"
cp "$RACINE/icons/icon-192.png" "$SORTIE/res/mipmap-xxxhdpi/ic_launcher.png"

# 2. Les ressources : thème clair et thème sombre, icône
theme() { # $1 fichier, $2 thème parent, $3 fond, $4 bandeau du haut
  cat > "$1" <<XML
<?xml version="1.0" encoding="utf-8"?>
<resources>
  <color name="bleu">#1d4dbd</color>
  <color name="fond">$3</color>
  <color name="bandeau">$4</color>
  <style name="Mathos" parent="@android:style/$2">
    <item name="android:windowBackground">@color/fond</item>
    <item name="android:statusBarColor">@color/bandeau</item>
    <item name="android:windowLightStatusBar">false</item>
  </style>
</resources>
XML
}
theme "$SORTIE/res/values/styles.xml" Theme.DeviceDefault.Light.NoActionBar '#f4f6fb' '#1d4dbd'
theme "$SORTIE/res/values-night/styles.xml" Theme.DeviceDefault.NoActionBar '#0d1321' '#151e32'
cat > "$SORTIE/res/drawable/ic_avant.xml" <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
  <path android:fillColor="#FFFFFF" android:pathData="M38,43h32v6h-32z M51,30h6v32h-6z M38,68h32v6h-32z" />
</vector>
XML
cat > "$SORTIE/res/mipmap-anydpi-v26/ic_launcher.xml" <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
  <background android:drawable="@color/bleu" />
  <foreground android:drawable="@drawable/ic_avant" />
</adaptive-icon>
XML

# 3. Le code
"$OUTILS/aapt2" compile --dir "$SORTIE/res" -o "$SORTIE/res.zip"
javac -source 8 -target 8 -Xlint:-options -encoding UTF-8 -bootclasspath "$PLATEFORME" -classpath "$PLATEFORME" -d "$SORTIE/classes" "$ICI/MainActivity.java"
"$OUTILS/d8" --release --min-api 24 --lib "$PLATEFORME" --output "$SORTIE" $(find "$SORTIE/classes" -name '*.class')

# 4. Deux fichiers : l'application, et une copie d'essai (débogable) réservée au téléphone simulé
assembler() { # $1 nom du fichier, puis options de liaison supplémentaires
  local nom="$1"; shift
  "$OUTILS/aapt2" link -o "$SORTIE/$nom.brut.apk" -I "$PLATEFORME" --manifest "$ICI/AndroidManifest.xml" -A "$SORTIE/assets" \
    --min-sdk-version 24 --target-sdk-version 34 --version-code "$CODE" --version-name "$VERSION" "$@" "$SORTIE/res.zip"
  (cd "$SORTIE" && zip -q -j "$nom.brut.apk" classes.dex)
  "$OUTILS/zipalign" -f -p 4 "$SORTIE/$nom.brut.apk" "$SORTIE/$nom.aligne.apk"
  "$OUTILS/apksigner" sign --ks "$CLES" --ks-pass "pass:$MDP" --ks-key-alias mathos --out "$SORTIE/$nom.apk" "$SORTIE/$nom.aligne.apk"
  "$OUTILS/apksigner" verify "$SORTIE/$nom.apk"
}
assembler MATHOS
assembler MATHOS-essai --debug-mode

"$OUTILS/aapt2" dump badging "$SORTIE/MATHOS.apk" | grep -E "^(package|sdkVersion|targetSdkVersion|application-label:|uses-permission)" || true
ls -l "$SORTIE"/MATHOS.apk "$SORTIE"/MATHOS-essai.apk
sha256sum "$SORTIE/MATHOS.apk"
