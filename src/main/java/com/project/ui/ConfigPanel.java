package com.project.ui;

import com.project.ai.AIAnalyzer;
import com.project.config.AppConfig;
import com.project.crawler.CodeforcesBrowserSession;
import com.project.scheduler.CrawlScheduler;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ConfigPanel extends JPanel {

    private final JPasswordField apiKeyField;
    private final JTextField apiUrlField;
    private final JCheckBox crawlScheduleEnabledCheckBox;
    private final JTextField crawlDailyTimeField;
    private final JSpinner crawlLimitSpinner;
    private final JLabel profilePathLabel;
    private final JLabel scheduleStatusLabel;
    private final Timer scheduleAutoApplyTimer;
    private final Runnable onConfigSaved;
    private final Runnable onRunScheduledCrawlNow;

    public ConfigPanel() {
        this(null);
    }

    public ConfigPanel(Runnable onConfigSaved) {
        this(onConfigSaved, null);
    }

    public ConfigPanel(Runnable onConfigSaved, Runnable onRunScheduledCrawlNow) {
        this.onConfigSaved = onConfigSaved;
        this.onRunScheduledCrawlNow = onRunScheduledCrawlNow;
        this.apiKeyField = new JPasswordField(28);
        this.apiUrlField = new JTextField(28);
        this.crawlScheduleEnabledCheckBox = new JCheckBox("Bật crawl tự động hằng ngày");
        this.crawlDailyTimeField = new JTextField(8);
        this.crawlLimitSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 100, 1));
        this.profilePathLabel = new JLabel();
        this.scheduleStatusLabel = new JLabel();
        this.scheduleAutoApplyTimer = new Timer(800, event -> autoApplyScheduleConfig());
        this.scheduleAutoApplyTimer.setRepeats(false);
        initializeLayout();
        loadCurrentConfig();
        updateScheduleStatusFromConfig();
    }

    private void initializeLayout() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new javax.swing.BoxLayout(contentPanel, javax.swing.BoxLayout.Y_AXIS));

        // --- NHÓM 1: XÁC MINH (BẮT BUỘC) ---
        JPanel verifyPanel = new JPanel(new GridBagLayout());
        verifyPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(java.awt.Color.RED, 2),
                " BƯỚC 1: XÁC MINH CODEFORCES (BẮT BUỘC) ",
                0, 0, null, java.awt.Color.RED));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        JLabel warningLabel = new JLabel("<html><b>QUAN TRỌNG:</b> Bạn phải mở Chrome xác minh và đăng nhập Codeforces trước khi sử dụng!</html>");
        warningLabel.setForeground(java.awt.Color.RED);
        verifyPanel.add(warningLabel, gbc);

        gbc.gridy = 1;
        JButton verifyBrowserButton = new JButton("MỞ CHROME XÁC MINH NGAY");
        verifyBrowserButton.setBackground(new java.awt.Color(220, 53, 69));
        verifyBrowserButton.setForeground(java.awt.Color.BLACK);
        verifyBrowserButton.setFont(verifyBrowserButton.getFont().deriveFont(java.awt.Font.BOLD, 14f));
        verifyBrowserButton.setFocusPainted(false);
        verifyBrowserButton.addActionListener(event -> openVerificationBrowser());
        verifyPanel.add(verifyBrowserButton, gbc);

        gbc.gridy = 2;
        profilePathLabel.setText("Đường dẫn Profile: " + CodeforcesBrowserSession.getProfileDirectoryDescription());
        profilePathLabel.setFont(profilePathLabel.getFont().deriveFont(11f));
        verifyPanel.add(profilePathLabel, gbc);

        // --- NHÓM 2: LẬP LỊCH CRAWL ---
        JPanel schedulePanel = new JPanel(new GridBagLayout());
        schedulePanel.setBorder(BorderFactory.createTitledBorder(" BƯỚC 2: CÀI ĐẶT LỊCH CRAWL ĐỊNH KỲ "));
        GridBagConstraints gbcS = new GridBagConstraints();
        gbcS.insets = new Insets(6, 10, 6, 10);
        gbcS.anchor = GridBagConstraints.WEST;

        gbcS.gridx = 0; gbcS.gridy = 0;
        schedulePanel.add(new JLabel("Crawl tự động:"), gbcS);
        gbcS.gridx = 1;
        schedulePanel.add(crawlScheduleEnabledCheckBox, gbcS);

        gbcS.gridx = 0; gbcS.gridy = 1;
        schedulePanel.add(new JLabel("Giờ chạy mỗi ngày:"), gbcS);
        gbcS.gridx = 1;
        schedulePanel.add(crawlDailyTimeField, gbcS);

        gbcS.gridx = 0; gbcS.gridy = 2;
        schedulePanel.add(new JLabel("Số bài mới mỗi handle:"), gbcS);
        gbcS.gridx = 1;
        schedulePanel.add(crawlLimitSpinner, gbcS);

        gbcS.gridx = 0; gbcS.gridy = 3;
        schedulePanel.add(new JLabel("Trạng thái hiện tại:"), gbcS);
        gbcS.gridx = 1;
        schedulePanel.add(scheduleStatusLabel, gbcS);

        gbcS.gridx = 1; gbcS.gridy = 4;
        gbcS.fill = GridBagConstraints.HORIZONTAL;
        JPanel scheduleButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JButton saveButton = new JButton("Lưu lịch");
        saveButton.addActionListener(event -> saveConfig());
        JButton runNowButton = new JButton("Chạy thử ngay");
        runNowButton.addActionListener(event -> runScheduledCrawlNow());
        scheduleButtons.add(saveButton);
        scheduleButtons.add(new JLabel("  "));
        scheduleButtons.add(runNowButton);
        schedulePanel.add(scheduleButtons, gbcS);

        // --- NHÓM 3: CẤU HÌNH AI ---
        JPanel aiPanel = new JPanel(new GridBagLayout());
        aiPanel.setBorder(BorderFactory.createTitledBorder(" BƯỚC 3: CẤU HÌNH AI (GEMINI) "));
        GridBagConstraints gbcA = new GridBagConstraints();
        gbcA.insets = new Insets(6, 10, 6, 10);
        gbcA.anchor = GridBagConstraints.WEST;

        gbcA.gridx = 0; gbcA.gridy = 0;
        aiPanel.add(new JLabel("API Key:"), gbcA);
        gbcA.gridx = 1; gbcA.weightx = 1.0; gbcA.fill = GridBagConstraints.HORIZONTAL;
        aiPanel.add(apiKeyField, gbcA);

        gbcA.gridx = 0; gbcA.gridy = 1; gbcA.weightx = 0; gbcA.fill = GridBagConstraints.NONE;
        aiPanel.add(new JLabel("API URL:"), gbcA);
        
        gbcA.gridx = 1; gbcA.weightx = 1.0; gbcA.fill = GridBagConstraints.HORIZONTAL;
        aiPanel.add(apiUrlField, gbcA);

        gbcA.gridx = 1; gbcA.gridy = 2;
        gbcA.fill = GridBagConstraints.NONE;
        JButton testAiButton = new JButton("Kiểm tra & Xác nhận API");
        testAiButton.addActionListener(event -> testAndSaveAiConfig());
        aiPanel.add(testAiButton, gbcA);

        contentPanel.add(verifyPanel);
        contentPanel.add(javax.swing.Box.createVerticalStrut(15));
        contentPanel.add(schedulePanel);
        contentPanel.add(javax.swing.Box.createVerticalStrut(15));
        contentPanel.add(aiPanel);

        add(new JScrollPane(contentPanel), BorderLayout.CENTER);
        registerUnsavedChangeListeners();
    }

    private void loadCurrentConfig() {
        apiKeyField.setText(AppConfig.getAiApiKey());
        apiUrlField.setText(AppConfig.getAiApiUrl());
        crawlScheduleEnabledCheckBox.setSelected(AppConfig.isCrawlScheduleEnabled());
        crawlDailyTimeField.setText(AppConfig.getCrawlDailyTimeText());
        crawlLimitSpinner.setValue(AppConfig.getCrawlMaxNewPerUser());
    }

    private void saveConfig() {
        persistConfig(true);
    }

    private boolean persistConfig(boolean showSuccessDialog) {
        try {
            AppConfig.setAiApiKey(new String(apiKeyField.getPassword()));
            AppConfig.setAiApiUrl(apiUrlField.getText().trim());
            AppConfig.setCrawlSchedule(
                    crawlScheduleEnabledCheckBox.isSelected(),
                    crawlDailyTimeField.getText().trim(),
                    ((Number) crawlLimitSpinner.getValue()).intValue()
            );
            if (onConfigSaved != null) {
                onConfigSaved.run();
            }
            updateScheduleStatusFromConfig();
            if (showSuccessDialog) {
                JOptionPane.showMessageDialog(this,
                        """
                        Đã lưu config.properties thành công.
                        Lịch crawl định kỳ đã được áp dụng theo cấu hình mới.
                        """.trim(),
                        "Cấu hình", JOptionPane.INFORMATION_MESSAGE);
            }
            return true;
        } catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this,
                    exception.getMessage(),
                    "Sai định dạng cấu hình",
                    JOptionPane.WARNING_MESSAGE);
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(this,
                    "Không thể lưu config.properties:\n" + exception.getMessage(),
                    "Lỗi cấu hình", JOptionPane.ERROR_MESSAGE);
        }
        return false;
    }

    private void runScheduledCrawlNow() {
        if (onRunScheduledCrawlNow == null) {
            JOptionPane.showMessageDialog(this,
                    "Chức năng chạy thử scheduler chưa được kết nối.",
                    "Không khả dụng",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!persistConfig(false)) {
            return;
        }
        onRunScheduledCrawlNow.run();
    }

    private void updateScheduleStatusFromConfig() {
        if (!AppConfig.isCrawlScheduleEnabled()) {
            scheduleStatusLabel.setText("Chưa bật. Tick chọn và nhấn Lưu cấu hình để áp dụng.");
            return;
        }

        LocalDateTime nextRun = calculateNextRun(AppConfig.getCrawlDailyTime());
        scheduleStatusLabel.setText("Đã bật. Lần chạy kế tiếp: "
                + nextRun.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }

    private LocalDateTime calculateNextRun(LocalTime crawlTime) {
        LocalDateTime now = LocalDateTime.now();
        if (now.getHour() == crawlTime.getHour() && now.getMinute() == crawlTime.getMinute()) {
            return now.plusSeconds(3);
        }

        LocalDateTime nextRun = now.withHour(crawlTime.getHour())
                .withMinute(crawlTime.getMinute())
                .withSecond(0)
                .withNano(0);
        if (!nextRun.isAfter(now)) {
            nextRun = nextRun.plusDays(1);
        }
        return nextRun;
    }

    public void updateLastScheduledRun(CrawlScheduler.ScheduledCrawlReport report) {
        String status = "Vừa chạy lúc %s | Handle: %d | Kiểm tra: %d | Có sẵn: %d | Mới: %d"
                .formatted(
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                        report.userCount(),
                        report.checkedAcceptedCount(),
                        report.existingCount(),
                        report.insertedCount()
                );
        if (AppConfig.isCrawlScheduleEnabled()) {
            LocalDateTime nextRun = calculateNextRun(AppConfig.getCrawlDailyTime());
            status += " | Kế tiếp: " + nextRun.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        scheduleStatusLabel.setText(status);
    }

    private void registerUnsavedChangeListeners() {
        crawlScheduleEnabledCheckBox.addItemListener(event -> markScheduleConfigUnsaved());
        crawlLimitSpinner.addChangeListener(event -> markScheduleConfigUnsaved());
        crawlDailyTimeField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                markScheduleConfigUnsaved();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                markScheduleConfigUnsaved();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                markScheduleConfigUnsaved();
            }
        });
    }

    private void markScheduleConfigUnsaved() {
        scheduleStatusLabel.setText("Có thay đổi lịch. Đang tự áp dụng nếu định dạng hợp lệ...");
        scheduleAutoApplyTimer.restart();
    }

    private void autoApplyScheduleConfig() {
        try {
            AppConfig.setCrawlSchedule(
                    crawlScheduleEnabledCheckBox.isSelected(),
                    crawlDailyTimeField.getText().trim(),
                    ((Number) crawlLimitSpinner.getValue()).intValue()
            );
            if (onConfigSaved != null) {
                onConfigSaved.run();
            }
            updateScheduleStatusFromConfig();
        } catch (IllegalArgumentException exception) {
            scheduleStatusLabel.setText("Giờ crawl phải đúng định dạng HH:mm, ví dụ 13:41.");
        } catch (IOException exception) {
            scheduleStatusLabel.setText("Không thể tự lưu lịch: " + exception.getMessage());
        }
    }

    private void testAndSaveAiConfig() {
        try {
            String newKey = new String(apiKeyField.getPassword()).trim();
            String newUrl = apiUrlField.getText().trim();
            
            if (newKey.isBlank()) {
                JOptionPane.showMessageDialog(this,
                        "Vui lòng nhập API Key trước khi kiểm tra.",
                        "Thiếu thông tin", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Lưu cấu hình vào file TRƯỚC khi test để API Key không bị mất
            AppConfig.setAiApiKey(newKey);
            AppConfig.setAiApiUrl(newUrl.isBlank() ? AppConfig.getDefaultAiApiUrl() : newUrl);
            if (onConfigSaved != null) {
                onConfigSaved.run();
            }

            // Sau đó mới test kết nối
            AIAnalyzer analyzer = new AIAnalyzer();
            analyzer.testConnection();

            JOptionPane.showMessageDialog(this,
                    "Kết nối AI API thành công! Cấu hình đã được lưu.",
                    "Thành công", JOptionPane.INFORMATION_MESSAGE);
        } catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this,
                    exception.getMessage(),
                    "Thiếu thông tin", JOptionPane.WARNING_MESSAGE);
        } catch (Exception exception) {
            // API Key đã được lưu nhưng test thất bại - hiển thị lỗi nhưng giữ key
            JOptionPane.showMessageDialog(this,
                    "API Key đã được lưu nhưng kiểm tra kết nối thất bại:\n" 
                    + exception.getMessage()
                    + "\n\nURL đang dùng: " + AppConfig.getAiApiUrl(),
                    "Cảnh báo kết nối", JOptionPane.WARNING_MESSAGE);
        }
    }
    private void openVerificationBrowser() {
        try {
            CodeforcesBrowserSession.openManualLoginBrowser();
            JOptionPane.showMessageDialog(this,
                    """
                    Chrome crawler đã được mở.
                    Hãy đăng nhập Codeforces và vượt Verification/Captcha trên cửa sổ đó.
                    Sau khi đăng nhập xong, hãy GIỮ cửa sổ này mở rồi mới quay lại bấm crawl.
                    """
                            .trim(),
                    "Xác minh Codeforces",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (IllegalStateException exception) {
            JOptionPane.showMessageDialog(this,
                    exception.getMessage(),
                    "Profile đang bận",
                    JOptionPane.WARNING_MESSAGE);
        }
    }
}
