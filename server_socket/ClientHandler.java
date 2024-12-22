import java.io.*;
import java.net.*;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final Server server;
    private PrintWriter out;
    private BufferedReader in;

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            String message;
            while ((message = in.readLine()) != null) {
                System.out.println("Message reçu: " + message);

                // Diffuser les messages de transfert de fichier
                if (message.startsWith("Fichier transféré:")) {
                    server.broadcast(message, this);
                } else {
                    // Diffuser les autres messages
                    server.broadcast(message, this);
                }
            }
        } catch (IOException e) {
            System.out.println("Client déconnecté: " + socket.getInetAddress());
        } finally {
            server.removeClient(this);
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Envoi d'un message au client
    public void sendMessage(String message) {
        out.println(message);
    }

    // Gestion de l'UPLOAD (envoi du client vers le serveur)
    private void handleFileUpload() {
        try (DataInputStream dataIn = new DataInputStream(socket.getInputStream())) {
            String fileName = dataIn.readUTF(); // Nom du fichier
            long fileSize = dataIn.readLong(); // Taille du fichier
            String destinationPath = "received_files"; // Répertoire par défaut pour sauvegarder

            server.saveFile(fileName, destinationPath, fileSize, dataIn);
            System.out.println("Fichier reçu : " + fileName);
            sendMessage("Fichier '" + fileName + "' transféré avec succès.");
        } catch (IOException e) {
            System.err.println("Erreur lors de l'UPLOAD : " + e.getMessage());
        }
    }

    // Gestion du DOWNLOAD (envoi du serveur vers le client)
    private void handleFileDownload() {
        try (DataOutputStream dataOut = new DataOutputStream(socket.getOutputStream())) {
            // Lire le nom du fichier demandé
            String fileName = in.readLine();
            File fileToSend = new File("received_files", fileName);

            if (fileToSend.exists() && fileToSend.isFile()) {
                dataOut.writeUTF("OK"); // Confirmation que le fichier est disponible
                dataOut.writeUTF(fileToSend.getName()); // Envoi du nom du fichier
                dataOut.writeLong(fileToSend.length()); // Envoi de la taille du fichier

                // Lecture et envoi du fichier
                try (FileInputStream fileIn = new FileInputStream(fileToSend)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = fileIn.read(buffer)) > 0) {
                        dataOut.write(buffer, 0, bytesRead);
                    }
                }

                System.out.println("Fichier envoyé : " + fileToSend.getName());
            } else {
                dataOut.writeUTF("ERROR"); // Indiquer que le fichier est introuvable
                System.err.println("Fichier non trouvé : " + fileName);
            }
        } catch (IOException e) {
            System.err.println("Erreur lors du DOWNLOAD : " + e.getMessage());
        }
    }
}
