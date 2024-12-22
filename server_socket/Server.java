import java.io.*;
import java.net.*;
import java.util.*;

public class Server {
    private ServerSocket serverSocket;
    private ServerSocket fileServerSocket; // Serveur pour les fichiers
    private final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());

    // Répertoire où les fichiers sont stockés sur le serveur
    private static final String FILES_DIRECTORY = "./server_files";

    public Server(int port) {
        try {
            serverSocket = new ServerSocket(port);
            fileServerSocket = new ServerSocket(1235); // Serveur fichiers sur port 1235
            System.out.println("Serveur démarré sur le port " + port);
            System.out.println("Serveur de fichiers démarré sur le port 1235");

            // Gestion des clients normaux
            new Thread(this::acceptClients).start();

            // Gestion des transferts de fichiers
            new Thread(this::acceptFileTransfers).start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void acceptClients() {
        try {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Nouveau client connecté: " + clientSocket.getInetAddress());
                ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                clients.add(clientHandler);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void acceptFileTransfers() {
        try {
            while (true) {
                Socket fileSocket = fileServerSocket.accept();
                System.out.println("Connexion pour transfert de fichier: " + fileSocket.getInetAddress());
                new Thread(() -> handleFileTransferOrDownload(fileSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleFileTransferOrDownload(Socket fileSocket) {
        try (DataInputStream dataIn = new DataInputStream(fileSocket.getInputStream());
                DataOutputStream dataOut = new DataOutputStream(fileSocket.getOutputStream())) {

            String action = dataIn.readUTF(); // "UPLOAD" ou "DOWNLOAD"

            if (action.equals("UPLOAD")) {
                handleFileUpload(dataIn, dataOut);
            } else if (action.equals("DOWNLOAD")) {
                handleFileDownload(dataIn, dataOut);
            } else {
                dataOut.writeUTF("ERROR: Action non reconnue.");
            }

        } catch (IOException e) {
            System.err.println("Erreur lors du transfert de fichier : " + e.getMessage());
        }
    }

    private void handleFileUpload(DataInputStream dataIn, DataOutputStream dataOut) throws IOException {
        String fileName = dataIn.readUTF();
        String destinationPath = FILES_DIRECTORY; // Sauvegarde dans le répertoire par défaut
        long fileSize = dataIn.readLong();

        saveFile(fileName, destinationPath, fileSize, dataIn);

        // Confirmation d'envoi au client
        dataOut.writeUTF("Fichier '" + fileName + "' transféré avec succès à l'emplacement : " + destinationPath);
        System.out.println("Fichier '" + fileName + "' enregistré dans : " + destinationPath);

        // Diffusion du message à tous les clients
        broadcast("Fichier transféré : " + fileName, null); // `null` ici représente le fait que ce message n'a pas
                                                            // d'émetteur spécifique.
    }

    private String getLastUploadedFile() {
        File directory = new File(FILES_DIRECTORY);
        File[] files = directory.listFiles(File::isFile);

        if (files == null || files.length == 0) {
            return null; // Aucun fichier trouvé
        }

        // Trier les fichiers par date de dernière modification
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        return files[0].getName(); // Retourne le fichier le plus récent
    }

    private void handleFileDownload(DataInputStream dataIn, DataOutputStream dataOut) throws IOException {
        // Récupérer le dernier fichier
        String fileName = getLastUploadedFile();

        if (fileName == null) {
            dataOut.writeUTF("ERROR: Aucun fichier disponible pour téléchargement.");
            System.out.println("Aucun fichier disponible pour le téléchargement.");
            return;
        }

        File file = new File(FILES_DIRECTORY, fileName);

        // Envoyer les métadonnées du fichier
        dataOut.writeUTF("SUCCESS");
        dataOut.writeUTF(file.getName());
        dataOut.writeLong(file.length());

        // Envoyer le contenu du fichier
        try (FileInputStream fileIn = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fileIn.read(buffer)) > 0) {
                dataOut.write(buffer, 0, bytesRead);
            }
        }
        System.out.println("Fichier envoyé au client : " + fileName);
    }

    public synchronized void broadcast(String message, ClientHandler sender) {
        for (ClientHandler client : clients) {
            if (client != sender) {
                client.sendMessage(message);
            }
        }
    }

    public void removeClient(ClientHandler client) {
        clients.remove(client);
        System.out.println("Client déconnecté.");
    }

    public void saveFile(String fileName, String destinationPath, long fileSize, DataInputStream dataIn)
            throws IOException {
        // Créer le répertoire si nécessaire
        File destinationDir = new File(destinationPath);
        if (!destinationDir.exists()) {
            destinationDir.mkdirs(); // Crée les répertoires si non existants
        }

        File file = new File(destinationDir, fileName);
        try (FileOutputStream fileOut = new FileOutputStream(file)) {
            byte[] buffer = new byte[4096];
            long totalRead = 0;
            int bytesRead;
            while (totalRead < fileSize && (bytesRead = dataIn.read(buffer)) > 0) {
                fileOut.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
            }
            System.out.println("Fichier sauvegardé dans : " + file.getAbsolutePath());
        }
    }

    public static void main(String[] args) {
        // Crée le répertoire de fichiers par défaut s'il n'existe pas
        File fileDir = new File(FILES_DIRECTORY);
        if (!fileDir.exists()) {
            fileDir.mkdirs();
        }

        new Server(1234); // Port principal pour les messages
    }
}
