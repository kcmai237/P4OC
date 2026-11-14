# Résultats — optimisations réseau P4OC (O1–O12)

Baseline : **T001.4** — APK `app-githubRelease.apk` = **5 563 332 octets**
(build CI `636a76a4`, version 0.16.0 / versionCode 22).

## 1. Statut par optimisation

Classification stricte : **mesuré** (protocole, n, médiane, CV) ou
**estimation** (raison). Aucun gain réseau n'est présenté comme acquis : il
n'existe ni appareil ni capture HAR dans cet environnement.

| O | Implémentation | Statut | Protocole / raison |
|---|---|---|---|
| O1 | Compression brotli | **estimation — non activé** | Aucune mesure exploitable ; OkHttp 5.3.2 fait déjà gzip transparent ; ajouter `okhttp-brotli` serait un Ko non justifié. Décision OFF (T001.12). |
| O2 | Messages incrémentaux v1 (`before`) | **estimation** | Gain = pages non re-téléchargées lors des « load older » ; pas de HAR. Tests d'équivalence ON/OFF + saturation (T001.14). |
| O3 | Backoff SSE plafonné + jitter | **estimation** | Réduit les tentatives de reconnexion en rafale ; dépend de la durée des coupures, non mesurée. Tests `EventSourceBackoffTest` (T001.10). |
| O4 | Polling shell v2 adaptatif (250 ms→1 s) | **estimation** | Débit shell variable selon commande ; plancher = valeur historique, plafond = estimation. Tests `ShellPollingAdaptiveTest` (T001.13). |
| O5 | Cache mémoire Coil borné 8 Mio | **estimation — gain réseau 0** | Gain mémoire uniquement (évite le cache disque) ; aucun octet réseau revendiqué. Tests `ImageLoaderCacheTest` (T001.7). |
| O6 | Curseur messages v2 | **estimation — serveur non disponible** | Demande documentée (`SERVER_REQUESTS.md`) ; volumétrie « non mesurée — à mesurer côté serveur » (T001.17). |
| O7 | Deltas SSE / catalogue agrégé / compression | **estimation — serveur non disponible** | Idem O6 ; aucune valeur chiffrée inventée (T001.17). |
| O8 | Liste de sessions bornée (50 + pas de 50) | **estimation** | Réduit la première page ; volume dépend du nombre de sessions. Tests `PagedSessionsLoadMoreTest` (T001.9). |
| O9 | Coalescence single-flight + TTL 30 s | **estimation** | Évite les appels concurrents/redondants sur catalogues ; fréquence réelle non mesurée. Tests `RequestCoalescerTest`, `V2CatalogCoalescingTest` (T001.8). |
| O10 | Pause partielle hors premier plan | **estimation** | Dépend du temps passé en arrière-plan ; non mesuré. Tests `SessionRepositoryPauseTest` (T001.11). |
| O11 | Data saver opt-in (réseau facturé) | **estimation** | Conditionnel : opt-in + metered ; aucun appareil. Tests `DataSaverPolicyTest`, `SettingsDataStoreDataSaverTest`, `DataSaverRefreshTest` (T001.16). |
| O12 | Contre-pression PTY sans perte | **estimation — gain 0 par conception** | Remplace un drop par une suspension (F15) ; aucun octet supprimé. Test de saturation 5000 frames (T001.15). |

**Taille APK** : seule grandeur *mesurée*, déterministe (n = 1 build CI par
enfant ; médiane = valeur ; CV = 0 car artefact reproductible). Protocole :
`assembleGithubRelease` en CI, `stat` en octets (jamais en SHA).

## 2. Budget APK

Plafond : baseline + 150 000 = **5 713 332 octets**.

| Enfant | O | APK (octets) | Δ vs enfant précédent | Δ vs baseline |
|---|---|---|---|---|
| T001.4 | baseline | 5 563 332 | — | 0 |
| T001.7 | O5 | 5 563 332 | 0 | 0 |
| T001.8 | O9 | 5 563 332 | 0 | 0 |
| T001.9 | O8 | 5 563 756 | +424 | +424 |
| T001.10 | O3 | 5 563 756 | 0 | +424 |
| T001.11 | O10 | 5 563 756 | 0 | +424 |
| T001.12 | O1 | (aucun build) | 0 | +424 |
| T001.13 | O4 | 5 580 140 | +16 384 | +16 808 |
| T001.14 | O2 | 5 580 140 | 0 | +16 808 |
| T001.15 | O12 | 5 580 140 | 0 | +16 808 |
| T001.16 | O11 | 5 581 108 | +968 | **+17 776** |
| T001.17 | O6/O7 | (docs only) | 0 | +17 776 |
| T001.18 | final docs | (docs only) | 0 | +17 776 |
| T001.19 | release 0.17.0 | 5 581 112 | +4 | **+17 780** |

**Cumul = +17 780 octets** sur +150 000 autorisés ; marge restante
**132 220 octets** (2,3 % du budget consommé). Aucun dépassement, aucune
réserve nécessaire.

## 3. Vetos — preuve par chantier

