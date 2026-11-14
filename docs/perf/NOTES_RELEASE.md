# Notes de release — P4OC réseau

## 1. État de version

- Base : `upstream/main` = `636a76a4`, version 0.16.0, versionCode 22.
- **Release finale** : **0.17.0 / versionCode 23** (bump unique T001.19).
- Canary publié : tag **`v0.17.0-canary.1`** sur le fork uniquement,
  pré-release GitHub (`isPrerelease = true`), APK `app-githubRelease.apk`
  = 5 581 112 octets.
- `origin/main` (fork) reste à `33021cb` (v0.13.0) : la livraison passe par la
  PR finale, jamais par un push direct sur `main`.

## 2. Procédure de release effectivement suivie

1. Commit sur une branche `perf/*`, poussée immédiatement vers
   `origin` (`kcmai237/P4OC`).
2. Tag `v*` poussé (`v0.16.0-tNN-test` pour les mesures) → déclenche le
   workflow `build` : Lint, Compile Kotlin, `Run unit tests`, `Verify net flags`
   (release + githubRelease), `assembleDebug`, puis le job `release`
   (`assembleGithubRelease` + création de la release GitHub).
3. Suivi : `gh run watch <id> --repo kcmai237/P4OC --interval 30 --exit-status`
   (le `--repo` est obligatoire : sinon `gh` résout `upstream`).
4. Récupération de l'APK :
   `gh release download <tag> --repo kcmai237/P4OC -p 'app-githubRelease.apk' -D <dir> --clobber`.
5. Mesure en **octets** (`stat -c %s`), jamais en SHA ; comparaison à la
   baseline T001.4 (5 563 332) et au plafond +150 Ko (5 713 332).
6. Nettoyage : suppression de la release et du tag de mesure
   (`gh release delete --cleanup-tag --yes`, `git tag -d`), vérification
   `git ls-remote --tags origin`.

## 3. Mécanisme de tag

- Tags de mesure : `v0.16.0-tNN[-suffix]-test` (temporaires, supprimés après
  mesure).
- Tag canary (build `githubRelease`, flags ON) : même schéma, utilisé pour
  valider un artefact mesurable.
- Release finale : tag `vX.Y.Z` sur `main` **après** fusion des branches
  validées ; `release` reste la variante flags OFF.

## 4. État des flags (release finale)

Les quatre flags `BuildConfig` de canary (`NET_O2_INCREMENTAL_MESSAGES`,
`NET_O3_SSE_BACKOFF`, `NET_O4_SHELL_POLLING`, `NET_O5_COIL_BOUNDED`) ont été
**retirés en T001.19** : les optimisations validées sont actives dans **toutes**
les variantes. Les coutures internes (`backoffEnabled`, `adaptivePolling`,
`incrementalMessages`) restent paramétrables pour les tests du chemin OFF,
mais le câblage de production est toujours ON.

Ordre de retrait justifié (risque croissant) : O5 (mémoire seule) → O3
(reconnexions seulement) → O4 (shell v2 seulement) → O2 (historique, impact le
plus large). Les étapes CI `Verify net flags` ont été supprimées du job
`build` ; le job `release` garde son mécanisme inchangé.

Non flaggés (toujours actifs) : O8 (pagination sessions), O9 (coalescence
catalogues), O10 (pause arrière-plan), O12 (contre-pression PTY). O11 (data
saver) reste un réglage **runtime opt-in**, OFF par défaut.

## 5. Prérequis

- `gh` authentifié (`GH_TOKEN`) avec écriture sur `kcmai237/P4OC`.
- Workflow CI du fork opérationnel (JDK 17, Android SDK via CI).
- Aucun JDK/SDK local requis ; aucun artefact installé sous `/data`.
- Ne jamais pousser sur `origin/main` ni `upstream` (push `DISABLED`).

## 6. Points d'attention

- Un tag poussé suffit à déclencher `build` + `release` ; supprimer le tag
  distant après mesure pour éviter une release orpheline.
- Un échec de `build` empêche le job `release` : supprimer quand même le tag
  distant avant de retenter.
- Mesurer l'APK `githubRelease` (flags ON), pas `debug`.
- Les gains réseau restent des **estimations** tant qu'aucune mesure appareil
  (HAR / `DebugNetworkMetrics`, ≥ 3 runs, médiane, CV < 20 %) n'est produite.
