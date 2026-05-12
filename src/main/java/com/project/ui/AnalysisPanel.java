package com.project.ui;

import com.project.ai.AIAnalyzer;
import com.project.dao.AnalysisDAO;
import com.project.dao.SubmissionDAO;
import com.project.dao.UserDAO;
import com.project.model.Analysis;
import com.project.model.Submission;
import com.project.model.User;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AnalysisPanel extends JPanel {

    private final UserDAO userDAO;
    private final SubmissionDAO submissionDAO;
    private final AnalysisDAO analysisDAO;
    private final AIAnalyzer aiAnalyzer;
    private final Runnable onDataChanged;
    private final JComboBox<UserItem> userComboBox;
    private final DefaultTableModel tableModel;
    private final JTable analysisTable;
    private final JTextArea detailArea;
    private final List<Submission> currentSubmissions;

    public AnalysisPanel(Runnable onDataChanged) {
        this.userDAO = new UserDAO();
        this.submissionDAO = new SubmissionDAO();
        this.analysisDAO = new AnalysisDAO();
        this.aiAnalyzer = new AIAnalyzer();
        this.onDataChanged = onDataChanged;
        this.userComboBox = new JComboBox<>();
        this.tableModel = new DefaultTableModel(
                new Object[]{"Submission ID", "Contest ID", "Bài", "Trạng thái", "AI %", "Thuật toán"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        this.analysisTable = new JTable(tableModel);
        this.detailArea = new JTextArea();
        this.currentSubmissions = new ArrayList<>();
        initializeLayout();
        refreshUsers();
    }

    private void initializeLayout() {
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        JButton filterButton = new JButton("Lọc");
        JButton analyzeButton = new JButton("Phân tích code đã chọn");
        filterButton.addActionListener(event -> filterSubmissions());
        analyzeButton.addActionListener(event -> analyzeSelectedSubmission());

        filterPanel.add(new JLabel("Handle:"));
        filterPanel.add(userComboBox);
        filterPanel.add(filterButton);
        filterPanel.add(analyzeButton);

        detailArea.setEditable(false);
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        analysisTable.setRowHeight(24);
        analysisTable.getSelectionModel().addListSelectionListener(event -> showSelectedAnalysis());

        JPanel centerPanel = new JPanel(new BorderLayout(8, 8));
        centerPanel.add(new JScrollPane(analysisTable), BorderLayout.CENTER);
        centerPanel.add(new JScrollPane(detailArea), BorderLayout.SOUTH);

        add(filterPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
    }

    public void refreshUsers() {
        try {
            userComboBox.removeAllItems();
            for (User user : userDAO.findAll()) {
                userComboBox.addItem(new UserItem(user.getId(), user.getHandle()));
            }
        } catch (SQLException exception) {
            showError("Không thể tải handle", exception);
        }
    }

    private void filterSubmissions() {
        UserItem selectedUser = (UserItem) userComboBox.getSelectedItem();
        if (selectedUser == null) {
            JOptionPane.showMessageDialog(this, "Chưa có handle trong hệ thống.", "Thiếu dữ liệu",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            loadSubmissions(selectedUser.userId());
            if (currentSubmissions.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Handle này chưa có source code đã crawl. Hãy sang giao diện Source Code để crawl trước.",
                        "Chưa có dữ liệu",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (SQLException exception) {
            showError("Không thể tải dữ liệu phân tích", exception);
        }
    }

    private void analyzeSelectedSubmission() {
        int row = analysisTable.getSelectedRow();
        if (row < 0 || row >= currentSubmissions.size()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một source code để phân tích.", "Chưa chọn dữ liệu",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        Submission submission = currentSubmissions.get(row);
        SwingWorker<Analysis, Void> worker = new SwingWorker<>() {
            @Override
            protected Analysis doInBackground() throws Exception {
                Optional<Analysis> existing = analysisDAO.findBySubmissionId(submission.getId());
                if (existing.isPresent()) {
                    return existing.get();
                }
                return aiAnalyzer.analyzeAndStore(submission);
            }

            @Override
            protected void done() {
                try {
                    get();
                    UserItem selectedUser = (UserItem) userComboBox.getSelectedItem();
                    if (selectedUser != null) {
                        loadSubmissions(selectedUser.userId());
                    }
                    notifyDataChanged();
                    JOptionPane.showMessageDialog(AnalysisPanel.this,
                            "Đã phân tích submission #" + submission.getSubmissionId(),
                            "Hoàn tất",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception exception) {
                    JOptionPane.showMessageDialog(AnalysisPanel.this,
                            "Phân tích thất bại:\n" + exception.getMessage(),
                            "Lỗi AI",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void loadSubmissions(Long userId) throws SQLException {
        currentSubmissions.clear();
        currentSubmissions.addAll(submissionDAO.findByUserId(userId));
        tableModel.setRowCount(0);
        for (Submission submission : currentSubmissions) {
            Optional<Analysis> analysis = analysisDAO.findBySubmissionId(submission.getId());
            tableModel.addRow(new Object[]{
                    submission.getSubmissionId(),
                    submission.getContestId(),
                    submission.getProblemIndex() + " - " + submission.getProblemName(),
                    analysis.isPresent() ? "Đã phân tích" : "Chưa phân tích",
                    analysis.map(value -> String.format("%.1f", value.getAiUsageScore())).orElse("-"),
                    analysis.map(Analysis::getAlgorithms).orElse("-")
            });
        }
        detailArea.setText("");
    }

    private void showSelectedAnalysis() {
        int row = analysisTable.getSelectedRow();
        if (row < 0 || row >= currentSubmissions.size()
                || analysisTable.getSelectionModel().getValueIsAdjusting()) {
            return;
        }

        Submission submission = currentSubmissions.get(row);
        try {
            Optional<Analysis> analysis = analysisDAO.findBySubmissionId(submission.getId());
            if (analysis.isEmpty()) {
                detailArea.setText("Submission này chưa được phân tích.");
                return;
            }
            Analysis value = analysis.get();
            detailArea.setText("""
                    Submission #%d
                    CTDL: %s
                    Thuật toán: %s
                    Khả năng dùng AI: %.1f%%
                    Nhãn: %s

                    Nhận xét:
                    %s
                    """.formatted(
                    submission.getSubmissionId(),
                    value.getDataStructures(),
                    value.getAlgorithms(),
                    value.getAiUsageScore(),
                    value.getAiUsageLabel(),
                    value.getSummary()
            ));
            detailArea.setCaretPosition(0);
        } catch (SQLException exception) {
            showError("Không thể đọc kết quả phân tích", exception);
        }
    }

    private void notifyDataChanged() {
        if (onDataChanged != null) {
            onDataChanged.run();
        }
    }

    private void showError(String message, Exception exception) {
        JOptionPane.showMessageDialog(this,
                message + ":\n" + exception.getMessage(),
                "Lỗi",
                JOptionPane.ERROR_MESSAGE);
    }

    private record UserItem(Long userId, String handle) {
        @Override
        public String toString() {
            return handle;
        }
    }
}
