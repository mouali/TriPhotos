package com.triphotos;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Point d'entrée en ligne de commande. */
public final class Main {

    static final int EXIT_OK = 0;
    static final int EXIT_ERROR = 1;
    static final int EXIT_USAGE = 2;

    private static final String USAGE = String.join(System.lineSeparator(),
            "Usage : java -jar triphotos.jar <source> <destination> [options]",
            "        java -jar triphotos.jar            (sans argument : interface graphique)",
            "",
            "Range les photos et vidéos de <source> dans <destination>/AAAA/MM/",
            "selon leur date de prise de vue (EXIF), ou à défaut la date du fichier.",
            "",
            "Options :",
            "  --move        déplace les fichiers au lieu de les copier",
            "  --dry-run     simule le tri sans rien copier ni déplacer",
            "  --recursive   traite aussi les sous-dossiers de <source>",
            "  --help        affiche cette aide");

    private Main() {
    }

    public static void main(String[] args) {
        if (shouldOpenGui(args, GraphicsEnvironment.isHeadless())) {
            SortWindow.open();
            return;
        }
        System.exit(run(args));
    }

    /** Sans argument et avec un écran disponible, on ouvre l'interface graphique. */
    static boolean shouldOpenGui(String[] args, boolean headless) {
        return args.length == 0 && !headless;
    }

    static int run(String[] args) {
        PhotoSorter.Options options = PhotoSorter.Options.defaults();
        List<String> positional = new ArrayList<>();

        for (String arg : args) {
            switch (arg) {
                case "--move" -> options = options.withMode(PhotoSorter.Mode.MOVE);
                case "--dry-run" -> options = options.withDryRun();
                case "--recursive", "-r" -> options = options.withRecursive();
                case "--help", "-h" -> {
                    System.out.println(USAGE);
                    return EXIT_OK;
                }
                default -> {
                    if (arg.startsWith("--")) {
                        System.err.println("Option inconnue : " + arg);
                        System.err.println(USAGE);
                        return EXIT_USAGE;
                    }
                    positional.add(arg);
                }
            }
        }
        if (positional.size() != 2) {
            System.err.println(USAGE);
            return EXIT_USAGE;
        }

        try {
            SortReport report = new PhotoSorter().sort(Path.of(positional.get(0)), Path.of(positional.get(1)), options);
            System.out.println(report);
            return report.hasErrors() ? EXIT_ERROR : EXIT_OK;
        } catch (IOException | UncheckedIOException | IllegalArgumentException e) {
            // Erreurs attendues (chemin invalide, disque…) ; toute autre exception est un bug et garde sa trace.
            System.err.println("Erreur : " + describe(e));
            return EXIT_ERROR;
        }
    }

    /** Message lisible pour l'utilisateur, même quand l'exception n'a pas de message. */
    static String describe(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
