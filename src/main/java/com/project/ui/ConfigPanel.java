package com.project.ui;

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
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
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
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(new JLabel("AI API Key:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(apiKeyField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        formPanel.add(new JLabel("AI API URL:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(apiUrlField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Profile crawler:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        profilePathLabel.setText(CodeforcesBrowserSession.getProfileDirectoryDescription());
        formPanel.add(profilePathLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Crawl định kỳ:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0;
        formPanel.add(crawlScheduleEnabledCheckBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Giờ crawl mỗi ngày:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0;
        formPanel.add(crawlDailyTimeField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Số code/handle/lần:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0;
        formPanel.add(crawlLimitSpinner, gbc);

        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Trạng thái lịch:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(scheduleStatusLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 7;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Xác minh Codeforces:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0;
        JButton verifyBrowserButton = new JButton("Mở Chrome xác minh");
        verifyBrowserButton.addActionListener(event -> openVerificationBrowser());
        formPanel.add(verifyBrowserButton, gbc);

        gbc.gridx = 1;
        gbc.gridy = 8;
        JButton saveButton = new JButton("Lưu cấu hình");
        saveButton.addActionListener(event -> saveConfig());
        formPanel.add(saveButton, gbc);

        gbc.gridx = 1;
        gbc.gridy = 9;
        JButton runNowButton = new JButton("Chạy crawl định kỳ ngay");
        runNowButton.addActionListener(event -> runScheduledCrawlNow());
        formPanel.add(runNowButton, gbc);

        add(formPanel, BorderLayout.NORTH);
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
