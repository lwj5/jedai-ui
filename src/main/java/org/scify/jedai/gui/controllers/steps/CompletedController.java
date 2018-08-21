package org.scify.jedai.gui.controllers.steps;

import com.google.inject.Inject;
import com.google.inject.Injector;
import eu.hansolo.medusa.Gauge;
import eu.hansolo.medusa.Gauge.SkinType;
import eu.hansolo.medusa.GaugeBuilder;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.util.StringConverter;
import org.scify.jedai.blockbuilding.IBlockBuilding;
import org.scify.jedai.blockprocessing.IBlockProcessing;
import org.scify.jedai.datamodel.AbstractBlock;
import org.scify.jedai.datamodel.EntityProfile;
import org.scify.jedai.datamodel.EquivalenceCluster;
import org.scify.jedai.datamodel.SimilarityPairs;
import org.scify.jedai.entityclustering.IEntityClustering;
import org.scify.jedai.entitymatching.IEntityMatching;
import org.scify.jedai.gui.controllers.EntityClusterExplorationController;
import org.scify.jedai.gui.model.BlClMethodConfiguration;
import org.scify.jedai.gui.model.WorkflowResult;
import org.scify.jedai.gui.nodes.DetailsCell;
import org.scify.jedai.gui.utilities.*;
import org.scify.jedai.gui.utilities.console_area.ConsoleArea;
import org.scify.jedai.gui.utilities.console_area.MultiOutputStream;
import org.scify.jedai.gui.wizard.MethodMapping;
import org.scify.jedai.gui.wizard.WizardData;
import org.scify.jedai.utilities.BlocksPerformance;
import org.scify.jedai.utilities.ClustersPerformance;
import org.scify.jedai.utilities.PrintToFile;
import org.scify.jedai.utilities.datastructures.AbstractDuplicatePropagation;
import org.scify.jedai.utilities.enumerations.BlockBuildingMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.text.NumberFormat;
import java.util.*;

public class CompletedController {
    public Button runBtn;
    public Button exportBtn;
    public VBox containerVBox;
    public Label numOfInstancesLabel;
    public Label numOfClustersLabel;
    public HBox gaugesHBox;
    public ProgressIndicator progressIndicator;
    public TextArea logTextArea;
    public Label totalTimeLabel;
    public TabPane resultsTabPane;
    public TableView workbenchTable;
    public Button exploreBtn;
    public VBox autoConfigContainer;

    private SingleSelectionModel<Tab> tabSelectionModel;
    private final ObservableList<WorkflowResult> tableData = FXCollections.observableArrayList();

    private Gauge f1Gauge;
    private Gauge recallGauge;
    private Gauge precisionGauge;
    private Logger log = LoggerFactory.getLogger(CompletedController.class);

    private List<WizardData> detailedRunData;
    private EquivalenceCluster[] entityClusters;
    private List<EntityProfile> profilesD1;
    private List<EntityProfile> profilesD2;

    @Inject
    private Injector injector;

    @Inject
    private WizardData model;

    @FXML
    public void initialize() {
        // Initialize list of detailed run data
        detailedRunData = new ArrayList<>();

        // Create gauges
        recallGauge = newGauge("Recall");
        gaugesHBox.getChildren().add(recallGauge);

        precisionGauge = newGauge("Precision");
        gaugesHBox.getChildren().add(precisionGauge);

        f1Gauge = newGauge("F1-measure");
        gaugesHBox.getChildren().add(f1Gauge);

        // Setup text area as log
        ConsoleArea ca = new ConsoleArea(logTextArea);
        PrintStream ps = new PrintStream(ca, true);

        MultiOutputStream multiOut = new MultiOutputStream(System.out, ps);
        MultiOutputStream multiErr = new MultiOutputStream(System.err, ps);

        PrintStream out = new PrintStream(multiOut);
        PrintStream err = new PrintStream(multiErr);

        System.setOut(out);
        System.setErr(err);

        // Save the TabPane's selection model to use it for resetting the selected tab later
        tabSelectionModel = resultsTabPane.getSelectionModel();

        // Add radio buttons for configuring automatic configuration options
        Label l = new Label("Automatic Configuration Type");
        l.setFont(Font.font("System", FontWeight.BOLD, 12));
        autoConfigContainer.getChildren().add(l);
        List<String> autoConfigTypes = Arrays.asList(
                JedaiOptions.AUTOCONFIG_HOLISTIC,
                JedaiOptions.AUTOCONFIG_STEPBYSTEP
        );
        RadioButtonHelper.createButtonGroup(autoConfigContainer, autoConfigTypes, model.autoConfigTypeProperty());

        autoConfigContainer.getChildren().add(new Separator());
        l = new Label("Search Type");
        l.setFont(Font.font("System", FontWeight.BOLD, 12));
        autoConfigContainer.getChildren().add(l);
        List<String> searchTypes = Arrays.asList(
                JedaiOptions.AUTOCONFIG_RANDOMSEARCH,
                JedaiOptions.AUTOCONFIG_GRIDSEARCH
        );
        RadioButtonHelper.createButtonGroup(autoConfigContainer, searchTypes, model.searchTypeProperty());

        // Setup table for previous results (Workbench)
        initGrid();
    }

