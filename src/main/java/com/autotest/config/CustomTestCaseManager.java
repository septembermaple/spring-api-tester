package com.autotest.config;

import com.autotest.executor.TestCase;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 自定义用例管理类 - 按接口路径分组管理用户自定义的测试用例
 * <p>
 * 保存格式为按接口分组的JSON：{"/api/path": [TestCase, ...], ...}
 * 支持按接口加载、保存、删除自定义用例，以及获取用于性能测试的用例。
 */
public class CustomTestCaseManager {

    private static final Logger logger = Logger.getLogger(CustomTestCaseManager.class.getName());

    private final ObjectMapper objectMapper;
    private final Config config;

    public CustomTestCaseManager(Config config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * 加载完整的按接口分组的用例数据
     *
     * @return 按接口路径分组的用例Map，如果文件不存在或加载失败则返回空Map
     */
    private Map<String, List<EndpointTestCase>> loadAllData() {
        String filePath = config.getCustomTestCaseFile();
        File file = new File(filePath);

        if (!file.exists()) {
            return new LinkedHashMap<>();
        }

        try {
            Map<String, List<EndpointTestCase>> data = objectMapper.readValue(
                    file, new TypeReference<Map<String, List<EndpointTestCase>>>() {});
            return data != null ? data : new LinkedHashMap<>();
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to load custom test cases: " + e.getMessage(), e);
            return new LinkedHashMap<>();
        }
    }

    /**
     * 保存完整的按接口分组的用例数据到文件
     *
     * @param data 按接口路径分组的用例Map
     * @return 是否保存成功
     */
    private boolean saveAllData(Map<String, List<EndpointTestCase>> data) {
        try {
            String filePath = config.getCustomTestCaseFile();
            File file = new File(filePath);

            // 确保父目录存在
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            objectMapper.writeValue(file, data);
            return true;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save custom test cases: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 加载指定接口的自定义用例
     *
     * @param endpointPath 接口路径
     * @return 自定义测试用例列表，如果文件不存在或该接口无用例则返回空列表
     */
    public List<TestCase> loadCustomTestCases(String endpointPath) {
        Map<String, List<EndpointTestCase>> data = loadAllData();
        List<EndpointTestCase> endpointCases = data.get(endpointPath);

        if (endpointCases == null || endpointCases.isEmpty()) {
            return new ArrayList<>();
        }

        return endpointCases.stream()
                .map(ec -> ec.toTestCase(endpointPath))
                .collect(Collectors.toList());
    }

    /**
     * 保存指定接口的自定义用例
     *
     * @param endpointPath 接口路径
     * @param testCases    要保存的测试用例列表
     * @return 是否保存成功
     */
    public boolean saveCustomTestCases(String endpointPath, List<TestCase> testCases) {
        if (endpointPath == null || endpointPath.isEmpty()) {
            logger.warning("Endpoint path is empty");
            return false;
        }

        Map<String, List<EndpointTestCase>> data = loadAllData();

        List<EndpointTestCase> endpointCases = testCases.stream()
                .map(EndpointTestCase::fromTestCase)
                .collect(Collectors.toList());

        data.put(endpointPath, endpointCases);

        boolean success = saveAllData(data);
        if (success) {
            logger.info("Saved " + testCases.size() + " custom test cases for endpoint: " + endpointPath);
        }
        return success;
    }

    /**
     * 删除指定接口的指定用例
     *
     * @param endpointPath 接口路径
     * @param caseName     用例名称
     * @return 是否删除成功
     */
    public boolean removeCustomTestCase(String endpointPath, String caseName) {
        if (endpointPath == null || caseName == null) {
            return false;
        }

        Map<String, List<EndpointTestCase>> data = loadAllData();
        List<EndpointTestCase> endpointCases = data.get(endpointPath);

        if (endpointCases == null || endpointCases.isEmpty()) {
            logger.warning("No test cases found for endpoint: " + endpointPath);
            return false;
        }

        boolean removed = endpointCases.removeIf(ec -> caseName.equals(ec.name));

        if (removed) {
            if (endpointCases.isEmpty()) {
                data.remove(endpointPath);
            }
            boolean success = saveAllData(data);
            if (success) {
                logger.info("Removed test case '" + caseName + "' from endpoint: " + endpointPath);
            }
            return success;
        }

        logger.warning("Test case not found: " + caseName + " for endpoint: " + endpointPath);
        return false;
    }

    /**
     * 获取所有已配置自定义用例的接口路径
     *
     * @return 接口路径列表
     */
    public List<String> getAllEndpoints() {
        Map<String, List<EndpointTestCase>> data = loadAllData();
        return new ArrayList<>(data.keySet());
    }

    /**
     * 加载所有自定义用例（兼容旧接口）
     *
     * @return 所有接口的所有自定义测试用例列表
     */
    public List<TestCase> loadCustomTestCases() {
        Map<String, List<EndpointTestCase>> data = loadAllData();
        List<TestCase> allCases = new ArrayList<>();

        for (Map.Entry<String, List<EndpointTestCase>> entry : data.entrySet()) {
            String endpointPath = entry.getKey();
            for (EndpointTestCase ec : entry.getValue()) {
                allCases.add(ec.toTestCase(endpointPath));
            }
        }

        logger.info("Loaded " + allCases.size() + " custom test cases total");
        return allCases;
    }

    /**
     * 保存自定义用例（兼容旧接口，保存所有用例到一个接口路径下）
     *
     * @param testCases 要保存的测试用例列表
     * @return 是否保存成功
     */
    public boolean saveCustomTestCases(List<TestCase> testCases) {
        if (testCases == null || testCases.isEmpty()) {
            logger.warning("No test cases to save");
            return false;
        }

        // 按接口路径分组保存
        Map<String, List<TestCase>> grouped = new LinkedHashMap<>();
        for (TestCase tc : testCases) {
            String endpoint = tc.getEndpointUrl() != null ? tc.getEndpointUrl() : "unknown";
            grouped.computeIfAbsent(endpoint, k -> new ArrayList<>()).add(tc);
        }

        Map<String, List<EndpointTestCase>> data = loadAllData();

        for (Map.Entry<String, List<TestCase>> entry : grouped.entrySet()) {
            List<EndpointTestCase> endpointCases = entry.getValue().stream()
                    .map(EndpointTestCase::fromTestCase)
                    .collect(Collectors.toList());
            data.put(entry.getKey(), endpointCases);
        }

        boolean success = saveAllData(data);
        if (success) {
            logger.info("Saved " + testCases.size() + " custom test cases to: " + config.getCustomTestCaseFile());
        }
        return success;
    }

    /**
     * 追加AI生成的用例到自定义用例文件
     *
     * @param aiGeneratedCases AI生成的测试用例列表
     * @return 是否追加成功
     */
    public boolean appendAiGeneratedCases(List<TestCase> aiGeneratedCases) {
        if (aiGeneratedCases == null || aiGeneratedCases.isEmpty()) {
            logger.warning("No AI generated cases to append");
            return false;
        }

        try {
            // 标记AI生成的用例
            for (TestCase tc : aiGeneratedCases) {
                tc.setName("[AI] " + tc.getName());
                List<String> tags = tc.getTags();
                if (tags == null) {
                    tags = new ArrayList<>();
                }
                if (!tags.contains("ai-generated")) {
                    tags.add("ai-generated");
                }
                tc.setTags(tags);
            }

            // 按接口路径分组
            Map<String, List<TestCase>> grouped = new LinkedHashMap<>();
            for (TestCase tc : aiGeneratedCases) {
                String endpoint = tc.getEndpointUrl() != null ? tc.getEndpointUrl() : "unknown";
                grouped.computeIfAbsent(endpoint, k -> new ArrayList<>()).add(tc);
            }

            Map<String, List<EndpointTestCase>> data = loadAllData();

            for (Map.Entry<String, List<TestCase>> entry : grouped.entrySet()) {
                List<EndpointTestCase> existing = data.getOrDefault(entry.getKey(), new ArrayList<>());

                // 去重：基于name
                List<String> existingNames = existing.stream()
                        .map(ec -> ec.name)
                        .collect(Collectors.toList());

                for (TestCase tc : entry.getValue()) {
                    if (!existingNames.contains(tc.getName())) {
                        existing.add(EndpointTestCase.fromTestCase(tc));
                    }
                }

                data.put(entry.getKey(), existing);
            }

            return saveAllData(data);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to append AI generated cases: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取用于性能测试的用例
     *
     * @return 标记为性能测试的用例列表
     */
    public List<TestCase> getPerformanceTestCases() {
        List<TestCase> allCases = loadCustomTestCases();

        return allCases.stream()
                .filter(tc -> tc.getTags() != null && tc.getTags().contains("performance"))
                .collect(Collectors.toList());
    }

    /**
     * 标记用例为性能测试用例
     *
     * @param testCaseName 用例名称
     * @param isPerformance 是否标记为性能测试
     * @return 是否操作成功
     */
    public boolean markAsPerformanceTest(String testCaseName, boolean isPerformance) {
        Map<String, List<EndpointTestCase>> data = loadAllData();

        boolean found = false;
        for (Map.Entry<String, List<EndpointTestCase>> entry : data.entrySet()) {
            for (EndpointTestCase ec : entry.getValue()) {
                if (testCaseName.equals(ec.name)) {
                    ec.forPerformance = isPerformance;
                    found = true;
                    break;
                }
            }
            if (found) break;
        }

        if (found) {
            return saveAllData(data);
        }

        logger.warning("Test case not found: " + testCaseName);
        return false;
    }

    /**
     * 清空所有自定义用例
     *
     * @return 是否清空成功
     */
    public boolean clearCustomTestCases() {
        String filePath = config.getCustomTestCaseFile();
        File file = new File(filePath);

        if (file.exists()) {
            return file.delete();
        }

        return true;
    }

    /**
     * 检查自定义用例文件是否存在
     *
     * @return 文件是否存在
     */
    public boolean customTestCaseFileExists() {
        return new File(config.getCustomTestCaseFile()).exists();
    }

    /**
     * 获取自定义用例文件路径
     *
     * @return 文件路径
     */
    public String getCustomTestCaseFilePath() {
        return config.getCustomTestCaseFile();
    }

    /**
     * 按接口分组的测试用例数据结构 - 用于JSON序列化
     * <p>
     * 每个用例包含：name, description, queryParams, expectedStatus, forPerformance
     */
    public static class EndpointTestCase {
        private String name;
        private String description;
        private java.util.Map<String, String> queryParams;
        private int expectedStatus = 200;
        private boolean forPerformance = false;

        public EndpointTestCase() {
        }

        public static EndpointTestCase fromTestCase(TestCase tc) {
            EndpointTestCase ec = new EndpointTestCase();
            ec.name = tc.getName();
            ec.description = tc.getDescription();
            ec.queryParams = tc.getQueryParams();
            ec.expectedStatus = tc.getExpectedStatus();
            // 检查tags中是否包含performance
            ec.forPerformance = tc.getTags() != null && tc.getTags().contains("performance");
            return ec;
        }

        public TestCase toTestCase(String endpointPath) {
            TestCase tc = new TestCase();
            tc.setName(name);
            tc.setDescription(description);
            tc.setEndpointUrl(endpointPath);
            tc.setQueryParams(queryParams);
            tc.setExpectedStatus(expectedStatus);
            tc.setPriority("P1");
            List<String> tags = new ArrayList<>();
            if (forPerformance) {
                tags.add("performance");
            }
            tags.add("custom");
            tc.setTags(tags);
            return tc;
        }

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public java.util.Map<String, String> getQueryParams() { return queryParams; }
        public void setQueryParams(java.util.Map<String, String> queryParams) { this.queryParams = queryParams; }

        public int getExpectedStatus() { return expectedStatus; }
        public void setExpectedStatus(int expectedStatus) { this.expectedStatus = expectedStatus; }

        public boolean isForPerformance() { return forPerformance; }
        public void setForPerformance(boolean forPerformance) { this.forPerformance = forPerformance; }
    }

    /**
     * 自定义测试用例包装类 - 用于JSON序列化（兼容旧格式）
     */
    public static class CustomTestCaseWrapper {
        private String name;
        private String description;
        private String httpMethod;
        private String endpointUrl;
        private java.util.Map<String, String> headers;
        private String body;
        private java.util.Map<String, String> queryParams;
        private java.util.Map<String, String> pathParams;
        private int expectedStatus;
        private java.util.List<String> expectedBodyContains;
        private boolean validateJsonSchema;
        private String priority;
        private java.util.List<String> tags;
        private boolean skipped;
        private String skipReason;

        public static CustomTestCaseWrapper fromTestCase(TestCase tc) {
            CustomTestCaseWrapper wrapper = new CustomTestCaseWrapper();
            wrapper.name = tc.getName();
            wrapper.description = tc.getDescription();
            wrapper.httpMethod = tc.getHttpMethod();
            wrapper.endpointUrl = tc.getEndpointUrl();
            wrapper.headers = tc.getHeaders();
            wrapper.body = tc.getBody();
            wrapper.queryParams = tc.getQueryParams();
            wrapper.pathParams = tc.getPathParams();
            wrapper.expectedStatus = tc.getExpectedStatus();
            wrapper.expectedBodyContains = tc.getExpectedBodyContains();
            wrapper.validateJsonSchema = tc.isValidateJsonSchema();
            wrapper.priority = tc.getPriority();
            wrapper.tags = tc.getTags();
            wrapper.skipped = tc.isSkipped();
            wrapper.skipReason = tc.getSkipReason();
            return wrapper;
        }

        public TestCase toTestCase() {
            TestCase tc = new TestCase();
            tc.setName(name);
            tc.setDescription(description);
            tc.setHttpMethod(httpMethod);
            tc.setEndpointUrl(endpointUrl);
            tc.setHeaders(headers);
            tc.setBody(body);
            tc.setQueryParams(queryParams);
            tc.setPathParams(pathParams);
            tc.setExpectedStatus(expectedStatus);
            tc.setExpectedBodyContains(expectedBodyContains);
            tc.setValidateJsonSchema(validateJsonSchema);
            tc.setPriority(priority != null ? priority : "P1");
            tc.setTags(tags);
            tc.setSkipped(skipped);
            tc.setSkipReason(skipReason);
            return tc;
        }

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getHttpMethod() { return httpMethod; }
        public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }

        public String getEndpointUrl() { return endpointUrl; }
        public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }

        public java.util.Map<String, String> getHeaders() { return headers; }
        public void setHeaders(java.util.Map<String, String> headers) { this.headers = headers; }

        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }

        public java.util.Map<String, String> getQueryParams() { return queryParams; }
        public void setQueryParams(java.util.Map<String, String> queryParams) { this.queryParams = queryParams; }

        public java.util.Map<String, String> getPathParams() { return pathParams; }
        public void setPathParams(java.util.Map<String, String> pathParams) { this.pathParams = pathParams; }

        public int getExpectedStatus() { return expectedStatus; }
        public void setExpectedStatus(int expectedStatus) { this.expectedStatus = expectedStatus; }

        public java.util.List<String> getExpectedBodyContains() { return expectedBodyContains; }
        public void setExpectedBodyContains(java.util.List<String> expectedBodyContains) { this.expectedBodyContains = expectedBodyContains; }

        public boolean isValidateJsonSchema() { return validateJsonSchema; }
        public void setValidateJsonSchema(boolean validateJsonSchema) { this.validateJsonSchema = validateJsonSchema; }

        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }

        public java.util.List<String> getTags() { return tags; }
        public void setTags(java.util.List<String> tags) { this.tags = tags; }

        public boolean isSkipped() { return skipped; }
        public void setSkipped(boolean skipped) { this.skipped = skipped; }

        public String getSkipReason() { return skipReason; }
        public void setSkipReason(String skipReason) { this.skipReason = skipReason; }
    }
}
