# Banc de test desktop

De vrais clients contre de vrais hôtes, sans toucher au système : des LavApollo en mode « hôte de test »
(faux PC isolés) et le build de test de LavArtemis-Qt (`LavArtemisTest.exe`), piloté par son canal de commande.
Windows seulement. Spec : `docs/superpowers/specs/2026-11-24-testbench-design.md`.

## Lancer

```powershell
pip install -r tools\testbench\requirements.txt          # pytest, Pillow (une fois)
python tools\testbench\testbench.py run                    # toute la suite desktop
python tools\testbench\testbench.py run -k stream          # un scénario (nom ou mot du nom)
python tools\testbench\testbench.py run --rerun-failed-once  # porte de release : un échec est rejoué une fois
python -m pytest tools\testbench\tests -q                  # tests du banc lui-même
```

Il faut les dépôts `LavApollo` et `LavArtemis-Qt` à côté de celui-ci (ou `LAV_TESTBENCH_LAVAPOLLO` /
`LAV_TESTBENCH_QT`). Le banc reconstruit ce qui est périmé : `sunshine` du build `build/t` de LavApollo
(`ninja -j <LAV_TESTBENCH_JOBS, défaut 1>`) et le client (`scripts\build-test.bat`, `LAV_TEST_JOBS`, défaut 2).
Un build tué faute de mémoire n'est pas relancé : libérer de la mémoire et relancer.

## Rapport

