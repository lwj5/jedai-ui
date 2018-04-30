package org.scify.jedai.gui.wizard.steps;

import com.google.inject.Inject;
import javafx.beans.Observable;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.RowConstraints;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.scify.jedai.gui.utilities.BlClMethodConfiguration;
import org.scify.jedai.gui.utilities.JedaiOptions;
import org.scify.jedai.gui.utilities.RowHidingChangeListener;
import org.scify.jedai.gui.utilities.dynamic_configuration.MethodConfiguration;
import org.scify.jedai.gui.wizard.Submit;
import org.scify.jedai.gui.wizard.WizardData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class ConfirmController {
    public GridPane paramsGrid;
    public Label titleLabel;
    private Logger log = LoggerFactory.getLogger(ConfirmController.class);
    private RowHidingChangeListener changeListener;

    // Vertical gap between rows (actual gap is double this number)
    private final int vGap = 5;

    @Inject
    private WizardData model;

    /**
     * Create a bold label with a specific text
     *
     * @param text Text to use for value
     * @return Label with bold text
     */
    private Label boldLabel(String text) {
        // Create label with the given text and set its font to bold
        Label l = new Label(text);
        l.setFont(Font.font("System", FontWeight.BOLD, 12));

        return l;
    }

    /**
     * Create a Label whose value is bound to an observable value.
     *
     * @param observable Value to bind to label text
     * @return Label with bound value
     */
    private Label boundLabel(ObservableValue<? extends String> observable) {
        // Create label
        Label l = new Label();

        // Bind its value to the observable
        l.textProperty().bind(observable);

        return l;
    }

    /**
     * Add a new row to the grid, with a label and value. For each new row, an empty RowConstaints object is created.
     *
     * @param rowIndex Index to add row
     * @param label    Label node of row
     * @param value    Value node of row
     * @return RowConstraints object for the newly added row
     */
    private RowConstraints addRow(int rowIndex, Label label, Node value) {
        // Set padding on the label
        label.setPadding(new Insets(vGap, 0, vGap, 0));

        // Add the new row
        paramsGrid.addRow(rowIndex, label, value);

        // Create the row constraints for it
        RowConstraints rowConstraints = new RowConstraints();
        paramsGrid.getRowConstraints().add(rowConstraints);

        return rowConstraints;
    }

    @FXML
    public void initialize() {
        int rows = 0;

        // ER type
        addRow(rows++, boldLabel("ER Type"), boundLabel(model.erTypeProperty()));

        // Dataset 1 type
        addRow(rows++, boldLabel("Dataset 1 Type"), boundLabel(model.entityProfilesD1TypeProperty()));

        // Dataset 1 parameters
        addRow(rows++, boldLabel("Dataset 1 Reader Parameters"),
                MethodConfiguration.newParamsNode(model.entityProfilesD1ParametersProperty()));

        // Dataset 2 type & parameters (only shown for Clean-Clean ER)
        Label d2TypeTitle = boldLabel("Dataset 2 Type");
        Label d2TypeValue = boundLabel(model.entityProfilesD2TypeProperty());
        Label d2ParamsTitle = boldLabel("Dataset 2 Reader Parameters");
        Node d2ParamsValue = MethodConfiguration.newParamsNode(model.entityProfilesD2ParametersProperty());

        // Add the new nodes to their rows, and keep the row constraints objects
        RowConstraints d2TypeConstraints = addRow(rows++, d2TypeTitle, d2TypeValue);
        RowConstraints d2ParamsConstraints = addRow(rows++, d2ParamsTitle, d2ParamsValue);

        // Hide the nodes of the two rows when ER type is not Clean-Clean
        d2TypeTitle.visibleProperty().bind(model.erTypeProperty().isEqualTo(JedaiOptions.CLEAN_CLEAN_ER));
        d2TypeValue.visibleProperty().bind(model.erTypeProperty().isEqualTo(JedaiOptions.CLEAN_CLEAN_ER));
        d2ParamsTitle.visibleProperty().bind(model.erTypeProperty().isEqualTo(JedaiOptions.CLEAN_CLEAN_ER));
        d2ParamsValue.visibleProperty().bind(model.erTypeProperty().isEqualTo(JedaiOptions.CLEAN_CLEAN_ER));

        // When ER type is Dirty, set the row heights to 0 to hide them
        changeListener = new RowHidingChangeListener(Arrays.asList(
                d2TypeConstraints,
                d2ParamsConstraints
        ));
        model.erTypeProperty().addListener(changeListener);

        // Run the changed() function once manually, to set the initial values correctly
        changeListener.changed(model.erTypeProperty(), null, model.getErType());

        // Ground truth type
        addRow(rows++, boldLabel("Ground Truth Type"), boundLabel(model.groundTruthTypeProperty()));

        // Ground Truth parameters
        addRow(rows++, boldLabel("Ground Truth Reader Parameters"),
                MethodConfiguration.newParamsNode(model.groundTruthParametersProperty()));

        // Block Building method
        addRow(rows++, boldLabel("Block Building Method"), boundLabel(model.blockBuildingProperty()));

        // Block Building parameters
        addRow(rows++, boldLabel("Block Building Parameters"),
                MethodConfiguration.newParamsNode(model.blockBuildingParametersProperty()));

        // Block Cleaning methods (sorted automatically)
        ListView<BlClMethodConfiguration> blockCleaningList = new ListView<>();
        blockCleaningList.setCellFactory(param -> new ListCell<BlClMethodConfiguration>() {
            @Override
            protected void updateItem(BlClMethodConfiguration item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getName() + ": " + item.isEnabled());
                }
            }
        });

        //todo: could this be done in the modeL?
        ObservableList<BlClMethodConfiguration> obsList = FXCollections.observableArrayList(
                param -> new Observable[]{
                        param.enabledProperty(),
                        param.manualParametersProperty(),
                        param.configurationTypeProperty()
                }
        );

        obsList.addAll(model.getBlockCleaningMethods());

