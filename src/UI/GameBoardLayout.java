package UI;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import logic.Box;
import logic.Game;
import logic.Grid;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import logic.MessageQueue;

/**
 *  Scene
 *  Gameplay scene, where you play the game
 */
class GameBoardLayout extends Pane {
    private enum GameState {
        PLAYING,
        END
    }

    private Game game;
    private GraphicsContextWrapper gc;

    private int x = -1;
    private int y = -1;
    private int gridSize;
    private int brushSize;
    private int fllPercentage;
    private int boxSize;
    private int canvasSize;
    private Canvas canvas;
    private String winners = "";
    private GameState state = GameState.PLAYING;

    private MessageQueue<String> UIrecvQueue, UIsendQueue;

    private final int UPDATE_MESSAGE = 4;
    private final int CAPTURE_SUCCESS = 5;
    private final int CAPTURE_FAILURE = 6;
    private final int LOCK_BOX = 7;
    private final int GAME_OVER = 8;
    private final int SERVER_DOWN = 80;

    //set up canvas to draw
    private void setUpCanvas() {
        gridSize = game.getGridSize();              //number of boxes per axis
        brushSize = game.getBrushSize();            //brush size in pixels
        fllPercentage = game.getFillPercentage();   //percentage of box to fill to capture box.
        boxSize = 600 / gridSize;                   //pixel width of each box
        canvasSize = gridSize * boxSize;            //canvas size

        canvas = new Canvas(canvasSize, canvasSize);
        gc = new GraphicsContextWrapper(canvas.getGraphicsContext2D());
    }

    //helper function to pin point box coordinates
    private void setCoordinateIndex(double mousePosX, double mousePosY) {
        x = ((int) mousePosX) / boxSize;
        if (x >= gridSize) {
            x = -1;
        }

        y = ((int) mousePosY) / boxSize;
    }

    //create grid to draw
    private void createUIGrid() {
        Grid grid = game.getGrid();
        for(int row = 0; row < gridSize; row++) {
            for(int col = 0; col < gridSize; col++) {
                int owner = grid.getBox(row, col).getOwner();
                gc.drawBox(row, col, boxSize, owner);
            }
        }
    }

    //action when mouse is pressed
    private void mousePressed(double mousePosX, double mousePosY) {
        //if out of drawing space, do nothing
        if(mousePosX >= canvasSize || mousePosY >= canvasSize) {
            return;
        }
        int player = game.getMyPID();
        setCoordinateIndex(mousePosX, mousePosY);
        Box box = game.getGrid().getBox(x, y);

        //send request to lock the box if no one owns the box yet
        if(box.getOwner() == 0) {
            gc.fillRect(mousePosX, mousePosY, brushSize, player);
            UIsendQueue.produce("1#" + LOCK_BOX + "#" + x + "#" + y + "#" + player);
        }
    }

    //action when mouse is dragged
    private void mouseDragged(double mousePosX, double mousePosY) {
        int player = game.getMyPID();
        Box box = game.getGrid().getBox(x, y);

        //if box already taken, disallow
        if(box.getOwner() != player && box.getOwner() != 0) {
            return;
        }

        //various canvas space checks
        if(x < 0) {
            return;
        }
        if((mousePosX < x * boxSize) || (mousePosX >= (x + 1) * boxSize - brushSize))  {
            return;
        }
        if((mousePosY < y * boxSize) || (mousePosY >= (y + 1) * boxSize - brushSize))  {
            return;
        }

        //draw on canvas
        gc.fillRect(mousePosX, mousePosY, brushSize, player);
        box.setUnit((int)(mousePosX % boxSize), (int)(mousePosY % boxSize), brushSize);

        //send what we draw to server
        UIsendQueue.produce("1#" + UPDATE_MESSAGE + "#" + mousePosX + "#" + mousePosY + "#" + player);

    }

    //action when mouse is released
    private void mouseReleased() {
         //canvas space check
        if(x < 0) {
            return;
        }

        //if box already taken, do nothing (to avoid certain user to release all the boxes)
        int player = game.getMyPID();
        Box box = game.getGrid().getBox(x, y);
        if(box.getOwner() != player && box.getOwner() != 0) {
            return;
        }

        //if box is captured successfully
        if(box.getPercentageFilled() >= fllPercentage) {
            box.fillBox();
            gc.drawBox(x, y, boxSize, player);
            UIsendQueue.produce("1#" + CAPTURE_SUCCESS + "#"+ x + "#" + y + "#" + player);

        //if not
        } else {
            box.clearBox();
            gc.drawBox(x, y, boxSize, 0);
            UIsendQueue.produce("1#" + CAPTURE_FAILURE + "#"+ x + "#" + y + "#" + player);
        }

        //reset x and y boxes
        x = -1;
        y = -1;
    }

