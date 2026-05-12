package com.project.ui;

import com.project.dao.UserDAO;
import com.project.model.User;
import com.project.service.EvaluationService;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.sql.SQLException;
import java.util.List;

public class EvaluationPanel extends JPanel {

    private final UserDAO userDAO;
    private final EvaluationService evaluationService;
    private final JComboBox<UserItem> userComboBox;
    private final DefaultTableModel detailTableModel;
    private final JTable detailTable;
    private final JTextArea summaryArea;

    public EvaluationPanel() {
        this.userDAO = new UserDAO();
        this.evaluationService = new EvaluationService();
        this.userComboBox = new JComboBox<>();
        this.detailTableModel = new DefaultTableModel(
                new Object[]{"Submission", "Bài", "AI %", "Nhãn", "Thuật toán"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        this.detailTable = new JTable(detailTableModel);
        this.summaryArea = new JTextArea();
        initializeLayout();
        refreshUsers();
    }

    private void initializeLayout() {
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        filterPanel.add(new JLabel("Handle:"));
        filterPanel.add(userComboBox);
        userComboBox.addActionListener(event -> showSelectedEvaluation());

        summaryArea.setEditable(false);
        summaryArea.setLineWrap(true);
        summaryArea.setWrapStyleWord(true);
        detailTable.setRowHeight(24);

        add(filterPanel, BorderLayout.NORTH);
        add(new JScrollPane(detailTable), BorderLayout.CENTER);
        add(new JScrollPane(summaryArea), BorderLayout.SOUTH);
    }

    public void refreshUsers() {
        try {
            userComboBox.removeAllItems();
            for (User user : userDAO.findAll()) {
                userComboBox.addItem(new UserItem(user.getId(), user.getHandle()));
            }
            showSelectedEvaluation();
        } catch (SQLException exception) {
            showError("Không thể tải danh sách đánh giá", exception);
        }
    }

    private void showSelectedEvaluation() {
        UserItem selectedUser = (UserItem) userComboBox.getSelectedItem();
        if (selectedUser == null) {
            detailTableModel.setRowCount(0);
            summaryArea.setText("Chưa có handle trong hệ thống.");
            return;
        }

        try {
            User user = userDAO.findById(selectedUser.userId()).orElseThrow();
            EvaluationService.UserEvaluation evaluation = evaluationService.evaluateUser(user);
            detailTableModel.setRowCount(0);
            for (EvaluationService.AnalysisDetail detail : evaluation.details()) {
                detailTableModel.addRow(new Object[]{
                        detail.codeforcesSubmissionId(),
                        detail.problemCode() + " - " + detail.problemName(),
                        String.format("%.1f", detail.aiUsageScore()),
                        detail.aiUsageLabel(),
                        detail.algorithms()
                });
            }
            summaryArea.setText("""
                    Handle: %s
                    Điểm tổng: %.1f
                    AI trung bình: %.1f%%
                    Nhãn AI: %s
                    Số bài đã phân tích: %d
                    """.formatted(
                    evaluation.handle(),
                    evaluation.totalScore(),
                    evaluation.averageAiProbability(),
                    evaluation.aiRiskLabel(),
                    evaluation.details().size()
            ));
        } catch (Exception exception) {
            showError("Không thể hiển thị đánh giá", exception);
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
