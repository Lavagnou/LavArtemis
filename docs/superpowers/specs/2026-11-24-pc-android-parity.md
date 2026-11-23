# Parité PC / Android (LavArtemis Dev) — 2026-11-24

> Règle de l'utilisateur : « Il faut garder une constance le plus possible entre les deux versions. Les
> seules choses qui diffèrent doivent être des features précises d'une version. Le reste doit être commun,
> au maximum. »

Ce document compare l'interface v2 des deux clients Dev : le PC (`LavArtemis-Qt`, `app/gui/v2/` et
`app/gui/QuickMenu.qml`) et Android (`app/src/main/java/com/limelight/v2/`, `GameMenu.java`). Il dit ce
qui est commun, ce qui diffère exprès, ce qui a été aligné le 2026-11-24 et ce qui reste à décider. **Le
tenir à jour** : tout changement d'un texte, d'un menu ou d'un réglage commun se fait des deux côtés, ou
s'ajoute ici comme écart.

## 1. Ce qui est commun (identique des deux côtés)

- **Accueil** : un PC à la fois ; cartes « Bureau » (« Ton bureau Windows, tel quel ») et « Playnite
  Fullscreen » (« Plein écran, choisis ton jeu à la manette ») ; badges « EN COURS » / « EN COURS · jeu » ;
  boutons « Lancer » / « Rejoindre » ; Scène de la session en cours avec **Reprendre puis Quitter** ; menu
  d'une appli : Lancer / Reprendre, Quitter, Relancer la session, Masquer / Ne plus masquer ; jeux masqués
  (« Afficher les N jeux masqués », « Cacher les jeux masqués ») ; état du PC dans l'en-tête : En ligne,
  Session en cours, Hors ligne (+ Recherche… sur Android) ; écrans vides « Aucun PC appairé » et
  « %1 est hors ligne » avec la même phrase d'explication.
- **Confirmations** : quitter ou relancer une session demande « Quitter %1 ? Ce qui n'est pas sauvegardé
  sera perdu. » / « Quitter %1 puis le relancer ? … » ; lancer une autre appli pendant une session :
  « %1 est en cours. Le quitter pour lancer %2 ? … ».
- **Mes PC** : en-tête, cartes (état, « adresse · LavApollo x.y », « En cours : jeu », « Éteint ou en
  veille… »), menu par carte (Ouvrir, Benchmark des réglages / Appairer / Réveiller, Renommer, Oublier ce
  PC), ajout d'un PC (liste du réseau, « Saisir une adresse… », dialogue, messages), appairage par code
  PIN (dialogue, messages d'échec, désormais avec les mêmes phrases), renommage (avec « Le nom de ce PC
  dans LavArtemis. Le PC lui-même garde le sien. »), oubli, réveil.
- **Réglages** : mêmes pages (Profil, Affichage, Vidéo, Audio, Entrées, Manettes, Apparence, PC, Avancé),
  même bascule « Mode expert », même tri Normal / Expert / Supprimé. Lignes communes avec le même libellé,
  la même description et les mêmes options : Profil actif, Gérer les profils, Résolution (+
  « Personnalisée… »), Images par seconde (+ « Autre… »), VRR et « Marge du VRR » (Faible latence /
  Équilibrée / Fluide, description par choix), Écran virtuel, Débit vidéo, Revenir au débit recommandé,
  Format vidéo (Auto, H.264, HEVC, AV1, PyroWave en expert), Couleurs complètes (4:4:4), Statistiques à
  l'écran, Canaux audio, Couper le son du PC distant, Envoyer mon micro, Micro utilisé, Tampon audio,
  Écran tactile (Multitouch / Trackpad), Souris pour le bureau à distance, Inverser A/B et X/Y, Toujours
  brancher une manette, Thème, Clair ou sombre, Langue, Synchroniser le presse-papiers, Notifier chaque
  synchronisation, Chercher les mises à jour au démarrage, Chercher une mise à jour maintenant (« Version
  installée : x »), Avertir quand la connexion se dégrade, Enregistrer les performances (CSV).
- **Menu en jeu**, entrées communes dans cet ordre : Quitter <jeu Playnite>, Déconnecter, Quitter la
  session (confirmée : « Voulez-vous vraiment quitter ? »), Commandes du PC hôte (« Aucune commande »
  expliqué si l'hôte n'en donne pas), Envoyer des touches (les 16 mêmes raccourcis, mêmes libellés),
  Envoyer / Récupérer le presse-papiers, Statistiques de performance, Couper / Réactiver le micro
  (seulement quand le micro est envoyé), Passer en trackpad / multitouch (pour ce stream).
