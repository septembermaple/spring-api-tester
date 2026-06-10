package com.autotest.license;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.net.URI;

/**
 * 激活对话框（GUI）
 * <p>
 * UI设计：
 * - 标题："软件激活"
 * - 作者信息：cc  邮箱：781100522@qq.com
 * - 机器码显示（只读）
 * - 激活码输入框
 * - 激活按钮
 * - 试用按钮（显示剩余天数）
 * - 用户协议复选框
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
    private JLabel lblAuthorInfo;

    // 颜色定义
    private static final Color PRIMARY_COLOR = new Color(41, 128, 185);
    private static final Color SUCCESS_COLOR = new Color(39, 174, 96);
    private static final Color ERROR_COLOR = new Color(231, 76, 60);
    private static final Color INFO_COLOR = new Color(52, 73, 94);
    private static final Color BG_COLOR = new Color(250, 250, 250);

    public LicenseDialog(JFrame parent) {
        super(parent, "软件激活", true);
        this.licenseManager = new LicenseManager();
        initComponents();
        setupLayout();
        setupListeners();
        updateUIState();

        setSize(500, 420);
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

        setSize(500, 420);
        setLocationRelativeTo(parent);
        setResizable(false);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    }

    private void initComponents() {
        // 机器码显示（只读）
        txtMachineCode = new JTextField(licenseManager.getMachineCode());
        txtMachineCode.setEditable(false);
        txtMachineCode.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
        txtMachineCode.setBackground(new Color(240, 240, 240));
        txtMachineCode.setForeground(INFO_COLOR);
        txtMachineCode.setHorizontalAlignment(JTextField.CENTER);
        txtMachineCode.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));

        // 激活码输入框
        txtLicenseKey = new JTextField(25);
        txtLicenseKey.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        txtLicenseKey.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));

        // 激活按钮
        btnActivate = createStyledButton("激活", PRIMARY_COLOR);
        btnActivate.setPreferredSize(new Dimension(120, 40));
        btnActivate.setEnabled(false); // 默认禁用，需要勾选协议

        // 试用按钮
        int remainingDays = licenseManager.getTrialRemainingDays();
        String trialText = remainingDays > 0 ? "试用 (" + remainingDays + "天)" : "试用已过期";
        btnTrial = createStyledButton(trialText, new Color(149, 165, 166));
        btnTrial.setPreferredSize(new Dimension(120, 40));

        // 用户协议复选框
        chkAgreement = new JCheckBox("我已阅读并同意用户协议");
        chkAgreement.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        chkAgreement.setBackground(BG_COLOR);

        // 状态标签
        lblStatus = new JLabel("请输入激活码或选择试用");
        lblStatus.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        lblStatus.setForeground(INFO_COLOR);
        lblStatus.setHorizontalAlignment(SwingConstants.CENTER);

        // 试用信息标签
        lblTrialInfo = new JLabel(licenseManager.getTrialStatusText());
        lblTrialInfo.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        lblTrialInfo.setForeground(INFO_COLOR);
        lblTrialInfo.setHorizontalAlignment(SwingConstants.CENTER);

        // 作者信息标签
        lblAuthorInfo = new JLabel("作者: cc  |  邮箱: 781100522@qq.com");
        lblAuthorInfo.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        lblAuthorInfo.setForeground(Color.GRAY);
        lblAuthorInfo.setHorizontalAlignment(SwingConstants.CENTER);
    }

    private JButton createStyledButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        button.setForeground(Color.WHITE);
        button.setBackground(bgColor);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        final Color originalColor = bgColor;
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(originalColor.darker());
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(originalColor);
                }
            }
        });

        return button;
    }

    private void setupLayout() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(BG_COLOR);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        // 标题
        JLabel lblTitle = new JLabel("Spring API Tester");
        lblTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        lblTitle.setForeground(PRIMARY_COLOR);
        lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblSubtitle = new JLabel("软件激活");
        lblSubtitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        lblSubtitle.setForeground(INFO_COLOR);
        lblSubtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        // 机器码区域
        JPanel machineCodePanel = new JPanel();
        machineCodePanel.setLayout(new BoxLayout(machineCodePanel, BoxLayout.Y_AXIS));
        machineCodePanel.setBackground(BG_COLOR);
        machineCodePanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        machineCodePanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220)),
                " 机器码 ",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font(Font.SANS_SERIF, Font.BOLD, 12),
                INFO_COLOR
        ));

        JLabel lblMachineCodeHint = new JLabel("请将此机器码发送给管理员获取激活码");
        lblMachineCodeHint.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        lblMachineCodeHint.setForeground(Color.GRAY);
        lblMachineCodeHint.setAlignmentX(Component.CENTER_ALIGNMENT);

        txtMachineCode.setAlignmentX(Component.CENTER_ALIGNMENT);
        txtMachineCode.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));

        machineCodePanel.add(Box.createVerticalStrut(5));
        machineCodePanel.add(lblMachineCodeHint);
        machineCodePanel.add(Box.createVerticalStrut(8));
        machineCodePanel.add(txtMachineCode);
        machineCodePanel.add(Box.createVerticalStrut(5));

        // 激活码区域
        JPanel licenseKeyPanel = new JPanel();
        licenseKeyPanel.setLayout(new BoxLayout(licenseKeyPanel, BoxLayout.Y_AXIS));
        licenseKeyPanel.setBackground(BG_COLOR);
        licenseKeyPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        licenseKeyPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220)),
                " 激活码 ",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font(Font.SANS_SERIF, Font.BOLD, 12),
                INFO_COLOR
        ));

        txtLicenseKey.setAlignmentX(Component.CENTER_ALIGNMENT);
        txtLicenseKey.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        licenseKeyPanel.add(Box.createVerticalStrut(5));
        licenseKeyPanel.add(txtLicenseKey);
        licenseKeyPanel.add(Box.createVerticalStrut(5));

        // 按钮区域
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));
        buttonPanel.setBackground(BG_COLOR);
        buttonPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        buttonPanel.add(btnActivate);
        buttonPanel.add(btnTrial);

        // 协议区域
        JPanel agreementPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
        agreementPanel.setBackground(BG_COLOR);
        agreementPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        agreementPanel.add(chkAgreement);

        // 用户协议链接
        JLabel lblAgreementLink = new JLabel("<html><a href=''>查看用户协议</a></html>");
        lblAgreementLink.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        lblAgreementLink.setForeground(PRIMARY_COLOR);
        lblAgreementLink.setCursor(new Cursor(Cursor.HAND_CURSOR));
        lblAgreementLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showAgreementDialog();
            }
        });
        agreementPanel.add(lblAgreementLink);

        // 组装主面板
        mainPanel.add(lblTitle);
        mainPanel.add(Box.createVerticalStrut(5));
        mainPanel.add(lblSubtitle);
        mainPanel.add(Box.createVerticalStrut(15));
        mainPanel.add(machineCodePanel);
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(licenseKeyPanel);
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(agreementPanel);
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(buttonPanel);
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(lblStatus);
        mainPanel.add(Box.createVerticalStrut(5));
        mainPanel.add(lblTrialInfo);
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(lblAuthorInfo);

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
            btnTrial.setText("试用 (" + remainingDays + "天)");
            btnTrial.setEnabled(true);
            lblTrialInfo.setText("试用期剩余 " + remainingDays + " 天");
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
        scrollPane.setPreferredSize(new Dimension(400, 300));

        JOptionPane.showMessageDialog(this, scrollPane, "用户协议", JOptionPane.PLAIN_MESSAGE);
    }

    /**
     * 是否已激活
     *
     * @return 是否已激活
     */
    public boolean isActivated() {
        return activated;
    }

    /**
     * 是否已启用试用
     *
     * @return 是否已启用试用
     */
    public boolean isTrialStarted() {
        return trialStarted;
    }
}