`tools/testbench/testbench-reports/<date>_<heure>/` (hors git : captures et journaux peuvent porter des noms de
machine) : `report.md` / `report.json` (résultat de chaque scénario, durée, raison d'un échec attendu, commits de
LavArtemis, LavApollo et LavArtemis-Qt utilisés, modifications non commitées signalées), `captures/<scénario>/`
(PNG des fenêtres, celles du stream par PrintWindow), `logs/<scénario>/` (`sunshine.log` et `testhost/*.jsonl`
de chaque hôte, le journal de **chaque** lancement du client — `client-1-reset.log` pour celui qui efface
l'organisation, `client-N-args.log` pour un lancement avec un lien —, notes et chronologie du premier plan).
`--report-dir` choisit un autre dossier.

La colonne *Note* de `report.md` signale toute **reprise** (`RETRY` : une lecture d'image refaite, un clic qui attendait
la fermeture d'une popup, un PIN redonné à l'hôte qui ne l'attendait pas encore) et tout passage du premier plan à
**aucune fenêtre** ou à une **boîte de plantage** (WerFault). `--rerun-failed-once` rejoue une fois chaque scénario
en échec, dans le même rapport (tentative 2, `captures/` et `logs/<scénario>-rerun`) ; un scénario qui ne passe
qu'au rejeu est marqué **FLAKE**, jamais masqué.

**Code de sortie de `run`** (la porte de release s'arrête sur tout code non nul et dit lequel) :

| Code | Sens |
|---|---|
| 0 | chaque scénario a passé **du premier coup** (un `xfail` attendu compte comme passé) |
| 1 | un scénario échoue (après son rejeu s'il y en a eu un), ou pytest a échoué sans résultat de scénario (collecte, interruption) |
| 3 | **FLAKY** : plus rien n'échoue, mais un scénario n'a passé qu'au rejeu : une instabilité à regarder, pas à livrer |

En fin de run, un bloc récapitulatif sur la sortie standard liste `FAIL: <scénario>` / `FLAKE: <scénario>` et
`RESULT: PASSED|FAILED|FLAKY (exit n)`, en plus de `report.md`.

Aucun bug produit connu n'est ouvert : chaque scénario doit passer. Un bug connu non corrigé passerait son scénario
en *expected failure* (`pytest.xfail`) sur sa **signature exacte** seulement, avec sa cause ; corrigé, le `xfail` part.

⚠️ **Écran allumé.** Quand Windows éteint l'écran (inactivité, 15 min ici), Qt Quick ne dessine plus la fenêtre du
client de test : plus de mise en page (`polish`) : les cartes de l'accueil restent toutes au même coin (vérifié pendant
plusieurs secondes), et les scénarios qui cliquent échouent (« covered », « no stream », ordre des
cartes) sans que l'appli y soit pour rien (vu le 2026-11-25 : la suite passe écran allumé ; écran éteint, la moitié des scénarios échouent ainsi).
Lancer la suite écran allumé ; une capture (`screenshot`) force un rendu, ce qui peut masquer le problème.

## Ce que le banc touche, et ce qu'il ne touche jamais

- **Écrit** : `%TEMP%\lavtestbench-run\<scénario>\` (un faux PC par dossier : sa config, ses applis, sa
  bibliothèque Playnite et ses jaquettes générées, ses journaux, son `ProgramData`), l'organisation Qt
  **« LavArtemis Test »** (registre `HKCU\Software\LavArtemis Test`, dossiers `LavArtemis Test`), effacée au début
  de chaque scénario, et le dossier du rapport.
- **Ports** : 127.0.0.1, 40000-44999. Le vrai LavApollo de ce PC (base 45989) n'est ni contacté, ni arrêté, ni
  configuré ; le client de test refuse toute autre adresse avant la moindre requête (scénario 8).
- **Jamais** : les réglages de « LavArtemis » / « LavArtemis Dev », le service `ApolloService` et sa config, un
  vrai hôte, une installation, un gestionnaire de liens `art://` (le lien passe en argument de l'exe), le vrai
  pointeur ou le vrai clavier (seules les entrées injectées par le canal atteignent l'hôte), le son, le vrai micro.
- **Processus** : seuls ceux que le banc a lancés sont arrêtés (chemin de l'exe vérifié), à la fin de chaque
  scénario, échec compris ; un run interrompu est nettoyé au suivant (fichiers de PID).
- **Premier plan** : les fenêtres sont visibles mais ne prennent jamais le focus. Un crochet
  `SetWinEventHook(EVENT_SYSTEM_FOREGROUND)` (hors contexte, sur un thread avec sa boucle de messages) reçoit les
  changements de premier plan que Windows annonce (un passage à « aucune fenêtre » n'en produit pas toujours un : le
  premier plan est donc aussi relu à la fin du scénario) : un scénario échoue si l'un d'eux va à une fenêtre du client
  ou d'un hôte de test (reconnue au chemin de l'exe, quel que soit le lancement). On peut continuer à travailler
  pendant un run ; un changement vers une de ses propres fenêtres est noté dans `logs/<scénario>/foreground.txt`, pas
  compté contre le client ; aucune fenêtre au premier plan (pendant ou à la fin) ou WerFault est signalé dans
  `report.md`.
- **Restes** : à la fin de chaque scénario, tout `sunshine.exe` de test dont la ligne de commande vise le dossier du
  scénario et tout `LavArtemisTest.exe` de ce build encore lancés sont tués **et** font échouer le scénario (cause à
  chercher) ; un `LavArtemisTest.exe` d'un autre build ou illisible, ou un PID enregistré vivant mais méconnaissable,
  n'est jamais tué et fait aussi échouer. Le client meurt pendant le
  scénario : échec, avec son code de sortie. Deux clients à la fois : refusé au lancement.

## Scénarios (`scenarios/`)

| # | Fichier | Vérifie |
|---|---|---|
| 1 | `test_01_pairing.py` | ajout par adresse + PIN lu à l'écran donné à l'hôte ; lien `art://…&passphrase=` (OTP) sans PIN |
| 2 | `test_02_home.py` | cartes Bureau puis Playnite Fullscreen, jaquettes, ordre de `library.json`, Masquer / réafficher |
| 3 | `test_03_hosts.py` | Mes PC : PC éteint, version de LavApollo trop ancienne, renommer, oublier |
| 4 | `test_04_settings.py` | chaque page des Réglages en normal et en expert, une écriture relue après redémarrage |
| 5 | `test_05_stream.py` | stream de Bureau : taille négociée, images décodées et numérotées, touches du menu en jeu, micro, quitter |
| 6 | `test_06_playnite.py` | jeu Playnite pendant que Playnite tourne : `resume`, `playnite_launch`, « Quitter <jeu> » |
| 7 | `test_07_this_pc.py` | « Impossible de streamer … depuis lui-même », aucune session |
| 8 | `test_08_guards.py` | 192.168.1.231, 127.0.0.1:45989, 127.0.0.1:47989 refusés, aucune requête au journal |

Les streams restent légers (1280×720 à 30 i/s, H.264 logiciel côté hôte) et courts.
