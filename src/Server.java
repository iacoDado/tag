import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class Server {

    private static final int SERVER_PORT = 1234;
    private static final String MULTICAST_IP = "230.0.0.1";
    private static final int MULTICAST_PORT = 4446;

    private static final int N_MURI = 30;
    private static final double PLAYER_DIAMETER = 25;
    private static final double WALL_DIM = 50;


    private DatagramSocket socket;
    private InetAddress multicastGroup;

    private int nextId = 1;

    private static class Player {
        int id;
        double x;
        double y;
        String color;
        boolean isIt;
        SocketAddress address;
    }

    private final Map<Integer, Player> players = new ConcurrentHashMap<Integer, Player>();
    private final List<int[]> walls = new ArrayList<int[]>();
    private final String[] colors = new String[]{"RED", "BLUE", "GREEN", "YELLOW", "MAGENTA", "CYAN"};

    public Server() throws Exception {
        socket = new DatagramSocket(SERVER_PORT);
        multicastGroup = InetAddress.getByName(MULTICAST_IP);
        generateWalls();
        System.out.println("SERVER avviato su porta " + SERVER_PORT + " - multicast " + MULTICAST_IP + ":" + MULTICAST_PORT);
    }

    public static void main(String[] args) throws Exception {
        Server s = new Server();
        s.start();
    }

    public void start() {
        Thread recv = new Thread(new Runnable() {
            public void run() {
                receiveLoop();
            }
        });
        recv.start(); //non daemon

        Thread state = new Thread(new Runnable() {
            public void run() {
                stateLoop();
            }
        });
        state.start(); //non daemon

        //tiene occupato il main thread così la JVM resta viva
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    private void receiveLoop() {
        byte[] buf = new byte[1024];
        while (true) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                socket.receive(p);
                String msg = new String(p.getData(), 0, p.getLength());
                SocketAddress addr = p.getSocketAddress();
                System.out.println("ricevuto da " + addr + " -> " + msg);
                handleMessage(msg, addr);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void handleMessage(String msg, SocketAddress addr) throws Exception {
        if (msg == null || msg.length() == 0) return;
        String[] parts = msg.split("\\|");
        String type = parts[0];
        if ("HELLO".equals(type)) {
            handleHello(addr);
        } else if ("INPUT".equals(type)) {
            handleInput(parts, addr);
        } else {
            System.out.println("tipo messaggio sconosciuto: " + type);
        }
    }

    private void handleHello(SocketAddress addr) throws Exception {
        Player p = new Player();
        p.id = nextId++;
        p.x = 25;
        p.y = 25;
        p.color = colors[p.id % colors.length];
        p.isIt = players.isEmpty();
        p.address = addr;
        players.put(p.id, p);

        String welcome = "WELCOME|" + p.id + "|" + p.color + "|" + p.isIt + "|" + (int) p.x + "|" + (int) p.y;
        sendUnicast(welcome, addr);

        System.out.println("creato player id=" + p.id + " color=" + p.color + " isIt=" + p.isIt + " addr=" + addr);
    }

    private void handleInput(String[] parts, SocketAddress addr) {
        try {
            if (parts.length < 4) return;
            int id = Integer.parseInt(parts[1]);
            double dx = Double.parseDouble(parts[2]);
            double dy = Double.parseDouble(parts[3]);

            Player p = players.get(id);
            if (p == null) return;

            if (p.address != null) {
                if (!p.address.equals(addr)) {
                    //accetta comunque ma logga
                    System.out.println("INPUT indirizzi diversi: " + addr + " vs " + p.address);
                }
            }

            //semplice update       !!! AGGIUNGERE CONTROLLO COLLISIONI
            if (checkCollisions(p.x + dx, p.y + dy)) {
                p.x += dx;
                p.y += dy;
            }

            if (checkTag(id)) {
                for (Player pl : players.values()) {
                    pl.isIt = false;
                }
                p.isIt = true;
            }


            //check su bordi (0,800)
            if (p.x < PLAYER_DIAMETER / 2) p.x = PLAYER_DIAMETER / 2;
            if (p.y < PLAYER_DIAMETER / 2) p.y = PLAYER_DIAMETER / 2;
            if (p.x > 800 - PLAYER_DIAMETER / 2) p.x = 800 - PLAYER_DIAMETER / 2;
            if (p.y > 800 - PLAYER_DIAMETER / 2) p.y = 800 - PLAYER_DIAMETER / 2;

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stateLoop() {
        while (true) {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append("STATE|");

                //muri
                for (int[] w : walls) {
                    sb.append("B|").append(w[0]).append("|").append(w[1]).append(";");
                }

                //players
                for (Player p : players.values()) {
                    sb.append("P|").append(p.id).append("|")
                            .append((int) p.x).append("|").append((int) p.y).append("|")
                            .append(p.color).append("|").append(p.isIt).append(";");
                }
                sb.append("END");

                byte[] data = sb.toString().getBytes();
                DatagramPacket packet = new DatagramPacket(data, data.length, multicastGroup, MULTICAST_PORT);
                socket.send(packet);

                Thread.sleep(100);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void sendUnicast(String msg, SocketAddress addr) throws Exception {
        byte[] data = msg.getBytes();

        InetSocketAddress isa = (InetSocketAddress) addr;
        DatagramPacket p = new DatagramPacket(data, data.length, isa.getAddress(), isa.getPort());
        socket.send(p);

    }

    private void generateWalls() { //        !!! RIFARE SERIAMENTE
        Random r = new Random();

        for (int i = 0; i < N_MURI; i++) {
            walls.add(new int[]{(int) (r.nextInt(700) + WALL_DIM), (int) (r.nextInt(700) + WALL_DIM)});
        }
    }

    private boolean checkCollisions(double x, double y) { //     !!! fare meglio (togliere 100 return)
        //true se ok false se c'è collisione

        boolean check = true;

        for (int[] w : walls) {
            if (isInsideSquare(x, y, w[0], w[1])) {
                return false;
            }
        }

        return true;
    }

    private boolean checkTag(int id) { //     !!! fare meglio (togliere 100 return)
        //true se preso false se niente

        double x = players.get(id).x;
        double y = players.get(id).y;

        for (Player p : players.values()) {
            if (isInsideCircle(x, y, p.x, p.y) && p.isIt) {
                return true;
            }
        }

        return false;
    }

    private boolean isInsideSquare(double x1, double y1, double x2, double y2) {

        //x2 muro   x1 player
        return (x1 + PLAYER_DIAMETER / 2 >= x2 && x1 - PLAYER_DIAMETER / 2 <= x2 + WALL_DIM) &&
                (y1 + PLAYER_DIAMETER / 2 >= y2 && y1 - PLAYER_DIAMETER / 2 <= y2 + WALL_DIM);
    }

    private boolean isInsideCircle(double x1, double y1, double centerX, double centerY) {
        //distanza punto punto e vedo distanza con raggio

        double dx = x1 - centerX;
        double dy = y1 - centerY;

        double squaredDistance = (dx * dx) + (dy * dy);

        double squaredRadius = PLAYER_DIAMETER * PLAYER_DIAMETER;

        return squaredDistance <= squaredRadius;
    }
}