    /**
     * Initialize the workbench grid which shows the results of previous JedAI runs
     */
    private void initGrid() {
        // Set grid properties
        workbenchTable.setEditable(false);
        workbenchTable.setItems(tableData);

        // Specify columns for grid
        Map<String, String> tableCols = new LinkedHashMap<>();
        tableCols.put("Run #", "runNumber");
        tableCols.put("Recall", "recall");
        tableCols.put("Precision", "precision");
        tableCols.put("F1-measure", "f1Measure");
        tableCols.put("Total time (sec.)", "totalTime");
        tableCols.put("Input instances", "inputInstances");
        tableCols.put("Clusters #", "numOfClusters");

        List<String> colsToFormat = Arrays.asList("Recall", "Precision", "F1-measure");

        int colsNum = tableCols.size() + 1; // Add 1 because we add the details column later

        // Create column objects
        for (String colName : tableCols.keySet()) {
            TableColumn col = new TableColumn(colName);
            col.setCellValueFactory(new PropertyValueFactory<WorkflowResult, String>(tableCols.get(colName)));

            // Set the width to be the same for all columns (subtract not needed but prevents horizontal scrollbar...)
            col.prefWidthProperty().bind(workbenchTable.widthProperty().multiply(1.0 / colsNum).subtract(1));

            if (colsToFormat.contains(colName)) {
                // Add number formatter which shows maximum 3 fraction digits
                col.setCellFactory(TextFieldTableCell.forTableColumn(new StringConverter<Double>() {
                    private final NumberFormat nf = NumberFormat.getNumberInstance();

                    {
                        nf.setMaximumFractionDigits(3);
                        nf.setMinimumFractionDigits(1);
                    }

                    @Override
                    public String toString(Double object) {
                        return nf.format(object);
                    }

                    @Override
                    public Double fromString(String string) {
                        // Not needed because we don't edit the table
                        return null;
                    }
                }));
            }

            // Add column to the table
            workbenchTable.getColumns().add(col);
        }

        // Add details button column
        TableColumn detailsBtnCol = new TableColumn("Details");
        detailsBtnCol.setCellFactory(param -> new DetailsCell(this.detailedRunData, this.injector));
        workbenchTable.getColumns().add(detailsBtnCol);
    }

    /**
     * Generate a new Medusa Gauge for showing a clustering accuracy metric
     *
     * @param title Title of gauge
     * @return Gauge
     */
    private Gauge newGauge(String title) {
        return GaugeBuilder.create()
                .skinType(SkinType.HORIZONTAL)
                .minValue(0.0)
                .maxValue(1.0)
                .tickLabelDecimals(1)
                .title(title)
                .decimals(3)
                .animated(true)
                .animationDuration(1500)
                .build();
    }

    /**
     * Update the progress of the progressIndicator in the UI thread
     *
     * @param percentage Percentage to set indicator to (in range [0, 1])
     */
    private void updateProgress(double percentage) {
        Platform.runLater(() -> progressIndicator.setProgress(percentage));
    }

