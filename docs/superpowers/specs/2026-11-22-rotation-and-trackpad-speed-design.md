# Rotation qui suit le téléphone + trackpad à la vitesse du doigt (Android) — design court

Deux retours de Lavagnou après avoir utilisé le zoom au pincement (21.5.0) sur son téléphone, façon
Chrome Remote Desktop.

## 1. Suivre la rotation du téléphone

**Intention.** Tenir le téléphone en portrait ou en paysage, et le tourner pendant le stream, sans que
l'écran du PC change : le PC reste en paysage, seul l'affichage tourne. En portrait, l'image paysage est
ajustée à la largeur (bandes noires dessus/dessous) ; le zoom au pincement permet d'agrandir.

**Approches.**
1. **Nouveau réglage « Suivre la rotation du téléphone »** — ✅ **choisi**, **désactivé par défaut**,
   activable dans les réglages (demande explicite).
2. Changer le sens d'« Orientation automatique » selon l'état d'« Inverser la résolution » — écarté :
   un réglage dont le sens dépend d'un autre devient illisible.

**Comportement.**
- Réglage `checkbox_follow_device_rotation` (défaut **off**), à côté d'« Orientation automatique » dans
  les réglages vidéo.
- Actif : l'activité suit l'orientation du téléphone pendant tout le stream
  (`SCREEN_ORIENTATION_FULL_USER` : **le verrouillage de rotation d'Android est respecté**). La
  résolution demandée au PC **ne change jamais** (pas d'inversion, même si « Inverser la résolution »
  est cochée), y compris au lancement en portrait.
- En portrait, l'image est **toujours ajustée** (pas d'étirement), même en mode d'échelle « Étirer » :
  une image paysage étirée en hauteur serait illisible. Retour en paysage = mode d'échelle choisi.
- La rotation ne relance rien : l'activité gère déjà le changement d'orientation sans être recréée ;
  la vue du stream, le zoom (`PanZoomHandler.handleSurfaceChange`) et le mapping des touchers suivent
  la nouvelle taille.
- *(call)* « Pivoter l'écran » du menu en jeu est **masqué** quand le réglage est actif (c'est le
  téléphone qui décide).
- Hors périmètre : écran externe (son orientation est déjà forcée en paysage).

## 2. Trackpad à la vitesse du doigt

**Intention.** Le doigt et le curseur parcourent **la même distance à l'écran**.

**Comportement** (une seule façon sensée).
- Quand le **zoom au pincement** est activé (l'appli tient le curseur) : un déplacement du doigt de
  `d` pixels écran déplace le curseur de `d` pixels **à l'écran**, quel que soit le zoom et
  l'orientation : en pixels du stream, `d × largeurStream / (largeurVue × zoom)`.
  - Naturel : **sans l'accélération** actuelle (`cbrt(magnitude / 8)` dans `TrackpadContext`).
  - Gaming : la sensibilité (`touchPadSensitivity` / `touchPadYSensitity`, défaut 100) reste un
    **multiplicateur** : 100 % = 1:1.
- Option désactivée : **rien ne change** (mouvements relatifs, accélération Windows — on ne peut
  qu'approcher le 1:1 sans tenir le curseur).
- Remplace la règle « vitesse divisée par le zoom » du design du zoom au pincement, qu'elle contient.

## Laissé au builder

- Où exactement brancher le suivi de rotation dans `Game.setPreferredOrientationForActivity()` et le
  forçage de l'ajustement en portrait (vue du stream / `StreamView`).
- Comment faire passer les deltas bruts du doigt jusqu'au `TrackpadZoomController` (le `MouseMover`
  reçoit aujourd'hui des deltas déjà accélérés / mis à l'échelle de référence).
- Les maths de conversion sont en Java pur et testées en JVM.
