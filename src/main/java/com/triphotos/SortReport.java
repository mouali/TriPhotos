package com.triphotos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Bilan d'une exécution du tri. */
public class SortReport {

    private int sorted;
    private int skipped;
    private int duplicates;
    private final List<String> errors = new ArrayList<>();

    void addSorted() {
        sorted++;
    }

    void addSkipped() {
        skipped++;
    }

    void addDuplicate() {
        duplicates++;
    }

    void addError(String message) {
        errors.add(message);
    }

    /** Fichiers copiés/déplacés (ou qui le seraient en mode simulation). */
    public int getSorted() {
        return sorted;
    }

    /** Fichiers ignorés car ce ne sont pas des photos/vidéos. */
    public int getSkipped() {
        return skipped;
    }

    /** Fichiers déjà présents à l'identique dans la destination. */
    public int getDuplicates() {
        return duplicates;
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Triés : ").append(sorted)
          .append(" | Doublons ignorés : ").append(duplicates)
          .append(" | Non-médias ignorés : ").append(skipped)
          .append(" | Erreurs : ").append(errors.size());
        for (String e : errors) {
            sb.append(System.lineSeparator()).append("  - ").append(e);
        }
        return sb.toString();
    }
}
