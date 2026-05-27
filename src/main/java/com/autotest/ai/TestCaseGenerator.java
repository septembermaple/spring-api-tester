package com.autotest.ai;

import com.autotest.config.Config;
import com.autotest.executor.TestCase;
import com.autotest.parser.EndpointDefinition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 测试用例生成器 - 使用AI根据API元数据自动生成测试用例
 * <p>
 * 支持单个接口生成和批量并发生成，内置健壮的JSON解析，
 * 能够处理AI返回的各种格式（带markdown代码块、纯JSON、带前后缀文本等）。
 */
public class TestCaseGenerator {

    private static final Logger logger = Logger.getLogger(TestCaseGenerator.class.getName());

    private static final String SYSTEM_PROMPT =
            "You are an expert API tester and QA engineer. Given an API endpoint definition, generate comprehensive test cases.\n" +
            "\n" +
            "For each test case, provide a JSON object with the following fields:\n" +
            "- \"name\": A descriptive test name (e.g., \"Create user with valid data\")\n" +
            "- \"description\": What this test verifies\n" +
            "- \"method\": HTTP method (GET, POST, PUT, DELETE, PATCH)\n" +
            "- \"url\": The full URL path (use path variables like {id} as needed)\n" +
            "- \"headers\": Object of request headers (key-value pairs), or null if not needed\n" +
            "- \"body\": Request body as JSON string (or null for GET/DELETE)\n" +
            "- \"queryParams\": Object of query parameters (key-value pairs), or null if not needed\n" +
            "- \"pathParams\": Object of path parameters (key-value pairs), or null if not needed\n" +
            "- \"expectedStatus\": Expected HTTP status code (integer)\n" +
            "- \"expectedBodyContains\": Array of strings that should be present in the response body, or null\n" +
            "- \"validateJsonSchema\": Boolean, whether to validate response JSON schema (default false)\n" +
            "- \"priority\": Test priority - \"P0\" (critical), \"P1\" (high), \"P2\" (medium), \"P3\" (low)\n" +
            "- \"tags\": Array of tag strings for categorization (e.g., [\"smoke\", \"regression\", \"security\"])\n" +
            "\n" +
            "You MUST generate test cases covering these dimensions:\n" +
            "1. **Normal/Happy Path** (priority: P0): Valid inputs, successful response expected\n" +
            "2. **Parameter Validation** (priority: P1): Missing required parameters, invalid types, wrong formats\n" +
            "3. **Boundary Values** (priority: P1): Empty strings, zero values, maximum length, minimum values\n" +
            "4. **Error Scenarios** (priority: P2): Resource not found, conflict, unauthorized, rate limiting\n" +
            "5. **Security Tests** (priority: P1): SQL injection, XSS in inputs, missing auth headers (if applicable)\n" +
            "\n" +
            "IMPORTANT RULES:\n" +
            "- Return ONLY a valid JSON array, no markdown code fences, no explanatory text\n" +
            "- Each test case must be a complete, self-contained JSON object\n" +
            "- Use realistic test data values (not placeholders like \"xxx\")\n" +
            "- For body fields, use proper JSON strings with escaped quotes\n" +
            "- Include at least 5 test cases per endpoint, covering all dimensions above\n" +
            "- Set appropriate priority levels based on test importance\n" +
            "- Add relevant tags for test categorization\n" +
            "\n" +
            "CRITICAL: For request body fields, you MUST:\n" +
            "- Use the exact field names and types from the Request Body Fields section\n" +
            "- For enum fields, use ONLY the allowed enum values listed\n" +
            "- For Integer fields, use numeric values (not strings)\n" +
            "- For Date fields, use the correct date format specified\n" +
            "- Follow the @ApiModelProperty examples when provided";

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final Config config;

    /** 每批发送给AI的最大失败数量（避免请求过大） */
    private static final int MAX_ENDPOINTS_PER_BATCH = 5;

