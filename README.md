# Krypton Utility 1.1.0 — Fabric 1.21.11

Mod **100 % client**. Aucune connexion réseau, aucune télémétrie, un seul fichier écrit : `config/krypton-utility.json`.

## Compiler
1. Pousse ce dossier sur GitHub. 2. Onglet **Actions** > **Build** > artefact `krypton-utility` (jar sans `-sources`).
3. Mets le jar dans `mods/` (Fabric Loader >= 0.18, Fabric API 0.141.x+1.21.11).
Local : `gradle build` (Gradle 9.2+, JDK 21). Le build exécute aussi `verifyLocalOnly` (voir plus bas).

## Nouveautés 1.1.0
- **Sélecteur de blocs** (Block Scanner > Edit) : recherche, grille de tous les blocs avec icône, clic = ajouter/retirer,
  clic droit = couleur, Select shown / Deselect shown / Invert, « Selected only », presets intégrés (minerais, conteneurs,
  spawners, construction, redstone…) et **vos presets** (créer, renommer, supprimer, copier un preset intégré).
  Chaque module a sa propre liste, sauvegardée avec ses couleurs.
- **Freecam complet** : 6 directions + souris, accélération/décélération, boost/ralenti (touches), molette = vitesse,
  FOV, collisions, distance max, retour au départ, caméra sur le corps, contrôle caméra/corps, suivi d'entité
  (orbite, distance réglable), touches configurables pour chaque action (Options > Commandes > Krypton Utility).
- Toutes les options rangées par sections, avec options conditionnelles ; ESP, HUD, Inventaire, Fullbright étoffés.
- Performances : lecture de gamma/FOV sans parcourir de liste, scanner par sections (sections vides ignorées),
  recherche par table de hachage, travail borné par tick.

## Discrétion et sécurité
| Module | Visible du serveur ? |
|---|---|
| Freecam | Non, sauf 2 options **désactivées par défaut** (interactions depuis la caméra, déplacer le corps) |
| Fullbright, ESP, Block Scanner, HUD | Non (rendu local, données déjà reçues du serveur) |
| Inventory Tools | **Oui** : mêmes paquets de clic qu'à la main, mais en rafale. Vitesse réglable. |

Chaque module affiche **CLIENT-ONLY** ou **SERVER-VISIBLE** dans le ClickGUI, avec une bannière explicative ;
chaque réglage risqué porte un « ! » avec infobulle. Fabric Loader lui-même annonce la marque « fabric » aux serveurs
(comportement standard, non modifié par ce mod).

- Aucun secret, clé, token ni chemin personnel dans le code. Journaux : texte fixe + nom de classe d'erreur uniquement
  (jamais de message d'exception, chemin, pseudo, coordonnée ou adresse de serveur). Un module qui plante est désactivé
  au lieu de faire planter le jeu.
- ClickGUI > **LOCAL DATA** : *Reset settings* (réglages par défaut) et *Erase all data* (réglages + presets + suppression
  du fichier). Double clic de confirmation. Le fichier n'est pas recréé tant que rien ne diffère des défauts.
- `gradle build` lance `verifyLocalOnly` : le build échoue si une source contient `java.net`, sockets, `http(s)://`,
  `ProcessBuilder`, `System.getenv`, `user.home`, `Base64`, `Class.forName`, etc. Le code est non obfusqué.

## Points à surveiller à la première compilation / exécution
Noms Mojang écrits sans pouvoir compiler ici : mixins (`Entity.turn`, `KeyMapping.isDown/consumeClick`,
`OptionInstance.get`, `MouseHandler.onScroll`, champs `leftPos/topPos/imageWidth/imageHeight`),
`MultiPlayerGameMode.handleInventoryMouseClick`, `LevelChunkSection.hasOnlyAir`, `Level.noCollision(AABB)`,
`EditBox` (constructeur, `setBordered`). Envoie le log d'erreur et je corrige.
