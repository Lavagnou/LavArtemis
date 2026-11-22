# Zoom au pincement en mode trackpad (Android) — design

## Intention

Sur le téléphone de Lavagnou, l'image du PC est petite. Il veut zoomer comme dans **Chrome Remote
Desktop (Android)** : pincer à deux doigts zoome/dézoome, et la vue **suit le curseur de la souris
toute seule**. Pas de déplacement manuel de la vue : quand le curseur s'approche du bord, la vue glisse
pour le garder dans un cadre central. Usage : bureau / applis, piloté au trackpad, sur son téléphone
uniquement — d'où une option **désactivée par défaut**.

## Objectifs

- Pincer à deux doigts zoome entre **1×** (plein écran) et **10×**, en mode trackpad.
- La vue suit le curseur : zone calme = **moitié centrale** de l'écran dans chaque axe.
- Le curseur est **toujours exactement** là où la vue croit qu'il est.
- En mode tactile, pas de zoom au pincement ; une image zoomée revient en plein écran.

## Non-objectifs

- Déplacer la vue à deux doigts (c'était une erreur de la demande initiale ; CRD ne le fait pas).
- Écran externe (`ExternalDisplayControlActivity`) et client desktop.
- Jeux à caméra souris (FPS) : non supportés quand l'option est active (voir Approches).
- Toucher au **mode zoom existant** du menu en jeu (`toggleZoomMode`, `isPanZoomMode`) : il reste un
  outil à part, inchangé.

## Anti-objectifs (échec même si « ça marche »)

- Une vue qui suit un curseur **fantôme** (position estimée qui dérive du vrai curseur du PC).
- Un défilement à deux doigts ou un clic droit à deux doigts qui se déclenchent pendant un pincement,
  ou un pincement qui vole un défilement.
- Une vue qui tremble ou se recentre à chaque petit mouvement du curseur.
- Une image qui reste zoomée en mode tactile (on viserait à côté de ce qu'on touche).

## Contraintes

- Le moteur de zoom existe : `utils/PanZoomHandler.java` (échelle + translation de la `SurfaceView`,
  pivot (0,0), `constrainToBounds()`, `MAX_SCALE = 10`). ⚠️ Le relecteur signale que
  `getNormalizedCoordinates(streamContainer, …)` (`Game.java` ~l. 2730) lit l'échelle du **conteneur**,
  pas de la `SurfaceView` zoomée : le mapping des touchers absolus ne suivrait donc pas le zoom
  existant. Non vérifié sur appareil, **hors périmètre** (voir « Calls ») : la fonction ne s'appuie pas
  dessus, la position du curseur à l'écran se calcule depuis la `SurfaceView`.
- Modes souris (`mouse_mode_list`) : 0 multitouch, 1 toucher absolu, 2 trackpad naturel
  (`TrackpadContext`), 3 trackpad gaming (`RelativeTouchContext`), 4 désactivé, 5 toucher absolu
  inversé. Contextes créés dans `Game` (~l. 4450) ; le mode peut changer **pendant le stream**
  (`Game.selectMouseMode()`).
- Aujourd'hui les deux trackpads envoient des **mouvements relatifs** (`sendMouseMove`) ; seul le
  gaming sait déjà envoyer une position absolue avec l'option existante `checkbox_absolute_mouse_mode`
  (`sendMouseMoveAsMousePosition`, dont la position courante vit dans `moonlight-common-c`).
- Java uniquement côté app ; pas de changement de protocole ni de LavApollo.

## Approches considérées

1. **L'appli tient le curseur** (position absolue envoyée au PC) — ✅ **choisie par Lavagnou.**
   Position toujours exacte, rien à changer côté hôte. Coût : plus d'accélération Windows (la
   sensibilité de l'appli fait foi) et jeux à caméra souris mal servis — acceptable, option opt-in.
2. **Estimer la position en cumulant les mouvements relatifs** — écartée : l'accélération de Windows
   (« Améliorer la précision du pointeur », active par défaut) fait dériver l'estimation → curseur
   fantôme.
3. **LavApollo envoie la position du curseur** — exacte même quand un programme bouge le curseur, mais
   demande un nouveau message (hôte + `moonlight-common-c` partagé + Android) et ne marche qu'avec
   LavApollo. Trop gros pour le besoin.

## Comportement décidé

**Réglage** `checkbox_trackpad_pinch_zoom` (défaut **off**), dans *Input Settings* à côté du mode
souris : « Zoom au pincement (trackpad) ».

**Mode trackpad (2 et 3) avec l'option active**
- **Curseur tenu par l'appli** : une position (x, y) en pixels du stream, bornée à l'image. Au début
  du stream elle vaut le **centre** ; le curseur du PC y saute au premier mouvement (comme CRD). Chaque
  mouvement du doigt met à jour la position et l'envoie en **absolu** (`sendMousePosition`). Les
  boutons, défilements et clics restent ceux du mode.
- **Sensibilité** : à 1×, chaque mode garde **exactement sa vitesse actuelle** à l'écran (le trackpad
  naturel tactile est construit par `new TrackpadContext(conn, i)`, donc vitesse fixe 1.0 — les curseurs
  `trackpadSensitivityX/Y` ne concernent que les touchpads physiques et restent hors du coup ; gaming :
  `touchPadSensitivity` / `touchPadYSensitity`). Cette vitesse est **divisée par le zoom courant** : un
  même geste déplace le curseur de la même distance *à l'écran*, donc plus finement dans l'image.
- **Pincement** : zoom autour du point milieu des doigts, borné à [1×, 10×].
  Arbitrage avec les gestes à deux doigts existants : si l'écart entre les deux doigts change
  **nettement** au début du geste, le geste devient un zoom pour toute sa durée (aucun défilement ni
  clic droit envoyé) ; sinon il reste le défilement / clic droit habituel pour toute sa durée.
- **Suivi** : **uniquement quand le curseur bouge** (« la vue suit la souris ») — jamais pendant ni
  juste après un pincement. On peut donc pincer pour regarder une zone loin du curseur ; dès que le
  curseur bouge, la vue le rattrape. Si la position du curseur **à l'écran** sort de la zone calme
  (moitié centrale de l'écran dans chaque axe), la vue glisse **juste assez** pour la ramener sur le bord
  de la zone — pas de recentrage. La vue ne dépasse jamais les bords de l'image (`constrainToBounds`).
  À 1× rien ne bouge. La règle vaut pour **tout zoom > 1×**, d'où qu'il vienne (pincement, mode zoom du
  menu, zoom retenu) : un seul comportement, pas d'origine du zoom à suivre.
- **Autres sources qui bougent le curseur du PC** :
  - un toucher absolu (mode tactile) met à jour la position tenue par l'appli : en revenant au trackpad,
    on repart de là où le doigt a laissé le curseur ;
  - l'option `checkbox_absolute_mouse_mode` du gaming est **supplantée** par celle-ci quand les deux
    sont actives (un seul propriétaire de la position) ;
  - souris / touchpad **physiques** branchés au téléphone : hors périmètre (le PC bouge, l'appli ne le
    sait pas ; le prochain geste au doigt fait sauter le curseur à la position tenue). Documenté, pas
    traité : ce n'est pas l'usage visé.

**Mode tactile (0, 1, 5) ou désactivé (4)**
- Pas de zoom au pincement (les deux doigts gardent leur sens actuel).
- Quand on **entre** dans ces modes avec l'option active (au début du stream ou en changeant de mode
  souris), l'image revient en plein écran (1×), quelle que soit l'origine du zoom (zoom retenu compris).
  Le mode zoom du menu reste utilisable ensuite : c'est un geste explicite.

**Option désactivée** : tout se comporte exactement comme aujourd'hui (mouvements relatifs, pas de
pincement en trackpad).

