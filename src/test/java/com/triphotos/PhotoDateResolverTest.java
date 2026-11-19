package com.triphotos;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class PhotoDateResolverTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final PhotoDateResolver resolver = new PhotoDateResolver(ZoneId.of("Europe/Paris"));

    @Test
    public void exifDateTimeOriginalTakesPriorityOverFileDate() throws Exception {
        Path photo = TestFiles.jpegWithExif(tmp.getRoot().toPath().resolve("IMG_0001.jpg"),
                "2019:07:14 18:05:30", Instant.parse("2024-01-01T12:00:00Z"));

        assertEquals(Optional.of(LocalDateTime.of(2019, 7, 14, 18, 5, 30)), resolver.resolve(photo));
    }

    @Test
    public void fallsBackToFileDateInLocalTimeZone() throws Exception {
        // 30 juin 22:30 UTC = 1er juillet 00:30 à Paris : la photo doit aller en juillet.
        Path photo = TestFiles.file(tmp.getRoot().toPath().resolve("no_exif.jpg"), "pas un vrai jpeg",
                Instant.parse("2019-06-30T22:30:00Z"));

        assertEquals(Optional.of(LocalDateTime.of(2019, 7, 1, 0, 30)), resolver.resolve(photo));
    }

    @Test
    public void parseExifRejectsEmptyOrInvalidValues() {
        assertFalse(PhotoDateResolver.parseExif(null).isPresent());
        assertFalse(PhotoDateResolver.parseExif("0000:00:00 00:00:00").isPresent());
        assertFalse(PhotoDateResolver.parseExif("n'importe quoi").isPresent());
        assertEquals(Optional.of(LocalDateTime.of(2020, 2, 29, 23, 59, 59)),
                PhotoDateResolver.parseExif("2020:02:29 23:59:59"));
    }
}