    @FXML
    private void runAlgorithmBtnHandler() {
        // Show the progress indicator
        progressIndicator.setVisible(true);
        progressIndicator.setProgress(0.0);

        // Reset console area
        logTextArea.clear();

        // Runnable that will run algorithm in separate thread
        new Thread(() -> {
            // Disable the step control buttons
            model.setWorkflowRunning(true);

            // Disable exploration button
            exploreBtn.setDisable(true);

            // Create performance variables
            long startTime = System.currentTimeMillis();
            double overheadStart;
            double overheadEnd;
            BlocksPerformance blp;

            try {
                // Get profiles and ground truth paths from model
                String erType = model.getErType();

                // Step 1: Data reading
                profilesD1 = DataReader.getEntitiesD1(model);
                System.out.println("Input Entity Profiles\t:\t" + profilesD1.size());

                // In case Clean-Clear ER was selected, also read 2nd profiles file
                profilesD2 = null;
                if (erType.equals(JedaiOptions.CLEAN_CLEAN_ER)) {
                    profilesD2 = DataReader.getEntitiesD2(model);
                }

                // Read ground truth file
                AbstractDuplicatePropagation duplicatePropagation =
                        DataReader.getGroundTruth(model, profilesD1, profilesD2);

                System.out.println("Existing Duplicates\t:\t" + duplicatePropagation.getDuplicates().size());

                // Set progress indicator to 20%
                updateProgress(0.2);

                // Step 2: Block Building
                IBlockBuilding blockBuildingMethod;

                BlockBuildingMethod blockingWorkflow = MethodMapping.blockBuildingMethods.get(model.getBlockBuilding());
                overheadStart = System.currentTimeMillis();

                // Check if the user set any custom parameters for block building
                if (!model.getBlockBuildingConfigType().equals(JedaiOptions.MANUAL_CONFIG)) {
                    // Auto or default configuration selected: use default configuration
                    blockBuildingMethod = BlockBuildingMethod.getDefaultConfiguration(blockingWorkflow);
                } else {
                    // Manual configuration selected, create method with the saved parameters
                    ObservableList<JPair<String, Object>> blBuParams = model.getBlockBuildingParameters();
                    blockBuildingMethod = DynamicMethodConfiguration.configureBlockBuildingMethod(blockingWorkflow, blBuParams);
                }

                List<AbstractBlock> blocks;
                if (blockBuildingMethod != null) {
                    if (erType.equals(JedaiOptions.DIRTY_ER)) {
                        blocks = blockBuildingMethod.getBlocks(profilesD1);
                    } else {
                        blocks = blockBuildingMethod.getBlocks(profilesD1, profilesD2);
                    }
                } else {
                    // Show error
                    DialogHelper.showError("Block Building Method Error", "Block Building Method is null!",
                            "There was a problem running the selected block building method!");
                    return;
                }

                System.out.println("Original blocks\t:\t" + blocks.size());

                // Print blocks performance
                overheadEnd = System.currentTimeMillis();
                blp = new BlocksPerformance(blocks, duplicatePropagation);
                blp.setStatistics();
                blp.printStatistics(overheadEnd - overheadStart, blockBuildingMethod.getMethodConfiguration(), blockBuildingMethod.getMethodName());

                // Set progress indicator to 40%
                updateProgress(0.4);

                // Step 3: Block Cleaning
                List<BlClMethodConfiguration> blClMethods = model.getBlockCleaningMethods();

                if (blClMethods != null) {
                    // Execute the methods
                    for (BlClMethodConfiguration currentMethod : blClMethods) {
                        // Only run the method if it is enabled
                        if (!currentMethod.isEnabled()) {
                            continue;
                        }

                        overheadStart = System.currentTimeMillis();

                        // Process blocks with this method
                        IBlockProcessing blockCleaningMethod;
                        if (!currentMethod.getConfigurationType().equals(JedaiOptions.MANUAL_CONFIG)) {
                            // Auto or default configuration selected: use default configuration
                            blockCleaningMethod = MethodMapping.getMethodByName(currentMethod.getName());
                        } else {
                            // Manual configuration selected, create method with the saved parameters
                            blockCleaningMethod = DynamicMethodConfiguration.configureBlockCleaningMethod(
                                    currentMethod.getName(), currentMethod.getManualParameters());
                        }

                        if (blockCleaningMethod != null) {
                            blocks = blockCleaningMethod.refineBlocks(blocks);

                            // Print blocks performance
                            overheadEnd = System.currentTimeMillis();
                            blp = new BlocksPerformance(blocks, duplicatePropagation);
                            blp.setStatistics();
                            blp.printStatistics(overheadEnd - overheadStart, blockCleaningMethod.getMethodConfiguration(), blockCleaningMethod.getMethodName());
                        }
                    }
                }

                // Step 4: Comparison Cleaning
                String coClMethod = model.getComparisonCleaning();
                if (coClMethod != null && !coClMethod.equals(JedaiOptions.NO_CLEANING)) {
                    overheadStart = System.currentTimeMillis();

                    // Create comparison cleaning method
                    IBlockProcessing comparisonCleaningMethod;

                    if (!model.getComparisonCleaningConfigType().equals(JedaiOptions.MANUAL_CONFIG)) {
                        // Auto or default configuration selected: use default configuration
                        comparisonCleaningMethod = MethodMapping.getMethodByName(coClMethod);
                    } else {
                        // Manual configuration selected, create method with the saved parameters
                        ObservableList<JPair<String, Object>> coClParams = model.getComparisonCleaningParameters();
                        comparisonCleaningMethod = DynamicMethodConfiguration.configureComparisonCleaningMethod(coClMethod, coClParams);
                    }

                    blocks = comparisonCleaningMethod.refineBlocks(blocks);

                    // Print blocks performance
                    overheadEnd = System.currentTimeMillis();
                    blp = new BlocksPerformance(blocks, duplicatePropagation);
                    blp.setStatistics();
                    blp.printStatistics(overheadEnd - overheadStart, comparisonCleaningMethod.getMethodConfiguration(), comparisonCleaningMethod.getMethodName());
                }

                // Set progress indicator to 60%
                updateProgress(0.6);

                // Step 5: Entity Matching
                String entityMatchingMethod = model.getEntityMatching();

                IEntityMatching em;
                if (!model.getEntityMatchingConfigType().equals(JedaiOptions.MANUAL_CONFIG)) {
                    // Default or automatic config, use default values
                    em = DynamicMethodConfiguration.configureEntityMatchingMethod(entityMatchingMethod, null);
                } else {
                    // Manual configuration, use given parameters
                    ObservableList<JPair<String, Object>> emParams = model.getEntityMatchingParameters();
                    em = DynamicMethodConfiguration.configureEntityMatchingMethod(entityMatchingMethod, emParams);
                }
                SimilarityPairs simPairs;

                if (em == null)
                    throw new Exception("Entity Matching method is null!");

                if (erType.equals(JedaiOptions.DIRTY_ER)) {
                    simPairs = em.executeComparisons(blocks, profilesD1);
                } else {
                    simPairs = em.executeComparisons(blocks, profilesD1, profilesD2);
                }

                // Set progress indicator to 80%
                updateProgress(0.8);

                // Step 6: Entity Clustering
                String entityClusteringMethod = model.getEntityClustering();
                overheadStart = System.currentTimeMillis();

                // Create entity clustering method
                IEntityClustering ec;

                if (!model.getEntityClusteringConfigType().equals(JedaiOptions.MANUAL_CONFIG)) {
                    // Auto or default configuration selected: use default configuration
                    ec = MethodMapping.getEntityClusteringMethod(entityClusteringMethod);
                } else {
                    // Manual configuration selected, create method with the saved parameters
                    ObservableList<JPair<String, Object>> ecParams = model.getEntityClusteringParameters();
                    ec = DynamicMethodConfiguration.configureEntityClusteringMethod(entityClusteringMethod, ecParams);
                }

                entityClusters = ec.getDuplicates(simPairs);

                // Print clustering performance
                overheadEnd = System.currentTimeMillis();
                ClustersPerformance clp = new ClustersPerformance(entityClusters, duplicatePropagation);
                clp.setStatistics();
                clp.printStatistics(overheadEnd - overheadStart, ec.getMethodName(), ec.getMethodConfiguration());

                // Set gauge values
                f1Gauge.setValue(clp.getFMeasure());
                recallGauge.setValue(clp.getRecall());
                precisionGauge.setValue(clp.getPrecision());

                // Set progress indicator to 100%
                updateProgress(1.0);

                // Get final run values
                double totalTimeSeconds = (System.currentTimeMillis() - startTime) / 1000.0;
                int inputInstances = profilesD1.size();
                int numOfClusters = entityClusters.length;
                double recall = clp.getRecall();
                double precision = clp.getPrecision();
                double f1 = clp.getFMeasure();

                // Create entry for Workbench table
                tableData.add(new WorkflowResult(
                        new SimpleIntegerProperty(tableData.size() + 1),
                        new SimpleDoubleProperty(recall),
                        new SimpleDoubleProperty(precision),
                        new SimpleDoubleProperty(f1),
                        new SimpleDoubleProperty(totalTimeSeconds),
                        new SimpleIntegerProperty(inputInstances),
                        new SimpleIntegerProperty(numOfClusters),
                        new SimpleIntegerProperty(tableData.size())
                ));

                // Add a copy of current WizardData to the list
                detailedRunData.add(WizardData.cloneData(model));

                // Update labels and JavaFX UI components from UI thread
                Platform.runLater(() -> {
                    // Set label values and show them
                    numOfInstancesLabel.setText("Input instances: " + inputInstances);
                    numOfInstancesLabel.setVisible(true);

                    totalTimeLabel.setText("Total running time: " + String.format("%.1f", totalTimeSeconds) + " sec.");
                    totalTimeLabel.setVisible(true);

                    numOfClustersLabel.setText("Number of clusters: " + numOfClusters);
                    numOfClustersLabel.setVisible(true);

                    // Enable button for result export to CSV
                    exportBtn.setDisable(false);

                    // Enable exploration button
                    exploreBtn.setDisable(false);
                });
            } catch (Exception e) {
                // Exception occurred, show alert with information about it
                Platform.runLater(() -> DialogHelper.showError("Exception",
                        "An exception occurred while running the workflow!",
                        "Details: " + e.toString() + " (" + e.getMessage() + ")"));

                // Print stack trace
                e.printStackTrace();

                // Set workflowRunning boolean to false
                model.setWorkflowRunning(false);
            }

            // Workflow ran, set workflowRunning boolean to false
            model.setWorkflowRunning(false);
        }).start();
    }

