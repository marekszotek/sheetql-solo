package com.codecool.solosheetql.tables;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GoogleSheetsLoader implements TableLoader {
    private static final String APPLICATION_NAME = "SheetQL";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS_READONLY);
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";
    private static final String SERVICE_ACCOUNT_FILE_PATH = "/sheetql-29dea33204e9.json";
    private final String worksheetName = "Class Data";
    private TablesRepository tablesRepository;

    @Autowired
    GoogleSheetsLoader(TablesRepository tablesRepository) {
        this.tablesRepository = tablesRepository;
    }

    public String loadTableContent(String spreadsheetName) throws TableNotFoundException{
        try {
            return String.join("\n", getSpreadsheetContent(spreadsheetName)).replaceAll("\\[|]", "");
        } catch (IOException | GeneralSecurityException e) {
            throw new TableNotFoundException("Cannot load google sheet by table name = " + spreadsheetName);
        }
    }

    /**
     * Creates an authorized Credential object.
     *
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        // Load client secrets.
        InputStream in = GoogleSheetsLoader.class.getResourceAsStream(SERVICE_ACCOUNT_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + SERVICE_ACCOUNT_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    private ValueRange getResponse(String spreadsheetName) throws IOException, GeneralSecurityException, TableNotFoundException {
        String spreadsheetId = tablesRepository.getSpreadsheetId(spreadsheetName);
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        Sheets service = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getServiceAccountCredentials())
                .setApplicationName(APPLICATION_NAME)
                .build();
        return service.spreadsheets().values()
                .get(spreadsheetId, worksheetName)
                .execute();
    }

    private List<String> getSpreadsheetContent(String spreadsheetName) throws IOException, GeneralSecurityException, TableNotFoundException {
        List<List<Object>> values = getResponse(spreadsheetName).getValues();
        return values.stream()
                .map(Object::toString)
                .collect(Collectors.toList());
    }

    private Credential getServiceAccountCredentials() throws IOException {
        InputStream is = GoogleSheetsLoader.class
                .getResourceAsStream(SERVICE_ACCOUNT_FILE_PATH);

        Credential credential = GoogleCredential.fromStream(is)
                .createScoped(SCOPES);

        return credential;
    }
}
