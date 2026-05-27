package com.autotest.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 支持搜索过滤的下拉输入框
 * 使用非可编辑的combobox实现，避免自动选中
 */
public class SearchableComboBox extends JPanel {

    private JComboBox<String> comboBox;
    private DefaultComboBoxModel<String> model;
    private List<String> allItems = new ArrayList<>();
    private boolean isFiltering = false;

    public SearchableComboBox() {
        setLayout(new BorderLayout(0, 0));
        initComponents();
    }

    private void initComponents() {
        model = new DefaultComboBoxModel<>();
        comboBox = new JComboBox<>(model);
        comboBox.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        comboBox.setMaximumRowCount(10);
        // 非可编辑模式，防止自动选中
        comboBox.setEditable(false);

        // 监听combobox选择（点击下拉列表时）
        comboBox.addActionListener(e -> {
            // 只在用户点击选择时触发
        });

        add(comboBox, BorderLayout.CENTER);
    }

    /**
     * 设置可选项目列表
     */
    public void setItems(List<String> items) {
        allItems.clear();
        allItems.addAll(items);

        model.removeAllElements();
        for (String item : items) {
            model.addElement(item);
        }
    }

    /**
     * 获取选中的值
     */
    public String getSelectedItem() {
        return (String) comboBox.getSelectedItem();
    }

    /**
     * 设置选中的值
     */
    public void setSelectedItem(String item) {
        comboBox.setSelectedItem(item);
    }

    /**
     * 获取文本框组件（用于外部输入监听）
     */
    public JComboBox<String> getComboBox() {
        return comboBox;
    }
    
    /**
     * 获取模型
     */
    public DefaultComboBoxModel<String> getModel() {
        return model;
    }

    /**
     * 获取所有项目
     */
    public List<String> getAllItems() {
        return new ArrayList<>(allItems);
    }
}
