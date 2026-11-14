# Compte rendu final — optimisation réseau P4OC

**Version livrée** : 0.17.0 (versionCode 23). Base : `upstream/main` `636a76a4`
(0.16.0/22). Fork : `kcmai237/P4OC` uniquement.

## Fait

- **Activé sans flag** (retrait T001.19) : O2 reprise incrémentale v1, O3
  backoff SSE, O4 polling shell v2 adaptatif, O5 cache Coil mémoire borné.
- **Actif sans flag** (déjà) : O8 pagination sessions, O9 coalescence
  catalogues, O10 pause partielle arrière-plan, O12 contre-pression PTY.
- **Opt-in runtime** : O11 data saver (OFF par défaut, réseau facturé).
- **Non activé** : O1 brotli (décision OFF, aucun gain mesurable).
- **Documenté serveur** : O6 (curseur messages v2) et O7 (deltas SSE, catalogue
  agrégé, compression) dans `docs/perf/SERVER_REQUESTS.md`.
- Aucun push sur `theblazehen/P4OC` ; `origin/main` du fork non modifié
  directement (PR finale uniquement).

## Mesuré vs estimé

- **Mesuré** : taille APK `githubRelease` — baseline T001.4 = 5 563 332 o ;
  canary **`v0.17.0-canary.1` = 5 581 112 o** (**+17 780 o**, plafond
  +150 000, marge 132 220). Pré-release publiée sur le fork uniquement.
- **Estimé** : tous les gains réseau (pas d'appareil/HAR). Aucun gain n'est
  présenté comme acquis.

## Écarts vs rapports 00–07

O1 OFF ; O11 images non réduites (préserve l'ouverture explicite) ; O12
backpressure sans coalescence de trames ; O4 v2 seulement ; O15/O16/O17 refusés
(cache HTTP, cache persistant, Protobuf) ; O6/O7 renvoyés au serveur.

## Résiduel vs 20 Mio/jour

Non mesurable localement. Estimation auditée ≈ 17,95–20,11 Mio/j selon
périmètre, avec **réserves** : O1 non activé, O6/O7 indisponibles. Conditions de
mesure : appareil réel, `DebugNetworkMetrics`, ≥ 3 sessions, médiane + CV < 20 %.

## Travaux restants

- Mesure réseau sur appareil (HAR / compteur) pour convertir les estimations.
- O6/O7 côté serveur amont.
- Coalescence des trames PTY (piste O12) si un besoin réel apparaît.

## Risques

- Gains non mesurés : la valeur réelle sur réseau facturé reste à confirmer.
- O2/O3/O4/O5 désormais actifs en `release` : couverts par tests et vetos
  (F31 SSE, F5 messages, F15 terminal, intégrité OFISH), mais sans validation
  terrain.
- O11 n'agit que sur réseau facturé ; Wi-Fi strictement inchangé.
