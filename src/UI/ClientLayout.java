package UI;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import logic.Game;
import logic.MessageQueue;

/**
 *  Scene
 *  Join the game as client
 */
class ClientLayout extends VBox {
    private final double WIDTH = 300;
    private final double HEIGHT = 250;
    private final String CONNECT = "1";
    private Game game;

    private boolean connected = false;

    private Scene caller;
    private MessageQueue<String> UIrecvQueue, UIsendQueue;

    private Label readyTextField;
    private Scene nextScene;

    //layout of the scene
    ClientLayout(Stage stage, Game game, MessageQueue<String> UIrecvQueue,
                 MessageQueue<String> UIsendQueue) {

        super(20);
        super.setWidth(WIDTH);
        super.setHeight(HEIGHT);

        this.game = game;
        this.UIrecvQueue = UIrecvQueue;
        this.UIsendQueue = UIsendQueue;

        game.spawnSendThread();
        game.spawnReceiveThread();

        Label clientLabel = new Label("Client Menu");
        Label ipLabel = new Label("IP:");
        TextField ipTextField = new TextField ();

        Button connectButton= new Button("Connect");
        connectButton.setOnAction(e ->connectButton(stage, ipTextField));

        Button clientBackButton = new Button("Back");
        clientBackButton.setOnAction(e -> stage.setScene(caller));

        readyTextField = new Label(("Not ready"));

        this.getChildren().addAll(clientLabel, connectButton, clientBackButton, ipLabel, ipTextField, readyTextField);
    }

    //back to welcome scene
    void setScene(Scene caller) {
        this.caller = caller;
    }

    //connect button
    private void connectButton(Stage stage, TextField ipTextField) {
        // Connect to host
        game.setHost(ipTextField.getText());
        UIsendQueue.produce("1#" + CONNECT + "#" + game.getMyName());

        ///SET TEMP VALUES FOR GAME SETTINGS
        game.setBrushSize(Integer.parseInt("5"));
        game.setGridSize(Integer.parseInt("10"));
        game.setFillPercentage(Integer.parseInt("30"));

        //wait for server to start the game
        game.spawnClientThread();
        Thread listenThread = new Thread(() -> {
            listenThread();
            Platform.runLater(() -> {
                GameBoardLayout gameboardLayout = new GameBoardLayout(stage, game, UIrecvQueue, UIsendQueue);
                nextScene = new Scene(gameboardLayout, gameboardLayout.getWidth(), gameboardLayout.getHeight());
                stage.setScene(nextScene);
            });
        });
        listenThread.start();
    }

    //listen thread to get updates from server
    private void listenThread() {
        while(!getConnected()) {
            String message = UIrecvQueue.consume();
            if(message != null) {
                processMessageConnected(message);
            }
        }
    }

    //process answers from server (is-connected, start-game)
    private void processMessageConnected(String message) {
        if(message.startsWith("2")) {
            Platform.runLater(() -> setConnectedText("Connected to server. Waiting to start"));
        } else if (message.startsWith("3")) {
            getSettings(message);
            setConnected(true);
        }
    }

    //synchronization
    private void getSettings(String message) {
        String[] parts = message.split("#");

        int brushSize = Integer.parseInt(parts[1]);
        game.setBrushSize(brushSize);

        int gridSize = Integer.parseInt(parts[2]);
        game.setGridSize(gridSize);

        int fillPercentage = Integer.parseInt(parts[3]);
        game.setFillPercentage(fillPercentage);
    }

    //set text fields in layout
    private synchronized void setConnectedText(String text) {
        readyTextField.setText(text);
    }

    private synchronized void setConnected(boolean value) {
        connected = value;
    }

    private synchronized boolean getConnected() {
        return connected;
    }
}
