package com.project.ui;

import com.project.config.AppConfig;
import com.project.dao.SubmissionDAO;
import com.project.dao.UserDAO;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.GridLayout;
import java.sql.SQLException;

public class OverviewPanel extends JPanel {

    private final UserDAO userDAO;
    private final SubmissionDAO submissionDAO;
    private final JLabel totalHandlesValue;
    private final JLabel activeHandlesValue;
    private final JLabel totalSourcesValue;
    private final JLabel tokenStatusValue;

    public OverviewPanel() {
        this.userDAO = new UserDAO();
        this.submissionDAO = new SubmissionDAO();
        this.totalHandlesValue = new JLabel("-");
        this.activeHandlesValue = new JLabel("-");
        this.totalSourcesValue = new JLabel("-");
        this.tokenStatusValue = new JLabel("-");
        initializeLayout();
    }

    private void initializeLayout() {
        setLayout(new GridLayout(2, 2, 16, 16));
        setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));

        add(createMetricPanel("Tổng số handle", totalHandlesValue));
        add(createMetricPanel("Handle crawl định kỳ", activeHandlesValue));
        add(createMetricPanel("Tổng source code", totalSourcesValue));
        add(createMetricPanel("Hạn mức AI (Gemini)", tokenStatusValue));
    }

    public void refreshData() {
        try {
            totalHandlesValue.setText(String.valueOf(userDAO.countAll()));
            activeHandlesValue.setText(String.valueOf(userDAO.countCrawlEnabled()));
            totalSourcesValue.setText(String.valueOf(submissionDAO.countAll()));
            tokenStatusValue.setText(resolveTokenStatus());
        } catch (SQLException exception) {
            JOptionPane.showMessageDialog(this,
                    "Không thể tải tổng quan hệ thống:\n" + exception.getMessage(),
                    "Lỗi cơ sở dữ liệu",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private JPanel createMetricPanel(String title, JLabel valueLabel) {
        JPanel panel = new JPanel(new GridLayout(2, 1, 4, 4));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(),
                BorderFactory.createEmptyBorder(18, 18, 18, 18)
        ));
        JLabel titleLabel = new JLabel(title);
        valueLabel.setFont(valueLabel.getFont().deriveFont(28f));
        panel.add(titleLabel);
        panel.add(valueLabel);
        return panel;
    }

    private String resolveTokenStatus() {
        if (AppConfig.getAiApiKey().isBlank()) {
            return "Chưa cấu hình API Key";
        }
        
        String model = AppConfig.getAiModel().toLowerCase();
        

        if (model.contains("2.5") || model.contains("flash")) {

            return "Free: 15 RPM | 1500 RPD";
        } else if (model.contains("pro")) {

            return "Free: 2 RPM | 50 RPD";
        }
        
        return "Gói Miễn phí (Tiêu chuẩn)";
    }
}
