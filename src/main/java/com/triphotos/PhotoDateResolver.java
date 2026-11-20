package com.triphotos;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.mp4.Mp4Directory;
import com.drew.metadata.mov.QuickTimeDirectory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;

/**
 * Détermine la date de prise de vue d'un fichier.
 * <p>
 * Ordre de priorité :
 * <ol>
 *   <li>EXIF DateTimeOriginal, puis DateTimeDigitized, puis DateTime (photos)</li>
 *   <li>Date de création QuickTime / MP4 (vidéos)</li>
 *   <li>Date de dernière modification du fichier</li>
 * </ol>
 * Toutes les dates sont exprimées dans le fuseau horaire {@code zone} (local par défaut).
 */
public final class PhotoDateResolver {

    private static final DateTimeFormatter EXIF_FORMAT = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");

    private final ZoneId zone;

    public PhotoDateResolver() {
        this(ZoneId.systemDefault());
    }

    public PhotoDateResolver(ZoneId zone) {
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    public Optional<LocalDateTime> resolve(Path file) {
        Optional<LocalDateTime> fromMetadata = readMetadataDate(file);
        if (fromMetadata.isPresent()) {
            return fromMetadata;
        }
        try {
            return Optional.of(LocalDateTime.ofInstant(Files.getLastModifiedTime(file).toInstant(), zone));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private Optional<LocalDateTime> readMetadataDate(Path file) {
        Metadata metadata;
        try {
            metadata = ImageMetadataReader.readMetadata(file.toFile());
        } catch (ImageProcessingException | IOException | RuntimeException e) {
            // Format non reconnu ou fichier illisible : on se rabat sur la date du fichier.
            return Optional.empty();
        }

        // L'heure EXIF est l'heure locale de l'appareil, sans fuseau : on la prend telle quelle.
        ExifSubIFDDirectory exif = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        if (exif != null) {
            for (int tag : new int[]{ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL, ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED}) {
                Optional<LocalDateTime> d = parseExif(exif.getString(tag));
                if (d.isPresent()) {
                    return d;
                }
            }
        }
        ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
        if (ifd0 != null) {
            Optional<LocalDateTime> d = parseExif(ifd0.getString(ExifIFD0Directory.TAG_DATETIME));
            if (d.isPresent()) {
                return d;
            }
        }

        // Les dates vidéo QuickTime/MP4 sont en UTC : conversion vers le fuseau local.
        Optional<LocalDateTime> video = videoDate(metadata.getFirstDirectoryOfType(QuickTimeDirectory.class),
                QuickTimeDirectory.TAG_CREATION_TIME);
        if (video.isPresent()) {
            return video;
        }
        return videoDate(metadata.getFirstDirectoryOfType(Mp4Directory.class), Mp4Directory.TAG_CREATION_TIME);
    }

    private Optional<LocalDateTime> videoDate(Directory dir, int tag) {
        if (dir == null) {
            return Optional.empty();
        }
        Date date = dir.getDate(tag);
        // Une date non renseignée vaut souvent 1904 (époque QuickTime) ou 1970 : on l'ignore.
        if (date == null || date.toInstant().getEpochSecond() <= 0) {
            return Optional.empty();
        }
        return Optional.of(LocalDateTime.ofInstant(date.toInstant(), zone));
    }

    static Optional<LocalDateTime> parseExif(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String v = value.trim();
        if (v.length() < 19 || v.startsWith("0000")) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDateTime.parse(v.substring(0, 19), EXIF_FORMAT));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
