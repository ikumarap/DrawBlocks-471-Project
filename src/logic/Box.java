package logic;

/**
 *  Logic
 *  Box inside the grid
 */
public class Box {

    private boolean[][] units;
    private int owner = 0;
    private int count = 0;

    //create box with size
    Box(int size) {
        units = new boolean[size][size];
        clearBox();
    }

    //get owner (default = 0, no owner)
    public int getOwner() {
        return owner;
    }

    //set units that are drawn on UI
    public void setUnit(int posX, int posY, int brushSize) {
        for(int i = 0; i < brushSize; i++){
            for(int j = 0; j < brushSize; j++) {
                if(!units[posX + i][posY + j]) {
                    count++;
                    units[posX + i][posY + j] = true;
                }
            }
        }
    }

    //set owner of box based on ID (1-4), default = 0
    public void setOwner(int owner) {
        this.owner = owner;
    }

    //clear the box
    public void clearBox() {
        populateBox(false);
        count = 0;
    }

    //fill box
    public void fillBox() {
        populateBox(true);
        count = units.length * units.length;
    }

    //fill the whole box (nothing - false)
    private void populateBox(boolean value) {
        for(int i = 0; i < units.length; i++) {
            for(int j = 0; j < units.length; j++) {
                units[i][j] = value;
            }
        }
    }

    //get percentage filled of the box
    public double getPercentageFilled() {
        return ((double)count / (units.length * units.length) * 100);
    }
}
