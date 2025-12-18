import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
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

        DatagramPacket packetIN = new DatagramPacket(bufferIn, bufferIn.length);
        socket.receive(packetIN);
        String messaggioRicevuto = new String(packetIN.getData(), 0, packetIN.getLength());

        String[] info = messaggioRicevuto.split(":");
        int ctr=0;

        while (ctr < info.length && !info[ctr].equals("over")){
            switch(info[ctr]){
                case "b":
                    Rectangle blocco = new Rectangle(50, 50);
                    blocco.setTranslateX(Integer.parseInt(info[ctr+1]));
                    blocco.setTranslateY(info[ctr+1]);

                    ctr+=3;
                    break;
            }
        }
    }
}
