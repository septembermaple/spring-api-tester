package com.autotest.git;

/**
 * 变更接口数据模型 - 表示一个因Git变更而受影响的API接口
 */
public class ChangedEndpoint {

    /** 文件路径 */
    private String filePath;

    /** 变更类型: ADD, MODIFY, DELETE, RENAME */
    private String changeType;

    /** 接口HTTP方法: GET, POST, PUT, DELETE等 */
    private String httpMethod;

    /** 接口URL路径 */
    private String urlPath;

    /** Controller类名 */
    private String controllerClass;

    /** 方法名 */
    private String methodName;

    /** 变更的代码片段 */
    private String changedCode;

    /** 变更描述 */
    private String changeDescription;

    /** 关联的commit hash */
    private String commitHash;

    /** 是否为新增接口 */
    private boolean isNewEndpoint;

    /** 是否为删除接口 */
    private boolean isDeletedEndpoint;

    /** 是否为间接测试 */
    private boolean isIndirectTest;

    /** 间接测试原因 */
    private String indirectTestReason;

    // ===== Getters and Setters =====

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }

    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }

    public String getUrlPath() { return urlPath; }
    public void setUrlPath(String urlPath) { this.urlPath = urlPath; }

    public String getControllerClass() { return controllerClass; }
    public void setControllerClass(String controllerClass) { this.controllerClass = controllerClass; }

    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }

    public String getChangedCode() { return changedCode; }
    public void setChangedCode(String changedCode) { this.changedCode = changedCode; }

    public String getChangeDescription() { return changeDescription; }
    public void setChangeDescription(String changeDescription) { this.changeDescription = changeDescription; }

    public String getCommitHash() { return commitHash; }
    public void setCommitHash(String commitHash) { this.commitHash = commitHash; }

    public boolean isNewEndpoint() { return isNewEndpoint; }
    public void setNewEndpoint(boolean newEndpoint) { isNewEndpoint = newEndpoint; }

    public boolean isDeletedEndpoint() { return isDeletedEndpoint; }
    public void setDeletedEndpoint(boolean deletedEndpoint) { isDeletedEndpoint = deletedEndpoint; }

    public boolean isIndirectTest() { return isIndirectTest; }
    public void setIndirectTest(boolean indirectTest) { isIndirectTest = indirectTest; }

    public String getIndirectTestReason() { return indirectTestReason; }
    public void setIndirectTestReason(String indirectTestReason) { this.indirectTestReason = indirectTestReason; }

    // ===== 别名方法（兼容性） =====

    /**
     * 获取接口路径（getUrlPath的别名）
     */
    public String getPath() {
        return this.urlPath;
    }

    /**
     * 获取接口HTTP方法（getHttpMethod的别名）
     */
    public String getMethod() {
        return this.httpMethod;
    }

    @Override
    public String toString() {
        return "ChangedEndpoint{" +
                "filePath='" + filePath + '\'' +
                ", changeType='" + changeType + '\'' +
                ", httpMethod='" + httpMethod + '\'' +
                ", urlPath='" + urlPath + '\'' +
                ", controllerClass='" + controllerClass + '\'' +
                ", methodName='" + methodName + '\'' +
                ", isNewEndpoint=" + isNewEndpoint +
                ", isDeletedEndpoint=" + isDeletedEndpoint +
                '}';
    }
}
