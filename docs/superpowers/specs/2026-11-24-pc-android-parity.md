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
  Portage V8 (2026-11-26) : mêmes lignes d'aide par entrée (« Touche pour ouvrir le PC. Appui long ou ⋯ : … »,
  « Entrée : ouvrir le PC. Touche Menu ou Entrée maintenue : … »), même ligne « Tour · réseau filaire · 4 ms », mêmes
  dialogues « Ajouter un PC » (« PC trouvés sur ton réseau local (Apollo ou Sunshine) : » / « Aucun PC trouvé sur ton
  réseau local pour l’instant. »), « Saisir une adresse » (champ « Adresse »), « Renommer {PC} » (champ « Nom »), même
  note des options d'un PC. **Écart ouvert** : Android écrit « En cours : {jeu} dans Playnite » quand le jeu tourne
  dans Playnite Fullscreen (prototype), le desktop « En cours : {jeu} ».
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
  presse-papiers : … »). Ce qui suit les deux-points est le même court motif traduit des deux côtés (« erreur
  500 », « connexion refusée », « pas de réponse à temps »… : `ComputerManager::hostErrorDetail()` /
  `utils/HostErrorText`), jamais le texte anglais de l'exception (vague 6).
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
  reste commun : langue, apparence, micro, mises à jour. » ; « …micro et tampon audio, presse-papiers… » depuis la
  nuit du 26), débit recommandé (« …, ajusté ensuite
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
autres). Un code de lien expiré a son message des deux côtés depuis la nuit du 26 (« Le code du lien n'est plus
valable sur %1… », voir 3 octies). Un PC non appairé ne peut pas être oublié, des deux
côtés (Android ne le propose que dans « Ajouter un PC »).

## 3 quater. Aligné le 2026-11-25 (Réglages, logique, banc de test scénario 11)

- **Nouveau profil** : il part du profil en cours sur PC aussi (il partait des réglages globaux : créé sous
  « Maison », il en perdait les valeurs). Android le faisait déjà (instantané).
  **Vague 6 (2026-11-26)** : des deux côtés, un nouveau profil est un **instantané complet** de ce qui est en
  vigueur, défauts compris, qu'un profil soit actif ou non, langue et apparence exclues : PC
  `ProfileManager::createProfile()` (il était vide sans profil actif, partiel sous un profil épars ; test
  `tests/profiles`), Android « Appliquer comme profil » du benchmark (`BenchmarkSettings.applyAsProfile`, qui ne
  copiait que le profil actif : sans profil, le seul réglage du passage ; test `BenchmarkProfileTest`).
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

## 3 quinquies. Aligné le 2026-11-25 (benchmark, ligne de commande, mise à jour, manettes ; scénarios 12 à 14)

- **Benchmark, référence** : la référence (et sa répétition) enregistre ce qu'elle a streamé (`requested` =
  `effective`), des deux côtés. En Auto, les deux enregistraient la supposition (HEVC) et le mode adaptatif
  montait le débit puis confirmait sur un codec que la référence n'avait pas streamé (`LavApollo/docs/benchmark.md`).
- **Benchmark, notes** : écrites en anglais dans le JSON et traduites à l'affichage, **mêmes phrases** des deux
  côtés (le PC les écrivait en français) ; le PC traduit toutes les formes d'Android, Android celles du PC
  (« Le stream n'a pas démarré. », « …n'a pas pu démarrer. », « Le débit a été plafonné à %1 Mbit/s. ») ;
  textes français repris d'Android (« Passé en %1, %2 à la place. », « Échec au démarrage (%1) : erreur %2. »,
  « l'appareil est revenu à »…). Les codecs et le pacing s'affichent par leur nom des deux côtés (« Passé en
  HEVC, V-Sync à la place. ») : Android montrait leur identifiant (« hevc, vsync ») jusqu'à la nuit du 26.
