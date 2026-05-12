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
import javax.swing.JProgressBar;
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
    private final JButton analyzeButton;
    private final JProgressBar progressBar;
    private final JLabel statusLabel;

    public AnalysisPanel(Runnable onDataChanged) {
        this.userDAO = new UserDAO();
        this.submissionDAO = new SubmissionDAO();
        this.analysisDAO = new AnalysisDAO();
        this.aiAnalyzer = new AIAnalyzer();
        this.onDataChanged = onDataChanged;
        this.userComboBox = new JComboBox<>();
        this.tableModel = new DefaultTableModel(
                new Object[]{"STT", "Submission ID", "Bài", "Trạng thái", "AI %", "CTDL", "Thuật toán"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        this.analysisTable = new JTable(tableModel);
        this.detailArea = new JTextArea();
        this.currentSubmissions = new ArrayList<>();
        this.analyzeButton = new JButton("Phân tích code đã chọn");
        this.progressBar = new JProgressBar(0, 100);
        this.statusLabel = new JLabel("Sẵn sàng.");
        
        analysisTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        initializeLayout();
        configureTableColumns();
        refreshUsers();
    }

    private void configureTableColumns() {
        if (analysisTable.getColumnCount() >= 7) {
            analysisTable.getColumnModel().getColumn(0).setPreferredWidth(40);
            analysisTable.getColumnModel().getColumn(1).setPreferredWidth(90);
            analysisTable.getColumnModel().getColumn(2).setPreferredWidth(160);
            analysisTable.getColumnModel().getColumn(3).setPreferredWidth(90);
            analysisTable.getColumnModel().getColumn(4).setPreferredWidth(50);
            analysisTable.getColumnModel().getColumn(5).setPreferredWidth(200);
            analysisTable.getColumnModel().getColumn(6).setPreferredWidth(250);
        }
    }

    private void initializeLayout() {
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        JButton filterButton = new JButton("Lọc");
        filterButton.addActionListener(event -> filterSubmissions());
        analyzeButton.addActionListener(event -> analyzeSelectedSubmission());

        filterPanel.add(new JLabel("Handle:"));
        filterPanel.add(userComboBox);
        filterPanel.add(filterButton);
        filterPanel.add(analyzeButton);


        progressBar.setStringPainted(true);
        progressBar.setString("");
        progressBar.setVisible(false);

        JPanel progressPanel = new JPanel(new BorderLayout(8, 4));
        progressPanel.add(progressBar, BorderLayout.CENTER);
        progressPanel.add(statusLabel, BorderLayout.SOUTH);

        JPanel topPanel = new JPanel(new BorderLayout(0, 6));
        topPanel.add(filterPanel, BorderLayout.NORTH);
        topPanel.add(progressPanel, BorderLayout.SOUTH);

        detailArea.setEditable(false);
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        analysisTable.setRowHeight(24);
        analysisTable.getSelectionModel().addListSelectionListener(event -> showSelectedAnalysis());

        JPanel centerPanel = new JPanel(new BorderLayout(8, 8));
        centerPanel.add(new JScrollPane(analysisTable), BorderLayout.CENTER);
        centerPanel.add(new JScrollPane(detailArea), BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);
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
            } else {
                statusLabel.setText("Đã tải " + currentSubmissions.size() + " submission. Chọn một dòng rồi nhấn Phân tích.");
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


        try {
            Optional<Analysis> existing = analysisDAO.findBySubmissionId(submission.getId());
            if (existing.isPresent()) {
                showSelectedAnalysis();
                statusLabel.setText("Submission #" + submission.getSubmissionId() + " đã được phân tích trước đó.");
                return;
            }
        } catch (SQLException ignored) {}


        setAnalyzing(true, "Đang khởi tạo kết nối tới Gemini AI...");

        SwingWorker<Analysis, Integer> worker = new SwingWorker<>() {
            @Override
            protected Analysis doInBackground() throws Exception {
                Thread progressThread = new Thread(() -> {
                    try {
                        int p = 10;
                        while (p < 95 && !isDone()) {
                            Thread.sleep(800 + (int)(Math.random() * 500));
                            p += (int)(Math.random() * 3 + 1);
                            publish(Math.min(p, 95));
                        }
                    } catch (InterruptedException ignored) {}
                });
                progressThread.start();


                Analysis result = aiAnalyzer.analyzeAndStore(submission);
                
                progressThread.interrupt();
                return result;
            }

            @Override
            protected void process(List<Integer> chunks) {
                if (!chunks.isEmpty()) {
                    int val = chunks.get(chunks.size() - 1);
                    progressBar.setValue(val);
                    progressBar.setString(val + "% - Đang xử lý dữ liệu...");
                    statusLabel.setText("Đang chờ AI phản hồi... (" + val + "%)");
                }
            }

            @Override
            protected void done() {
                try {
                    get();
                    progressBar.setValue(100);
                    progressBar.setString("100% - Hoàn tất!");
                    UserItem selectedUser = (UserItem) userComboBox.getSelectedItem();
                    if (selectedUser != null) {
                        loadSubmissions(selectedUser.userId());
                    }
                    notifyDataChanged();
                    statusLabel.setText("✓ Phân tích hoàn tất thành công.");
                    showSelectedAnalysis();
                } catch (Exception exception) {
                    statusLabel.setText("✗ Lỗi: " + exception.getMessage());
                    JOptionPane.showMessageDialog(AnalysisPanel.this,
                            "Phân tích thất bại:\n" + exception.getMessage(),
                            "Lỗi AI",
                            JOptionPane.ERROR_MESSAGE);
                } finally {
                    setAnalyzing(false, null);
                }
            }
        };
        worker.execute();
    }

    private void setAnalyzing(boolean analyzing, String message) {
        analyzeButton.setEnabled(!analyzing);
        progressBar.setVisible(analyzing);
        if (analyzing) {
            progressBar.setValue(10);
            progressBar.setString("10%");
            analyzeButton.setText("Đang phân tích...");
            statusLabel.setText(message != null ? message : "Đang xử lý...");
        } else {
            analyzeButton.setText("Phân tích code đã chọn");
        }
    }

    private void loadSubmissions(Long userId) throws SQLException {
        currentSubmissions.clear();
        currentSubmissions.addAll(submissionDAO.findByUserId(userId));
        tableModel.setRowCount(0);
        int stt = currentSubmissions.size();
        for (Submission submission : currentSubmissions) {
            Optional<Analysis> analysis = analysisDAO.findBySubmissionId(submission.getId());
            tableModel.addRow(new Object[]{
                    stt--,
                    submission.getSubmissionId(),
                    submission.getProblemIndex() + " - " + submission.getProblemName(),
                    analysis.isPresent() ? "Đã phân tích" : "Chưa phân tích",
                    analysis.map(value -> String.format("%.1f", value.getAiUsageScore())).orElse("-"),
                    analysis.map(Analysis::getDataStructures).orElse("-"),
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
