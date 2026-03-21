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
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
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

    private static final String VIEW_PATH        = "/com/raez/finance/view/";
    private static final int    SUGGESTION_LIMIT = 5;

    // ── Role colour constants ────────────────────────────────────────────
    private static final String ADMIN_AVATAR_STYLE =
        "-fx-background-color: #059669; -fx-background-radius: 999;";
    private static final String USER_AVATAR_STYLE  =
        "-fx-background-color: #2563EB; -fx-background-radius: 999;";
    private static final String ADMIN_ROLE_STYLE   =
        "-fx-text-fill: #059669; -fx-font-weight: 700; -fx-font-size: 11px;";
    private static final String USER_ROLE_STYLE    =
        "-fx-text-fill: #2563EB; -fx-font-weight: 700; -fx-font-size: 11px;";

    // ── FXML injections ──────────────────────────────────────────────────
    @FXML private TextField  txtSearch;
    @FXML private Label      lblNotificationBadge;
    @FXML private Button     btnFullReport;
    @FXML private Button     btnNotifications;
    @FXML private Button     btnProfile;
    @FXML private Button     btnClearSearch;
    @FXML private StackPane  avatarStack;
    @FXML private Label      lblInitials;
    @FXML private Label      lblName;
    @FXML private Label      lblRole;
    @FXML private SVGPath    profileChevron;

    private MainLayoutController mainLayoutController;
    private PauseTransition      searchDebounce;
    private Popup                suggestionPopup;
    private Popup                profilePopup;
    private Popup                mergedReportPopup;  // side-popout for Merged Reports
    private VBox                 suggestionContent;

    private final ExecutorService searchExecutor = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "search-pool");
        t.setDaemon(true);
        return t;
    });

    public void setMainLayoutController(MainLayoutController mlc) {
        this.mainLayoutController = mlc;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  INIT
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        FUser   user    = SessionManager.getCurrentUserOrNull();
        boolean isAdmin = user != null && user.getRole() == UserRole.ADMIN;

        setUserData(SessionManager.getDisplayName(), isAdmin ? "admin" : "finance");
        applyAvatarStyle(isAdmin);
        applyRoleLabelStyle(isAdmin);

        // Admin-only buttons
        setAdminOnly(btnFullReport, isAdmin);

        // Hover effects on top-bar icon buttons
        addTopBarHover(btnNotifications);
        addTopBarHover(btnProfile);
        if (btnFullReport != null) addFullReportHover(btnFullReport);

        // ── Search wiring ────────────────────────────────────────────────
        buildSuggestionPopup();
        searchDebounce = new PauseTransition(Duration.millis(280));
        searchDebounce.setOnFinished(e -> runSearchSuggestions(txtSearch.getText()));

        txtSearch.textProperty().addListener((obs, o, n) -> {
            if (n == null) return;
            if (n.isBlank()) { hidePopup(suggestionPopup); toggleClear(false); return; }
            toggleClear(true);
            if (n.trim().length() >= 2) searchDebounce.playFromStart();
            else hidePopup(suggestionPopup);
        });

        txtSearch.setOnAction(e -> {
            if (searchDebounce != null) searchDebounce.stop();
            hidePopup(suggestionPopup);
            String q = txtSearch.getText();
            if (q != null && !q.trim().isEmpty() && mainLayoutController != null)
                mainLayoutController.showGlobalSearch(q.trim());
        });

        txtSearch.focusedProperty().addListener((obs, was, is) -> {
            if (!is) hidePopup(suggestionPopup);
        });

        if (btnClearSearch != null) {
            btnClearSearch.setOnAction(ev -> {
                txtSearch.clear();
                if (searchDebounce != null) searchDebounce.stop();
                hidePopup(suggestionPopup);
            });
        }

        setNotificationBadgeCount(0);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  NOTIFICATION BADGE
    // ══════════════════════════════════════════════════════════════════════

    public void setNotificationBadgeCount(int count) {
        if (lblNotificationBadge == null) return;
        boolean show = count > 0;
        lblNotificationBadge.setVisible(show);
        lblNotificationBadge.setManaged(show);
        lblNotificationBadge.setText(count > 99 ? "99+" : (show ? String.valueOf(count) : ""));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  FXML HANDLERS
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    private void handleFullReport() {
        if (mainLayoutController != null) mainLayoutController.exportFullMergedReportPdf();
    }

    @FXML
    private void handleNotificationsClick() {
        if (mainLayoutController != null) navigateTo(VIEW_PATH + "NotificationsAlerts.fxml");
    }

    @FXML
    private void handleProfileClick() {
        if (profilePopup != null && profilePopup.isShowing()) {
            hidePopup(profilePopup);
            animateChevron(false);
            return;
        }
        hidePopup(mergedReportPopup);
        VBox card = buildProfileCard();
        card.setScaleX(0.95); card.setScaleY(0.95); card.setOpacity(0);

        if (profilePopup == null) {
            profilePopup = new Popup();
            profilePopup.setAutoHide(true);
            profilePopup.setHideOnEscape(true);
            profilePopup.setOnAutoHide(e -> {
                animateChevron(false);
                hidePopup(mergedReportPopup);
            });
        }
        profilePopup.getContent().setAll(card);

        Platform.runLater(() -> {
            if (btnProfile == null || btnProfile.getScene() == null) return;
            showPopupBelow(profilePopup, btnProfile);
            animateChevron(true);
            // Entrance animation
            FadeTransition  ft = new FadeTransition(Duration.millis(160), card);
            ft.setFromValue(0); ft.setToValue(1);
            ScaleTransition st = new ScaleTransition(Duration.millis(160), card);
            st.setFromX(0.95); st.setToX(1.0);
            st.setFromY(0.95); st.setToY(1.0);
            ft.play(); st.play();
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PROFILE DROPDOWN CARD
    //  Settings | Notifications | Merged Reports (side popout) | Sign Out
    // ══════════════════════════════════════════════════════════════════════

    private VBox buildProfileCard() {
        VBox card = new VBox(0);
        card.getStyleClass().add("profile-dropdown");
        card.setPrefWidth(272);
        card.setMaxWidth(300);
        card.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 12;" +
            "-fx-border-color: #E5E7EB;" +
            "-fx-border-radius: 12;" +
            "-fx-border-width: 1;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.14), 18, 0, 0, 6);" +
            "-fx-padding: 8 0 8 0;"
        );

        FUser   user        = SessionManager.getCurrentUserOrNull();
        boolean isAdmin     = SessionManager.isAdmin();
        String  displayName = SessionManager.getDisplayName();
        String  initials    = SessionManager.getInitials();
        String  email       = user != null && user.getEmail() != null ? user.getEmail() : "—";

        // ── User identity block ───────────────────────────────────────────
        HBox identity = new HBox(12);
        identity.setAlignment(Pos.CENTER_LEFT);
        identity.setPadding(new Insets(8, 16, 14, 16));

        Label avatarLbl = new Label(initials);
        avatarLbl.setPrefWidth(42); avatarLbl.setPrefHeight(42);
        avatarLbl.setAlignment(Pos.CENTER);
        avatarLbl.setTextFill(Color.WHITE);
        avatarLbl.setStyle(
            (isAdmin ? "-fx-background-color: #059669;" : "-fx-background-color: #2563EB;") +
            "-fx-background-radius: 999;" +
            "-fx-font-weight: 700; -fx-font-size: 15px;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 6, 0, 0, 1);"
        );

        VBox nameBox = new VBox(2);
        Label nameLbl = new Label(displayName);
        nameLbl.setStyle("-fx-font-size: 13.5px; -fx-font-weight: 700; -fx-text-fill: #111827;");
        Label emailLbl = new Label(email);
        emailLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #6B7280;");

        // Role badge
        Label roleBadge = new Label(isAdmin ? "ADMIN" : "FINANCE USER");
        roleBadge.setStyle(
            (isAdmin
                ? "-fx-background-color: #D1FAE5; -fx-text-fill: #065F46;"
                : "-fx-background-color: #DBEAFE; -fx-text-fill: #1E40AF;") +
            "-fx-font-size: 9.5px; -fx-font-weight: 700;" +
            "-fx-background-radius: 4; -fx-padding: 2 6 2 6;"
        );
        nameBox.getChildren().addAll(nameLbl, emailLbl, roleBadge);
        identity.getChildren().addAll(avatarLbl, nameBox);

        // ── Divider ───────────────────────────────────────────────────────
        Region div1 = makeDivider();

        // ── Menu items ────────────────────────────────────────────────────
        Button btnSettings = makeMenuItem(
            "Settings",
            "M12 15a3 3 0 100-6 3 3 0 000 6zM19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83 0 2 2 0 010-2.83l.06-.06A1.65 1.65 0 004.6 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 010-2.83 2 2 0 012.83 0l.06.06A1.65 1.65 0 009 4.6a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 0 2 2 0 010 2.83l-.06.06a1.65 1.65 0 00-.33 1.82V9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z",
            "#6B7280", false
        );
        btnSettings.setOnAction(e -> { hideAll(); navigateTo(VIEW_PATH + "Settings.fxml"); });

        Button btnNotifNav = makeMenuItem(
            "Notifications & Alerts",
            "M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9 M13.73 21a2 2 0 01-3.46 0",
            "#6B7280", false
        );
        btnNotifNav.setOnAction(e -> { hideAll(); navigateTo(VIEW_PATH + "NotificationsAlerts.fxml"); });

        card.getChildren().addAll(identity, div1, btnSettings, btnNotifNav);

        // ── Merged Reports (admin only) ───────────────────────────────────
        if (isAdmin) {
            Region div2 = makeDivider();
            HBox mergedRow = buildMergedReportsRow(card);
            card.getChildren().addAll(div2, mergedRow);
        }

        // ── Sign Out ──────────────────────────────────────────────────────
        Region divLast = makeDivider();
        Button btnSignOut = makeMenuItem(
            "Sign Out",
            "M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4M16 17l5-5-5-5M21 12H9",
            "#EF4444", true
        );
        btnSignOut.setOnAction(e -> {
            hideAll();
            if (mainLayoutController != null) mainLayoutController.handleLogout();
        });
        card.getChildren().addAll(divLast, btnSignOut);

        return card;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MERGED REPORTS ROW  — hover triggers side-popout sub-menu
    // ══════════════════════════════════════════════════════════════════════

    private HBox buildMergedReportsRow(VBox parentCard) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 16, 10, 16));
        row.setMaxWidth(Double.MAX_VALUE);
        row.setStyle("-fx-cursor: hand;");

        // Updated SVG Icon for Merged Reports (Overlapping Documents icon)
        SVGPath icon = makeSvgIcon(
            "M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z",
            "#6B7280"
        );

        Label lbl = new Label("Merged Reports");
        lbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #374151;");
        HBox.setHgrow(lbl, Priority.ALWAYS);

        // Right arrow indicator
        SVGPath arrow = new SVGPath();
        arrow.setContent("M9 18l6-6-6-6");
        arrow.setFill(Color.TRANSPARENT);
        arrow.setStroke(Color.web("#9CA3AF"));
        arrow.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        arrow.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        arrow.setStrokeWidth(1.8);

        row.getChildren().addAll(icon, lbl, arrow);
        applyMenuHover(row);

        // Build the sub-menu popup
        buildMergedReportSubPopup();

        row.setOnMouseEntered(e -> {
            if (mergedReportPopup != null && !mergedReportPopup.isShowing()) {
                Point2D pt = row.localToScreen(row.getBoundsInLocal().getWidth(),
                                               -4);
                if (pt != null && row.getScene() != null)
                    mergedReportPopup.show(row.getScene().getWindow(), pt.getX() + 4, pt.getY());
            }
        });
        row.setOnMouseClicked(e -> {
            if (mergedReportPopup != null && !mergedReportPopup.isShowing()) {
                Point2D pt = row.localToScreen(row.getBoundsInLocal().getWidth(), -4);
                if (pt != null && row.getScene() != null)
                    mergedReportPopup.show(row.getScene().getWindow(), pt.getX() + 4, pt.getY());
            }
        });

        return row;
    }

    private void buildMergedReportSubPopup() {
        if (mergedReportPopup != null) return;

        VBox sub = new VBox(2);
        sub.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 12;" +
            "-fx-border-color: #E5E7EB;" +
            "-fx-border-radius: 12;" +
            "-fx-border-width: 1;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.14), 16, 0, 0, 5);" +
            "-fx-padding: 8 0 8 0;"
        );
        sub.setPrefWidth(220);

        // Sub-menu label
        Label heading = new Label("MERGED REPORTS");
        heading.setStyle("-fx-font-size: 10px; -fx-font-weight: 700; -fx-text-fill: #9CA3AF; -fx-padding: 4 16 8 16;");
        sub.getChildren().add(heading);

        record Entry(String label, String svg, String color, Runnable action) {}
        List<Entry> entries = List.of(
            new Entry("Customer Report",
                "M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2 M9 3a4 4 0 100 8 4 4 0 000-8z M23 21v-2a4 4 0 00-3-3.87 M16 3.13a4 4 0 010 7.75",
                "#10B981",
                () -> { if (mainLayoutController != null) mainLayoutController.exportMergedCustomerReport(); }),
            new Entry("Order Report",
                "M9 20a1 1 0 100-2 1 1 0 000 2zM20 20a1 1 0 100-2 1 1 0 000 2zM1 1h4l2.68 13.39a2 2 0 002 1.61h9.72a2 2 0 002-1.61L23 6H6",
                "#8B5CF6",
                () -> { if (mainLayoutController != null) mainLayoutController.exportMergedOrderReport(); }),
            new Entry("Product Report",
                "M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4",
                "#F59E0B",
                () -> { if (mainLayoutController != null) mainLayoutController.exportMergedProductReport(); })
        );

        for (Entry en : entries) {
            HBox item = new HBox(10);
            item.setAlignment(Pos.CENTER_LEFT);
            item.setPadding(new Insets(9, 16, 9, 16));
            item.setMaxWidth(Double.MAX_VALUE);
            item.setStyle("-fx-cursor: hand;");

            SVGPath ico = makeSvgIcon(en.svg(), en.color());

            Label txt = new Label(en.label());
            txt.setStyle("-fx-font-size: 13px; -fx-text-fill: #374151;");

            item.getChildren().addAll(ico, txt);
            applyMenuHover(item);
            item.setOnMouseClicked(me -> { hideAll(); en.action().run(); });
            sub.getChildren().add(item);
        }

        mergedReportPopup = new Popup();
        mergedReportPopup.setAutoHide(true);
        mergedReportPopup.setHideOnEscape(true);
        mergedReportPopup.getContent().add(sub);

        // Fade-in on show
        sub.setOpacity(0);
        mergedReportPopup.setOnShown(ev -> {
            FadeTransition ft = new FadeTransition(Duration.millis(140), sub);
            ft.setFromValue(0); ft.setToValue(1); ft.play();
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SEARCH SUGGESTIONS POPUP
    // ══════════════════════════════════════════════════════════════════════

    private void buildSuggestionPopup() {
        suggestionPopup = new Popup();
        suggestionPopup.setAutoHide(true);
        suggestionPopup.setHideOnEscape(true);
        suggestionContent = new VBox(4);
        suggestionContent.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 10;" +
            "-fx-border-color: #E5E7EB;" +
            "-fx-border-radius: 10;" +
            "-fx-border-width: 1;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 14, 0, 0, 4);" +
            "-fx-padding: 8 0 8 0;"
        );
        suggestionContent.setPrefWidth(400);
        suggestionContent.setMaxWidth(460);
        suggestionPopup.getContent().add(suggestionContent);
    }

    private void runSearchSuggestions(String query) {
        String q = query != null ? query.trim() : "";
        if (q.length() < 2 || mainLayoutController == null) { hidePopup(suggestionPopup); return; }

        Task<Map<String, List<String[]>>> task = new Task<>() {
            @Override
            protected Map<String, List<String[]>> call() throws Exception {
                OrderDao    od  = new OrderDao();
                ProductDao  pd  = new ProductDao();
                CustomerDao cd  = new CustomerDao();
                LocalDate to = LocalDate.now(), from = to.minusYears(1);

                List<String[]> orders = od.findReportRows(from, to, "All Status", null, q, SUGGESTION_LIMIT, 0)
                    .stream().map(r -> new String[]{"#" + r.getOrderId(), r.getCustomer() + " — " + r.getStatus()}).toList();
                List<String[]> products = pd.findReportRows(from, to, "All Categories", q, SUGGESTION_LIMIT, 0)
                    .stream().map(r -> new String[]{r.getName(), r.getCategory()}).toList();
                List<String[]> customers = cd.findReportRows(null, null, "All", "All", null, q, SUGGESTION_LIMIT, 0)
                    .stream().map(r -> new String[]{r.getName(), r.getType()}).toList();

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

                Label header = new Label(section.toUpperCase());
                header.setStyle("-fx-font-size: 10px; -fx-font-weight: 700; -fx-text-fill: #9CA3AF; -fx-padding: 4 16 4 16;");
                suggestionContent.getChildren().add(header);

                for (String[] item : items) {
                    HBox row = new HBox(10);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setPadding(new Insets(8, 14, 8, 14));
                    row.setStyle("-fx-cursor: hand;");
                    row.setMaxWidth(Double.MAX_VALUE);

                    SVGPath icon = createSectionIcon(section);
                    Label primary = new Label(item[0]);
                    primary.setStyle("-fx-font-size: 13px; -fx-text-fill: #111827; -fx-font-weight: 600;");
                    Label secondary = new Label(item.length > 1 ? item[1] : "");
                    secondary.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #9CA3AF;");
                    HBox.setHgrow(primary, Priority.ALWAYS);
                    row.getChildren().addAll(icon, primary, secondary);
                    applyMenuHover(row);

                    row.setOnMouseClicked(me -> {
                        hidePopup(suggestionPopup);
                        txtSearch.setText(item[0]);
                        if (mainLayoutController != null) mainLayoutController.showGlobalSearch(item[0]);
                    });
                    suggestionContent.getChildren().add(row);
                }
            }
            if (!hasAny) {
                Label noRes = new Label("No results for \"" + q + "\"");
                noRes.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #9CA3AF; -fx-padding: 8 16 8 16;");
                suggestionContent.getChildren().add(noRes);
            }
            Label hint = new Label("↵  Press Enter to see all results");
            hint.setStyle("-fx-font-size: 11px; -fx-text-fill: #C4C9D4; -fx-padding: 6 16 2 16;");
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
        switch (section) {
            case "Orders"    -> { icon.setContent("M9 20a1 1 0 100-2 1 1 0 000 2zM20 20a1 1 0 100-2 1 1 0 000 2zM1 1h4l2.68 13.39a2 2 0 002 1.61h9.72a2 2 0 002-1.61L23 6H6"); icon.setStroke(Color.web("#8B5CF6")); }
            case "Products"  -> { icon.setContent("M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4"); icon.setStroke(Color.web("#F59E0B")); }
            case "Customers" -> { icon.setContent("M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2M9 3a4 4 0 100 8 4 4 0 000-8z"); icon.setStroke(Color.web("#10B981")); }
            default          -> { icon.setContent("M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"); icon.setStroke(Color.web("#9CA3AF")); }
        }
        return icon;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  HOVER ANIMATIONS
    // ══════════════════════════════════════════════════════════════════════

    /** Subtle fade bg on HBox rows (dropdown items / suggestions). */
    private void applyMenuHover(HBox row) {
        String base = row.getStyle();
        row.setOnMouseEntered(e ->
            row.setStyle(base + "-fx-background-color: #F3F4F6; -fx-background-radius: 6;"));
        row.setOnMouseExited(e ->
            row.setStyle(base));
    }

    /** Top-bar icon buttons: subtle rounded bg on hover. */
    private void addTopBarHover(Button btn) {
        if (btn == null) return;
        String base = btn.getStyle();
        btn.setOnMouseEntered(e ->
            btn.setStyle(base + " -fx-background-color: #F3F4F6;"));
        btn.setOnMouseExited(e ->
            btn.setStyle(base));
    }

    /** Full Report button: darken on hover, scale up slightly. */
    private void addFullReportHover(Button btn) {
        if (btn == null) return;
        String hoverStyle =
            "-fx-background-color: #2D3E52; -fx-text-fill: white;" +
            "-fx-font-size: 12.5px; -fx-font-weight: 700;" +
            "-fx-background-radius: 8; -fx-padding: 9 16 9 16; -fx-cursor: hand;" +
            "-fx-effect: dropshadow(gaussian, rgba(30,41,57,0.38), 9, 0, 0, 3);";
        String baseStyle =
            "-fx-background-color: #1E2939; -fx-text-fill: white;" +
            "-fx-font-size: 12.5px; -fx-font-weight: 700;" +
            "-fx-background-radius: 8; -fx-padding: 9 16 9 16; -fx-cursor: hand;" +
            "-fx-effect: dropshadow(gaussian, rgba(30,41,57,0.28), 7, 0, 0, 2);";

        btn.setOnMouseEntered(e -> {
            btn.setStyle(hoverStyle);
            ScaleTransition st = new ScaleTransition(Duration.millis(100), btn);
            st.setToX(1.03); st.setToY(1.03); st.play();
        });
        btn.setOnMouseExited(e -> {
            btn.setStyle(baseStyle);
            ScaleTransition st = new ScaleTransition(Duration.millis(100), btn);
            st.setToX(1.0); st.setToY(1.0); st.play();
        });
    }

    /** Animates the profile chevron between ↓ and ↑. */
    private void animateChevron(boolean open) {
        if (profileChevron == null) return;
        profileChevron.setContent(open ? "M18 15l-6-6-6 6" : "M6 9l6 6 6-6");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  STYLING HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private void applyAvatarStyle(boolean admin) {
        if (avatarStack == null) return;
        avatarStack.setStyle(admin ? ADMIN_AVATAR_STYLE : USER_AVATAR_STYLE);
    }

    private void applyRoleLabelStyle(boolean admin) {
        if (lblRole == null) return;
        lblRole.setStyle(admin ? ADMIN_ROLE_STYLE : USER_ROLE_STYLE);
    }

    private void setUserData(String name, String role) {
        if (lblName     != null) lblName.setText(name);
        if (lblRole     != null) lblRole.setText("admin".equals(role) ? "Admin" : "Finance User");
        if (lblInitials != null) lblInitials.setText(SessionManager.getInitials());
    }

    private void setAdminOnly(Node node, boolean isAdmin) {
        if (node == null) return;
        node.setVisible(isAdmin);
        node.setManaged(isAdmin);
    }

    private void toggleClear(boolean show) {
        if (btnClearSearch != null) {
            btnClearSearch.setVisible(show);
            btnClearSearch.setManaged(show);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  POPUP HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private void showPopupBelow(Popup popup, Node anchor) {
        if (anchor.getScene() == null || anchor.getScene().getWindow() == null) return;
        Point2D p = anchor.localToScreen(0, anchor.getBoundsInLocal().getHeight());
        if (p == null) return;
        popup.show(anchor.getScene().getWindow(), p.getX(), p.getY() + 6);
    }

    private void hidePopup(Popup popup) {
        if (popup != null && popup.isShowing()) popup.hide();
    }

    private void hideAll() {
        hidePopup(profilePopup);
        hidePopup(mergedReportPopup);
        hidePopup(suggestionPopup);
        animateChevron(false);
    }

    private void navigateTo(String fxmlPath) {
        if (mainLayoutController == null) return;
        try {
            URL url = MainLayoutController.class.getResource(fxmlPath);
            if (url == null) url = getClass().getResource(fxmlPath);
            if (url == null) return;
            Parent root = FXMLLoader.load(url);
            mainLayoutController.setContent(root);
        } catch (Exception ex) {
            System.err.println("[TopBar] Navigation failed: " + ex.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SHARED UI FACTORIES
    // ══════════════════════════════════════════════════════════════════════

    private Button makeMenuItem(String text, String svgPath, String strokeColor, boolean isDanger) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-background-radius: 0;" +
            "-fx-padding: 10 16 10 16;" +
            "-fx-cursor: hand;" +
            (isDanger ? "-fx-text-fill: #EF4444;" : "-fx-text-fill: #374151;") +
            "-fx-font-size: 13px;"
        );
        btn.setGraphicTextGap(10);

        SVGPath icon = makeSvgIcon(svgPath, strokeColor);
        btn.setGraphic(icon);

        String base  = btn.getStyle();
        String hover = base + "-fx-background-color: " + (isDanger ? "#FEF2F2" : "#F3F4F6") + ";";
        btn.setOnMouseEntered(e -> btn.setStyle(hover));
        btn.setOnMouseExited(e  -> btn.setStyle(base));
        return btn;
    }

    private SVGPath makeSvgIcon(String path, String stroke) {
        SVGPath svg = new SVGPath();
        svg.setContent(path);
        svg.setFill(Color.TRANSPARENT);
        svg.setStroke(Color.web(stroke));
        svg.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        svg.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        svg.setStrokeWidth(1.6);
        return svg;
    }

    private Region makeDivider() {
        Region div = new Region();
        div.setPrefHeight(1);
        div.setMaxWidth(Double.MAX_VALUE);
        div.setStyle("-fx-background-color: #F3F4F6;");
        VBox.setMargin(div, new Insets(4, 0, 4, 0));
        return div;
    }

    public void shutdown() {
        searchExecutor.shutdownNow();
    }
}