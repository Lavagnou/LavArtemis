# Banc de test desktop

De vrais clients contre de vrais hôtes, sans toucher au système : des LavApollo en mode « hôte de test »
(faux PC isolés) et le build de test de LavArtemis-Qt (`LavArtemisTest.exe`), piloté par son canal de commande.
Windows seulement. Spec : `docs/superpowers/specs/2026-11-24-testbench-design.md`.

## Lancer

```powershell
pip install -r tools\testbench\requirements.txt          # pytest, Pillow (une fois)
python tools\testbench\testbench.py run                    # toute la suite desktop
python tools\testbench\testbench.py run -k stream          # un scénario (nom ou mot du nom)
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
de chaque hôte, journal du client, chronologie du premier plan). `--report-dir` choisit un autre dossier.

Un **bug produit connu** fait passer son scénario en *expected failure* (`XFAIL`), reconnu à sa signature et avec
la cause (`scenarios/bugs.py`) ; une fois corrigé, son entrée et son `xfail` sont retirés : le scénario doit passer.

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
- **Premier plan** : les fenêtres sont visibles mais ne prennent jamais le focus. Un chien de garde relève la
  fenêtre au premier plan toutes les 50 ms : un scénario échoue si elle appartient un instant au client ou à un hôte
  de test. On peut continuer à travailler pendant un run ; un changement de premier plan vers une de ses propres
  fenêtres est noté dans `logs/<scénario>/foreground.txt`, pas compté contre le client.

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