> **Mis à jour le 2026-11-22** : la règle de vitesse est remplacée par « même distance que le doigt à
> l'écran » (`2026-11-22-rotation-and-trackpad-speed-design.md`), qui contient la division par le zoom.

## Calls faits par Claude (validés en conversation)

- Mode zoom existant du menu inchangé.
- Curseur de départ au centre.
- Vitesse du curseur divisée par le zoom.
- Arbitrage pincement / défilement au début du geste.
- Zone calme = moitié centrale ; glissement minimal, sans recentrage.

## Calls faits après relecture (à vérifier par Lavagnou)

- Le suivi ne se déclenche **que sur un mouvement du curseur**, jamais sur un pincement.
- Le suivi s'applique à **tout** zoom > 1×, quelle que soit son origine.
- Entrée en mode tactile : retour à 1× pour **tout** zoom, mode zoom du menu toujours utilisable après.
- Vitesse à 1× **identique à aujourd'hui** (le trackpad naturel tactile ignore les curseurs de
  sensibilité, qui concernent les touchpads physiques).
- Un toucher absolu resynchronise la position tenue ; l'option « souris absolue » du gaming est
  supplantée ; souris/touchpad physiques hors périmètre.
- Le possible défaut de mapping des touchers sous le mode zoom existant est **hors périmètre** :
  signalé, pas corrigé ici.

## Laissé au builder

- Le seuil exact de « l'écart change nettement » (tests à la main sur appareil).
- Comment partager le zoom / la translation avec `PanZoomHandler` (l'étendre ou l'encapsuler), tant
  que le mode zoom existant garde son comportement.
- Interaction avec l'option existante « retenir le zoom » (`checkbox_remember_zoom_pan`) : la garder
  cohérente, sans comportement nouveau.
- Les maths (zone calme, glissement, vitesse divisée par le zoom, bornage) sont en Java pur et doivent
  être testées en JVM ; l'arbitrage pincement/défilement aussi si possible.
