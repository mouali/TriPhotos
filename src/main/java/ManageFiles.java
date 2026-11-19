import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;

public class ManageFiles {

    public void moveFiles(String sourcePath, String destPath) {
        File dir = new File(sourcePath);
        File[] liste = dir.listFiles();
        System.out.println("Nombre du fichier: " + liste.length);

        String creationYear = null;
        String creationMonth = null;
        String src = null;
        String dest = null;

        for (File item : liste) {
            if (item.isFile()) {
                System.out.format("Nom du fichier: %s%n", item.getName());
                try {
                    Path file = Paths.get(item.getPath());
                    BasicFileAttributes attr = Files.readAttributes(file, BasicFileAttributes.class);
                    String creationDate = attr.lastModifiedTime().toString();
                    //System.out.println("--- Date de creation: " + creationDate.substring(0, 10));

                    creationYear = creationDate.substring(0, 4);
                    creationMonth = creationDate.substring(5, 7);
                    //System.out.println("********** Annee de creation: " + creationYear);
                    //System.out.println("********** Mois de creation: " + creationMonth);

                    src = sourcePath + "\\" + item.getName();
                    dest = destPath + "\\" + creationYear + "\\" + creationMonth + "\\" + item.getName();
                    Path tmp = Files.move(Paths.get(src), Paths.get(dest));

                    if (tmp != null) {
                        System.out.println("Fichier déplacé avec succès");
                    } else {
                        System.out.println("Impossible de déplacer le fichier");
                    }

                } catch (IOException e) {
                    System.out.println(e);
                }
            } else if (item.isDirectory()) {
                System.out.format("Nom du répertoir: %s%n", item.getName());
            }
        }
    }
}
