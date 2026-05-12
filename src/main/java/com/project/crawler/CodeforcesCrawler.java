package com.project.crawler;

import com.project.dao.SubmissionDAO;
import com.project.dao.UserDAO;
import com.project.model.Submission;
import com.project.model.User;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class CodeforcesCrawler {

    private static final String BASE_URL = "https://codeforces.com";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final int DEFAULT_MAX_NEW_SUBMISSIONS = 20;
    private static final Path DEBUG_LOG_PATH = Path.of("crawler-debug.log");
    private static final DateTimeFormatter SUBMISSION_TIME_FORMATTER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("MMM/dd/yyyy HH:mm")
            .toFormatter(Locale.ENGLISH);

    private static final List<By> SUBMISSION_ROW_LOCATORS = List.of(
            By.cssSelector("table.status-frame-datatable tr[data-submission-id]"),
            By.xpath("//table[contains(@class,'status-frame-datatable')]//tr[td]"),
            By.xpath("//table[.//th[contains(normalize-space(.), 'Verdict')]]//tr[td]")
    );
    private static final List<By> SOURCE_CODE_LOCATORS = List.of(
            By.cssSelector("pre#program-source-text"),
            By.cssSelector("pre.program-source"),
            By.cssSelector("pre.prettyprint"),
            By.cssSelector("div.source-code pre")
    );

    private final SubmissionDAO submissionDAO;
    private final UserDAO userDAO;
    private final boolean headless;
    private final HttpClient httpClient;

    public CodeforcesCrawler() {
        this(false);
    }

    public CodeforcesCrawler(boolean headless) {
        this.submissionDAO = new SubmissionDAO();
        this.userDAO = new UserDAO();
        this.headless = headless;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    public int crawl(String handle) throws SQLException {
        return crawl(handle, DEFAULT_MAX_NEW_SUBMISSIONS);
    }

    public int crawl(String handle, int maxNewSubmissions) throws SQLException {
        return crawl(resolveUser(handle), maxNewSubmissions);
    }

    public int crawl(User user) throws SQLException {
        return crawl(user, DEFAULT_MAX_NEW_SUBMISSIONS);
    }

    public int crawl(User user, int maxNewSubmissions) throws SQLException {
        return crawlDetailed(user, maxNewSubmissions).insertedCount();
    }

    public CrawlReport crawlDetailed(String handle, int maxNewSubmissions) throws SQLException {
        return crawlDetailed(resolveUser(handle), maxNewSubmissions);
    }

    public CrawlReport crawlDetailed(User user, int maxNewSubmissions) throws SQLException {
        try {
            return crawlWithMode(user, headless, sanitizeMaxNewSubmissions(maxNewSubmissions));
        } catch (NoSuchSessionException sessionException) {
            throw new IllegalStateException("""
                    Cửa sổ Chrome crawler đã bị đóng hoặc mất kết nối trong khi đang lấy source code.
                    Hãy mở lại Chrome xác minh, đăng nhập trên đúng profile crawler rồi thử crawl lại.
                    """.trim(), sessionException);
        } catch (TimeoutException timeoutException) {
            if (headless) {
                return crawlWithMode(user, false, sanitizeMaxNewSubmissions(maxNewSubmissions));
            }
            throw timeoutException;
        }
    }

    private CrawlReport crawlWithMode(User user, boolean runHeadless, int maxNewSubmissions) throws SQLException {
        WebDriver driver = null;
        try {
            logDebug("Start crawl handle=%s maxNewSubmissions=%d headless=%s".formatted(
                    user.getHandle(), maxNewSubmissions, runHeadless));

            List<SubmissionSnapshot> acceptedSubmissions = collectAcceptedSubmissionsFromApi(user.getHandle(), maxNewSubmissions);
            logDebug("Collected accepted submissions handle=%s count=%d".formatted(user.getHandle(), acceptedSubmissions.size()));
            if (acceptedSubmissions.isEmpty()) {
                logDebug("No accepted submissions found from Codeforces API handle=%s".formatted(user.getHandle()));
            }
            int insertedCount = 0;
            int existingCount = 0;
            int sourceUnavailableCount = 0;
            int missingSourceElementCount = 0;
            int blockedCount = 0;
            List<Long> sourceUnavailableSubmissionIds = new ArrayList<>();
            Optional<String> loggedInHandle = Optional.empty();
            boolean ownCrawl = false;

            for (SubmissionSnapshot snapshot : acceptedSubmissions) {
                if (submissionDAO.existsBySubmissionId(snapshot.submissionId())) {
                    existingCount++;
                    logDebug("Skip existing submissionId=%d handle=%s".formatted(snapshot.submissionId(), user.getHandle()));
                    continue;
                }

                if (driver == null) {
                    driver = createDriver(runHeadless);
                    ensureCodeforcesContext(driver);
                    loggedInHandle = resolveLoggedInHandle(driver);
                    ownCrawl = loggedInHandle.isPresent() && isSameCodeforcesHandle(loggedInHandle.get(), user.getHandle());
                    logDebug("Chrome logged in handle=%s targetHandle=%s ownCrawl=%s".formatted(
                            loggedInHandle.orElse("<none>"), user.getHandle(), ownCrawl));
                }

                sleepQuietly(1200L);
                SourceFetchResult fetchResult = ownCrawl
                        ? fetchSourceCodeViaAuthenticatedApi(driver, user.getHandle(), snapshot.submissionId(), maxNewSubmissions)
                        : new SourceFetchResult(null, -1, "Public source crawl: skip private includeSources API.");
                if (fetchResult.sourceCode() == null || fetchResult.sourceCode().isBlank()) {
                    if (fetchResult.blockedByCloudflare()) {
                        blockedCount++;
                        logDebug("Blocked while fetching submissionId=%d handle=%s".formatted(
                                snapshot.submissionId(), user.getHandle()));
                        break;
                    }
                    logDebug("Trying source page fallback submissionId=%d handle=%s reason=%s".formatted(
                            snapshot.submissionId(), user.getHandle(), fetchResult.shortErrorMessage()));
                    fetchResult = fetchSourceCodeFromSubmissionPage(driver, snapshot);
                }

                if (fetchResult.blockedByCloudflare()) {
                    blockedCount++;
                    logDebug("Blocked while fetching submissionId=%d handle=%s".formatted(
                            snapshot.submissionId(), user.getHandle()));
                    break;
                }

                String sourceCode = normalizeSourceCode(fetchResult.sourceCode());
                if (sourceCode == null || sourceCode.isBlank()) {
                    if (fetchResult.sourceUnavailable()) {
                        sourceUnavailableCount++;
                        sourceUnavailableSubmissionIds.add(snapshot.submissionId());
                        logDebug("Source unavailable (N/A) submissionId=%d handle=%s reason=%s".formatted(
                                snapshot.submissionId(), user.getHandle(), fetchResult.shortErrorMessage()));
                        continue;
                    }
                    if (fetchResult.permissionDenied()) {
                        if (!ownCrawl) {
                            sourceUnavailableCount++;
                            sourceUnavailableSubmissionIds.add(snapshot.submissionId());
                            logDebug("Public source not permitted submissionId=%d handle=%s loginHandle=%s reason=%s".formatted(
                                    snapshot.submissionId(),
                                    user.getHandle(),
                                    loggedInHandle.orElse("<none>"),
                                    fetchResult.shortErrorMessage()));
                            continue;
                        }
                        throw new IllegalStateException("""
                                Codeforces không cho lấy source của handle %s.
                                Chrome crawler phải đăng nhập đúng tài khoản có handle này. Nếu đang đăng nhập tài khoản khác,
                                hãy Logout trong Chrome xác minh, đăng nhập lại đúng handle rồi crawl lại.
                                Chi tiết: %s
                                """.formatted(user.getHandle(), fetchResult.shortErrorMessage()).trim());
                    }
                    missingSourceElementCount++;
                    logDebug("No readable source text submissionId=%d url=%s details=%s".formatted(
                            snapshot.submissionId(), snapshot.submissionUrl(), fetchResult.shortErrorMessage()));
                    continue;
                }

                submissionDAO.insert(toSubmission(user, snapshot, sourceCode));
                insertedCount++;
                logDebug("Inserted submissionId=%d handle=%s sourceLength=%d".formatted(
                        snapshot.submissionId(), user.getHandle(), sourceCode.length()));
            }

            userDAO.updateLastCrawledAt(user.getId(), Timestamp.from(Instant.now()));
            CrawlReport report = new CrawlReport(
                    user.getHandle(),
                    acceptedSubmissions.size(),
                    existingCount,
                    sourceUnavailableCount,
                    missingSourceElementCount,
                    blockedCount,
                    insertedCount,
                    sourceUnavailableSubmissionIds
            );
            logDebug("Finish crawl " + report.toDebugString());

            if (blockedCount > 0) {
                throw new IllegalStateException("""
                        Codeforces chặn request lấy source code.
                        Trang submissions đã mở được, nhưng khi vào trang submission để đọc source thì bị Verification/Captcha hoặc bị từ chối.
                        Hãy mở Chrome xác minh trong phần Cài đặt, đăng nhập/vượt Verification trên đúng profile crawler rồi thử lại.
                        """.trim());
            }
            return report;
        } finally {
            CodeforcesBrowserSession.releaseDriver(driver);
        }
    }

    private WebDriver createDriver(boolean runHeadless) {
        return CodeforcesBrowserSession.createCrawlerDriver(runHeadless);
    }

    private void waitForSubmissionsPage(WebDriver driver, String handle) {
        WebDriverWait wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
        wait.until(webDriver -> {
            String title = webDriver.getTitle();
            return title == null || !title.toLowerCase(Locale.ROOT).contains("just a moment");
        });

        try {
            wait.until(webDriver -> hasSubmissionTable(webDriver) || hasNoSubmissionMessage(webDriver));
        } catch (TimeoutException exception) {
            throw buildCrawlerPageException(driver, handle, exception);
        }
    }

    private RuntimeException buildCrawlerPageException(WebDriver driver, String handle, TimeoutException exception) {
        String title = safeLower(driver.getTitle());
        String url = safeLower(driver.getCurrentUrl());
        String pageSource = safeLower(driver.getPageSource());

        if (title.contains("verification") || pageSource.contains("captcha")) {
            return new IllegalStateException("""
                    Codeforces đang hiển thị trang Verification/Captcha nên crawler không đọc được submissions.
                    Hãy thử lại bằng Chrome thường hoặc đổi mạng rồi crawl lại.
                    """.trim(), exception);
        }

        if (url.contains("/enter") || pageSource.contains(">enter<")) {
            return new IllegalStateException("""
                    Codeforces đang chuyển về trang đăng nhập hoặc trang xác minh.
                    Crawler chưa thể truy cập submissions của handle %s.
                    """.formatted(handle).trim(), exception);
        }

        return new IllegalStateException("""
                Không đọc được bảng submissions của handle %s trong thời gian chờ.
                URL hiện tại: %s
                Tiêu đề trang: %s
                """.formatted(handle, driver.getCurrentUrl(), driver.getTitle()).trim(), exception);
    }

    private boolean hasSubmissionTable(WebDriver driver) {
        for (By locator : SUBMISSION_ROW_LOCATORS) {
            if (!driver.findElements(locator).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasNoSubmissionMessage(WebDriver driver) {
        return driver.getPageSource().toLowerCase(Locale.ROOT).contains("no submissions");
    }

    private List<SubmissionSnapshot> collectAcceptedSubmissions(WebDriver driver, String handle, int maxNewSubmissions) {
        List<SubmissionSnapshot> snapshots = collectAcceptedSubmissionsFromApi(handle, maxNewSubmissions);
        if (!snapshots.isEmpty()) {
            return snapshots;
        }

        snapshots = collectAcceptedSubmissionsViaJavascript(driver);
        if (snapshots.isEmpty()) {
            List<WebElement> rows = findSubmissionRows(driver);
            for (WebElement row : rows) {
                Optional<SubmissionSnapshot> snapshot = toSnapshot(row);
                if (snapshot.isPresent() && isAccepted(snapshot.get().verdict())) {
                    snapshots.add(snapshot.get());
                }
            }
        }

        snapshots.sort(Comparator.comparing(SubmissionSnapshot::submittedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SubmissionSnapshot::submissionId, Comparator.reverseOrder()));
        if (snapshots.size() <= maxNewSubmissions) {
            return snapshots;
        }
        return new ArrayList<>(snapshots.subList(0, maxNewSubmissions));
    }

    private List<SubmissionSnapshot> collectAcceptedSubmissionsFromApi(String handle, int maxNewSubmissions) {
        int apiCount = Math.min(Math.max(maxNewSubmissions * 20, 100), 1000);
        String encodedHandle = URLEncoder.encode(handle, StandardCharsets.UTF_8);
        URI uri = URI.create(BASE_URL + "/api/user.status?handle=" + encodedHandle + "&from=1&count=" + apiCount);

        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "AlgoProfiler/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logDebug("Codeforces API user.status failed handle=%s httpStatus=%d".formatted(handle, response.statusCode()));
                return List.of();
            }

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!"OK".equalsIgnoreCase(getString(root, "status"))) {
                logDebug("Codeforces API user.status returned FAILED handle=%s comment=%s".formatted(
                        handle, getString(root, "comment")));
                return List.of();
            }

            JsonArray submissions = root.getAsJsonArray("result");
            List<SubmissionSnapshot> snapshots = new ArrayList<>();
            for (JsonElement element : submissions) {
                if (!element.isJsonObject()) {
                    continue;
                }

                JsonObject submission = element.getAsJsonObject();
                if (!"OK".equalsIgnoreCase(getString(submission, "verdict"))) {
                    continue;
                }

                Optional<SubmissionSnapshot> snapshot = toSnapshotFromApiSubmission(submission);
                snapshot.ifPresent(snapshots::add);
                if (snapshots.size() >= maxNewSubmissions) {
                    break;
                }
            }

            logDebug("Codeforces API accepted metadata handle=%s apiCount=%d accepted=%d".formatted(
                    handle, apiCount, snapshots.size()));
            return snapshots;
        } catch (IOException exception) {
            logDebug("Codeforces API IO error handle=%s message=%s".formatted(handle, exception.getMessage()));
            return List.of();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logDebug("Codeforces API interrupted handle=%s".formatted(handle));
            return List.of();
        } catch (RuntimeException exception) {
            logDebug("Codeforces API parse error handle=%s message=%s".formatted(handle, exception.getMessage()));
            return List.of();
        }
    }

    private Optional<SubmissionSnapshot> toSnapshotFromApiSubmission(JsonObject submission) {
        JsonObject problem = submission.has("problem") && submission.get("problem").isJsonObject()
                ? submission.getAsJsonObject("problem")
                : new JsonObject();
        Long submissionId = getLong(submission, "id");
        Integer contestId = getInteger(submission, "contestId");
        if (contestId == null) {
            contestId = getInteger(problem, "contestId");
        }
        if (submissionId == null || contestId == null) {
            return Optional.empty();
        }

        long creationTimeSeconds = Optional.ofNullable(getLong(submission, "creationTimeSeconds")).orElse(0L);
        Timestamp submittedAt = creationTimeSeconds > 0
                ? Timestamp.from(Instant.ofEpochSecond(creationTimeSeconds))
                : null;
        String problemIndex = getString(problem, "index");
        String problemName = getString(problem, "name");
        String language = getString(submission, "programmingLanguage");

        return Optional.of(new SubmissionSnapshot(
                contestId,
                submissionId,
                problemIndex,
                problemName,
                language,
                "Accepted",
                submittedAt,
                buildSubmissionUrl(contestId, submissionId)
        ));
    }

    private List<WebElement> findSubmissionRows(WebDriver driver) {
        for (By locator : SUBMISSION_ROW_LOCATORS) {
            List<WebElement> rows = driver.findElements(locator);
            if (!rows.isEmpty()) {
                return rows;
            }
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<SubmissionSnapshot> collectAcceptedSubmissionsViaJavascript(WebDriver driver) {
        if (!(driver instanceof JavascriptExecutor executor)) {
            return List.of();
        }

        Object result = executor.executeScript("""
                const selectors = [
                  "table.status-frame-datatable tr[data-submission-id]",
                  "table.status-frame-datatable tr"
                ];
                let rows = [];
                for (const selector of selectors) {
                  rows = Array.from(document.querySelectorAll(selector));
                  if (rows.length > 0) break;
                }
                return rows
                  .map((row) => {
                    const cells = Array.from(row.querySelectorAll("td")).map((cell) => (cell.innerText || cell.textContent || "").trim());
                    const idLink = row.querySelector("td:first-child a, a[href*='/submission/']");
                    return {
                      cells,
                      href: idLink ? idLink.href : "",
                      idText: idLink ? (idLink.innerText || idLink.textContent || "").trim() : "",
                      submissionIdAttr: row.getAttribute("data-submission-id") || ""
                    };
                  })
                  .filter((row) => row.cells.length > 0);
                """);

        if (!(result instanceof List<?> rawRows)) {
            return List.of();
        }

        List<SubmissionSnapshot> snapshots = new ArrayList<>();
        for (Object rawRow : rawRows) {
            if (!(rawRow instanceof Map<?, ?> rawMap)) {
                continue;
            }

            Map<String, Object> rowMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                rowMap.put(String.valueOf(entry.getKey()), entry.getValue());
            }

            Optional<SubmissionSnapshot> snapshot = toSnapshotFromJavascriptRow(rowMap);
            if (snapshot.isPresent() && isAccepted(snapshot.get().verdict())) {
                snapshots.add(snapshot.get());
            }
        }
        return snapshots;
    }

    private Optional<SubmissionSnapshot> toSnapshotFromJavascriptRow(Map<String, Object> rowMap) {
        List<String> cells = extractStringList(rowMap.get("cells"));
        if (cells.size() < 6) {
            return Optional.empty();
        }

        String href = sanitizeText(String.valueOf(rowMap.getOrDefault("href", "")));
        String idText = sanitizeText(String.valueOf(rowMap.getOrDefault("idText", "")));
        String submissionIdAttr = sanitizeText(String.valueOf(rowMap.getOrDefault("submissionIdAttr", "")));

        Long submissionId = parseLongSafe(!idText.isBlank() ? idText : submissionIdAttr);
        if (submissionId == null) {
            return Optional.empty();
        }

        Integer contestId = extractContestId(resolveCodeforcesUrl(href));
        if (contestId == null) {
            return Optional.empty();
        }

        String verdict = extractVerdict(cells);
        String problemText = extractProblemTextFromStrings(cells);
        String language = extractLanguageTextFromStrings(cells);
        Timestamp submittedAt = parseSubmissionTimestamp(extractSubmissionTimeTextFromStrings(cells));

        return Optional.of(new SubmissionSnapshot(
                contestId,
                submissionId,
                extractProblemIndex(problemText),
                extractProblemName(problemText),
                language,
                verdict,
                submittedAt,
                resolveCodeforcesUrl(href)
        ));
    }

    private Optional<SubmissionSnapshot> toSnapshot(WebElement row) {
        List<WebElement> cells = row.findElements(By.tagName("td"));
        if (cells.size() < 6) {
            return Optional.empty();
        }

        WebElement idLink = findSubmissionLink(row);
        if (idLink == null) {
            return Optional.empty();
        }

        String submissionIdText = sanitizeText(idLink.getText());
        Long submissionId = parseLongSafe(submissionIdText);
        if (submissionId == null) {
            return Optional.empty();
        }

        String submissionUrl = resolveCodeforcesUrl(idLink.getAttribute("href"));
        Integer contestId = extractContestId(submissionUrl);
        if (contestId == null) {
            return Optional.empty();
        }

        String verdict = extractVerdict(row, cells);
        String problemText = extractProblemText(cells);
        String language = extractLanguageText(cells);
        Timestamp submittedAt = parseSubmissionTimestamp(extractSubmissionTimeText(cells));

        return Optional.of(new SubmissionSnapshot(
                contestId,
                submissionId,
                extractProblemIndex(problemText),
                extractProblemName(problemText),
                language,
                verdict,
                submittedAt,
                submissionUrl
        ));
    }

    private WebElement findSubmissionLink(WebElement row) {
        List<By> locators = List.of(
                By.cssSelector("td:first-child a"),
                By.xpath(".//a[contains(@href, '/submission/')]"),
                By.xpath(".//a[normalize-space()]")
        );

        for (By locator : locators) {
            List<WebElement> candidates = row.findElements(locator);
            for (WebElement candidate : candidates) {
                String text = sanitizeText(candidate.getText());
                if (parseLongSafe(text) != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private boolean isAccepted(String verdict) {
        return verdict != null && verdict.toLowerCase(Locale.ROOT).startsWith("accepted");
    }

    private String extractVerdict(List<String> cells) {
        for (String cell : cells) {
            String text = sanitizeText(cell);
            String lower = text.toLowerCase(Locale.ROOT);
            if (lower.startsWith("accepted")
                    || lower.contains("wrong answer")
                    || lower.contains("runtime error")
                    || lower.contains("time limit")
                    || lower.contains("memory limit")
                    || lower.contains("compilation error")
                    || lower.contains("skipped")
                    || lower.contains("pretest")) {
                return text;
            }
        }
        return cells.size() > 5 ? sanitizeText(cells.get(5)) : "";
    }

    private String extractVerdict(WebElement row, List<WebElement> cells) {
        List<By> verdictLocators = List.of(
                By.cssSelector("span.verdict-accepted"),
                By.cssSelector("span.submissionVerdictWrapper"),
                By.cssSelector("td.status-verdict-cell"),
                By.xpath(".//td[contains(., 'Accepted')]"),
                By.xpath(".//span[contains(., 'Accepted')]")
        );

        for (By locator : verdictLocators) {
            List<WebElement> matches = row.findElements(locator);
            for (WebElement match : matches) {
                String text = sanitizeText(match.getText());
                if (!text.isBlank()) {
                    return text;
                }
            }
        }

        for (WebElement cell : cells) {
            String text = sanitizeText(cell.getText());
            String lower = text.toLowerCase(Locale.ROOT);
            if (lower.startsWith("accepted")
                    || lower.contains("wrong answer")
                    || lower.contains("runtime error")
                    || lower.contains("time limit")
                    || lower.contains("memory limit")
                    || lower.contains("compilation error")
                    || lower.contains("skipped")
                    || lower.contains("pretest")) {
                return text;
            }
        }

        return cells.size() > 5 ? sanitizeText(cells.get(5).getText()) : "";
    }

    private String extractProblemTextFromStrings(List<String> cells) {
        for (String cell : cells) {
            String text = sanitizeText(cell);
            if (text.contains(" - ")) {
                return text;
            }
        }
        return cells.size() > 3 ? sanitizeText(cells.get(3)) : "";
    }

    private String extractProblemText(List<WebElement> cells) {
        for (WebElement cell : cells) {
            String text = sanitizeText(cell.getText());
            if (text.contains(" - ")) {
                return text;
            }
        }
        return cells.size() > 3 ? sanitizeText(cells.get(3).getText()) : "";
    }

    private String extractLanguageTextFromStrings(List<String> cells) {
        for (String cell : cells) {
            String text = sanitizeText(cell);
            String lower = text.toLowerCase(Locale.ROOT);
            if (lower.contains("gcc") || lower.contains("java") || lower.contains("python") || lower.contains("clang")) {
                return text;
            }
        }
        return cells.size() > 4 ? sanitizeText(cells.get(4)) : "";
    }

    private String extractLanguageText(List<WebElement> cells) {
        for (WebElement cell : cells) {
            String text = sanitizeText(cell.getText());
            String lower = text.toLowerCase(Locale.ROOT);
            if (lower.contains("gcc") || lower.contains("java") || lower.contains("python") || lower.contains("clang")) {
                return text;
            }
        }
        return cells.size() > 4 ? sanitizeText(cells.get(4).getText()) : "";
    }

    private String extractSubmissionTimeTextFromStrings(List<String> cells) {
        for (String cell : cells) {
            String text = sanitizeText(cell);
            if (looksLikeSubmissionTime(text)) {
                return text;
            }
        }
        return cells.size() > 1 ? sanitizeText(cells.get(1)) : "";
    }

    private String extractSubmissionTimeText(List<WebElement> cells) {
        for (WebElement cell : cells) {
            String text = sanitizeText(cell.getText());
            if (looksLikeSubmissionTime(text)) {
                return text;
            }
        }
        return cells.size() > 1 ? sanitizeText(cells.get(1).getText()) : "";
    }

    private boolean looksLikeSubmissionTime(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return text.toLowerCase(Locale.ROOT).matches("^[a-z]{3}/\\d{2}/\\d{4}\\s+\\d{2}:\\d{2}$");
    }

    private SourceFetchResult fetchSourceCodeFromSubmissionPage(WebDriver driver, SubmissionSnapshot snapshot) {
        try {
            openUrlReliably(driver, snapshot.submissionUrl(), snapshot.submissionId());
            waitForSubmissionSourcePage(driver, snapshot);
        } catch (RuntimeException exception) {
            return new SourceFetchResult(null, 404, exception.getMessage());
        }

        if (isVerificationPage(driver) || isLoginPage(driver)) {
            return new SourceFetchResult(null, 403, driver.getPageSource());
        }

        for (By locator : SOURCE_CODE_LOCATORS) {
            List<WebElement> sourceElements = driver.findElements(locator);
            if (!sourceElements.isEmpty()) {
                String source = normalizeSourceCode(sourceElements.get(0).getText());
                if (source != null && !source.isBlank()) {
                    return new SourceFetchResult(source, 200, null);
                }
            }
        }

        if (isSourceUnavailablePage(driver)) {
            return new SourceFetchResult(null, 204, getBodyText(driver));
        }

        return new SourceFetchResult(null, 404, getBodyText(driver));
    }

    private void ensureCodeforcesContext(WebDriver driver) {
        String currentUrl = safeLower(driver.getCurrentUrl());
        if (!currentUrl.startsWith(BASE_URL) || currentUrl.contains("/enter")) {
            driver.get(BASE_URL + "/");
        }

        try {
            new WebDriverWait(driver, Duration.ofSeconds(15)).until(webDriver -> {
                String title = safeLower(webDriver.getTitle());
                return !title.contains("just a moment");
            });
        } catch (TimeoutException exception) {
            throw new IllegalStateException("""
                    Codeforces đang hiển thị trang Verification/Captcha trong Chrome crawler.
                    Hãy hoàn tất xác minh thủ công trong phần Cài đặt rồi thử crawl lại.
                    """.trim(), exception);
        }
    }

    private Optional<String> resolveLoggedInHandle(WebDriver driver) {
        if (isLoginPage(driver) || isVerificationPage(driver) || !(driver instanceof JavascriptExecutor executor)) {
            return Optional.empty();
        }

        try {
            Object result = executor.executeScript("""
                    const links = Array.from(document.querySelectorAll('a'));
                    const normalize = (value) => (value || '').trim();
                    const profileHandle = (link) => {
                      const href = link.getAttribute('href') || '';
                      const match = href.match(/\\/profile\\/([^/?#]+)/);
                      if (match && match[1]) {
                        return decodeURIComponent(match[1]);
                      }
                      return '';
                    };
                    const logoutIndex = links.findIndex((link) => normalize(link.textContent).toLowerCase() === 'logout');
                    if (logoutIndex >= 0) {
                      for (let i = logoutIndex - 1; i >= 0; i--) {
                        const handle = profileHandle(links[i]);
                        if (handle) return handle;
                      }
                    }
                    return '';
                    """);
            String handle = sanitizeText(result == null ? "" : result.toString());
            return handle.isBlank() ? Optional.empty() : Optional.of(handle);
        } catch (RuntimeException exception) {
            logDebug("Cannot resolve logged-in Codeforces handle: " + exception.getMessage());
            return Optional.empty();
        }
    }

    private boolean isSameCodeforcesHandle(String firstHandle, String secondHandle) {
        return sanitizeText(firstHandle).equalsIgnoreCase(sanitizeText(secondHandle));
    }

    private SourceFetchResult fetchSourceCodeViaAuthenticatedApi(
            WebDriver driver,
            String handle,
            Long targetSubmissionId,
            int maxNewSubmissions
    ) {
        if (!(driver instanceof JavascriptExecutor executor)) {
            return new SourceFetchResult(null, -1, "JavascriptExecutor is not available.");
        }

        try {
            ensureCodeforcesContext(driver);

            int apiCount = Math.min(Math.max(maxNewSubmissions * 20, 100), 1000);
            Object result = executor.executeAsyncScript("""
                    const handle = arguments[0];
                    const submissionId = String(arguments[1]);
                    const count = arguments[2];
                    const callback = arguments[arguments.length - 1];
                    const url = '/api/user.status?handle=' + encodeURIComponent(handle)
                        + '&from=1&count=' + encodeURIComponent(count)
                        + '&includeSources=true';

                    fetch(url, { credentials: 'include' })
                        .then(async (response) => {
                            const text = await response.text();
                            let data;
                            try {
                                data = JSON.parse(text);
                            } catch (error) {
                                callback({ status: response.status, source: null, error: text.slice(0, 500) });
                                return;
                            }

                            if (data.status !== 'OK') {
                                callback({
                                    status: response.status,
                                    source: null,
                                    error: data.comment || data.status || text.slice(0, 500)
                                });
                                return;
                            }

                            const found = (data.result || []).find((item) => String(item.id) === submissionId);
                            callback({
                                status: response.status,
                                source: found && found.source ? found.source : null,
                                error: found ? null : 'Submission not found in includeSources API window.'
                            });
                        })
                        .catch((error) => callback({ status: -1, source: null, error: String(error) }));
                    """, handle, targetSubmissionId.toString(), apiCount);

            if (!(result instanceof Map<?, ?> resultMap)) {
                return new SourceFetchResult(null, -1, "Unknown includeSources API response.");
            }

            int statusCode = resultMap.get("status") instanceof Number number ? number.intValue() : -1;
            String source = resultMap.get("source") == null ? null : resultMap.get("source").toString();
            String error = resultMap.get("error") == null ? null : resultMap.get("error").toString();
            if (source != null && !source.isBlank()) {
                logDebug("Fetched source through authenticated includeSources API submissionId=%s sourceLength=%d"
                        .formatted(targetSubmissionId, source.length()));
            } else if (error != null && !error.isBlank()) {
                logDebug("includeSources API did not return source submissionId=%s message=%s"
                        .formatted(targetSubmissionId, new SourceFetchResult(null, statusCode, error).shortErrorMessage()));
            }
            return new SourceFetchResult(source, statusCode, error);
        } catch (RuntimeException exception) {
            logDebug("includeSources API browser error submissionId=%s message=%s".formatted(
                    targetSubmissionId, exception.getMessage()));
            return new SourceFetchResult(null, -1, exception.getMessage());
        }
    }

    private void openUrlReliably(WebDriver driver, String url, Long expectedSubmissionId) {
        driver.get(url);
        if (currentUrlContains(driver, expectedSubmissionId)) {
            return;
        }

        sleepQuietly(1000L);
        if (currentUrlContains(driver, expectedSubmissionId)) {
            return;
        }

        if (driver instanceof JavascriptExecutor executor) {
            executor.executeScript("window.location.assign(arguments[0]);", url);
            sleepQuietly(1500L);
        }

        if (!currentUrlContains(driver, expectedSubmissionId)) {
            driver.navigate().to(url);
        }
    }

    private boolean currentUrlContains(WebDriver driver, Long expectedSubmissionId) {
        String currentUrl = safeLower(driver.getCurrentUrl());
        return expectedSubmissionId != null && currentUrl.contains(expectedSubmissionId.toString());
    }

    private void waitForSubmissionSourcePage(WebDriver driver, SubmissionSnapshot snapshot) {
        WebDriverWait wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
        try {
            wait.until(webDriver -> {
                if (isTransientBrowserCheck(webDriver)) {
                    return false;
                }
                if (isVerificationPage(webDriver)
                        || isLoginPage(webDriver)
                        || isPermissionDeniedPage(webDriver)
                        || isSourceUnavailablePage(webDriver)) {
                    return true;
                }
                for (By locator : SOURCE_CODE_LOCATORS) {
                    if (!webDriver.findElements(locator).isEmpty()) {
                        return true;
                    }
                }
                return false;
            });
        } catch (TimeoutException exception) {
            throw new IllegalStateException("""
                    Không mở được trang source của submission %s trong thời gian chờ.
                    URL hiện tại: %s
                    Tiêu đề trang: %s
                    """.formatted(snapshot.submissionId(), driver.getCurrentUrl(), driver.getTitle()).trim(), exception);
        }
    }

    private boolean isVerificationPage(WebDriver driver) {
        String title = safeLower(driver.getTitle());
        String bodyText = safeLower(getBodyText(driver));
        return title.contains("verification")
                || title.contains("just a moment")
                || bodyText.contains("captcha")
                || bodyText.contains("checking your browser")
                || bodyText.contains("verify you are human");
    }

    private boolean isTransientBrowserCheck(WebDriver driver) {
        String bodyText = safeLower(getBodyText(driver));
        return bodyText.contains("please wait. your browser is being checked")
                || bodyText.contains("browser is being checked");
    }

    private boolean isLoginPage(WebDriver driver) {
        String url = safeLower(driver.getCurrentUrl());
        String bodyText = safeLower(getBodyText(driver));
        return url.contains("/enter")
                || (bodyText.contains("login") && bodyText.contains("password") && bodyText.contains("handle/email"));
    }

    private boolean isPermissionDeniedPage(WebDriver driver) {
        String bodyText = safeLower(getBodyText(driver));
        return bodyText.contains("you are not allowed to view")
                || bodyText.contains("not allowed to view the requested page");
    }

    private boolean isSourceUnavailablePage(WebDriver driver) {
        String pageText = safeLower(getBodyText(driver));
        String pageSource = safeLower(driver.getPageSource());
        return (pageText.contains("source") && pageText.contains("n/a"))
                || pageSource.contains(">n/a<")
                || pageSource.contains(">n/a </");
    }

    private Submission toSubmission(User user, SubmissionSnapshot snapshot, String sourceCode) {
        Submission submission = new Submission();
        submission.setUserId(user.getId());
        submission.setContestId(snapshot.contestId());
        submission.setSubmissionId(snapshot.submissionId());
        submission.setProblemIndex(snapshot.problemIndex());
        submission.setProblemName(snapshot.problemName());
        submission.setProgrammingLanguage(snapshot.programmingLanguage());
        submission.setVerdict(snapshot.verdict());
        submission.setSubmittedAt(snapshot.submittedAt());
        submission.setSourceCode(sourceCode);
        submission.setCodeHash(calculateSha256(sourceCode));
        return submission;
    }

    private Timestamp parseSubmissionTimestamp(String rawTimestamp) {
        if (rawTimestamp == null || rawTimestamp.isBlank()) {
            return null;
        }

        try {
            LocalDateTime dateTime = LocalDateTime.parse(rawTimestamp, SUBMISSION_TIME_FORMATTER);
            return Timestamp.valueOf(dateTime);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private Integer extractContestId(String submissionUrl) {
        if (submissionUrl == null || submissionUrl.isBlank()) {
            return null;
        }

        String path = URI.create(submissionUrl).getPath();
        String[] parts = path.split("/");
        for (int index = 0; index < parts.length; index++) {
            if (("contest".equals(parts[index]) || "gym".equals(parts[index]))
                    && index + 1 < parts.length
                    && isInteger(parts[index + 1])) {
                return Integer.parseInt(parts[index + 1]);
            }

            if ("problemset".equals(parts[index])
                    && index + 2 < parts.length
                    && "submission".equals(parts[index + 1])
                    && isInteger(parts[index + 2])) {
                return Integer.parseInt(parts[index + 2]);
            }
        }
        return null;
    }

    private String extractProblemIndex(String problemText) {
        if (problemText == null || problemText.isBlank()) {
            return "";
        }

        int separatorIndex = problemText.indexOf(" - ");
        if (separatorIndex > 0) {
            return problemText.substring(0, separatorIndex).trim();
        }
        return problemText.trim();
    }

    private String extractProblemName(String problemText) {
        if (problemText == null || problemText.isBlank()) {
            return "";
        }

        int separatorIndex = problemText.indexOf(" - ");
        if (separatorIndex >= 0 && separatorIndex + 3 < problemText.length()) {
            return problemText.substring(separatorIndex + 3).trim();
        }
        return problemText.trim();
    }

    private String normalizeHandle(String handle) {
        return sanitizeText(handle);
    }

    private User resolveUser(String handle) throws SQLException {
        String normalizedHandle = normalizeHandle(handle);
        return userDAO.findByHandle(normalizedHandle)
                .orElseThrow(() -> new IllegalArgumentException("Handle not found in database: " + normalizedHandle));
    }

    private String resolveCodeforcesUrl(String href) {
        if (href == null || href.isBlank()) {
            return "";
        }
        return URI.create(BASE_URL).resolve(href).toString();
    }

    private String buildSubmissionUrl(Integer contestId, Long submissionId) {
        if (contestId == null || submissionId == null) {
            return "";
        }
        String area = contestId >= 100000 ? "gym" : "contest";
        return BASE_URL + "/" + area + "/" + contestId + "/submission/" + submissionId;
    }

    private String normalizeSourceCode(String sourceCode) {
        if (sourceCode == null) {
            return null;
        }
        return sourceCode.replace("\r\n", "\n").trim();
    }

    private String sanitizeText(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        return normalized.replace('\u00A0', ' ').trim();
    }

    private Long parseLongSafe(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String digits = value.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }

        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String getString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        return object.get(key).getAsString();
    }

    private Long getLong(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        try {
            return object.get(key).getAsLong();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private Integer getInteger(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        try {
            return object.get(key).getAsInt();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private String calculateSha256(String sourceCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sourceCode.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", exception);
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private int sanitizeMaxNewSubmissions(int maxNewSubmissions) {
        return maxNewSubmissions <= 0 ? DEFAULT_MAX_NEW_SUBMISSIONS : maxNewSubmissions;
    }

    private List<String> extractStringList(Object rawValue) {
        if (!(rawValue instanceof List<?> rawList)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object item : rawList) {
            values.add(sanitizeText(String.valueOf(item)));
        }
        return values;
    }

    private String buildPageSnippet(String pageSource) {
        if (pageSource == null || pageSource.isBlank()) {
            return "";
        }
        String normalized = pageSource.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 300) {
            return normalized;
        }
        return normalized.substring(0, 300) + "...";
    }

    private String getBodyText(WebDriver driver) {
        try {
            List<WebElement> bodies = driver.findElements(By.tagName("body"));
            if (bodies.isEmpty()) {
                return "";
            }
            return sanitizeText(bodies.get(0).getText());
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private void logDebug(String message) {
        String line = "[%s] %s%n".formatted(Instant.now(), message);
        System.out.print(line);
        try {
            Files.writeString(
                    DEBUG_LOG_PATH,
                    line,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (Exception ignored) {
        }
    }

    private void logSampleRows(WebDriver driver, String handle) {
        List<WebElement> rows = findSubmissionRows(driver);
        int sampleCount = Math.min(rows.size(), 5);
        for (int index = 0; index < sampleCount; index++) {
            List<WebElement> cells = rows.get(index).findElements(By.tagName("td"));
            List<String> cellTexts = new ArrayList<>();
            for (WebElement cell : cells) {
                cellTexts.add(sanitizeText(cell.getText()));
            }
            logDebug("Row sample handle=%s row=%d cells=%s".formatted(handle, index, cellTexts));
        }
    }

    public record CrawlReport(
            String handle,
            int checkedAcceptedCount,
            int existingCount,
            int sourceUnavailableCount,
            int missingSourceElementCount,
            int blockedCount,
            int insertedCount,
            List<Long> sourceUnavailableSubmissionIds
    ) {
        public String toDebugString() {
            return "handle=%s checkedAccepted=%d existing=%d sourceUnavailable=%d missingSourceElement=%d blocked=%d inserted=%d sourceUnavailableIds=%s"
                    .formatted(handle, checkedAcceptedCount, existingCount, sourceUnavailableCount,
                            missingSourceElementCount, blockedCount, insertedCount, sourceUnavailableSubmissionIds);
        }
    }

    private record SubmissionSnapshot(
            Integer contestId,
            Long submissionId,
            String problemIndex,
            String problemName,
            String programmingLanguage,
            String verdict,
            Timestamp submittedAt,
            String submissionUrl
    ) {
    }

    private record SourceFetchResult(String sourceCode, int statusCode, String errorMessage) {

        private boolean blockedByCloudflare() {
            return statusCode == 403
                    && errorMessage != null
                    && (errorMessage.toLowerCase(Locale.ROOT).contains("just a moment")
                    || errorMessage.toLowerCase(Locale.ROOT).contains("captcha")
                    || errorMessage.toLowerCase(Locale.ROOT).contains("checking your browser")
                    || errorMessage.toLowerCase(Locale.ROOT).contains("browser is being checked"));
        }

        private boolean permissionDenied() {
            String lowerMessage = errorMessage == null ? "" : errorMessage.toLowerCase(Locale.ROOT);
            return lowerMessage.contains("you can only include sources for your own submissions")
                    || lowerMessage.contains("not allowed")
                    || lowerMessage.contains("permission")
                    || lowerMessage.contains("you are not allowed to view");
        }

        private boolean sourceUnavailable() {
            String lowerMessage = errorMessage == null ? "" : errorMessage.toLowerCase(Locale.ROOT);
            return statusCode == 204
                    || lowerMessage.contains("source n/a")
                    || lowerMessage.matches(".*\\bsource\\s+n/a\\b.*");
        }

        private String shortErrorMessage() {
            if (errorMessage == null || errorMessage.isBlank()) {
                return "";
            }
            String normalized = errorMessage.replaceAll("\\s+", " ").trim();
            if (normalized.length() <= 300) {
                return normalized;
            }
            return normalized.substring(0, 300) + "...";
        }
    }
}
