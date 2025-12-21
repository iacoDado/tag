import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Tabellone extends Application {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 1234;
    private static final String MULTICAST_IP = "230.0.0.1";
    private static final int MULTICAST_PORT = 4446;

    private DatagramSocket unicastSocket;
    private MulticastSocket multicastSocket;

    private int playerId = -1;
    private Circle me;
    private String myColor = "GRAY";

    private final Map<Integer, Circle> others = new HashMap<Integer, Circle>();
    private final Map<String, Rectangle> walls = new HashMap<String, Rectangle>();
    private final Set<String> keys = new HashSet<String>();

    @Override
    public void start(Stage primaryStage) throws Exception {
        Pane root = new Pane();
        Scene scene = new Scene(root, 800, 800);
        primaryStage.setTitle("Tag UDP - Client");
        primaryStage.setScene(scene);
        primaryStage.show();

        scene.setOnKeyPressed(e -> keys.add(e.getCode().toString()));
        scene.setOnKeyReleased(e -> keys.remove(e.getCode().toString()));

        //socket unicast (client)
        unicastSocket = new DatagramSocket();

        //multicast socket per ricevere lo state
        multicastSocket = new MulticastSocket(MULTICAST_PORT);
        InetAddress group = InetAddress.getByName(MULTICAST_IP);
        multicastSocket.joinGroup(group);

        //ricevitore per WELCOME (unicast)
        Thread unicastReceiver = new Thread(new Runnable() {
            @Override
            public void run() {
                unicastReceiveLoop(root);
            }
        });
        unicastReceiver.setDaemon(true);
        unicastReceiver.start();

        //ricevitore multicast per lo state
        Thread multicastReceiver = new Thread(new Runnable() {
            @Override
            public void run() {
                multicastReceiveLoop(root);
            }
        });
        multicastReceiver.setDaemon(true);
        multicastReceiver.start();

        //invio HELLO al server per ottenere WELCOME
        sendUnicast("HELLO");

        primaryStage.setOnCloseRequest(e -> {
            try {
                multicastSocket.leaveGroup(group);
            } catch (Exception ex) {
                //
            }
            multicastSocket.close();
            unicastSocket.close();
        });
    }

    private void unicastReceiveLoop(Pane root) {
        byte[] buf = new byte[1024];
        while (true) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                unicastSocket.receive(p);
                String msg = new String(p.getData(), 0, p.getLength(), "UTF-8");
                System.out.println("unicast ricevuto -> " + msg);
                if (msg.startsWith("WELCOME|")) {
                    handleWelcome(msg, root);
                }
            } catch (Exception e) {
                e.printStackTrace();
                break;
            }
        }
    }

    private void handleWelcome(String msg, Pane root) {
        String[] parts = msg.split("\\|");
        if (!(parts.length < 6)) {
            try {
                final int id = Integer.parseInt(parts[1]);
                final String color = parts[2];
                final boolean isIt = Boolean.parseBoolean(parts[3]);
                final int x = Integer.parseInt(parts[4]);
                final int y = Integer.parseInt(parts[5]);

                this.playerId = id;
                this.myColor = color;

                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        if (me == null) {
                            me = new Circle(12.5);
                            me.setFill(Color.valueOf(myColor));
                            me.setTranslateX(x);
                            me.setTranslateY(y);
                            if (isIt) {
                                me.setStroke(Color.BLACK);
                                me.setStrokeWidth(5);
                            }
                            getRootPane(root).getChildren().add(me);
                            startMovement();
                        }
                    }
                });

                System.out.println("WELCOME id=" + id + " color=" + color + " isIt=" + isIt);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void multicastReceiveLoop(Pane root) {
        byte[] buf = new byte[1024];
        while (true) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                multicastSocket.receive(p);
                String msg = new String(p.getData(), 0, p.getLength());


                final String finalMsg = msg;
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        parseState(finalMsg, root);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                break;
            }
        }
    }

    private void parseState(String msg, Pane root) {
        if (!(msg == null || !msg.startsWith("STATE|"))) {

            //segments separati da ';'
            String payload = msg.substring("STATE|".length()); //toglie STATE|
            String[] segments = payload.split(";");
            for (int i = 0; i < segments.length; i++) {
                String s = segments[i];
                if (s.startsWith("B|")) {
                    String[] parts = s.split("\\|");
                    if (parts.length >= 3) {
                        int x = Integer.parseInt(parts[1]);
                        int y = Integer.parseInt(parts[2]);
                        String key = x + ":" + y;
                        if (!walls.containsKey(key)) {
                            Rectangle r = new Rectangle(50, 50);
                            r.setFill(Color.BROWN);
                            r.setTranslateX(x);
                            r.setTranslateY(y);
                            walls.put(key, r);
                            root.getChildren().add(r);
                        }
                    }
                } else if (s.startsWith("P|")) {
                    String[] parts = s.split("\\|");
                    if (parts.length >= 6) {
                        int id = Integer.parseInt(parts[1]);
                        int x = Integer.parseInt(parts[2]);
                        int y = Integer.parseInt(parts[3]);
                        String color = parts[4];
                        boolean isIt = Boolean.parseBoolean(parts[5]);

                        if (id == this.playerId) {
                            if (me == null) {
                                me = new Circle(12.5);
                                try {
                                    me.setFill(Color.valueOf(color));
                                } catch (Exception ex) {
                                    me.setFill(Color.GRAY);
                                }
                                me.setTranslateX(x);
                                me.setTranslateY(y);
                                if (isIt) {
                                    me.setStroke(Color.BLACK);
                                    me.setStrokeWidth(5);
                                }
                                root.getChildren().add(me);
                                startMovement();
                            } else {
                                me.setTranslateX(x);
                                me.setTranslateY(y);
                                if (isIt) {
                                    me.setStroke(Color.BLACK);
                                    me.setStrokeWidth(5);
                                } else {
                                    me.setStrokeWidth(0);
                                }
                            }
                        } else {
                            Circle o = others.get(id);
                            if (o == null) {
                                Circle c = new Circle(12.5);
                                try {
                                    c.setFill(Color.valueOf(color));
                                } catch (Exception ex) {
                                    c.setFill(Color.GRAY);
                                }
                                c.setTranslateX(x);
                                c.setTranslateY(y);
                                if (isIt) {
                                    c.setStroke(Color.BLACK);
                                    c.setStrokeWidth(5);
                                }
                                others.put(id, c);
                                root.getChildren().add(c);
                            } else {
                                o.setTranslateX(x);
                                o.setTranslateY(y);
                                if (isIt) {
                                    o.setStroke(Color.BLACK);
                                    o.setStrokeWidth(5);
                                } else {
                                    o.setStrokeWidth(0);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void startMovement() {
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (me == null || playerId < 0) return;
                double dx = 0;
                double dy = 0;
                if (keys.contains("LEFT")) dx -= 3;
                if (keys.contains("RIGHT")) dx += 3;
                if (keys.contains("UP")) dy -= 3;
                if (keys.contains("DOWN")) dy += 3;
                if (dx != 0 || dy != 0) {
                    String msg = "INPUT|" + playerId + "|" + dx + "|" + dy;
                    sendUnicast(msg);
                }
            }
        }.start();
    }

    private void sendUnicast(String msg) {
        try {
            byte[] data = msg.getBytes();
            DatagramPacket p = new DatagramPacket(data, data.length, InetAddress.getByName(SERVER_HOST), SERVER_PORT);
            unicastSocket.send(p);
            //log
            //System.out.println("inviato -> " + msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Pane getRootPane(Pane root) {
        return root;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
