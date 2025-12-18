import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;



import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

import java.net.InetAddress;
import java.net.MulticastSocket;

public class Tabellone extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        Pane root = new Pane();


        Scene scene = new Scene(root, 500, 500);
    }

    private void inizializzaTabellone() throws IOException {
        byte [] bufferIn = new byte[1024];
        byte [] bufferOut = new byte[1024];
        int porta = 1234;
        String gruppo = "230.0.0.1";
        MulticastSocket socket = new MulticastSocket(porta);
        socket.joinGroup(InetAddress.getByName(gruppo));
    }
}
