package com.autotest.gui;

import com.autotest.executor.TestCase;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * 测试用例浏览对话框
 * 用于查看、添加、编辑、删除测试用例，以及设置性能测试用例
 */
public class TestCaseBrowserDialog extends JDialog {
    
    // 颜色定义
    private static final Color PRIMARY_COLOR = new Color(41, 128, 185);
    private static final Color SUCCESS_COLOR = new Color(39, 174, 96);
    private static final Color ERROR_COLOR = new Color(231, 76, 60);
    private static final Color INFO_COLOR = new Color(52, 73, 94);
    
    // 组件
    private JList<String> caseList;           // 用例列表
    private DefaultListModel<String> listModel;
    private JTextArea detailArea;            // 详情区域
    private JButton btnAdd;                  // 添加用例按钮
    private JButton btnEdit;                 // 编辑用例按钮
    private JButton btnDelete;               // 删除用例按钮
    private JButton btnSetPerf;               // 设为性能测试按钮
    private JButton btnStartTest;            // 开始测试按钮
    
    // 数据
    private List<TestCase> testCases;        // 测试用例列表引用
    private Map<String, TestCase> caseMap;   // 名称到用例的映射
    private JFrame parentFrame;              // 父窗口
    
    /**
     * 构造函数
     * @param parent 父窗口
     * @param testCases 测试用例列表（引用）
     */
    public TestCaseBrowserDialog(JFrame parent, List<TestCase> testCases) {
        super(parent, "测试用例浏览", true);
        this.parentFrame = parent;
        this.testCases = testCases;
        this.caseMap = new LinkedHashMap<>();
        
        initComponents();
        loadCases();
        setupLayout();
        setupListeners();
        
        setSize(900, 600);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }
    
