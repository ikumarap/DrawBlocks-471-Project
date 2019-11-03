package UI;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
/**
 *  Graphics Context: what canvas uses to draw on
 *  This is a wrapper class to allow multithreading
 */
class GraphicsContextWrapper {
    private GraphicsContext gc;

    GraphicsContextWrapper(GraphicsContext gc) {
        this.gc = gc;
    }

    //normal drawing with brush size
    synchronized void fillRect(double x, double y, double size, int player) {
        setFill(player);
        gc.fillRect(x, y, size, size);
    }

    //draw a particular box from X, Y coordinates, size and player ID
    synchronized void drawBox(int indexX, int indexY, double boxSize, int player) {
        double posX = indexX * boxSize;
        double posY = indexY * boxSize;

        fillRect(posX, posY, boxSize, player);
        gc.strokeLine(posX, posY, posX + boxSize, posY);
        gc.strokeLine(posX, posY, posX, posY + boxSize);
        gc.strokeLine(posX + boxSize, posY, posX + boxSize, posY + boxSize);
        gc.strokeLine(posX, posY + boxSize, posX + boxSize, posY + boxSize);
    }

    //set color based on player ID
    private void setFill(int player) {
        if(player == 1) {
            gc.setFill(Color.RED);
        } else if (player == 2) {
            gc.setFill(Color.BLUE);
        } else if (player == 3) {
            gc.setFill(Color.GREEN);
        } else if (player == 4) {
            gc.setFill(Color.YELLOW);
        } else{
            gc.setFill((Color.WHITE));
        }
    }
}
