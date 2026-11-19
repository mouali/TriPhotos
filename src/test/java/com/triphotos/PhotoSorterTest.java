package com.triphotos;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PhotoSorterTest {

    private static final Instant JULY_2019 = Instant.parse("2019-07-14T10:00:00Z");
    private static final Instant MARCH_2021 = Instant.parse("2021-03-02T10:00:00Z");

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private Path source;
    private Path dest;
    private PhotoSorter sorter;

    @Before
    public void setUp() throws Exception {
        source = tmp.newFolder("source").toPath();
        dest = tmp.getRoot().toPath().resolve("dest");
        sorter = new PhotoSorter(new PhotoDateResolver(ZoneId.of("Europe/Paris")),
                new PrintStream(new ByteArrayOutputStream()));
    }

    private static PhotoSorter.Options options(boolean move, boolean dryRun, boolean recursive) {
        return new PhotoSorter.Options(move, dryRun, recursive);
    }

    @Test
    public void copiesByDefaultIntoYearMonthFoldersCreatedOnTheFly() throws Exception {
        Path a = TestFiles.file(source.resolve("a.jpg"), "A", JULY_2019);
        Path b = TestFiles.file(source.resolve("b.MP4"), "B", MARCH_2021);

        SortReport report = sorter.sort(source, dest, PhotoSorter.Options.defaults());

        assertEquals(2, report.getSorted());
        assertFalse(report.hasErrors());
        assertEquals("A", Files.readString(dest.resolve("2019/07/a.jpg")));
        assertEquals("B", Files.readString(dest.resolve("2021/03/b.MP4")));
        assertTrue("la source doit rester intacte en mode copie", Files.exists(a) && Files.exists(b));
        assertEquals("la date du fichier est conservée", Files.getLastModifiedTime(a),
                Files.getLastModifiedTime(dest.resolve("2019/07/a.jpg")));
    }

    @Test
    public void moveOptionRemovesFilesFromSource() throws Exception {
        Path a = TestFiles.file(source.resolve("a.jpg"), "A", JULY_2019);

        sorter.sort(source, dest, options(true, false, false));

        assertFalse(Files.exists(a));
        assertTrue(Files.exists(dest.resolve("2019/07/a.jpg")));
    }

    @Test
    public void dryRunChangesNothing() throws Exception {
        Path a = TestFiles.file(source.resolve("a.jpg"), "A", JULY_2019);

        SortReport report = sorter.sort(source, dest, options(true, true, false));

        assertEquals(1, report.getSorted());
        assertTrue(Files.exists(a));
        assertFalse(Files.exists(dest));
    }

    @Test
    public void identicalFileAlreadyInDestinationIsSkipped() throws Exception {
        TestFiles.file(source.resolve("a.jpg"), "A", JULY_2019);
        TestFiles.file(dest.resolve("2019/07/a.jpg"), "A", JULY_2019);

        SortReport report = sorter.sort(source, dest, PhotoSorter.Options.defaults());

        assertEquals(0, report.getSorted());
        assertEquals(1, report.getDuplicates());
        assertFalse(Files.exists(dest.resolve("2019/07/a_1.jpg")));
    }

    @Test
    public void differentFileWithSameNameGetsSuffix() throws Exception {
        TestFiles.file(source.resolve("a.jpg"), "nouveau", JULY_2019);
        TestFiles.file(dest.resolve("2019/07/a.jpg"), "ancien", JULY_2019);

        SortReport report = sorter.sort(source, dest, PhotoSorter.Options.defaults());

        assertEquals(1, report.getSorted());
        assertEquals("ancien", Files.readString(dest.resolve("2019/07/a.jpg")));
        assertEquals("nouveau", Files.readString(dest.resolve("2019/07/a_1.jpg")));
    }

    @Test
    public void nonMediaFilesAreIgnored() throws Exception {
        TestFiles.file(source.resolve("notes.txt"), "x", JULY_2019);
        TestFiles.file(source.resolve("Thumbs.db"), "x", JULY_2019);

        SortReport report = sorter.sort(source, dest, PhotoSorter.Options.defaults());

        assertEquals(0, report.getSorted());
        assertEquals(2, report.getSkipped());
        assertFalse(Files.exists(dest));
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingSourceFolderIsReportedClearly() throws Exception {
        sorter.sort(source.resolve("inexistant"), dest, PhotoSorter.Options.defaults());
    }

    @Test
    public void subfoldersAreOnlyProcessedInRecursiveMode() throws Exception {
        TestFiles.file(source.resolve("a.jpg"), "A", JULY_2019);
        TestFiles.file(source.resolve("vacances/b.jpg"), "B", MARCH_2021);

        assertEquals(1, sorter.sort(source, dest, options(false, true, false)).getSorted());
        assertEquals(2, sorter.sort(source, dest, options(false, false, true)).getSorted());
        assertTrue(Files.exists(dest.resolve("2021/03/b.jpg")));
    }

    @Test
    public void sameNameInDifferentSubfoldersDoesNotCollideEvenInDryRun() throws Exception {
        TestFiles.file(source.resolve("x/a.jpg"), "1", JULY_2019);
        TestFiles.file(source.resolve("y/a.jpg"), "2", JULY_2019);

        assertEquals(2, sorter.sort(source, dest, options(false, true, true)).getSorted());
        sorter.sort(source, dest, options(false, false, true));
        assertTrue(Files.exists(dest.resolve("2019/07/a.jpg")));
        assertTrue(Files.exists(dest.resolve("2019/07/a_1.jpg")));
    }

    @Test
    public void destinationInsideSourceIsNotReprocessed() throws Exception {
        Path inner = source.resolve("tri");
        TestFiles.file(inner.resolve("2019/07/old.jpg"), "old", JULY_2019);
        TestFiles.file(source.resolve("a.jpg"), "A", MARCH_2021);

        SortReport report = sorter.sort(source, inner, options(false, false, true));

        assertEquals(1, report.getSorted());
        assertFalse(Files.exists(inner.resolve("2019/07/2019")));
    }

    @Test
    public void suffixIsInsertedBeforeExtension() {
        assertEquals("IMG_1_2.jpg", PhotoSorter.withSuffix("IMG_1.jpg", 2));
        assertEquals("README_1", PhotoSorter.withSuffix("README", 1));
        assertEquals(".hidden_1", PhotoSorter.withSuffix(".hidden", 1));
    }
}
