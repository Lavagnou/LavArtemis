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
  boutons « Lancer » / « Rejoindre » ; Scène de la session en cours avec **Reprendre puis Quitter** et la
  ligne d'état « EN COURS · DANS <Playnite Fullscreen> · DEPUIS 12 MIN » (durée comptée depuis la première
  fois que l'appareil a vu l'appli tourner, gardée d'un lancement à l'autre, rien sous une minute) ; menu
  d'une appli : Lancer / Reprendre, Quitter, Relancer la session, Masquer / Ne plus masquer (**jeux Playnite
  seulement**) ; jeux masqués (« Afficher les N jeux masqués », « Cacher les jeux masqués ») ; état du PC
  dans l'en-tête : En ligne, Session en cours, Hors ligne, Recherche… ; écrans d'attente « Aucun PC
  appairé », « Connexion à %1… », « Chargement des applis… », « %1 est hors ligne » (+ « Réveiller » quand
  le PC peut l'être, mêmes messages que Mes PC) ; bibliothèque vide : « Aucun jeu Playnite sur ce PC.
  Installe l'extension LavApollo Link… » ; bibliothèque Playnite **dans l'ordre de l'hôte**, le plus récemment
  joué d'abord (le PC la triait par nom jusqu'au 2026-11-25).
- **Confirmations** : quitter ou relancer une session demande « Quitter %1 ? Ce qui n'est pas sauvegardé
  sera perdu. » / « Quitter %1 puis le relancer ? … » ; lancer une autre appli pendant une session :
  « %1 est en cours. Le quitter pour lancer %2 ? … ».
- **Logiciel hôte non pris en charge** : même règle (appversion en quatre nombres, génération ≥ 3 ; inconnue
  = acceptée ; le PC garde en plus la liste GeForce Experience de moonlight-qt, que LavApollo passe
  toujours), même message « La version du logiciel hôte de %1 n'est pas prise en charge (%2). Mets à jour
  LavApollo sur ce PC. », vérifiée avant d'ouvrir le PC, de lancer une appli et de lancer un benchmark.
- **Mes PC** : en-tête, cartes (état, « adresse · LavApollo x.y », « En cours : jeu », « Éteint ou en
  veille… »), menu par carte (Ouvrir, Benchmark des réglages / Appairer / Réveiller, Renommer, Oublier ce
  PC), ajout d'un PC (liste du réseau, « Saisir une adresse… », dialogue, messages ; le PC qui répond à
  l'adresse est appairé tout de suite s'il ne l'est pas, sinon « Ce PC est déjà dans la liste » s'il était
  déjà appairé, « %1 est ajouté » autrement), appairage par code
  PIN (dialogue, messages d'échec, désormais avec les mêmes phrases), renommage (avec « Le nom de ce PC
  dans LavArtemis. Le PC lui-même garde le sien. »), oubli, réveil.
