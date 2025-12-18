import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
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
import java.util.HashSet;
import java.util.Set;

public class Tabellone extends Application {

    private final Set<String> tastiPremuti = new HashSet<>();

    @Override
    public void start(Stage primaryStage) {
        Pane root = new Pane();
        Scene scene = new Scene(root, 500, 500);

        //gestione tasti sulla scena
        scene.setOnKeyPressed(e -> {
            tastiPremuti.add(e.getCode().toString());
            System.out.println("Tasto premuto: " + e.getCode()); // DEBUG!!! togliere dopo
        });
        scene.setOnKeyReleased(e -> tastiPremuti.remove(e.getCode().toString()));

        primaryStage.setTitle("Tag UDP");
        primaryStage.setScene(scene);
        primaryStage.show();

        //richiedi il focus DOPO lo show
        root.setFocusTraversable(true);
        root.requestFocus();

        //avvia la rete in un thread separato per non bloccare la UI
        Thread networkThread = new Thread(() -> {
            try {
                inizializzaTabellone(root);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        networkThread.setDaemon(true); //chiude il thread quando chiudi l'app
        networkThread.start();
    }

    private void inizializzaTabellone(Pane root) throws IOException {
        MulticastSocket socket = new MulticastSocket(1234);
        InetAddress group = InetAddress.getByName("230.0.0.1");
        socket.joinGroup(group);

        byte[] bufferIn = new byte[1024];
        DatagramPacket packetIN = new DatagramPacket(bufferIn, bufferIn.length);

        socket.receive(packetIN);
        String messaggioRicevuto = new String(packetIN.getData(), 0, packetIN.getLength());

        //  !!!     Importante: Gli aggiornamenti grafici (root.getChildren().add) devono tornare sul thread JavaFX tramite Platform.runLater
        Platform.runLater(() -> {
            elaboraMessaggio(messaggioRicevuto, root);
        });
    }

    private void elaboraMessaggio(String messaggio, Pane root) {
        String[] info = messaggio.split(":");
        int ctr = 0;

        while (ctr < info.length && !info[ctr].equals("over")) {
            switch (info[ctr]) {
                case "b":
                    Rectangle blocco = new Rectangle(50, 50, Color.BROWN);
                    blocco.setTranslateX(Integer.parseInt(info[ctr + 1]));
                    blocco.setTranslateY(Integer.parseInt(info[ctr + 2]));
                    root.getChildren().add(blocco);
                    ctr += 3;
                    break;

                case "g":
                    Circle g = creaCerchio(info, ctr);
                    root.getChildren().add(g);
                    ctr += 5;
                    break;

                case "gC":
                    Circle giocatoreC = creaCerchio(info, ctr);
                    avviaTimerMovimento(giocatoreC);
                    root.getChildren().add(giocatoreC);
                    ctr += 5;
                    break;
                default:
                    ctr++;
            }
        }
    }

    private Circle creaCerchio(String[] info, int ctr) {
        Circle c = new Circle(12.5);
        c.setTranslateX(Integer.parseInt(info[ctr + 1]));
        c.setTranslateY(Integer.parseInt(info[ctr + 2]));
        c.setFill(Color.valueOf(info[ctr + 3].toUpperCase()));
        if (Boolean.parseBoolean(info[ctr + 4])) {
            c.setStroke(Color.BLACK);
            c.setStrokeWidth(5);
        }
        return c;
    }

    private void avviaTimerMovimento(Circle giocatoreC) {
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                double velocita = 3.0;
                if (tastiPremuti.contains("UP")) giocatoreC.setTranslateY(giocatoreC.getTranslateY() - velocita);
                if (tastiPremuti.contains("DOWN")) giocatoreC.setTranslateY(giocatoreC.getTranslateY() + velocita);
                if (tastiPremuti.contains("LEFT")) giocatoreC.setTranslateX(giocatoreC.getTranslateX() - velocita);
                if (tastiPremuti.contains("RIGHT")) giocatoreC.setTranslateX(giocatoreC.getTranslateX() + velocita);
            }
        }.start();
    }
}