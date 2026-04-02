package hf.automation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Main {

    private static final String ORG_NAME = "InfoBayAI";
    private static final String SHEET_ID = "1FrDhCNi0A7xiZmCXf4-PGa56ttp-15pNOKk6pEkvUZc";
    private static final String SHEET_NAME = "Sheet1";
    private static final String DOWNLOAD_PATH = "C:\\Users\\EduGorilla\\Downloads\\HF";
    private static final String CREDENTIALS_PATH = "credentials.json";
    private static final long LOGIN_WAIT_TIMEOUT_MS = 60000;
    private static final long DOWNLOAD_WAIT_TIMEOUT_MS = 30000;

    public static void main(String[] args) throws Exception {
        HuggingFaceAccessCollector collector = new HuggingFaceAccessCollector(
                ORG_NAME,
                DOWNLOAD_PATH,
                LOGIN_WAIT_TIMEOUT_MS,
                DOWNLOAD_WAIT_TIMEOUT_MS
        );

        List<DatasetAccessRecord> acceptedAccessRecords = collector.collectAcceptedAccessRecords();
        List<List<Object>> sheetRows = buildSheetRows(acceptedAccessRecords);

        GoogleSheetsWriter sheetsWriter = new GoogleSheetsWriter(
                SHEET_ID,
                SHEET_NAME,
                CREDENTIALS_PATH
        );

        sheetsWriter.overwriteSheet(sheetRows);

        System.out.println("Accepted access report rebuilt successfully.");
        System.out.println("Datasets processed: " + countDistinctDatasets(acceptedAccessRecords));
        System.out.println("Unique users written: " + Math.max(0, sheetRows.size() - 1));
    }

    private static List<List<Object>> buildSheetRows(List<DatasetAccessRecord> records) {
        Map<String, UserDatasetSummary> usersByEmail = new LinkedHashMap<>();

        for (DatasetAccessRecord record : records) {
            if (record.email().isBlank()) {
                continue;
            }

            UserDatasetSummary summary = usersByEmail.computeIfAbsent(
                    record.email().toLowerCase(),
                    email -> new UserDatasetSummary(record.email())
            );

            summary.captureName(record.fullName());
            summary.addDataset(record.datasetName());
        }

        List<UserDatasetSummary> summaries = new ArrayList<>(usersByEmail.values());
        summaries.sort(
                Comparator.comparing(UserDatasetSummary::sortableName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(UserDatasetSummary::email, String.CASE_INSENSITIVE_ORDER)
        );

        int maxDatasets = summaries.stream()
                .mapToInt(UserDatasetSummary::datasetCount)
                .max()
                .orElse(0);

        List<List<Object>> rows = new ArrayList<>();
        rows.add(buildHeaderRow(maxDatasets));

        int serialNumber = 1;
        for (UserDatasetSummary summary : summaries) {
            List<Object> row = new ArrayList<>();
            row.add(serialNumber++);
            row.add(summary.displayName());
            row.add(summary.email());
            row.addAll(summary.datasetColumns());

            while (row.size() < 3 + maxDatasets) {
                row.add("");
            }

            rows.add(row);
        }

        return rows;
    }

    private static List<Object> buildHeaderRow(int maxDatasets) {
        List<Object> header = new ArrayList<>();
        header.add("S No.");
        header.add("Name");
        header.add("Email");

        for (int index = 1; index <= maxDatasets; index++) {
            header.add("Dataset " + index);
        }

        return header;
    }

    private static int countDistinctDatasets(List<DatasetAccessRecord> records) {
        Set<String> distinctDatasets = new LinkedHashSet<>();
        for (DatasetAccessRecord record : records) {
            distinctDatasets.add(record.datasetName());
        }
        return distinctDatasets.size();
    }
}