- **Réglages** : mêmes pages (Profil, Affichage, Vidéo, Audio, Entrées, Manettes, Apparence, PC, Avancé),
  même bascule « Mode expert », même tri Normal / Expert / Supprimé. Lignes communes avec le même libellé,
  la même description et les mêmes options : Profil actif, Gérer les profils, Résolution (+
  « Personnalisée… »), Images par seconde (+ « Autre… »), VRR et « Marge du VRR » (Faible latence /
  Équilibrée / Fluide, description par choix), Écran virtuel, **Débit vidéo** (0,5 à 800 Mbit/s sur une
  piste cubique, jusqu'à 3 Gbit/s quand le codec est PyroWave), Revenir au débit recommandé, **Saisir un débit** (expert, toute
  valeur au-delà), Format vidéo (Auto, H.264, HEVC, AV1, PyroWave en expert — codec expérimental, gardé parce
  qu'il est construit, que l'utilisateur n'utilise pas pour l'instant), Couleurs complètes (4:4:4),
  Statistiques à l'écran, Canaux audio, Couper le son du PC distant, Envoyer mon micro, Micro utilisé (ces
  deux-là **hors profils** des deux côtés), Tampon audio, Écran tactile (Multitouch / Trackpad), **Retenir le
  mode tactile choisi en jeu** (expert), Souris pour le bureau à distance, Inverser A/B et X/Y, Toujours
  brancher une manette, Thème, Clair ou sombre, Langue, **Quitter l'appli sur le PC distant à la fin**
  (page PC : ferme le jeu quand le stream s'arrête normalement, pas après une erreur), Synchroniser le
  presse-papiers, Notifier chaque synchronisation, Chercher les mises à jour au démarrage, Chercher une
  mise à jour maintenant (« Version installée : x »), Avertir quand la connexion se dégrade, Enregistrer
  les performances (CSV).
- **Mise à jour** : « Ignorer cette version » dans le dialogue ; la vérification automatique ne la propose
  plus, « Vérifier » dans les réglages si.
- **Menu en jeu**, entrées communes dans cet ordre : Quitter <jeu Playnite>, Déconnecter, Quitter la
  session (confirmée : « Tu veux vraiment quitter ? » ; ferme l'appli sur le PC distant et revient à l'accueil
  du client, qui reste ouvert), Commandes du PC hôte (la liste `ServerCommand` de l'hôte ; vide : « Aucune
  commande de serveur » et le même texte d'explication qu'Android), Envoyer des touches (les **15** mêmes raccourcis, mêmes libellés ; plus de
  raccourci HDR), Envoyer / Récupérer le presse-papiers, Statistiques de performance, Couper / Réactiver le
  micro (seulement quand le micro est envoyé), Passer en trackpad / multitouch (pour ce stream, retenu
  avec « Retenir le mode tactile choisi en jeu »).
- **Stream** (vague 2 de la vérification, 2026-11-25) : nom des étapes de connexion traduits comme
  `utils/StreamStages` (« Démarrage : négociation RTSP », « Échec au démarrage : … (erreur n) ») ; « Connexion
  lente au PC / Baisse le débit », « Mauvaise connexion au PC » ; statistiques à l'écran avec les formulations
  des `perf_overlay_*` ; erreur d'encodage fatale sans le conseil HDR (le HDR est retiré des deux Dev, texte
  Android Dev mis à jour) ; changement d'écran du PC : une reconnexion qui reprend la session, « Écran du PC
  modifié : reconnexion… », jamais l'appli quittée ; suivi actif dès que l'hôte annonce son écran (comme Android).
- **Presse-papiers** : envoyé au début du stream, récupéré sur « Déconnecter », envoyé / récupéré à la demande
  depuis le menu ; mêmes messages (« Presse-papiers envoyé avec succès », « Échec de la récupération du
  presse-papiers : … »).
