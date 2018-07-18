package org.scify.jedai.gui.wizard;

import javafx.fxml.FXML;
import javafx.scene.control.Pagination;
import javafx.scene.layout.VBox;
import org.scify.jedai.datamodel.EntityProfile;
import org.scify.jedai.gui.utilities.DataReadingHelper;
import org.scify.jedai.gui.utilities.EntityProfileNode;
import org.scify.jedai.gui.utilities.JPair;

import java.util.List;

public class DatasetExplorationController {
    private final int pageSize = 10;

    public Pagination entityPagination;
    private String datasetType = null;
    private List<JPair<String, Object>> datasetParams = null;

    @FXML
    public void initialize() {
    }

    /**
     * Show the entities in the window.
     */
    private void updateView() {
        // Read dataset
        List<EntityProfile> entities = DataReadingHelper.getEntities(this.datasetType, this.datasetParams);

        // Find number of pages we need to show 10 entities per page
        int pagesNum = 0;
        if (entities != null) {
            pagesNum = entities.size() / pageSize;

            // Add last page if there are remaining items
            if (entities.size() % pageSize > 0) {
                pagesNum++;
            }
        }

        // Setup pagination
        entityPagination.setPageCount(pagesNum);
        entityPagination.setPageFactory(pageIndex -> {
            // Create node that we will add entities to
            VBox vBox = new VBox();

            // Get the entities to show
            int firstEntity = pageIndex * pageSize;
            assert entities != null;
            int lastEntity = Math.min((pageIndex + 1) * pageSize, entities.size());
            List<EntityProfile> pageEntities = entities.subList(firstEntity, lastEntity);

            // Generate an entity profile node for each entity
            for (EntityProfile ep : pageEntities) {
                vBox.getChildren().add(new EntityProfileNode(ep));
            }

            // Return generated node for this page
            return vBox;
        });
    }

    public void setDatasetType(String datasetType) {
        this.datasetType = datasetType;

        // Check if we can show the entities
        if (this.datasetType != null && this.datasetParams != null) {
            updateView();
        }
    }

    public void setDatasetParams(List<JPair<String, Object>> datasetParams) {
        this.datasetParams = datasetParams;

        // Check if we can show the entities
        if (this.datasetType != null && this.datasetParams != null) {
            updateView();
        }
    }
}
