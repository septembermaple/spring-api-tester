package com.autotest.ai;

import com.autotest.config.Config;
import com.autotest.executor.TestCase;
import com.autotest.executor.TestResult;
import com.autotest.report.FixSuggestion;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 失败分析与修复建议生成器 - 使用AI分析测试失败原因并生成修复建议
 * <p>
 * 支持对失败结果进行分类、分批AI分析、自动修复测试用例。
 * 当config.isAutoFix()为true时，除了生成修复建议外，还会尝试自动修复测试用例。
 */
public class FixAnalyzer {

    private static final Logger logger = Logger.getLogger(FixAnalyzer.class.getName());

    /** 每批发送给AI的最大失败数量（避免单次请求过大） */
    private static final int MAX_FAILURES_PER_BATCH = 10;

    /** 响应体最大截断长度 */
    private static final int MAX_RESPONSE_BODY_LENGTH = 1000;

    /** 请求体最大截断长度 */
    private static final int MAX_REQUEST_BODY_LENGTH = 500;

    private static final String SYSTEM_PROMPT =
            "你是一位专业的Spring Boot开发者和API测试专家。请分析失败的测试结果并提供修复建议。\n" +
            "\n" +
            "对于每个失败的测试，请提供一个JSON对象，JSON字段名必须使用英文，字段值中的分析内容使用中文：\n" +
            "- \"testName\": 失败测试的名称（必须完全匹配原始名称）\n" +
            "- \"rootCause\": 详细分析测试失败的原因（中文描述）\n" +
            "- \"severity\": 严重程度，可选值：HIGH、MEDIUM、LOW\n" +
            "- \"fixSuggestion\": 具体的代码或配置修改建议（中文描述）\n" +
            "- \"affectedFiles\": 可能需要修改的文件路径数组\n" +
            "- \"codeExample\": 可选的修复代码示例（或null）\n" +
            "- \"confidence\": 诊断的可信度 - HIGH、MEDIUM或LOW\n" +
            "- \"referenceLink\": 可选的相关文档链接（或null）\n" +
            "- \"autoFixAction\": 可选的自动修复操作类型，可选值：\n" +
            "    - \"UPDATE_EXPECTED_STATUS\": 将期望状态码更改为实际状态码\n" +
            "    - \"UPDATE_EXPECTED_BODY\": 更新期望的响应体包含列表\n" +
            "    - \"UPDATE_REQUEST_BODY\": 修复请求体\n" +
            "    - \"UPDATE_REQUEST_HEADERS\": 修复请求头\n" +
            "    - \"UPDATE_URL\": 修复请求URL\n" +
            "    - \"NONE\": 无法自动修复\n" +
            "- \"autoFixValue\": 自动修复操作要应用的新值（或null）\n" +
            "\n" +
            "常见失败类别及修复方法：\n" +
            "- 状态码不匹配：检查期望的状态码是否正确\n" +
            "- 响应体不匹配：检查API响应格式是否已更改\n" +
            "- 连接被拒绝：服务可能未运行或URL错误\n" +
            "- 超时：接口可能太慢或超时时间太短\n" +
            "- 认证失败：检查Token/凭据\n" +
            "\n" +
            "重要：请仅返回有效的JSON数组，不要使用markdown代码块格式。所有分析描述内容使用中文。";

    private final AiClient aiClient;
    private final Config config;

    public FixAnalyzer(Config config) {
        this.config = config;
        this.aiClient = new AiClient(config);
    }

