# Interface v2 Android — carte du code existant (pour l'implémentation)

Chemins relatifs à `app/src/main/java/com/limelight/` sauf mention. Relevé du 2026-11-23, branche `dev`.

## Hôtes (`PcView.java`)
- Launcher `.PcView` (manifest :93-108, LAUNCHER + LEANBACK + MULTIWINDOW), thème `@style/AppTheme`.
- Binding : `bindService(new Intent(this, ComputerManagerService.class), serviceConnection, BIND_AUTO_CREATE)` (:282) ;
  `onServiceConnected` (:85) → thread : `localBinder.waitForReady()`, garde `managerBinder`, `startComputerUpdates()`.
- Mises à jour : `managerBinder.startPolling(new ComputerManagerListener(){ notifyComputerUpdated(ComputerDetails) })` (:335),
  rappel sur un thread de fond → `runOnUiThread`, fusion par `uuid` (:909). `startPolling` dans `onResume`, `stopPolling()` dans `onPause` (:371).
- Binder (`computers/ComputerManagerService.java`) : `startPolling` :197, `waitForReady` :227, `addComputerBlocking(ComputerDetails)` :260,
  `removeComputer` :264, `stopPolling` :268, `createAppListPoller(ComputerDetails)` :273 (`ApplistPoller.start/stop/pollNow`),
  `getUniqueId` :277, `getComputer(uuid)` :281, `invalidateStateForComputer(uuid)` :293. Renommage ajouté en phase 2a :
  `renameComputer(uuid, nom)`, nom choisi dans les prefs `computers/HostNames`, réappliqué après chaque poll.
- `nvstream/http/ComputerDetails` : `uuid, name`, adresses `localAddress/remoteAddress/manualAddress/ipv6Address` (`AddressTuple{address,port}`),
  `macAddress`, `serverCert`, `state` (ONLINE/OFFLINE/UNKNOWN), `activeAddress`, `httpsPort`, `pairState`, `runningGameId`,
  `runningGameUUID`, `rawAppList`, `nvidiaServer`, `vDisplaySupported/vDisplayDriverReady`, `serverCommands`,
  `benchmarkAppId/Uuid`, `playniteHostRunning`, `playniteGameName`.
- Appairage `doPair` (:515, thread) : `stopComputerUpdates(true)` ; `new NvHTTP(ServerHelper.getCurrentAddressFromComputer(c), c.httpsPort,
  managerBinder.getUniqueId(), c.serverCert, PlatformBinding.getCryptoProvider(ctx))` ; `getPairState()` ; PIN `PairingManager.generatePinString()` ;
  `httpConn.getPairingManager().pair(httpConn.getServerInfo(true), pin, passphrase)` → PIN_WRONG/FAILED/ALREADY_IN_PROGRESS/PAIRED ;
  si PAIRED : `managerBinder.getComputer(uuid).serverCert = pm.getPairedCert()`, `invalidateStateForComputer(uuid)` ; puis relancer les mises à jour.
- Réveil `doWakeOnLan` (:674) : hors ligne + `macAddress != null` → `WakeOnLanSender.sendWolPacket(computer)` (thread, IOException).
- Suppression `removeComputer` (:877) : `managerBinder.removeComputer(d)`, `new DiskAssetLoader(ctx).deleteAssetsForComputer(uuid)`,
  retirer la clé `uuid` des prefs `AppView.HIDDEN_APPS_PREF_FILENAME`, `shortcutHelper.disableComputerShortcut(...)`.
- Ajout manuel : `preferences/AddComputerManually` → `details.manualAddress = new AddressTuple(host, port)` puis
  `managerBinder.addComputerBlocking(details)` (thread).
- Benchmark : `new Intent(ctx, BenchmarkActivity.class)` + `EXTRA_PC_UUID`, `EXTRA_PC_NAME` (:853).
- Reprendre : `ServerHelper.doStart(this, new NvApp("app", null, d.runningGameId, false), d, managerBinder, false)` ;
  quitter : `ServerHelper.doQuit(this, d, new NvApp("app", null, 0, false), managerBinder, null)`.

