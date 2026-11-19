package com.triphotos;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final String USAGE = String.join(System.lineSeparator(),
            "Usage : java -jar triphotos.jar <source> <destination> [options]",
            "",
            "Range les photos et vidéos de <source> dans <destination>/AAAA/MM/",
            "selon leur date de prise de vue (EXIF), ou à défaut la date du fichier.",
            "",
            "Options :",
            "  --move        déplace les fichiers au lieu de les copier",
            "  --dry-run     simule le tri sans rien copier ni déplacer",
            "  --recursive   traite aussi les sous-dossiers de <source>",
            "  --help        affiche cette aide");

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        boolean move = false;
        boolean dryRun = false;
        boolean recursive = false;
        List<String> positional = new ArrayList<>();

        for (String arg : args) {
            switch (arg) {
                case "--move" -> move = true;
                case "--dry-run" -> dryRun = true;
                case "--recursive", "-r" -> recursive = true;
                case "--help", "-h" -> {
                    System.out.println(USAGE);
                    return 0;
                }
                default -> {
                    if (arg.startsWith("--")) {
                        System.err.println("Option inconnue : " + arg);
                        System.err.println(USAGE);
                        return 2;
                    }
                    positional.add(arg);
                }
            }
        }
        if (positional.size() != 2) {
            System.err.println(USAGE);
            return 2;
        }

        try {
            SortReport report = new PhotoSorter().sort(Path.of(positional.get(0)), Path.of(positional.get(1)),
                    new PhotoSorter.Options(move, dryRun, recursive));
            System.out.println(report);
            return report.hasErrors() ? 1 : 0;
        } catch (Exception e) {
            System.err.println("Erreur : " + e.getMessage());
            return 1;
        }
    }
}
