# Séparation stable / dev — design

*2026-11-23 — validé d'avance par l'utilisateur (« je valide directement les specs »).*

## Pourquoi

L'application qui marche aujourd'hui (Android et PC) ne doit plus risquer d'être cassée par les
nouveautés. On veut une **deuxième application, « LavArtemis Dev »**, installable à côté de la stable
sur le même téléphone et le même PC, alimentée par ses propres releases. Toute la suite (extension
Playnite, refonte de l'interface) part d'abord dans la Dev.

## Objectifs

- Deux applis qui cohabitent : nom, icône, identifiant, réglages et dossier d'installation distincts.
- Des releases Dev publiées sans toucher à la stable : la stable ne voit jamais une release Dev,
  la Dev ne voit que les releases Dev.
- Un seul code : la Dev n'est pas un fork, c'est la même base construite avec un autre « canal ».

## Non-objectifs

- Pas de Linux pour la Dev (le job AppImage est de loin le plus long ; l'utilisateur est sur Windows
  et Android). Le code Linux doit quand même compiler.
- Pas de migration Android des réglages stable → Dev (Android interdit de lire les données d'une autre
  appli). La Dev Android s'appaire à nouveau.

## Anti-objectifs

- Qu'une installation, une mise à jour ou une désinstallation de la Dev modifie quoi que ce soit de la
  stable (réglages, appairage, lien `art://`, raccourcis, registre).
- Que la stable propose une release Dev à la mise à jour, ou l'inverse.

## Décisions

### Le canal se lit dans la version

**Une version qui contient `-dev.` est une Dev** (`21.8.0-dev.1`). C'est la seule source de vérité :

- `versionName` dans `app/build.gradle` sur la branche `dev` : `21.8.0-dev.N`.
- La release pose le tag `v<versionName>` (`scripts/local-release.ps1`) : tag et version ne peuvent
  pas diverger.
- Le desktop reçoit la même chaîne dans `app/version.txt` (écrite par le build de release, via
  `CI_VERSION`).

Ainsi rien ne dépend du nom de branche, et une fusion `dev → main` ne peut pas « emporter » le canal :
le passage en stable change la version (`21.8.0`), donc le canal.

Branches : `main` = stable (inchangée), `dev` = Dev, dans LavArtemis **et** LavArtemis-Qt. Le
sous-module `desktop/` de la branche `dev` pointe sur la branche `dev` de LavArtemis-Qt.

### Android

| | Stable | Dev |
|---|---|---|
| applicationId release | `com.limelight.lav` | `com.limelight.lav.dev` |
| applicationId debug | `com.limelight.lavdebug` | `com.limelight.lavdebug.dev` |
| Nom | LavArtemis | LavArtemis Dev |
| Icône | logo lune, fond bleu nuit | même écran, fond « plan d'atelier » `#1D4E89` quadrillé + pastille verte `DEV` (`docs/branding`) |

- `build.gradle` calcule `lavDev = versionName.contains('-dev.')` ; le suffixe `.lav` reste écrit en
  toutes lettres (commentaire amont), la Dev ajoute `.dev` derrière.
- `BuildConfig.LAV_DEV` expose le canal au code.
- Les `authorities` des providers utilisent déjà `${applicationId}` : pas de conflit d'installation.
- Le lien `art://` : les deux applis le déclarent, Android propose le choix. Accepté.

### Desktop (Windows)

| | Stable | Dev |
|---|---|---|
| Organisation Qt (registre, `AppData`) | `LavArtemis` | `LavArtemis Dev` |
| Dossier d'installation | `LavArtemis` | `LavArtemis Dev` |
| Raccourcis, pare-feu, nom du paquet | LavArtemis | LavArtemis Dev |
| UpgradeCode MSI et bundle | inchangés | nouveaux GUID |
| `art://` et `.art` | enregistrés | **non** enregistrés (la stable les garde) |

- Changer l'**organisation** et pas seulement le nom d'appli est obligatoire : la désinstallation de la
  stable supprime `HKCU\Software\LavArtemis` et `%LOCALAPPDATA%\LavArtemis` entiers.
- `version.txt` peut contenir `-dev.N` : qmake reçoit une version numérique `21.8.0.N` (ressource
  Windows, version MSI), `VERSION_STR` garde la chaîne lisible. Le MSI ignore le 4ᵉ nombre dans ses
  comparaisons : la Dev autorise donc la mise à niveau à version égale.
- **Premier lancement de la Dev** : si elle n'a encore aucun réglage, elle copie une fois ceux de la
  stable (registre et fichiers `AppConfigLocation` : hôtes appairés, profils, macros). La stable n'est
  que lue. Ainsi la Dev PC n'a pas à être réappairée.
- Le canal est un `DEFINES += LAV_DEV_CHANNEL` posé par `app.pro` quand la version contient `-dev.`.
- La Dev s'affiche comme telle : titre de fenêtre « LavArtemis Dev ».

### Mises à jour in-app (les deux clients)

- Stable : `/releases/latest` uniquement (GitHub n'y met jamais une prerelease).
- Dev : `/releases`, première release dont le tag contient `-dev.`.
- Le réglage « canal prerelease / CI » disparaît des deux clients (le tri des réglages l'avait retiré :
  l'appli Dev le remplace).
- Comparaison de versions : déjà compatible (`21.8.0-dev.2 < 21.8.0-dev.10 < 21.8.0`).

### Releases

- Construites sur le PC du mainteneur par `scripts/local-release.ps1` (APK signés, client Windows x64,
  `gh release create`), pas par GitHub Actions : le quota est court. `release.yml` ne part plus qu'à la
  main.
- Une version `-dev.` est publiée en **prerelease** (elle ne devient jamais « latest »), Android +
  Windows seulement.
- Les noms d'artefacts gardent le tag (`LavArtemis-v21.8.0-dev.1-android-arm64-v8a.apk`), ce qui suffit
  à les distinguer.
- Tout le travail va dans la Dev, sans report vers la stable ; la stable ne sort qu'après des semaines
  d'usage de la Dev par l'utilisateur.

## Laissé au constructeur

- Couleur exacte de l'icône Dev, texte des notes de release.
- Ordre des commits.

## Approches écartées

- **Flavor Gradle `channel`** : double toutes les variantes (`nonRoot_gameStableDebug`…), change chaque
  commande documentée et la CI ; pour un seul booléen.
- **Propriété Gradle `-Pchannel=dev`** : un build local de la branche `dev` sans l'option produirait la
  stable ; source d'erreur.
- **Fichier de canal versionné par branche** : une fusion `dev → main` l'emporterait silencieusement.
