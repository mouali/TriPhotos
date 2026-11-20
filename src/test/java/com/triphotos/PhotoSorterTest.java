package com.triphotos;

import com.triphotos.PhotoSorter.Mode;
import com.triphotos.PhotoSorter.Options;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PhotoSorterTest {

    private static final Instant JULY_2019 = Instant.parse("2019-07-14T10:00:00Z");
    private static final Instant MARCH_2021 = Instant.parse("2021-03-02T10:00:00Z");

    @TempDir
    Path tmp;

    private Path source;
    private Path dest;
    private PhotoSorter sorter;

    @BeforeEach
    void setUp() throws Exception {
        source = Files.createDirectory(tmp.resolve("source"));
        dest = tmp.resolve("dest");
        sorter = new PhotoSorter(new PhotoDateResolver(ZoneId.of("Europe/Paris")),
                message -> { });
    }

    @Test
    void copiesByDefaultIntoYearMonthFoldersCreatedOnTheFly() throws Exception {
        Path a = TestFiles.file(source.resolve("a.jpg"), "A", JULY_2019);
        Path b = TestFiles.file(source.resolve("b.MP4"), "B", MARCH_2021);

        SortReport report = sorter.sort(source, dest, Options.defaults());

        assertEquals(2, report.getSorted());
        assertFalse(report.hasErrors());
        assertEquals("A", Files.readString(dest.resolve("2019/07/a.jpg")));
        assertEquals("B", Files.readString(dest.resolve("2021/03/b.MP4")));
        assertTrue(Files.exists(a) && Files.exists(b), "la source doit rester intacte en mode copie");
        assertEquals(Files.getLastModifiedTime(a), Files.getLastModifiedTime(dest.resolve("2019/07/a.jpg")),
                "la date du fichier est conservée");
    }

    @Test
    void moveOptionRemovesFilesFromSource() throws Exception {
        Path a = TestFiles.file(source.resolve("a.jpg"), "A", JULY_2019);

        sorter.sort(source, dest, Options.defaults().withMode(Mode.MOVE));

        assertFalse(Files.exists(a));
        assertTrue(Files.exists(dest.resolve("2019/07/a.jpg")));
    }

    @Test
    void dryRunChangesNothing() throws Exception {
        Path a = TestFiles.file(source.resolve("a.jpg"), "A", JULY_2019);

        SortReport report = sorter.sort(source, dest, Options.defaults().withMode(Mode.MOVE).withDryRun());

        assertEquals(1, report.getSorted());
        assertTrue(Files.exists(a));
        assertFalse(Files.exists(dest));
    }

    @Test
    void identicalFileAlreadyInDestinationIsSkipped() throws Exception {
        TestFiles.file(source.resolve("a.jpg"), "A", JULY_2019);
        TestFiles.file(dest.resolve("2019/07/a.jpg"), "A", JULY_2019);

        SortReport report = sorter.sort(source, dest, Options.defaults());

        assertEquals(0, report.getSorted());
        assertEquals(1, report.getDuplicates());
        assertFalse(Files.exists(dest.resolve("2019/07/a_1.jpg")));
    }

    @Test
    void differentFileWithSameNameGetsSuffix() throws Exception {
        TestFiles.file(source.resolve("a.jpg"), "nouveau", JULY_2019);
        TestFiles.file(dest.resolve("2019/07/a.jpg"), "ancien", JULY_2019);

        SortReport report = sorter.sort(source, dest, Options.defaults());

        assertEquals(1, report.getSorted());
        assertEquals("ancien", Files.readString(dest.resolve("2019/07/a.jpg")));
        assertEquals("nouveau", Files.readString(dest.resolve("2019/07/a_1.jpg")));
    }

    @Test
    void nonMediaFilesAreIgnored() throws Exception {
        TestFiles.file(source.resolve("notes.txt"), "x", JULY_2019);
        TestFiles.file(source.resolve("Thumbs.db"), "x", JULY_2019);

        SortReport report = sorter.sort(source, dest, Options.defaults());

        assertEquals(0, report.getSorted());
        assertEquals(2, report.getSkipped());
        assertFalse(Files.exists(dest));
    }

    @Test
    void missingSourceFolderIsReportedClearly() {
        assertThrows(IllegalArgumentException.class,
                () -> sorter.sort(source.resolve("inexistant"), dest, Options.defaults()));
    }

    @Test
    void subfoldersAreOnlyProcessedInRecursiveMode() throws Exception {
        TestFiles.file(source.resolve("a.jpg"), "A", JULY_2019);
        TestFiles.file(source.resolve("vacances/b.jpg"), "B", MARCH_2021);

        assertEquals(1, sorter.sort(source, dest, Options.defaults().withDryRun()).getSorted());
        assertEquals(2, sorter.sort(source, dest, Options.defaults().withRecursive()).getSorted());
        assertTrue(Files.exists(dest.resolve("2021/03/b.jpg")));
    }

    @Test
    void sameNameInDifferentSubfoldersDoesNotCollideEvenInDryRun() throws Exception {
        TestFiles.file(source.resolve("x/a.jpg"), "1", JULY_2019);
        TestFiles.file(source.resolve("y/a.jpg"), "2", JULY_2019);

        assertEquals(2, sorter.sort(source, dest, Options.defaults().withRecursive().withDryRun()).getSorted());
        sorter.sort(source, dest, Options.defaults().withRecursive());
        assertTrue(Files.exists(dest.resolve("2019/07/a.jpg")));
        assertTrue(Files.exists(dest.resolve("2019/07/a_1.jpg")));
    }

    @Test
    void dryRunDetectsDuplicatesWithinTheSameRunLikeARealRun() throws Exception {
        TestFiles.file(source.resolve("x/a.jpg"), "même contenu", JULY_2019);
        TestFiles.file(source.resolve("y/a.jpg"), "même contenu", JULY_2019);
        Options options = Options.defaults().withRecursive();

        SortReport simulated = sorter.sort(source, dest, options.withDryRun());
        assertEquals(1, simulated.getSorted());
        assertEquals(1, simulated.getDuplicates());

        SortReport real = sorter.sort(source, dest, options);
        assertEquals(1, real.getSorted());
        assertEquals(1, real.getDuplicates());
        assertFalse(Files.exists(dest.resolve("2019/07/a_1.jpg")));
    }

    @Test
    void destinationInsideSourceIsNotReprocessed() throws Exception {
        Path inner = source.resolve("tri");
        TestFiles.file(inner.resolve("2019/07/old.jpg"), "old", JULY_2019);
        TestFiles.file(source.resolve("a.jpg"), "A", MARCH_2021);

        SortReport report = sorter.sort(source, inner, Options.defaults().withRecursive());

        assertEquals(1, report.getSorted());
        assertFalse(Files.exists(inner.resolve("2019/07/2019")));
    }

    @Test
    void folderNamesUseAsciiDigitsWhateverTheDefaultLocale() throws Exception {
        TestFiles.file(source.resolve("a.jpg"), "A", JULY_2019);
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("ar-EG")); // chiffres arabo-indiens par défaut
        try {
            sorter.sort(source, dest, Options.defaults());
        } finally {
            Locale.setDefault(previous);
        }

        assertTrue(Files.exists(dest.resolve("2019/07/a.jpg")));
    }

    @Test
    void unreadableSubfolderIsReportedWithoutStoppingTheSort() throws Exception {
        TestFiles.file(source.resolve("a.jpg"), "A", JULY_2019);
        Path locked = Files.createDirectory(source.resolve("verrouille"));
        TestFiles.file(locked.resolve("b.jpg"), "B", JULY_2019);

        try (TestFiles.Restorer ignored = TestFiles.denyListing(locked)) {
            assumeTrue(isUnlistable(locked), "impossible de rendre un dossier illisible ici");

            SortReport report = sorter.sort(source, dest, Options.defaults().withRecursive());

            assertEquals(1, report.getSorted());
            assertEquals(1, report.getErrors().size());
            assertTrue(report.getErrors().get(0).contains("verrouille"));
        }
    }

    private static boolean isUnlistable(Path dir) {
        try (var stream = Files.list(dir)) {
            stream.count();
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    @Test
    void suffixIsInsertedBeforeExtension() {
        assertEquals("IMG_1_2.jpg", PhotoSorter.withSuffix("IMG_1.jpg", 2));
        assertEquals("README_1", PhotoSorter.withSuffix("README", 1));
        assertEquals(".hidden_1", PhotoSorter.withSuffix(".hidden", 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a.jpg", "A.JPG", "clip.Mov", "raw.CR3", "img.avif"})
    void recognisesMediaExtensionsCaseInsensitively(String name) {
        assertTrue(PhotoSorter.isMedia(Path.of(name)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"notes.txt", "jpg", ".jpg", "archive.jpg.zip"})
    void rejectsNonMediaFiles(String name) {
        assertFalse(PhotoSorter.isMedia(Path.of(name)));
    }
}
