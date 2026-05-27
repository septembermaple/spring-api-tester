package com.autotest.gui;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

/**
 * AI实时思考过程展示面板 - 作为子窗口显示在主窗口内
 */
public class AiProgressPanel extends JDialog {

    private JTextArea txtThinking;
    private JScrollPane scrollPane;
    private JLabel lblStatus;
    private JLabel lblEndpoint;
    private JLabel lblStats;  // 统计信息标签
    private JProgressBar progressBar;

    // 颜色定义
    private static final Color PRIMARY_COLOR = new Color(41, 128, 185);
    private static final Color BG_COLOR = new Color(248, 249, 250);
    private static final Color TEXT_COLOR = new Color(33, 37, 41);

    public AiProgressPanel(JFrame parent) {
        super(parent, "AI 思考过程", false); // false表示非模态对话框
        initComponents();
        setupLayout();
        setupWindowPosition(parent);
    }

    private void initComponents() {
        setSize(700, 500);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        // 思考内容区域
        txtThinking = new JTextArea();
        txtThinking.setEditable(false);
        txtThinking.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        txtThinking.setBackground(BG_COLOR);
        txtThinking.setForeground(TEXT_COLOR);
        txtThinking.setLineWrap(true);
        txtThinking.setWrapStyleWord(true);
        txtThinking.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        scrollPane = new JScrollPane(txtThinking);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setBorder(BorderFactory.createTitledBorder(
            new LineBorder(PRIMARY_COLOR, 1, true),
            " 实时输出 ",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font(Font.SANS_SERIF, Font.BOLD, 12),
            PRIMARY_COLOR
        ));

        // 状态标签
        lblStatus = new JLabel("准备中...");
        lblStatus.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        lblStatus.setForeground(PRIMARY_COLOR);

        lblEndpoint = new JLabel("");
        lblEndpoint.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        lblEndpoint.setForeground(Color.GRAY);

        // 统计信息标签
        lblStats = new JLabel("");
        lblStats.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        lblStats.setForeground(new Color(142, 68, 173)); // 紫色

        // 进度条
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setStringPainted(true);
        progressBar.setString("AI思考中...");
    }

    private void setupLayout() {
        setLayout(new BorderLayout(10, 10));

        // 顶部信息栏
        JPanel topPanel = new JPanel(new BorderLayout(10, 5));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 5, 15));
        topPanel.setBackground(Color.WHITE);

        topPanel.add(lblStatus, BorderLayout.WEST);
        topPanel.add(lblEndpoint, BorderLayout.EAST);

        // 统计信息栏
        JPanel statsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        statsPanel.setBackground(Color.WHITE);
        statsPanel.setBorder(BorderFactory.createEmptyBorder(0, 15, 5, 15));
        statsPanel.add(lblStats);

        add(topPanel, BorderLayout.NORTH);
        add(statsPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(progressBar, BorderLayout.SOUTH);
    }

    /**
     * 设置窗口位置在主窗口内居中
     */
    private void setupWindowPosition(JFrame parent) {
        // 监听父窗口移动事件，跟随移动
        parent.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                if (isVisible()) {
                    centerInParent(parent);
                }
            }

            @Override
            public void componentResized(ComponentEvent e) {
                if (isVisible()) {
                    centerInParent(parent);
                }
            }
        });
    }

    /**
     * 在主窗口内居中显示
     */
    private void centerInParent(JFrame parent) {
        if (parent == null) return;

        Point parentLocation = parent.getLocation();
        Dimension parentSize = parent.getSize();
        Dimension dialogSize = getSize();

        // 计算居中位置
        int x = parentLocation.x + (parentSize.width - dialogSize.width) / 2;
        int y = parentLocation.y + (parentSize.height - dialogSize.height) / 2;

        // 确保不会超出屏幕边界
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Rectangle screenBounds = ge.getMaximumWindowBounds();

        // 如果计算的位置在当前屏幕显示区域内，则使用；否则使用父窗口所在屏幕
        GraphicsDevice[] screens = ge.getScreenDevices();
        GraphicsDevice parentScreen = null;

        // 找到父窗口所在的屏幕
        for (GraphicsDevice screen : screens) {
            Rectangle bounds = screen.getDefaultConfiguration().getBounds();
            if (bounds.contains(parent.getLocation())) {
                parentScreen = screen;
                break;
            }
        }

        // 如果找到了父窗口所在屏幕，确保对话框也在该屏幕内
        if (parentScreen != null) {
            Rectangle parentScreenBounds = parentScreen.getDefaultConfiguration().getBounds();

            // 调整位置确保在父窗口屏幕内
            x = Math.max(parentScreenBounds.x + 50,
                    Math.min(x, parentScreenBounds.x + parentScreenBounds.width - dialogSize.width - 50));
            y = Math.max(parentScreenBounds.y + 50,
                    Math.min(y, parentScreenBounds.y + parentScreenBounds.height - dialogSize.height - 50));
        }

        setLocation(x, y);
    }

    /**
     * 开始新的AI思考任务
     */
    public void startThinking(String endpoint, JFrame parent) {
        txtThinking.setText("");
        lblStatus.setText("AI正在生成测试用例...");
        lblStatus.setForeground(PRIMARY_COLOR);
        lblEndpoint.setText(endpoint);
        progressBar.setIndeterminate(true);
        progressBar.setString("AI思考中...");

        // 在主窗口内居中显示
        centerInParent(parent);
        setVisible(true);
    }

    /**
     * 追加AI输出的token
     */
    public void appendToken(String token) {
        SwingUtilities.invokeLater(() -> {
            txtThinking.append(token);
            // 自动滚动到底部
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    /**
     * 完成思考
     */
    public void complete() {
        SwingUtilities.invokeLater(() -> {
            lblStatus.setText("生成完成");
            lblStatus.setForeground(new Color(39, 174, 96));
            progressBar.setIndeterminate(false);
            progressBar.setValue(100);
            progressBar.setString("完成");
        });
    }

    /**
     * 发生错误
     */
    public void error(String message) {
        SwingUtilities.invokeLater(() -> {
            lblStatus.setText("错误: " + message);
            lblStatus.setForeground(new Color(231, 76, 60));
            progressBar.setIndeterminate(false);
            progressBar.setString("失败");
        });
    }

    /**
     * 更新统计信息
     */
    public void updateStats(String statsText) {
        SwingUtilities.invokeLater(() -> {
            lblStats.setText(statsText);
        });
    }
}
