package com.project.ui;

import com.project.scheduler.CrawlScheduler;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;

public class MainFrame extends JFrame {

    private static final String CARD_USER = "user";
    private static final String CARD_DASHBOARD = "dashboard";
    private static final String CARD_SETTINGS = "settings";

    private final CardLayout cardLayout;
    private final JPanel contentPanel;
    private final UserPanel userPanel;
    private final DashboardPanel dashboardPanel;
    private final ConfigPanel configPanel;
    private final CrawlScheduler crawlScheduler;

    public MainFrame() {
        super("AlgoProfiler");
        this.cardLayout = new CardLayout();
        this.contentPanel = new JPanel(cardLayout);
        this.crawlScheduler = new CrawlScheduler(false);
        this.userPanel = new UserPanel(this::refreshDashboard);
        this.dashboardPanel = new DashboardPanel(crawlScheduler, this::refreshDashboard);
        this.configPanel = new ConfigPanel();

        initializeFrame();
        initializeLayout();
        refreshDashboard();
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

        contentPanel.add(userPanel, CARD_USER);
        contentPanel.add(dashboardPanel, CARD_DASHBOARD);
        contentPanel.add(configPanel, CARD_SETTINGS);
        add(contentPanel, BorderLayout.CENTER);
    }

    private JPanel createMenuPanel() {
        JPanel menuPanel = new JPanel(new GridLayout(0, 1, 0, 12));
        menuPanel.setPreferredSize(new Dimension(220, 0));
        menuPanel.setBorder(BorderFactory.createEmptyBorder(24, 16, 24, 16));
        menuPanel.setBackground(new Color(245, 247, 250));

        JButton userButton = createMenuButton("Quản lý Nick", CARD_USER);
        JButton dashboardButton = createMenuButton("Dashboard Đánh Giá", CARD_DASHBOARD);
        JButton settingsButton = createMenuButton("Cài đặt hệ thống", CARD_SETTINGS);

        menuPanel.add(userButton);
        menuPanel.add(dashboardButton);
        menuPanel.add(settingsButton);
        return menuPanel;
    }

    private JButton createMenuButton(String text, String cardName) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.addActionListener(event -> {
            if (CARD_DASHBOARD.equals(cardName)) {
                refreshDashboard();
            }
            cardLayout.show(contentPanel, cardName);
        });
        return button;
    }

    private void refreshDashboard() {
        dashboardPanel.refreshData();
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

    @Override
    public void dispose() {
        crawlScheduler.stop();
        super.dispose();
    }
}
