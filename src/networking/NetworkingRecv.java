package networking;

import javafx.concurrent.Task;
import javafx.util.Pair;
import logic.MessageQueue;
import logic.ReliableObj;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 *  Networking
 *  A thread dedicating to receive messages
 */
public class NetworkingRecv extends Task<Void> {
    //networking and communication channels
    private MessageQueue<Pair<byte[], String>> sendQueue;
    private MessageQueue<Pair<byte[], String>> serverRecvQueue, clientRecvQueue;
    private MessageQueue<ReliableObj> reliableQueue;
    private DatagramSocket socket;

    //thread state
    private boolean isRunning = true;

    //constructor
    public NetworkingRecv(MessageQueue<Pair<byte[], String>> sendQueue, DatagramSocket socket,
                          MessageQueue<Pair<byte[], String>> serverRecvQueue,
                          MessageQueue<Pair<byte[], String>> clientRecvQueue,
                          MessageQueue<ReliableObj> reliableQueue) {

        this.sendQueue = sendQueue;
        this.socket = socket;
        this.serverRecvQueue = serverRecvQueue;
        this.clientRecvQueue = clientRecvQueue;
        this.reliableQueue = reliableQueue;
    }

    //main loop of the thread, run when the thread starts
    @Override
    protected Void call() {
        recvLoop();
        return null;
    }

    //main loop of the thread, run when the thread starts
    private void recvLoop() {
        while(isRunning) {
            try {
                //UDP recv
                byte[] buf = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                //extract message info
                byte[] address = packet.getAddress().getAddress();
                String message = new String(packet.getData(), 0, packet.getLength());
                Pair<byte[], String> pair = new Pair<>(address, message);

                //process message
                processMessage(pair);
            } catch (Exception ex) {
                System.out.println("Oh oh...");
            }
        }
    }

    //stop thread at the end
    public synchronized void stopThread() {
        isRunning = false;
    }

    //process messages
    private void processMessage(Pair<byte[], String> message) {
        String[] parts = message.getValue().split("#");

        //if ack, remove the message that is acked from the list
        if(parts[1].equals("99")) {
            String id = parts[parts.length - 1];
            reliableQueue.removeIf((ReliableObj temp) -> temp.getID() == Long.parseLong((id)));
            return;
        }

        //if the message received is reliable, send ack for it
        if(!(parts[1].equals("4") || parts[1].equals("9"))) {
            String ack;
            //if from server, send to client and vice versa
            if(parts[1].equals("0")) {
                ack = "1#";
            } else {
                ack = "0#";
            }
            ack += "99#" + parts[parts.length - 1];

            //send ack
            sendQueue.produce(new Pair<>(message.getKey(), ack));

            //trim the timestamp at the end and send to the correct recipient
            int endOfMessage = message.getValue().lastIndexOf("#");
            String newRawMessage = message.getValue().substring(0, endOfMessage);
            sendToServerOrClient(new Pair<>(message.getKey(), newRawMessage));

        //if no need to send ack, just send it to client/server thread
        } else {
            sendToServerOrClient(message);
        }
    }

    //send to client/server thread of the same machine based on prefix (1 = for server, 0 = for client)
    private void sendToServerOrClient(Pair<byte[], String> message) {
        char hostOrClient = message.getValue().charAt(0);

        //trim the prefix
        String payload = message.getValue().substring(2);
        Pair<byte[], String> newMessage = new Pair<>(message.getKey(), payload);

        //send to the right thread
        if(hostOrClient == '1') {
            serverRecvQueue.produce(newMessage);
        } else {
            clientRecvQueue.produce(newMessage);
        }
    }
}
