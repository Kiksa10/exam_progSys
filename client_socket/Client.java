import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Client {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private static Config conf = new Config();
    private String username; // Variable pour stocker le nom d'utilisateur

    public Client(String host, int port) {
        try {
            Scanner scanner = new Scanner(System.in);

            // Demander le nom d'utilisateur au démarrage
            System.out.print("Entrez votre nom d'utilisateur : ");
            username = scanner.nextLine().trim();
            if (username.isEmpty()) {
                System.out.println("Nom d'utilisateur invalide. Fermeture...");
                return;
            }

            // Connexion au serveur
            socket = new Socket(host, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // Lancer le thread pour écouter les messages du serveur
            new Thread(this::listenToServer).start();

            // Gérer les entrées de l'utilisateur directement sur le terminal
            handleUserInput(scanner);

        } catch (IOException e) {
            System.out.println("Erreur de connexion au serveur : " + e.getMessage());
        }
    }

    private void listenToServer() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                System.out.println(message); // Affichage des messages reçus du serveur
            }
        } catch (IOException e) {
            System.out.println("Connexion au serveur perdue.");
        }
    }

    private void handleUserInput(Scanner scanner) {
        System.out.println("Vous pouvez commencer à écrire des messages...");
        System.out.println("Tapez 'QUIT' pour quitter la discussion.");
        System.out.println("Commandes disponibles :");
        System.out.println("1. UPLOAD <chemin du fichier>");
        System.out.println("2. DOWNLOAD");

        try {
            // L'utilisateur peut envoyer des messages directement
            while (true) {
                System.out.print(username + " > ");
                String command = scanner.nextLine().trim();

                if (command.equalsIgnoreCase("QUIT")) {
                    System.out.println("Déconnexion...");
                    break;
                } else if (command.startsWith("UPLOAD ")) {
                    // Envoi d'un fichier
                    String filePath = command.substring(7).trim();
                    File file = new File(filePath);
                    if (file.exists()) {
                        try {
                            sendFileToServer(file);
                            System.out.println("Fichier transféré : " + file.getName());
                        } catch (IOException e) {
                            System.out.println("Erreur de transfert de fichier : " + e.getMessage());
                        }
                    } else {
                        System.out.println("Fichier introuvable : " + filePath);
                    }
                } else if (command.equalsIgnoreCase("DOWNLOAD")) {
                    // Télécharger le dernier fichier
                    try {
                        downloadLastUploadedFileFromServer();
                    } catch (IOException e) {
                        System.out.println("Erreur lors du téléchargement : " + e.getMessage());
                    }
                } else {
                    // Envoi d'un message
                    out.println(username + ": " + command);
                }
            }
        } catch (Exception e) {
            System.out.println("Une erreur s'est produite lors de l'envoi du message : " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    private void sendFileToServer(File file) throws IOException {
        try (Socket fileSocket = new Socket(conf.getHost(), conf.getPortTransfer());
                DataOutputStream fileOut = new DataOutputStream(fileSocket.getOutputStream());
                FileInputStream fileIn = new FileInputStream(file)) {

            fileOut.writeUTF("UPLOAD");
            fileOut.writeUTF(file.getName());
            fileOut.writeLong(file.length());

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fileIn.read(buffer)) > 0) {
                fileOut.write(buffer, 0, bytesRead);
            }
        }
    }

    private void downloadLastUploadedFileFromServer() throws IOException {
        // Demander au serveur de télécharger le dernier fichier uploadé
        String defaultPath = "./downloads";
        File dir = new File(defaultPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        try (Socket fileSocket = new Socket(conf.getHost(), conf.getPortTransfer());
                DataOutputStream fileOut = new DataOutputStream(fileSocket.getOutputStream());
                DataInputStream fileIn = new DataInputStream(fileSocket.getInputStream())) {

            fileOut.writeUTF("DOWNLOAD");

            String serverResponse = fileIn.readUTF();
            if (serverResponse.equals("ERROR")) {
                System.out.println("Aucun fichier disponible pour le téléchargement.");
                return;
            }

            String serverFileName = fileIn.readUTF();
            long fileSize = fileIn.readLong();
            File fileToSave = new File(dir, serverFileName);

            try (FileOutputStream fileOutStream = new FileOutputStream(fileToSave)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalRead = 0;

                while ((bytesRead = fileIn.read(buffer)) > 0) {
                    fileOutStream.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;
                    if (totalRead >= fileSize)
                        break;
                }

                System.out.println("Fichier téléchargé avec succès : " + fileToSave.getAbsolutePath());
            }
        }
    }

    private void closeConnection() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
        } catch (IOException e) {
            System.out.println("Erreur lors de la fermeture des ressources : " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        new Client(conf.getHost(), conf.getPortMessage());
    }
}