- PC seulement : noms de mode et d'objectif traduits dans le rapport et l'historique (« quick », « balanced »
  s'affichaient) ; « Arrêter » avant le premier passage ramène la page de départ (page vide avant) ; « Arrêter »
  pendant le redémarrage de la scène n'est plus suivi d'un passage.
- PC seulement : un jeu Playnite lancé en ligne de commande, par un `.art` ou un lien pendant que Playnite tourne
  démarre dans Playnite comme depuis l'accueil (la ligne de commande proposait de quitter Playnite) ; un lien ou
  un `.art` mal formé ouvre l'accueil avec un message en français (le processus quittait) ; seul le premier
  argument est un lien ou un `.art`.
- PC seulement : une disposition de manette devinée est annoncée à chaque fois (mapper, chaque lancement), pas
  seulement la première ; couper « Deviner la disposition » vaut sans redémarrer ; le nettoyage des installeurs
  téléchargés ne touche plus ceux de l'autre appli (stable / Dev) ; le dialogue de mise à jour n'a plus l'icône
  d'erreur.

## 3 sexies. Aligné le 2026-11-26 (données de l'hôte pour le prototype, vague 5, scénario 15)

- **Mêmes données des deux côtés**, lues des mêmes champs de LavApollo (`LavApollo/docs/lavartemis-host-data.md`) :
  installé ou non, heures jouées, dernière activité et image de fond de chaque jeu Playnite ; début de la session et
  du jeu lancé dans Playnite ; type de PC (tour / mini-PC / portable) ; filaire ou Wi-Fi. Pas encore affichées
  (la vague du portage du prototype s'en charge), sauf le début de session, que les deux accueils prennent
  désormais de l'hôte (« depuis 42 min ») au lieu de leur propre horloge quand l'hôte le donne.
- **Ordre « Récents »** : même règle des deux côtés, tri sur la dernière activité, jamais joués à la fin, un hôte qui
  ne la donne pas garde son ordre (`NvComputer::sortApps()` / `HomeModel.sortRecentFirst()`).
- **Latence** : même mesure, un `connect` TCP après chaque poll réussi, au moins 1 ms (`PcMonitorThread::
  measureLatencyMs` / `computers/HostLatency`).

## 3 septies. Aligné le 2026-11-26 (vague 6, revue des vagues 1–4)

- **Quitter une appli qui refuse** (accueil, fin de session, « Quitter l'appli sur le PC distant à la fin ») : la
  même phrase des deux côtés, « Impossible de quitter %1 sur le PC (%2). », %1 l'appli, %2 le motif court commun
  (« erreur 500 », « connexion refusée »…) ; l'erreur 599 garde sa phrase (« Le jeu en cours n'a pas été lancé par
  ce PC / cet appareil… »). Avant : texte anglais de l'exception sur PC (fin de session, accueil) et sur Android
  (`ServerHelper.doQuit`, fin de stream de `Game`, où le 599 n'avait même pas sa phrase).
- Écart restant, minime : le motif d'une erreur réseau inconnue porte son numéro sur PC (« erreur réseau 99 »,
  code Qt), pas sur Android (« erreur réseau ») : les deux piles n'ont pas les mêmes codes.

## 3 octies. Aligné la nuit du 26 (Android)

- **Lien d'appairage expiré** : LavApollo répond `503 « OTP auth not available. »` à un code de lien expiré (3 min)
  ou déjà servi. Android affichait « La connexion avec %1 a été perdue pendant l'appairage (Host PC returned error:
  OTP auth not available. (Error code: 503)) » ; il affiche maintenant la phrase du PC, « Le code du lien n'est plus
  valable sur %1 : il ne sert qu'une fois et expire au bout de 3 minutes. Crée un nouveau lien dans LavApollo. »
  (même test que le PC : 503 et « OTP » dans le message). Toute autre panne d'appairage finit par le motif court
  commun (« erreur 500 », « connexion refusée »), comme `ComputerManager::pairingErrorText` : plus jamais le texte
  anglais de l'exception (`utils/PairingErrorText`, test `PairingErrorTextTest`).
- **Notes du benchmark** : Android nomme les codecs et le pacing (« Passé en HEVC, rythme par défaut à la place. »,
  « AV1 n'est pas disponible : l'appareil est revenu à HEVC »), identifiants d'Android et du PC
  (`BenchmarkNotes.codecLabel` / `pacingLabel`, aussi utilisés par `BenchmarkActivity`). Libellés VRR repris du PC
  (« VRR, faible latence / équilibré / fluide », au lieu de « rythme VRR, … »). Le planificateur Android écrit la
  note VRR de la spécification (« VRR is not available on this display » ; l'ancienne forme reste traduite).
  Tests `BenchmarkNotesTest`, `BenchmarkTest`.
- **Profils** : tampon audio, presse-papiers (synchroniser, notifier, masquer le contenu) et « Enregistrer les
  performances (CSV) » sont hors profils sur Android aussi (`ProfilesManager.APP_WIDE_KEYS`, lignes `global()` des
  Réglages), comme les `ArtemisSettings` du PC. Un profil enregistré les perd au chargement, la valeur du profil actif
  devient la valeur commune. Description du profil alignée sur le PC (« …langue, apparence, micro et tampon audio,
  presse-papiers, mises à jour. », sans la recherche des PC, qui n'existe que sur PC). Tests `AppWideKeysTest`,
  `SettingsCatalogTest`.
- **Benchmark, PC** : le planificateur écrit la note de la spécification (« av1 is not available: the **device** fell
  back to hevc » ; l'ancienne forme « client » reste traduite) ; un rapport Android ouvert sur PC nomme ses modes de
  pacing comme Android (« rythme par défaut », « latence ultra faible », « latence ultra faible rythmée », « rythme
  prédictif » ; en anglais « default pacing »…), les modes communs (V-Sync, VRR) l'étaient déjà
  (`Benchmark::pacingLabel`, `qml_fr.ts`, test `tests/benchmark`).

## 3 nonies. Portage exact du prototype V8, nuit du 26 (branche `port-v8` des deux dépôts, non fusionnée)

Les deux clients copient le prototype (textes de `textes-choisis.md`) ; détails et reste : `port-notes.md`.
- **Communs** : entrée manette (Y = options, LB / RB, Start = retour, A maintenu 500 ms = options, grille de focus du
  prototype) ; toast en haut 2,4 s au style du thème ; barre d'aide manette / clavier ; accueil par thème (héros
  Braise, Scène Reprendre / Quitter + ligne « via », 6 récents + tuile « Tout voir » + lien, légende « {jeu} · {N} h
  jouées », cartes d'état hors ligne / recherche / chargement / premier lancement) ; options du prototype (« Jouer »
  pour un jeu, « Lancer » pour une appli, « Fermer X et lancer », « Fermer X et jouer dans Playnite », Reprendre,
  Relancer la session, Quitter, Masquer, Annuler) dans un dialogue centré, avertissement de fermeture au lieu de
  « %1 est en cours. Le quitter pour lancer %2 ? ».
- **Écarts de la nuit** : Android n'a pas « Afficher les détails » (écran de détails pas porté) ; le desktop n'a pas
  le « Créer un raccourci sur le bureau (.art) » côté Android (PC seulement, voulu). Android a le menu en jeu en
  tuiles (Reprendre + 9 + Plus) et la pastille « Menu » ; le desktop garde `QuickMenu` pour l'instant.
- **Écart ouvert (2026-11-26, Android)** : les 15 raccourcis d'« Envoyer des touches » sont écrits comme le prototype
  (« Échap », « Alt + F4 », « Windows », « Windows + D », « Ctrl + Alt + Suppr », « Ctrl + Maj + Échap »,
  « Windows + Maj + Gauche », « Ctrl + Alt + Maj + F12 »…, sans parenthèses) ; le desktop (`KeyMacroManager`) garde
  « Alt+F4 », « Win+D (afficher le bureau) »… et doit suivre. Sous-menus du menu en jeu Android au style du prototype
  (« Menu du stream », « Envoyées au PC comme si tu les tapais là-bas. », « Accéder aux commandes de contrôle du PC
  hôte », « Commande envoyée : … ») : à reprendre au desktop avec son menu en tuiles.

- **Écart ouvert (2026-11-26, Android, Réglages V8)** : textes choisis appliqués côté Android, à reprendre au desktop :
  description du débit « Suit la résolution et les images/s tant que tu ne le changes pas. » (choix 49, le desktop dit
  encore « Baisse-le si le réseau est lent… »), valeur auto du codec « Automatique (HEVC) » (choix 55), « Revenir au
  débit recommandé » qui dit « Le débit suit déjà la résolution et les images par seconde. » tant qu'il suit, Thème
  « Change l’allure de toute l’appli. » (choix 101), Langue « Prend effet au redémarrage. » / « Automatique
  (français) » (choix 103-104), « N réglages avancés masqués · activer le mode expert », toasts « Mode expert activé :
  tous les réglages sont visibles » / « Mode expert désactivé », en-tête « Thème {nom} · LavArtemis {version} »,
  LB / RB = page précédente / suivante.

## 4. Ce qui reste

### À coder (taille raisonnable, pas fait dans ce passage)

Rien hors portage du prototype.

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
