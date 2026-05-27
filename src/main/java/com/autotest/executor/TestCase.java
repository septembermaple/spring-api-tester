package com.autotest.executor;

import java.util.List;
import java.util.Map;

/**
 * 测试用例数据模型 - 描述一个API测试用例
 */
public class TestCase {

    /** 测试用例名称 */
    private String name;

    /** 测试描述 */
    private String description;

    /** HTTP方法 */
    private String httpMethod;

    /** 接口URL路径 */
    private String endpointUrl;

    /** 请求头 */
    private Map<String, String> headers;

    /** 请求体（JSON字符串） */
    private String body;

    /** 查询参数 */
    private Map<String, String> queryParams;

    /** 路径参数 */
    private Map<String, String> pathParams;

    /** 期望的HTTP状态码 */
    private int expectedStatus;

    /** 期望响应体包含的字符串列表 */
    private List<String> expectedBodyContains;

    /** 是否验证JSON Schema */
    private boolean validateJsonSchema;

    /** 期望的JSON Schema */
    private String expectedJsonSchema;

    /** 测试优先级: P0, P1, P2, P3 */
    private String priority;

    /** 测试标签 */
    private List<String> tags;

    /** 超时时间（毫秒） */
    private long timeoutMs;

    /** 是否跳过 */
    private boolean skipped;

    /** 跳过原因 */
    private String skipReason;

    // ===== Getters and Setters =====

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }

    public String getEndpointUrl() { return endpointUrl; }
    public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }

    public Map<String, String> getHeaders() { return headers; }
    public void setHeaders(Map<String, String> headers) { this.headers = headers; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public Map<String, String> getQueryParams() { return queryParams; }
    public void setQueryParams(Map<String, String> queryParams) { this.queryParams = queryParams; }

    public Map<String, String> getPathParams() { return pathParams; }
    public void setPathParams(Map<String, String> pathParams) { this.pathParams = pathParams; }

    public int getExpectedStatus() { return expectedStatus; }
    public void setExpectedStatus(int expectedStatus) { this.expectedStatus = expectedStatus; }

    public List<String> getExpectedBodyContains() { return expectedBodyContains; }
    public void setExpectedBodyContains(List<String> expectedBodyContains) { this.expectedBodyContains = expectedBodyContains; }

    public boolean isValidateJsonSchema() { return validateJsonSchema; }
    public void setValidateJsonSchema(boolean validateJsonSchema) { this.validateJsonSchema = validateJsonSchema; }

    public String getExpectedJsonSchema() { return expectedJsonSchema; }
    public void setExpectedJsonSchema(String expectedJsonSchema) { this.expectedJsonSchema = expectedJsonSchema; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }

    public boolean isSkipped() { return skipped; }
    public void setSkipped(boolean skipped) { this.skipped = skipped; }

    public String getSkipReason() { return skipReason; }
    public void setSkipReason(String skipReason) { this.skipReason = skipReason; }

    // ===== 别名方法（兼容性） =====

    /**
     * 获取请求URL（getEndpointUrl的别名）
     */
    public String getUrl() {
        return this.endpointUrl;
    }

    /**
     * 获取期望的HTTP状态码（getExpectedStatus的别名）
     */
    public int getExpectedStatusCode() {
        return this.expectedStatus;
    }

    @Override
    public String toString() {
        return "TestCase{" +
                "name='" + name + '\'' +
                ", httpMethod='" + httpMethod + '\'' +
                ", endpointUrl='" + endpointUrl + '\'' +
                ", expectedStatus=" + expectedStatus +
                ", priority='" + priority + '\'' +
                '}';
    }
}
