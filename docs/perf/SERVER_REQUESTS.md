# Demandes d'optimisation côté serveur — O6 / O7

**Périmètre.** Ce document décrit des demandes adressées au **serveur OpenCode
amont**. Le serveur et son dépôt sont **hors périmètre de ce dépôt client** :
aucun travail serveur n'est inclus dans les PR de ce projet et aucun correctif
serveur n'est supposé disponible. Les propositions ci-dessous sont
actionnables indépendamment ; le client continue de fonctionner sans elles.

**Convention de volumétrie.** Toute valeur non mesurée est explicitement
étiquetée **« non mesurée — à mesurer côté serveur »**. Aucun chiffre n'est
estimé ni inventé dans ce document.

---

## O6 — Curseur de pagination des messages v2

### Endpoint concerné

`GET /api/session/{sessionID}/message?limit=<n>&order=desc[&cursor=<opaque>]`

### Comportement actuel (client)

`V2SessionOpenCodeApi.getMessages` pagine du plus récent vers le plus ancien
avec un curseur opaque (`cursor`) renvoyé par la page précédente. Le curseur
n'est **pas conservé entre deux appels** de chargement d'historique : chaque
agrandissement de fenêtre repart de la page la plus récente et re-télécharge
les pages déjà connues jusqu'à atteindre `limit`. Le paramètre v1
`before=<messageID>` est explicitement non supporté en v2
(`unsupported("load messages before a message", "v2 pagination uses opaque
cursors that cannot be derived from a message ID")`).

### Volumétrie

- Octets par page, taille de page par défaut, et taux de re-téléchargement
  lors des « load older » : **non mesurée — à mesurer côté serveur.**

### Proposition

1. Accepter un ancrage `before=<messageID>` sur la route message v2 : renvoyer
   les `limit` messages **strictement antérieurs** à cet ID, du plus récent au
   plus ancien (même convention que la route actuelle).
