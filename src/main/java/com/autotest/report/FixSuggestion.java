package com.autotest.report;

import java.util.List;

/**
 * 修复建议数据模型 - 描述一个测试失败的修复建议
 */
public class FixSuggestion {

    /** 关联的测试用例名称 */
    private String testName;

    /** 根因分析 */
    private String rootCause;

    /** 严重程度: HIGH, MEDIUM, LOW */
    private String severity;

    /** 修复建议 */
    private String fixSuggestion;

    /** 可能需要修改的文件列表 */
    private List<String> affectedFiles;

    /** 代码示例 */
    private String codeExample;

    /** 相关文档链接 */
    private String referenceLink;

    /** 置信度: HIGH, MEDIUM, LOW */
    private String confidence;

    // ===== Getters and Setters =====

    public String getTestName() { return testName; }
    public void setTestName(String testName) { this.testName = testName; }

    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getFixSuggestion() { return fixSuggestion; }
    public void setFixSuggestion(String fixSuggestion) { this.fixSuggestion = fixSuggestion; }

    public List<String> getAffectedFiles() { return affectedFiles; }
    public void setAffectedFiles(List<String> affectedFiles) { this.affectedFiles = affectedFiles; }

    public String getCodeExample() { return codeExample; }
    public void setCodeExample(String codeExample) { this.codeExample = codeExample; }

    public String getReferenceLink() { return referenceLink; }
    public void setReferenceLink(String referenceLink) { this.referenceLink = referenceLink; }

    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }

    // ===== 别名方法（兼容性） =====

    /**
     * 获取关联的测试用例名称（getTestName的别名）
     */
    public String getTestCaseName() {
        return this.testName;
    }

    /**
     * 获取修复建议（getFixSuggestion的别名）
     */
    public String getSuggestion() {
        return this.fixSuggestion;
    }

    /**
     * 获取错误类型（getRootCause的别名）
     */
    public String getErrorType() {
        return this.rootCause;
    }

    /**
     * 获取严重程度（getSeverity的别名）
     */
    public String getSeverityLevel() {
        return this.severity;
    }

    /**
     * 获取严重程度的CSS样式类
     */
    public String getSeverityClass() {
        if (severity == null) return "severity-medium";
        switch (severity.toUpperCase()) {
            case "HIGH": return "severity-high";
            case "LOW": return "severity-low";
            default: return "severity-medium";
        }
    }

    @Override
    public String toString() {
        return "FixSuggestion{" +
                "testName='" + testName + '\'' +
                ", rootCause='" + rootCause + '\'' +
                ", severity='" + severity + '\'' +
                ", fixSuggestion='" + fixSuggestion + '\'' +
                ", confidence='" + confidence + '\'' +
                '}';
    }
}
