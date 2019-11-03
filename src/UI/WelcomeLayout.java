package UI;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import logic.Game;
import logic.MessageQueue;

/**
 *  Scene
 *  Welcome scene, where everything starts
 */
class WelcomeLayout extends VBox {

    private final double WIDTH = 300;
    private final double HEIGHT = 200;
    private Scene thisScene;
    private Game game;
    private MessageQueue<String> UIrecvQueue, UIsendQueue;

    private enum NextScene {
        SERVER,
        CLIENT
    }

    //layout of the scene
    WelcomeLayout(Stage stage, Game game, MessageQueue<String> UIrecvQueue,
                  MessageQueue<String> UIsendQueue) {
        super(20);
        super.setWidth(WIDTH);
        super.setHeight(HEIGHT);

        this.game = game;
        this.UIrecvQueue = UIrecvQueue;
        this.UIsendQueue = UIsendQueue;

        stage.setTitle("Deny and Conquer");

        Label welcomeLabel= new Label("Welcome to the Game");
        Label nameLabel = new Label(game.getMyName());

        Button serverButton = new Button("Start as Server");
        checkAndGoToNextScene(stage, serverButton, NextScene.SERVER);

        Button clientButton = new Button("Start as Client");
        checkAndGoToNextScene(stage, clientButton, NextScene.CLIENT);

        this.getChildren().addAll(welcomeLabel, serverButton, clientButton, nameLabel);
    }

    //go to next scene based on button
    private void checkAndGoToNextScene(Stage stage, Button button, NextScene nextScene) {
        button.setOnAction(e -> openNextScene(stage, nextScene));
    }

    private void openNextScene(Stage stage, NextScene nextSceneType) {
        Scene nextScene;

        if(nextSceneType == NextScene.CLIENT) {
            ClientLayout clientLayout = new ClientLayout(stage, game, UIrecvQueue, UIsendQueue);
            nextScene = new Scene(clientLayout, clientLayout.getWidth(), clientLayout.getHeight());
            clientLayout.setScene(thisScene);
        } else {
            ServerLayout serverLayout = new ServerLayout(stage, game, UIrecvQueue, UIsendQueue);
            nextScene = new Scene(serverLayout, serverLayout.getWidth(), serverLayout.getHeight());
            serverLayout.setScene(thisScene);
        }

        stage.setScene(nextScene);
    }

    //back button
    void setScene(Scene thisScene) {
        this.thisScene = thisScene;
    }
}
