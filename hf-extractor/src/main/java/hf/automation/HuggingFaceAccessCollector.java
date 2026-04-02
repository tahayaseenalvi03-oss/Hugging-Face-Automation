package hf.automation;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HuggingFaceAccessCollector {

    private static final String DATASETS_LIST_URL_TEMPLATE = "https://huggingface.co/%s?tab=datasets";
    private static final String DATASET_SETTINGS_URL_TEMPLATE = "https://huggingface.co/datasets/%s/%s/settings";
    private static final Pattern DATASET_LINK_PATTERN = Pattern.compile("^/datasets/([^/]+)/([^/?#]+)$");
    private static final int MAX_DATASET_PAGES = 20;

    private final String orgName;
    private final String downloadPath;
    private final long loginWaitTimeoutMs;
    private final long downloadWaitTimeoutMs;
    private final Gson gson = new Gson();

    public HuggingFaceAccessCollector(
            String orgName,
            String downloadPath,
            long loginWaitTimeoutMs,
            long downloadWaitTimeoutMs
    ) {
        this.orgName = orgName;
        this.downloadPath = downloadPath;
        this.loginWaitTimeoutMs = loginWaitTimeoutMs;
        this.downloadWaitTimeoutMs = downloadWaitTimeoutMs;
    }

    public List<DatasetAccessRecord> collectAcceptedAccessRecords() throws Exception {
        ensureDownloadFolderExists();

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.setExperimentalOption("prefs", buildChromePrefs());

        ChromeDriver driver = new ChromeDriver(options);
        configureChromeDownloadFolder(driver);

        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

            driver.get("https://huggingface.co/login");
            System.out.println("Login manually within 60 seconds.");
            Thread.sleep(loginWaitTimeoutMs);

            List<String> datasetNames = collectDatasetNames(driver, wait);
            System.out.println("Datasets discovered: " + datasetNames.size());

            List<DatasetAccessRecord> acceptedRecords = new ArrayList<>();
            List<String> failedDatasets = new ArrayList<>();
            for (int index = 0; index < datasetNames.size(); index++) {
                String datasetName = datasetNames.get(index);
                System.out.printf("Processing dataset %d/%d: %s%n", index + 1, datasetNames.size(), datasetName);

                try {
                    File reportFile = downloadDatasetAccessReport(driver, wait, datasetName);
                    List<DatasetAccessRecord> datasetRecords = parseAcceptedRecords(datasetName, reportFile);
                    acceptedRecords.addAll(datasetRecords);

                    System.out.println("Accepted users found: " + datasetRecords.size());
                } catch (Exception e) {
                    failedDatasets.add(datasetName);
                    System.out.println("Skipping dataset due to error: " + e.getMessage());
                }
            }

            if (!failedDatasets.isEmpty()) {
                System.out.println("Datasets skipped: " + String.join(", ", failedDatasets));
            }

            return acceptedRecords;
        } finally {
            driver.quit();
        }
    }

    private List<String> collectDatasetNames(ChromeDriver driver, WebDriverWait wait) throws Exception {
        return collectDatasetNamesFromPage(driver, wait);
    }

    private List<String> collectDatasetNamesFromPage(ChromeDriver driver, WebDriverWait wait) throws Exception {
        Set<String> datasetNames = new LinkedHashSet<>();
        driver.get(String.format(DATASETS_LIST_URL_TEMPLATE, orgName));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
        Thread.sleep(3000);

        openFullDatasetListing(driver, wait);

        for (int pageIndex = 1; pageIndex <= MAX_DATASET_PAGES; pageIndex++) {
            List<String> pageDatasets = extractDatasetNamesFromPage(driver);
            datasetNames.addAll(pageDatasets);
            System.out.printf("Dataset page %d yielded %d dataset links.%n", pageIndex, pageDatasets.size());

            if (!goToNextDatasetPage(driver, wait, pageDatasets)) {
                break;
            }
        }

        return new ArrayList<>(datasetNames);
    }

    private void openFullDatasetListing(ChromeDriver driver, WebDriverWait wait) throws Exception {
        scrollToBottom(driver);
        Thread.sleep(1500);

        WebElement openButton = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//button[contains(., 'View') and contains(., 'datasets')] | //a[contains(., 'View') and contains(., 'datasets')]")
        ));

        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", openButton);
        Thread.sleep(500);
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", openButton);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//a[contains(@href, '/datasets/" + orgName + "/')]")));
        Thread.sleep(2000);
    }

    private List<String> extractDatasetNamesFromPage(ChromeDriver driver) {
        JavascriptExecutor js = driver;
        @SuppressWarnings("unchecked")
        List<String> hrefs = (List<String>) js.executeScript(
                "return Array.from(document.querySelectorAll('a[href]')).map(a => a.getAttribute('href'));"
        );

        Set<String> datasetNames = new LinkedHashSet<>();
        for (String href : hrefs) {
            if (href == null) {
                continue;
            }

            Matcher matcher = DATASET_LINK_PATTERN.matcher(href);
            if (!matcher.matches()) {
                continue;
            }

            if (!orgName.equals(matcher.group(1))) {
                continue;
            }

            datasetNames.add(matcher.group(2));
        }

        return new ArrayList<>(datasetNames);
    }

    private boolean goToNextDatasetPage(ChromeDriver driver, WebDriverWait wait, List<String> currentPageDatasets) throws Exception {
        scrollToBottom(driver);
        Thread.sleep(1000);

        List<WebElement> nextControls = driver.findElements(By.xpath(
                "//a[contains(normalize-space(.), 'Next')] | " +
                "//button[contains(normalize-space(.), 'Next')] | " +
                "//a[@aria-label='Next'] | //button[@aria-label='Next']"
        ));

        if (nextControls.isEmpty()) {
            return false;
        }

        WebElement nextControl = nextControls.get(0);
        String classes = safe(nextControl.getAttribute("class")).toLowerCase();
        String ariaDisabled = safe(nextControl.getAttribute("aria-disabled")).toLowerCase();
        String disabled = safe(nextControl.getAttribute("disabled")).toLowerCase();

        if (classes.contains("disabled") || "true".equals(ariaDisabled) || !disabled.isBlank()) {
            return false;
        }

        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", nextControl);
        return waitForDatasetPageChange(driver, currentPageDatasets, wait);
    }

    private void scrollToBottom(ChromeDriver driver) throws Exception {
        long previousHeight = -1;

        for (int attempt = 0; attempt < 10; attempt++) {
            long currentHeight = ((Number) ((JavascriptExecutor) driver)
                    .executeScript("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);"))
                    .longValue();

            ((JavascriptExecutor) driver).executeScript(
                    "window.scrollTo({top: Math.max(document.body.scrollHeight, document.documentElement.scrollHeight), behavior: 'instant'});"
            );

            Thread.sleep(1000);

            if (currentHeight == previousHeight) {
                return;
            }

            previousHeight = currentHeight;
        }
    }

    private boolean waitForDatasetPageChange(ChromeDriver driver, List<String> currentPageDatasets, WebDriverWait wait) throws Exception {
        try {
            wait.until(ignored -> {
                try {
                    List<String> nextPageDatasets = extractDatasetNamesFromPage(driver);
                    return !nextPageDatasets.isEmpty() && !nextPageDatasets.equals(currentPageDatasets);
                } catch (StaleElementReferenceException e) {
                    return false;
                }
            });

            Thread.sleep(1500);
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    private File downloadDatasetAccessReport(ChromeDriver driver, WebDriverWait wait, String datasetName) throws Exception {
        driver.get(String.format(DATASET_SETTINGS_URL_TEMPLATE, orgName, datasetName));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
        Thread.sleep(4000);

        WebElement downloadButton = driver.findElement(
                By.xpath("//*[contains(text(),'Download user access report')]")
        );

        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", downloadButton);
        Thread.sleep(1000);

        long downloadStartedAt = System.currentTimeMillis();
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", downloadButton);

        System.out.println("Downloading report...");
        return waitForReportFile(downloadStartedAt);
    }

    private File waitForReportFile(long downloadStartedAt) throws Exception {
        long deadline = System.currentTimeMillis() + downloadWaitTimeoutMs;
        File lastCandidate = null;
        long lastSize = -1;
        int stableChecks = 0;

        while (System.currentTimeMillis() < deadline) {
            File latestFile = findLatestJsonFile();

            if (latestFile != null && latestFile.length() > 0 && latestFile.lastModified() >= downloadStartedAt) {
                if (lastCandidate != null
                        && latestFile.getAbsolutePath().equals(lastCandidate.getAbsolutePath())
                        && latestFile.length() == lastSize) {
                    stableChecks++;
                } else {
                    lastCandidate = latestFile;
                    lastSize = latestFile.length();
                    stableChecks = 0;
                }

                if (stableChecks >= 2) {
                    return latestFile;
                }
            }

            Thread.sleep(1000);
        }

        File fallbackFile = findLatestJsonFile();
        if (fallbackFile != null) {
            return fallbackFile;
        }

        throw new RuntimeException("No JSON report found in download folder: " + downloadPath);
    }

    private File findLatestJsonFile() {
        File folder = new File(downloadPath);
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".json"));

        if (files == null || files.length == 0) {
            return null;
        }

        return List.of(files).stream()
                .max(Comparator.comparingLong(File::lastModified))
                .orElse(null);
    }

    private List<DatasetAccessRecord> parseAcceptedRecords(String datasetName, File reportFile) throws Exception {
        List<DatasetAccessRecord> acceptedRecords = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(reportFile))) {
            Type listType = new TypeToken<List<HfAccessReportEntry>>() { }.getType();
            List<HfAccessReportEntry> records = gson.fromJson(reader, listType);

            if (records == null) {
                return acceptedRecords;
            }

            for (HfAccessReportEntry record : records) {
                if (!"accepted".equalsIgnoreCase(safe(record.status))) {
                    continue;
                }

                acceptedRecords.add(new DatasetAccessRecord(
                        datasetName,
                        safe(record.fullname),
                        safe(record.email)
                ));
            }
        }

        return acceptedRecords;
    }

    private void ensureDownloadFolderExists() {
        File folder = new File(downloadPath);
        if (!folder.exists() && !folder.mkdirs()) {
            throw new RuntimeException("Unable to create download folder: " + downloadPath);
        }
    }

    private void configureChromeDownloadFolder(ChromeDriver driver) {
        Map<String, Object> params = new HashMap<>();
        params.put("behavior", "allow");
        params.put("downloadPath", downloadPath);
        driver.executeCdpCommand("Page.setDownloadBehavior", params);
    }

    private Map<String, Object> buildChromePrefs() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("download.default_directory", downloadPath);
        prefs.put("download.prompt_for_download", false);
        prefs.put("download.directory_upgrade", true);
        prefs.put("safebrowsing.enabled", true);
        return prefs;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static class HfAccessReportEntry {
        String fullname;
        String status;
        String email;
    }
}
