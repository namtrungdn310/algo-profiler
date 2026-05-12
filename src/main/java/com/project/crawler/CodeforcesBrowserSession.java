package com.project.crawler;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WindowType;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CodeforcesBrowserSession {

    private static final Path PROFILE_DIRECTORY = Path.of(".browser-profile", "chrome-user-data")
            .toAbsolutePath()
            .normalize();
    private static final int DEBUG_PORT = 9222;
    private static final AtomicBoolean DRIVER_IN_USE = new AtomicBoolean(false);
    private static final Map<WebDriver, DriverState> DRIVER_STATES = Collections.synchronizedMap(new IdentityHashMap<>());

    private CodeforcesBrowserSession() {
    }

    public static WebDriver createCrawlerDriver(boolean headless) {
        reserveCrawlerDriver();
        try {
            ensureProfileDirectory();
            setupChromeDriver();
            System.setProperty("webdriver.chrome.silentOutput", "true");
            System.setProperty("webdriver.http.factory", "jdk-http-client");

            ChromeOptions options = new ChromeOptions();
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-blink-features=AutomationControlled");
            options.addArguments("--lang=en-US");
            options.addArguments("--log-level=3");
            options.addArguments("--silent");

            boolean attachToManualBrowser = isManualBrowserRunning();
            if (attachToManualBrowser) {
                options.setExperimentalOption("debuggerAddress", "127.0.0.1:" + DEBUG_PORT);
            } else {
                if (headless) {
                    options.addArguments("--headless=new");
                }
                options.addArguments("--user-data-dir=" + PROFILE_DIRECTORY);
                options.addArguments("--profile-directory=Default");
                options.setExperimentalOption("excludeSwitches", List.of("enable-automation", "enable-logging"));
            }

            ChromeDriver driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));
            driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(60));
            String crawlerWindowHandle = null;
            if (attachToManualBrowser) {
                driver.switchTo().newWindow(WindowType.TAB);
                crawlerWindowHandle = driver.getWindowHandle();
            }
            DRIVER_STATES.put(driver, new DriverState(attachToManualBrowser, crawlerWindowHandle));
            return driver;
        } catch (RuntimeException exception) {
            DRIVER_IN_USE.set(false);
            throw exception;
        }
    }

    public static void releaseDriver(WebDriver driver) {
        try {
            if (driver != null) {
                DriverState state = DRIVER_STATES.remove(driver);
                if (state != null && state.attachedToManualBrowser()) {
                    closeCrawlerTabOnly(driver, state);
                } else {
                    driver.quit();
                }
            }
        } finally {
            DRIVER_IN_USE.set(false);
        }
    }

    public static void openManualLoginBrowser() {
        ensureProfileDirectory();
        if (isManualBrowserRunning()) {
            return;
        }

        Path chromeExecutable = findChromeExecutable();
        ProcessBuilder processBuilder = new ProcessBuilder(
                chromeExecutable.toString(),
                "--remote-debugging-port=" + DEBUG_PORT,
                "--user-data-dir=" + PROFILE_DIRECTORY,
                "--profile-directory=Default",
                "--disable-blink-features=AutomationControlled",
                "--window-size=1400,900",
                "https://codeforces.com/enter?back=%2F"
        );
        processBuilder.redirectErrorStream(true);

        try {
            processBuilder.start();
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể mở Chrome xác minh: " + exception.getMessage(), exception);
        }
    }

    public static boolean isManualBrowserRunning() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", DEBUG_PORT), 500);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    public static String getProfileDirectoryDescription() {
        return PROFILE_DIRECTORY.toString();
    }

    private static void ensureProfileDirectory() {
        try {
            Files.createDirectories(PROFILE_DIRECTORY);
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể tạo thư mục profile Chrome: " + PROFILE_DIRECTORY, exception);
        }
    }

    private static void reserveCrawlerDriver() {
        if (!DRIVER_IN_USE.compareAndSet(false, true)) {
            throw new IllegalStateException("""
                    Một phiên crawl khác đang chạy.
                    Hãy đợi tác vụ hiện tại kết thúc rồi thử lại.
                    """.trim());
        }
    }

    private static void setupChromeDriver() {
        try {
            WebDriverManager.chromedriver().setup();
        } catch (Throwable throwable) {
            System.err.println("WebDriverManager không khởi tạo được ChromeDriver, dùng Selenium Manager fallback: "
                    + throwable.getMessage());
        }
    }

    private static void closeCrawlerTabOnly(WebDriver driver, DriverState state) {
        try {
            if (state.crawlerWindowHandle() == null || driver.getWindowHandles().size() <= 1) {
                return;
            }
            driver.switchTo().window(state.crawlerWindowHandle());
            driver.close();
        } catch (RuntimeException ignored) {
            // The manually opened Chrome must stay alive even if tab cleanup fails.
        }
    }

    private static Path findChromeExecutable() {
        List<String> roots = List.of(
                System.getenv("ProgramFiles"),
                System.getenv("ProgramFiles(x86)"),
                System.getenv("LocalAppData")
        );

        for (String root : roots) {
            if (root == null || root.isBlank()) {
                continue;
            }
            Path candidate = Path.of(root, "Google", "Chrome", "Application", "chrome.exe");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException("""
                Không tìm thấy chrome.exe trên máy.
                Hãy cài Google Chrome hoặc chỉnh code để trỏ đúng đường dẫn trình duyệt.
                """.trim());
    }

    private record DriverState(boolean attachedToManualBrowser, String crawlerWindowHandle) {
    }
}
