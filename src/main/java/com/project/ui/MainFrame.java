package com.project.ui;

import com.project.config.AppConfig;
import com.project.config.DatabaseConnection;
import com.project.scheduler.CrawlScheduler;
import org.h2.tools.Server;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;

public class MainFrame extends JFrame {

    private static final String CARD_USER = "user";
    private static final String CARD_OVERVIEW = "overview";
    private static final String CARD_SOURCE = "source";
    private static final String CARD_ANALYSIS = "analysis";
    private static final String CARD_EVALUATION = "evaluation";
    private static final String CARD_SETTINGS = "settings";

    private final CardLayout cardLayout;
    private final JPanel contentPanel;
    private final OverviewPanel overviewPanel;
    private final AccountPanel accountPanel;
    private final SourceCodePanel sourceCodePanel;
    private final AnalysisPanel analysisPanel;
    private final EvaluationPanel evaluationPanel;
    private final ConfigPanel configPanel;
    private final CrawlScheduler crawlScheduler;

    public MainFrame() {
        super("AlgoProfiler");
        startDatabaseConsole();
        this.cardLayout = new CardLayout();
        this.contentPanel = new JPanel(cardLayout);
        this.crawlScheduler = new CrawlScheduler(false,
                report -> SwingUtilities.invokeLater(() -> handleScheduledCrawlFinished(report)));
        this.overviewPanel = new OverviewPanel();
        this.accountPanel = new AccountPanel(this::refreshAllData);
        this.sourceCodePanel = new SourceCodePanel(this::refreshAllData);
        this.analysisPanel = new AnalysisPanel(this::refreshAllData);
        this.evaluationPanel = new EvaluationPanel();
        this.configPanel = new ConfigPanel(this::applySchedulerConfig, this::runScheduledCrawlNow);

        initializeFrame();
        initializeLayout();
        refreshAllData();
        applySchedulerConfig();
        printBoldJdbcUrl();
    }

    private void printBoldJdbcUrl() {
        String url = DatabaseConnection.getJdbcUrl();
        System.out.println("\n" + "=".repeat(80));
        System.out.println("\u001B[1m\u001B[32m[DATABASE CONSOLE] COPY DONG DUOI DAY VAO TRINH DUYET:\u001B[0m");
        System.out.println("\u001B[1m" + url + "\u001B[0m");
        System.out.println("=".repeat(80) + "\n");
    }

    private void initializeFrame() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 700);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
    }

    private void initializeLayout() {
        setLayout(new BorderLayout());
        add(createMenuPanel(), BorderLayout.WEST);

        contentPanel.add(overviewPanel, CARD_OVERVIEW);
        contentPanel.add(accountPanel, CARD_USER);
        contentPanel.add(sourceCodePanel, CARD_SOURCE);
        contentPanel.add(analysisPanel, CARD_ANALYSIS);
        contentPanel.add(evaluationPanel, CARD_EVALUATION);
        contentPanel.add(configPanel, CARD_SETTINGS);
        add(contentPanel, BorderLayout.CENTER);
    }

    private JPanel createMenuPanel() {
        JPanel menuPanel = new JPanel(new GridLayout(0, 1, 0, 12));
        menuPanel.setPreferredSize(new Dimension(220, 0));
        menuPanel.setBorder(BorderFactory.createEmptyBorder(24, 16, 24, 16));
        menuPanel.setBackground(new Color(245, 247, 250));

        JButton overviewButton = createMenuButton("Tổng quan", CARD_OVERVIEW);
        JButton userButton = createMenuButton("Tài khoản", CARD_USER);
        JButton sourceButton = createMenuButton("Source Code", CARD_SOURCE);
        JButton analysisButton = createMenuButton("Phân tích Source", CARD_ANALYSIS);
        JButton evaluationButton = createMenuButton("Đánh giá", CARD_EVALUATION);
        JButton settingsButton = createMenuButton("Cài đặt hệ thống (Bắt buộc)", CARD_SETTINGS);
        settingsButton.setForeground(java.awt.Color.RED);
        settingsButton.setBackground(new java.awt.Color(255, 230, 230));

        menuPanel.add(overviewButton);
        menuPanel.add(userButton);
        menuPanel.add(sourceButton);
        menuPanel.add(analysisButton);
        menuPanel.add(evaluationButton);
        menuPanel.add(settingsButton);
        return menuPanel;
    }

    private JButton createMenuButton(String text, String cardName) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.addActionListener(event -> {
            refreshAllData();
            cardLayout.show(contentPanel, cardName);
        });
        return button;
    }

    private void refreshAllData() {
        overviewPanel.refreshData();
        accountPanel.refreshData();
        sourceCodePanel.refreshData();
        analysisPanel.refreshUsers();
        evaluationPanel.refreshUsers();
    }

    private void applySchedulerConfig() {
        crawlScheduler.stop();
        if (!AppConfig.isCrawlScheduleEnabled()) {
            return;
        }
        crawlScheduler.startDaily(AppConfig.getCrawlDailyTime(), AppConfig.getCrawlMaxNewPerUser());
    }

    private void handleScheduledCrawlFinished(CrawlScheduler.ScheduledCrawlReport report) {
        refreshAllData();
        configPanel.updateLastScheduledRun(report);
        
        JOptionPane.showMessageDialog(this,
                """
                [Lịch Crawl Định Kỳ] Đã hoàn thành thu thập dữ liệu tự động.
                
                - Số handle đã kiểm tra: %d
                - Tổng số bài nộp mới: %d
                - Số bài đã có sẵn: %d
                - Lỗi: %d handle
                """.formatted(
                        report.userCount(),
                        report.insertedCount(),
                        report.existingCount(),
                        report.failedUserCount()
                ).trim(),
                "Thông báo lịch crawl",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void runScheduledCrawlNow() {
        SwingWorker<CrawlScheduler.ScheduledCrawlReport, Void> worker = new SwingWorker<>() {
            @Override
            protected CrawlScheduler.ScheduledCrawlReport doInBackground() {
                return crawlScheduler.crawlAllUsersNow(AppConfig.getCrawlMaxNewPerUser());
            }

            @Override
            protected void done() {
                try {
                    CrawlScheduler.ScheduledCrawlReport report = get();
                    refreshAllData();
                    configPanel.updateLastScheduledRun(report);
                    JOptionPane.showMessageDialog(MainFrame.this,
                            report.toDisplayText(),
                            "Crawl định kỳ",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception exception) {
                    JOptionPane.showMessageDialog(MainFrame.this,
                            "Chạy crawl định kỳ thất bại:\n" + exception.getMessage(),
                            "Lỗi crawler",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            applySystemLookAndFeel();
            new MainFrame().setVisible(true);
        });
    }

    private static void applySystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
    }

    private void startDatabaseConsole() {
        try {

            Server.createWebServer("-web", "-webAllowOthers", "-webPort", "8082").start();
        } catch (Exception ignored) {

        }
    }

    @Override
    public void dispose() {
        crawlScheduler.stop();
        super.dispose();
    }
}
