package com.triphotos;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Range les photos et vidéos d'un dossier source dans {@code destination/AAAA/MM/}. */
public class PhotoSorter {

    /** Options d'exécution. Par défaut : copie, non récursif, écriture réelle. */
    public record Options(boolean move, boolean dryRun, boolean recursive) {
        public static Options defaults() {
            return new Options(false, false, false);
        }
    }

    static final Set<String> MEDIA_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "tif", "tiff", "webp", "heic", "heif",
            "dng", "cr2", "cr3", "nef", "arw", "orf", "rw2",
            "mp4", "mov", "m4v", "avi", "3gp", "mkv");

    private final PhotoDateResolver dateResolver;
    private final PrintStream log;

    public PhotoSorter() {
        this(new PhotoDateResolver(), System.out);
    }

    public PhotoSorter(PhotoDateResolver dateResolver, PrintStream log) {
        this.dateResolver = dateResolver;
        this.log = log;
    }

    public SortReport sort(Path source, Path destination, Options options) throws IOException {
        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException("Le dossier source n'existe pas ou n'est pas un dossier : " + source);
        }
        Path src = source.toAbsolutePath().normalize();
        Path dest = destination.toAbsolutePath().normalize();

        List<Path> files = listFiles(src, dest, options.recursive());
        log.println((options.dryRun() ? "[SIMULATION] " : "") + files.size() + " fichier(s) trouvé(s) dans " + src);

        SortReport report = new SortReport();
        // Cibles déjà attribuées pendant cette exécution (indispensable en simulation, où rien n'est écrit).
        Set<Path> reserved = new HashSet<>();

        for (Path file : files) {
            if (!isMedia(file)) {
                report.addSkipped();
                continue;
            }
            try {
                processFile(file, dest, options, report, reserved);
            } catch (IOException | RuntimeException e) {
                report.addError(file + " : " + e);
                log.println("ERREUR " + file + " : " + e);
            }
        }
        return report;
    }

    private List<Path> listFiles(Path src, Path dest, boolean recursive) throws IOException {
        try (Stream<Path> stream = recursive ? Files.walk(src) : Files.list(src)) {
            return stream
                    .filter(Files::isRegularFile)
                    // Ne jamais retraiter les fichiers déjà rangés si la destination est dans la source.
                    .filter(p -> !p.startsWith(dest))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private void processFile(Path file, Path dest, Options options, SortReport report, Set<Path> reserved)
            throws IOException {
        Optional<LocalDateTime> date = dateResolver.resolve(file);
        if (date.isEmpty()) {
            throw new IOException("date introuvable");
        }
        Path targetDir = dest
                .resolve(String.format("%04d", date.get().getYear()))
                .resolve(String.format("%02d", date.get().getMonthValue()));

        Path target = targetDir.resolve(file.getFileName());
        int suffix = 1;
        while (Files.exists(target) || reserved.contains(target)) {
            if (Files.exists(target) && sameContent(file, target)) {
                report.addDuplicate();
                log.println("Doublon ignoré : " + file + " (déjà présent : " + target + ")");
                return;
            }
            target = targetDir.resolve(withSuffix(file.getFileName().toString(), suffix++));
        }
        reserved.add(target);

        String action = options.move() ? "Déplacement" : "Copie";
        log.println((options.dryRun() ? "[SIMULATION] " : "") + action + " : " + file + " -> " + target);
        if (!options.dryRun()) {
            Files.createDirectories(targetDir);
            if (options.move()) {
                Files.move(file, target);
            } else {
                Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
        report.addSorted();
    }

    private static boolean sameContent(Path a, Path b) throws IOException {
        return Files.size(a) == Files.size(b) && Files.mismatch(a, b) == -1L;
    }

    static String withSuffix(String fileName, int n) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return fileName + "_" + n;
        }
        return fileName.substring(0, dot) + "_" + n + fileName.substring(dot);
    }

    static boolean isMedia(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 && MEDIA_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
