package com.raez.finance.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;

public class FooterController {

    @FXML
    public void initialize() {
        // Footer doesn't need much initialization, but the method is here for JavaFX standards.
    }

    @FXML
    private void handlePrivacyPolicy(ActionEvent event) {
        System.out.println("Opening Privacy Policy...");
        // Cursor can add logic to open a web browser or show a modal
    }

    @FXML
    private void handleTermsOfService(ActionEvent event) {
        System.out.println("Opening Terms of Service...");
    }

    @FXML
    private void handleSupport(ActionEvent event) {
        System.out.println("Opening Support Portal...");
    }
}