    private void initComponents() {
        // 初始化用例列表
        listModel = new DefaultListModel<>();
        caseList = new JList<>(listModel);
        caseList.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        caseList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        caseList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, 
                    int index, boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                String caseName = value.toString();
                TestCase tc = caseMap.get(caseName);
                if (tc != null && tc.getTags() != null && tc.getTags().contains("performance")) {
                    setText(value + " [性能]");
                }
                return c;
            }
        });
        
        // 初始化详情区域
        detailArea = new JTextArea();
        detailArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        detailArea.setEditable(false);
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        detailArea.setBackground(new Color(248, 248, 248));
        detailArea.setForeground(INFO_COLOR);
        
        // 初始化按钮
        btnAdd = createStyledButton("添加用例", SUCCESS_COLOR);
        btnAdd.setPreferredSize(new Dimension(100, 32));
        
        btnEdit = createStyledButton("编辑用例", PRIMARY_COLOR);
        btnEdit.setPreferredSize(new Dimension(100, 32));
        
        btnDelete = createStyledButton("删除用例", ERROR_COLOR);
        btnDelete.setPreferredSize(new Dimension(100, 32));
        
        btnSetPerf = createStyledButton("设为性能测试", new Color(230, 126, 34));
        btnSetPerf.setPreferredSize(new Dimension(120, 32));
        
        btnStartTest = createStyledButton("开始测试", SUCCESS_COLOR);
        btnStartTest.setPreferredSize(new Dimension(100, 36));
        btnStartTest.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
    }
    
    private void setupLayout() {
        setLayout(new BorderLayout(10, 10));
        
        // 左侧：用例列表
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setBorder(new TitledBorder(new LineBorder(PRIMARY_COLOR, 1, true), 
                "用例列表", TitledBorder.LEFT, TitledBorder.TOP, 
                new Font(Font.SANS_SERIF, Font.BOLD, 13), PRIMARY_COLOR));
        
        JScrollPane listScroll = new JScrollPane(caseList);
        listScroll.setPreferredSize(new Dimension(300, 0));
        leftPanel.add(listScroll, BorderLayout.CENTER);
        
        // 右侧：详情区域
        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        rightPanel.setBorder(new TitledBorder(new LineBorder(INFO_COLOR, 1, true), 
                "用例详情", TitledBorder.LEFT, TitledBorder.TOP, 
                new Font(Font.SANS_SERIF, Font.BOLD, 13), INFO_COLOR));
        
        JScrollPane detailScroll = new JScrollPane(detailArea);
        rightPanel.add(detailScroll, BorderLayout.CENTER);
        
        // 底部：按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        buttonPanel.setBackground(Color.WHITE);
        buttonPanel.add(btnAdd);
        buttonPanel.add(btnEdit);
        buttonPanel.add(btnDelete);
        buttonPanel.add(Box.createHorizontalStrut(20));
        buttonPanel.add(btnSetPerf);
        buttonPanel.add(Box.createHorizontalGlue());
        buttonPanel.add(btnStartTest);
        
        // 组装主面板
        JPanel centerPanel = new JPanel(new BorderLayout(10, 5));
        centerPanel.setBackground(Color.WHITE);
        centerPanel.setBorder(new EmptyBorder(10, 10, 5, 10));
        centerPanel.add(leftPanel, BorderLayout.WEST);
        centerPanel.add(rightPanel, BorderLayout.CENTER);
        
        add(centerPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }
    
    private void setupListeners() {
        // 列表选择事件
        caseList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    updateDetail();
                }
            }
        });
        
        // 添加用例按钮
        btnAdd.addActionListener(e -> showAddEditDialog(null));
        
        // 编辑用例按钮
        btnEdit.addActionListener(e -> {
            String selected = caseList.getSelectedValue();
            if (selected != null) {
                TestCase tc = caseMap.get(selected);
                if (tc != null) {
                    showAddEditDialog(tc);
                }
            } else {
                JOptionPane.showMessageDialog(this, "请先选择一个用例", "提示", JOptionPane.WARNING_MESSAGE);
            }
        });
        
        // 删除用例按钮
        btnDelete.addActionListener(e -> {
            String selected = caseList.getSelectedValue();
            if (selected != null) {
                int confirm = JOptionPane.showConfirmDialog(this, 
                        "确定删除用例: " + selected + "?", "确认删除", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    TestCase tc = caseMap.get(selected);
                    if (tc != null) {
                        testCases.remove(tc);
                        loadCases();
                    }
                }
            } else {
                JOptionPane.showMessageDialog(this, "请先选择一个用例", "提示", JOptionPane.WARNING_MESSAGE);
            }
        });
        
        // 设为性能测试按钮
        btnSetPerf.addActionListener(e -> {
            String selected = caseList.getSelectedValue();
            if (selected != null) {
                TestCase tc = caseMap.get(selected);
                if (tc != null) {
                    boolean isPerf = tc.getTags() != null && tc.getTags().contains("performance");
                    if (isPerf) {
                        // 移除性能标签
                        if (tc.getTags() != null) {
                            tc.getTags().remove("performance");
                        }
                    } else {
                        // 添加性能标签
                        if (tc.getTags() == null) {
                            tc.setTags(new ArrayList<>());
                        }
                        tc.getTags().add("performance");
                    }
                    loadCases();
                }
            } else {
                JOptionPane.showMessageDialog(this, "请先选择一个用例", "提示", JOptionPane.WARNING_MESSAGE);
            }
        });
        
        // 开始测试按钮
        btnStartTest.addActionListener(e -> {
            dispose();
        });
        
        // 窗口关闭事件
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dispose();
            }
        });
    }
    
    /**
     * 加载用例到列表
     */
    private void loadCases() {
        listModel.clear();
        caseMap.clear();
        
        for (TestCase tc : testCases) {
            String name = tc.getName();
            if (name == null || name.isEmpty()) {
                name = tc.getHttpMethod() + " " + tc.getEndpointUrl();
            }
            listModel.addElement(name);
            caseMap.put(name, tc);
        }
    }
    
    /**
     * 更新详情区域
     */
    private void updateDetail() {
        String selected = caseList.getSelectedValue();
        if (selected == null) {
            detailArea.setText("请选择一个用例查看详情");
            return;
        }
        
        TestCase tc = caseMap.get(selected);
        if (tc == null) {
            detailArea.setText("用例信息加载失败");
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== 用例详情 ===\n\n");
        sb.append("名称: ").append(tc.getName() != null ? tc.getName() : "无").append("\n\n");
        sb.append("描述: ").append(tc.getDescription() != null ? tc.getDescription() : "无").append("\n\n");
        sb.append("请求方式: ").append(tc.getHttpMethod() != null ? tc.getHttpMethod() : "GET").append("\n\n");
        sb.append("URL路径: ").append(tc.getEndpointUrl() != null ? tc.getEndpointUrl() : "无").append("\n\n");
        
        // 查询参数
        sb.append("查询参数:\n");
        if (tc.getQueryParams() != null && !tc.getQueryParams().isEmpty()) {
            for (Map.Entry<String, String> entry : tc.getQueryParams().entrySet()) {
                sb.append("  ").append(entry.getKey()).append(" = ").append(entry.getValue()).append("\n");
            }
        } else {
            sb.append("  无\n");
        }
        sb.append("\n");
        
        // 请求体
        sb.append("请求体:\n");
        sb.append(tc.getBody() != null ? tc.getBody() : "无").append("\n\n");
        
        // 期望状态码
        sb.append("期望状态码: ").append(tc.getExpectedStatus()).append("\n\n");
        
        // 优先级
        sb.append("优先级: ").append(tc.getPriority() != null ? tc.getPriority() : "P1").append("\n\n");
        
        // 标签
        sb.append("标签: ");
        if (tc.getTags() != null && !tc.getTags().isEmpty()) {
            sb.append(String.join(", ", tc.getTags()));
        } else {
            sb.append("无");
        }
        sb.append("\n");
        
        // 性能测试标记
        if (tc.getTags() != null && tc.getTags().contains("performance")) {
            sb.append("\n[已标记为性能测试用例]");
        }
        
        detailArea.setText(sb.toString());
    }
    
    /**
     * 显示添加/编辑用例对话框
     */
    private void showAddEditDialog(TestCase editCase) {
        boolean isEdit = editCase != null;
        String dialogTitle = isEdit ? "编辑用例" : "添加用例";
        
        JDialog dialog = new JDialog(this, dialogTitle, true);
        dialog.setSize(500, 450);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));
        
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        formPanel.setBackground(Color.WHITE);
        
        // 用例名称
        JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        nameRow.setBackground(Color.WHITE);
        nameRow.add(new JLabel("用例名称(*):"));
        JTextField txtCaseName = new JTextField(30);
        txtCaseName.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        nameRow.add(txtCaseName);
        
        // 用例描述
        JPanel descRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        descRow.setBackground(Color.WHITE);
        descRow.add(new JLabel("用例描述:"));
        JTextField txtCaseDesc = new JTextField(30);
        txtCaseDesc.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        descRow.add(txtCaseDesc);
        
        // 请求参数
        JPanel paramsRow = new JPanel(new BorderLayout(5, 5));
        paramsRow.setBackground(Color.WHITE);
        paramsRow.add(new JLabel("请求参数(key=value, 每行一个):"), BorderLayout.NORTH);
        JTextArea txtCaseParams = new JTextArea(6, 30);
        txtCaseParams.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        txtCaseParams.setLineWrap(true);
        txtCaseParams.setWrapStyleWord(true);
        JScrollPane paramsScroll = new JScrollPane(txtCaseParams);
        paramsRow.add(paramsScroll, BorderLayout.CENTER);
        
        // 期望状态码
        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusRow.setBackground(Color.WHITE);
        statusRow.add(new JLabel("期望状态码:"));
        JSpinner spinnerStatus = new JSpinner(new SpinnerNumberModel(200, 100, 599, 1));
        spinnerStatus.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        spinnerStatus.setPreferredSize(new Dimension(80, 28));
        statusRow.add(spinnerStatus);
        
        // 性能测试标记
        JPanel perfRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        perfRow.setBackground(Color.WHITE);
        JCheckBox chkForPerf = new JCheckBox("设为性能测试");
        chkForPerf.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        chkForPerf.setForeground(INFO_COLOR);
        perfRow.add(chkForPerf);
        
        formPanel.add(nameRow);
        formPanel.add(Box.createVerticalStrut(5));
        formPanel.add(descRow);
        formPanel.add(Box.createVerticalStrut(5));
        formPanel.add(paramsRow);
        formPanel.add(Box.createVerticalStrut(5));
        formPanel.add(statusRow);
        formPanel.add(Box.createVerticalStrut(5));
        formPanel.add(perfRow);
        
        // 如果是编辑模式，加载现有数据
        if (isEdit) {
            txtCaseName.setText(editCase.getName());
            txtCaseDesc.setText(editCase.getDescription() != null ? editCase.getDescription() : "");
            
            // 填充请求参数
            if (editCase.getQueryParams() != null) {
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, String> entry : editCase.getQueryParams().entrySet()) {
                    sb.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
                }
                txtCaseParams.setText(sb.toString().trim());
            }
            
            spinnerStatus.setValue(editCase.getExpectedStatus());
            chkForPerf.setSelected(editCase.getTags() != null && editCase.getTags().contains("performance"));
        }
        
        // 按钮面板
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        btnPanel.setBackground(Color.WHITE);
        JButton btnSave = createStyledButton("保存", SUCCESS_COLOR);
        btnSave.setPreferredSize(new Dimension(80, 30));
        JButton btnCancel = createStyledButton("取消", new Color(149, 165, 166));
        btnCancel.setPreferredSize(new Dimension(80, 30));
        btnPanel.add(btnSave);
        btnPanel.add(btnCancel);
        
        btnSave.addActionListener(e -> {
            String caseName = txtCaseName.getText().trim();
            if (caseName.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "用例名称不能为空", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            // 解析请求参数
            Map<String, String> queryParams = new LinkedHashMap<>();
            String paramsText = txtCaseParams.getText().trim();
            if (!paramsText.isEmpty()) {
                String[] lines = paramsText.split("\n");
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    int eqIdx = line.indexOf('=');
                    if (eqIdx > 0) {
                        String key = line.substring(0, eqIdx).trim();
                        String value = line.substring(eqIdx + 1).trim();
                        queryParams.put(key, value);
                    }
                }
            }
            
            if (isEdit) {
                // 编辑模式：更新现有用例
                editCase.setName(caseName);
                editCase.setDescription(txtCaseDesc.getText().trim());
                editCase.setQueryParams(queryParams);
                editCase.setExpectedStatus((Integer) spinnerStatus.getValue());
                
                List<String> tags = editCase.getTags();
                if (tags == null) {
                    tags = new ArrayList<>();
                }
                tags.remove("performance");
                if (chkForPerf.isSelected()) {
                    tags.add("performance");
                }
                editCase.setTags(tags);
            } else {
                // 添加模式：创建新用例
                TestCase testCase = new TestCase();
                testCase.setName(caseName);
                testCase.setDescription(txtCaseDesc.getText().trim());
                testCase.setQueryParams(queryParams);
                testCase.setExpectedStatus((Integer) spinnerStatus.getValue());
                testCase.setPriority("P1");
                testCase.setHttpMethod("GET");
                
                List<String> tags = new ArrayList<>();
                if (chkForPerf.isSelected()) {
                    tags.add("performance");
                }
                testCase.setTags(tags);
                
                testCases.add(testCase);
            }
            
            loadCases();
            dialog.dispose();
        });
        
        btnCancel.addActionListener(ev -> dialog.dispose());
        
        dialog.add(formPanel, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
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
}
