package com.autotest.license;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;

/**
 * 激活对话框（GUI）- 现代化设计
 * <p>
 * UI设计：
 * - 圆角卡片式布局
 * - 渐变色标题栏
 * - 清晰的视觉层次
 * - 作者信息：cc  邮箱：781100522@qq.com
 */
public class LicenseDialog extends JDialog {

    private final LicenseManager licenseManager;
    private boolean activated = false;
    private boolean trialStarted = false;

    // UI组件
    private JTextField txtMachineCode;
    private JTextField txtLicenseKey;
    private JButton btnActivate;
    private JButton btnTrial;
    private JCheckBox chkAgreement;
    private JLabel lblStatus;
    private JLabel lblTrialInfo;

    // 颜色定义 - 现代化配色
    private static final Color PRIMARY_COLOR = new Color(59, 130, 246);      // 蓝色
    private static final Color PRIMARY_HOVER = new Color(37, 99, 235);       // 深蓝
    private static final Color SUCCESS_COLOR = new Color(34, 197, 94);       // 绿色
    private static final Color ERROR_COLOR = new Color(239, 68, 68);         // 红色
    private static final Color WARNING_COLOR = new Color(245, 158, 11);      // 橙色
    private static final Color INFO_COLOR = new Color(107, 114, 128);        // 灰色
    private static final Color BG_COLOR = new Color(248, 250, 252);          // 浅灰背景
    private static final Color CARD_COLOR = Color.WHITE;                      // 卡片白色
    private static final Color BORDER_COLOR = new Color(226, 232, 240);      // 边框色
    private static final Color TEXT_PRIMARY = new Color(15, 23, 42);         // 主文字
    private static final Color TEXT_SECONDARY = new Color(100, 116, 139);    // 次要文字

    public LicenseDialog(JFrame parent) {
        super(parent, "软件激活", true);
        this.licenseManager = new LicenseManager();
        initComponents();
        setupLayout();
        setupListeners();
        updateUIState();

        setSize(520, 600);
        setLocationRelativeTo(parent);
        setResizable(false);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    }

    public LicenseDialog(JFrame parent, LicenseManager licenseManager) {
        super(parent, "软件激活", true);
        this.licenseManager = licenseManager;
        initComponents();
        setupLayout();
        setupListeners();
        updateUIState();

        setSize(520, 600);
        setLocationRelativeTo(parent);
        setResizable(false);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    }

