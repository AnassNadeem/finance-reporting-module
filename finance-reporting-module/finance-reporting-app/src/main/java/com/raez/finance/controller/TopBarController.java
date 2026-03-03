package com.raez.finance.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.geometry.Side;

import java.net.URL;

public class TopBarController {

    private static final String VIEW_PATH = "/com/raez/finance/view/";

    @FXML private TextField txtSearch;
    @FXML private Button btnNotifications;
    @FXML private Button btnProfile;
    
    @FXML private Label lblInitials;
    @FXML private Label lblName;
    @FXML private Label lblRole;

    private MainLayoutController mainLayoutController;
    private ContextMenu profileMenu;
    private ContextMenu notificationsMenu;

    public void setMainLayoutController(MainLayoutController mainLayoutController) {
        this.mainLayoutController = mainLayoutController;
    }

    @FXML
    public void initialize() {
        try {
            com.raez.finance.model.FUser user = com.raez.finance.service.SessionManager.getCurrentUser();
            String name = user.getFirstName() != null && !user.getFirstName().isBlank()
                    ? (user.getFirstName() + " " + (user.getLastName() != null ? user.getLastName() : "")).trim()
                    : user.getUsername() != null ? user.getUsername() : user.getEmail();
            if (name == null || name.isEmpty()) name = "User";
            setUserData(name, user.getRole() == com.raez.finance.model.UserRole.ADMIN ? "admin" : "finance");
        } catch (Exception e) {
            setUserData("User", "finance");
        }

        // 2. Build the Dropdown Menus programmatically
        buildProfileMenu();
        buildNotificationsMenu();
        
        // 3. Global search: print query to console when user types
        txtSearch.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                System.out.println("Global search: " + newValue);
            }
        });
    }

    private void setUserData(String name, String role) {
        lblName.setText(name);
        lblRole.setText(role.equals("admin") ? "Admin" : "Finance User");
        
        // Generate Initials
        if (name != null && !name.isEmpty()) {
            String[] parts = name.split(" ");
            String initials = (parts[0].substring(0, 1) + 
                              (parts.length > 1 ? parts[1].substring(0, 1) : "")).toUpperCase();
            lblInitials.setText(initials);
        }
    }

    private void buildProfileMenu() {
        profileMenu = new ContextMenu();
        profileMenu.setStyle("-fx-background-radius: 8; -fx-border-radius: 8; -fx-padding: 8;");

        MenuItem itemSettings = new MenuItem("⚙ Settings");
        itemSettings.setOnAction(e -> System.out.println("Navigate to Settings"));

        MenuItem itemAlerts = new MenuItem("🔔 Notifications & Alerts");
        itemAlerts.setOnAction(e -> System.out.println("Navigate to Notifications"));

        // Nested Submenu for Export
        Menu menuExport = new Menu("📥 Export Reports");
        MenuItem exportOverall = new MenuItem("Overall Report");
        MenuItem exportOrder = new MenuItem("Order Report");
        MenuItem exportProduct = new MenuItem("Product Report");
        MenuItem exportCustomer = new MenuItem("Customer Report");
        menuExport.getItems().addAll(exportOverall, exportOrder, exportProduct, exportCustomer);

        MenuItem itemLogout = new MenuItem("🚪 Logout");
        itemLogout.setStyle("-fx-text-fill: red;");
        itemLogout.setOnAction(e -> {
            if (mainLayoutController != null) {
                mainLayoutController.handleLogout();
            }
        });

        profileMenu.getItems().addAll(
            itemSettings, 
            itemAlerts, 
            menuExport, 
            new SeparatorMenuItem(), 
            itemLogout
        );
    }

    private void buildNotificationsMenu() {
        notificationsMenu = new ContextMenu();
        notificationsMenu.setStyle("-fx-background-radius: 8; -fx-padding: 4;");
        
        // Mock notifications
        MenuItem notif1 = new MenuItem("🔴 New order received - #ORD-1847\n2 min ago");
        MenuItem notif2 = new MenuItem("🔴 Payment processed successfully\n15 min ago");
        MenuItem notif3 = new MenuItem("⚪ Monthly report is ready\n1 hour ago");
        
        MenuItem seeMore = new MenuItem("See More...");
        seeMore.setOnAction(e -> navigateToContent(VIEW_PATH + "NotificationsAlerts.fxml"));

        notificationsMenu.getItems().addAll(notif1, notif2, notif3, new SeparatorMenuItem(), seeMore);
    }

    @FXML
    private void handleProfileClick(ActionEvent event) {
        // Show the context menu directly below the profile button
        profileMenu.show(btnProfile, Side.BOTTOM, 0, 5);
    }

    @FXML
    private void handleNotificationsClick(ActionEvent event) {
        notificationsMenu.show(btnNotifications, Side.BOTTOM, 0, 5);
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
            ex.printStackTrace();
        }
    }
}