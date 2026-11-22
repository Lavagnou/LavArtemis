# Résolution et fréquence calées sur l'écran du PC — design

Touche **trois dépôts** : LavApollo (hôte), LavArtemis Android, LavArtemis-Qt (client desktop).

## Intention

Lavagnou streame le même PC depuis plusieurs appareils de formats différents, souvent en
**rejoignant une session déjà lancée** par un autre appareil :

- session lancée depuis son **PC portable** (écran virtuel 1920×1080, 16:9, 60 Hz), puis rejointe
  depuis son **Pixel 7** (2400×1080, 20:9, réglé à 90 i/s) ;
- ou l'inverse : session lancée depuis le **Pixel 7** (écran virtuel 2400×1080, 20:9), rejointe depuis
  le **portable** (réglage de streaming 1920×1080) ;
- ou **sans écran virtuel** (option désactivée sur le client) : c'est son vrai écran 1920×1080 qui est
  streamé.

Aujourd'hui chaque client demande **sa** résolution et **sa** fréquence. Rejoindre une session ne recrée
pas l'écran virtuel (`/resume` ne reconstruit pas les écrans), donc l'hôte encode un écran d'un format
dans une image d'un autre : il ajoute des **bandes noires dans la vidéo** (vu en 21.6.3 : bureau 16:9
dans un stream 20:9), et un client à 90 i/s se cale sur des images qui n'existent pas (l'écran du PC n'en
produit que 60).

## La règle

Quand le client **ne crée pas lui-même l'écran** — il **rejoint une session en cours**, ou il **lance
sans écran virtuel** — il cale le stream sur l'écran que le PC affiche réellement :

- **Résolution** = le format de l'écran du PC, à la **plus grande taille qui tient à la fois** dans la
  résolution de streaming réglée sur le client **et** dans la résolution de l'écran du PC (jamais
  d'agrandissement au-delà de l'écran du PC). Dimensions arrondies vers le bas à un nombre pair.
- **Fréquence** = la **plus basse** entre la fréquence réglée sur le client et la fréquence actuelle de
  l'écran du PC (arrondie à l'entier ; 59,94 → 60).

Exemples de référence (à reprendre tels quels dans les tests des deux clients) :

| Écran du PC | Réglage client | Stream |
|---|---|---|
| 1920×1080 @ 60 | 2400×1080 @ 90 | **1920×1080 @ 60** |
| 2400×1080 @ 90 | 1920×1080 @ 60 | **1920×864 @ 60** |
| 2400×1080 @ 90 | 1920×1080 @ 120 | **1920×864 @ 90** |
| 1280×720 @ 60 | 1920×1080 @ 60 | **1280×720 @ 60** (pas d'agrandissement) |
| 3840×2160 @ 60 | 1920×1080 @ 60 | **1920×1080 @ 60** |

Quand le client **crée l'écran** (lancement d'une nouvelle session avec écran virtuel), rien ne change :
l'écran virtuel est taillé pour lui.

## Objectifs / non-objectifs / anti-objectifs

- **Objectifs** : plus de bandes noires ajoutées par l'hôte quand on rejoint une session ou qu'on streame
  le vrai écran ; jamais plus de pixels ni d'images que l'écran du PC n'en produit ; jamais plus que ce
  que le client a réglé.
- **Non-objectifs** : changer la résolution de l'écran du PC ; recréer l'écran virtuel en rejoignant ;
  le multi-écran émulé (desktop, `displayLayout`) — il garde son propre calcul de toile.
