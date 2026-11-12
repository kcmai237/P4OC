# Plan de mesure — optimisation réseau P4OC

Statut : **Phase 0 — baseline figée, aucune modification de code.**
Date de rédaction : 2026-11-12.

Ce document fixe la référence de mesure et le protocole à appliquer avant toute
modification de code. Il ne contient **aucun chiffre de gain** : seuls la
baseline mesurée et la méthode y figurent. Tout effet annoncé par la suite est
une hypothèse à mesurer, jamais un résultat acquis.

## 1. Baseline de référence

La base de travail est `upstream/main`, dont le commit est exactement celui de
la release amont `v0.16.0`.

| Élément | Valeur |
|---|---|
| Commit de base | `636a76a46f30fff0b989f5090c91807de04170d4` |
| Tag | `v0.16.0` (versionCode 22, versionName `0.16.0`) |
| Date de publication de la release | 2026-10-11T10:54:19Z |
| Run CI amont | [38133609120](https://github.com/theblazehen/P4OC/actions/runs/38133609120) — workflow `Build`, `head_sha = 636a76a4…`, conclusion `success` (jobs `build` et `release`) |
| Source des métadonnées | `gh api repos/theblazehen/P4OC/releases/tags/v0.16.0` |
| Nom de fichier de l'asset | `app-githubRelease.apk` |
| Taille exacte | **5 563 332 octets** |
| SHA-256 | `374be2d23aabe7b0e7d25605a196eab5e12e85d4b3362498f7977071c847e64a` |
| Date de mesure | 2026-11-12 |

### Cohérence vérifiée

- La taille mesurée localement (`stat -c %s`) est **5 563 332 octets**, identique
  à la taille déclarée par l'API GitHub pour l'asset (`size: 5563332`).
- Le SHA-256 calculé localement
  (`sha256sum`) est
  `374be2d23aabe7b0e7d25605a196eab5e12e85d4b3362498f7977071c847e64a`,
  identique au `digest` renvoyé par l'API GitHub
  (`sha256:374be2d2…47e64a`).
- Le run CI `38133609120` s'est exécuté sur `head_sha = 636a76a4…` (le commit de
  `v0.16.0`) et a produit l'artefact `debug-apk` ; le job `release`
  (`assembleGithubRelease`) a publié les assets de la release.

### Provenance et limites de cette baseline

- **La CI du fork (`kcmai237/P4OC`) est inactive (0 run)** : elle n'a pas été
  sollicitée. La CI du dépôt amont est utilisée uniquement comme source de
  l'artefact déjà publié, pas comme runner pour nos modifications.
- Le type de build `release` canonique exige un keystore absent du dépôt. Le
  type **`githubRelease`** (minifié, shrink activé, signé avec la clé debug,
  sans `local.properties`) est l'artefact *release-like* garanti et reproductible
  par CI. C'est lui qui sert de baseline.
- Chemin d'artefact attendu pour un build local/CI de ce type :
  `app/build/outputs/apk/githubRelease/app-githubRelease.apk`.
- Copie de travail de l'asset :
  `/tmp/opencode/workspace/p4oc/collect/T001.4/app-githubRelease.apk`
  (secondaire éphémère ; la référence durable est la taille + le SHA-256
  ci-dessus).

## 2. Trois périmètres de mesure distincts

Les trois périmètres ci-dessous sont **indépendants** : un gain sur l'un ne vaut
pas preuve sur un autre. Chaque résultat doit nommer explicitement le périmètre
auquel il appartient.

### 2.1 Octets réseau

Volume de données échangé entre l'application et le serveur (REST, SSE, WebSocket
PTY, transferts de fichiers) sur un scénario donné. Unité : octets, par scénario
et par session.

### 2.2 Taille APK

Taille de l'artefact `app-githubRelease.apk` produit par CI sur le commit
mesuré.

- **Plafond cumulé admis : +150 Ko** par rapport à la baseline de 5 563 332
  octets (soit un maximum de 5 713 332 octets).
- La taille est toujours comparée à **type de build et commit identiques**, en
  octets exacts, jamais en valeur arrondie.

### 2.3 Débit de requêtes

Nombre de requêtes émises vers le serveur par unité de temps ou par scénario
(par exemple requêtes/s, ou nombre total de GET/POST par session). Ce périmètre
est distinct du volume réseau : réduire le nombre de requêtes et réduire les
octets sont deux objectifs qui ne se recouvrent pas.

## 3. Scénarios d'usage ultra-intensif

Chaque scénario doit pouvoir être rejoué sur la même base de code, le même type
de build et le même corpus. Le protocole de rejeu est décrit en §4.

