package com.raez.finance;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Single entry point for the RAEZ Finance application.
 * Initializes the JavaFX Stage and loads the Login view.
 *
 * To run: use {@code mvn javafx:run}, double-click run.bat, or run {@link com.raez.finance.MainLauncher}
 * (Right-click MainLauncher.java → Run) so JavaFX is available even when the IDE does not add it to the module path.
 */
public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/raez/finance/view/RoleSelection.fxml")
        );
        Parent root = loader.load();
        Scene scene = new Scene(root);
        java.net.URL cssUrl = getClass().getResource("/css/app.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }
        primaryStage.setTitle("RAEZ Finance Portal");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