2. À défaut, exposer un curseur **stable et rejouable** dérivé d'un message
   connu (par exemple `GET /api/session/{sessionID}/message/{messageID}/cursor`
   renvoyant l'opaque cursor positionné juste avant ce message), afin que le
   client puisse reprendre la pagination depuis le plus ancien message connu.
3. Renvoyer dans l'enveloppe de page `nextCursor`/`hasMore` explicites plutôt
   que de faire dériver le client de la seule taille de page.

### Critère d'acceptation serveur

- Pour une session et un ancre `before` (ou un curseur dérivé) fixés, deux
  appels successifs renvoient exactement la même page (déterminisme).
- Aucun doublon et aucun trou d'ordre entre pages contiguës.
- `limit` est honoré (au plus `limit` messages rendus ; moins uniquement en
  début d'historique).
- Le curseur reste valide après redémarrage du processus serveur ou est
  explicitement invalidé avec une erreur documentée.

### Impact client attendu

La reprise incrémentale (O2) devient disponible en v2 : un « load older » ne
re-télécharge plus la fenêtre déjà chargée. Gain proportionnel à la taille de
la fenêtre et à la fréquence des agrandissements — **estimation**, à mesurer.

### Repli client

Sans support serveur, le client conserve le chemin actuel (pagination
newest-first jusqu'à `limit`) : aucun changement fonctionnel, aucun curseur
inventé.

---

## O7 — Événements delta-only, catalogues agrégés, compression

### O7.1 — Événements delta-only (SSE)

#### Endpoints concernés

- v1 : `GET /global/event` (SSE), événements `message.part.updated`,
  `message.part.delta`, `message.part.removed`.
- v2 : `GET /api/event` (SSE), événements `session.text.delta`,
  `session.reasoning.delta`, `session.text.ended`, etc.

#### Comportement actuel (client)

- v1 : `message.part.updated` transporte un **instantané complet** de la part
  (texte/état) et peut aussi porter `delta`. Le client applique le delta quand
  il est présent, sinon remplace la part par l'instantané.
- v2 : les deltas existent déjà pour le texte (`session.text.delta`,
  `session.reasoning.delta`) ; les phases `ended` renvoient encore le texte
  complet. Les sorties d'outils et certains états peuvent être ré-émis en
  entier.

#### Volumétrie

- Octets SSE par message long, part des instantanés complets vs deltas, nombre
  d'événements par part : **non mesurée — à mesurer côté serveur.**

#### Proposition

1. Garantir un événement **delta** pour chaque part streamée (texte,
   raisonnement, sortie/arguments d'outil), avec un `partVersion` monotone.
2. Rendre l'instantané complet des événements `*.ended` optionnel : envoyer à
   la place une référence courte (taille/checksum/`partVersion` finale), le
   client reconstruisant l'état depuis les deltas.
3. Si un instantané complet reste nécessaire (reprise/replay), l'exposer via
   un paramètre explicite de la requête SSE plutôt que par défaut.

#### Critère d'acceptation serveur

- Le volume d'octets d'un flux long croît avec le texte ajouté (deltas), pas
  avec `taille_texte × nombre_d_événements`.
- La concaténation des deltas et l'événement final permettent de reconstruire
  exactement l'état final (vérifiable par un test serveur de replay).
- Ordre garanti par `partVersion` monotone ; aucun delta perdu entre deux
  versions.

#### Impact client attendu

Réduction importante des octets SSE sur les longues réponses et sorties
d'outils ; gain proportionnel à la durée du streaming — **estimation**, à
mesurer.

#### Repli client

Le client ignore les types d'événements inconnus et continue d'appliquer les
instantanés complets lorsqu'ils sont présents (comportement actuel).

### O7.2 — Endpoint agrégé providers / models / config

#### Endpoints concernés

- `GET /api/provider`
- `GET /api/model`
- `GET /api/model/default`
- `GET /api/config`

#### Comportement actuel (client)

`getProviders` effectue **trois** requêtes séquentielles (`provider`, `model`,
`model/default`) pour construire la réponse providers. `getConfig` effectue
**deux** requêtes (`config`, `model/default`). Le client coalesce ces appels
30 s en mémoire (O9), mais chaque rafraîchissement coûte plusieurs
aller-retours et plusieurs enveloppes JSON.

#### Volumétrie

- Octets par catalogue, nombre d'appels par navigation/rafraîchissement :
  **non mesurée — à mesurer côté serveur.**

#### Proposition

1. `GET /api/catalog?location[directory]=...&location[workspace]=...`
   renvoyant `{ providers, models, defaults, config }` en une seule réponse.
2. À défaut, `GET /api/config?include=providers,models,defaults` avec la même
   union.
3. Ajouter un `ETag`/`version` stable pour permettre une revalidation
   conditionnelle (`If-None-Match`) sans re-télécharger le catalogue.

#### Critère d'acceptation serveur

- Une seule requête renvoie exactement l'union des données des endpoints
  actuels, schéma stable et documenté.
- `ETag`/`version` change uniquement quand le catalogue change ; réponse `304`
  sinon.
- Les endpoints existants restent disponibles (compatibilité).

#### Impact client attendu

Moins d'aller-retours (latence) et moins d'octets de structure (une enveloppe,
un flux de compression). Gain **estimation** ; à mesurer.

#### Repli client

Sans endpoint agrégé, le client conserve les appels séparés actuels (avec le
coalescer O9).

### O7.3 — Compression REST et SSE

#### Comportement actuel (client)

OkHttp négocie automatiquement `Accept-Encoding: gzip` et décompresse
transparemment les réponses REST ; le compteur debug enregistre
`Content-Encoding` au niveau réseau. Le flux SSE est consommé via la
bibliothèque `okhttp-eventsource` sur le même client authentifié.

#### Volumétrie

- Ratio gzip sur des réponses REST représentatives et sur un flux SSE long,
  impact sur la latence de flush : **non mesurée — à mesurer côté serveur.**

#### Proposition

1. REST : garantir que le serveur honore `Accept-Encoding: gzip` (déjà
   transparent côté client) et ne double-compresse pas.
2. SSE : support **optionnel** de `Accept-Encoding: gzip` avec flush par
   événement (ou par petit lot borné), afin de ne pas retarder la livraison.
   Si ce n'est pas réalisable sans buffering, conserver l'encodage identité.
3. Documenter publiquement la politique de compression (types, seuils,
   `Content-Encoding`).

#### Critère d'acceptation serveur

- REST : `Content-Encoding: gzip` présent quand demandé et rentable ;
  décompression client vérifiée.
- SSE compressé (si activé) : chaque événement est livré avec une latence
  bornée (pas d'attente de remplissage de buffer), et le flux reste
  strictement ordonné.
- Aucun changement de sémantique du protocole SSE.

#### Impact client attendu

Réduction des octets REST (déjà partiellement acquise via gzip) et SSE —
**estimation**, à mesurer côté serveur. Aucun changement client requis pour
REST.

#### Repli client

Encodage identité ; comportement actuel inchangé.

---

## Hors périmètre

- Toute modification ou PR dans le dépôt serveur.
- Toute hypothèse de disponibilité immédiate d'une de ces demandes.
- Protobuf / format binaire (refusé, cf. décision O17).
- Cache HTTP disque global (refusé, cf. décision O15).