| Veto | Exigence | Preuve |
|---|---|---|
| **F31 (SSE jamais suspendu)** | Le flux SSE n'est jamais totalement coupé. | O3 ne touche qu'aux **reconnexions manuelles** (backoff), pas au flux vivant ; O10 ne gate que les rafraîchissements REST (`SessionRepositoryPauseTest`) ; O11 ne gate que projets/sessions et laisse SSE intact (`DataSaverRefreshTest` : mise à jour SSE appliquée mode actif) ; O12 ne touche que le PTY. |
| **F5 (zéro perte/dup message)** | Ensemble des messages identique au chemin de base. | O2 : `MessageWindowPagingTest` (pagination complète sans doublon, repli curseur ignoré, équivalence ON/OFF, reconnexion + prompt) ; O10 : `MessageRefreshRequested` **non gated** ; O12 : hors messages. |
| **F15 (zéro perte terminal)** | Flux reçu = flux émis, ordre conservé. | O12 : `PtyTerminalClientTest` — 5000 frames produites, 5000 reçues dans l'ordre ; suspension réelle quand le buffer est plein ; aucun `drop`. |
| **Intégrité OFISH** | Hash identique, wire format et chunk 64 Kio inchangés. | Aucun fichier `data/files/ofish/*` modifié ; tests `Ofish*` verts dans chaque run CI (1 339 → 1 370 tests, 0 échec). |

## 4. Checklist F1–F37

Aucune fonctionnalité supprimée. Les seules fonctionnalités **modifiées** sont
listées ci-dessous ; toutes les autres sont inchangées (code non touché,
tests existants verts à chaque run).

| F | Fonctionnalité | État | Preuve |
|---|---|---|---|
| F4 | Liste des sessions | Modifiée : pagination 50 + pas de 50 ; rafraîchissements différés (arrière-plan / data saver) | `PagedSessionsLoadMoreTest`, `SessionRepositoryPauseTest`, `DataSaverRefreshTest` |
| F5 | Chat / historique | Modifiée : reprise incrémentale v1, backoff SSE ; messages jamais gated | `MessageWindowPagingTest`, `EventSourceBackoffTest` |
| F13 | Slash-commands & shell | Modifiée : polling adaptatif **v2 uniquement** ; v1 inchangé, timeout 120 s inchangé | `ShellPollingAdaptiveTest` |
| F14 | Pièces jointes / images | Modifiée : cache Coil **mémoire** borné 8 Mio, cache disque désactivé ; chargement d'images inchangé | `ImageLoaderCacheTest` |
| F15 | Terminal embarqué | Modifiée : contre-pression PTY bornée, zéro drop | `PtyTerminalClientTest` |
| F25 | Paramètres | Modifiée : nouvel interrupteur « Data saver » | `SettingsViewModelRegistryTest`, `DataSaverPolicyTest` |
| F30 | Paramètres de connexion | Modifiée : ajout du toggle data saver dans l'écran connexion | idem |
| F17/F24 | Visionneuse / uploads OFISH | **Inchangées** | tests `Ofish*` verts |
| F31 | Notifications (homonyme du veto F31) | **Inchangée** | code non touché ; note : le veto « F31 » désigne le SSE, pas cette fonctionnalité |
| F1–F3, F6–F12, F16, F18–F23, F26–F29, F32–F37 | — | **Inchangées** | code non touché ; suites vertes dans les runs CI |

## 5. Résiduel vs 20 Mio/jour

**Aucune mesure locale de consommation réseau** (pas d'appareil, pas de HAR).
Le résiduel ne peut donc pas être présenté comme acquis.

- Estimation auditée (rapport 05) : **≈ 17,95 Mio/j** après Phase 3, **≈ 20,11
  Mio/j** après Phase 2 seule — selon le périmètre et sous les hypothèses des
  rapports.
- **Réserve O1** : O1 (brotli) n'est **pas activé** ; son gain estimé reste
  donc non acquis et doit être ajouté au résiduel si l'on retient la
  fourchette auditée.
- **Réserve O6/O7** : gains serveur non disponibles ; non comptés.
- Conditions de mesure : appareil Android réel, `DebugNetworkMetrics`
  (T001.5), ≥ 3 sessions d'usage représentatif, médiane + CV < 20 %, réseau
  facturé vs Wi-Fi.

**Aucun gain global non mesuré n'est déclaré acquis.**

## 6. Écarts vs rapports 00–07

| Écart | Décision | Preuve / raison |
|---|---|---|
| O1 brotli non activé | OFF | Aucune mesure ; gzip transparent déjà présent ; dépendance non justifiée (T001.12) |
| O11 images non réduites | Non appliqué | Bloquerait l'ouverture explicite d'une image (critère « aucune action bloquée ») ; seuls les rafraîchissements sont différés (T001.16) |
| O12 : backpressure seulement | Non appliqué : coalescence des trames | Le drop silencieux était le risque critique (F15) ; la coalescence PTY reste une piste future |
| O4 v2 seulement | Périmètre | Le chemin shell v1 n'est pas pollé ; aucun changement |
| O15 cache HTTP disque | Refusé | Décision projet (risque SSE/staleness), conforme aux rapports |
| O16 cache applicatif persistant | Refusé | Caches mémoire bornés uniquement |
| O17 Protobuf | Refusé | JSON-only, rupture de contrat |
| O6/O7 | Demandes serveur | Documentées, hors périmètre client (T001.17) |

## 7. Renvois

- Détails par chantier : `findings/T001.4.md` … `findings/T001.17.md`.
- Mesures APK : `collect/T001.4/` … `collect/T001.19/`.
- Demandes serveur : `docs/perf/SERVER_REQUESTS.md`.
- Procédure de release et flags : `docs/perf/NOTES_RELEASE.md`.
