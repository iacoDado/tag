import java.io.IOException;
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
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        System.out.println("Server chiude...");
        socket.close();
    }
}
