package com.raez.finance;

import com.raez.finance.util.StageNavigator;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.net.URL;

/**
 * MainApp — JavaFX Application entry point.
 *
 * DO NOT call stage.sizeToScene() anywhere in the app — it overrides maximized state.
 * All scene transitions must go through StageNavigator (com.raez.finance.util.StageNavigator).
 *
 * Run via:
 *   mvn javafx:run           (recommended — always works)
 *   Right-click MainLauncher → Run  (IDE without module-path config)
 */
public class MainApp extends Application {

    private static final String ROLE_SELECTION = "/com/raez/finance/view/RoleSelection.fxml";
    private static final String CSS            = "/css/app.css";
    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.initStyle(StageStyle.DECORATED);
        primaryStage.setResizable(true);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(620);
        primaryStage.setTitle("RAEZ Finance Portal");

        // Load RoleSelection as the first scene.
        // No explicit width/height passed to Scene — avoids sizeToScene interference.
        Parent root = FXMLLoader.load(
            getClass().getResource(ROLE_SELECTION)
        );
        Scene scene = new Scene(root);
        URL css = getClass().getResource(CSS);
        if (css != null) scene.getStylesheets().add(css.toExternalForm());

        primaryStage.setScene(scene);

        // Re-apply maximize after setScene() — JavaFX can clear it during the swap.
        StageNavigator.forceMaximizedLayout(primaryStage);

        primaryStage.setOnCloseRequest(e -> Platform.exit());
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}