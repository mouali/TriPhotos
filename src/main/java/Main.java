public class Main {

    public static void main(String[] args){

        ManageFiles manageFiles = new ManageFiles();

        //String sourcePath = "C:\\Users\\Mohamed OUALI\\Documents\\DEV\\Tests\\TestFolder\\Mo_Telephone_20221103";
        //String destPath = "C:\\Users\\Mohamed OUALI\\Documents\\DEV\\Tests\\OutcomeFolder\\Mo_Telephone_20221103";

        String sourcePath = "C:\\Users\\Mohamed OUALI\\Documents\\DEV\\Tests\\TestFolder\\TelMo_20251212";
        String destPath = "C:\\Users\\Mohamed OUALI\\Documents\\DEV\\Tests\\OutcomeFolder\\Telephone_OK";

        manageFiles.moveFiles(sourcePath, destPath);

    }

}