//        blockCleaningList.setItems(model.getBlockCleaningMethods());
        blockCleaningList.setItems(obsList);
        blockCleaningList.setMaxHeight(80);
//        blockCleaningList.itemsProperty().bind(
//                model.blockCleaningMethodsProperty()
//                        .filtered(BlClMethodConfiguration::isEnabled)
//                        .sorted(new BlockCleaningObjectComparator())
//        );

        addRow(rows++, boldLabel("Block Cleaning Methods"), blockCleaningList);

        // Comparison Cleaning method
        addRow(rows++, boldLabel("Comparison Cleaning Method"), boundLabel(model.comparisonCleaningProperty()));

        // Comparison Cleaning configuration type
        addRow(rows++, boldLabel("Comparison Cleaning Configuration"),
                boundLabel(model.comparisonCleaningConfigTypeProperty()));

        // Comparison Cleaning parameters
        addRow(rows++, boldLabel("Comparison Cleaning Parameters"),
                MethodConfiguration.newParamsNode(model.comparisonCleaningParametersProperty()));

        // Entity Matching method
        addRow(rows++, boldLabel("Entity Matching Method"), boundLabel(model.entityMatchingProperty()));

        // Entity Matching configuration type
        addRow(rows++, boldLabel("Entity Matching Configuration"),
                boundLabel(model.entityMatchingConfigTypeProperty()));

        // Entity Matching parameters
        addRow(rows++, boldLabel("Entity Matching Parameters"),
                MethodConfiguration.newParamsNode(model.entityMatchingParametersProperty()));

        // Entity Clustering algorithm
        addRow(rows++, boldLabel("Entity Clustering Algorithm"), boundLabel(model.entityClusteringProperty()));

        // Entity Clustering configuration type
        addRow(rows++, boldLabel("Entity Clustering Configuration"),
                boundLabel(model.entityClusteringConfigTypeProperty()));

        // Entity Clustering parameters
        addRow(rows, boldLabel("Entity Clustering Parameters"),
                MethodConfiguration.newParamsNode(model.entityClusteringParametersProperty()));
    }

    /**
     * Set a new model, and rerun the initialization. Useful when showing a detailed configuration in a popup.
     *
     * @param newModel New model to show
     * @param newTitle New title to show in the window. If null, the title won't be changed.
     */
    public void setModel(WizardData newModel, String newTitle) {
        // Remove change listener from old model
        model.erTypeProperty().removeListener(changeListener);

        // Set the new model
        this.model = newModel;

        // Rerun the initialization to bind UI items to the new model
        paramsGrid.getChildren().clear();
        initialize();

        // If there is a new title, set it
        if (newTitle != null) {
            titleLabel.setText(newTitle);
        }
    }

    @Submit
    public void submit() {
        if (log.isDebugEnabled()) {
            log.debug("[SUBMIT] Confirmation step completed");
        }
    }
}
