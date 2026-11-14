# Consommation des données — mesure embarquée (0.18.0)

## Où voir le chiffre

**Settings → Connection → Data usage** affiche, pour la journée en cours :

- total reçu + envoyé (octets réseau réels, corps compressé) ;
- détail reçu / envoyé / nombre d'appels ;
- les `Content-Encoding` observés (`gzip`, `br`, `identity`…) ;
- le top 3 des endpoints depuis le démarrage de l'app ;
- un bouton **Reset counter**.

Le compteur est actif en release (plus seulement en debug), persiste au
redémarrage et se remet à zéro chaque jour. Les octets SSE sont comptés au fur
et à mesure. Les frames WebSocket du terminal ne sont pas incluses.

## Comment lire le résultat

- `identity` seul : le serveur n'envoie pas de compression → c'est le levier
  principal (~25 Mio/j dans l'audit). Aucun réglage client ne peut le remplacer.
- `gzip` ou `br` : la compression est déjà active ; le gisement restant est
  dans les caches et la fréquence des rafraîchissements.

## Comportement « sobre » de 0.18.0

- Data saver **ON par défaut sur tous les réseaux** (désactivable).
- Les rafraîchissements automatiques non essentiels (projets/sessions) sont
  limités à **un par 5 minutes**.
- Le chat en direct (SSE), les messages, le terminal et les transferts OFISH ne
  sont jamais différés.

## Mesure fiable

Utiliser l'app normalement pendant une journée complète, puis relever le total
du jour. Répéter sur ≥ 3 jours et retenir la médiane. Le compteur repart de
zéro à minuit local.
