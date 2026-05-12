package com.project.ui;

import com.project.dao.UserDAO;
import com.project.model.User;
import com.project.service.CodeforcesUserService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class AccountPanel extends JPanel {

    private final UserDAO userDAO;
    private final CodeforcesUserService codeforcesUserService;
    private final Runnable onDataChanged;
    private final JTextField handleField;
    private final DefaultTableModel tableModel;
    private final JTable accountTable;
    private boolean loading;

    public AccountPanel(Runnable onDataChanged) {
        this.userDAO = new UserDAO();
        this.codeforcesUserService = new CodeforcesUserService();
        this.onDataChanged = onDataChanged;
        this.handleField = new JTextField(24);
        this.tableModel = new DefaultTableModel(
                new Object[]{"ID", "Handle", "Rating", "Max Rating", "Rank", "Crawl định kỳ", "Lần crawl gần nhất"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 5;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 5 ? Boolean.class : Object.class;
            }
        };
        this.accountTable = new JTable(tableModel);
        initializeLayout();
        loadUsers();
    }

    private void initializeLayout() {
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel formPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        JLabel label = new JLabel("Handle Codeforces:");
        label.setHorizontalAlignment(SwingConstants.LEFT);
        JButton verifyButton = new JButton("Kiểm tra & cập nhật");
        JButton deleteButton = new JButton("Xóa handle");
        verifyButton.addActionListener(event -> verifyAndSaveHandle());
        deleteButton.addActionListener(event -> deleteSelectedHandle());
        handleField.addActionListener(event -> verifyAndSaveHandle());

        formPanel.add(label);
        formPanel.add(handleField);
        formPanel.add(verifyButton);
        formPanel.add(deleteButton);

        accountTable.setRowHeight(24);
        tableModel.addTableModelListener(event -> {
            if (!loading && event.getType() == TableModelEvent.UPDATE && event.getColumn() == 5) {
                updateCrawlEnabled(event.getFirstRow());
            }
        });

        add(formPanel, BorderLayout.NORTH);
        add(new JScrollPane(accountTable), BorderLayout.CENTER);
    }

    public void refreshData() {
        loadUsers();
    }

    private void verifyAndSaveHandle() {
        String handle = handleField.getText().trim();
        if (handle.isBlank()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập handle Codeforces.", "Thiếu dữ liệu",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        SwingWorker<User, Void> worker = new SwingWorker<>() {
            @Override
            protected User doInBackground() throws Exception {
                Optional<User> profile = codeforcesUserService.fetchUserProfile(handle);
                return profile.orElseThrow(() -> new IllegalArgumentException("Không tồn tại tài khoản Codeforces: " + handle));
            }

            @Override
            protected void done() {
                try {
                    User profile = get();
                    saveOrUpdate(profile);
                    handleField.setText("");
                    loadUsers();
                    notifyDataChanged();
                    JOptionPane.showMessageDialog(AccountPanel.this,
                            "Đã cập nhật handle " + profile.getHandle() + " vào hệ thống.",
                            "Thành công",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception exception) {
                    JOptionPane.showMessageDialog(AccountPanel.this,
                            "Không thể cập nhật handle:\n" + exception.getMessage(),
                            "Lỗi tài khoản",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void saveOrUpdate(User profile) throws SQLException {
        Optional<User> existing = userDAO.findByHandle(profile.getHandle());
        if (existing.isPresent()) {
            User current = existing.get();
            current.setDisplayName(profile.getDisplayName());
            current.setRating(profile.getRating());
            current.setMaxRating(profile.getMaxRating());
            current.setRankTitle(profile.getRankTitle());
            userDAO.updateProfile(current);
            return;
        }
        profile.setCrawlEnabled(true);
        userDAO.insert(profile);
    }

    private void loadUsers() {
        try {
            loading = true;
            List<User> users = userDAO.findAll();
            tableModel.setRowCount(0);
            for (User user : users) {
                tableModel.addRow(new Object[]{
                        user.getId(),
                        user.getHandle(),
                        user.getRating(),
                        user.getMaxRating(),
                        user.getRankTitle(),
                        user.isCrawlEnabled(),
                        user.getLastCrawledAt()
                });
            }
        } catch (SQLException exception) {
            JOptionPane.showMessageDialog(this,
                    "Không thể tải danh sách tài khoản:\n" + exception.getMessage(),
                    "Lỗi cơ sở dữ liệu",
                    JOptionPane.ERROR_MESSAGE);
        } finally {
            loading = false;
        }
    }

    private void updateCrawlEnabled(int row) {
        if (row < 0 || row >= tableModel.getRowCount()) {
            return;
        }
        try {
            Long userId = ((Number) tableModel.getValueAt(row, 0)).longValue();
            boolean enabled = Boolean.TRUE.equals(tableModel.getValueAt(row, 5));
            userDAO.updateCrawlEnabled(userId, enabled);
            notifyDataChanged();
        } catch (SQLException exception) {
            JOptionPane.showMessageDialog(this,
                    "Không thể cập nhật trạng thái crawl:\n" + exception.getMessage(),
                    "Lỗi cơ sở dữ liệu",
                    JOptionPane.ERROR_MESSAGE);
            loadUsers();
        }
    }

    private void deleteSelectedHandle() {
        int row = accountTable.getSelectedRow();
        if (row < 0 || row >= tableModel.getRowCount()) {
            JOptionPane.showMessageDialog(this,
                    "Vui lòng chọn một handle trong bảng để xóa.",
                    "Chưa chọn handle",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        Long userId = ((Number) tableModel.getValueAt(row, 0)).longValue();
        String handle = String.valueOf(tableModel.getValueAt(row, 1));
        int confirm = JOptionPane.showConfirmDialog(this,
                """
                Xóa handle %s khỏi hệ thống?
                Tất cả source code, metadata submission và kết quả phân tích của handle này cũng sẽ bị xóa.
                """.formatted(handle).trim(),
                "Xác nhận xóa",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            userDAO.deleteById(userId);
            loadUsers();
            notifyDataChanged();
            JOptionPane.showMessageDialog(this,
                    "Đã xóa handle " + handle + " và toàn bộ dữ liệu liên quan.",
                    "Đã xóa",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException exception) {
            JOptionPane.showMessageDialog(this,
                    "Không thể xóa handle:\n" + exception.getMessage(),
                    "Lỗi cơ sở dữ liệu",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void notifyDataChanged() {
        if (onDataChanged != null) {
            onDataChanged.run();
        }
    }
}
