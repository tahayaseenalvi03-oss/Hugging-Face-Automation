package hf.automation;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public class UserDatasetSummary {

    private final String email;
    private String displayName = "";
    private final TreeSet<String> datasets = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    public UserDatasetSummary(String email) {
        this.email = email;
    }

    public void captureName(String candidateName) {
        if (displayName.isBlank() && candidateName != null && !candidateName.isBlank()) {
            displayName = candidateName.trim();
        }
    }

    public void addDataset(String datasetName) {
        if (datasetName != null && !datasetName.isBlank()) {
            datasets.add(datasetName.trim());
        }
    }

    public String sortableName() {
        return displayName.isBlank() ? email : displayName;
    }

    public String displayName() {
        return displayName;
    }

    public String email() {
        return email;
    }

    public int datasetCount() {
        return datasets.size();
    }

    public List<Object> datasetColumns() {
        return new ArrayList<>(datasets);
    }
}
