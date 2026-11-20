package com.triphotos;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Range les photos et vidéos d'un dossier source dans {@code destination/AAAA/MM/}. */
public final class PhotoSorter {

    /** Mode de transfert des fichiers vers la destination. */
    public enum Mode {
        COPY("Copie"),
        MOVE("Déplacement");

        private final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    /**
     * Options d'exécution.
     *
     * @param mode      copie ou déplacement des fichiers
     * @param dryRun    {@code true} pour simuler le tri sans rien écrire
     * @param recursive {@code true} pour traiter aussi les sous-dossiers de la source
     */
    public record Options(Mode mode, boolean dryRun, boolean recursive) {

        public Options {
            Objects.requireNonNull(mode, "mode");
        }

        /** Copie, non récursif, écriture réelle. */
        public static Options defaults() {
            return new Options(Mode.COPY, false, false);
        }

        public Options withMode(Mode newMode) {
            return new Options(newMode, dryRun, recursive);
        }

        public Options withDryRun() {
            return new Options(mode, true, recursive);
        }

        public Options withRecursive() {
            return new Options(mode, dryRun, true);
        }
    }

    static final Set<String> MEDIA_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "tif", "tiff", "webp", "heic", "heif", "avif",
            "dng", "cr2", "cr3", "nef", "arw", "orf", "rw2", "raf", "pef", "srw",
            "mp4", "mov", "m4v", "avi", "3gp", "mkv", "mts", "m2ts");

    private final PhotoDateResolver dateResolver;
    private final Consumer<String> log;

    public PhotoSorter() {
        this(new PhotoDateResolver(), System.out::println);
    }

    public PhotoSorter(PhotoDateResolver dateResolver, Consumer<String> log) {
        this.dateResolver = Objects.requireNonNull(dateResolver, "dateResolver");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Range les médias de {@code source} dans {@code destination/AAAA/MM/}.
     * <p>
     * Les erreurs propres à un fichier ou à un sous-dossier (illisible, date introuvable…) n'interrompent
     * pas le tri : elles sont consignées dans le rapport.
     *
     * @param source      dossier contenant les fichiers à ranger
     * @param destination dossier racine du rangement (créé si besoin)
     * @param options     options d'exécution
     * @return le bilan de l'exécution
     * @throws IllegalArgumentException si {@code source} n'est pas un dossier existant
     * @throws IOException              si le parcours de la source échoue globalement
     */
    public SortReport sort(Path source, Path destination, Options options) throws IOException {
        Objects.requireNonNull(options, "options");
        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException("Le dossier source n'existe pas ou n'est pas un dossier : " + source);
        }
        Run run = new Run(destination.toAbsolutePath().normalize(), options);
        Path src = source.toAbsolutePath().normalize();

        List<Path> files = listFiles(src, run);
        log.accept(dryRunPrefix(options) + files.size() + " fichier(s) trouvé(s) dans " + src);

        for (Path file : files) {
            if (!isMedia(file)) {
                run.report.addSkipped();
                continue;
            }
            try {
                processFile(file, run);
            } catch (IOException | RuntimeException e) {
                error(run, file, e.toString());
            }
        }
        return run.report;
    }

    /** Liste les fichiers de la source ; un dossier illisible est signalé sans arrêter le parcours. */
    private List<Path> listFiles(Path src, Run run) throws IOException {
        List<Path> files = new ArrayList<>();
        int maxDepth = run.options.recursive() ? Integer.MAX_VALUE : 1;
        Files.walkFileTree(src, EnumSet.noneOf(FileVisitOption.class), maxDepth,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        // Ne jamais retraiter les fichiers déjà rangés si la destination est dans la source.
                        return dir.startsWith(run.dest) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (Files.isRegularFile(file) && !file.startsWith(run.dest)) {
                            files.add(file);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException e) {
                        error(run, file, "inaccessible (" + e + ")");
                        return FileVisitResult.CONTINUE;
                    }
                });
        Collections.sort(files);
        return files;
    }

    private void processFile(Path file, Run run) throws IOException {
        Optional<LocalDateTime> date = dateResolver.resolve(file);
        if (date.isEmpty()) {
            error(run, file, "date introuvable");
            return;
        }
        Path targetDir = run.dest
                .resolve(String.format(Locale.ROOT, "%04d", date.get().getYear()))
                .resolve(String.format(Locale.ROOT, "%02d", date.get().getMonthValue()));

        Path target = targetDir.resolve(file.getFileName());
        int suffix = 1;
        while (Files.exists(target) || run.reserved.containsKey(target)) {
            // Sur disque, la cible fait foi ; sinon (simulation), on compare au fichier source qui l'a réservée.
            Path occupant = Files.exists(target) ? target : run.reserved.get(target);
            if (sameContent(file, occupant)) {
                run.report.addDuplicate();
                log.accept("Doublon ignoré : " + file + " (déjà présent : " + target + ")");
                return;
            }
            target = targetDir.resolve(withSuffix(file.getFileName().toString(), suffix++));
        }
        run.reserved.put(target, file);

        log.accept(dryRunPrefix(run.options) + run.options.mode().label + " : " + file + " -> " + target);
        if (!run.options.dryRun()) {
            Files.createDirectories(targetDir);
            if (run.options.mode() == Mode.MOVE) {
                Files.move(file, target);
            } else {
                Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
        run.report.addSorted();
    }

    private void error(Run run, Path file, String message) {
        run.report.addError(file + " : " + message);
        log.accept("ERREUR " + file + " : " + message);
    }

    private static String dryRunPrefix(Options options) {
        return options.dryRun() ? "[SIMULATION] " : "";
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

    /** État d'une exécution du tri. */
    private static final class Run {
        final Path dest;
        final Options options;
        final SortReport report = new SortReport();
        /** Cible attribuée -> fichier source (indispensable en simulation, où rien n'est écrit). */
        final Map<Path, Path> reserved = new HashMap<>();

        Run(Path dest, Options options) {
            this.dest = dest;
            this.options = options;
        }
    }
}
