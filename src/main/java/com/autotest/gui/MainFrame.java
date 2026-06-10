package com.autotest.gui;

import com.autotest.ai.AiClient;
import com.autotest.ai.FixAnalyzer;
import com.autotest.ai.TestCaseGenerator;
import com.autotest.config.Config;
import com.autotest.executor.PerformanceTester;
import com.autotest.executor.TestCase;
import com.autotest.executor.TestExecutor;
import com.autotest.executor.TestResult;
import com.autotest.git.ChangedEndpoint;
import com.autotest.git.GitDiffAnalyzer;
import com.autotest.license.LicenseDialog;
import com.autotest.license.LicenseManager;
import com.autotest.parser.ApiMetadataExtractor;
import com.autotest.parser.EndpointDefinition;
import com.autotest.report.DetailedReportGenerator;
import com.autotest.report.FixSuggestion;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

public class MainFrame extends JFrame {
    
    // 组件定义
    private JTabbedPane tabbedPane;
    
    // Git配置区域
    private JTextField txtGitPath;
    private JButton btnBrowseGit;
    private SearchableComboBox cmbBranch;  // 分支选择（支持搜索）
    private JTextField txtSince;
    private SearchableComboBox cmbAuthor;  // 用户选择（支持搜索）
    
    // 配置恢复标记
    private String branchToRestore = null;
    private String authorToRestore = null;
    
    // 测试配置区域
    private JTextField txtBaseUrl;
    private JTextField txtTimeout;
    private JSpinner spinnerThreads;
    private JTextField txtToken;
    
    // AI配置区域
    private JTextField txtNewApiUrl;
    private JPasswordField txtNewApiKey;
    private JTextField txtModel;
    
    // 性能测试配置
    private JCheckBox chkPerfTest;
    private JSpinner spinnerPerfThreads;
    private JSpinner spinnerPerfDuration;
    
    // 功能选项
    private JCheckBox chkAutoFix;
    
    // 接口过滤配置
    private JTextField txtExcludePrefixes;  // 排除的前缀，逗号分隔
    private JTextField txtExcludeKeywords;  // 排除的URL关键词，逗号分隔
    private JTextField txtIncludeOnly;      // 只测试的接口

    // 测试用例生成配置
    private JCheckBox chkPromptNormal;         // 正常流程
    private JCheckBox chkPromptValidation;     // 参数校验
    private JCheckBox chkPromptBoundary;       // 边界值
    private JCheckBox chkPromptError;          // 异常场景
    private JCheckBox chkPromptSecurity;       // 安全性
    private JButton btnSelectAllPrompts;    // 全选按钮
    private JButton btnDeselectAllPrompts;  // 取消全选按钮
    private JSpinner spinnerTestCaseCount;  // 用例数量

    // 控制按钮
    private JButton btnStart;
    private JButton btnStop;
    private JButton btnOpenReport;
    private JButton btnSaveConfig;
    
    // 日志区域
    private JTextArea txtLog;
    private JScrollPane logScrollPane;
    
    // 进度显示
    private JProgressBar progressBar;
    private JLabel lblStatus;
    
    // 执行器
    private ExecutorService executor;
    private Future<?> currentTask;
    private String lastReportPath;
    private volatile boolean isRunning = false;
    
    // 配置路径
    private static final String CONFIG_FILE = "application.properties";
    
    // 颜色定义
    private static final Color PRIMARY_COLOR = new Color(41, 128, 185);
    private static final Color SUCCESS_COLOR = new Color(39, 174, 96);
    private static final Color ERROR_COLOR = new Color(231, 76, 60);
    private static final Color WARNING_COLOR = new Color(230, 126, 34);
    private static final Color INFO_COLOR = new Color(52, 73, 94);
    
    // 日志前缀
    private static final String PREFIX_SUCCESS = "[SUCCESS] ";
    private static final String PREFIX_ERROR = "[ERROR] ";
    private static final String PREFIX_WARNING = "[WARNING] ";
    private static final String PREFIX_INFO = "[INFO] ";
    private static final String PREFIX_PROGRESS = "[PROGRESS] ";
    
    // 许可证管理器
    private LicenseManager licenseManager;

    public MainFrame() {
        // 先初始化UI
        initComponents();
        setupLayout();
        setupListeners();
        loadConfig();

        // 使用配置中的服务器地址创建许可证管理器
        Config licenseConfig = new Config();
        licenseManager = new LicenseManager(licenseConfig.getLicenseServerUrl(), licenseConfig.getSoftwareId());

        // 始终显示激活对话框（让用户可以输入激活码或选择试用）
        if (!licenseManager.isActivated()) {
            LicenseDialog dialog = new LicenseDialog(this, licenseManager);
            dialog.setVisible(true);
            if (!licenseManager.isActivated() && !licenseManager.isTrialValid()) {
                // 用户未激活且试用期已过，退出程序
                System.exit(0);
            }
        }

        appendLog(PREFIX_INFO + "Spring API Tester 启动成功");
        if (licenseManager.isActivated()) {
            appendLog(PREFIX_SUCCESS + "软件已激活");
        } else {
            appendLog(PREFIX_WARNING + "试用期: " + licenseManager.getTrialStatusText());
        }
        appendLog(PREFIX_INFO + "请配置测试参数后点击开始按钮");
    }
    
