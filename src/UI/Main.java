package UI;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import logic.Game;
import logic.MessageQueue;

/**
 *  Application initialization
 */
public class Main extends Application {
    @Override
    public void start(Stage primaryStage) {
        //create game logic object
        MessageQueue<String> UIrecvQueue = new MessageQueue<>();
        MessageQueue<String> UIsendQueue = new MessageQueue<>();
        Game game = new Game(UIrecvQueue, UIsendQueue);

        //create stage and welcome scene
        WelcomeLayout welcomeLayout = new WelcomeLayout(primaryStage, game, UIrecvQueue, UIsendQueue);
        Scene welcomeScene = new Scene(welcomeLayout, welcomeLayout.getWidth(), welcomeLayout.getHeight());
        welcomeLayout.setScene(welcomeScene);
        primaryStage.setScene(welcomeScene);
        primaryStage.show();
    }


    public static void main(String[] args) {
        launch(args);
    }
}
