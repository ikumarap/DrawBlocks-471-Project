package networking;

import javafx.concurrent.Task;
import javafx.util.Pair;
import logic.MessageQueue;
import logic.ReliableObj;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 *  Networking
 *  A thread dedicating to send messages
 */
public class NetworkingSend extends Task<Void> {
    private DatagramSocket socket;
    private MessageQueue<Pair<byte[], String>> sendQueue;
    private MessageQueue<ReliableObj> reliableQueue;
    private boolean isRunning = true;

    private enum SEND_TYPE {
        RELIABLE,
        WHATEVER
    }

    //constructor
    public NetworkingSend(MessageQueue<Pair<byte[], String>> sendQueue, DatagramSocket socket,
                          MessageQueue<ReliableObj> reliableQueue) {
        this.sendQueue = sendQueue;
        this.reliableQueue = reliableQueue;
        this.socket = socket;
    }

    //main loop of the thread, run when the thread starts
    @Override
    protected Void call() {
        while(isRunning) {
            sendNewMessages();
            resendReliableMessages();
        }
        return null;
    }

    //stop thread at the end
    public synchronized void stopThread() {
        isRunning = false;
    }

    //send new message from server/UI
    private void sendNewMessages() {
        //check if there's any new message to send
        Pair<byte[], String> messageToSend = sendQueue.consume();
        if (messageToSend == null) {
            return;
        }

        //decide if it needs to be reliable or not
        String[] split = messageToSend.getValue().split("#");
        String type = split[1];
        if (type.equals("4") || type.equals("9") || type.equals("99")) {
            sendTo(messageToSend, SEND_TYPE.WHATEVER);
        } else {
            sendTo(messageToSend, SEND_TYPE.RELIABLE);
        }
    }

    //do some background work depending on the message type
    private void sendTo(Pair<byte[], String> message, SEND_TYPE type) {
        //if reliable, add it to the reliable queue and send it with unique ID
        if(type == SEND_TYPE.RELIABLE) {
            ReliableObj obj = new ReliableObj(message);
            Pair<byte[], String> newMessage = obj.getMessage();
            reliableQueue.produce(obj);
            sendMessage(newMessage);

        //if not, just send
        } else {
            sendMessage(message);
        }
    }

    //resend reliable messages that haven't been acked
    private void resendReliableMessages() {
        //send if there's anything left
        ReliableObj obj = reliableQueue.peek();
        if(obj == null) {
            return;
        }

        //resend every 20ms
        long currentTime = System.currentTimeMillis();
        if(currentTime - obj.getTimeStamp() >= 20) {
            //resend if there's a message
            obj = reliableQueue.consume();
            if(obj == null) {
                return;
            }

            //if the message has been sent 5 times and not acked, drop it
            if(obj.getCount() == 0) {
                return;
            }

            //resend and update new send time
            obj.resend();
            sendMessage(obj.getMessage());
            reliableQueue.produce(obj);
        }
    }

    //send message to a particular IP
    private void sendMessage(Pair<byte[], String> message) {
        //send message to IP
        byte[] buf = message.getValue().getBytes();
        try {
            InetAddress address = InetAddress.getByAddress(message.getKey());
            DatagramPacket packet = new DatagramPacket(buf, buf.length, address, 8888);
            socket.send(packet);
        } catch (Exception ex) {
            //swallow
            System.out.println("Cannot resolve host name");
        }
    }
}
