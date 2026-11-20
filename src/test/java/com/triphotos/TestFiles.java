package com.triphotos;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

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

    /** Remet un fichier dans son état initial (utilisable en try-with-resources). */
    interface Restorer extends AutoCloseable {
        @Override
        void close() throws IOException;
    }

    /**
     * Interdit le listage d'un dossier pour l'utilisateur courant (ACL sous Windows, droits POSIX ailleurs).
     * Si le système ne le permet pas, ne fait rien : le test appelant doit le vérifier.
     */
    static Restorer denyListing(Path dir) throws IOException {
        AclFileAttributeView acl = Files.getFileAttributeView(dir, AclFileAttributeView.class);
        if (acl != null) {
            List<AclEntry> original = acl.getAcl();
            UserPrincipal me = dir.getFileSystem().getUserPrincipalLookupService()
                    .lookupPrincipalByName(System.getProperty("user.name"));
            AclEntry deny = AclEntry.newBuilder()
                    .setType(AclEntryType.DENY)
                    .setPrincipal(me)
                    .setPermissions(AclEntryPermission.READ_DATA)
                    .build();
            List<AclEntry> locked = new ArrayList<>();
            locked.add(deny);
            locked.addAll(original);
            acl.setAcl(locked);
            return () -> acl.setAcl(original);
        }
        PosixFileAttributeView posix = Files.getFileAttributeView(dir, PosixFileAttributeView.class);
        if (posix != null) {
            Set<PosixFilePermission> original = Files.getPosixFilePermissions(dir);
            Files.setPosixFilePermissions(dir, EnumSet.of(PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE));
            return () -> Files.setPosixFilePermissions(dir, original);
        }
        return () -> { };
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
