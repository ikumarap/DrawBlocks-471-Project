package logic;

import javafx.concurrent.Task;
import javafx.util.Pair;
import java.util.Vector;

/**
 *  Logic
 *  What a client should do
 */
public class ClientThread extends Task<Void> {

    //queues for communication
    private MessageQueue<Pair<byte[], String>> recvQueue, sendQueue;
    private MessageQueue<String> UIsendQueue, UIrecvQueue;

    //list of players and server
    private Vector<Pair<byte[], String>> players;
    private byte[] server;

    //game state
    private Game game;
    private boolean connectedToServer = false;
    private boolean isRunning = true;

    //variables to calculate delay time
    private long currTime = System.currentTimeMillis();
    private long rttC1, tripTime, diff, lastSeen;

    //constructor
    ClientThread(MessageQueue<Pair<byte[], String>> recvQueue,
                 MessageQueue<Pair<byte[], String>> sendQueue,
                 MessageQueue<String> UIsendQueue,
                 MessageQueue<String> UIrecvQueue,
                 byte[] server, Vector<Pair<byte[], String>> players,
                 Game game) {
        this.recvQueue = recvQueue;
        this.sendQueue = sendQueue;
        this.UIsendQueue = UIsendQueue;
        this.UIrecvQueue = UIrecvQueue;
        this.server = server;
        this.players = players;
        this.game = game;
    }

    //main loop of the client, run when the thread starts
    @Override
    protected Void call() {
        try {
            while(isRunning) {
                processRecv();
                processSend();
                if(connectedToServer)
                    isServerAlive();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    //If no replies from server for 2 seconds, connection lost
    private void isServerAlive() {
        if(System.currentTimeMillis() - lastSeen > 2000) {
            connectedToServer = false;

            //send a notification to UI
            UIrecvQueue.produce("80");

            //remove the current server from the list of players
            if(players.size() == 0) {
                return;
            }
            players.remove(0);

            //find the next server from the list of players
            server = players.elementAt(0).getKey();

            //if the server is this user
            if(server[0] == game.getThisPlayerIP()[0] &&
                server[1] == game.getThisPlayerIP()[1] &&
                server[2] == game.getThisPlayerIP()[2] &&
                server[3] == game.getThisPlayerIP()[3]) {

                game.setHost(server);
                game.spawnBackupServerThread();
            }

            //send request to connect
            sendQueue.produce(new Pair<>(server, "1#1"));
        }
    }

    //stop thread at the end
    synchronized void stopThread() {
        isRunning = false;
    }

    //process all receiving messages
    private void processRecv() {
        //pop message and update last time the server is seen online
        Pair<byte[], String> message = recvQueue.consume();
        if(message == null) {
            return;
        }

        //drop all messages not coming from server
        if(!isMessageFromServer(message)) {
            return;
        }

        lastSeen = System.currentTimeMillis();

        //extract message meanings
        String[] parts = message.getValue().split("#");
        switch (parts[0]) {
            case "3": recvStartGame(parts); break;
            case "9": recvPing(parts); break;
            case "2": recvServerConnection(parts); break;
        }
        UIrecvQueue.produce(message.getValue());
    }

    //check if the message is from server
    private boolean isMessageFromServer(Pair<byte[], String> message) {
        return (server[0] == message.getKey()[0] &&
                server[1] == message.getKey()[1] &&
                server[2] == message.getKey()[2] &&
                server[3] == message.getKey()[3]);
    }

    //server notifies to start the game
    private void recvStartGame(String[] parts) {
        //get the list of players (same as everyone)
        players.clear();
        int startingIndex = 5;
        for (; startingIndex < parts.length - 1; startingIndex += 6) {
            byte[] ip = new byte[4];
            ip[0] = (byte) Integer.parseInt(parts[startingIndex]);
            ip[1] = (byte) Integer.parseInt(parts[startingIndex + 1]);
            ip[2] = (byte) Integer.parseInt(parts[startingIndex + 2]);
            ip[3] = (byte) Integer.parseInt(parts[startingIndex + 3]);
            String name = parts[startingIndex + 4] + "#" + parts[startingIndex + 5];
            players.add(new Pair<>(ip, name));
        }
    }

    //calculate delay time between this machine and server to adjust time stamp
    private void recvPing(String[] parts) {
        tripTime = (System.currentTimeMillis() - rttC1) / 2;
        diff = System.currentTimeMillis() - (Long.parseLong(parts[1]) + tripTime);
    }

    //successfully connect to the server
    private void recvServerConnection(String[] parts) {
        //if it's the back up server, resynchronize game state
        if (parts.length > 1) {
            resyncGrid(parts[1]);
        }
        connectedToServer = true;
    }

    //resynchronize game state to ensure everyone is having the same state
    private void resyncGrid(String list) {
        Grid grid = game.getGrid();
        for(int i = 0; i < list.length(); i++) {
            int row = i / grid.size();
            int col = i % grid.size();
            grid.getBox(row, col).setOwner(Integer.parseInt(list.substring(i, i+1)));
        }
    }

    //send things (from UI and ping the server every 500ms with )
    private void processSend() {
        sendFromUI();
        if(System.currentTimeMillis() - currTime > 500){
            sendQueue.produce(new Pair<>(server, "1#9#"));
            currTime = System.currentTimeMillis();
            rttC1 = currTime;
        }
    }

    //get things to send to server from UIsendQueue
    private void sendFromUI() {
        //check if there's anything to send
        String messageToSend = UIsendQueue.consume();
        if(messageToSend == null) {
            return;
        }

        //if the message is request for box lock, add timestamp (with delay)
        String[] parts = messageToSend.split("#");
        if(parts[1].equals("7")) {
            long serverTime = System.currentTimeMillis() - diff;
            messageToSend += "#" + serverTime;
        }

        sendQueue.produce(new Pair<>(server, messageToSend));
    }
}