- **Toucher en mode Trackpad** : le doigt déplace le curseur, tap = clic gauche, appui à deux doigts = clic
  droit, appui long (un doigt) = glisser, **deux doigts qui bougent = défilement** (même vitesse : 5 unités
  par pixel d'un écran de référence de 720 lignes). L'appui long à deux doigts ne fait rien, des deux côtés.
- **Tutoiement** dans tous les textes de la Dev, dialogues de mise à jour, de sortie et messages de stream
  compris.

## 2. Ce qui diffère exprès (feature d'une plateforme)

| PC seulement | Android seulement |
|---|---|
| Multi-écran (réglage et bouton de l'accueil) | Contrôles à l'écran (page entière), clavier à l'écran |
| Page « Raccourcis clavier » (Ctrl+Alt+Maj+…) et leurs rappels dans les descriptions | Bouton vocal push-to-talk (réglage + 2 entrées du menu en jeu) |
| VRR à présentation adaptative Windows, « Lisser la cadence », traces VRR | Rotation : orientation automatique, inverser la résolution, suivre la rotation, « Pivoter l'écran » |
| V-Sync + Régularité des images (vraie source vsync) | Régularité des images en liste (6 modes SurfaceFlinger), faible latence calée, prédictif, LFR |
| Affichage du stream = Plein écran / Sans bordure / Fenêtre | Affichage du stream = Ajuster / Remplir / Étirer |
| Fenêtre de l'appli au démarrage, Garder l'écran allumé, Couper le son hors premier plan | Plein écran immersif, image en haut, forcer le mode d'affichage, fréquence plus basse, débloquer les FPS, écran externe |
| Décodage vidéo (Auto / Logiciel), tampon réseau, débit par défaut et calibrage PyroWave | ADPF, performances soutenues, ping de maintien, égaliseur du système, plage complète, latence ultra faible |
| Manettes : mapper SDL, deviner la disposition, manette en arrière-plan | Manettes : vibrations (+ repli sur l'appareil), Joy-Con, pilote USB, zone morte, capteurs, batterie… |
| — | Débit sur réseau limité |
| PC : trouver les PC (mDNS), détecter les connexions bloquées | **Zoom au pincement** (décision de l'utilisateur : pas sur PC), gestes à plusieurs doigts, sensibilités tactiles, pavé tactile Samsung |
| Menu en jeu : Libérer la souris et le clavier, Capturer le clavier, Plein écran / fenêtre ; descriptions sous chaque entrée ; « Fermer (Échap) » | Menu en jeu : clavier logiciel, zoom, pivoter, Menu avancé ; « Annuler » |
| Créer un raccourci .art (menu d'une appli) | Image dans l'image, latence moyenne en fin de stream |
| Avertissements de configuration | Retenir le zoom |
| Mise à jour : pastille « Mise à jour » dans l'en-tête (pas de dialogue au démarrage) | Mise à jour : dialogue au démarrage |
| Pas de stream vers son propre PC (« Impossible de streamer %1 depuis lui-même… », `NvComputer::isThisPc`, depuis 0.7.0-dev.7) : un appareil Android n'est jamais son propre hôte | — |
| Mes PC, ajout par adresse : toast « %1 a été oublié pendant l'ajout : ajoute-le à nouveau » quand le PC qui a répondu n'est plus dans la liste (oublié pendant l'ajout, `HostsView.addComplete`, 2026-11-25) | — : `HostsActivity.onAdded()` reçoit le PC de l'ajout bloquant lui-même, ce cas n'existe pas |

Les textes qui nomment l'appareil restent différents exprès : « suit le mode clair ou sombre de Windows » /
« de l'appareil », « Ctrl+Alt+Maj+U le coupe en jeu » / « le menu en jeu le coupe ».

## 3. Aligné le 2026-11-24

**Premier passage — Android (Dev seulement, la stable ne change pas)**

- Réglages : description du profil (« Un jeu de réglages de stream par usage… Ce qui tient à cet appareil
  reste commun : langue, apparence, micro, mises à jour. »), débit recommandé (« …, ajusté ensuite
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
  « Statistiques de performance » sortent du Menu avancé ; la liste des touches devient celle du PC.
- Message anglais en dur « This session wasn't started by this device… » traduit.

**Premier passage — PC**

- Toucher : **défilement à deux doigts** en Trackpad (porté d'Android) ; **« Passer en trackpad /
  multitouch » dans le menu en jeu**.
- Menu en jeu : construit à chaque ouverture, ordre et libellés d'Android, confirmation de « Quitter la
  session », micro visible seulement quand il est envoyé, « Commandes du PC hôte » sans liste anglaise
  inventée, résultat d'une commande affiché. **Bogue corrigé** : « Capture de la souris », « Capture du
  clavier » et « Plein écran » ne faisaient rien ; elles s'appellent maintenant comme les raccourcis.
- Envoyer des touches : libellés traduits, mêmes qu'Android.
- Accueil : confirmation avant Quitter / Relancer la session ; « Bureau » au lieu de « Desktop » ; phrase
  d'explication sous « Aucun PC appairé » et « %1 est hors ligne ».
- Mes PC : texte du dialogue Renommer ; messages d'appairage d'Android.
- Description de « Écran tactile » : « …, deux doigts pour défiler ».

**Second passage — Android**

- **Micro hors profils** (`ProfilesManager.APP_WIDE_KEYS`, comme les clés globales du desktop) : lu et écrit
  dans les préférences de base ; les profils enregistrés le perdent au chargement, la valeur du profil actif
  devient la valeur commune (rien n'est perdu).
- **« Quitter l'appli sur le PC distant à la fin »** (page PC, mêmes textes, même règle que le PC :
  `utils/StreamEndPolicy`).
- **Tutoiement** : `app/src/dev/res/values-fr/strings_tu.xml` remplace pour la Dev les chaînes au « vous »
  qu'elle affiche (mise à jour, « Tu veux vraiment quitter ? », erreurs de connexion, micro, benchmark…).
- Raccourci HDR (Win+Alt+B) retiré d'« Envoyer des touches ».

**Second passage — PC**

- Accueil : « Réveiller » quand le PC est hors ligne, « Connexion à %1… », « Chargement des applis… »,
  « Recherche… » dans l'en-tête, texte de bibliothèque vide ; ligne d'état de la Scène d'Android avec la
  durée (même règle que `SessionClock`) ; « Masquer » pour les jeux seulement.
- Logiciel hôte non pris en charge : règle et message d'Android, vérifiés aussi au lancement et au
  benchmark.
- « Retenir le mode tactile choisi en jeu » (expert) ; l'appui long à deux doigts ne tient plus le clic droit.
- Débit : curseur d'Android (0,5–800 Mbit/s, piste cubique ; 3 Gbit/s avec PyroWave, des deux côtés) + « Saisir un débit » ; « Débloquer la limite
  (500 Mbit/s) » retiré (une valeur au-delà, déjà réglée, est gardée) ; benchmark plafonné à 800.
- Mise à jour : « Ignorer cette version ».
- Raccourci HDR retiré ; tutoiement dans toutes les traductions (messages de stream, avertissements de
  lancement, « Tu veux vraiment quitter ? »).

## 3 bis. Aligné le 2026-11-25 (PC, bogues trouvés par le banc de test)

- **« Quitter la session »** fermait tout le client PC : il revient maintenant à l'accueil, comme Android (un
  stream lancé en ligne de commande ou par un lien `art://` ferme toujours le client à la fin).
- **Ajout d'un PC par adresse** : le PC affichait presque toujours « Ce PC est déjà dans la liste » sans
  dialogue d'appairage ; il appaire désormais le PC qui a répondu, avec la règle d'Android
  (`HostsActivity.onAdded()`), y compris un PC déjà connu mais pas appairé.
- **Bibliothèque Playnite** dans l'ordre de l'hôte (le plus récent d'abord) au lieu de l'ordre alphabétique.
- Hors parité mais trouvés au même passage, PC seulement : aucun stream ne démarrait depuis l'interface et
  aucun lien `art://` n'appairait (0.7.0-dev.2 à dev.6) ; « PIN incorrect » pour le bon PIN une fois sur 16
  environ (hérité de moonlight-qt ; Android n'a pas ce défaut) ; plantage `ncrypt.dll` pendant des rafales
  de requêtes HTTPS ; pied du menu en jeu par-dessus la dernière ligne.

## 3 ter. Aligné le 2026-11-25 (PC : vie d'un PC et appairage, banc de test scénario 9)

- **Appairage retiré sur l'hôte** : le PC restait « Hors ligne » pour toujours ; il est désormais en ligne et
  pas appairé (repli HTTP sur un 401, comme Android), quitte l'accueil et les cartes, apparaît dans « Ajouter
  un PC », et l'ajouter de nouveau l'appaire.
- **Oublier le PC affiché** : l'accueil passe à un autre PC ou à « Aucun PC appairé » (il gardait le PC oublié).
  **PC éteint** : ses cartes quittent l'accueil (elles restaient à côté de « hors ligne »). **2ᵉ PC appairé** :
  l'accueil le montre, comme Android.
- **Appairage** : messages d'Android (`v2_pair_wrong_pin`, `_failed`, `_in_game`, `_busy`, `_unknown_host`,
  `_not_found`, `_network`), qui nomment le PC (le PC disait « sur le PC » et montrait parfois le XML de l'hôte
  ou une exception anglaise) ; un seul appairage à la fois, « C'est fait » ne fait que fermer le dialogue (le
  redemander rouvre le même) ; polling du PC arrêté pendant l'appairage ; « %1 est hors ligne : allume-le, puis
  appaire-le. » ; après le succès, PC ouvert seulement si son LavApollo est pris en charge.
- **Ajout par adresse** : « « %1 » n'est pas une adresse valide… » et « %1 n'est pas sur le réseau de cet
  appareil… » (`v2_add_invalid`, `v2_add_wrong_subnet`) au lieu de « Impossible de joindre ».
- **Réveil** : le toast dit si les paquets sont partis (« Réveil envoyé à %1 » / « Le réveil de %1 n'a pas pu
  être envoyé. »), il disait toujours « envoyé ».
- **Lien `art://` d'appairage** (et `lavartemis pair`) : textes de Mes PC / Android (`v2_pair_link_text` pour un
  lien avec code : plus de « Saisis » ; « %1 est appairé ») ; un PC déjà appairé est un succès ; au succès le PC
  s'ouvre sur l'accueil (le PC quittait, ou disait « déjà couplé »).
- Le PC s'annonce à l'hôte sous son nom (celui de `serverinfo`), plus « roth » / « Legacy Moonlight Client ».

Écarts gardés, PC seulement : la première requête d'appairage attend le code **3 minutes au plus** (« %1 n'a
pas reçu le code à temps… » ; Android attend sans limite, mais un appairage abandonné bloquerait ici tous les
autres) ; un code de lien expiré a son message (« Le code du lien n'est plus valable sur %1… » ; Android
affiche l'erreur réseau brute de l'hôte, à aligner). Un PC non appairé ne peut pas être oublié, des deux
côtés (Android ne le propose que dans « Ajouter un PC »).

## 3 quater. Aligné le 2026-11-25 (Réglages, logique, banc de test scénario 11)

- **Nouveau profil** : il part du profil en cours sur PC aussi (il partait des réglages globaux : créé sous
  « Maison », il en perdait les valeurs). Android le faisait déjà (instantané).
- **Débit qui suit la résolution et les images/s** : une seule règle, celle du texte choisi « Suit la résolution
  et les images/s tant que tu ne le changes pas » : il suit tant qu'il est le débit recommandé, bougé à la main il
  reste, « Revenir au débit recommandé » le fait suivre de nouveau. Le PC le faisait déjà (`autoAdjustBitrate`) ;
  **Android le remettait toujours au défaut**, il suit maintenant la même règle (`SettingsStore.bitrateIsRecommended()`).
- **Quitter PyroWave** pour un autre codec ramène un débit au-delà de 800 Mbit/s au bout de la piste, des deux
  côtés (le PC gardait 1,5 Gbit/s en HEVC).
- PC seulement : la liste des langues couvre toutes les langues que l'appli peut enregistrer (7 manquaient), chaque
  nom dans sa langue ; échecs de la recherche de mise à jour en français (« GitHub est injoignable… », « …erreur
  HTTP 403… ») et jamais sans fin (délai de 15 s) ; page Raccourcis : **Ctrl+Alt+Maj+Q = « Déconnecter »**,
  **Ctrl+Alt+Maj+E = « Quitter la session »** (E fermait aussi tout le client).

## 4. Ce qui reste

### À coder (taille raisonnable, pas fait dans ce passage)

| Écart | Estimation | Recommandation |
|---|---|---|
| Profils : le PC garde aussi tampon audio, presse-papiers et recherche des PC hors profil ; Android seulement langue, apparence, micro, mises à jour | ½ jour (même mécanisme que le micro : `APP_WIDE_KEYS`) | À décider : le tampon audio dépend de l'appareil, le presse-papiers moins. |

### À décider (changement visible ou retrait)

| Écart | Recommandation |
|---|---|
| Presse-papiers : aux mêmes moments des deux côtés depuis la vague 2 (début, « Déconnecter », menu) ; le PC envoie en plus une copie faite pendant le stream (Alt+Tab, copie, retour), garde « Taille maximale », et notifie toujours une demande du menu (Android : seulement avec la notification activée) | Garder : sur PC on copie dans une autre fenêtre pendant le stream ; le reste est commun. |
| Tampon audio par défaut : 30 ms (PC, valeur de moonlight-qt) / 40 ms (Android, valeur de moonlight-android) | Garder : chaque défaut vient de la pile audio de sa plateforme. |
| Accueil Android : rangée « Récents » + « Tout voir » ; PC : bibliothèque entière sur l'accueil | Garder : choix d'écran validés (téléphone / PC). |

### Décidé par l'utilisateur le 2026-11-24 (fait)

Appui long à deux doigts : rien des deux côtés. Zoom au pincement : Android seulement. « Masquer » : jeux
seulement. « Ignorer cette version » sur PC. Raccourci HDR retiré des deux listes. Débit du PC comme
Android (800 Mbit/s, 3 Gbit/s avec PyroWave, + saisie). Ligne d'état de la Scène d'Android, avec la durée, sur PC.
