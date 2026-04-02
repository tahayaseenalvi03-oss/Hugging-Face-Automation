package hf.automation;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ClearValuesRequest;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.FileInputStream;
import java.util.List;

public class GoogleSheetsWriter {

    private final String sheetId;
    private final String sheetName;
    private final String credentialsPath;

    public GoogleSheetsWriter(String sheetId, String sheetName, String credentialsPath) {
        this.sheetId = sheetId;
        this.sheetName = sheetName;
        this.credentialsPath = credentialsPath;
    }

    public void overwriteSheet(List<List<Object>> rows) throws Exception {
        Sheets service = buildService();
        verifySheetAccess(service);
        clearSheet(service);
        writeRows(service, rows);
    }

    private Sheets buildService() throws Exception {
        GoogleCredentials credentials = GoogleCredentials
                .fromStream(new FileInputStream(credentialsPath))
                .createScoped(List.of("https://www.googleapis.com/auth/spreadsheets"));

        return new Sheets.Builder(
                new com.google.api.client.http.javanet.NetHttpTransport(),
                new com.google.api.client.json.gson.GsonFactory(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("HF Extractor")
                .build();
    }

    private void verifySheetAccess(Sheets service) throws Exception {
        try {
            Spreadsheet spreadsheet = service.spreadsheets().get(sheetId).execute();
            System.out.println("Connected to spreadsheet: " + spreadsheet.getProperties().getTitle());
        } catch (GoogleJsonResponseException e) {
            throw buildGoogleSheetsException("Unable to open the spreadsheet", e);
        }
    }

    private void clearSheet(Sheets service) throws Exception {
        try {
            service.spreadsheets().values()
                    .clear(sheetId, sheetName, new ClearValuesRequest())
                    .execute();
        } catch (GoogleJsonResponseException e) {
            throw buildGoogleSheetsException("Unable to clear existing sheet data", e);
        }
    }

    private void writeRows(Sheets service, List<List<Object>> rows) throws Exception {
        ValueRange body = new ValueRange().setValues(rows);

        try {
            service.spreadsheets().values()
                    .update(sheetId, sheetName + "!A1", body)
                    .setValueInputOption("RAW")
                    .execute();
        } catch (GoogleJsonResponseException e) {
            throw buildGoogleSheetsException("Unable to write rows to Google Sheets", e);
        }
    }

    private RuntimeException buildGoogleSheetsException(String action, GoogleJsonResponseException e) {
        int statusCode = e.getStatusCode();
        String details = e.getDetails() != null ? e.getDetails().getMessage() : e.getMessage();

        StringBuilder message = new StringBuilder(action)
                .append(". Google API returned ")
                .append(statusCode)
                .append(". ")
                .append(details);

        if (statusCode == 404) {
            message.append(" Check that spreadsheet ID and sheet name ").append(sheetName).append(" are correct.");
        }

        return new RuntimeException(message.toString(), e);
    }
}
