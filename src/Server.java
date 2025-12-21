/*import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Date;

public class Server {
    public static void main(String[] args) throws IOException {
        byte[] bufferOUT = new byte[1024];
        byte[] bufferIN = new byte[1024];
        int porta = 1234;
        InetAddress gruppo = InetAddress.getByName("230.0.0.1");
        MulticastSocket socket = new MulticastSocket();
        String toSend = "";

        boolean exit = false;
        while(!exit) {
            toSend = "b:50:200:g:150:200:BLUE:true:gC:150:300:RED:false:over";
            bufferOUT = toSend.getBytes();
            DatagramPacket dp;
            dp = new DatagramPacket(bufferOUT, bufferOUT.length, gruppo, porta);
            socket.send(dp);

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        System.out.println("Server chiude...");
        socket.close();
    }
}
*/


import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Server {

    private static final int SERVER_PORT = 1234;
    private static final String MULTICAST_IP = "230.0.0.1";
    private static final int MULTICAST_PORT = 4446;

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
    private final String[] colors = new String[] { "RED", "BLUE", "GREEN", "YELLOW", "MAGENTA", "CYAN" };

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
        Thread recv = new Thread(new Runnable() { public void run() { receiveLoop(); } });
        recv.start(); // non daemon

        Thread state = new Thread(new Runnable() { public void run() { stateLoop(); } });
        state.start(); // non daemon

// blocca il main thread così la JVM resta viva
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    private void receiveLoop() {
        byte[] buf = new byte[2048];
        while (true) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                socket.receive(p);
                String msg = new String(p.getData(), 0, p.getLength(), "UTF-8");
                SocketAddress addr = p.getSocketAddress();
                System.out.println("[SERVER] ricevuto da " + addr + " -> " + msg);
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
            System.out.println("[SERVER] tipo messaggio sconosciuto: " + type);
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

        String welcome = "WELCOME|" + p.id + "|" + p.color + "|" + p.isIt + "|" + (int)p.x + "|" + (int)p.y;
        sendUnicast(welcome, addr);

        System.out.println("[SERVER] creato player id=" + p.id + " color=" + p.color + " isIt=" + p.isIt + " addr=" + addr);
    }

    private void handleInput(String[] parts, SocketAddress addr) {
        try {
            if (parts.length < 4) return;
            int id = Integer.parseInt(parts[1]);
            double dx = Double.parseDouble(parts[2]);
            double dy = Double.parseDouble(parts[3]);

            Player p = players.get(id);
            if (p == null) return;

            // opzionale: verifica che l'input arrivi dall'indirizzo registrato
            if (p.address != null) {
                if (!p.address.equals(addr)) {
                    // accetta comunque ma logga
                    System.out.println("[SERVER] INPUT id mismatch addr: " + addr + " vs " + p.address);
                }
            }

            // semplice update (no collision for brevity)
            p.x += dx;
            p.y += dy;

            // clamp su bordi (0..800)
            if (p.x < 0) p.x = 0;
            if (p.y < 0) p.y = 0;
            if (p.x > 800) p.x = 800;
            if (p.y > 800) p.y = 800;

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stateLoop() {
        while (true) {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append("STATE|");
                // muri
                for (int[] w : walls) {
                    sb.append("B|").append(w[0]).append("|").append(w[1]).append(";");
                }
                // players
                for (Player p : players.values()) {
                    sb.append("P|").append(p.id).append("|")
                            .append((int)p.x).append("|").append((int)p.y).append("|")
                            .append(p.color).append("|").append(p.isIt).append(";");
                }
                sb.append("END");

                byte[] data = sb.toString().getBytes("UTF-8");
                DatagramPacket pkt = new DatagramPacket(data, data.length, multicastGroup, MULTICAST_PORT);
                socket.send(pkt);

                Thread.sleep(100);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void sendUnicast(String msg, SocketAddress addr) throws Exception {
        byte[] data = msg.getBytes("UTF-8");
        if (addr instanceof InetSocketAddress) {
            InetSocketAddress isa = (InetSocketAddress) addr;
            DatagramPacket p = new DatagramPacket(data, data.length, isa.getAddress(), isa.getPort());
            socket.send(p);
        } else {
            // fallback (non probabile)
            DatagramPacket p = new DatagramPacket(data, data.length);
            socket.send(p);
        }
    }

    private void generateWalls() {
        // esempio semplice: fila di muri orizzontali
        for (int i = 0; i < 15; i++) {
            walls.add(new int[] { 50 + i * 40, 300 });
        }
        // altri muri sparsi
        walls.add(new int[] { 200, 100 });
        walls.add(new int[] { 400, 500 });
    }
}