    public TestCaseGenerator(Config config) {
        this.config = config;
        this.aiClient = new AiClient(config);
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 为单个接口生成测试用例
     * <p>
     * 调用AI生成全面的测试用例，如果AI调用失败则降级为生成默认用例。
     *
     * @param endpoint 接口定义
     * @return 测试用例列表
     */
    public List<TestCase> generateTestCases(EndpointDefinition endpoint) {
        List<TestCase> testCases = new ArrayList<>();

        try {
            String userPrompt = buildPromptForEndpoint(endpoint);
            logger.info("Generating test cases for: " + endpoint.getHttpMethod() + " " + endpoint.getUrlPath());

            String aiResponse = aiClient.chatCompletion(SYSTEM_PROMPT, userPrompt);
            logger.fine("AI response for " + endpoint.getUrlPath() + ": " + aiResponse);

            // 解析AI返回的JSON测试用例
            testCases = parseTestCases(aiResponse, endpoint);

            logger.info("Generated " + testCases.size() + " test cases for: " + endpoint.getUrlPath());

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error generating test cases for "
                    + endpoint.getUrlPath() + ": " + e.getMessage(), e);
            // 生成默认的基础测试用例作为降级方案
            testCases.add(createDefaultTestCase(endpoint));
            logger.info("Falling back to default test case for: " + endpoint.getUrlPath());
        }

        return testCases;
    }

    /**
     * 批量生成测试用例（支持并发）
     * <p>
     * 使用ExecutorService并发地为多个接口生成测试用例。
     * 线程数从config.getThreads()获取，如果未配置则默认为4。
     *
     * @param endpoints 接口定义列表
     * @return 所有接口的测试用例
     */
    public List<TestCase> generateTestCases(List<EndpointDefinition> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            return new ArrayList<>();
        }

        // 如果只有一个接口，直接同步生成
        if (endpoints.size() == 1) {
            return generateTestCases(endpoints.get(0));
        }

