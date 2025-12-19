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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Tabellone extends Application {

    private final Set<String> tastiPremuti = new HashSet<>();
    private Circle giocatoreC;
    boolean creatoGiocatoreC = false;
    //mappa che associa il colore del giocatore al suo oggetto Circle
    private final Map<String, Circle> altriGiocatori = new HashMap<>();

    @Override
    public void start(Stage primaryStage) {
        Pane root = new Pane();
        Scene scene = new Scene(root, 500, 500);

        //gestione tasti sulla scena
        scene.setOnKeyPressed(e -> {
            tastiPremuti.add(e.getCode().toString());
            //System.out.println("Tasto premuto: " + e.getCode()); // DEBUG!!! togliere dopo
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
        int port = 1234;
        MulticastSocket socket = new MulticastSocket(port);
        InetAddress group = InetAddress.getByName("230.0.0.1");
        socket.joinGroup(group);

        String messaggioRicevuto = "";

        do {
            byte[] bufferIn = new byte[1024];
            DatagramPacket packetIN = new DatagramPacket(bufferIn, bufferIn.length);

            socket.receive(packetIN);
            messaggioRicevuto = new String(packetIN.getData(), 0, packetIN.getLength());

            //  !!!     Importante: gli aggiornamenti grafici (root.getChildren().add) devono tornare sul thread JavaFX tramite Platform.runLater
            String finalMessaggioRicevuto = messaggioRicevuto; //se messo nella lambda function deve essere finale
            Platform.runLater(() -> {
                elaboraMessaggio(finalMessaggioRicevuto, root);
            });

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            String messaggio = creaMessaggio();
            byte[] bufferOut = messaggio.getBytes();
            DatagramPacket packetOUT = new DatagramPacket(bufferOut, bufferOut.length, group, port);

            try {
                socket.send(packetOUT);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        } while (!messaggioRicevuto.equals("stop"));
    }

    private String creaMessaggio() {

        String messaggio = "check:" +
                giocatoreC.getTranslateX() + ":" +
                giocatoreC.getTranslateY() + ":" +
                giocatoreC.getFill() + ":" +
                (giocatoreC.getStrokeWidth() > 0) + ":over";

        return messaggio;
    }

    private void elaboraMessaggio(String messaggio, Pane root) {

        //root.getChildren().removeIf(node -> node instanceof Circle && node != giocatoreC);


        String[] info = messaggio.split(":");
        int ctr = 0;

        while (ctr < info.length && !info[ctr].equals("over") && !info[ctr].equals("check")) {  //diverso da check perche si legge anche i messaggi inviati verso il server
            switch (info[ctr]) {
                case "b":
                    Rectangle blocco = new Rectangle(50, 50, Color.BROWN);
                    blocco.setTranslateX(Integer.parseInt(info[ctr + 1]));
                    blocco.setTranslateY(Integer.parseInt(info[ctr + 2]));
                    root.getChildren().add(blocco);
                    ctr += 3;
                    break;

                case "g":
                    String coloreID = info[ctr + 3]; //usa il colore come ID unico
                    double nuovaX = Double.parseDouble(info[ctr + 1]);
                    double nuovaY = Double.parseDouble(info[ctr + 2]);

                    Circle altroG = altriGiocatori.get(coloreID);

                    if (altroG == null) {
                        altroG = creaCerchio(info, ctr);
                        altriGiocatori.put(coloreID, altroG);
                        root.getChildren().add(altroG);
                    } else {
                        altroG.setTranslateX(nuovaX);
                        altroG.setTranslateY(nuovaY);

                        if (Boolean.parseBoolean(info[ctr + 4])) {
                            altroG.setStroke(Color.BLACK);
                            altroG.setStrokeWidth(5);
                        } else {
                            altroG.setStrokeWidth(0);
                        }
                    }
                    ctr += 5;

                case "gC":
                    if (!creatoGiocatoreC) {
                        giocatoreC = creaCerchio(info, ctr);
                        creatoGiocatoreC = true;

                        avviaTimerMovimento(giocatoreC);
                        root.getChildren().add(giocatoreC);
                    } else {
                        giocatoreC.setTranslateX(Integer.parseInt(info[ctr + 1]));
                        giocatoreC.setTranslateY(Integer.parseInt(info[ctr + 2]));
                    }
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