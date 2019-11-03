package logic;

import javafx.util.Pair;

/**
 *  Logic
 *  Object to hold message that needs to be sent reliably
 *  count: Max number of retries = 5
 */
public class ReliableObj {
    private Pair<byte[], String> message;
    private long id;
    private long timeStamp;
    private int count;

    //constructor
    public ReliableObj(Pair<byte[], String> message) {
        this.message = message;
        timeStamp = System.currentTimeMillis();
        id = System.currentTimeMillis();
        count = 5;
    }

    //update resend time and retry count
    public void resend() {
        timeStamp = System.currentTimeMillis();
        count--;
    }

    //getters
    public int getCount() {
        return count;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public long getID() {
        return id;
    }

    public Pair<byte[], String> getMessage() {
        String rawMessageWithID = (message.getValue() + "#" + id);
        return new Pair<>(message.getKey(), rawMessageWithID);
    }

}