    /**
     * 分析失败的测试结果并生成修复建议
     * <p>
     * 完整的分析流程：
     * 1. 过滤出失败的测试结果
     * 2. 对失败结果进行分类
     * 3. 分批发送给AI分析（避免单次请求过大）
     * 4. 如果config.isAutoFix()为true，尝试自动修复测试用例
     *
     * @param results    所有测试结果
     * @param testCases  对应的测试用例（用于自动修复）
     * @return 修复建议列表
     */
    public List<FixSuggestion> analyzeAndSuggest(List<TestResult> results, Map<String, TestCase> testCases) {
        List<FixSuggestion> suggestions = new ArrayList<>();

        // 过滤出失败的测试
        List<TestResult> failures = new ArrayList<>();
        for (TestResult r : results) {
            if (!r.isPassed()) {
                failures.add(r);
            }
        }

        if (failures.isEmpty()) {
            logger.info("All tests passed. No fix suggestions needed.");
            return suggestions;
        }

        logger.info("Analyzing " + failures.size() + " failed tests...");

        // 对失败结果进行分类
        Map<String, List<TestResult>> categorized = categorizeFailures(failures);
        for (Map.Entry<String, List<TestResult>> entry : categorized.entrySet()) {
            logger.info("  Category [" + entry.getKey() + "]: " + entry.getValue().size() + " failures");
        }

        // 分批发送给AI分析
        List<List<TestResult>> batches = splitIntoBatches(failures, MAX_FAILURES_PER_BATCH);
        logger.info("Split failures into " + batches.size() + " batch(es) for AI analysis");

        for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
            List<TestResult> batch = batches.get(batchIndex);
            logger.info("Processing batch " + (batchIndex + 1) + "/" + batches.size()
                    + " (" + batch.size() + " failures)");

            try {
                String userPrompt = buildFailurePrompt(batch);
                String aiResponse = aiClient.chatCompletion(SYSTEM_PROMPT, userPrompt);
                List<FixSuggestion> batchSuggestions = parseSuggestions(aiResponse);
                suggestions.addAll(batchSuggestions);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error analyzing batch " + (batchIndex + 1)
                        + " with AI: " + e.getMessage(), e);
                // AI分析失败时，为每个失败生成基本建议
                for (TestResult failure : batch) {
                    suggestions.add(createBasicSuggestion(failure));
                }
            }
        }

        logger.info("Generated " + suggestions.size() + " fix suggestions");

        // 如果启用了自动修复，尝试自动修复测试用例
        if (config.isAutoFix() && testCases != null && !testCases.isEmpty()) {
            int fixedCount = autoFixTestCases(suggestions, testCases);
            logger.info("Auto-fixed " + fixedCount + " test case(s)");
        }

        return suggestions;
    }

    /**
     * 分析失败的测试结果并生成修复建议（简化版，不包含自动修复）
     *
     * @param results 所有测试结果
     * @return 修复建议列表
     */
    public List<FixSuggestion> analyzeAndSuggest(List<TestResult> results) {
        return analyzeAndSuggest(results, null);
    }

    /**
     * 流式分析失败的测试结果，带进度回调
     *
     * @param results          所有测试结果
     * @param progressCallback 进度回调接口
     * @return 修复建议列表
     */
    public List<FixSuggestion> analyzeAndSuggestStreaming(List<TestResult> results, java.util.function.Consumer<String> progressCallback) {
        return analyzeAndSuggestStreaming(results, null, progressCallback);
    }

    /**
     * 流式分析失败的测试结果，带进度回调
     *
     * @param results          所有测试结果
     * @param testCases        对应的测试用例
     * @param progressCallback 进度回调接口
     * @return 修复建议列表
     */
    public List<FixSuggestion> analyzeAndSuggestStreaming(List<TestResult> results, Map<String, TestCase> testCases,
            java.util.function.Consumer<String> progressCallback) {
        List<FixSuggestion> suggestions = new ArrayList<>();

        // 过滤出失败的测试
        List<TestResult> failures = new ArrayList<>();
        for (TestResult r : results) {
            if (!r.isPassed()) {
                failures.add(r);
            }
        }

        if (failures.isEmpty()) {
            logger.info("所有测试通过，无需修复建议。");
            return suggestions;
        }

        logger.info("正在分析 " + failures.size() + " 个失败测试...");

        // 构建分析提示
        String prompt = buildAnalysisPrompt(failures);

        // 使用流式调用
        final StringBuilder fullResponse = new StringBuilder();
        try {
            aiClient.chatCompletionStream(SYSTEM_PROMPT, prompt, new AiClient.StreamCallback() {
                @Override
                public void onToken(String token) {
                    fullResponse.append(token);
                    if (progressCallback != null) {
                        progressCallback.accept(token);
                    }
                }

                @Override
                public void onComplete(String fullText) {
                    // 完成
                }

                @Override
                public void onError(Exception e) {
                    logger.severe("AI分析流式调用错误: " + e.getMessage());
                }
            });

            // 等待流完成
            Thread.sleep(500);

            String response = fullResponse.toString();
            if (response.isEmpty()) {
                // 降级为普通调用
                response = aiClient.chatCompletion(SYSTEM_PROMPT, prompt);
            }

            // 解析响应
            suggestions = parseSuggestions(response);

        } catch (Exception e) {
            logger.severe("分析失败测试时出错: " + e.getMessage());
            // 降级为普通调用
            return analyzeAndSuggest(results, testCases);
        }

        return suggestions;
    }

    /**
     * 对失败结果进行分类
     * <p>
     * 分类维度：
     * - STATUS_CODE_MISMATCH: 状态码不匹配
     * - RESPONSE_BODY_ERROR: 响应内容错误
     * - CONNECTION_FAILED: 连接失败
     * - TIMEOUT: 超时
     * - AUTHENTICATION_FAILED: 认证失败
     * - VALIDATION_ERROR: 参数校验失败
     * - SERVER_ERROR: 服务端内部错误
     * - UNKNOWN: 未知错误
     *
     * @param failures 失败的测试结果列表
     * @return 按分类分组的失败结果
     */
    public Map<String, List<TestResult>> categorizeFailures(List<TestResult> failures) {
        Map<String, List<TestResult>> categorized = new LinkedHashMap<>();

        for (TestResult failure : failures) {
            String category = categorizeFailure(failure);
            categorized.computeIfAbsent(category, k -> new ArrayList<TestResult>()).add(failure);
        }

        return categorized;
    }

    /**
     * 对单个失败结果进行分类
     *
     * @param failure 测试失败结果
     * @return 失败分类字符串
     */
    public String categorizeFailure(TestResult failure) {
        // 检查错误消息中的关键信息
        String errorMessage = failure.getErrorMessage();
        if (errorMessage == null) {
            errorMessage = "";
        }
        String lowerError = errorMessage.toLowerCase();

        // 连接失败
        if (lowerError.contains("connection refused") || lowerError.contains("connect exception")
                || lowerError.contains("no route to host") || lowerError.contains("unknown host")
                || lowerError.contains("socketexception")) {
            return "CONNECTION_FAILED";
        }

        // 超时
        if (lowerError.contains("timeout") || lowerError.contains("timed out")
                || lowerError.contains("sockettimeoutexception")) {
            return "TIMEOUT";
        }

        // 认证失败
        if (failure.getActualStatus() == 401 || failure.getActualStatus() == 403) {
            return "AUTHENTICATION_FAILED";
        }

        // 参数校验失败
        if (failure.getActualStatus() == 400 || lowerError.contains("validation")
                || lowerError.contains("bad request") || lowerError.contains("method argument")) {
            return "VALIDATION_ERROR";
        }

        // 服务端内部错误
        if (failure.getActualStatus() >= 500) {
            return "SERVER_ERROR";
        }

        // 状态码不匹配（最常见的情况）
        if (failure.getActualStatus() != failure.getExpectedStatus()) {
            return "STATUS_CODE_MISMATCH";
        }

        // 响应内容错误（状态码匹配但其他断言失败）
        if (failure.isPassed() == false && failure.getActualStatus() == failure.getExpectedStatus()) {
            return "RESPONSE_BODY_ERROR";
        }

        return "UNKNOWN";
    }

    /**
     * 根据AI建议自动修复测试用例
     * <p>
     * 支持的自动修复操作：
     * - UPDATE_EXPECTED_STATUS: 更新期望状态码
     * - UPDATE_EXPECTED_BODY: 更新期望响应体包含内容
     * - UPDATE_REQUEST_BODY: 修复请求体
     * - UPDATE_REQUEST_HEADERS: 修复请求头
     * - UPDATE_URL: 修复请求URL
     *
     * @param suggestions AI生成的修复建议列表
     * @param testCases   测试用例映射（key为测试用例名称）
     * @return 成功修复的测试用例数量
     */
    public int autoFixTestCases(List<FixSuggestion> suggestions, Map<String, TestCase> testCases) {
        int fixedCount = 0;

        for (FixSuggestion suggestion : suggestions) {
            if (suggestion.getTestName() == null) {
                continue;
            }

            TestCase testCase = testCases.get(suggestion.getTestName());
            if (testCase == null) {
                // 尝试模糊匹配
                testCase = findTestCaseByName(suggestion.getTestName(), testCases);
            }

            if (testCase == null) {
                logger.warning("Cannot auto-fix: test case not found for suggestion: "
                        + suggestion.getTestName());
                continue;
            }

            boolean fixed = autoFixTestCase(testCase, suggestion);
            if (fixed) {
                fixedCount++;
                logger.info("Auto-fixed test case: " + suggestion.getTestName()
                        + " (action: " + suggestion.getCodeExample() + ")");
            }
        }

        return fixedCount;
    }

    /**
     * 根据单个修复建议自动修复测试用例
     *
     * @param testCase   待修复的测试用例
     * @param suggestion 修复建议
     * @return 是否成功修复
     */
    public boolean autoFixTestCase(TestCase testCase, FixSuggestion suggestion) {
        if (testCase == null || suggestion == null) {
            return false;
        }

        // 从codeExample字段提取自动修复动作（兼容旧格式）
        String action = suggestion.getCodeExample();
        String value = suggestion.getReferenceLink();

        // 如果codeExample和referenceLink为空，尝试从fixSuggestion中提取
        if (action == null && value == null) {
            return false;
        }

        boolean fixed = false;

        if (action != null) {
            switch (action.toUpperCase()) {
                case "UPDATE_EXPECTED_STATUS":
                    if (value != null) {
                        try {
                            int newStatus = Integer.parseInt(value.trim());
                            testCase.setExpectedStatus(newStatus);
                            fixed = true;
                            logger.info("Updated expected status to " + newStatus
                                    + " for test: " + testCase.getName());
                        } catch (NumberFormatException e) {
                            logger.warning("Invalid status code in auto-fix: " + value);
                        }
                    }
                    break;

                case "UPDATE_EXPECTED_BODY":
                    if (value != null) {
                        String[] parts = value.split(",");
                        List<String> newContains = new ArrayList<>();
                        for (String part : parts) {
                            String trimmed = part.trim();
                            if (!trimmed.isEmpty()) {
                                newContains.add(trimmed);
                            }
                        }
                        if (!newContains.isEmpty()) {
                            testCase.setExpectedBodyContains(newContains);
                            fixed = true;
                            logger.info("Updated expected body contains for test: " + testCase.getName());
                        }
                    }
                    break;

                case "UPDATE_REQUEST_BODY":
                    if (value != null && !value.trim().isEmpty()) {
                        testCase.setBody(value.trim());
                        fixed = true;
                        logger.info("Updated request body for test: " + testCase.getName());
                    }
                    break;

                case "UPDATE_REQUEST_HEADERS":
                    // value格式: "Key1:Value1,Key2:Value2"
                    if (value != null && !value.trim().isEmpty()) {
                        Map<String, String> newHeaders = new LinkedHashMap<>();
                        String[] pairs = value.split(",");
                        for (String pair : pairs) {
                            String[] kv = pair.split(":", 2);
                            if (kv.length == 2) {
                                newHeaders.put(kv[0].trim(), kv[1].trim());
                            }
                        }
                        if (!newHeaders.isEmpty()) {
                            if (testCase.getHeaders() != null) {
                                newHeaders.putAll(testCase.getHeaders());
                            }
                            testCase.setHeaders(newHeaders);
                            fixed = true;
                            logger.info("Updated request headers for test: " + testCase.getName());
                        }
                    }
                    break;

                case "UPDATE_URL":
                    if (value != null && !value.trim().isEmpty()) {
                        testCase.setEndpointUrl(value.trim());
                        fixed = true;
                        logger.info("Updated URL for test: " + testCase.getName());
                    }
                    break;

                case "NONE":
                default:
                    logger.fine("No auto-fix action for test: " + testCase.getName());
                    break;
            }
        }

        return fixed;
    }

    // ===== 私有辅助方法 =====

    /**
     * 构建失败分析的详细提示词
     * <p>
     * 包含请求和响应的完整信息，以及失败分类信息。
     */
    private String buildFailurePrompt(List<TestResult> failures) {
        StringBuilder sb = new StringBuilder();
        sb.append("Analyze the following failed API tests and provide detailed fix suggestions.\n\n");

        for (int i = 0; i < failures.size(); i++) {
            TestResult result = failures.get(i);
            String category = categorizeFailure(result);

            sb.append("--- Failure #").append(i + 1).append(" ---\n");
            sb.append("Test Name: ").append(result.getTestCaseName()).append("\n");
            sb.append("Category: ").append(category).append("\n");
            sb.append("Request: ").append(result.getHttpMethod()).append(" ").append(result.getUrl()).append("\n");

            // 请求体
            if (result.getRequestBody() != null && !result.getRequestBody().isEmpty()) {
                sb.append("Request Body: ").append(truncate(result.getRequestBody(), MAX_REQUEST_BODY_LENGTH)).append("\n");
            }

            // 期望与实际状态码
            sb.append("Expected Status: ").append(result.getExpectedStatus()).append("\n");
            sb.append("Actual Status: ").append(result.getActualStatus()).append("\n");

            // 响应体
            if (result.getResponseBody() != null && !result.getResponseBody().isEmpty()) {
                sb.append("Response Body: ").append(truncate(result.getResponseBody(), MAX_RESPONSE_BODY_LENGTH)).append("\n");
            }

            // 响应头（截取前200字符）
            if (result.getResponseHeaders() != null && !result.getResponseHeaders().isEmpty()) {
                sb.append("Response Headers: ").append(truncate(result.getResponseHeaders(), 200)).append("\n");
            }

            // 错误消息
            if (result.getErrorMessage() != null && !result.getErrorMessage().isEmpty()) {
                sb.append("Error Message: ").append(result.getErrorMessage()).append("\n");
            }

            // 响应时间
            sb.append("Duration: ").append(result.getDurationMs()).append("ms");
            if (result.getDurationMs() > 5000) {
                sb.append(" (SLOW - consider timeout or performance issue)");
            }
            sb.append("\n\n");
        }

        sb.append("Please analyze each failure and provide:\n");
        sb.append("1. Root cause analysis\n");
        sb.append("2. Specific fix suggestion with code example if applicable\n");
        sb.append("3. Confidence level (HIGH/MEDIUM/LOW)\n");
        sb.append("4. Auto-fix action if possible (UPDATE_EXPECTED_STATUS, etc.)\n");
        sb.append("5. Auto-fix value (the new value to apply)\n");

        return sb.toString();
    }

    /**
     * 构建分析提示（调用buildFailurePrompt）
     */
    private String buildAnalysisPrompt(List<TestResult> failures) {
        return buildFailurePrompt(failures);
    }

    /**
     * 解析AI返回的修复建议JSON
     * <p>
     * 增强解析，支持 codeExample, confidence, referenceLink 字段，
     * 以及自动修复相关字段 autoFixAction 和 autoFixValue。
     */
    private List<FixSuggestion> parseSuggestions(String aiResponse) {
        List<FixSuggestion> suggestions = new ArrayList<>();

        try {
            String json = extractJsonArray(aiResponse);

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<java.util.Map<String, Object>> rawSuggestions = mapper.readValue(
                    json, new com.fasterxml.jackson.core.type.TypeReference<List<java.util.Map<String, Object>>>() {});

            for (java.util.Map<String, Object> raw : rawSuggestions) {
                try {
                    FixSuggestion suggestion = new FixSuggestion();
                    suggestion.setTestName(getStringField(raw, "testName"));
                    suggestion.setRootCause(getStringField(raw, "rootCause"));
                    suggestion.setSeverity(getStringField(raw, "severity", "MEDIUM"));
                    suggestion.setFixSuggestion(getStringField(raw, "fixSuggestion"));

                    // affectedFiles
                    Object affectedFiles = raw.get("affectedFiles");
                    if (affectedFiles instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> files = (List<String>) affectedFiles;
                        List<String> filteredFiles = new ArrayList<>();
                        for (String file : files) {
                            if (file != null && !file.isEmpty()) {
                                filteredFiles.add(file);
                            }
                        }
                        suggestion.setAffectedFiles(filteredFiles);
                    } else if (affectedFiles == null) {
                        suggestion.setAffectedFiles(new ArrayList<>());
                    }

                    // codeExample - 代码示例
                    suggestion.setCodeExample(getStringField(raw, "codeExample"));

                    // confidence - 置信度
                    String confidence = getStringField(raw, "confidence", "MEDIUM");
                    if ("HIGH".equalsIgnoreCase(confidence) || "MEDIUM".equalsIgnoreCase(confidence)
                            || "LOW".equalsIgnoreCase(confidence)) {
                        suggestion.setConfidence(confidence.toUpperCase());
                    } else {
                        suggestion.setConfidence("MEDIUM");
                    }

                    // referenceLink - 参考文档链接
                    suggestion.setReferenceLink(getStringField(raw, "referenceLink"));

                    // 自动修复相关字段
                    // 将autoFixAction存入codeExample，autoFixValue存入referenceLink
                    // （复用现有字段，避免修改FixSuggestion数据模型）
                    String autoFixAction = getStringField(raw, "autoFixAction");
                    String autoFixValue = getStringField(raw, "autoFixValue");

                    if (autoFixAction != null && !"NONE".equalsIgnoreCase(autoFixAction)) {
                        suggestion.setCodeExample(autoFixAction);
                        suggestion.setReferenceLink(autoFixValue);
                    }

                    suggestions.add(suggestion);
                } catch (Exception e) {
                    logger.warning("Failed to parse individual suggestion: " + raw + " - " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error parsing AI suggestions: " + e.getMessage(), e);
        }

        return suggestions;
    }

    /**
     * 创建基本的修复建议（AI不可用时的降级方案）
     * <p>
     * 根据失败分类生成更有针对性的基本建议。
     */
    private FixSuggestion createBasicSuggestion(TestResult failure) {
        FixSuggestion suggestion = new FixSuggestion();
        suggestion.setTestName(failure.getTestCaseName());
        suggestion.setConfidence("LOW");

        String category = categorizeFailure(failure);

        switch (category) {
            case "CONNECTION_FAILED":
                suggestion.setRootCause("Unable to connect to the API server. The service may not be running or the URL may be incorrect.");
                suggestion.setSeverity("HIGH");
                suggestion.setFixSuggestion("1. Verify the API server is running\n"
                        + "2. Check the base URL configuration\n"
                        + "3. Verify network connectivity\n"
                        + "4. Check firewall settings");
                suggestion.setCodeExample("UPDATE_URL");
                break;

            case "TIMEOUT":
                suggestion.setRootCause("The API request timed out after " + failure.getDurationMs() + "ms. "
                        + "The endpoint may be too slow or the timeout configuration is too short.");
                suggestion.setSeverity("HIGH");
                suggestion.setFixSuggestion("1. Increase the timeout configuration\n"
                        + "2. Check if the endpoint has performance issues\n"
                        + "3. Verify database queries are optimized");
                break;

            case "AUTHENTICATION_FAILED":
                suggestion.setRootCause("Authentication failed with HTTP " + failure.getActualStatus()
                        + ". The API requires valid authentication credentials.");
                suggestion.setSeverity("HIGH");
                suggestion.setFixSuggestion("1. Verify the authentication token is valid and not expired\n"
                        + "2. Check the Authorization header format\n"
                        + "3. Ensure the user has the required permissions");
                break;

            case "VALIDATION_ERROR":
                suggestion.setRootCause("Request validation failed with HTTP 400. "
                        + "The request parameters or body may be invalid.");
                suggestion.setSeverity("MEDIUM");
                suggestion.setFixSuggestion("1. Check request body format and field types\n"
                        + "2. Verify required fields are present\n"
                        + "3. Check parameter constraints (min/max length, format, etc.)");
                suggestion.setCodeExample("UPDATE_REQUEST_BODY");
                break;

            case "SERVER_ERROR":
                suggestion.setRootCause("Server returned HTTP " + failure.getActualStatus()
                        + ". This indicates an internal server error.");
                suggestion.setSeverity("HIGH");
                suggestion.setFixSuggestion("1. Check server logs for error details\n"
                        + "2. Verify database connectivity\n"
                        + "3. Check for recent code changes that may have introduced bugs");
                break;

            case "STATUS_CODE_MISMATCH":
                suggestion.setRootCause("Expected HTTP " + failure.getExpectedStatus()
                        + " but received HTTP " + failure.getActualStatus() + ". "
                        + "The API behavior may have changed.");
                suggestion.setSeverity("MEDIUM");
                suggestion.setFixSuggestion("1. Verify the expected status code is correct for this operation\n"
                        + "2. Check if the API contract has changed\n"
                        + "3. Review recent API changes");
                suggestion.setCodeExample("UPDATE_EXPECTED_STATUS");
                suggestion.setReferenceLink(String.valueOf(failure.getActualStatus()));
                break;

            case "RESPONSE_BODY_ERROR":
                suggestion.setRootCause("Response body does not match expected content. "
                        + "The API response format may have changed.");
                suggestion.setSeverity("MEDIUM");
                suggestion.setFixSuggestion("1. Check if the response format has changed\n"
                        + "2. Update expected body contains assertions\n"
                        + "3. Review API documentation for current response format");
                suggestion.setCodeExample("UPDATE_EXPECTED_BODY");
                break;

            default:
                suggestion.setRootCause("HTTP " + failure.getActualStatus()
                        + " returned, expected " + failure.getExpectedStatus());
                suggestion.setSeverity("MEDIUM");
                suggestion.setFixSuggestion("Check the API endpoint implementation and ensure it handles the request correctly.");
                break;
        }

        suggestion.setAffectedFiles(new ArrayList<>());
        return suggestion;
    }

    /**
     * 从AI响应中提取JSON数组
     */
    private String extractJsonArray(String aiResponse) {
        if (aiResponse == null || aiResponse.trim().isEmpty()) {
            return "[]";
        }

        String text = aiResponse.trim();

        // 移除markdown代码块标记
        Pattern codeBlockPattern = Pattern.compile("```(?:json)?\\s*\\n?(.*?)\\n?```", Pattern.DOTALL);
        Matcher matcher = codeBlockPattern.matcher(text);
        if (matcher.find()) {
            text = matcher.group(1).trim();
        }

        text = text.trim();
        if (text.startsWith("[") && text.endsWith("]")) {
            return text;
        }

        // 尝试找到第一个 [ 和最后一个 ]
        int firstBracket = text.indexOf('[');
        int lastBracket = text.lastIndexOf(']');
        if (firstBracket >= 0 && lastBracket > firstBracket) {
            return text.substring(firstBracket, lastBracket + 1);
        }

        return text;
    }

    /**
     * 将失败列表分批
     */
    private List<List<TestResult>> splitIntoBatches(List<TestResult> failures, int batchSize) {
        List<List<TestResult>> batches = new ArrayList<>();
        for (int i = 0; i < failures.size(); i += batchSize) {
            int end = Math.min(i + batchSize, failures.size());
            batches.add(failures.subList(i, end));
        }
        return batches;
    }

    /**
     * 模糊匹配测试用例名称
     */
    private TestCase findTestCaseByName(String testName, Map<String, TestCase> testCases) {
        // 精确匹配
        if (testCases.containsKey(testName)) {
            return testCases.get(testName);
        }

        // 包含匹配
        for (Map.Entry<String, TestCase> entry : testCases.entrySet()) {
            if (entry.getKey().contains(testName) || testName.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // 忽略大小写匹配
        String lowerName = testName.toLowerCase();
        for (Map.Entry<String, TestCase> entry : testCases.entrySet()) {
            if (entry.getKey().toLowerCase().equals(lowerName)) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * 安全获取字符串字段
     */
    private String getStringField(Map<String, Object> map, String key) {
        return getStringField(map, key, null);
    }

    /**
     * 安全获取字符串字段（带默认值）
     */
    private String getStringField(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        String strValue = value.toString().trim();
        return strValue.isEmpty() ? defaultValue : strValue;
    }

    /**
     * 截断字符串
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + "... [truncated, total " + str.length() + " chars]";
    }

    /**
     * 获取AiClient实例（供外部使用）
     */
    public AiClient getAiClient() {
        return aiClient;
    }
}
