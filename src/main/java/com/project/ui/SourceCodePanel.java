package com.project.ui;

import com.project.crawler.CodeforcesCrawler;
import com.project.crawler.CodeforcesCrawler.CrawlReport;
import com.project.dao.SubmissionDAO;
import com.project.dao.UserDAO;
import com.project.model.Submission;
import com.project.model.User;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
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

public class SourceCodePanel extends JPanel {

    private static final int DEFAULT_CRAWL_LIMIT = 5;

    private final UserDAO userDAO;
    private final SubmissionDAO submissionDAO;
    private final Runnable onDataChanged;
    private final JComboBox<UserItem> userComboBox;
    private final DefaultTableModel tableModel;
    private final JTable sourceTable;
    private final List<Submission> currentSubmissions;

    public SourceCodePanel(Runnable onDataChanged) {
        this.userDAO = new UserDAO();
        this.submissionDAO = new SubmissionDAO();
        this.onDataChanged = onDataChanged;
        this.userComboBox = new JComboBox<>();
        this.tableModel = new DefaultTableModel(
                new Object[]{"", "Contest ID", "Submission ID", "Bài", "Ngôn ngữ", "Verdict", "Đã phân tích"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        this.sourceTable = new JTable(tableModel);
        this.currentSubmissions = new ArrayList<>();
        initializeLayout();
        refreshUsers();
    }

    private void initializeLayout() {
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        JButton filterButton = new JButton("Lọc");
        JButton crawlButton = new JButton("Crawl 5 code gần nhất");
        filterButton.addActionListener(event -> filterSubmissions());
        crawlButton.addActionListener(event -> crawlSelectedUser());

        filterPanel.add(new JLabel("Handle:"));
        filterPanel.add(userComboBox);
        filterPanel.add(filterButton);
        filterPanel.add(crawlButton);

        sourceTable.setRowHeight(24);
        sourceTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2 || sourceTable.columnAtPoint(event.getPoint()) == 0) {
                    showSelectedSource();
                }
            }
        });

        add(filterPanel, BorderLayout.NORTH);
        add(new JScrollPane(sourceTable), BorderLayout.CENTER);
    }

    public void refreshUsers() {
        reloadUsers(null);
    }

    public void refreshData() {
        UserItem selectedUser = (UserItem) userComboBox.getSelectedItem();
        reloadUsers(selectedUser == null ? null : selectedUser.handle());
        selectedUser = (UserItem) userComboBox.getSelectedItem();
        if (selectedUser == null) {
            currentSubmissions.clear();
            tableModel.setRowCount(0);
            return;
        }

        try {
            loadSubmissions(selectedUser.userId());
        } catch (SQLException exception) {
            showError("Không thể tải lại source code", exception);
        }
    }

    private void reloadUsers(String preferredHandle) {
        try {
            userComboBox.removeAllItems();
            UserItem preferredItem = null;
            for (User user : userDAO.findAll()) {
                UserItem item = new UserItem(user.getId(), user.getHandle());
                userComboBox.addItem(item);
                if (preferredHandle != null && preferredHandle.equalsIgnoreCase(user.getHandle())) {
                    preferredItem = item;
                }
            }
            if (preferredItem != null) {
                userComboBox.setSelectedItem(preferredItem);
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
                        "Handle này chưa có dữ liệu crawl. Vui lòng chọn nút crawl bên cạnh.",
                        "Chưa có dữ liệu",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (SQLException exception) {
            showError("Không thể lọc source code", exception);
        }
    }

    private void crawlSelectedUser() {
        UserItem selectedUser = (UserItem) userComboBox.getSelectedItem();
        if (selectedUser == null) {
            JOptionPane.showMessageDialog(this, "Chưa có handle trong hệ thống.", "Thiếu dữ liệu",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        SwingWorker<CrawlReport, Void> worker = new SwingWorker<>() {
            @Override
            protected CrawlReport doInBackground() throws Exception {
                CodeforcesCrawler crawler = new CodeforcesCrawler(false);
                return crawler.crawlDetailed(selectedUser.handle(), DEFAULT_CRAWL_LIMIT);
            }

            @Override
            protected void done() {
                try {
                    CrawlReport report = get();
                    loadSubmissions(selectedUser.userId());
                    notifyDataChanged();
                    JOptionPane.showMessageDialog(SourceCodePanel.this,
                            """
                            Đã crawl xong handle %s.
                            Accepted đã kiểm tra: %d
                            Đã có trong DB: %d
                            Source N/A / không có quyền xem: %d
                            Không đọc được DOM source: %d
                            Source mới lưu vào DB: %d
                            """.formatted(
                                    report.handle(),
                                    report.checkedAcceptedCount(),
                                    report.existingCount(),
                                    report.sourceUnavailableCount(),
                                    report.missingSourceElementCount(),
                                    report.insertedCount()
                            ).trim(),
                            "Crawl hoàn tất",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception exception) {
                    JOptionPane.showMessageDialog(SourceCodePanel.this,
                            "Crawl thất bại:\n" + exception.getMessage(),
                            "Lỗi crawler",
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
            tableModel.addRow(new Object[]{
                    ">",
                    submission.getContestId(),
                    submission.getSubmissionId(),
                    submission.getProblemIndex() + " - " + submission.getProblemName(),
                    submission.getProgrammingLanguage(),
                    submission.getVerdict(),
                    submission.isAnalyzed() ? "Có" : "Chưa"
            });
        }
    }

    private void showSelectedSource() {
        int row = sourceTable.getSelectedRow();
        if (row < 0 || row >= currentSubmissions.size()) {
            return;
        }
        Submission submission = currentSubmissions.get(row);
        JTextArea sourceArea = new JTextArea(submission.getSourceCode());
        sourceArea.setEditable(false);
        sourceArea.setCaretPosition(0);

        JDialog dialog = new JDialog();
        dialog.setTitle("Source #" + submission.getSubmissionId());
        dialog.setModal(true);
        dialog.setSize(950, 620);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());
        dialog.add(new JScrollPane(sourceArea), BorderLayout.CENTER);
        dialog.setVisible(true);
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
