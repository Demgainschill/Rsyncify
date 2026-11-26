package com.rsyncify;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/shell.fxml"));
        Scene scene = new Scene(loader.load(), 980, 720);
        stage.setTitle("Rsyncify 1.0: Reliable Backup & Sync");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