## Applis (`AppView.java`)
- Liste : `NvHTTP.getAppListByReader(new StringReader(details.rawAppList))` (statique, `LinkedList<NvApp>`), mise à jour par le poller
  (`managerBinder.createAppListPoller(computer).start()`) ; cache `CacheHelper.readInputStreamToString(CacheHelper.openCacheFileForInput(getCacheDir(), "applist", uuid))`.
- `nvstream/http/NvApp` : `getAppName()`, `getAppId()`, `getAppUUID()`, `getAppIndex()`, `isPlayniteGame()`, `isPlayniteHost()`.
- Jeux masqués : `getSharedPreferences("HiddenApps", MODE_PRIVATE)`, clé = uuid du PC, valeur = `StringSet` d'appIds.
- Jaquettes : `new CachedAppAssetLoader(ComputerDetails, double scalingDivider, new NetworkAssetLoader(ctx, uniqueId), new MemoryAssetLoader(),
  new DiskAssetLoader(ctx), Bitmap noAppImage)` (exemple `grid/AppGridAdapter.java:95-114`), `populateImageView(NvApp, ImageView, TextView)`,
  `cancelForegroundLoads/BackgroundLoads`, `freeCacheMemory`.
- Lancer : `ServerHelper.doStart(Activity, NvApp, ComputerDetails, ComputerManagerBinder, boolean withVDisplay)` (garde écran virtuel :
  `UiHelper.displayVdisplayConfirmationDialog` si `!(vDisplaySupported && vDisplayDriverReady)`) ; quitter :
  `ServerHelper.doQuit(Activity, ComputerDetails, NvApp, ComputerManagerBinder, Runnable onComplete)` puis `poller.pollNow()`.
- Playnite (:746-775) : si quelque chose tourne et `app.isPlayniteGame() && computer.playniteHostRunning` → `doStart` direct (lancé dans Playnite).

## Canal
- `BuildConfig.LAV_DEV` (build.gradle) ; `app/src/dev/res` ajouté aux res des flavors en Dev seulement (overlay possible).

## Réglages
- `preferences/StreamSettings` (PreferenceFragmentCompat, `R.xml.preferences`, 149 clés), clés dans `preferences/PreferenceConfiguration`
  (lecture `readPreferences(Context)`).
- Profils : `ProfilesManager.getInstance().getOverlayingSharedPreferences(ctx)` lit le profil actif puis les prefs de base ;
  écriture compatible profils : `profiles/ProfileAwarePreferences.putBoolean/putString/putInt/putFloat/remove` (ajoutés en phase 2b
  sur le modèle de putBoolean — si le profil actif porte la clé, `SettingsProfile.setOptions` + `ProfilesManager.update`).

## Menu en jeu
- `GameMenu implements Game.GameMenuCallbacks` ; `showMenuDialog(String, MenuOption[])` (:108) = `AlertDialog` + `ArrayAdapter`.
  Listes : `showMenu` :288-356, `showAdvancedMenu` :245, `showSpecialKeysMenu` :155, `showServerCmd` :274.

## Conventions
- minSdk 21, targetSdk 34, Java 11. Dépendances : appcompat 1.7.1, recyclerview 1.4.0, material 1.13.0, cardview. Pas de constraintlayout.
- Aucune gestion de touches dans PcView/AppView : la v2 doit gérer focus, D-pad, A/B/Y elle-même.
- Build : `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"` puis `.\gradlew.bat assembleNonRoot_gameDebug`.
  Tests : `.\gradlew.bat :app:testNonRoot_gameDebugUnitTest --tests "com.limelight.v2.*"` (Robolectric 4.16, `@Config(sdk = {33})`).
  ⚠️ 5 tests JVM historiques échouent déjà (voir CLAUDE.md). Mémoire de la machine limitée : un seul build Gradle à la fois.
