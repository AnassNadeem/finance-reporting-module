package com.raez.finance.controller;

import com.raez.finance.dao.CustomerDao;
import com.raez.finance.dao.OrderDao;
import com.raez.finance.dao.ProductDao;
import com.raez.finance.model.FUser;
import com.raez.finance.model.UserRole;
import com.raez.finance.service.SessionManager;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Popup;
import javafx.util.Duration;

import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TopBarController {

    private static final String VIEW_PATH = "/com/raez/finance/view/";
    private static final int SUGGESTION_LIMIT = 5;

    @FXML private TextField txtSearch;
    @FXML private Button btnNotifications;
    @FXML private Button btnProfile;
    @FXML private Button btnClearSearch;
    @FXML private Label lblInitials;
    @FXML private Label lblName;
    @FXML private Label lblRole;

    private MainLayoutController mainLayoutController;
    private PauseTransition searchDebounce;
    private Popup suggestionPopup;
    private Popup profilePopup;
    private VBox suggestionContent;
    private final ExecutorService searchExecutor = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "search-pool");
        t.setDaemon(true);
        return t;
    });

    public void setMainLayoutController(MainLayoutController mainLayoutController) {
        this.mainLayoutController = mainLayoutController;
    }

    @FXML
    public void initialize() {
        FUser user = SessionManager.getCurrentUserOrNull();
        if (user != null) {
            setUserData(SessionManager.getDisplayName(),
                    user.getRole() == UserRole.ADMIN ? "admin" : "finance");
        } else {
            setUserData("User", "finance");
        }

        buildSuggestionPopup();
        buildProfilePopup();

        searchDebounce = new PauseTransition(Duration.millis(300));
        searchDebounce.setOnFinished(e -> runSearchSuggestions(txtSearch.getText()));

        txtSearch.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) return;
            if (newVal.isBlank()) {
                hidePopup(suggestionPopup);
                toggleClear(false);
                return;
            }
            toggleClear(true);
            if (newVal.trim().length() >= 2) {
                searchDebounce.playFromStart();
            } else {
                hidePopup(suggestionPopup);
            }
        });

        txtSearch.setOnAction(e -> {
            if (searchDebounce != null) searchDebounce.stop();
            hidePopup(suggestionPopup);
            String q = txtSearch.getText();
            if (q != null && !q.trim().isEmpty() && mainLayoutController != null) {
                mainLayoutController.showGlobalSearch(q.trim());
            }
        });

        txtSearch.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) hidePopup(suggestionPopup);
        });

        if (btnClearSearch != null) {
            btnClearSearch.setOnAction(ev -> {
                txtSearch.clear();
                if (searchDebounce != null) searchDebounce.stop();
                hidePopup(suggestionPopup);
            });
        }
    }

    private void toggleClear(boolean show) {
        if (btnClearSearch != null) {
            btnClearSearch.setVisible(show);
            btnClearSearch.setManaged(show);
        }
    }

    // ───────── SEARCH SUGGESTIONS (grouped popup) ─────────

    private void buildSuggestionPopup() {
        suggestionPopup = new Popup();
        suggestionPopup.setAutoHide(true);
        suggestionPopup.setHideOnEscape(true);
        suggestionContent = new VBox(4);
        suggestionContent.getStyleClass().add("search-popup");
        suggestionContent.setPrefWidth(360);
        suggestionContent.setMaxWidth(420);
        suggestionPopup.getContent().add(suggestionContent);
    }

    private void runSearchSuggestions(String query) {
        String q = query != null ? query.trim() : "";
        if (q.length() < 2 || mainLayoutController == null) {
            hidePopup(suggestionPopup);
            return;
        }

        Task<Map<String, List<String[]>>> task = new Task<>() {
            @Override
            protected Map<String, List<String[]>> call() throws Exception {
                OrderDao orderDao = new OrderDao();
                ProductDao productDao = new ProductDao();
                CustomerDao customerDao = new CustomerDao();
                LocalDate to = LocalDate.now();
                LocalDate from = to.minusYears(1);

                List<String[]> orders = orderDao.findReportRows(from, to, "All Status", q, SUGGESTION_LIMIT, 0)
                        .stream()
                        .map(r -> new String[]{"#" + r.getOrderId(), r.getCustomer() + " - " + r.getStatus()})
                        .toList();

                List<String[]> products = productDao.findReportRows(from, to, "All Categories", q, SUGGESTION_LIMIT, 0)
                        .stream()
                        .map(r -> new String[]{r.getName(), r.getCategory()})
                        .toList();

                List<String[]> customers = customerDao.findReportRows("All", "All", null, q, SUGGESTION_LIMIT, 0)
                        .stream()
                        .map(r -> new String[]{r.getName(), r.getType()})
                        .toList();

                return Map.of("Orders", orders, "Products", products, "Customers", customers);
            }
        };

        task.setOnSucceeded(ev -> {
            Map<String, List<String[]>> results = task.getValue();
            suggestionContent.getChildren().clear();

            boolean hasAny = false;
            for (String section : List.of("Orders", "Products", "Customers")) {
                List<String[]> items = results.getOrDefault(section, List.of());
                if (items.isEmpty()) continue;
                hasAny = true;

                Label header = new Label(section);
                header.getStyleClass().add("search-section-header");
                suggestionContent.getChildren().add(header);

                for (String[] item : items) {
                    HBox row = new HBox(8);
                    row.getStyleClass().add("search-result-row");
                    row.setAlignment(Pos.CENTER_LEFT);

                    SVGPath icon = createSectionIcon(section);
                    Label primary = new Label(item[0]);
                    primary.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #111827;");
                    Label secondary = new Label(item.length > 1 ? item[1] : "");
                    secondary.setStyle("-fx-font-size: 11px; -fx-text-fill: #9CA3AF;");

                    row.getChildren().addAll(icon, primary, secondary);
                    row.setOnMouseClicked(me -> {
                        hidePopup(suggestionPopup);
                        txtSearch.setText(item[0]);
                        if (mainLayoutController != null) mainLayoutController.showGlobalSearch(item[0]);
                    });
                    suggestionContent.getChildren().add(row);
                }
            }

            if (!hasAny) {
                Label noResults = new Label("No results found");
                noResults.setStyle("-fx-text-fill: #9CA3AF; -fx-font-size: 12px; -fx-padding: 8;");
                suggestionContent.getChildren().add(noResults);
            }

            Label hint = new Label("Press Enter for full results");
            hint.setStyle("-fx-text-fill: #6B7280; -fx-font-size: 11px; -fx-padding: 6 0 0 0;");
            suggestionContent.getChildren().add(hint);

            showPopupBelow(suggestionPopup, txtSearch);
        });

        task.setOnFailed(ev -> {});
        searchExecutor.execute(task);
    }

    private SVGPath createSectionIcon(String section) {
        SVGPath icon = new SVGPath();
        icon.setFill(Color.TRANSPARENT);
        icon.setStrokeWidth(1.5);
        icon.setStroke(Color.web("#9CA3AF"));
        switch (section) {
            case "Orders" -> icon.setContent("M9 20a1 1 0 100-2 1 1 0 000 2zM20 20a1 1 0 100-2 1 1 0 000 2zM1 1h4l2.68 13.39a2 2 0 002 1.61h9.72a2 2 0 002-1.61L23 6H6");
            case "Products" -> icon.setContent("M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4");
            case "Customers" -> icon.setContent("M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2M9 3a4 4 0 100 8 4 4 0 000-8z");
            default -> icon.setContent("M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z");
        }
        return icon;
    }

    // ───────── PROFILE DROPDOWN ─────────

    private void buildProfilePopup() {
        profilePopup = new Popup();
        profilePopup.setAutoHide(true);
        profilePopup.setHideOnEscape(true);

        VBox card = new VBox(0);
        card.getStyleClass().add("profile-dropdown");
        card.setPrefWidth(260);
        card.setMaxWidth(280);

        FUser user = SessionManager.getCurrentUserOrNull();
        String displayName = SessionManager.getDisplayName();
        String initials = SessionManager.getInitials();
        boolean isAdmin = SessionManager.isAdmin();
        String email = user != null && user.getEmail() != null ? user.getEmail() : "—";
        String roleName = isAdmin ? "ADMIN" : "FINANCE USER";

        HBox userInfo = new HBox(12);
        userInfo.setAlignment(Pos.CENTER_LEFT);
        userInfo.setPadding(new Insets(4, 0, 12, 0));

        Label avatar = new Label(initials);
        avatar.getStyleClass().add(isAdmin ? "profile-avatar-admin" : "profile-avatar-user");

        VBox nameBox = new VBox(2);
        Label nameLabel = new Label(displayName);
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #111827;");
        Label emailLabel = new Label(email);
        emailLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #9CA3AF;");

        HBox badgeRow = new HBox(6);
        badgeRow.setAlignment(Pos.CENTER_LEFT);
        Label roleBadge = new Label(roleName);
        roleBadge.getStyleClass().add(isAdmin ? "role-badge-admin" : "role-badge-user");
        badgeRow.getChildren().add(roleBadge);

        nameBox.getChildren().addAll(nameLabel, emailLabel, badgeRow);
        userInfo.getChildren().addAll(avatar, nameBox);

        Separator divider1 = new Separator();
        divider1.setPadding(new Insets(4, 0, 4, 0));

        Button btnMyProfile = createProfileMenuItem("My Profile", "M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2M12 3a4 4 0 100 8 4 4 0 000-8z", false);
        Button btnChangePass = createProfileMenuItem("Change Password", "M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z", false);
        Button btnPrefs = createProfileMenuItem("Preferences", "M12 15a3 3 0 100-6 3 3 0 000 6zM19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83 0 2 2 0 010-2.83l.06-.06A1.65 1.65 0 004.6 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 010-2.83 2 2 0 012.83 0l.06.06A1.65 1.65 0 009 4.6a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 0 2 2 0 010 2.83l-.06.06a1.65 1.65 0 00-.33 1.82V9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z", false);

        btnMyProfile.setOnAction(e -> { hidePopup(profilePopup); navigateToContent(VIEW_PATH + "Settings.fxml"); });
        btnChangePass.setOnAction(e -> { hidePopup(profilePopup); navigateToContent(VIEW_PATH + "Settings.fxml"); });
        btnPrefs.setOnAction(e -> { hidePopup(profilePopup); navigateToContent(VIEW_PATH + "Settings.fxml"); });

        Separator divider2 = new Separator();
        divider2.setPadding(new Insets(4, 0, 4, 0));

        Button btnSignOut = createProfileMenuItem("Sign Out", "M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4M16 17l5-5-5-5M21 12H9", true);
        btnSignOut.setOnAction(e -> {
            hidePopup(profilePopup);
            if (mainLayoutController != null) mainLayoutController.handleLogout();
        });

        card.getChildren().addAll(userInfo, divider1, btnMyProfile, btnChangePass, btnPrefs, divider2, btnSignOut);
        profilePopup.getContent().add(card);
    }

    private Button createProfileMenuItem(String text, String svgContent, boolean isDanger) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.getStyleClass().add(isDanger ? "profile-signout" : "profile-menu-item");
        btn.setGraphicTextGap(10);

        SVGPath icon = new SVGPath();
        icon.setContent(svgContent);
        icon.setFill(Color.TRANSPARENT);
        icon.setStroke(isDanger ? Color.web("#EF4444") : Color.web("#6B7280"));
        icon.setStrokeWidth(1.5);
        btn.setGraphic(icon);
        return btn;
    }

    @FXML
    private void handleProfileClick() {
        if (profilePopup.isShowing()) {
            hidePopup(profilePopup);
            return;
        }
        VBox card = (VBox) profilePopup.getContent().getFirst();
        card.setScaleY(0.95);
        card.setOpacity(0);

        showPopupBelow(profilePopup, btnProfile);

        FadeTransition fade = new FadeTransition(Duration.millis(150), card);
        fade.setFromValue(0);
        fade.setToValue(1);
        ScaleTransition scale = new ScaleTransition(Duration.millis(150), card);
        scale.setFromY(0.95);
        scale.setToY(1);
        fade.play();
        scale.play();
    }

    @FXML
    private void handleNotificationsClick() {
        if (mainLayoutController != null) {
            navigateToContent(VIEW_PATH + "NotificationsAlerts.fxml");
        }
    }

    private void setUserData(String name, String role) {
        if (lblName != null) lblName.setText(name);
        if (lblRole != null) lblRole.setText(role.equals("admin") ? "Admin" : "Finance User");
        if (lblInitials != null) lblInitials.setText(SessionManager.getInitials());
    }

    private void showPopupBelow(Popup popup, javafx.scene.Node anchor) {
        if (anchor.getScene() == null || anchor.getScene().getWindow() == null) return;
        Point2D p = anchor.localToScreen(0, anchor.getBoundsInLocal().getHeight());
        if (p == null) return;
        popup.show(anchor.getScene().getWindow(), p.getX(), p.getY() + 4);
    }

    private void hidePopup(Popup popup) {
        if (popup != null && popup.isShowing()) popup.hide();
    }

    private void navigateToContent(String fxmlPath) {
        if (mainLayoutController == null) return;
        try {
            URL url = MainLayoutController.class.getResource(fxmlPath);
            if (url == null) url = getClass().getResource(fxmlPath);
            if (url == null) return;
            Parent root = FXMLLoader.load(url);
            mainLayoutController.setContent(root);
        } catch (Exception ex) {
            System.err.println("Navigation failed: " + ex.getMessage());
        }
    }

    public void shutdown() {
        searchExecutor.shutdownNow();
    }
}
