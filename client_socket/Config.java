public class Config {

    private String host = "localhost";
    private int port_message = 1234;
    private int port_transfer = 1235;

    // Constructeur par défaut
    public Config() {
    }

    // Constructeur avec paramètres
    public Config(String host, int port_message, int port_transfer) {
        this.host = host;
        this.port_message = port_message;
        this.port_transfer = port_transfer;
    }

    public String getHost() {
        return host;
    }

    public int getPortMessage() {
        return port_message;
    }

    public int getPortTransfer() {
        return port_transfer;
    }
}
