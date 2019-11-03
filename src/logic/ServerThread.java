package logic;

import javafx.concurrent.Task;
import javafx.util.Pair;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Vector;

/**
 *  Logic
 *  What a server should do
 */
public class ServerThread extends Task<Void> {
    //queues for communication channels
    private MessageQueue<Pair<byte[], String>> recvQueue, sendQueue;
    private MessageQueue<String> UIrecvQueue;

    //list of players
    private Vector<Pair<byte[], String>> players;

    //game state
    private int[][] grid;
    private boolean isRunning = true;

    //lock requests to solve conflicts
    private Queue<LockRequestObj> lockRequests;

    //initial server constructor
    ServerThread(MessageQueue<Pair<byte[], String>> recvQueue,
                 MessageQueue<Pair<byte[], String>> sendQueue,
                 MessageQueue<String> UIrecvQueue,
                 Vector<Pair<byte[], String>> players) {
        this.recvQueue = recvQueue;
        this.sendQueue = sendQueue;
        this.UIrecvQueue = UIrecvQueue;
        this.players = players;
        lockRequests = new ArrayDeque<>();
    }

    //back up server constructor
    ServerThread(MessageQueue<Pair<byte[], String>> recvQueue,
                 MessageQueue<Pair<byte[], String>> sendQueue,
                 MessageQueue<String> UIrecvQueue,
                 Vector<Pair<byte[], String>> players,
                 Grid grid) {
        this.recvQueue = recvQueue;
        this.sendQueue = sendQueue;
        this.UIrecvQueue = UIrecvQueue;
        this.players = players;
        lockRequests = new ArrayDeque<>();
        createGrid(grid);
    }

    //when back up server goes online, it copies whatever the client of the same machine is having
    private void createGrid(Grid gameGrid) {
        grid = new int[gameGrid.size()][gameGrid.size()];
        for(int i = 0; i < gameGrid.size(); i++) {
            for(int j = 0; j < gameGrid.size(); j++) {
                Box box = gameGrid.getBox(i, j);
                grid[i][j] = box.getOwner();
            }
        }
    }

