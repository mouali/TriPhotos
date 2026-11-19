# TriPhotos

Range automatiquement les photos et vidéos d'un dossier dans une arborescence `AAAA/MM/`
selon leur **date de prise de vue**.

```
destination/
├── 2022/
│   └── 11/
│       ├── IMG_0001.jpg
│       └── VID_0002.mp4
└── 2025/
    └── 12/
        └── IMG_0042.heic
```

## Fonctionnement

- **Date utilisée**, par ordre de priorité :
  1. EXIF `DateTimeOriginal` (puis `DateTimeDigitized`, `DateTime`) pour les photos ;
  2. date de création QuickTime/MP4 pour les vidéos ;
  3. à défaut, date de dernière modification du fichier, en heure locale.
- **Copie par défaut** : les originaux restent dans le dossier source. `--move` pour déplacer.
- **Dossiers créés automatiquement**.
- **Doublons** : un fichier déjà présent à l'identique (même nom, même contenu) est ignoré ;
  un fichier différent portant le même nom est renommé `nom_1.jpg`, `nom_2.jpg`…
  Aucun fichier existant n'est jamais écrasé.
- **Seuls les médias sont traités** (jpg, jpeg, png, heic, gif, webp, tif, formats RAW courants,
  mp4, mov, avi, 3gp, mkv…) ; les autres fichiers sont ignorés.
- Un bilan s'affiche à la fin (triés, doublons, ignorés, erreurs).

## Prérequis

- Java 17 ou plus récent
- Maven 3.6+ (pour compiler)

## Compilation

```bash
mvn package
```

Le jar exécutable est produit dans `target/triphotos.jar`.

## Utilisation

```
java -jar target/triphotos.jar <source> <destination> [options]

Options :
  --move        déplace les fichiers au lieu de les copier
  --dry-run     simule le tri sans rien copier ni déplacer
  --recursive   traite aussi les sous-dossiers de <source>   (alias : -r)
  --help        affiche l'aide
```

Il est conseillé de lancer d'abord une simulation :

```bash
java -jar target/triphotos.jar "C:\Photos\Telephone_20251212" "C:\Photos\Triees" --dry-run
```

puis le tri réel :

```bash
java -jar target/triphotos.jar "C:\Photos\Telephone_20251212" "C:\Photos\Triees"
```

Sous Linux / macOS :

```bash
java -jar target/triphotos.jar ~/Images/Import ~/Images/Triees --recursive
```

Code de sortie : `0` si tout s'est bien passé, `1` en cas d'erreur, `2` si les arguments sont invalides.

## Tests

```bash
mvn test
```