- **Toucher en mode Trackpad** : le doigt déplace le curseur, tap = clic gauche, appui à deux doigts = clic
  droit, appui long = glisser, **deux doigts qui bougent = défilement** (même vitesse : 5 unités par pixel
  d'un écran de référence de 720 lignes).

## 2. Ce qui diffère exprès (feature d'une plateforme)

| PC seulement | Android seulement |
|---|---|
| Multi-écran (réglage et bouton de l'accueil) | Contrôles à l'écran (page entière), clavier à l'écran |
| Page « Raccourcis clavier » (Ctrl+Alt+Maj+…) et leurs rappels dans les descriptions | Bouton vocal push-to-talk (réglage + 2 entrées du menu en jeu) |
| VRR à présentation adaptative Windows, « Lisser la cadence », traces VRR | Rotation : orientation automatique, inverser la résolution, suivre la rotation, « Pivoter l'écran » |
| V-Sync + Régularité des images (vraie source vsync) | Régularité des images en liste (6 modes SurfaceFlinger), faible latence calée, prédictif, LFR |
| Affichage du stream = Plein écran / Sans bordure / Fenêtre | Affichage du stream = Ajuster / Remplir / Étirer |
| Fenêtre de l'appli au démarrage, Garder l'écran allumé, Couper le son hors premier plan | Plein écran immersif, image en haut, forcer le mode d'affichage, fréquence plus basse, débloquer les FPS, écran externe |
| Décodage vidéo (Auto / Logiciel), tampon réseau et calibrage PyroWave | ADPF, performances soutenues, ping de maintien, égaliseur du système, plage complète, latence ultra faible |
| Manettes : mapper SDL, deviner la disposition, manette en arrière-plan | Manettes : vibrations (+ repli sur l'appareil), Joy-Con, pilote USB, zone morte, capteurs, batterie… |
| Débloquer la limite de débit (500 Mbit/s) | Débit jusqu'à 800 Mbit/s + « Saisir un débit », débit sur réseau limité |
| PC : quitter l'appli à la fin, trouver les PC (mDNS), détecter les connexions bloquées | Zoom au pincement, gestes à plusieurs doigts, sensibilités tactiles, pavé tactile Samsung |
| Menu en jeu : Libérer la souris et le clavier, Capturer le clavier, Plein écran / fenêtre ; descriptions sous chaque entrée ; « Fermer (Échap) » | Menu en jeu : clavier logiciel, zoom, pivoter, Menu avancé ; « Annuler » |
| Créer un raccourci .art (menu d'une appli) | Image dans l'image, latence moyenne en fin de stream |
| Avertissements de configuration | Retenir le mode tactile choisi en jeu, retenir le zoom |

Les textes qui nomment l'appareil restent différents exprès : « suit le mode clair ou sombre de Windows » /
« de l'appareil », « Ctrl+Alt+Maj+U le coupe en jeu » / « le menu en jeu le coupe ».

## 3. Aligné le 2026-11-24

**Android (Dev seulement, la stable ne change pas)**

- Réglages : description du profil (« Un jeu de réglages de stream par usage… Ce qui tient à cet appareil
  reste commun : langue, apparence, mises à jour. »), débit recommandé (« …, ajusté ensuite
  automatiquement »), codec (« « Auto » choisit le meilleur format que cet appareil sait décoder. » et ordre
  Auto, H.264, HEVC, AV1), Couper le son du PC distant (« Relance le jeu en cours pour que ça
  s'applique. »), Tampon audio (« Effet à la prochaine connexion. »), Langue, Chercher les mises à jour,
  bouton « Vérifier » et « Version installée : x », « Enregistrer les performances (CSV) », Marge du VRR
  (une description par choix, celles du PC), description du Trackpad (« deux doigts pour défiler »).
- Réglages, placement : « Avertir quand la connexion se dégrade » passe d'Apparence à **Avancé** ; le
  presse-papiers passe d'Avancé à une page **PC**, comme sur le PC.
- **Lien Obtainium retiré** (demande de l'utilisateur : la Dev se met à jour seule) ; la page des versions
  reste.
- Accueil : Reprendre puis Quitter dans la Scène ; état du PC en « En ligne / Session en cours / Hors ligne /
  Recherche… » ; « Réveiller » de l'accueil avec les messages de Mes PC.
- Menu en jeu : ordre et libellés du PC pour les entrées communes ; « Envoyer des touches » et
  « Statistiques de performance » sortent du Menu avancé ; la liste des touches devient celle du PC (16
  raccourcis, dont Ctrl+Alt+Suppr et Ctrl+Maj+Échap ; l'ancien « ALT + B », qui envoyait Win+Alt+B, est
  nommé pour ce qu'il fait).
- Message anglais en dur « This session wasn't started by this device… » traduit (même texte que le PC).

**PC**

- Toucher : **défilement à deux doigts** en Trackpad (nouveau code, porté d'Android) ; **« Passer en
  trackpad / multitouch » dans le menu en jeu** (nouveau code, pour ce stream seulement).
- Menu en jeu : construit à chaque ouverture, ordre et libellés d'Android, confirmation de « Quitter la
  session », micro visible seulement quand il est envoyé et libellé selon son état, « Commandes du PC
  hôte » sans liste anglaise inventée (« Aucune commande » expliqué), résultat d'une commande affiché.
  **Bogue corrigé** : « Capture de la souris », « Capture du clavier » et « Plein écran » ne faisaient rien ;
  elles s'appellent maintenant comme les raccourcis (Libérer la souris et le clavier, Capturer le clavier,
  Plein écran / fenêtre) et font la même chose qu'eux.
- Envoyer des touches : les libellés restés en anglais sont traduits (Alt+Entrée, Ctrl+Alt+Suppr,
  Maj+Tab (overlay Steam), Win+Maj+Gauche, Ctrl+Alt+Maj+F1/F12 (changer d'écran)) — mêmes libellés
  qu'Android.
- Accueil : confirmation avant Quitter / Relancer la session ; « Bureau » au lieu du nom brut « Desktop »
  dans le menu, les dialogues et les écrans de lancement ; phrase d'explication sous « Aucun PC appairé »
  et « %1 est hors ligne ».
- Mes PC : texte du dialogue Renommer ; messages d'appairage avec les phrases d'Android (plus de
  « jumelage », plus de vouvoiement).
- Description de « Écran tactile » : « …, deux doigts pour défiler ».

## 4. Ce qui reste

### À coder (taille raisonnable, pas fait dans ce passage)

| Écart | Estimation | Recommandation |
|---|---|---|
| Zoom au pincement en Trackpad sur PC (Android : 1×–10×, la vue suit le curseur) | 1 à 2 jours : le PC a déjà le zoom (`panzoom`), mais suivre le curseur impose de le tenir en position absolue, comme Android (`TrackedCursor`, `MouseMover`) | Seulement si l'écran tactile du PC sert vraiment ; sinon le noter comme propre à Android. |
| « Retenir le mode tactile choisi en jeu » sur PC | 1 h (un réglage expert + écrire `absoluteTouchMode` au changement) | À faire si l'entrée du menu en jeu sert souvent. |
| Accueil PC : bouton « Réveiller » dans l'état hors ligne, états « Connexion à %1… » / « Chargement des applis… », texte « Aucun jeu Playnite sur ce PC… » | 2 h | Oui, petit et visible. |
| Message « logiciel hôte non pris en charge » : Android donne la version et le vérifie aussi avant Benchmark et lancement | 1 h | Oui, aligner le PC sur Android. |
| Profils : le PC garde micro, tampon audio, presse-papiers et recherche des PC hors profil ; Android seulement langue, apparence, mises à jour | ½ jour (les lecteurs Android lisent à travers le profil : il faut les faire lire les préférences de base) | Oui pour le micro, qui dépend de l'appareil. |
| Presse-papiers : le PC synchronise en continu (taille max, notification) ; Android envoie au début et récupère en quittant | 1 jour et plus | À décider avec l'utilisateur ; les descriptions disent ce que chacun fait. |
| « Quitter l'appli sur le PC distant à la fin » existe sur PC, pas sur Android | ½ jour | Oui si l'utilisateur s'en sert. |
| Textes de mise à jour et de « Voulez-vous vraiment quitter ? » d'Android au vouvoiement (chaînes partagées avec la stable) | 1 h (chaînes v2 pour la Dev) | Oui, passer au tutoiement des deux côtés. |

### À décider (changement visible ou retrait)

| Écart | Recommandation |
|---|---|
| Appui long à deux doigts : clic droit maintenu sur PC, rien sur Android | Garder le clic droit maintenu et l'ajouter à Android (rien n'utilise ce geste là-bas). |
| Ligne d'état de la Scène : « EN COURS SUR <PC> » (PC) / « EN COURS · DANS <jeu> · DEPUIS 12 MIN » (Android) | Prendre celle d'Android, plus informative, après passage par le prototype. |
| « Masquer » proposé pour Bureau et Playnite sur PC, pour les jeux seulement sur Android | Jeux seulement (le bouton parle de « jeux masqués »). |
| Mise à jour : pastille « Mise à jour » + OK/Annuler sur PC ; dialogue automatique avec « Ignorer cette version » sur Android | Ajouter « Ignorer cette version » au PC ; garder la pastille (pas de dialogue au démarrage). |
| Raccourci « Win+Alt+B (activer/désactiver le HDR) » dans Envoyer des touches | Le retirer des deux listes (l'utilisateur n'utilise pas le HDR). |
| Débit : « Débloquer la limite (500 Mbit/s) » sur PC, curseur jusqu'à 800 + « Saisir un débit » sur Android | Garder (le PC a ses propres limites de décodage) ou unifier sur la saisie d'Android. |
| Tampon audio par défaut : 30 ms (PC, valeur de moonlight-qt) / 40 ms (Android, valeur de moonlight-android) | Garder : chaque défaut vient de la pile audio de sa plateforme. |
| Accueil Android : rangée « Récents » + « Tout voir » ; PC : bibliothèque entière sur l'accueil | Garder : choix d'écran validés (téléphone / PC). |