    //layout of the scene
    GameBoardLayout(Stage stage, Game game, MessageQueue<String> UIrecvQueue,
                    MessageQueue<String> UIsendQueue)
    {
        this.game = game;
        this.UIrecvQueue = UIrecvQueue;
        this.UIsendQueue = UIsendQueue;

        setUpCanvas();
        super.setWidth(canvasSize + 400);
        super.setHeight(canvasSize);

        createUIGrid();

        canvas.setOnMousePressed(e -> mousePressed(e.getSceneX(), e.getSceneY()));
        canvas.setOnMouseDragged(e -> mouseDragged(e.getSceneX(), e.getSceneY()));
        canvas.setOnMouseReleased(e -> mouseReleased());

        // Set the Style-properties of the Pane
        this.setStyle("-fx-padding: 0;" +
                "-fx-border-style: solid inside;" +
                "-fx-border-width: 0;" +
                "-fx-border-insets: 0;" +
                "-fx-border-radius: 0;" +
                "-fx-border-color: blue;");

        // Add the Canvas to the Pane
        this.getChildren().add(canvas);
        startListening();

    }

    //spawn listen thread to update drawings
    private void startListening() {
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() {
                listeningThread();
                Platform.runLater(() -> showWinText());
                return null;
            }
        };
        Thread thread = new Thread(task);
        thread.start();
    }

    //listen thread
    private void listeningThread() {
        //stop when gameState is END
        while(state != GameState.END) {
            String message = UIrecvQueue.consume();
            if(message == null) {
                continue;
            }

            //extract messages and perform actions accordingly
            String[] messageParts = message.split("#");
            int type = Integer.parseInt(messageParts[0]);
            switch (type) {
                case UPDATE_MESSAGE: Platform.runLater(() ->updateBoxDraw(messageParts)); break;
                case CAPTURE_SUCCESS: Platform.runLater(() ->boxCaptured(messageParts)); break;
                case CAPTURE_FAILURE: Platform.runLater(() ->boxCaptureFailed(messageParts)); break;
                case LOCK_BOX: Platform.runLater(() ->lockBox(messageParts)); break;
                case GAME_OVER: Platform.runLater(() ->gameOver(messageParts)); break;
                case SERVER_DOWN: Platform.runLater(() ->showDisconnect()); break;
            }
        }
    }

    //game over, show win notification and stop all threads
    private void gameOver(String[] messageParts) {
        winners = "Winner:";
        //count and show winner(s)
        for(int i = 1; i < 5; i++) {
            if(messageParts[i].equals("1")) {
                winners += " " + translateIdToString(i-1);
            }
        }
        game.stopAllThreads();
        state = GameState.END;
    }

    //show win alert text
    private void showWinText() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Game Over");
        alert.setHeaderText(winners);
        alert.show();
    }

    //show disconnect when losing connection to server
    private void showDisconnect() {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Server Problem");
        alert.setHeaderText("Server is down, please wait");
        alert.show();
    }

    //helper function to convert number to text to show who wins
    private String translateIdToString(int id) {
        switch (id) {
            case 0: return "Red";
            case 1: return "Blue";
            case 2: return "Green";
            case 3: return  "Yellow";
        }
        return "";
    }

    //lock box, disallow this user to draw on
    private void lockBox(String[] messageParts) {
        int x = (int)(Double.parseDouble(messageParts[1]));
        int y = (int)(Double.parseDouble(messageParts[2]));
        int player = (int)(Double.parseDouble(messageParts[3]));

        game.getGrid().getBox(x, y).setOwner(player);
    }

    //update drawings others make on grid
    private void updateBoxDraw(String[] messageParts) {
        int x = (int)(Double.parseDouble(messageParts[1]));
        int y = (int)(Double.parseDouble(messageParts[2]));
        int player = (int)(Double.parseDouble(messageParts[3]));
        gc.fillRect(x, y, brushSize, player);
    }

    //fill the whole box that has been captured
    private void boxCaptured(String[] messageParts) {
        int x = (int)(Double.parseDouble(messageParts[1]));
        int y = (int)(Double.parseDouble(messageParts[2]));
        int player = (int)(Double.parseDouble(messageParts[3]));
        gc.drawBox(x, y, boxSize, player);
    }

    //clear the whole box that has been freed
    private void boxCaptureFailed(String[] messageParts) {
        int x = (int)(Double.parseDouble(messageParts[1]));
        int y = (int)(Double.parseDouble(messageParts[2]));
        game.getGrid().getBox(x, y).setOwner(0);
        gc.drawBox(x, y, boxSize, 0);
    }
}

