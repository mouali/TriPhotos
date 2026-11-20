package com.triphotos;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;

/** Fenêtre de TriPhotos : choix des dossiers et des options, lancement du tri et journal. */
final class SortWindow {

    private static final String PREF_SOURCE = "source";
    private static final String PREF_DESTINATION = "destination";

    private final Preferences prefs = Preferences.userNodeForPackage(SortWindow.class);

    private final JFrame frame = new JFrame("TriPhotos");
    private final JTextField sourceField = new JTextField(40);
    private final JTextField destinationField = new JTextField(40);
    private final JButton sourceButton = new JButton("Parcourir…");
    private final JButton destinationButton = new JButton("Parcourir…");
    private final JCheckBox moveBox = new JCheckBox("Déplacer au lieu de copier");
    private final JCheckBox dryRunBox = new JCheckBox("Simulation (ne rien modifier)", true);
    private final JCheckBox recursiveBox = new JCheckBox("Inclure les sous-dossiers");
    private final JButton startButton = new JButton("Lancer le tri");
    private final JTextArea logArea = new JTextArea(16, 70);
    private final JProgressBar progress = new JProgressBar();

    private boolean running;

    private SortWindow() {
        sourceField.setText(prefs.get(PREF_SOURCE, ""));
        destinationField.setText(prefs.get(PREF_DESTINATION, ""));
        sourceButton.addActionListener(e -> chooseFolder(sourceField, "Dossier source"));
        destinationButton.addActionListener(e -> chooseFolder(destinationField, "Dossier de destination"));
        startButton.addActionListener(e -> start());

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        progress.setIndeterminate(true);
        progress.setVisible(false);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(buildForm(), BorderLayout.NORTH);
        content.add(new JScrollPane(logArea), BorderLayout.CENTER);
        content.add(progress, BorderLayout.SOUTH);

        frame.setContentPane(content);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                close();
            }
        });
        frame.pack();
        frame.setLocationRelativeTo(null);
    }

    /** Ouvre la fenêtre (appelable depuis n'importe quel thread). */
    static void open() {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException e) {
                // Apparence par défaut de Swing : sans conséquence.
            }
            new SortWindow().frame.setVisible(true);
        });
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;

        addFolderRow(form, c, 0, "Dossier source :", sourceField, sourceButton);
        addFolderRow(form, c, 1, "Dossier de destination :", destinationField, destinationButton);

        JPanel options = new JPanel();
        options.add(moveBox);
        options.add(dryRunBox);
        options.add(recursiveBox);
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 3;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        form.add(options, c);

        c.gridy = 3;
        c.anchor = GridBagConstraints.CENTER;
        form.add(startButton, c);
        return form;
    }

    private static void addFolderRow(JPanel form, GridBagConstraints c, int row, String label,
                                     JTextField field, JButton button) {
        c.gridy = row;
        c.gridwidth = 1;
        c.gridx = 0;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        form.add(new JLabel(label), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        form.add(field, c);
        c.gridx = 2;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        form.add(button, c);
    }

    private void chooseFolder(JTextField field, String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        String current = field.getText().trim();
        if (!current.isEmpty()) {
            chooser.setCurrentDirectory(new File(current));
        }
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            field.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void start() {
        Path source;
        Path destination;
        try {
            source = toPath(sourceField, "le dossier source");
            destination = toPath(destinationField, "le dossier de destination");
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
            return;
        }
        if (!Files.isDirectory(source)) {
            showError("Le dossier source n'existe pas : " + source);
            return;
        }
        if (source.toAbsolutePath().normalize().equals(destination.toAbsolutePath().normalize())) {
            showError("Le dossier de destination doit être différent du dossier source.");
            return;
        }

        PhotoSorter.Options options = PhotoSorter.Options.defaults();
        if (moveBox.isSelected()) {
            options = options.withMode(PhotoSorter.Mode.MOVE);
        }
        if (dryRunBox.isSelected()) {
            options = options.withDryRun();
        }
        if (recursiveBox.isSelected()) {
            options = options.withRecursive();
        }
        if (options.mode() == PhotoSorter.Mode.MOVE && !options.dryRun() && !confirm(
                "Les fichiers vont être DÉPLACÉS hors de :\n" + source + "\n\nContinuer ?")) {
            return;
        }

        prefs.put(PREF_SOURCE, source.toString());
        prefs.put(PREF_DESTINATION, destination.toString());
        logArea.setText("");
        setRunning(true);
        new SortTask(source, destination, options).execute();
    }

    private static Path toPath(JTextField field, String what) {
        String text = field.getText().trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Veuillez choisir " + what + ".");
        }
        try {
            return Path.of(text);
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("Chemin invalide pour " + what + " : " + text);
        }
    }

    private void setRunning(boolean value) {
        running = value;
        for (var component : List.of(sourceField, destinationField, sourceButton, destinationButton,
                moveBox, dryRunBox, recursiveBox, startButton)) {
            component.setEnabled(!value);
        }
        progress.setVisible(value);
        frame.getContentPane().revalidate();
    }

    private void appendLog(String line) {
        logArea.append(line + System.lineSeparator());
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void close() {
        if (running && !confirm("Un tri est en cours. Quitter quand même ?")) {
            return;
        }
        frame.dispose();
        System.exit(Main.EXIT_OK);
    }

    private boolean confirm(String message) {
        return JOptionPane.showConfirmDialog(frame, message, "TriPhotos", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(frame, message, "TriPhotos", JOptionPane.ERROR_MESSAGE);
    }

    /** Tri exécuté hors du thread graphique ; chaque ligne de journal est transmise à la fenêtre. */
    private final class SortTask extends SwingWorker<SortReport, String> {

        private final Path source;
        private final Path destination;
        private final PhotoSorter.Options options;

        SortTask(Path source, Path destination, PhotoSorter.Options options) {
            this.source = source;
            this.destination = destination;
            this.options = options;
        }

        @Override
        protected SortReport doInBackground() throws IOException {
            return new PhotoSorter(new PhotoDateResolver(), line -> publish(line))
                    .sort(source, destination, options);
        }

        @Override
        protected void process(List<String> lines) {
            lines.forEach(SortWindow.this::appendLog);
        }

        @Override
        protected void done() {
            setRunning(false);
            try {
                SortReport report = get();
                appendLog("");
                appendLog(report.toString());
                JOptionPane.showMessageDialog(frame, report.toString(), "Tri terminé",
                        report.hasErrors() ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                String message = cause instanceof Exception ex ? Main.describe(ex) : String.valueOf(cause);
                appendLog("Erreur : " + message);
                showError("Erreur : " + message);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
