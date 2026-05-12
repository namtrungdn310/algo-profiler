package com.project.ui;

import com.project.ai.AIAnalyzer;
import com.project.scheduler.CrawlScheduler;
import com.project.service.EvaluationService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DashboardPanel extends JPanel {

    private final CrawlScheduler crawlScheduler;
    private final Runnable onDataChanged;
    private final EvaluationService evaluationService;
    private final AIAnalyzer aiAnalyzer;
    private final DefaultTableModel tableModel;
    private final JTable dashboardTable;
    private final JButton runWorkflowButton;
    private final JProgressBar progressBar;
    private final JSpinner crawlLimitSpinner;
    private List<EvaluationService.UserEvaluation> currentEvaluations;

    public DashboardPanel(CrawlScheduler crawlScheduler, Runnable onDataChanged) {
        this.crawlScheduler = crawlScheduler;
        this.onDataChanged = onDataChanged;
        this.evaluationService = new EvaluationService();
        this.aiAnalyzer = new AIAnalyzer();
        this.tableModel = new DefaultTableModel(
                new Object[]{"Nick", "Điểm tổng", "Đánh giá AI"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        this.dashboardTable = new JTable(tableModel);
        this.runWorkflowButton = new JButton("Bắt đầu Crawl & Phân tích");
        this.progressBar = new JProgressBar();
        this.crawlLimitSpinner = new JSpinner(
                new SpinnerNumberModel(CrawlScheduler.DEFAULT_MAX_NEW_SUBMISSIONS_PER_USER, 1, 200, 1)
        );
        this.currentEvaluations = new ArrayList<>();

        initializeLayout();
    }

    private void initializeLayout() {
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel headerPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        headerPanel.add(new JLabel("Dashboard đánh giá"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0;
        headerPanel.add(new JLabel("Tối đa bài mới / nick:"), gbc);

        gbc.gridx = 2;
        headerPanel.add(crawlLimitSpinner, gbc);

        gbc.gridx = 3;
        gbc.weightx = 0;
        runWorkflowButton.addActionListener(event -> runWorkflowInBackground());
        headerPanel.add(runWorkflowButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 4;
        progressBar.setIndeterminate(false);
        progressBar.setStringPainted(true);
        progressBar.setString("Sẵn sàng");
        headerPanel.add(progressBar, gbc);

        gbc.gridy = 2;
        headerPanel.add(new JLabel("Luồng chạy: crawl source code từng bài Accepted chưa có trong DB -> AI phân tích từng submission -> dashboard tổng hợp điểm."), gbc);

        dashboardTable.setRowHeight(24);
        dashboardTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    showEvaluationDetails(dashboardTable.getSelectedRow());
                }
            }
        });

        add(headerPanel, BorderLayout.NORTH);
        add(new JScrollPane(dashboardTable), BorderLayout.CENTER);
    }

    public void refreshData() {
        try {
            currentEvaluations = evaluationService.evaluateAllUsers();
            tableModel.setRowCount(0);
            for (EvaluationService.UserEvaluation evaluation : currentEvaluations) {
                tableModel.addRow(new Object[]{
                        evaluation.handle(),
                        String.format("%.1f", evaluation.totalScore()),
                        String.format("%s (TB %.1f%%)", evaluation.aiRiskLabel(), evaluation.averageAiProbability())
                });
            }
        } catch (SQLException exception) {
            JOptionPane.showMessageDialog(this, "Không thể tải dashboard:\n" + exception.getMessage(),
                    "Lỗi cơ sở dữ liệu", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void runWorkflowInBackground() {
        int maxNewSubmissionsPerUser = (Integer) crawlLimitSpinner.getValue();
        runWorkflowButton.setEnabled(false);
        progressBar.setIndeterminate(true);
        progressBar.setString("Đang crawl source code và phân tích AI...");

        SwingWorker<WorkflowResult, Void> worker = new SwingWorker<>() {
            @Override
            protected WorkflowResult doInBackground() throws Exception {
                int crawledCount = crawlScheduler.crawlAllUsersNow(maxNewSubmissionsPerUser);
                int analyzedCount = aiAnalyzer.analyzePendingSubmissions(Math.max(crawledCount, maxNewSubmissionsPerUser));
                List<EvaluationService.UserEvaluation> evaluations = evaluationService.evaluateAllUsers();
                return new WorkflowResult(crawledCount, analyzedCount, evaluations.size(), maxNewSubmissionsPerUser);
            }

            @Override
            protected void done() {
                runWorkflowButton.setEnabled(true);
                progressBar.setIndeterminate(false);
                progressBar.setString("Hoàn tất");
                try {
                    WorkflowResult result = get();
                    refreshData();
                    if (onDataChanged != null) {
                        onDataChanged.run();
                    }
                    JOptionPane.showMessageDialog(DashboardPanel.this,
                            "Đã crawl và phân tích xong."
                                    + "\nGiới hạn mỗi nick: " + result.maxNewSubmissionsPerUser()
                                    + "\nSố source code mới crawl được: " + result.crawledSubmissionCount()
                                    + "\nSố bài được phân tích AI: "
                                    + result.analyzedSubmissionCount()
                                    + "\nSố nick được cập nhật điểm: " + result.evaluatedUserCount(),
                            "Hoàn tất", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception exception) {
                    progressBar.setString("Có lỗi");
                    JOptionPane.showMessageDialog(DashboardPanel.this,
                            "Tác vụ Crawl & Phân tích thất bại:\n" + exception.getMessage(),
                            "Lỗi xử lý", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void showEvaluationDetails(int selectedRow) {
        if (selectedRow < 0 || selectedRow >= currentEvaluations.size()) {
            return;
        }

        EvaluationService.UserEvaluation evaluation = currentEvaluations.get(selectedRow);
        JDialog dialog = new JDialog();
        dialog.setTitle("Chi tiết đánh giá - " + evaluation.handle());
        dialog.setModal(true);
        dialog.setSize(950, 540);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(12, 12));

        DefaultTableModel detailTableModel = new DefaultTableModel(
                new Object[]{"Submission", "Bài", "AI %", "Nhãn"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable detailTable = new JTable(detailTableModel);
        detailTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        for (EvaluationService.AnalysisDetail detail : evaluation.details()) {
            detailTableModel.addRow(new Object[]{
                    detail.codeforcesSubmissionId(),
                    detail.problemCode() + " - " + detail.problemName(),
                    String.format("%.1f", detail.aiUsageScore()),
                    detail.aiUsageLabel()
            });
        }

        JTextArea reviewArea = new JTextArea();
        reviewArea.setEditable(false);
        reviewArea.setLineWrap(true);
        reviewArea.setWrapStyleWord(true);

        detailTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                int index = detailTable.getSelectedRow();
                if (index >= 0 && index < evaluation.details().size()) {
                    reviewArea.setText(buildDetailReview(evaluation.details().get(index)));
                    reviewArea.setCaretPosition(0);
                }
            }
        });

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(detailTable),
                new JScrollPane(reviewArea)
        );
        splitPane.setResizeWeight(0.45);

        dialog.add(new JLabel(String.format(
                "Tổng điểm: %.1f | AI trung bình: %.1f%% | Nhãn: %s",
                evaluation.totalScore(),
                evaluation.averageAiProbability(),
                evaluation.aiRiskLabel()
        )), BorderLayout.NORTH);
        dialog.add(splitPane, BorderLayout.CENTER);

        if (!evaluation.details().isEmpty()) {
            detailTable.setRowSelectionInterval(0, 0);
        } else {
            reviewArea.setText("Chưa có dữ liệu phân tích AI.");
        }

        dialog.setVisible(true);
    }

    private String buildDetailReview(EvaluationService.AnalysisDetail detail) {
        StringBuilder builder = new StringBuilder();
        builder.append("Submission #").append(detail.codeforcesSubmissionId()).append('\n');
        builder.append("Bài: ").append(detail.problemCode()).append(" - ").append(detail.problemName()).append('\n');
        builder.append("Ngôn ngữ: ").append(detail.programmingLanguage()).append('\n');
        builder.append("Kết quả: ").append(detail.verdict()).append('\n');
        builder.append("CTDL: ").append(detail.dataStructures()).append('\n');
        builder.append("Thuật toán: ").append(detail.algorithms()).append('\n');
        builder.append("Xác suất AI: ").append(String.format("%.1f%%", detail.aiUsageScore())).append('\n');
        builder.append("Nhãn: ").append(detail.aiUsageLabel()).append("\n\n");
        builder.append("Nhận xét AI:\n").append(detail.summary());
        return builder.toString();
    }

    private record WorkflowResult(
            int crawledSubmissionCount,
            int analyzedSubmissionCount,
            int evaluatedUserCount,
            int maxNewSubmissionsPerUser
    ) {
    }
}
