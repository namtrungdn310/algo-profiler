package com.project.ui;

import com.project.config.AppConfig;
import com.project.crawler.CodeforcesBrowserSession;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;

public class ConfigPanel extends JPanel {

    private final JPasswordField apiKeyField;
    private final JTextField apiUrlField;
    private final JLabel profilePathLabel;

    public ConfigPanel() {
        this.apiKeyField = new JPasswordField(28);
        this.apiUrlField = new JTextField(28);
        this.profilePathLabel = new JLabel();
        initializeLayout();
        loadCurrentConfig();
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
        formPanel.add(new JLabel("Xác minh Codeforces:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0;
        JButton verifyBrowserButton = new JButton("Mở Chrome xác minh");
        verifyBrowserButton.addActionListener(event -> openVerificationBrowser());
        formPanel.add(verifyBrowserButton, gbc);

        gbc.gridx = 1;
        gbc.gridy = 4;
        JButton saveButton = new JButton("Lưu cấu hình");
        saveButton.addActionListener(event -> saveConfig());
        formPanel.add(saveButton, gbc);

        add(formPanel, BorderLayout.NORTH);
    }

    private void loadCurrentConfig() {
        apiKeyField.setText(AppConfig.getAiApiKey());
        apiUrlField.setText(AppConfig.getAiApiUrl());
    }

    private void saveConfig() {
        try {
            AppConfig.setAiApiKey(new String(apiKeyField.getPassword()));
            AppConfig.setAiApiUrl(apiUrlField.getText().trim());
            JOptionPane.showMessageDialog(this,
                    "Đã lưu config.properties thành công. Người dùng không cần sửa code.",
                    "Cấu hình", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(this,
                    "Không thể lưu config.properties:\n" + exception.getMessage(),
                    "Lỗi cấu hình", JOptionPane.ERROR_MESSAGE);
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