    private void initComponents() {
        // 机器码显示（只读）
        txtMachineCode = new JTextField(licenseManager.getMachineCode());
        txtMachineCode.setEditable(false);
        txtMachineCode.setFont(new Font("Consolas", Font.BOLD, 14));
        txtMachineCode.setBackground(new Color(241, 245, 249));
        txtMachineCode.setForeground(TEXT_PRIMARY);
        txtMachineCode.setHorizontalAlignment(JTextField.CENTER);
        txtMachineCode.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));
        txtMachineCode.setCaretColor(txtMachineCode.getBackground());

        // 激活码输入框
        txtLicenseKey = new JTextField(25);
        txtLicenseKey.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        txtLicenseKey.setForeground(TEXT_PRIMARY);
        txtLicenseKey.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)
        ));
        txtLicenseKey.setToolTipText("请输入激活码，格式：XXXX-XXXX-XXXX-XXXX-XXXX");

        // 激活按钮 - 圆角设计
        btnActivate = createPrimaryButton("立即激活", PRIMARY_COLOR);
        btnActivate.setEnabled(false);

        // 试用按钮
        int remainingDays = licenseManager.getTrialRemainingDays();
        String trialText = remainingDays > 0 ? "免费试用 " + remainingDays + " 天" : "试用已过期";
        btnTrial = createSecondaryButton(trialText);

        // 用户协议复选框
        chkAgreement = new JCheckBox("我已阅读并同意用户协议");
        chkAgreement.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        chkAgreement.setBackground(CARD_COLOR);
        chkAgreement.setForeground(TEXT_SECONDARY);

        // 状态标签
        lblStatus = new JLabel("请输入激活码或选择试用");
        lblStatus.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        lblStatus.setForeground(INFO_COLOR);
        lblStatus.setHorizontalAlignment(SwingConstants.CENTER);

        // 试用信息标签
        lblTrialInfo = new JLabel(licenseManager.getTrialStatusText());
        lblTrialInfo.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        lblTrialInfo.setForeground(WARNING_COLOR);
        lblTrialInfo.setHorizontalAlignment(SwingConstants.CENTER);
    }

    /**
     * 创建主按钮（蓝色填充）
     */
    private JButton createPrimaryButton(String text, Color bgColor) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // 绘制圆角背景
                if (getModel().isEnabled()) {
                    if (getModel().isPressed()) {
                        g2.setColor(bgColor.darker());
                    } else if (getModel().isRollover()) {
                        g2.setColor(PRIMARY_HOVER);
                    } else {
                        g2.setColor(bgColor);
                    }
                } else {
                    g2.setColor(new Color(203, 213, 225));
                }

                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                g2.dispose();

                super.paintComponent(g);
            }
        };

        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(140, 44));

        return button;
    }

    /**
     * 创建次要按钮（边框样式）
     */
    private JButton createSecondaryButton(String text) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (getModel().isEnabled()) {
                    if (getModel().isPressed()) {
                        g2.setColor(new Color(241, 245, 249));
                        g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                    } else if (getModel().isRollover()) {
                        g2.setColor(new Color(241, 245, 249));
                        g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                    }
                }

                // 绘制边框
                g2.setColor(BORDER_COLOR);
                g2.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 8, 8));
                g2.dispose();

                super.paintComponent(g);
            }
        };

        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        button.setForeground(TEXT_SECONDARY);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(140, 44));

        return button;
    }

    private void setupLayout() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(BG_COLOR);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        // ===== 顶部标题栏（渐变色） =====
        JPanel headerPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // 绘制渐变背景
                GradientPaint gradient = new GradientPaint(
                        0, 0, new Color(59, 130, 246),
                        getWidth(), 0, new Color(37, 99, 235)
                );
                g2.setPaint(gradient);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(28, 30, 28, 30));
        headerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        headerPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblTitle = new JLabel("Spring API Tester");
        lblTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        lblTitle.setForeground(Color.WHITE);
        lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblSubtitle = new JLabel("软件激活");
        lblSubtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        lblSubtitle.setForeground(new Color(191, 219, 254));
        lblSubtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        headerPanel.add(lblTitle);
        headerPanel.add(Box.createVerticalStrut(6));
        headerPanel.add(lblSubtitle);

        // ===== 内容卡片 =====
        JPanel cardPanel = new JPanel();
        cardPanel.setLayout(new BoxLayout(cardPanel, BoxLayout.Y_AXIS));
        cardPanel.setBackground(CARD_COLOR);
        cardPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 1, 1, BORDER_COLOR),
                BorderFactory.createEmptyBorder(24, 28, 24, 28)
        ));
        cardPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // 机器码区域
        JLabel lblMachineCodeTitle = new JLabel("机器码");
        lblMachineCodeTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        lblMachineCodeTitle.setForeground(TEXT_SECONDARY);
        lblMachineCodeTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblMachineCodeHint = new JLabel("双击复制，发送给管理员获取激活码");
        lblMachineCodeHint.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        lblMachineCodeHint.setForeground(INFO_COLOR);
        lblMachineCodeHint.setAlignmentX(Component.LEFT_ALIGNMENT);

        txtMachineCode.setAlignmentX(Component.LEFT_ALIGNMENT);
        txtMachineCode.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));

        // 激活码区域
        JLabel lblLicenseKeyTitle = new JLabel("激活码");
        lblLicenseKeyTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        lblLicenseKeyTitle.setForeground(TEXT_SECONDARY);
        lblLicenseKeyTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        txtLicenseKey.setAlignmentX(Component.LEFT_ALIGNMENT);
        txtLicenseKey.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));

        // 按钮区域
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        buttonPanel.setBackground(CARD_COLOR);
        buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonPanel.add(btnActivate);
        buttonPanel.add(btnTrial);

        // 协议区域 - 使用BoxLayout确保水平排列
        JPanel agreementPanel = new JPanel();
        agreementPanel.setLayout(new BoxLayout(agreementPanel, BoxLayout.X_AXIS));
        agreementPanel.setBackground(CARD_COLOR);
        agreementPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        agreementPanel.add(chkAgreement);
        agreementPanel.add(Box.createHorizontalStrut(8));

        // 用户协议链接
        JLabel lblAgreementLink = new JLabel("<html><a href='' style='color:#3B82F6;text-decoration:none;'>查看用户协议</a></html>");
        lblAgreementLink.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        lblAgreementLink.setCursor(new Cursor(Cursor.HAND_CURSOR));
        lblAgreementLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showAgreementDialog();
            }
        });
        agreementPanel.add(lblAgreementLink);
        agreementPanel.add(Box.createHorizontalGlue());

        // 状态区域
        JPanel statusPanel = new JPanel();
        statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.Y_AXIS));
        statusPanel.setBackground(CARD_COLOR);
        statusPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR),
                BorderFactory.createEmptyBorder(16, 0, 0, 0)
        ));

        lblStatus.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblTrialInfo.setAlignmentX(Component.CENTER_ALIGNMENT);

        statusPanel.add(lblStatus);
        statusPanel.add(Box.createVerticalStrut(6));
        statusPanel.add(lblTrialInfo);

        // 组装卡片
        cardPanel.add(lblMachineCodeTitle);
        cardPanel.add(Box.createVerticalStrut(4));
        cardPanel.add(lblMachineCodeHint);
        cardPanel.add(Box.createVerticalStrut(8));
        cardPanel.add(txtMachineCode);
        cardPanel.add(Box.createVerticalStrut(20));
        cardPanel.add(lblLicenseKeyTitle);
        cardPanel.add(Box.createVerticalStrut(8));
        cardPanel.add(txtLicenseKey);
        cardPanel.add(Box.createVerticalStrut(20));
        cardPanel.add(buttonPanel);
        cardPanel.add(Box.createVerticalStrut(16));
        cardPanel.add(agreementPanel);
        cardPanel.add(Box.createVerticalStrut(16));
        cardPanel.add(statusPanel);

        // ===== 底部作者信息 =====
        JPanel footerPanel = new JPanel();
        footerPanel.setLayout(new BoxLayout(footerPanel, BoxLayout.Y_AXIS));
        footerPanel.setBackground(BG_COLOR);
        footerPanel.setBorder(BorderFactory.createEmptyBorder(16, 0, 16, 0));
        footerPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblAuthor = new JLabel("作者: cc");
        lblAuthor.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        lblAuthor.setForeground(TEXT_SECONDARY);
        lblAuthor.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblEmail = new JLabel("邮箱: 781100522@qq.com");
        lblEmail.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        lblEmail.setForeground(TEXT_SECONDARY);
        lblEmail.setAlignmentX(Component.CENTER_ALIGNMENT);

        footerPanel.add(lblAuthor);
        footerPanel.add(Box.createVerticalStrut(2));
        footerPanel.add(lblEmail);

        // 组装主面板
        mainPanel.add(headerPanel);
        mainPanel.add(Box.createVerticalStrut(-1)); // 消除间隙
        mainPanel.add(cardPanel);
        mainPanel.add(footerPanel);

        setContentPane(mainPanel);
    }

    private void setupListeners() {
        // 用户协议复选框
        chkAgreement.addActionListener(e -> {
            btnActivate.setEnabled(chkAgreement.isSelected());
            updateUIState();
        });

        // 激活按钮
        btnActivate.addActionListener(e -> {
            String licenseKey = txtLicenseKey.getText().trim();
            if (licenseKey.isEmpty()) {
                lblStatus.setText("请输入激活码");
                lblStatus.setForeground(ERROR_COLOR);
                return;
            }

            lblStatus.setText("正在验证激活码...");
            lblStatus.setForeground(INFO_COLOR);
            btnActivate.setEnabled(false);
            btnTrial.setEnabled(false);

            // 在后台线程中验证
            new SwingWorker<LicenseManager.LicenseVerificationResult, Void>() {
                @Override
                protected LicenseManager.LicenseVerificationResult doInBackground() {
                    return licenseManager.verifyLicense(licenseKey);
                }

                @Override
                protected void done() {
                    try {
                        LicenseManager.LicenseVerificationResult result = get();
                        if (result.isSuccess()) {
                            activated = true;
                            lblStatus.setText("激活成功！" + result.getMessage());
                            lblStatus.setForeground(SUCCESS_COLOR);
                            JOptionPane.showMessageDialog(LicenseDialog.this,
                                    "激活成功！\n" + result.getMessage(),
                                    "激活成功",
                                    JOptionPane.INFORMATION_MESSAGE);
                            dispose();
                        } else {
                            lblStatus.setText("激活失败: " + result.getMessage());
                            lblStatus.setForeground(ERROR_COLOR);
                            btnActivate.setEnabled(chkAgreement.isSelected());
                            btnTrial.setEnabled(true);
                        }
                    } catch (Exception ex) {
                        lblStatus.setText("验证异常: " + ex.getMessage());
                        lblStatus.setForeground(ERROR_COLOR);
                        btnActivate.setEnabled(chkAgreement.isSelected());
                        btnTrial.setEnabled(true);
                    }
                }
            }.execute();
        });

        // 试用按钮
        btnTrial.addActionListener(e -> {
            if (!licenseManager.isTrialValid()) {
                lblStatus.setText("试用期已过期，请激活软件");
                lblStatus.setForeground(ERROR_COLOR);
                JOptionPane.showMessageDialog(this,
                        "试用期已过期，请联系管理员获取激活码。\n邮箱: 781100522@qq.com",
                        "试用过期",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (!chkAgreement.isSelected()) {
                lblStatus.setText("请先同意用户协议");
                lblStatus.setForeground(ERROR_COLOR);
                return;
            }

            trialStarted = true;
            licenseManager.getTrialManager().initTrial();
            lblStatus.setText("已启用试用模式");
            lblStatus.setForeground(SUCCESS_COLOR);
            JOptionPane.showMessageDialog(this,
                    "已启用试用模式，剩余 " + licenseManager.getTrialRemainingDays() + " 天\n" +
                            "试用结束后需要激活才能继续使用。",
                    "试用模式",
                    JOptionPane.INFORMATION_MESSAGE);
            dispose();
        });

        // 窗口关闭按钮
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (!activated && !trialStarted) {
                    int result = JOptionPane.showConfirmDialog(LicenseDialog.this,
                            "未激活且未启用试用，确定要退出程序吗？",
                            "确认退出",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE);
                    if (result == JOptionPane.YES_OPTION) {
                        System.exit(0);
                    }
                } else {
                    dispose();
                }
            }
        });

        // 机器码点击复制
        txtMachineCode.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    StringSelection selection = new StringSelection(txtMachineCode.getText());
                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    clipboard.setContents(selection, selection);
                    lblStatus.setText("机器码已复制到剪贴板");
                    lblStatus.setForeground(SUCCESS_COLOR);
                }
            }
        });
    }

    private void updateUIState() {
        int remainingDays = licenseManager.getTrialRemainingDays();
        if (remainingDays > 0) {
            btnTrial.setText("免费试用 " + remainingDays + " 天");
            btnTrial.setEnabled(true);
            lblTrialInfo.setText("试用期剩余 " + remainingDays + " 天");
            lblTrialInfo.setForeground(WARNING_COLOR);
        } else {
            btnTrial.setText("试用已过期");
            btnTrial.setEnabled(false);
            lblTrialInfo.setText("试用期已过期，请激活");
            lblTrialInfo.setForeground(ERROR_COLOR);
        }
    }

    /**
     * 显示用户协议对话框
     */
    private void showAgreementDialog() {
        JTextArea textArea = new JTextArea(
                "用户协议\n\n" +
                        "1. 本软件仅供授权用户使用。\n" +
                        "2. 用户不得反向工程、反编译或反汇编本软件。\n" +
                        "3. 用户不得将本软件用于非法目的。\n" +
                        "4. 试用期为3天，过期后需要激活才能继续使用。\n" +
                        "5. 一机一码绑定，激活码仅限当前设备使用。\n" +
                        "6. 作者保留对本软件的所有权利。\n\n" +
                        "退款规则：\n" +
                        "- 激活后7天内可申请退款\n" +
                        "- 已使用超过7天不支持退款\n\n" +
                        "隐私声明：\n" +
                        "- 仅收集机器码用于设备绑定\n" +
                        "- 不收集任何个人隐私信息\n\n" +
                        "作者: cc\n" +
                        "邮箱: 781100522@qq.com"
        );
        textArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setBackground(Color.WHITE);
        textArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(420, 320));

        JOptionPane.showMessageDialog(this, scrollPane, "用户协议", JOptionPane.PLAIN_MESSAGE);
    }

    public boolean isActivated() {
        return activated;
    }

    public boolean isTrialStarted() {
        return trialStarted;
    }
}
