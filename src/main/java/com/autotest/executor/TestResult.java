package com.autotest.executor;

/**
 * 测试结果数据模型 - 描述一个测试用例的执行结果
 */
public class TestResult {

    /** 测试用例名称 */
    private String testCaseName;

    /** HTTP方法 */
    private String httpMethod;

    /** 请求URL */
    private String url;

    /** 请求体 */
    private String requestBody;

    /** 期望的HTTP状态码 */
    private int expectedStatus;

    /** 实际的HTTP状态码 */
    private int actualStatus;

    /** 响应体 */
    private String responseBody;

    /** 响应头 */
    private String responseHeaders;

    /** 响应时间（毫秒） */
    private long durationMs;

    /** 是否通过 */
    private boolean passed;

    /** 错误消息 */
    private String errorMessage;

    /** 响应体大小（字节） */
    private long responseSize;

    /** 执行时间戳 */
    private long timestamp;

    /** 测试分类 */
    private String category;

    // ===== Getters and Setters =====

    public String getTestCaseName() { return testCaseName; }
    public void setTestCaseName(String testCaseName) { this.testCaseName = testCaseName; }

    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getRequestBody() { return requestBody; }
    public void setRequestBody(String requestBody) { this.requestBody = requestBody; }

    public int getExpectedStatus() { return expectedStatus; }
    public void setExpectedStatus(int expectedStatus) { this.expectedStatus = expectedStatus; }

    public int getActualStatus() { return actualStatus; }
    public void setActualStatus(int actualStatus) { this.actualStatus = actualStatus; }

    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }

    public String getResponseHeaders() { return responseHeaders; }
    public void setResponseHeaders(String responseHeaders) { this.responseHeaders = responseHeaders; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public long getResponseSize() { return responseSize; }
    public void setResponseSize(long responseSize) { this.responseSize = responseSize; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    // ===== 别名方法（兼容性） =====

    /**
     * 获取测试是否成功（isPassed的别名）
     */
    public boolean isSuccess() {
        return this.passed;
    }

    /**
     * 获取HTTP状态码（getActualStatus的别名）
     */
    public int getStatusCode() {
        return this.actualStatus;
    }

    /**
     * 获取响应时间（getDurationMs的别名）
     */
    public long getResponseTime() {
        return this.durationMs;
    }

    /**
     * 获取期望的HTTP状态码（getExpectedStatus的别名）
     */
    public int getExpectedStatusCode() {
        return this.expectedStatus;
    }

    /**
     * 获取状态码是否匹配
     */
    public boolean isStatusMatch() {
        return expectedStatus == actualStatus;
    }

    /**
     * 获取格式化的持续时间
     */
    public String getFormattedDuration() {
        if (durationMs < 1000) {
            return durationMs + "ms";
        } else if (durationMs < 60000) {
            return String.format("%.1fs", durationMs / 1000.0);
        } else {
            return String.format("%.1fm", durationMs / 60000.0);
        }
    }

    /**
     * 获取状态标签
     */
    public String getStatusLabel() {
        if (passed) {
            return "PASSED";
        }
        return "FAILED";
    }

    @Override
    public String toString() {
        return "TestResult{" +
                "testCaseName='" + testCaseName + '\'' +
                ", httpMethod='" + httpMethod + '\'' +
                ", url='" + url + '\'' +
                ", expectedStatus=" + expectedStatus +
                ", actualStatus=" + actualStatus +
                ", passed=" + passed +
                ", durationMs=" + durationMs +
                '}';
    }
}
