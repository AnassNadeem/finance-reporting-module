package com.raez.finance.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;
import java.util.function.Consumer;

/**
 * StageNavigator
 * ─────────────────────────────────────────────────────────────────────
 * Package  : com.raez.finance.util   (same as CurrencyUtil, DBConnection)
 * Purpose  : Central scene-transition utility that preserves Stage maximized /
 *            full-screen / windowed state across every scene swap.
 *
 * ROOT CAUSE OF THE WINDOW-SHRINK BUG
 * ─────────────────────────────────────
 * When stage.setScene(newScene) is called, JavaFX internally calls
 * Window.sizeToScene() which resets the window dimensions to the FXML's
 * prefWidth/prefHeight and clears the maximized flag.  The fix is to
 * snapshot the state BEFORE setScene(), swap the scene, then re-apply
 * the state immediately after — using a false→true toggle on setMaximized()
 * to force JavaFX to re-calculate the maximized layout.
 *
 * RULES FOR ALL CONTROLLERS
 * ─────────────────────────
 *   ✓ Use StageNavigator.navigate(stage, path) for every scene change
 *   ✓ Use StageNavigator.navigateToLogin(stage) for logout/back flows
 *   ✗ Never call stage.sizeToScene()
 *   ✗ Never pass explicit width/height to new Scene(root, w, h)
 *   ✗ Never call stage.setWidth/setHeight BEFORE setScene()
 *
 * USAGE
 * ─────
 *   // Simple — no controller wiring needed:
 *   StageNavigator.navigate(stage, "/com/raez/finance/view/MainLayout.fxml");
 *
 *   // With controller callback — wire dependencies before the scene shows:
 *   StageNavigator.navigate(stage, "/com/raez/finance/view/MainLayout.fxml", ctrl -> {
 *       MainLayoutController mlc = (MainLayoutController) ctrl;
 *       MainLayoutController.queueStartupToast("success", "Welcome back!");
 *   });
 *
 *   // Logout / back to role selection:
 *   StageNavigator.navigateToLogin(stage);
 */
public final class StageNavigator {

    private static final String CSS_PATH            = "/css/app.css";
    private static final String ROLE_SELECTION_FXML = "/com/raez/finance/view/RoleSelection.fxml";

    private StageNavigator() {}

    // ══════════════════════════════════════════════════════════════════════
    //  NAVIGATE — preserves maximized / full-screen / windowed state
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Navigate to any FXML view while keeping the stage at its current size.
     *
     * @param stage    The primary stage (never create a new Stage).
     * @param fxmlPath Absolute classpath path, e.g. "/com/raez/finance/view/Foo.fxml"
     */
    public static void navigate(Stage stage, String fxmlPath) {
        navigate(stage, fxmlPath, null);
    }

    /**
     * Navigate to any FXML view, optionally receiving the loaded controller.
     *
     * @param stage          The primary stage.
     * @param fxmlPath       Absolute classpath path.
     * @param controllerInit Receives the loaded controller; pass null if not needed.
     */
    public static void navigate(Stage stage, String fxmlPath, Consumer<Object> controllerInit) {
        try {
            // ── 1. Snapshot BEFORE any scene change ──────────────────────
            boolean wasMaximized  = stage.isMaximized();
            boolean wasFullScreen = stage.isFullScreen();
            double  prevW         = stage.getWidth();
            double  prevH         = stage.getHeight();
            double  prevX         = stage.getX();
            double  prevY         = stage.getY();

            // ── 2. Load FXML ──────────────────────────────────────────────
            URL url = StageNavigator.class.getResource(fxmlPath);
            if (url == null) {
                System.err.println("[StageNavigator] FXML not found: " + fxmlPath);
                return;
            }
            FXMLLoader loader = new FXMLLoader(url);
            Parent root = loader.load();

            if (controllerInit != null && loader.getController() != null)
                controllerInit.accept(loader.getController());

            // ── 3. Build scene — NO explicit dimensions ───────────────────
            Scene scene = new Scene(root);
            URL css = StageNavigator.class.getResource(CSS_PATH);
            if (css != null) scene.getStylesheets().add(css.toExternalForm());

            // ── 4. Swap scene — JavaFX may shrink window here ─────────────
            stage.setScene(scene);

            // ── 5. Restore stage state immediately after ──────────────────
            if (wasFullScreen) {
                stage.setFullScreen(true);

            } else if (wasMaximized) {
                // false→true toggle forces JavaFX to re-apply maximized layout.
                // Calling setMaximized(true) alone is often a no-op when JavaFX
                // thinks the state is already "maximized" but hasn't re-laid out.
                stage.setMaximized(false);
                stage.setMaximized(true);

            } else {
                // Windowed: restore exact pixel position + size
                stage.setWidth(prevW);
                stage.setHeight(prevH);
                stage.setX(prevX);
                stage.setY(prevY);
            }

            stage.show();

        } catch (Exception ex) {
            System.err.println("[StageNavigator] Navigation failed: " + fxmlPath);
            ex.printStackTrace();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  NAVIGATE TO LOGIN  (logout / back flows)
    //  Resets to a sensible windowed size if not maximized.
    // ══════════════════════════════════════════════════════════════════════

    public static void navigateToLogin(Stage stage) {
        try {
            boolean wasMaximized = stage.isMaximized();

            URL url = StageNavigator.class.getResource(ROLE_SELECTION_FXML);
            if (url == null) {
                System.err.println("[StageNavigator] RoleSelection.fxml not found");
                return;
            }
            Parent root  = FXMLLoader.load(url);
            Scene  scene = new Scene(root);
            URL    css   = StageNavigator.class.getResource(CSS_PATH);
            if (css != null) scene.getStylesheets().add(css.toExternalForm());

            stage.setScene(scene);

            if (wasMaximized) {
                stage.setMaximized(false);
                stage.setMaximized(true);
            } else {
                stage.setWidth(1000);
                stage.setHeight(700);
                stage.centerOnScreen();
            }
            stage.show();

        } catch (Exception ex) {
            System.err.println("[StageNavigator] navigateToLogin failed");
            ex.printStackTrace();
        }
    }
}