    /**
     * Ask the user for a filename with a save file dialog, and save a CSV with the entity clusters
     */
    public void exportBtnHandler() {
        FileChooser fileChooser = new FileChooser();

        // Set extension filter
        FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("CSV File", "*.csv");
        fileChooser.getExtensionFilters().add(extFilter);

        // Show save file dialog
        File file = fileChooser.showSaveDialog(containerVBox.getScene().getWindow());

        if (file != null) {
            // Results export to CSV
            try {
                PrintToFile.toCSV(profilesD1, profilesD2, entityClusters, file.getAbsolutePath());
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Reset the view of the step
     */
    public void resetData() {
        // Disable exploration button
        exploreBtn.setDisable(true);

        // Hide time measurements
        numOfInstancesLabel.setVisible(false);
        totalTimeLabel.setVisible(false);
        numOfClustersLabel.setVisible(false);

        // Make export button disabled
        exportBtn.setDisable(true);

        // Reset gauges
        f1Gauge.setValue(0.0);
        recallGauge.setValue(0.0);
        precisionGauge.setValue(0.0);

        // Reset Details tab
        logTextArea.setText("");

        // Reset progress indicator
        progressIndicator.setVisible(false);
        progressIndicator.setProgress(0.0);

        // Go to first tabset tab
        tabSelectionModel.selectFirst();
    }

    /**
     * Explore the results of the dataset. Assumes that it will not be called when this is not possible (because the
     * button is supposed to be disabled when that's the case...)
     *
     * @param actionEvent Button event
     */
    public void exploreResults(ActionEvent actionEvent) {
        // Get LIST of equivalence clusters (from array)
        List<EquivalenceCluster> duplicates = Arrays.asList(this.entityClusters);

        // Load FXML for exploration window and get the controller
        Parent root = DialogHelper.loadFxml(this.getClass(), injector,
                "wizard-fxml/EntityClusterExploration.fxml");
        Object controller = null;
        if (root != null) {
            controller = root.getProperties().get("controller");
        }

        // Set properties of the controller & show window
        if (controller instanceof EntityClusterExplorationController) {
            // Cast the controller instance since we know it's safe here
            EntityClusterExplorationController popupController = (EntityClusterExplorationController) controller;
            popupController.setTitle("Results Exploration");

            // Give the configuration options to the controller
            if (model.getErType().equals(JedaiOptions.DIRTY_ER)) {
                popupController.setDuplicates(duplicates, this.profilesD1);
            } else {
                popupController.setDuplicates(duplicates, this.profilesD1, this.profilesD2);
            }

            // Create the popup
            DialogHelper.showScene(root, Modality.WINDOW_MODAL, false,
                    "JedAI - Results Exploration");
        } else {
            // This shouldn't ever happen.
            System.err.println("Error when showing the results exploration popup (Wrong controller instance?)");
        }
    }
}