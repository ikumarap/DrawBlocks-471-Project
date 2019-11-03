package logic;
/**
 *  Logic
 *  Object to hold info about a lock request on a box
 */
class LockRequestObj {
    private int x;
    private int y;
    private long time;
    private int owner;
    private long serverTime;

    //create the request from message
    //serverTime is to check when it is received
    LockRequestObj(int x, int y, long time, int owner) {
        this.x = x;
        this.y = y;
        this.time = time;
        this.owner = owner;
        this.serverTime = System.currentTimeMillis();
    }

    //getters
    int getX() {
        return x;
    }

    int getY() {
        return y;
    }

    long getTime() {
        return time;
    }

    int getOwner() {
        return owner;
    }

    long getServerTime() {
        return serverTime;
    }
}
