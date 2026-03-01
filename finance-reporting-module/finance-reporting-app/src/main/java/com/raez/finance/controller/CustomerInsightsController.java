package com.raez.finance.controller;

import com.raez.finance.dao.CustomerDao;
import com.raez.finance.model.TopBuyerRow;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CustomerInsightsController {

    private final CustomerDao customerDao = new CustomerDao();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ObservableList<TopBuyerRow> topBuyerItems = FXCollections.observableArrayList();

    @FXML private ComboBox<String> cmbCustomerFilter;

    @FXML private Label lblTotalCustomers;
    @FXML private Label lblAvgSpending;
    @FXML private Label lblAvgFrequency;
    @FXML private Label lblCompanyCustomers;

    @FXML private BarChart<String, Number> chartFrequency;
    @FXML private TableView<TopBuyerRow> tblTopBuyers;
    @FXML private TableColumn<TopBuyerRow, Number> colRank;
    @FXML private TableColumn<TopBuyerRow, String> colName;
    @FXML private TableColumn<TopBuyerRow, String> colType;
    @FXML private TableColumn<TopBuyerRow, String> colCountry;
    @FXML private TableColumn<TopBuyerRow, Number> colSpent;
    @FXML private TableColumn<TopBuyerRow, Number> colOrders;
    @FXML private TableColumn<TopBuyerRow, Number> colAOV;
    @FXML private TableColumn<TopBuyerRow, String> colLastPurchase;

    @FXML
    public void initialize() {
        cmbCustomerFilter.setItems(FXCollections.observableArrayList("All Customers", "Companies", "Normal Users"));
        cmbCustomerFilter.setValue("All Customers");

        colRank.setCellValueFactory(new PropertyValueFactory<>("rank"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colCountry.setCellValueFactory(new PropertyValueFactory<>("country"));
        colSpent.setCellValueFactory(new PropertyValueFactory<>("totalSpent"));
        colOrders.setCellValueFactory(new PropertyValueFactory<>("orderCount"));
        colAOV.setCellValueFactory(new PropertyValueFactory<>("avgOrderValue"));
        colLastPurchase.setCellValueFactory(new PropertyValueFactory<>("lastPurchase"));

        tblTopBuyers.setItems(topBuyerItems);

        cmbCustomerFilter.valueProperty().addListener((obs, oldV, newV) -> loadData());

        loadData();
    }

    private void loadData() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                int totalCustomers = customerDao.getTotalCustomerCount();
                double totalRevenue = customerDao.getTotalRevenue();
                double avgSpending = totalCustomers > 0 ? totalRevenue / totalCustomers : 0;
                List<CustomerDao.MonthlyCount> monthly = customerDao.findMonthlyOrderCounts();
                int totalOrdersLast12 = monthly.stream().mapToInt(m -> m.count).sum();
                double avgFrequency = totalCustomers > 0 && totalOrdersLast12 > 0
                        ? (double) totalOrdersLast12 / 12 / totalCustomers : 0;
                List<TopBuyerRow> topBuyers = customerDao.findTopBuyers(100);

                javafx.application.Platform.runLater(() -> {
                    lblTotalCustomers.setText(String.format("%,d", totalCustomers));
                    lblAvgSpending.setText(String.format("$%,.2f", avgSpending));
                    lblAvgFrequency.setText(String.format("%.1f / month", avgFrequency));
                    lblCompanyCustomers.setText("0");

                    topBuyerItems.clear();
                    topBuyerItems.addAll(topBuyers);

                    chartFrequency.getData().clear();
                    XYChart.Series<String, Number> series = new XYChart.Series<>();
                    for (CustomerDao.MonthlyCount m : monthly) {
                        series.getData().add(new XYChart.Data<>(m.month, m.count));
                    }
                    chartFrequency.getData().add(series);
                });
                return null;
            }
        };
        task.exceptionProperty().addListener((o, p, ex) -> { if (ex != null) ex.printStackTrace(); });
        executor.execute(task);
    }

    @FXML
    private void handleExportCSV(ActionEvent event) {
        System.out.println("Exporting Customer Insights to CSV...");
    }

    @FXML
    private void handleExportPDF(ActionEvent event) {
        System.out.println("Exporting Customer Insights to PDF...");
    }
}
