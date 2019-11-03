package logic;

import javafx.util.Pair;
import networking.NetworkingRecv;
import networking.NetworkingSend;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Vector;
/**
 *  Logic
 *  Everything about the game
 */
public class Game {
    //game data
    private int gridSize;
    private int brushSize;
    private int fillPercentage;
    private Grid grid;

    //players info
    private Pair<byte[], String> thisPlayer;
    private Vector<Pair<byte[], String>> players;
    private byte[] host = new byte[4];

    //threads
    private NetworkingRecv networkingRecv;
    private NetworkingSend networkingSend;
    private ServerThread serverTask;
    private ClientThread clientTask;

    //1 socket for both send and receive threads
    private DatagramSocket socket;

    //various queues for communications between threads
    private MessageQueue<Pair<byte[], String>> serverRecvQueue, clientRecvQueue, sendQueue;
    private MessageQueue<ReliableObj> reliableQueue;
    private MessageQueue<String> UIrecvQueue, UIsendQueue;

    //constructor and game setup
    public Game(MessageQueue<String> UIrecvQueue, MessageQueue<String> UIsendQueue) {
        createSocket();
        setThisPlayer();
        createQueues();

        players = new Vector<>();

        this.UIrecvQueue = UIrecvQueue;
        this.UIsendQueue = UIsendQueue;
    }

    //get player ID of the user
    public int getMyPID() {
        for(Pair<byte[], String> player: players) {
            if(player.getKey()[0] == thisPlayer.getKey()[0] &&
                player.getKey()[1] == thisPlayer.getKey()[1] &&
                player.getKey()[2] == thisPlayer.getKey()[2] &&
                player.getKey()[3] == thisPlayer.getKey()[3]) {
                    String fullName = player.getValue();
                    String[] parts = fullName.split("#");
                    return Integer.parseInt(parts[1]);
            }
        }
        return 0;
    }

    //get game grid
    public Grid getGrid() {
        return grid;
    }

    //get list of players
    public Vector<Pair<byte[], String>> getPlayers() {
        return players;
    }

    //create UDP socket on port 8888
    private void createSocket() {
        try {
            socket = new DatagramSocket(8888);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //create queues
    private void createQueues() {
        clientRecvQueue = new MessageQueue<>();
        serverRecvQueue = new MessageQueue<>();
        sendQueue = new MessageQueue<>();
        reliableQueue = new MessageQueue<>();
    }

    //set thisPlayer info (name, IP)
    private void setThisPlayer() {
        try {
            thisPlayer = new Pair<>(InetAddress.getLocalHost().getAddress(),
                                    InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    //get username
    public String getMyName() {
        return thisPlayer.getValue();
    }

    //get user's IP
    public String getMyIP() {
        String ret = "";
        for(int i = 0; i < host.length - 1; i++) {
            int num = (host[i] & 0xFF);
            ret += (num + ".");
        }
        return ret + (host[host.length - 1] & 0xFF);
    }

    //set host based on input string "x.x.x.x"
    public void setHost(String host) {
        String[] split = host.split("\\.");
        for(int i = 0; i < 4;i++) {
            this.host[i] = (byte) Integer.parseInt(split[i]);
        }
    }

    //set new host based on byte[]
    void setHost(byte[] IP) {
        this.host = IP;
    }

    //get user's IP in byte[]
    byte[] getThisPlayerIP() {
        return thisPlayer.getKey();
    }

    //set user as host
    public void setMeAsHost() {
        try {
            this.host = InetAddress.getLocalHost().getAddress();
            players.add(thisPlayer);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    //set grid size
    public void setGridSize(int gridSize) {
        this.gridSize = gridSize;
        createGrid(gridSize);
    }

    //create grid from gridSize
    private void createGrid(int gridSize) {
        grid = new Grid(gridSize,  600 / gridSize);
    }

    //set fill percentage
    public void setFillPercentage(int fillPercentage) {
        this.fillPercentage = fillPercentage;
    }

    //set brush size
    public void setBrushSize(int brushSize) {
        this.brushSize = brushSize;
    }

    //set grid size
    public int getGridSize() {
        return gridSize;
    }

    //get fill percentage
    public int getFillPercentage() {
        return fillPercentage;
    }

    //get brush size
    public int getBrushSize() {
        return brushSize;
    }

    //client thread
    public void spawnClientThread() {
        clientTask = new ClientThread(clientRecvQueue, sendQueue, UIsendQueue, UIrecvQueue, host, players, this);
        Thread clientThread = new Thread(clientTask);
        clientThread.start();
    }

    //server thread
    public void spawnServerThread() {
        serverTask = new ServerThread(serverRecvQueue, sendQueue, UIrecvQueue, players);
        Thread serverThread = new Thread(serverTask);
        serverThread.start();
    }

    //back up server thread (only spawned when user becomes host)
    void spawnBackupServerThread() {
        serverTask = new ServerThread(serverRecvQueue, sendQueue, UIrecvQueue, players, grid);
        Thread serverThread = new Thread(serverTask);
        serverThread.start();
    }

    //networking send thread
    public void spawnSendThread() {
        networkingSend = new NetworkingSend(sendQueue, socket, reliableQueue);
        Thread sendThread = new Thread(networkingSend);
        sendThread.start();
    }

    //networking receive thread
    public void spawnReceiveThread() {
        networkingRecv = new NetworkingRecv(sendQueue, socket, serverRecvQueue, clientRecvQueue, reliableQueue);
        Thread recvThread = new Thread(networkingRecv);
        recvThread.start();
    }

    //stop all threads when the game ends
    public void stopAllThreads() {
        networkingRecv.stopThread();
        networkingSend.stopThread();
        serverTask.stopThread();
        clientTask.stopThread();
    }
}