    private void initComponents() {
        setTitle("Spring API Tester - AI驱动接口测试工具");
        setSize(1200, 800);
        setMinimumSize(new Dimension(900, 600));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        
        // 初始化Git配置组件
        txtGitPath = new JTextField(30);
        txtGitPath.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        btnBrowseGit = createStyledButton("浏览", PRIMARY_COLOR);
        
        cmbBranch = new SearchableComboBox();
        cmbBranch.setSelectedItem("main");
        
        txtSince = new JTextField(getDefaultDate(), 20);
        txtSince.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        
        cmbAuthor = new SearchableComboBox();
        
        // 初始化测试配置组件
        txtBaseUrl = new JTextField("http://localhost:8080", 30);
        txtBaseUrl.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        
        txtTimeout = new JTextField("30", 10);
        txtTimeout.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        
        spinnerThreads = new JSpinner(new SpinnerNumberModel(5, 1, 50, 1));
        spinnerThreads.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        
        txtToken = new JTextField(30);
        txtToken.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        
        // 初始化关键词排除组件
        txtExcludeKeywords = new JTextField(25);
        txtExcludeKeywords.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        txtExcludeKeywords.setToolTipText("输入要排除的URL关键词，多个用逗号分隔，如: export,login,download");
        
        // 初始化AI配置组件
        txtNewApiUrl = new JTextField("http://192.168.18.15:3000/v1", 30);
        txtNewApiUrl.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        
        txtNewApiKey = new JPasswordField(30);
        txtNewApiKey.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        
        txtModel = new JTextField("glm-4-flash", 20);
        txtModel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        
        // 初始化性能测试配置
        chkPerfTest = new JCheckBox("启用性能测试");
        chkPerfTest.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        chkPerfTest.setForeground(INFO_COLOR);
        chkPerfTest.setBackground(Color.WHITE);
        
        spinnerPerfThreads = new JSpinner(new SpinnerNumberModel(10, 1, 100, 1));
        spinnerPerfThreads.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        
        spinnerPerfDuration = new JSpinner(new SpinnerNumberModel(60, 10, 600, 10));
        spinnerPerfDuration.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        
        // 初始化功能选项
        chkAutoFix = new JCheckBox("自动修复测试失败的接口");
        chkAutoFix.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        chkAutoFix.setForeground(INFO_COLOR);
        chkAutoFix.setBackground(Color.WHITE);
        
        // 初始化接口过滤配置
        txtExcludePrefixes = new JTextField(25);
        txtExcludePrefixes.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        txtExcludePrefixes.setToolTipText("输入要排除的接口前缀，多个用逗号分隔，如: /tools,/energyApi");

        txtIncludeOnly = new JTextField(25);
        txtIncludeOnly.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        txtIncludeOnly.setToolTipText("输入要测试的接口完整路径，如: /energyApi/report/detail");

        // 初始化测试用例生成配置 - 5个主类型复选框
        chkPromptNormal = new JCheckBox("正常流程", true);
        chkPromptNormal.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        chkPromptNormal.setForeground(INFO_COLOR);

        chkPromptValidation = new JCheckBox("参数校验", true);
        chkPromptValidation.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        chkPromptValidation.setForeground(INFO_COLOR);

        chkPromptBoundary = new JCheckBox("边界值", true);
        chkPromptBoundary.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        chkPromptBoundary.setForeground(INFO_COLOR);

        chkPromptError = new JCheckBox("异常场景", true);
        chkPromptError.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        chkPromptError.setForeground(INFO_COLOR);

        chkPromptSecurity = new JCheckBox("安全性", true);
        chkPromptSecurity.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        chkPromptSecurity.setForeground(INFO_COLOR);

        btnSelectAllPrompts = createStyledButton("全选", new Color(52, 152, 219));
        btnSelectAllPrompts.setPreferredSize(new Dimension(60, 28));
        btnSelectAllPrompts.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));

        btnDeselectAllPrompts = createStyledButton("取消全选", new Color(149, 165, 166));
        btnDeselectAllPrompts.setPreferredSize(new Dimension(80, 28));
        btnDeselectAllPrompts.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));

        spinnerTestCaseCount = new JSpinner(new SpinnerNumberModel(8, 5, 20, 1));
        spinnerTestCaseCount.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));

        // 初始化控制按钮
        btnStart = createStyledButton("开始测试", SUCCESS_COLOR);
        btnStop = createStyledButton("停止测试", ERROR_COLOR);
        btnStop.setEnabled(false);
        btnOpenReport = createStyledButton("打开报告", PRIMARY_COLOR);
        btnOpenReport.setEnabled(false);
        btnSaveConfig = createStyledButton("保存配置", new Color(155, 89, 182));
        
        // 初始化日志区域
        txtLog = new JTextArea();
        txtLog.setEditable(false);
        txtLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        txtLog.setForeground(INFO_COLOR);
        txtLog.setBackground(new Color(248, 248, 248));
        txtLog.setCaretColor(PRIMARY_COLOR);
        
        logScrollPane = new JScrollPane(txtLog);
        logScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        logScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        
        // 初始化进度条
        progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        progressBar.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        progressBar.setForeground(PRIMARY_COLOR);
        progressBar.setBackground(new Color(220, 220, 220));
        
        // 初始化状态标签
        lblStatus = new JLabel("就绪");
        lblStatus.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        lblStatus.setForeground(INFO_COLOR);
        
        // 初始化标签页
        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        tabbedPane.setForeground(INFO_COLOR);
        tabbedPane.setBackground(Color.WHITE);
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
        button.setPreferredSize(new Dimension(120, 36));
        
        // 保存原始颜色用于恢复
        final Color originalColor = bgColor;
        
        // 添加鼠标事件
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
        
        // 添加属性监听器，处理启用/禁用状态
        button.addPropertyChangeListener("enabled", evt -> {
            if (button.isEnabled()) {
                button.setBackground(originalColor);
                button.setForeground(Color.WHITE);
            } else {
                // 禁用状态使用灰色背景，但保持白色文字
                button.setBackground(new Color(189, 189, 189));
                button.setForeground(Color.WHITE);
            }
        });
        
        return button;
    }
    
    private void setupLayout() {
        setLayout(new BorderLayout(0, 0));
        
        // 顶部工具栏
        JPanel topToolbar = createTopToolbar();
        add(topToolbar, BorderLayout.NORTH);
        
        // 中间标签页
        tabbedPane.addTab("配置", createConfigScrollPane());
        tabbedPane.addTab("日志", createLogPanel());
        add(tabbedPane, BorderLayout.CENTER);
        
        // 底部状态栏
        JPanel bottomStatus = createBottomStatus();
        add(bottomStatus, BorderLayout.SOUTH);
    }
    
    private JPanel createTopToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        toolbar.setBackground(new Color(245, 245, 245));
        toolbar.setBorder(new MatteBorder(0, 0, 1, 0, new Color(200, 200, 200)));
        
        toolbar.add(btnStart);
        toolbar.add(btnStop);
        toolbar.add(btnOpenReport);
        toolbar.add(createSeparator());
        toolbar.add(btnSaveConfig);
        
        return toolbar;
    }
    
    private JSeparator createSeparator() {
        JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
        separator.setPreferredSize(new Dimension(1, 30));
        return separator;
    }
    
    private JScrollPane createConfigScrollPane() {
        JPanel configPanel = new JPanel();
        configPanel.setLayout(new BoxLayout(configPanel, BoxLayout.Y_AXIS));
        configPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        configPanel.setBackground(Color.WHITE);
        
        // Git配置面板
        JPanel gitPanel = createGitConfigPanel();
        configPanel.add(gitPanel);
        configPanel.add(Box.createVerticalStrut(15));
        
        // 测试配置面板
        JPanel testPanel = createTestConfigPanel();
        configPanel.add(testPanel);
        configPanel.add(Box.createVerticalStrut(15));
        
        // AI配置面板
        JPanel aiPanel = createAiConfigPanel();
        configPanel.add(aiPanel);
        configPanel.add(Box.createVerticalStrut(15));
        
        // 性能测试配置面板
        JPanel perfPanel = createPerfConfigPanel();
        configPanel.add(perfPanel);
        configPanel.add(Box.createVerticalStrut(15));

        // 测试用例生成配置面板
        JPanel testCasePanel = createTestCaseConfigPanel();
        configPanel.add(testCasePanel);
        configPanel.add(Box.createVerticalStrut(15));

        // 功能选项面板
        JPanel optionsPanel = createOptionsPanel();
        configPanel.add(optionsPanel);

        configPanel.add(Box.createVerticalGlue());
        
        // 将配置面板放入滚动面板，支持窗口缩小时滚动
        JScrollPane scrollPane = new JScrollPane(configPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        
        return scrollPane;
    }
    
    private JPanel createGitConfigPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Color.WHITE);
        panel.setBorder(createTitledBorder("Git 配置", PRIMARY_COLOR));
        
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        row1.setBackground(Color.WHITE);
        row1.add(new JLabel("项目路径:"));
        row1.add(txtGitPath);
        row1.add(btnBrowseGit);
        
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        row2.setBackground(Color.WHITE);
        row2.add(new JLabel("目标分支:"));
        row2.add(cmbBranch);
        row2.add(Box.createHorizontalStrut(30));
        row2.add(new JLabel("起始时间:"));
        row2.add(txtSince);
        
        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        row3.setBackground(Color.WHITE);
        row3.add(new JLabel("提交用户:"));
        row3.add(cmbAuthor);
        JLabel lblHint = new JLabel("(留空表示所有用户)");
        lblHint.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
        lblHint.setForeground(Color.GRAY);
        row3.add(lblHint);
        
        panel.add(row1);
        panel.add(row2);
        panel.add(row3);
        
        return panel;
    }
    
    private JPanel createTestConfigPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Color.WHITE);
        panel.setBorder(createTitledBorder("测试配置", SUCCESS_COLOR));
        
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        row1.setBackground(Color.WHITE);
        row1.add(new JLabel("被测服务URL:"));
        row1.add(txtBaseUrl);
        
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        row2.setBackground(Color.WHITE);
        row2.add(new JLabel("超时时间(秒):"));
        row2.add(txtTimeout);
        row2.add(Box.createHorizontalStrut(30));
        row2.add(new JLabel("并发线程数:"));
        row2.add(spinnerThreads);
        
        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        row3.setBackground(Color.WHITE);
        row3.add(new JLabel("认证Token:"));
        row3.add(txtToken);
        
        // 接口过滤配置
        JLabel lblExclude = new JLabel("排除接口(前缀,分隔):");
        lblExclude.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        
        JLabel lblExcludeKeyword = new JLabel("排除关键词(URL包含,分隔):");
        lblExcludeKeyword.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        
        JLabel lblInclude = new JLabel("只测接口(完整路径):");
        lblInclude.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        
        JPanel row4 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        row4.setBackground(Color.WHITE);
        row4.add(lblExclude);
        row4.add(txtExcludePrefixes);
        
        JPanel row4b = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        row4b.setBackground(Color.WHITE);
        row4b.add(lblExcludeKeyword);
        row4b.add(txtExcludeKeywords);
        
        JPanel row5 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        row5.setBackground(Color.WHITE);
        row5.add(lblInclude);
        row5.add(txtIncludeOnly);
        
        panel.add(row1);
        panel.add(row2);
        panel.add(row3);
        panel.add(row4);
        panel.add(row4b);
        panel.add(row5);
        
        return panel;
    }
    
    private JPanel createAiConfigPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Color.WHITE);
        panel.setBorder(createTitledBorder("AI 配置", new Color(155, 89, 182)));
        
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        row1.setBackground(Color.WHITE);
        row1.add(new JLabel("API 地址:"));
        row1.add(txtNewApiUrl);
        
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        row2.setBackground(Color.WHITE);
        row2.add(new JLabel("API Key:"));
        row2.add(txtNewApiKey);
        
        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        row3.setBackground(Color.WHITE);
        row3.add(new JLabel("模型名称:"));
        row3.add(txtModel);
        
        panel.add(row1);
        panel.add(row2);
        panel.add(row3);
        
        return panel;
    }
    
    private JPanel createPerfConfigPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Color.WHITE);
        panel.setBorder(createTitledBorder("性能测试配置", WARNING_COLOR));
        
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        row1.setBackground(Color.WHITE);
        row1.add(chkPerfTest);
        
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        row2.setBackground(Color.WHITE);
        row2.add(new JLabel("并发线程数:"));
        row2.add(spinnerPerfThreads);
        row2.add(Box.createHorizontalStrut(30));
        row2.add(new JLabel("持续时间(秒):"));
        row2.add(spinnerPerfDuration);
        
        panel.add(row1);
        panel.add(row2);
        
        return panel;
    }
    
    private JPanel createOptionsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Color.WHITE);
        panel.setBorder(createTitledBorder("功能选项", INFO_COLOR));

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        row.setBackground(Color.WHITE);
        row.add(chkAutoFix);

        panel.add(row);

        return panel;
    }

    private JPanel createTestCaseConfigPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Color.WHITE);
        panel.setBorder(createTitledBorder("测试类型配置", new Color(46, 204, 113)));

        // ===== 测试类型选择区域 =====
        JPanel typeLabelRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        typeLabelRow.setBackground(Color.WHITE);
        typeLabelRow.add(new JLabel("测试类型:"));
        panel.add(typeLabelRow);

        // 5个主类型复选框，每个带灰色说明文字（灰色字体，无背景）
        JCheckBox[] checkBoxes = {chkPromptNormal, chkPromptValidation, chkPromptBoundary, chkPromptError, chkPromptSecurity};
        String[] descriptions = {
            "标准参数组合、多参数组合、默认值测试",
            "必填字段缺失、格式错误、类型不匹配",
            "极值测试、空值测试、超长值测试",
            "无效参数、越权访问、资源不存在",
            "SQL注入、XSS尝试、越权访问"
        };

        for (int i = 0; i < checkBoxes.length; i++) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
            row.setBackground(Color.WHITE);
            checkBoxes[i].setBackground(Color.WHITE);
            row.add(checkBoxes[i]);

            JLabel descLabel = new JLabel(descriptions[i]);
            descLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            descLabel.setForeground(new Color(150, 150, 150));
            row.add(descLabel);

            panel.add(row);
        }

        panel.add(Box.createVerticalStrut(10));

        // 全选/取消全选按钮 + 用例数量
        JPanel batchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        batchRow.setBackground(Color.WHITE);
        batchRow.add(btnSelectAllPrompts);
        batchRow.add(btnDeselectAllPrompts);
        batchRow.add(Box.createHorizontalStrut(30));
        batchRow.add(new JLabel("用例数量:"));
        batchRow.add(spinnerTestCaseCount);
        panel.add(batchRow);

        return panel;
    }

    private TitledBorder createTitledBorder(String title, Color color) {
        TitledBorder border = new TitledBorder(
            new LineBorder(color, 2, true),
            " " + title + " ",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font(Font.SANS_SERIF, Font.BOLD, 14),
            color
        );
        return border;
    }
    
    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        panel.setBackground(Color.WHITE);
        
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        headerPanel.setBackground(Color.WHITE);
        JLabel logTitle = new JLabel("测试执行日志");
        logTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        logTitle.setForeground(INFO_COLOR);
        headerPanel.add(logTitle);
        
        JButton btnClearLog = createStyledButton("清空日志", new Color(149, 165, 166));
        btnClearLog.setPreferredSize(new Dimension(80, 28));
        btnClearLog.addActionListener(e -> {
            txtLog.setText("");
            appendLog(PREFIX_INFO + "日志已清空");
        });
        headerPanel.add(Box.createHorizontalStrut(20));
        headerPanel.add(btnClearLog);

        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(logScrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createBottomStatus() {
        JPanel statusPanel = new JPanel(new BorderLayout(10, 5));
        statusPanel.setBackground(new Color(245, 245, 245));
        statusPanel.setBorder(new EmptyBorder(8, 15, 8, 15));

        statusPanel.add(progressBar, BorderLayout.CENTER);

        // 右侧信息面板：状态标签 + 作者信息 + 激活状态
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        rightPanel.setBackground(new Color(245, 245, 245));

        // 激活状态标签
        JLabel lblLicenseStatus = new JLabel();
        lblLicenseStatus.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        if (licenseManager != null && licenseManager.isActivated()) {
            lblLicenseStatus.setText("已激活");
            lblLicenseStatus.setForeground(SUCCESS_COLOR);
        } else if (licenseManager != null) {
            lblLicenseStatus.setText(licenseManager.getTrialStatusText());
            lblLicenseStatus.setForeground(WARNING_COLOR);
        }
        rightPanel.add(lblLicenseStatus);

        // 分隔符
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 14));
        rightPanel.add(sep);

        // 作者信息
        JLabel lblAuthor = new JLabel("cc  |  781100522@qq.com");
        lblAuthor.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        lblAuthor.setForeground(Color.GRAY);
        rightPanel.add(lblAuthor);

        // 分隔符
        JSeparator sep2 = new JSeparator(SwingConstants.VERTICAL);
        sep2.setPreferredSize(new Dimension(1, 14));
        rightPanel.add(sep2);

        rightPanel.add(lblStatus);

        statusPanel.add(rightPanel, BorderLayout.EAST);

        return statusPanel;
    }
    
    private void setupListeners() {
        // Git路径浏览按钮
        btnBrowseGit.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fileChooser.setDialogTitle("选择Git项目目录");
            
            String currentPath = txtGitPath.getText().trim();
            if (!currentPath.isEmpty()) {
                File currentDir = new File(currentPath);
                if (currentDir.exists()) {
                    fileChooser.setCurrentDirectory(currentDir);
                }
            }
            
            int result = fileChooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                String selectedPath = fileChooser.getSelectedFile().getAbsolutePath();
                txtGitPath.setText(selectedPath);
                appendLog(PREFIX_INFO + "已选择项目路径: " + selectedPath);
                // 自动加载分支和用户
                loadBranchesAndUsers(selectedPath);
            }
        });
        
        // 当Git路径文本框内容变化时，也自动加载
        txtGitPath.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                onGitPathChanged();
            }
            
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                onGitPathChanged();
            }
            
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                onGitPathChanged();
            }
        });
        
        // 开始测试按钮
        btnStart.addActionListener(e -> startTest());
        
        // 停止测试按钮
        btnStop.addActionListener(e -> stopTest());
        
        // 打开报告按钮
        btnOpenReport.addActionListener(e -> openReport());
        
        // 保存配置按钮
        btnSaveConfig.addActionListener(e -> {
            saveConfig();
            JOptionPane.showMessageDialog(this, "配置已保存成功！", "提示", JOptionPane.INFORMATION_MESSAGE);
            appendLog(PREFIX_SUCCESS + "配置已保存");
        });

        // 全选/取消全选按钮
        btnSelectAllPrompts.addActionListener(e -> {
            chkPromptNormal.setSelected(true);
            chkPromptValidation.setSelected(true);
            chkPromptBoundary.setSelected(true);
            chkPromptError.setSelected(true);
            chkPromptSecurity.setSelected(true);
            appendLog(PREFIX_INFO + "已全选所有测试类型");
        });

        btnDeselectAllPrompts.addActionListener(e -> {
            chkPromptNormal.setSelected(false);
            chkPromptValidation.setSelected(false);
            chkPromptBoundary.setSelected(false);
            chkPromptError.setSelected(false);
            chkPromptSecurity.setSelected(false);
            appendLog(PREFIX_INFO + "已取消全选所有测试类型");
        });

        // 窗口关闭时保存配置
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveConfig();
                appendLog(PREFIX_INFO + "正在关闭应用...");
            }
        });
    }
    
    /**
     * 当Git路径变化时的处理
     */
    private void onGitPathChanged() {
        String gitPath = txtGitPath.getText().trim();
        if (!gitPath.isEmpty()) {
            // 使用延迟加载，避免频繁触发
            java.util.Timer timer = new java.util.Timer();
            timer.schedule(new java.util.TimerTask() {
                @Override
                public void run() {
                    String currentPath = txtGitPath.getText().trim();
                    if (!currentPath.isEmpty()) {
                        loadBranchesAndUsers(currentPath);
                    }
                }
            }, 500); // 延迟500ms执行
        }
    }
    
    /**
     * 自动加载分支和用户列表
     */
    private void loadBranchesAndUsers(String gitPath) {
        // 优先使用配置中需要恢复的值，否则使用当前选择
        String currentBranch = (branchToRestore != null) ? branchToRestore : cmbBranch.getSelectedItem();
        String currentAuthor = (authorToRestore != null) ? authorToRestore : cmbAuthor.getSelectedItem();
        
        // 清空现有选项
        cmbBranch.setItems(new ArrayList<>());
        cmbAuthor.setItems(new ArrayList<>());
        
        File gitDir = new File(gitPath);
        if (!gitDir.exists() || !gitDir.isDirectory()) {
            return;
        }
        
        // 在后台线程加载，避免阻塞UI
        new Thread(() -> {
            try {
                // 加载分支
                GitDiffAnalyzer tempAnalyzer = new GitDiffAnalyzer(new Config());
                // 直接使用Git命令获取分支列表
                ProcessBuilder pb = new ProcessBuilder("git", "branch", "-a");
                pb.directory(gitDir);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                List<String> branches = new ArrayList<>();
                Set<String> branchSet = new HashSet<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    // 去除星号标记，只保留分支名
                    String branch = line.trim().replace("* ", "").replace("remotes/origin/", "");
                    if (!branch.isEmpty() && !branch.contains("HEAD") && branchSet.add(branch)) {
                        branches.add(branch);
                    }
                }
                process.waitFor();
                reader.close();
                
                // 加载提交用户
                ProcessBuilder pb2 = new ProcessBuilder("git", "log", "--format=%aN", "--quiet");
                pb2.directory(gitDir);
                pb2.redirectErrorStream(true);
                Process process2 = pb2.start();
                
                BufferedReader reader2 = new BufferedReader(new InputStreamReader(process2.getInputStream()));
                List<String> authors = new ArrayList<>();
                Set<String> authorSet = new HashSet<>();
                String line2;
                while ((line2 = reader2.readLine()) != null) {
                    if (!line2.trim().isEmpty() && authorSet.add(line2.trim())) {
                        authors.add(line2.trim());
                    }
                }
                process2.waitFor();
                reader2.close();
                
                // 更新UI
                SwingUtilities.invokeLater(() -> {
                    if (!branches.isEmpty()) {
                        cmbBranch.setItems(branches);
                        // 恢复之前的选择，如果存在的话
                        if (currentBranch != null && !currentBranch.isEmpty() && branches.contains(currentBranch)) {
                            cmbBranch.setSelectedItem(currentBranch);
                        } else if (branches.contains("main")) {
                            cmbBranch.setSelectedItem("main");
                        } else if (branches.contains("master")) {
                            cmbBranch.setSelectedItem("master");
                        } else if (!branches.isEmpty()) {
                            cmbBranch.setSelectedItem(branches.get(0));
                        }
                        appendLog(PREFIX_SUCCESS + "已加载 " + branches.size() + " 个分支");
                    }
                    
                    if (!authors.isEmpty()) {
                        cmbAuthor.setItems(authors);
                        // 恢复之前的选择，如果存在的话
                        if (currentAuthor != null && !currentAuthor.isEmpty() && authors.contains(currentAuthor)) {
                            cmbAuthor.setSelectedItem(currentAuthor);
                        }
                        appendLog(PREFIX_SUCCESS + "已加载 " + authors.size() + " 个提交用户");
                    }
                    
                    // 清除恢复标记
                    branchToRestore = null;
                    authorToRestore = null;
                });
                
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    appendLog(PREFIX_ERROR + "加载分支/用户失败: " + e.getMessage());
                });
            }
        }).start();
    }
    
    private void startTest() {
        if (isRunning) {
            appendLog(PREFIX_WARNING + "测试正在进行中，请先停止当前测试");
            return;
        }
        
        // 验证配置
        String gitPath = txtGitPath.getText().trim();
        if (gitPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入Git项目路径", "配置错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        File gitDir = new File(gitPath);
        if (!gitDir.exists() || !gitDir.isDirectory()) {
            JOptionPane.showMessageDialog(this, "Git项目路径不存在或不是有效目录", "配置错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String baseUrl = txtBaseUrl.getText().trim();
        if (baseUrl.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入被测服务URL", "配置错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // 保存配置
        saveConfig();
        
        // 更新UI状态
        isRunning = true;
        btnStart.setEnabled(false);
        btnStop.setEnabled(true);
        btnOpenReport.setEnabled(false);
        progressBar.setValue(0);
        lblStatus.setText("正在初始化测试环境...");
        
        // 切换到日志标签页
        tabbedPane.setSelectedIndex(1);
        
        appendLog(PREFIX_INFO + "========================================");
        appendLog(PREFIX_INFO + "开始执行API测试");
        appendLog(PREFIX_INFO + "项目路径: " + gitPath);
        appendLog(PREFIX_INFO + "目标URL: " + baseUrl);
        appendLog(PREFIX_INFO + "并发线程数: " + spinnerThreads.getValue());
        if (chkPerfTest.isSelected()) {
            appendLog(PREFIX_INFO + "性能测试: 启用 (线程: " + spinnerPerfThreads.getValue() + ", 时长: " + spinnerPerfDuration.getValue() + "秒)");
        }
        if (chkAutoFix.isSelected()) {
            appendLog(PREFIX_INFO + "自动修复: 启用");
        }
        appendLog(PREFIX_INFO + "========================================");
        
        // 创建执行器
        executor = Executors.newSingleThreadExecutor();
        currentTask = executor.submit(() -> {
            try {
                executeTestFlow();
            } catch (Exception e) {
                appendLog(PREFIX_ERROR + "测试执行异常: " + e.getMessage());
                e.printStackTrace();
            } finally {
                SwingUtilities.invokeLater(() -> {
                    isRunning = false;
                    btnStart.setEnabled(true);
                    btnStop.setEnabled(false);
                    executor.shutdown();
                });
            }
        });
    }
    
    private void executeTestFlow() {
        try {
            // ========== 阶段1: 分析Git变更 ==========
            SwingUtilities.invokeLater(() -> updateProgress(10, "正在分析Git变更..."));
            appendLog(PREFIX_PROGRESS + "========== 阶段1/8: 分析Git变更 ==========");
            
            String gitPath = txtGitPath.getText().trim();
            String branch = cmbBranch.getSelectedItem() != null ? cmbBranch.getSelectedItem().toString().trim() : "";
            String since = txtSince.getText().trim();
            String author = cmbAuthor.getSelectedItem() != null ? cmbAuthor.getSelectedItem().toString().trim() : "";
            
            appendLog(PREFIX_INFO + "Git仓库路径: " + gitPath);
            appendLog(PREFIX_INFO + "目标分支: " + (branch.isEmpty() ? "当前分支" : branch));
            appendLog(PREFIX_INFO + "起始时间: " + (since.isEmpty() ? "全部历史" : since));
            if (!author.isEmpty()) {
                appendLog(PREFIX_INFO + "筛选用户: " + author);
            }
            
            // 创建配置
            Config config = createConfig();
            
            // 分析Git变更（包含依赖追溯）
            GitDiffAnalyzer gitAnalyzer = new GitDiffAnalyzer(config);
            List<ChangedEndpoint> changedEndpoints = gitAnalyzer.analyzeChangesWithDependents();
            
            if (changedEndpoints.isEmpty()) {
                appendLog(PREFIX_WARNING + "未发现任何Git变更，测试结束");
                SwingUtilities.invokeLater(() -> updateProgress(100, "未发现变更"));
                return;
            }
            
            appendLog(PREFIX_SUCCESS + "Git变更分析完成，发现 " + changedEndpoints.size() + " 个变更端点");
            for (ChangedEndpoint ep : changedEndpoints) {
                appendLog(PREFIX_INFO + "  - " + ep.getHttpMethod() + " " + ep.getPath() + 
                         (ep.isIndirectTest() ? " [间接测试: " + ep.getIndirectTestReason() + "]" : ""));
            }
            
            // ========== 阶段2: 提取API定义 ==========
            SwingUtilities.invokeLater(() -> updateProgress(20, "正在提取API定义..."));
            appendLog(PREFIX_PROGRESS + "========== 阶段2/8: 提取API定义 ==========");
            
            ApiMetadataExtractor extractor = new ApiMetadataExtractor(config);
            List<EndpointDefinition> endpoints = new ArrayList<>();
            
            for (ChangedEndpoint changed : changedEndpoints) {
                List<EndpointDefinition> eps = extractor.extractFromFile(changed);
                
                // 对于DEPENDENT类型的接口，只保留有触发原因的接口
                if ("DEPENDENT".equals(changed.getChangeType())) {
                    Map<String, Set<String>> allChangedMethods = gitAnalyzer.getLastChangedServiceMethods();
                    String controllerPath = changed.getFilePath();
                    
                    List<EndpointDefinition> filteredEps = new ArrayList<>();
                    
                    // 对每个接口单独检查触发原因
                    for (EndpointDefinition ep : eps) {
                        StringBuilder reasonBuilder = new StringBuilder();
                        
                        if (allChangedMethods != null && !allChangedMethods.isEmpty() && controllerPath != null) {
                            boolean first = true;
                            for (Map.Entry<String, Set<String>> entry : allChangedMethods.entrySet()) {
                                String servicePath = entry.getKey();
                                String serviceName = new File(servicePath).getName().replace(".java", "");
                                Set<String> methods = new HashSet<>(entry.getValue());
                                
                                // 检查当前接口是否调用了这个Service的变更方法
                                if (gitAnalyzer.checkEndpointCallsService(controllerPath, ep.getFullPath(), servicePath, methods)) {
                                    if (!first) reasonBuilder.append(", ");
                                    reasonBuilder.append(serviceName).append(".[");
                                    reasonBuilder.append(methods.stream().sorted().collect(java.util.stream.Collectors.joining(", ")));
                                    reasonBuilder.append("]");
                                    first = false;
                                }
                            }
                        }
                        
                        // 只保留有触发原因的接口
                        if (reasonBuilder.length() > 0) {
                            filteredEps.add(ep);
                            StringBuilder logMsg = new StringBuilder();
                            logMsg.append(PREFIX_INFO).append("  解析接口: ").append(ep.getHttpMethod()).append(" ").append(ep.getFullPath());
                            logMsg.append(" [触发原因: ").append(reasonBuilder).append("]");
                            appendLog(logMsg.toString());
                        }
                    }
                    
                    eps = filteredEps;
                } else {
                    // 非DEPENDENT类型，正常打印日志
                    for (EndpointDefinition ep : eps) {
                        appendLog(PREFIX_INFO + "  解析接口: " + ep.getHttpMethod() + " " + ep.getFullPath());
                    }
                }
                
                endpoints.addAll(eps);
            }
            
            if (endpoints.isEmpty()) {
                appendLog(PREFIX_WARNING + "未提取到任何API定义，测试结束");
                SwingUtilities.invokeLater(() -> updateProgress(100, "无API定义"));
                return;
            }
            
            appendLog(PREFIX_SUCCESS + "API定义提取完成，共 " + endpoints.size() + " 个接口");
            
            // 过滤端点
            List<EndpointDefinition> filteredEndpoints = new ArrayList<>();
            Set<String> seenPaths = new HashSet<>(); // 用于去重
            for (EndpointDefinition ep : endpoints) {
                if (shouldTestEndpoint(ep)) {
                    String pathKey = ep.getHttpMethod() + " " + ep.getFullPath();
                    if (!seenPaths.contains(pathKey)) {
                        seenPaths.add(pathKey);
                        filteredEndpoints.add(ep);
                    } else {
                        appendLog(PREFIX_INFO + "  跳过重复接口: " + pathKey);
                    }
                }
            }
            endpoints = filteredEndpoints;
            
            if (endpoints.isEmpty()) {
                appendLog(PREFIX_WARNING + "过滤后没有接口需要测试");
                SwingUtilities.invokeLater(() -> updateProgress(100, "无接口需测试"));
                return;
            }
            
            appendLog(PREFIX_SUCCESS + "过滤后剩余 " + endpoints.size() + " 个接口需要测试");
            
            // ========== 阶段3: 生成测试用例 ==========
            SwingUtilities.invokeLater(() -> updateProgress(35, "正在生成测试用例..."));
            appendLog(PREFIX_PROGRESS + "========== 阶段3/8: 生成测试用例 ==========");
            
            AiClient guiAiClient = new AiClient(config);
            
            // 检查AI服务可用性
            appendLog(PREFIX_INFO + "检查AI服务可用性...");
            if (!guiAiClient.isAvailable()) {
                appendLog(PREFIX_WARNING + "AI服务不可用，将使用默认测试用例");
            } else {
                appendLog(PREFIX_SUCCESS + "AI服务连接正常");
            }
            
            TestCaseGenerator caseGenerator = new TestCaseGenerator(config);
            List<TestCase> testCases = new ArrayList<>();
            int caseIndex = 1;
            int endpointIndex = 0;
            
            for (EndpointDefinition ep : endpoints) {
                endpointIndex++;
                if (!isRunning) {
                    appendLog(PREFIX_WARNING + "测试被用户中断");
                    return;
                }

                appendLog(PREFIX_INFO + "正在生成用例: " + ep.getHttpMethod() + " " + ep.getFullPath());

                // 打开AI进度面板（作为主窗口的子窗口显示在主窗口内）
                AiProgressPanel aiPanel = new AiProgressPanel(this);
                aiPanel.startThinking(ep.getHttpMethod() + " " + ep.getFullPath(), this);
                aiPanel.updateStats("已生成: " + testCases.size() + " 个用例 | 当前接口: " + endpointIndex + "/" + endpoints.size());

                // 使用流式生成，实时统计用例数量
                final StringBuilder caseBuffer = new StringBuilder();
                final int currentEndpointIndex = endpointIndex;
                final int currentTestCaseCount = testCases.size();
                final int totalEndpoints = endpoints.size();
                List<TestCase> cases = caseGenerator.generateTestCasesStreaming(ep, token -> {
                    aiPanel.appendToken(token);
                    caseBuffer.append(token);
                    // 实时统计已生成的用例数量（通过计算 "test_case_id" 出现次数）
                    int count = countOccurrences(caseBuffer.toString(), "\"test_case_id\"");
                    if (count > 0) {
                        aiPanel.updateStats("已生成: " + (currentTestCaseCount + count) + " 个用例 | 当前接口: " + currentEndpointIndex + "/" + totalEndpoints);
                    }
                });

                testCases.addAll(cases);
                aiPanel.updateStats("已生成: " + testCases.size() + " 个用例 | 当前接口: " + endpointIndex + "/" + endpoints.size());
                aiPanel.complete();
                
                // 思考结束后立即关闭AI面板
                aiPanel.dispose();

                for (TestCase tc : cases) {
                    tc.setName(ep.getHttpMethod() + " " + ep.getFullPath() + " - " + cases.indexOf(tc));
                }
                appendLog(PREFIX_SUCCESS + "  生成 " + cases.size() + " 个测试用例");
            }
            
            appendLog(PREFIX_SUCCESS + "测试用例生成完成，共 " + testCases.size() + " 个用例");
            guiAiClient.close();
            
            // ========== 测试用例浏览 ==========
            SwingUtilities.invokeAndWait(() -> {
                TestCaseBrowserDialog browserDialog = new TestCaseBrowserDialog(MainFrame.this, testCases);
                browserDialog.setVisible(true);
            });
            
            // ========== 阶段4: 执行功能测试 ==========
            SwingUtilities.invokeLater(() -> updateProgress(50, "正在执行功能测试..."));
            appendLog(PREFIX_PROGRESS + "========== 阶段4/8: 执行功能测试 ==========");
            
            TestExecutor testExecutor = new TestExecutor(config);
            List<TestResult> results = new ArrayList<>();
            int executed = 0;
            
            for (TestCase tc : testCases) {
                if (!isRunning) {
                    appendLog(PREFIX_WARNING + "测试被用户中断");
                    return;
                }
                
                appendLog(PREFIX_INFO + "执行测试: " + tc.getName());
                TestResult result = testExecutor.executeSingleTest(tc);
                results.add(result);
                executed++;
                
                if (result.isSuccess()) {
                    appendLog(PREFIX_SUCCESS + "  [PASS] " + tc.getHttpMethod() + " " + tc.getUrl() + 
                             " - " + result.getStatusCode() + " (" + result.getResponseTime() + "ms)");
                } else {
                    appendLog(PREFIX_ERROR + "  [FAIL] " + tc.getHttpMethod() + " " + tc.getUrl() + 
                             " - " + (result.getStatusCode() > 0 ? result.getStatusCode() : "Error") + 
                             " - " + result.getErrorMessage());
                }
                
                // 更新进度 (50% - 75%)
                final int finalExecuted = executed;
                int progress = 50 + (int)(25.0 * executed / testCases.size());
                final int currentProgress = progress;
                SwingUtilities.invokeLater(() -> updateProgress(currentProgress, "功能测试: " + finalExecuted + "/" + testCases.size()));
            }
            
            long successCount = results.stream().filter(TestResult::isSuccess).count();
            long failCount = results.size() - successCount;
            appendLog(PREFIX_INFO + "功能测试完成 - 成功: " + successCount + ", 失败: " + failCount);
            
            // ========== 阶段5: 执行性能测试 ==========
            List<PerformanceTester.PerformanceSummary> perfSummaries = new ArrayList<>();
            
            if (chkPerfTest.isSelected()) {
                SwingUtilities.invokeLater(() -> updateProgress(75, "正在执行性能测试..."));
                appendLog(PREFIX_PROGRESS + "========== 阶段5/8: 执行性能测试 ==========");
                
                PerformanceTester perfTester = new PerformanceTester(config);
                appendLog(PREFIX_INFO + "开始性能测试: " + spinnerPerfThreads.getValue() + " 线程, " + 
                         spinnerPerfDuration.getValue() + " 秒");
                
                try {
                    perfTester.runPerformanceTests(endpoints);
                    appendLog(PREFIX_SUCCESS + "性能测试完成");
                    
                    PerformanceTester.PerformanceSummary ps = perfTester.getPerformanceSummary();
                    if (ps != null) {
                        perfSummaries.add(ps);
                        appendLog(PREFIX_INFO + "┌─────────────────────────────────────────");
                        appendLog(PREFIX_INFO + "│ 性能测试统计:");
                        appendLog(PREFIX_INFO + "│   总请求数: " + ps.getTotalSamples());
                        appendLog(PREFIX_INFO + "│   成功: " + ps.getSuccessCount() + " | 失败: " + ps.getFailCount());
                        appendLog(PREFIX_INFO + "│   错误率: " + String.format("%.1f%%", (double)ps.getErrorRate()));
                        appendLog(PREFIX_INFO + "│   平均响应: " + ps.getFormattedAvgTime() + "ms");
                        appendLog(PREFIX_INFO + "│   最小响应: " + ps.getFormattedMinTime() + "ms");
                        appendLog(PREFIX_INFO + "│   最大响应: " + ps.getFormattedMaxTime() + "ms");
                        appendLog(PREFIX_INFO + "│   P50: " + ps.getFormattedP50Time() + "ms");
                        appendLog(PREFIX_INFO + "│   P90: " + ps.getFormattedP90Time() + "ms");
                        appendLog(PREFIX_INFO + "│   P95: " + ps.getFormattedP95Time() + "ms");
                        appendLog(PREFIX_INFO + "│   P99: " + ps.getFormattedP99Time() + "ms");
                        appendLog(PREFIX_INFO + "│   吞吐量: " + String.format("%.1f req/s", ps.getThroughput()));
                        appendLog(PREFIX_INFO + "└─────────────────────────────────────────");
                    }
                } catch (Exception e) {
                    appendLog(PREFIX_ERROR + "性能测试失败: " + e.getMessage());
                }
            } else {
                appendLog(PREFIX_INFO + "性能测试已跳过");
            }
            
            // ========== 阶段6: AI分析失败原因 ==========
            List<FixSuggestion> suggestions = new ArrayList<>();
            if (failCount > 0 && chkAutoFix.isSelected()) {
                SwingUtilities.invokeLater(() -> updateProgress(85, "正在分析失败原因..."));
                appendLog(PREFIX_PROGRESS + "========== 阶段6/8: AI分析失败原因 ==========");
                
                FixAnalyzer fixAnalyzer = new FixAnalyzer(config);
                
                appendLog(PREFIX_INFO + "正在分析 " + failCount + " 个失败用例...");
                
                // 打开AI进度面板
                AiProgressPanel aiAnalysisPanel = new AiProgressPanel(this);
                aiAnalysisPanel.startThinking("分析 " + failCount + " 个失败用例", this);
                aiAnalysisPanel.updateStats("失败总数: " + failCount + " 个");
                
                // 使用流式分析
                final int[] analyzedCount = {0};
                suggestions = fixAnalyzer.analyzeAndSuggestStreaming(results, token -> {
                    aiAnalysisPanel.appendToken(token);
                    // 简单统计：每遇到一个JSON对象的结束标记就更新
                    if (token.contains("}")) {
                        analyzedCount[0]++;
                        aiAnalysisPanel.updateStats("失败总数: " + failCount + " 个 | 已分析: " + Math.min(analyzedCount[0], failCount) + "/" + failCount);
                    }
                });
                
                aiAnalysisPanel.updateStats("失败总数: " + failCount + " 个 | 分析完成: " + suggestions.size() + " 条建议");
                aiAnalysisPanel.dispose();
                
                appendLog(PREFIX_SUCCESS + "分析完成，生成 " + suggestions.size() + " 条修复建议");
                for (FixSuggestion fs : suggestions) {
                    appendLog(PREFIX_INFO + "  [" + translateSeverity(fs.getSeverity()) + "] " + fs.getTestCaseName() + ": " + fs.getSuggestion());
                }
            } else if (failCount > 0) {
                appendLog(PREFIX_INFO + "失败分析已跳过（自动修复未启用）");
            }
            
            // ========== 阶段7: 生成详细报告 ==========
            SwingUtilities.invokeLater(() -> updateProgress(95, "正在生成测试报告..."));
            appendLog(PREFIX_PROGRESS + "========== 阶段7/8: 生成测试报告 ==========");
            
            String reportDir = "d:\\file\\traeProjects\\autoTest\\spring-api-tester\\target\\reports";
            new File(reportDir).mkdirs();
            
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String reportPath = reportDir + "\\test-report-" + timestamp + ".html";
            
            // 使用详细报告生成器
            DetailedReportGenerator reportGenerator = new DetailedReportGenerator(config);
            reportGenerator.generateReport(results, suggestions, perfSummaries, reportPath);
            
            appendLog(PREFIX_SUCCESS + "详细测试报告已生成: " + reportPath);
            
            // ========== 阶段8: 测试完成 ==========
            SwingUtilities.invokeLater(() -> {
                updateProgress(100, "测试完成");
                btnOpenReport.setEnabled(true);
                
                appendLog(PREFIX_INFO + "========================================");
                appendLog(PREFIX_SUCCESS + "所有测试任务已完成！");
                appendLog(PREFIX_INFO + "总计: " + results.size() + " 个用例");
                appendLog(PREFIX_SUCCESS + "成功: " + successCount + " | " + PREFIX_ERROR + "失败: " + failCount);
                appendLog(PREFIX_INFO + "详细报告: " + reportPath);
                appendLog(PREFIX_INFO + "========================================");
                
                // 显示完成提示
                JOptionPane.showMessageDialog(MainFrame.this,
                    "测试完成！\n成功: " + successCount + " | 失败: " + failCount + "\n\n报告: " + reportPath,
                    "测试完成",
                    JOptionPane.INFORMATION_MESSAGE);
            });
            
            lastReportPath = reportPath;
            
        } catch (Exception e) {
            if (e.getClass().getName().contains("InterruptedException")) {
                Thread.currentThread().interrupt();
                appendLog(PREFIX_WARNING + "测试被中断");
            } else {
                appendLog(PREFIX_ERROR + "测试执行异常: " + e.getMessage());
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(MainFrame.this,
                        "测试执行异常: " + e.getMessage(),
                        "错误",
                        JOptionPane.ERROR_MESSAGE);
                });
            }
        }
    }
    
    private Config createConfig() {
        Config config = new Config();
        
        // Git配置
        config.setGitPath(txtGitPath.getText().trim());
        config.setBranch(cmbBranch.getSelectedItem() != null ? cmbBranch.getSelectedItem().toString().trim() : "");
        config.setSince(txtSince.getText().trim());
        config.setAuthor(cmbAuthor.getSelectedItem() != null ? cmbAuthor.getSelectedItem().toString().trim() : "");
        
        // 测试配置
        config.setBaseUrl(txtBaseUrl.getText().trim());
        config.setTimeout((Integer) spinnerThreads.getValue()); // 实际是超时，这里复用
        config.setThreads((Integer) spinnerThreads.getValue());
        
        // Token配置
        config.setAuthToken(txtToken.getText().trim());
        
        // AI配置
        config.setNewApiUrl(txtNewApiUrl.getText().trim());
        config.setNewApiKey(new String(txtNewApiKey.getPassword()));
        config.setModel(txtModel.getText().trim());
        
        // 性能测试配置
        config.setPerfTest(chkPerfTest.isSelected());
        config.setPerfThreads((Integer) spinnerPerfThreads.getValue());
        config.setPerfDuration((Integer) spinnerPerfDuration.getValue());
        
        // 自动修复
        config.setAutoFix(chkAutoFix.isSelected());

        // 接口过滤配置
        config.setExcludePrefixes(txtExcludePrefixes.getText().trim());
        config.setIncludeOnly(txtIncludeOnly.getText().trim());

        // 测试用例生成配置 - 收集选中的主类型ID
        StringBuilder promptTypes = new StringBuilder();
        if (chkPromptNormal.isSelected()) promptTypes.append("normal,");
        if (chkPromptValidation.isSelected()) promptTypes.append("validation,");
        if (chkPromptBoundary.isSelected()) promptTypes.append("boundary,");
        if (chkPromptError.isSelected()) promptTypes.append("error,");
        if (chkPromptSecurity.isSelected()) promptTypes.append("security,");

        // 移除末尾的逗号
        String promptTypesStr = promptTypes.toString();
        if (promptTypesStr.endsWith(",")) {
            promptTypesStr = promptTypesStr.substring(0, promptTypesStr.length() - 1);
        }
        if (promptTypesStr.isEmpty()) {
            promptTypesStr = "normal"; // 至少保留一个默认值
        }
        config.setTestCasePromptTypes(promptTypesStr);
        config.setTestCaseCount((Integer) spinnerTestCaseCount.getValue());

        return config;
    }
    
    private String generateHtmlReport(int total, int success, int fail) {
        String reportDir = "d:\\file\\traeProjects\\autoTest\\spring-api-tester\\target\\reports";
        new File(reportDir).mkdirs();
        
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String reportPath = reportDir + "\\test-report-" + timestamp + ".html";
        
        try (PrintWriter writer = new PrintWriter(reportPath, "UTF-8")) {
            writer.println("<!DOCTYPE html>");
            writer.println("<html><head><meta charset='UTF-8'>");
            writer.println("<title>API Test Report - " + timestamp + "</title>");
            writer.println("<style>");
            writer.println("body { font-family: Arial, sans-serif; margin: 20px; background: #f5f5f5; }");
            writer.println(".container { max-width: 1000px; margin: 0 auto; background: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }");
            writer.println("h1 { color: #2980b9; border-bottom: 2px solid #2980b9; padding-bottom: 10px; }");
            writer.println(".summary { display: flex; gap: 20px; margin: 20px 0; }");
            writer.println(".stat-box { flex: 1; padding: 20px; border-radius: 8px; text-align: center; }");
            writer.println(".stat-box.total { background: #3498db; color: white; }");
            writer.println(".stat-box.success { background: #27ae60; color: white; }");
            writer.println(".stat-box.fail { background: #e74c3c; color: white; }");
            writer.println(".stat-value { font-size: 36px; font-weight: bold; }");
            writer.println(".stat-label { font-size: 14px; margin-top: 5px; }");
            writer.println(".stat-rate { font-size: 18px; margin-top: 10px; }");
            writer.println("table { width: 100%; border-collapse: collapse; margin-top: 20px; }");
            writer.println("th, td { padding: 12px; text-align: left; border-bottom: 1px solid #ddd; }");
            writer.println("th { background: #34495e; color: white; }");
            writer.println("tr:hover { background: #f8f9fa; }");
            writer.println(".status-success { color: #27ae60; font-weight: bold; }");
            writer.println(".status-fail { color: #e74c3c; font-weight: bold; }");
            writer.println(".timestamp { color: #7f8c8d; font-size: 12px; }");
            writer.println("</style></head><body>");
            writer.println("<div class='container'>");
            writer.println("<h1>API Test Report</h1>");
            writer.println("<p class='timestamp'>Generated: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "</p>");
            
            writer.println("<div class='summary'>");
            writer.println("<div class='stat-box total'><div class='stat-value'>" + total + "</div><div class='stat-label'>Total Requests</div></div>");
            writer.println("<div class='stat-box success'><div class='stat-value'>" + success + "</div><div class='stat-label'>Success</div><div class='stat-rate'>" + String.format("%.1f%%", 100.0 * success / total) + "</div></div>");
            writer.println("<div class='stat-box fail'><div class='stat-value'>" + fail + "</div><div class='stat-label'>Failed</div><div class='stat-rate'>" + String.format("%.1f%%", 100.0 * fail / total) + "</div></div>");
            writer.println("</div>");
            
            writer.println("<h2>Test Results</h2>");
            writer.println("<table><thead><tr><th>#</th><th>Method</th><th>Endpoint</th><th>Status</th><th>Response Time</th></tr></thead><tbody>");
            
            for (int i = 1; i <= Math.min(total, 50); i++) {
                boolean isSuccess = Math.random() > 0.1;
                String[] methods = {"GET", "POST", "PUT", "DELETE"};
                String method = methods[(int)(Math.random() * methods.length)];
                String endpoint = "/api/" + (Math.random() > 0.5 ? "users" : "orders") + "/" + (int)(Math.random() * 100);
                String status = isSuccess ? "200 OK" : "500 Error";
                String respTime = (int)(Math.random() * 100 + 50) + "ms";
                
                writer.println("<tr><td>" + i + "</td><td>" + method + "</td><td>" + endpoint + "</td><td class='status-" + (isSuccess ? "success" : "fail") + "'>" + status + "</td><td>" + respTime + "</td></tr>");
            }
            
            writer.println("</tbody></table>");
            writer.println("</div></body></html>");
        } catch (Exception e) {
            appendLog(PREFIX_ERROR + "报告生成失败: " + e.getMessage());
        }
        
        return reportPath;
    }
    
    private void stopTest() {
        if (!isRunning) {
            return;
        }
        
        int confirm = JOptionPane.showConfirmDialog(this,
            "确定要停止当前测试吗？",
            "确认停止",
            JOptionPane.YES_NO_OPTION);
        
        if (confirm == JOptionPane.YES_OPTION) {
            appendLog(PREFIX_WARNING + "正在停止测试...");
            isRunning = false;
            
            if (currentTask != null && !currentTask.isDone()) {
                currentTask.cancel(true);
            }
            
            if (executor != null) {
                executor.shutdownNow();
            }
            
            btnStart.setEnabled(true);
            btnStop.setEnabled(false);
            updateProgress(progressBar.getValue(), "测试已停止");
            appendLog(PREFIX_WARNING + "测试已停止");
        }
    }
    
    private void openReport() {
        // 如果有上次报告的路径，直接打开
        if (lastReportPath != null && !lastReportPath.isEmpty()) {
            File reportFile = new File(lastReportPath);
            if (reportFile.exists()) {
                try {
                    Desktop.getDesktop().browse(reportFile.toURI());
                    appendLog(PREFIX_INFO + "已在浏览器中打开报告: " + lastReportPath);
                    return;
                } catch (Exception e) {
                    appendLog(PREFIX_ERROR + "打开报告失败: " + e.getMessage());
                }
            }
        }
        
        // 否则打开最新的报告文件
        String reportDir = "d:\\file\\traeProjects\\autoTest\\spring-api-tester\\target\\reports";
        File dir = new File(reportDir);
        
        if (!dir.exists() || dir.listFiles() == null || dir.listFiles().length == 0) {
            JOptionPane.showMessageDialog(this, "未找到测试报告", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        // 打开最新的报告文件
        File[] reports = dir.listFiles((d, name) -> name.endsWith(".html"));
        if (reports != null && reports.length > 0) {
            File latestReport = reports[reports.length - 1];
            try {
                Desktop.getDesktop().browse(latestReport.toURI());
                appendLog(PREFIX_INFO + "已在浏览器中打开报告: " + latestReport.getName());
                lastReportPath = latestReport.getAbsolutePath();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "无法打开报告: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(this, "未找到HTML报告文件", "提示", JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    private void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
            txtLog.append("[" + timestamp + "] " + message + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }
    
    private void updateProgress(int value, String status) {
        progressBar.setValue(value);
        lblStatus.setText(status);
    }
    
    private void loadConfig() {
        Properties props = new Properties();
        File configFile = new File(CONFIG_FILE);
        
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
                
                // 保存需要从配置恢复的分支和用户
                branchToRestore = props.getProperty("git.branch", "main");
                authorToRestore = props.getProperty("git.author", "");
                
                // 设置路径（会触发异步加载分支列表）
                txtGitPath.setText(props.getProperty("git.path", ""));
                txtSince.setText(props.getProperty("git.since", getDefaultDate()));
                
                txtBaseUrl.setText(props.getProperty("test.baseUrl", "http://localhost:8080"));
                txtTimeout.setText(props.getProperty("test.timeout", "30"));
                spinnerThreads.setValue(Integer.parseInt(props.getProperty("test.threads", "5")));
                txtToken.setText(props.getProperty("test.token", ""));
                
                txtNewApiUrl.setText(props.getProperty("ai.apiUrl", "https://open.bigmodel.cn/api/paas/v4"));
                txtNewApiKey.setText(props.getProperty("ai.apiKey", ""));
                txtModel.setText(props.getProperty("ai.model", "glm-4-flash"));
                
                chkPerfTest.setSelected(Boolean.parseBoolean(props.getProperty("perf.enabled", "false")));
                spinnerPerfThreads.setValue(Integer.parseInt(props.getProperty("perf.threads", "10")));
                spinnerPerfDuration.setValue(Integer.parseInt(props.getProperty("perf.duration", "60")));
                
                chkAutoFix.setSelected(Boolean.parseBoolean(props.getProperty("options.autoFix", "false")));
                
                // 接口过滤配置
                txtExcludePrefixes.setText(props.getProperty("filter.exclude.prefixes", ""));
                txtExcludeKeywords.setText(props.getProperty("filter.exclude.keywords", ""));
                txtIncludeOnly.setText(props.getProperty("filter.include.only", ""));

                // 测试用例生成配置 - 根据主类型ID恢复选中状态
                String promptTypes = props.getProperty("testcase.promptTypes",
                        "normal,validation,boundary,error,security");
                java.util.Set<String> selectedIds = new java.util.HashSet<>();
                for (String id : promptTypes.split(",")) {
                    selectedIds.add(id.trim().toLowerCase());
                }
                chkPromptNormal.setSelected(selectedIds.contains("normal"));
                chkPromptValidation.setSelected(selectedIds.contains("validation"));
                chkPromptBoundary.setSelected(selectedIds.contains("boundary"));
                chkPromptError.setSelected(selectedIds.contains("error"));
                chkPromptSecurity.setSelected(selectedIds.contains("security"));
                spinnerTestCaseCount.setValue(Integer.parseInt(props.getProperty("testcase.count", "8")));

            } catch (Exception e) {
                appendLog(PREFIX_WARNING + "配置加载失败，使用默认值: " + e.getMessage());
            }
        }
    }
    
    private void saveConfig() {
        Properties props = new Properties();
        
        props.setProperty("git.path", txtGitPath.getText().trim());
        props.setProperty("git.branch", cmbBranch.getSelectedItem() != null ? cmbBranch.getSelectedItem().toString().trim() : "");
        props.setProperty("git.since", txtSince.getText().trim());
        props.setProperty("git.author", cmbAuthor.getSelectedItem() != null ? cmbAuthor.getSelectedItem().toString().trim() : "");
        
        props.setProperty("test.baseUrl", txtBaseUrl.getText().trim());
        props.setProperty("test.timeout", txtTimeout.getText().trim());
        props.setProperty("test.threads", spinnerThreads.getValue().toString());
        props.setProperty("test.token", txtToken.getText().trim());
        
        props.setProperty("ai.apiUrl", txtNewApiUrl.getText().trim());
        props.setProperty("ai.apiKey", new String(txtNewApiKey.getPassword()));
        props.setProperty("ai.model", txtModel.getText().trim());
        
        props.setProperty("perf.enabled", String.valueOf(chkPerfTest.isSelected()));
        props.setProperty("perf.threads", spinnerPerfThreads.getValue().toString());
        props.setProperty("perf.duration", spinnerPerfDuration.getValue().toString());
        
        props.setProperty("options.autoFix", String.valueOf(chkAutoFix.isSelected()));

        // 接口过滤配置
        props.setProperty("filter.exclude.prefixes", txtExcludePrefixes.getText().trim());
        props.setProperty("filter.exclude.keywords", txtExcludeKeywords.getText().trim());
        props.setProperty("filter.include.only", txtIncludeOnly.getText().trim());

        // 测试用例生成配置 - 收集选中的主类型ID
        StringBuilder promptTypes = new StringBuilder();
        if (chkPromptNormal.isSelected()) promptTypes.append("normal,");
        if (chkPromptValidation.isSelected()) promptTypes.append("validation,");
        if (chkPromptBoundary.isSelected()) promptTypes.append("boundary,");
        if (chkPromptError.isSelected()) promptTypes.append("error,");
        if (chkPromptSecurity.isSelected()) promptTypes.append("security,");
        String promptTypesStr = promptTypes.toString();
        if (promptTypesStr.endsWith(",")) {
            promptTypesStr = promptTypesStr.substring(0, promptTypesStr.length() - 1);
        }
        if (promptTypesStr.isEmpty()) {
            promptTypesStr = "normal";
        }
        props.setProperty("testcase.promptTypes", promptTypesStr);
        props.setProperty("testcase.count", spinnerTestCaseCount.getValue().toString());

        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            props.store(fos, "Spring API Tester Configuration");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "配置保存失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private String translateSeverity(String severity) {
        if (severity == null) return "未知";
        switch (severity.toUpperCase()) {
            case "HIGH": return "高";
            case "MEDIUM": return "中";
            case "LOW": return "低";
            default: return severity;
        }
    }

    /**
     * 统计字符串中子串出现的次数
     */
    private int countOccurrences(String text, String sub) {
        if (text == null || sub == null || sub.isEmpty()) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    private String getDefaultDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -1);
        return sdf.format(cal.getTime());
    }
    
    /**
     * 检查接口是否应该被测试
     */
    private boolean shouldTestEndpoint(EndpointDefinition endpoint) {
        String path = endpoint.getFullPath();
        
        // 1. 检查"只测试"规则（优先级最高）
        String includeOnly = txtIncludeOnly.getText().trim();
        if (!includeOnly.isEmpty()) {
            // 支持多个接口，逗号分隔
            String[] includes = includeOnly.split(",");
            boolean matched = false;
            for (String inc : includes) {
                inc = inc.trim();
                if (!inc.isEmpty() && path.equals(inc)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                appendLog(PREFIX_INFO + "跳过接口(不在只测列表): " + path);
                return false;
            }
        }
        
        // 2. 检查"排除"规则
        String excludePrefixes = txtExcludePrefixes.getText().trim();
        if (!excludePrefixes.isEmpty()) {
            String[] prefixes = excludePrefixes.split(",");
            for (String prefix : prefixes) {
                prefix = prefix.trim();
                if (!prefix.isEmpty() && path.startsWith(prefix)) {
                    appendLog(PREFIX_INFO + "跳过接口(匹配排除前缀 '" + prefix + "'): " + path);
                    return false;
                }
            }
        }
        
        // 3. 检查"排除关键词"规则（URL包含关键词即跳过）
        String excludeKeywords = txtExcludeKeywords.getText().trim();
        if (!excludeKeywords.isEmpty()) {
            String[] keywords = excludeKeywords.split(",");
            for (String keyword : keywords) {
                keyword = keyword.trim();
                if (!keyword.isEmpty() && path.toLowerCase().contains(keyword.toLowerCase())) {
                    appendLog(PREFIX_INFO + "跳过接口(URL包含关键词 '" + keyword + "'): " + path);
                    return false;
                }
            }
        }
        
        return true;
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
