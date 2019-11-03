package UI;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Pair;
import logic.Game;
import logic.MessageQueue;

import javax.swing.*;
import javax.swing.plaf.UIResource;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Vector;

/**
 *  Scene
 *  Host the game
 */
class ServerLayout extends VBox {

    private final double WIDTH = 300;
    private final double HEIGHT = 500;
    private Scene caller;
    private Game game;

    private Thread listenThread;
    private boolean ready;

    private MessageQueue<String> UIrecvQueue, UIsendQueue;

    Label playersLabel1 = new Label();
    Label playersLabel2 = new Label();
    Label playersLabel3 = new Label();
    Label playersLabel4 = new Label();

    //layout of the scene
    ServerLayout(Stage stage, Game game, MessageQueue<String> UIrecvQueue,
                 MessageQueue<String> UIsendQueue) {
        super(20);
        super.setWidth(WIDTH);
        super.setHeight(HEIGHT);

        this.game = game;
        this.game.setMeAsHost();
        this.UIrecvQueue = UIrecvQueue;
        this.UIsendQueue = UIsendQueue;

        Label serverLabel = new Label("Server Menu");

        Label brushLabel = new Label("Brush Size:");
        TextField brushTextField = new TextField();
        brushTextField.setText("10");
        Label gridLabel = new Label("Grid Size:");
        TextField gridTextField = new TextField();
        gridTextField.setText("5");
        Label fillLabel = new Label("Fill Percentage:");
        TextField fillTextField = new TextField();
        fillTextField.setText("30");
        Label myIpLabel = new Label("My IP: " + game.getMyIP());

        Button startButton = new Button("Start Game");
        startButton.setOnAction(e -> startButton(stage, brushTextField, gridTextField, fillTextField));

        Button serverBackButton = new Button("Back");
        serverBackButton.setOnAction(e -> stage.setScene(caller));

        playersLabel1.setText("Player 1: " + game.getPlayers().elementAt(0).getValue());

        this.getChildren().addAll(serverLabel, startButton, brushLabel,
                brushTextField, gridLabel, gridTextField,fillLabel, fillTextField, myIpLabel,
                playersLabel1, playersLabel2, playersLabel3, playersLabel4,
                serverBackButton);

        spawnThreads();
    }

    //spawn threads to prepare the game
    private void spawnThreads() {
        game.spawnSendThread();
        game.spawnReceiveThread();
        game.spawnServerThread();
        game.spawnClientThread();
        spawnListenThread();
    }

    //start button
    private void startButton(Stage stage, TextField brushTextField, TextField gridTextField,TextField fillTextField) {
        String brushSize = brushTextField.getText();
        String gridSize = gridTextField.getText();
        String fillPercentage = fillTextField.getText();

        //validate inputs
        if(isValid(brushSize, gridSize, fillPercentage)) {
            game.setBrushSize(Integer.parseInt(brushSize));
            game.setGridSize(Integer.parseInt(gridSize));
            game.setFillPercentage(Integer.parseInt(fillPercentage));

            //stop listen thread
            ready = true;
            sendGameInfo(game);

            //start new scene
            GameBoardLayout gameboardLayout = new GameBoardLayout(stage, game, UIrecvQueue, UIsendQueue);
            Scene nextScene = new Scene(gameboardLayout, gameboardLayout.getWidth(), gameboardLayout.getHeight());
            stage.setScene(nextScene);

        } else {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Error. brush size and grid size must be numbers");
            alert.showAndWait();
        }
    }

    //synchronize game settings with everyone
    private void sendGameInfo(Game game) {
        int brushSize = game.getBrushSize();
        int gridSize = game.getGridSize();
        int fillPercentage = game.getFillPercentage();

        String message = "1#3#" + brushSize + "#" + gridSize + "#" + fillPercentage + "#" + System.currentTimeMillis();
        Vector<Pair<byte[], String>> players = game.getPlayers();

        int playerID = 1;
        for(Pair<byte[], String> player: players) {
            String playerIP = "#" + player.getKey()[0] + "#" + player.getKey()[1] + "#" + player.getKey()[2] +"#" + player.getKey()[3];
            String playerName = "#" + player.getValue();
            message += (playerIP + playerName + "#" + playerID);
            playerID++;
        }

        UIsendQueue.produce(message);
    }

    //listen thread to update who's connecting
    private void spawnListenThread() {
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() {
                listenThread();
                return null;
            }
        };
        listenThread = new Thread(task);
        listenThread.start();
    }

    //listen thread
    private void listenThread() {
        while(!ready) {
            String message = UIrecvQueue.consume();
            if(message == null) {
                continue;
            }

            if(message.charAt(0) == '1') {
                Platform.runLater(() -> showPlayers());
            }
        }
    }

    //update player list
    private void showPlayers() {
        Vector<Pair<byte[], String>> players = game.getPlayers();
        try {
            playersLabel1.setText("Player 1: " + players.elementAt(0).getValue());
            playersLabel2.setText("Player 2: " + players.elementAt(1).getValue());
            playersLabel3.setText("Player 3: " + players.elementAt(2).getValue());
            playersLabel4.setText("Player 4: " + players.elementAt(3).getValue());
        } catch (Exception ex) {
            //swallow
        }
    }

    //validate inputs
    private boolean isValid(String brushSize, String gridSize, String fillPercentage) {
        try {
            Integer.parseInt(brushSize);
            Integer.parseInt(gridSize);
            Integer.parseInt(fillPercentage);

        } catch (NumberFormatException ex) {
            return false;
        }
        return true;
    }

    //back button
    void setScene(Scene caller) {
        this.caller = caller;
    }
}