1. **Session de chat longue en streaming** — un échange serveur→client en
   streaming continu sur une durée longue, message par message, sans
   interruption volontaire.
2. **Session chargée (> 100 messages)** — historique dépassant le seuil de
   pagination initiale (100 messages), avec navigation/scroll et rechargements,
   pour solliciter le paging et la réconciliation.
3. **Session riche en images** — pièces jointes et médias multiples, y compris
   re-affichage des mêmes médias, pour solliciter le chargement et la mise en
   cache des octets.
4. **Transferts terminal / OFISH** — commandes shell longues et transferts de
   fichiers par chunks, pour solliciter le polling shell et les transferts
   chunkés.
5. **Reconnexions réseau** — coupures et rétablissements du réseau répétés
   pendant une session active, pour solliciter le backoff/reconnexion SSE et la
   récupération d'état.
6. **Multi-sessions** — plusieurs sessions/workspaces sollicités en parallèle ou
   en alternance, pour observer les effets cumulés.

Chaque scénario est documenté par : objectif, corpus utilisé, actions exactes,
durée, périmètre(s) mesuré(s) et résultat attendu en termes de méthode (pas de
chiffre de gain).

## 4. Protocole de mesure

### 4.1 Répétitions et agrégation

- **≥ 3 exécutions** par scénario et par périmètre.
- Résultat retenu : **médiane** des exécutions (jamais la meilleure, jamais la
  moyenne seule).
- **CV < 20 %** : le coefficient de variation (écart-type / moyenne) doit rester
  strictement inférieur à 20 %. Au-delà, la série est déclarée non valide et doit
  être refaite (le bruit domine la mesure).

### 4.2 Conditions de validité d'un résultat

Un résultat n'est valide que si **toutes** les conditions suivantes sont
réunies :

- **Même commit** que la référence comparée (baseline `636a76a4` ou commit
  modifié explicitement identifié).
- **Même type de build** (`githubRelease` ; jamais comparer `debug` à
  `githubRelease`).
- **Même corpus** de données et mêmes actions de scénario.
- **Réseau stable** (même réseau, pas de variation de bande passante ou de
  latence non contrôlée pendant la série).
- **Appareil et conditions thermiques constants** (même appareil, même état
  thermique de départ, pas de throttling en cours de série).

Tout résultat ne satisfaisant pas ces conditions est écarté, pas « ajusté ».

### 4.3 Règle « estimation »

Tout gain qui n'a pas été mesuré selon le protocole ci-dessus est qualifié
d'**« estimation »**, jamais de « validé ». Un gain ne peut être déclaré
**validé** que s'il est mesuré sur le périmètre concerné, avec ≥ 3 exécutions,
médiane, CV < 20 % et conditions de validité respectées.

## 5. Ce qui est mesurable dans cet environnement

- **Taille APK** — mesurable de façon fiable : taille exacte en octets et SHA-256
  de l'artefact `app-githubRelease.apk` produit par CI (ou récupéré d'une release
  amont), comparés à la baseline.
- **Comptage de requêtes / d'octets** — mesurable **uniquement** si un compteur
  de debug est exécuté sur un appareil, ou à partir de logs serveur. Le code de
  production actuel ne contient aucun `EventListener` OkHttp ; un compteur
  debug-only devra être ajouté (par exemple via un `Interceptor` de debug, sur
  le modèle de l'intercepteur de logs diagnostiques existant).

## 6. Ce qui n'est pas mesurable dans cet environnement

- **Pas d'appareil ni d'émulateur** disponible ici : il n'est **pas** possible de
  produire une mesure réseau *end-to-end* (octets réseau, débit de requêtes sur
  scénario réel) dans cet environnement.
- Les périmètres octets réseau et débit de requêtes ne peuvent donc, en Phase 0,
  être **que** préparés méthodologiquement ; leurs chiffres ne peuvent provenir
  que d'une exécution ultérieure sur appareil ou de logs serveur.

## 7. Résumé des invariants

- Baseline figée : `636a76a4` / `v0.16.0` / `app-githubRelease.apk` /
  **5 563 332 octets** /
  `374be2d23aabe7b0e7d25605a196eab5e12e85d4b3362498f7977071c847e64a`.
- Trois périmètres distincts : octets réseau, taille APK (plafond cumulé
  +150 Ko), débit de requêtes.
- ≥ 3 exécutions, médiane, CV < 20 %, conditions de validité strictes.
- Aucun gain non mesuré n'est « validé » : c'est une « estimation ».
- Ce document ne contient aucun chiffre de gain.
