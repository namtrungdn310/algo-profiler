package com.project.ui;

import com.project.crawler.CodeforcesCrawler;
import com.project.crawler.CodeforcesCrawler.CrawlReport;
import com.project.dao.UserDAO;
import com.project.model.User;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.sql.SQLException;
import java.util.List;

public class UserPanel extends JPanel {

    private final UserDAO userDAO;
    private final Runnable onUserChanged;
    private final JTextField handleField;
    private final DefaultTableModel tableModel;
    private final JTable userTable;
    private final JButton crawlNowButton;
    private final JSpinner crawlLimitSpinner;

    public UserPanel(Runnable onUserChanged) {
        this.userDAO = new UserDAO();
        this.onUserChanged = onUserChanged;
        this.handleField = new JTextField(24);
        this.tableModel = new DefaultTableModel(
                new Object[]{"ID", "Nick Codeforces", "Tên hiển thị", "Rating", "Lần crawl gần nhất"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        this.userTable = new JTable(tableModel);
        this.crawlNowButton = new JButton("Crawl source mới");
        this.crawlLimitSpinner = new JSpinner(new SpinnerNumberModel(20, 1, 200, 1));

        initializeLayout();
        loadUsers();
    }

    private void initializeLayout() {
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel formPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        JLabel label = new JLabel("Nick Codeforces:");
        label.setHorizontalAlignment(SwingConstants.LEFT);
        JButton addButton = new JButton("Thêm Nick");
        crawlNowButton.addActionListener(event -> crawlSelectedUser());
        addButton.addActionListener(event -> addUser());
        handleField.addActionListener(event -> addUser());

        formPanel.add(label);
        formPanel.add(handleField);
        formPanel.add(addButton);
        formPanel.add(new JLabel("Tối đa bài mới:"));
        formPanel.add(crawlLimitSpinner);
        formPanel.add(crawlNowButton);

        userTable.setRowHeight(24);
        JScrollPane scrollPane = new JScrollPane(userTable);

        add(formPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(new JLabel("Crawl source code của các bài Accepted chưa có trong DB. Mỗi lần chỉ lấy tối đa số bài mới đã chọn."),
                BorderLayout.SOUTH);
    }

    private void addUser() {
        String handle = handleField.getText().trim();
        if (handle.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập nick Codeforces.", "Thiếu dữ liệu",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            if (userDAO.existsByHandle(handle)) {
                JOptionPane.showMessageDialog(this, "Nick này đã có trong hệ thống.", "Trùng dữ liệu",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            User user = new User();
            user.setHandle(handle);
            user.setDisplayName(handle);
            userDAO.insert(user);

            handleField.setText("");
            loadUsers();
            if (onUserChanged != null) {
                onUserChanged.run();
            }

            JOptionPane.showMessageDialog(this, "Đã thêm nick thành công.", "Thành công",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException exception) {
            JOptionPane.showMessageDialog(this, "Không thể lưu nick vào cơ sở dữ liệu:\n" + exception.getMessage(),
                    "Lỗi cơ sở dữ liệu", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void crawlSelectedUser() {
        String handle = resolveHandleForCrawl();
        if (handle == null || handle.isBlank()) {
            JOptionPane.showMessageDialog(this, "Hãy chọn một dòng trong bảng hoặc nhập handle để crawl.",
                    "Chưa chọn nick", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int maxNewSubmissions = (Integer) crawlLimitSpinner.getValue();
        crawlNowButton.setEnabled(false);
        SwingWorker<CrawlReport, Void> worker = new SwingWorker<>() {
            @Override
            protected CrawlReport doInBackground() throws Exception {
                CodeforcesCrawler crawler = new CodeforcesCrawler(false);
                return crawler.crawlDetailed(handle, maxNewSubmissions);
            }

            @Override
            protected void done() {
                crawlNowButton.setEnabled(true);
                try {
                    CrawlReport report = get();
                    loadUsers();
                    if (onUserChanged != null) {
                        onUserChanged.run();
                    }
                    JOptionPane.showMessageDialog(UserPanel.this,
                            "Đã crawl xong source code cho nick " + handle
                                    + ".\nGiới hạn mỗi lần: " + maxNewSubmissions
                                    + "\nAccepted đã kiểm tra: " + report.checkedAcceptedCount()
                                    + "\nĐã có sẵn trong DB: " + report.existingCount()
                                    + "\nSource = N/A / không public: " + report.sourceUnavailableCount()
                                    + "\nKhông đọc được DOM source: " + report.missingSourceElementCount()
                                    + "\nSubmission mới lưu vào DB: " + report.insertedCount()
                                    + (report.sourceUnavailableSubmissionIds().isEmpty()
                                        ? ""
                                        : "\nVí dụ submission N/A: " + report.sourceUnavailableSubmissionIds()),
                            "Crawl hoàn tất", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception exception) {
                    JOptionPane.showMessageDialog(UserPanel.this,
                            "Crawl thất bại:\n" + exception.getMessage(),
                            "Lỗi crawler", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private String resolveHandleForCrawl() {
        int selectedRow = userTable.getSelectedRow();
        if (selectedRow >= 0) {
            Object handleValue = tableModel.getValueAt(selectedRow, 1);
            if (handleValue != null) {
                return handleValue.toString().trim();
            }
        }

        String typedHandle = handleField.getText().trim();
        return typedHandle.isBlank() ? null : typedHandle;
    }

    private void loadUsers() {
        try {
            List<User> users = userDAO.findAll();
            tableModel.setRowCount(0);
            for (User user : users) {
                tableModel.addRow(new Object[]{
                        user.getId(),
                        user.getHandle(),
                        user.getDisplayName(),
                        user.getRating(),
                        user.getLastCrawledAt()
                });
            }
        } catch (SQLException exception) {
            JOptionPane.showMessageDialog(this, "Không thể tải danh sách nick:\n" + exception.getMessage(),
                    "Lỗi cơ sở dữ liệu", JOptionPane.ERROR_MESSAGE);
        }
    }
}