    //main loop of the server, run when the thread starts
    @Override
    protected Void call() {
        try {
            while(isRunning) {
                processRecv();
                resolveLockConflicts();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    //stop thread at the end
    synchronized void stopThread() {
        isRunning = false;
    }

    //resolve any conflict of multiple box-lock requests on the same box
    private void resolveLockConflicts() {
        LockRequestObj obj = lockRequests.peek();
        if(obj == null) {
            return;
        }

        //wait for 20ms then resolve the head of the queue (oldest request)
        long currentTime = System.currentTimeMillis();
        if(currentTime - obj.getServerTime() > 20) {
            resolveConflictAndRemoveRequests();
        }
    }

    //resolve by checking if there's any other request of the same box in the queue
    private void resolveConflictAndRemoveRequests() {
        LockRequestObj obj = lockRequests.poll();
        if(obj == null) {
            return;
        }

        //find min time of all requests of the same box
        for(LockRequestObj r: lockRequests) {
            if(obj.getX() == r.getX() && obj.getY() == r.getY() && obj.getTime() > r.getTime()) {
                obj = r;
            }
        }

        //remove all requests of the same box after resolving the conflict
        final int x = obj.getX();
        final int y = obj.getY();
        lockRequests.removeIf((LockRequestObj temp) -> (temp.getX() == x && temp.getY() == y));

        //broadcast the winner
        grid[obj.getX()][obj.getY()] = obj.getOwner();
        broadcast("7#" + obj.getX() + "#" + obj.getY() + "#" + obj.getOwner() + "#" + obj.getTime());
    }

    //process incoming messages
    private void processRecv() {
        //only process when there's something
        Pair<byte[], String> message = recvQueue.consume();
        if(message == null) {
            return;
        }

        //extract the message and process each type accordingly
        int messageType = Integer.parseInt(message.getValue().substring(0,1));
        switch (messageType) {
            case 1: messageRequestToConnect(message); break;
            case 3: messageStartMessage(message); break;
            case 4: broadcast(message.getValue()); break;
            case 5: captureSuccess(message); break;
            case 6: releaseLock(message); break;
            case 7: lockBox(message); break;
            case 9: sendTimeReply(message); break;
        }
    }

    //box is successfully captured
    private void captureSuccess(Pair<byte[], String> message) {
        //check number of boxes left
        int count = 0;
        for(int[] row: grid) {
            for(int val: row) {
                if(val != 0) {
                    count++;
                }
            }
        }

        //if it's done, send end game message, otherwise just broadcast the message
        if(count == grid.length * grid.length) {
            sendEndGameMessage();
        } else {
            broadcast(message.getValue());
        }
    }

    //ping back the client with current server time
    private void sendTimeReply(Pair<byte[], String> message) {
        reply("9#" + System.currentTimeMillis(), message.getKey());
    }

    //calculate winner and send end game message
    private void sendEndGameMessage() {
        //count boxes of each player
        int[] scores = new int[] {0, 0, 0, 0};
        for(int[] row: grid) {
            for(int value: row) {
                if(value > 0) {
                    (scores[value-1])++;
                }
            }
        }

        //find the max score
        int max = 0;
        for(int score: scores) {
            if(score > max) {
                max = score;
            }
        }

        //send the winner list (0 = no, 1 = yes) - there can be multiple winners
        String message = "8";
        for(int i = 0; i < 4; i++) {
            if(max == scores[i]) {
                message += "#1";
            } else {
                message += "#0";
            }
        }
        broadcast(message);
    }

    //process request to lock the box
    private void lockBox(Pair<byte[], String> message) {

        //extract the message
        String rawMessage = message.getValue();
        String[] parts = rawMessage.split("#");
        int x = Integer.parseInt(parts[1]);
        int y = Integer.parseInt(parts[2]);
        int playerID = Integer.parseInt(parts[3]);
        long timeStamp = Long.parseLong(parts[4]);

        //if the box is not available, drop message
        if(grid[x][y] != playerID && grid[x][y] != 0) {
            return;
        }

        //otherwise, put on the queue to be processed later (max after 20ms)
        LockRequestObj obj = new LockRequestObj(x, y, timeStamp, playerID);
        lockRequests.add(obj);
    }

    //process message to release the lock on a box
    private void releaseLock(Pair<byte[], String> message) {
        String rawMessage = message.getValue();
        String[] parts = rawMessage.split("#");
        int x = Integer.parseInt(parts[1]);
        int y = Integer.parseInt(parts[2]);

        //restore owner of the box and broadcast result
        grid[x][y] = 0;
        broadcast(message.getValue());
    }

    //when user presses Start button to start the game
    private void messageStartMessage(Pair<byte[], String> message) {
        //broadcast the message to start game with game settings
        broadcast(message.getValue());
        UIrecvQueue.produce(message.getValue());
        String rawMessage = message.getValue();
        String[] parts = rawMessage.split("#");

        //create tue logic grid
        int gridSize = Integer.parseInt((parts[2]));
        grid = new int[gridSize][gridSize];
        for(int i = 0; i < gridSize; i++) {
            for(int j = 0; j < gridSize; j++) {
                grid[i][j] = 0;
            }
        }
    }

    //when someone requests to connect to play the game
    private void messageRequestToConnect(Pair<byte[], String> message) {
        //add player to the list and reply yes
        addPlayer(message);
        String reply = "2#";

        //only for backup server
        //send game state for the other player to resynchronize
        if(grid != null) {
            for(int[] row: grid) {
                for(int val: row) {
                    reply += val;
                }
            }
        }

        reply(reply, message.getKey());
        UIrecvQueue.produce(message.getValue());
    }

    //helper function to reply to a certain player
    private void reply(String rawString, byte[] player) {
        String message = "0#" + rawString;
        sendQueue.produce(new Pair<>(player, message));
    }

    //helper function to broadcast the message
    private void broadcast(String rawString) {
        String message = "0#" + rawString;
        for(Pair<byte[], String> player: players) {
            sendQueue.produce(new Pair<>(player.getKey(), message));
        }
    }

    //add player to the list
    private void addPlayer(Pair<byte[], String> message) {
        //only add when the player is not already on the list
        String[] splitMessage = message.getValue().split("#");
        for(Pair<byte[], String> player: players) {
            if(player.getKey()[0] == message.getKey()[0] &&
                    player.getKey()[1] == message.getKey()[1] &&
                    player.getKey()[2] == message.getKey()[2] &&
                    player.getKey()[3] == message.getKey()[3]) {
                        return;
            }
        }
        Pair<byte[], String> newPlayer = new Pair<>(message.getKey(), splitMessage[1]);
        players.add(newPlayer);
    }
}