        int threadCount = config.getThreads() > 0 ? config.getThreads() : 4;
        logger.info("Batch generating test cases for " + endpoints.size()
                + " endpoints using " + threadCount + " threads");

        ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(threadCount, endpoints.size()),
                new ThreadFactory() {
                    private int count = 0;

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "test-gen-" + (count++));
                        t.setDaemon(true);
                        return t;
                    }
                });

        List<Future<List<TestCase>>> futures = new ArrayList<>();

        for (EndpointDefinition endpoint : endpoints) {
            futures.add(executor.submit(new Callable<List<TestCase>>() {
                @Override
                public List<TestCase> call() throws Exception {
                    return generateTestCases(endpoint);
                }
            }));
        }

        List<TestCase> allTestCases = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        for (int i = 0; i < futures.size(); i++) {
            try {
                // 设置单个接口生成的超时时间
                List<TestCase> cases = futures.get(i).get(config.getTimeout() * 3, TimeUnit.MILLISECONDS);
                allTestCases.addAll(cases);
                successCount++;
            } catch (TimeoutException e) {
                logger.warning("Timeout generating test cases for: "
                        + endpoints.get(i).getUrlPath());
                allTestCases.add(createDefaultTestCase(endpoints.get(i)));
                failCount++;
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error generating test cases for: "
                        + endpoints.get(i).getUrlPath(), e);
                allTestCases.add(createDefaultTestCase(endpoints.get(i)));
                failCount++;
            }
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("Batch generation completed: " + successCount + " succeeded, "
                + failCount + " failed, " + allTestCases.size() + " test cases total");

        return allTestCases;
    }

    /**
     * 构建接口描述的详细提示词
     * <p>
     * 包含接口的完整签名、参数详细描述、请求体/响应体示例结构，
     * 以及明确的JSON输出格式要求。
     *
     * @param endpoint 接口定义
     * @return 构建好的用户提示词
     */
    private String buildPromptForEndpoint(EndpointDefinition endpoint) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("请为以下Spring Boot REST API接口生成全面的测试用例。\n\n");

        // 接口基本信息
        prompt.append("【接口信息】\n");
        prompt.append("HTTP方法: ").append(endpoint.getHttpMethod()).append("\n");
        prompt.append("URL路径: ").append(endpoint.getFullPath()).append("\n");
        prompt.append("接口描述: ").append(endpoint.getDescription() != null ? endpoint.getDescription() : "无").append("\n\n");

        // 请求参数详细信息
        if (endpoint.getParameters() != null && !endpoint.getParameters().isEmpty()) {
            prompt.append("【请求参数】\n");
            for (EndpointDefinition.ParameterInfo param : endpoint.getParameters()) {
                prompt.append("- ").append(param.getName());
                prompt.append(" (").append(param.getType()).append(")");
                prompt.append(" [").append(param.getSource()).append("]");
                if (param.isRequired()) {
                    prompt.append(" 必填");
                }
                if (param.getDescription() != null) {
                    prompt.append(" - ").append(param.getDescription());
                }
                prompt.append("\n");
            }
            prompt.append("\n");
        }

        // 请求体类型信息（关键改进）
        if (endpoint.getRequestBodyType() != null && !endpoint.getRequestBodyType().isEmpty()) {
            prompt.append("【请求体类型】\n");
            prompt.append("类型: ").append(endpoint.getRequestBodyType()).append("\n");

            // 尝试获取请求体类的字段信息
            String requestBodyFields = getRequestBodyFieldsInfo(endpoint.getRequestBodyType());
            if (requestBodyFields != null && !requestBodyFields.isEmpty()) {
                prompt.append("字段说明:\n").append(requestBodyFields).append("\n");
            }
            prompt.append("\n");
        }

        // 响应信息
        prompt.append("【响应信息】\n");
        prompt.append("Content-Type: ").append(endpoint.getProduces() != null ? endpoint.getProduces() : "application/json").append("\n\n");

        // 生成要求
        prompt.append("【生成要求】\n");
        prompt.append("1. 生成5-10个测试用例，覆盖以下维度:\n");
        prompt.append("   - 正常流程: 标准参数组合\n");
        prompt.append("   - 参数校验: 必填字段缺失、格式错误\n");
        prompt.append("   - 边界值: 极值、空值、超长值\n");
        prompt.append("   - 异常场景: 无效参数、越权访问\n");
        prompt.append("   - 安全性: SQL注入、XSS尝试\n\n");

        prompt.append("2. 请求体字段必须根据【请求体类型】中的字段说明生成，注意:\n");
        prompt.append("   - 枚举类型字段必须使用枚举值，不能随意编造\n");
        prompt.append("   - 注意@ApiModelProperty中的value说明和example示例\n");
        prompt.append("   - Integer类型字段不要传字符串\n");
        prompt.append("   - 日期格式必须符合要求\n\n");

        prompt.append("3. 每个用例包含:\n");
        prompt.append("   - name: 用例名称\n");
        prompt.append("   - priority: 优先级(P0/P1/P2/P3)\n");
        prompt.append("   - request: 请求体JSON\n");
        prompt.append("   - expectedStatus: 期望HTTP状态码\n");
        prompt.append("   - description: 用例描述\n\n");

        prompt.append("请直接返回JSON数组格式的测试用例。");

        return prompt.toString();
    }

    /**
     * 获取请求体类的字段信息
     * 尝试从源码中解析请求体DTO类的字段和注解
     */
    private String getRequestBodyFieldsInfo(String className) {
        if (className == null || className.isEmpty()) {
            return null;
        }

        try {
            // 简化类名（去掉包名）
            String simpleName = className;
            if (className.contains(".")) {
                simpleName = className.substring(className.lastIndexOf(".") + 1);
            }

            // 在Git项目中搜索这个类文件
            String gitPath = config.getGitPath();
            File projectDir = new File(gitPath);

            // 尝试找到类文件
            File classFile = findClassFile(projectDir, simpleName + ".java");
            if (classFile == null || !classFile.exists()) {
                return null;
            }

            // 解析类文件获取字段信息
            return parseClassFields(classFile);

        } catch (Exception e) {
            System.err.println("Error getting request body fields for " + className + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * 在项目中查找类文件
     */
    private File findClassFile(File dir, String fileName) {
        if (dir == null || !dir.exists()) return null;

        File[] files = dir.listFiles();
        if (files == null) return null;

        for (File file : files) {
            if (file.isDirectory() && !file.getName().equals("target") && !file.getName().equals(".git")) {
                File found = findClassFile(file, fileName);
                if (found != null) return found;
            } else if (file.getName().equals(fileName)) {
                return file;
            }
        }
        return null;
    }

    /**
     * 解析类文件获取字段和注解信息
     */
    private String parseClassFields(File classFile) {
        StringBuilder fields = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new FileReader(classFile))) {
            String line;
            String lastComment = null;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // 提取@ApiModelProperty注解
                if (line.contains("@ApiModelProperty")) {
                    // 提取value值
                    int valueStart = line.indexOf("value = \"");
                    if (valueStart > 0) {
                        valueStart += 9;
                        int valueEnd = line.indexOf("\"", valueStart);
                        if (valueEnd > valueStart) {
                            lastComment = line.substring(valueStart, valueEnd);
                        }
                    }
                    // 尝试提取example值
                    int exampleStart = line.indexOf("example = \"");
                    if (exampleStart > 0) {
                        exampleStart += 11;
                        int exampleEnd = line.indexOf("\"", exampleStart);
                        if (exampleEnd > exampleStart) {
                            lastComment = (lastComment != null ? lastComment + " 示例:" : "示例:")
                                         + line.substring(exampleStart, exampleEnd);
                        }
                    }
                }

                // 提取字段定义
                if (line.contains("private ") && !line.contains("(") && !line.contains("static")) {
                    // 解析字段类型和名称
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 3) {
                        String type = parts[1];
                        String name = parts[2].replace(";", "").replace("=", "").trim();

                        fields.append("  - ").append(name).append(" (").append(type).append(")");
                        if (lastComment != null) {
                            fields.append(": ").append(lastComment);
                        }
                        fields.append("\n");
                    }
                    lastComment = null;
                }
            }

        } catch (Exception e) {
            System.err.println("Error parsing class file: " + e.getMessage());
        }

        return fields.toString();
    }

    /**
     * 解析AI返回的测试用例JSON
     * <p>
     * 增强JSON解析的健壮性：
     * - 处理AI返回的各种格式（带markdown代码块、纯JSON、带前后缀文本）
     * - 解析 expectedBodyContains 字段为List
     * - 解析 validateJsonSchema 字段
     * - 解析 priority 字段
     * - 解析 tags 字段
     * - 错误处理：解析失败时尝试修复常见JSON格式问题
     *
     * @param aiResponse AI返回的原始文本
     * @param endpoint   接口定义（用于设置默认值）
     * @return 解析后的测试用例列表
     * @throws Exception 解析失败时抛出异常
     */
    private List<TestCase> parseTestCases(String aiResponse, EndpointDefinition endpoint) throws Exception {
        String json = extractJsonArray(aiResponse);

        // 尝试直接解析
        try {
            return doParseTestCases(json, endpoint);
        } catch (Exception e) {
            logger.warning("Direct JSON parse failed, attempting repair: " + e.getMessage());
        }

        // 尝试修复常见JSON格式问题后重新解析
        String repaired = repairJson(json);
        try {
            return doParseTestCases(repaired, endpoint);
        } catch (Exception e) {
            logger.warning("Repaired JSON parse also failed: " + e.getMessage());
        }

        // 最后尝试：提取第一个 [ 和最后一个 ] 之间的内容
        int firstBracket = aiResponse.indexOf('[');
        int lastBracket = aiResponse.lastIndexOf(']');
        if (firstBracket >= 0 && lastBracket > firstBracket) {
            String extracted = aiResponse.substring(firstBracket, lastBracket + 1);
            try {
                return doParseTestCases(extracted, endpoint);
            } catch (Exception e) {
                logger.warning("Bracket extraction parse also failed: " + e.getMessage());
            }
        }

        throw new Exception("Failed to parse AI response as test cases after all repair attempts");
    }

    /**
     * 实际执行JSON解析为TestCase列表
     */
    private List<TestCase> doParseTestCases(String json, EndpointDefinition endpoint) throws Exception {
        List<Map<String, Object>> rawCases = objectMapper.readValue(
                json, new TypeReference<List<Map<String, Object>>>() {});

        List<TestCase> testCases = new ArrayList<>();

        for (Map<String, Object> rawCase : rawCases) {
            try {
                TestCase testCase = new TestCase();
                testCase.setEndpointUrl(endpoint.getUrlPath());

                // 基本信息
                testCase.setName(getStringField(rawCase, "name",
                        "Test for " + endpoint.getMethodName()));
                testCase.setDescription(getStringField(rawCase, "description", ""));
                testCase.setHttpMethod(getStringField(rawCase, "method", endpoint.getHttpMethod()));

                // URL - 支持覆盖
                String url = getStringField(rawCase, "url", endpoint.getUrlPath());
                testCase.setEndpointUrl(url);

                // 请求体
                Object body = rawCase.get("body");
                if (body != null) {
                    testCase.setBody(body.toString());
                }

                // 期望状态码
                Object expectedStatus = rawCase.get("expectedStatus");
                if (expectedStatus != null) {
                    if (expectedStatus instanceof Number) {
                        testCase.setExpectedStatus(((Number) expectedStatus).intValue());
                    } else {
                        try {
                            testCase.setExpectedStatus(Integer.parseInt(expectedStatus.toString()));
                        } catch (NumberFormatException e) {
                            testCase.setExpectedStatus(200);
                        }
                    }
                } else {
                    testCase.setExpectedStatus(200);
                }

                // 请求头
                Object headers = rawCase.get("headers");
                if (headers instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> headersMap = (Map<String, String>) headers;
                    testCase.setHeaders(headersMap);
                }

                // 查询参数
                Object queryParams = rawCase.get("queryParams");
                if (queryParams instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> qpMap = (Map<String, String>) queryParams;
                    testCase.setQueryParams(qpMap);
                }

                // 路径参数
                Object pathParams = rawCase.get("pathParams");
                if (pathParams instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> ppMap = (Map<String, String>) pathParams;
                    testCase.setPathParams(ppMap);
                }

                // expectedBodyContains - 解析为List<String>
                Object expectedBodyContains = rawCase.get("expectedBodyContains");
                if (expectedBodyContains instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> containsList = (List<String>) expectedBodyContains;
                    // 过滤掉null值
                    List<String> filtered = new ArrayList<>();
                    for (String item : containsList) {
                        if (item != null && !item.isEmpty()) {
                            filtered.add(item);
                        }
                    }
                    testCase.setExpectedBodyContains(filtered);
                } else if (expectedBodyContains instanceof String) {
                    // 如果AI返回的是逗号分隔的字符串
                    String[] parts = ((String) expectedBodyContains).split(",");
                    List<String> containsList = new ArrayList<>();
                    for (String part : parts) {
                        String trimmed = part.trim();
                        if (!trimmed.isEmpty()) {
                            containsList.add(trimmed);
                        }
                    }
                    if (!containsList.isEmpty()) {
                        testCase.setExpectedBodyContains(containsList);
                    }
                }

                // validateJsonSchema
                Object validateSchema = rawCase.get("validateJsonSchema");
                if (validateSchema instanceof Boolean) {
                    testCase.setValidateJsonSchema((Boolean) validateSchema);
                } else if (validateSchema instanceof String) {
                    testCase.setValidateJsonSchema(Boolean.parseBoolean((String) validateSchema));
                }

                // priority
                String priority = getStringField(rawCase, "priority", "P1");
                if (isValidPriority(priority)) {
                    testCase.setPriority(priority);
                } else {
                    testCase.setPriority("P1");
                }

                // tags
                Object tags = rawCase.get("tags");
                if (tags instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> tagsList = (List<String>) tags;
                    List<String> filteredTags = new ArrayList<>();
                    for (String tag : tagsList) {
                        if (tag != null && !tag.isEmpty()) {
                            filteredTags.add(tag);
                        }
                    }
                    testCase.setTags(filteredTags);
                }

                testCases.add(testCase);

            } catch (Exception e) {
                logger.warning("Failed to parse individual test case: " + rawCase
                        + " - " + e.getMessage());
                // 跳过解析失败的个别用例，继续处理其他用例
            }
        }

        return testCases;
    }

    /**
     * 创建默认的基础测试用例
     * <p>
     * 根据HTTP方法和参数生成合理的默认值，比简单的空壳用例更智能。
     *
     * @param endpoint 接口定义
     * @return 智能生成的默认测试用例
     */
    private TestCase createDefaultTestCase(EndpointDefinition endpoint) {
        TestCase testCase = new TestCase();

        String method = endpoint.getHttpMethod().toUpperCase();
        testCase.setHttpMethod(method);
        testCase.setEndpointUrl(endpoint.getUrlPath());
        testCase.setName("Default " + method + " test for " + endpoint.getMethodName());
        testCase.setDescription("Basic connectivity test for " + endpoint.getUrlPath());
        testCase.setPriority("P1");
        testCase.setTags(Arrays.asList("default", "smoke"));

        // 根据HTTP方法设置合理的期望状态码
        switch (method) {
            case "POST":
                testCase.setExpectedStatus(201);
                break;
            case "PUT":
            case "PATCH":
                testCase.setExpectedStatus(200);
                break;
            case "DELETE":
                testCase.setExpectedStatus(204);
                break;
            default:
                testCase.setExpectedStatus(200);
                break;
        }

        // 设置默认的Content-Type请求头
        Map<String, String> defaultHeaders = new LinkedHashMap<>();
        defaultHeaders.put("Content-Type", "application/json");
        defaultHeaders.put("Accept", "application/json");
        testCase.setHeaders(defaultHeaders);

        // 如果有请求体类型，生成示例请求体
        if (endpoint.getRequestBodyType() != null && !"void".equals(endpoint.getRequestBodyType())) {
            String exampleBody = generateExampleBody(endpoint);
            testCase.setBody(exampleBody);
        }

        // 如果有查询参数，生成示例查询参数
        if (endpoint.getParameters() != null) {
            Map<String, String> queryParams = new LinkedHashMap<>();
            Map<String, String> pathParams = new LinkedHashMap<>();

            for (EndpointDefinition.ParameterInfo param : endpoint.getParameters()) {
                String source = param.getSource();
                if ("QUERY".equalsIgnoreCase(source)) {
                    queryParams.put(param.getName(), getExampleValue(param));
                } else if ("PATH".equalsIgnoreCase(source)) {
                    pathParams.put(param.getName(), getExampleValue(param));
                }
            }

            if (!queryParams.isEmpty()) {
                testCase.setQueryParams(queryParams);
            }
            if (!pathParams.isEmpty()) {
                testCase.setPathParams(pathParams);
            }
        }

        return testCase;
    }

    // ===== 私有辅助方法 =====

    /**
     * 从AI响应中提取JSON数组
     * <p>
     * 处理各种格式：
     * - 纯JSON数组
     * - 包含在 ```json ... ``` 中的JSON
     * - 包含在 ``` ... ``` 中的JSON
     * - 带有前后缀文本的JSON
     */
    private String extractJsonArray(String aiResponse) {
        if (aiResponse == null || aiResponse.trim().isEmpty()) {
            return "[]";
        }

        String text = aiResponse.trim();

        // 移除markdown代码块标记
        // 匹配 ```json ... ``` 或 ``` ... ```
        Pattern codeBlockPattern = Pattern.compile("```(?:json)?\\s*\\n?(.*?)\\n?```", Pattern.DOTALL);
        Matcher matcher = codeBlockPattern.matcher(text);
        if (matcher.find()) {
            text = matcher.group(1).trim();
        }

        // 如果文本以 [ 开头和 ] 结尾，直接返回
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
     * 修复常见的JSON格式问题
     */
    private String repairJson(String json) {
        String repaired = json;

        // 移除尾部逗号（JSON标准不允许）: }, ] -> },\n] 中的逗号
        repaired = repaired.replaceAll(",\\s*([\\]}])", "$1");

        // 修复单引号为双引号（简单替换，不处理嵌套情况）
        // 注意：这个替换比较激进，只在其他修复都失败时使用
        // repaired = repaired.replace("'", "\"");

        // 移除控制字符（除\n\r\t外的不可见字符）
        repaired = repaired.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");

        // 修复未转义的换行符在字符串值中
        // 这是一个启发式修复，将字符串内的裸换行替换为\n
        repaired = repaired.replaceAll("\"\\s*\\n\\s*\"", "\"\\n\"");

        return repaired.trim();
    }

    /**
     * 安全获取字符串字段
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
     * 验证优先级值是否有效
     */
    private boolean isValidPriority(String priority) {
        if (priority == null) return false;
        String upper = priority.toUpperCase();
        return "P0".equals(upper) || "P1".equals(upper)
                || "P2".equals(upper) || "P3".equals(upper);
    }

    /**
     * 解释参数来源
     */
    private String explainSource(String source) {
        if (source == null) return "unknown";
        switch (source.toUpperCase()) {
            case "PATH": return "extracted from URL path segment, e.g., /users/{id}";
            case "QUERY": return "passed as URL query parameter, e.g., ?page=1&size=10";
            case "HEADER": return "passed as HTTP request header";
            case "BODY": return "passed in the request body (JSON)";
            case "FORM": return "passed as form data";
            case "REQUEST": return "injected from the HTTP request object";
            case "MODEL": return "Spring MVC @ModelAttribute, form binding";
            default: return source;
        }
    }

    /**
     * 根据参数类型生成示例值
     */
    private String getExampleValue(EndpointDefinition.ParameterInfo param) {
        if (param.getDefaultValue() != null) {
            return param.getDefaultValue();
        }

        String type = param.getType();
        if (type == null) return "1";

        String lowerType = type.toLowerCase();

        if (lowerType.contains("string") || lowerType.equals("java.lang.string")) {
            return "test-" + param.getName();
        } else if (lowerType.equals("long") || lowerType.equals("java.lang.long")) {
            return "1";
        } else if (lowerType.equals("integer") || lowerType.equals("int")
                || lowerType.equals("java.lang.integer")) {
            return "1";
        } else if (lowerType.equals("boolean") || lowerType.equals("java.lang.boolean")) {
            return "true";
        } else if (lowerType.equals("double") || lowerType.equals("java.lang.double")
                || lowerType.equals("float") || lowerType.equals("java.lang.float")) {
            return "1.0";
        } else if (lowerType.contains("list") || lowerType.contains("array")
                || lowerType.contains("set") || lowerType.contains("collection")) {
            return "1,2,3";
        } else if (lowerType.contains("localdate")) {
            return "2024-01-01";
        } else if (lowerType.contains("localdatetime")) {
            return "2024-01-01T00:00:00";
        } else if (lowerType.contains("pageable") || lowerType.contains("page")) {
            return "0";
        } else {
            return "1";
        }
    }

    /**
     * 根据接口定义生成示例请求体
     */
    private String generateExampleBody(EndpointDefinition endpoint) {
        String bodyType = endpoint.getRequestBodyType();
        if (bodyType == null) {
            return "{}";
        }

        // 从类型名提取简单的字段名
        String simpleName = bodyType.contains(".") ? bodyType.substring(bodyType.lastIndexOf('.') + 1) : bodyType;

        // 根据常见命名约定生成示例
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        if (endpoint.getParameters() != null) {
            boolean first = true;
            for (EndpointDefinition.ParameterInfo param : endpoint.getParameters()) {
                if ("BODY".equalsIgnoreCase(param.getSource()) || "FORM".equalsIgnoreCase(param.getSource())
                        || "MODEL".equalsIgnoreCase(param.getSource())) {
                    if (!first) {
                        sb.append(",\n");
                    }
                    sb.append("  \"").append(param.getName()).append("\": ");
                    sb.append(formatExampleJsonValue(param));
                    first = false;
                }
            }
        }

        // 如果没有BODY参数，根据类型名生成一些通用字段
        if (endpoint.getParameters() == null || endpoint.getParameters().isEmpty()
                || !hasBodyParameter(endpoint)) {
            sb.append("  \"id\": 1,\n");
            sb.append("  \"name\": \"example\"");
        }

        sb.append("\n}");
        return sb.toString();
    }

    /**
     * 根据接口定义生成示例响应体
     */
    private String generateExampleResponse(EndpointDefinition endpoint) {
        String returnType = endpoint.getReturnType();
        if (returnType == null || "void".equals(returnType)) {
            return "No response body (void)";
        }

        if (returnType.contains("List") || returnType.contains("Collection") || returnType.contains("Page")) {
            return "[\n  {\n    \"id\": 1,\n    \"name\": \"example\"\n  }\n]";
        }

        return "{\n  \"id\": 1,\n  \"name\": \"example\",\n  \"message\": \"success\"\n}";
    }

    /**
     * 检查是否有BODY来源的参数
     */
    private boolean hasBodyParameter(EndpointDefinition endpoint) {
        if (endpoint.getParameters() == null) return false;
        for (EndpointDefinition.ParameterInfo param : endpoint.getParameters()) {
            String source = param.getSource();
            if ("BODY".equalsIgnoreCase(source) || "FORM".equalsIgnoreCase(source)
                    || "MODEL".equalsIgnoreCase(source)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 根据参数类型格式化JSON示例值
     */
    private String formatExampleJsonValue(EndpointDefinition.ParameterInfo param) {
        String type = param.getType();
        if (type == null) return "\"value\"";

        String lowerType = type.toLowerCase();

        if (lowerType.contains("string") || lowerType.equals("java.lang.string")) {
            return "\"test-" + param.getName() + "\"";
        } else if (lowerType.equals("long") || lowerType.equals("java.lang.long")
                || lowerType.equals("integer") || lowerType.equals("int")
                || lowerType.equals("java.lang.integer")
                || lowerType.equals("double") || lowerType.equals("java.lang.double")
                || lowerType.equals("float") || lowerType.equals("java.lang.float")) {
            return "1";
        } else if (lowerType.equals("boolean") || lowerType.equals("java.lang.boolean")) {
            return "true";
        } else if (lowerType.contains("localdate")) {
            return "\"2024-01-01\"";
        } else if (lowerType.contains("localdatetime")) {
            return "\"2024-01-01T00:00:00\"";
        } else if (lowerType.contains("bigdecimal") || lowerType.contains("biginteger")) {
            return "1";
        } else {
            return "\"value\"";
        }
    }

    /**
     * 获取AiClient实例（供外部使用）
     */
    public AiClient getAiClient() {
        return aiClient;
    }

    /**
     * 流式生成测试用例，带进度回调
     *
     * @param endpoint 接口定义
     * @param progressCallback 进度回调接口，接收AI输出的token
     * @return 测试用例列表
     */
    public List<TestCase> generateTestCasesStreaming(EndpointDefinition endpoint, java.util.function.Consumer<String> progressCallback) {
        try {
            String prompt = buildPromptForEndpoint(endpoint);

            final StringBuilder fullResponse = new StringBuilder();
            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            final Exception[] errorHolder = new Exception[1];

            // 使用流式调用
            aiClient.chatCompletionStream(SYSTEM_PROMPT, prompt, new AiClient.StreamCallback() {
                @Override
                public void onToken(String token) {
                    fullResponse.append(token);
                    // 回调更新UI
                    if (progressCallback != null) {
                        progressCallback.accept(token);
                    }
                }

                @Override
                public void onComplete(String fullText) {
                    // 流完成
                    latch.countDown();
                }

                @Override
                public void onError(Exception e) {
                    System.err.println("[AI Stream] Error: " + e.getMessage());
                    errorHolder[0] = e;
                    latch.countDown();
                }
            });

            // 等待流完成（带超时）
            boolean completed = latch.await(config.getTimeout() * 3, TimeUnit.MILLISECONDS);

            if (!completed) {
                logger.warning("Stream timeout, falling back to normal call");
            }

            if (errorHolder[0] != null) {
                throw errorHolder[0];
            }

            String response = fullResponse.toString();
            if (response.isEmpty()) {
                // 如果流式没有返回，降级为普通调用
                response = aiClient.chatCompletion(SYSTEM_PROMPT, prompt);
            }

            return parseTestCases(response, endpoint);
        } catch (Exception e) {
            System.err.println("Error generating test cases with stream: " + e.getMessage());
            // 降级为普通调用
            return generateTestCases(endpoint);
        }
    }
}
