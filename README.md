<div align="center">

<img src="fastlane/metadata/android/en-US/images/icon.png" width="96" alt="">

# amusic

Un client Navidrome pour Android, habillé façon lecteur de musique iOS 26, avec des transitions DJ automatiques entre les morceaux.

![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white)
![Navidrome](https://img.shields.io/badge/serveur-Navidrome-0084FF)
![Licence](https://img.shields.io/badge/licence-GPL--3.0-blue)

</div>

amusic est un fork de [Navic](https://github.com/ssalggnikool/Navic), le client Subsonic de paige. J'ai refait l'interface (barre d'onglets et mini lecteur en verre liquide, écran de lecture plein cadre, paroles, pages album et artiste, bibliothèque) et ajouté AutoMix.

AutoMix enchaîne deux morceaux comme le ferait un DJ. Un petit service sur le serveur analyse la bibliothèque une fois (tempo, tonalité, début et fin utiles de chaque piste) et publie le résultat dans un fichier `automix.json`. L'application le télécharge, puis, en fin de morceau, cale le suivant sur les temps, ajuste légèrement sa vitesse et fait le fondu. Quand les tempos sont trop éloignés, elle se contente d'un fondu court. Les albums écoutés dans l'ordre, le live et le classique ne sont pas mixés par défaut.

## Ce qu'il faut

- Un serveur [Navidrome](https://www.navidrome.org/) qui tourne sous Docker.
- Un téléphone Android 7.0 ou plus récent. Le verre liquide demande Android 12 ; en dessous, les barres sont simplement opaques.
- Pour compiler : JDK 21 et le SDK Android.

## Installer l'application

Récupère `Navic.apk` dans les [Releases](../../releases), ouvre-le sur le téléphone et accepte l'installation depuis une source inconnue. Au premier lancement, connecte-toi avec ton adresse Navidrome et ton compte.

Pour compiler toi-même :

```bash
git clone --recursive https://github.com/myvoow/amusic
cd amusic
./gradlew assembleDebug
```

L'APK sort dans `app/build/outputs/apk/debug/`. Le décodeur FFmpeg se compile sous Linux ou macOS ; sous Windows, il faut construire les bibliothèques à part (voir `androidx-media/libraries/decoder_ffmpeg/README.md`).

## Installer AutoMix sur le serveur

Le dossier [`automix-server`](automix-server) contient l'analyseur (Python, Essentia et ffmpeg dans une image Docker) et un petit serveur web pour `automix.json`.

1. Copie le dossier sur le serveur, par exemple dans `~/automix`.
2. Dans `compose.yml`, remplace `/ssdcaca/shared/Musique` par le chemin de ta musique et `/home/myvo/navidrome/data` par le dossier `data` de Navidrome. Les deux sont montés en lecture seule ; l'analyseur lit la base de Navidrome pour retrouver l'identifiant de chaque morceau.
3. Lance-le :

   ```bash
   docker compose up -d --build
   ```

La première analyse prend du temps (compte quelques heures pour 6 000 morceaux sur deux cœurs). Ensuite, elle repasse toutes les six heures et ne traite que les fichiers nouveaux ou modifiés. Pour suivre l'avancement :

```bash
docker logs -f automix-analyzer
```

Le fichier est servi sur `http://IP_DU_SERVEUR:4534/automix.json`.

### Une seule adresse avec Navidrome

Si Navidrome passe par un tunnel Cloudflare ou un reverse proxy, tu peux servir `automix.json` sur la même adresse que Navidrome. [`automix-server/navidrome`](automix-server/navidrome) montre comment : Caddy prend le port 4533, renvoie `/automix.json` depuis le dossier de l'analyseur et transmet tout le reste à Navidrome. Le conteneur Caddy garde le nom `navidrome`, donc un tunnel qui pointe vers `http://navidrome:4533` continue de marcher sans changement.

## Régler AutoMix dans l'application

Dans Réglages, puis Lecture :

1. Choisis AutoMix comme transition entre les morceaux.
2. Colle l'adresse de `automix.json`, par exemple `https://musique.exemple.fr/automix.json`.

Les autres options de la même page règlent l'écart de tempo accepté, la durée des enchaînements, la prise en compte des tonalités et les genres à ne jamais mixer. Le bouton AutoMix de la file d'attente passe d'un mode à l'autre sans ouvrir les réglages, et l'écran de lecture affiche « Mixing » pendant un enchaînement.

## Licence

GPL-3.0, comme Navic. Le code du verre liquide vient du catalogue de [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) (Apache-2.0). L'application n'utilise ni police ni icône d'Apple.