- **Anti-objectifs** : un client qui *perd* une fonction avec un hôte non-LavApollo (sans l'information,
  comportement **identique à aujourd'hui**) ; un stream plus grand que le réglage du client (débit,
  décodeur) ; une image déformée (le format est toujours celui de l'écran du PC).

## Approches

1. **LavApollo annonce l'écran en cours dans `serverinfo`, chaque client applique la règle** — ✅ la
   seule sensée. Ajout de champs, rétrocompatible, même mécanisme que `MicPassthroughState` /
   `MultiDisplayCapable`.
2. Deviner côté client — impossible : l'hôte ne publie la taille de son écran nulle part.
3. Laisser l'hôte rogner/étirer — c'est précisément ce qui produit les bandes noires.

## Comportement décidé

### Hôte (LavApollo)

- `serverinfo` (clients appairés) ajoute **`CurrentDisplayWidth`**, **`CurrentDisplayHeight`**,
  **`CurrentDisplayRefreshRate`** : le mode actuel de l'écran **que le stream capture** — l'écran de la
  session en cours (écran virtuel ou physique) s'il y en a une, sinon l'écran qu'une session **sans**
  écran virtuel capturerait (`output_name` configuré, sinon l'écran principal). Absents si l'hôte ne sait
  pas répondre.
- ⚠️ `serverinfo` tourne sur le thread HTTPS : la lecture du mode doit être immédiate (pas d'attente sur
  un appel d'affichage bloquant — cf. les leçons du `recovery::stop()` et de `MicPassthroughState` dans
  `LavApollo/CLAUDE.md`). Un instantané rafraîchi en arrière-plan ou une lecture `EnumDisplaySettings`
  directe conviennent ; pas de verrou partagé avec la capture.
- Multi-écran émulé (`+`-joined) : la **toile** (bounding box) fait office d'écran.
- Écran virtuel VRR à 1000 Hz : annoncer 1000 → la règle `min()` garde la fréquence du client.

### Clients (Android et desktop, même règle)

- La règle est une **fonction pure** partagée en esprit (Java `utils/…`, C++ `backend/…`), testée avec
  le tableau de référence ci-dessus dans les deux dépôts.
- **Quand l'appliquer** :
  - en **rejoignant** une session en cours (l'app lancée est celle que `serverinfo` donne en
    `currentgame`) — **même si l'écran virtuel est coché** sur le client ;
  - en **lançant sans écran virtuel** (option du client désactivée, ou hôte sans écran virtuel).
  - Pas au lancement avec écran virtuel.
- La résolution et la fréquence calculées remplacent celles des réglages **pour ce stream seulement**
  (en mémoire, jamais écrites dans les préférences ni le profil).
- Tout ce qui dépend de la taille du stream (format de la vue / bandes du client, zoom au pincement,
  `TrackedCursor`, rotation, multi-écran desktop) doit lire la **taille effective**, pas les préférences.
- Sans les champs (hôte non-LavApollo, ancien LavApollo) : comportement actuel, inchangé.
- Benchmark : un run qui *lance* l'app Benchmark crée son écran (pas concerné) ; un run qui *reprend*
  l'app Benchmark est concerné — la config effective doit refléter la taille réellement streamée.

### Suivi en cours de session (résolution et fréquence dynamiques)

La résolution et la fréquence de l'écran du PC peuvent changer **pendant** la session (réglages
d'affichage de Windows). Le protocole fixe la taille du stream à la connexion et n'a aucun message pour la
changer en cours de route ; l'hôte adapte alors sa capture mais encode toujours à l'ancienne taille
(bandes ou image déformée).

**Approche choisie : reconnexion automatique.** (Écartée : changement de taille à chaud — nouveau message
dans le `moonlight-common-c` partagé + reconfiguration à chaud de l'encodeur et des deux décodeurs, pour
gagner 1-2 s sur un événement rare.)

- Pendant le stream, le client **relit `serverinfo` toutes les 2-3 s** (hors thread de rendu ; même requête
  que la reconnexion normale) et applique la règle aux `CurrentDisplay*` reçus.
- Il compare la **cible calculée** à la taille/fréquence **du stream en cours** — jamais l'écran du PC à
  la taille du stream : un écran 2400×1080 streamé en 1920×864 est un état **stable**, pas un écart à
  corriger. Sinon, boucle de reconnexions.
- Écart sur **deux lectures consécutives** (anti-rebond : Windows passe par des modes intermédiaires),
  puis **reconnexion à la même session** (`/resume`, qui ne recrée pas l'écran virtuel) avec la nouvelle
  taille/fréquence. Coupure attendue : 1-2 s.
- **S'applique à toutes les sessions**, y compris celles que le client a lancées avec son propre écran
  virtuel : si l'utilisateur change la résolution de cet écran dans Windows, c'est voulu. *(call)*
- **Pas pendant un run de benchmark** : une reconnexion fausserait la mesure ; le run garde sa taille et
  la note en `note` s'il voit un écart. *(call)*
- La reconnexion passe par le même chemin d'arrêt que d'habitude : touche du bouton vocal relâchée, micro
  arrêté avant la connexion, comme aujourd'hui (`stopConnection()` / `DeferredSessionCleanupTask`).
- Sans les champs (hôte non-LavApollo) : pas de relecture, rien ne change.

## Relecture « builder » (faite par Claude, le relecteur indépendant ayant été refusé)

Points tranchés en relisant le document comme celui qui doit le construire :

- **« Rejoindre une session »** = le chemin `/resume` : l'app demandée est celle que `serverinfo` donne
  en `currentgame`. Une autre app en cours suit le chemin habituel (on propose de la quitter).
- **Hôte qui force un écran virtuel** (`always_use_virtual_display` ou réglage par app) alors que le client
  n'en demande pas : l'écran est créé à la taille demandée par le client au lancement ; la règle avant
  lancement lit l'écran qui *serait* capturé, la relecture en cours de session corrige si besoin. *(call)*
- **Android** : la taille effective n'est connue qu'une fois `serverinfo` lu par `NvConnection`, alors que
  `Game` fixe aujourd'hui format de vue et dépendances depuis `prefConfig` dans `onCreate`. Le builder
  doit faire circuler la **taille effective** vers tout ce qui en dépend (format de la vue, zoom, curseur,
  rotation) avant le premier rendu, et à chaque reconnexion.
- **Desktop** : même point dans `Session::initialize()` (`m_StreamConfig.width/height/fps` depuis les
  préférences) ; le multi-écran émulé garde son calcul de toile et n'applique pas la règle.

## Laissé au builder

- Où lire le mode de l'écran capturé côté LavApollo (`proc` / `VDISPLAY` / libdisplaydevice) et comment
  le garder non bloquant.
- Où, côté Android, la taille effective doit être connue avant la configuration du décodeur et du
  format de la vue (la résolution est aujourd'hui figée dans `Game.onCreate` via `prefConfig`, alors que
  `serverinfo` est relu par `NvConnection` au démarrage) ; idem côté desktop (`Session::initialize`).
- Les noms exacts des champs, tant qu'ils restent préfixés `CurrentDisplay` et documentés dans
  `LavApollo/CLAUDE.md` + les deux `CLAUDE.md` clients.
