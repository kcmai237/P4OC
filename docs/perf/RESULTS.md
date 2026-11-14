# Résultats — optimisations réseau P4OC

## O11 — Économie de données (opt-in, OFF par défaut)

Réglage : `ConnectionSettingsScreen` → « Data saver » (persisté dans
`SettingsDataStore`, clé `data_saver_enabled`, défaut `false`).

Le mode n'est **actif** que si l'utilisateur l'active **et** que le réseau
courant est **facturé** (`NetworkCapabilities.NET_CAPABILITY_NOT_METERED`
absent). Sur Wi-Fi (non facturé), le comportement est strictement inchangé.

### Éléments réduits (liste exhaustive)

1. **Rafraîchissements automatiques non essentiels différés** — sur réseau
   facturé, les événements `ProjectUpdated`, `ProjectDirectoriesUpdated`,
   `ProjectRefreshRequested` et `SessionRefreshRequested` ne déclenchent plus
   de requête REST immédiate. Le rafraîchissement différé est exécuté **une
   seule fois** à la désactivation du mode (ou au retour au premier plan).
   Les actions explicites de l'utilisateur (navigation, envoi, refresh manuel)
   ne sont jamais bloquées.

### Ce qui n'est jamais réduit (vetos)

- **SSE** (`F31`) : le flux d'événements n'est jamais suspendu.
- **Messages** (`F5`) : réception/streaming/reconnexion inchangés ;
  `MessageRefreshRequested` n'est pas concerné par la pause.
- **Terminal / PTY** (`F15`) : aucune modification du flux terminal.
- **Uploads OFISH** : hash, wire format et chunks 64 Kio inchangés.
- **Images de chat** : volontairement non réduites — bloquer le chargement
  empêcherait l'ouverture explicite d'une image, ce qui contredirait
  « aucune action explicite bloquée ». Un utilisateur qui veut économiser des
  images peut désactiver le mode.

### Comportement OFF

`DataSaverPolicy.Disabled` (défaut) renvoie toujours `active = false` ; le
chemin de rafraîchissement est identique à la base (test dédié
`DataSaverRefreshTest`).

### Gain

**Estimation** (aucun appareil/HAR) : réduction conditionnelle sur réseau
facturé, proportionnelle aux rafraîchissements de projets/sessions évités.
Aucun gain central revendiqué ; aucun changement de taille APK significatif.

### Fichiers

- `core/datastore/SettingsDataStore.kt` (préférence + setter)
- `core/network/DataSaverPolicy.kt`, `core/network/AndroidNetworkMeteredSource.kt`
- `data/session/SessionRepositoryImpl.kt` (pause des rafraîchissements)
- `ui/screens/settings/SettingsViewModel.kt`,
  `ui/screens/settings/ConnectionSettingsScreen.kt`
- Tests : `DataSaverPolicyTest`, `SettingsDataStoreDataSaverTest`,
  `DataSaverRefreshTest`
