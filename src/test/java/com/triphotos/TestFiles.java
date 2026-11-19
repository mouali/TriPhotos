package com.triphotos;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

/** Utilitaires de création de fichiers pour les tests. */
final class TestFiles {

    private TestFiles() {
    }

    /** Crée un fichier avec le contenu donné et la date de modification donnée. */
    static Path file(Path path, String content, Instant modified) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        Files.setLastModifiedTime(path, FileTime.from(modified));
        return path;
    }

    /** Crée un vrai JPEG 1x1 contenant un champ EXIF DateTimeOriginal ("yyyy:MM:dd HH:mm:ss"). */
    static Path jpegWithExif(Path path, String dateTimeOriginal, Instant modified) throws IOException {
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "jpg", jpeg);
        byte[] img = jpeg.toByteArray();

        byte[] tiff = exifTiff(dateTimeOriginal);
        byte[] header = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);
        int segmentLength = 2 + header.length + tiff.length;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(img, 0, 2); // SOI
        out.write(0xFF);
        out.write(0xE1); // APP1
        out.write((segmentLength >> 8) & 0xFF);
        out.write(segmentLength & 0xFF);
        out.write(header);
        out.write(tiff);
        out.write(img, 2, img.length - 2);

        Files.createDirectories(path.getParent());
        Files.write(path, out.toByteArray());
        Files.setLastModifiedTime(path, FileTime.from(modified));
        return path;
    }

    /** TIFF big-endian minimal : IFD0 -> pointeur ExifIFD -> DateTimeOriginal. */
    private static byte[] exifTiff(String dateTimeOriginal) {
        byte[] date = (dateTimeOriginal + "\0").getBytes(StandardCharsets.US_ASCII);
        int ifd0 = 8;
        int exifIfd = ifd0 + 18;
        int dateOffset = exifIfd + 18;
        ByteBuffer b = ByteBuffer.allocate(dateOffset + date.length);
        b.put((byte) 'M').put((byte) 'M').putShort((short) 42).putInt(ifd0);
        // IFD0 : 1 entrée, ExifIFDPointer (0x8769), LONG
        b.putShort((short) 1);
        b.putShort((short) 0x8769).putShort((short) 4).putInt(1).putInt(exifIfd);
        b.putInt(0);
        // ExifIFD : 1 entrée, DateTimeOriginal (0x9003), ASCII
        b.putShort((short) 1);
        b.putShort((short) 0x9003).putShort((short) 2).putInt(date.length).putInt(dateOffset);
        b.putInt(0);
        b.put(date);
        return b.array();
    }
}
