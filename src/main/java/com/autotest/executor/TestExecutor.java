package com.autotest.executor;

import com.autotest.config.Config;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 功能测试执行器 - 执行HTTP接口测试并收集结果
 */
public class TestExecutor {

    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    /** 健康检查路径候选列表 */
    private static final String[] HEALTH_CHECK_PATHS = {
            "/actuator/health",
            "/health",
            "/api/health"
    };

    /** 默认重试次数 */
    private static final int DEFAULT_RETRY_COUNT = 2;

    private final Config config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private int retryCount = DEFAULT_RETRY_COUNT;

    public TestExecutor(Config config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(config.getTimeout(), TimeUnit.SECONDS)
                .readTimeout(config.getTimeout(), TimeUnit.SECONDS)
                .writeTimeout(config.getTimeout(), TimeUnit.SECONDS)
                .build();
    }

    /**
     * 设置重试次数
     *
     * @param retryCount 失败测试的最大重试次数
     */
    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    /**
     * 执行测试用例列表
     *
     * @param testCases 测试用例列表
     * @return 测试结果列表
     */
    public List<TestResult> executeTests(List<TestCase> testCases) {
        if (testCases == null || testCases.isEmpty()) {
            return new ArrayList<>();
        }

        // 执行前先进行健康检查
        System.out.println("[Health Check] Checking target service availability...");
        boolean healthy = healthCheck();
        if (!healthy) {
            System.err.println("[Health Check] WARNING: Target service is not healthy. " +
                    "Tests will proceed but may fail.");
        } else {
            System.out.println("[Health Check] Target service is healthy.");
        }

        int total = testCases.size();
        List<TestResult> results = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(config.getThreads());
        AtomicInteger completed = new AtomicInteger(0);

        System.out.printf("[Test Execution] Starting %d tests with %d threads...%n", total, config.getThreads());

        List<Future<TestResult>> futures = new ArrayList<>();

        for (TestCase testCase : testCases) {
            Future<TestResult> future = executor.submit(() -> {
                TestResult result = executeSingleTest(testCase);
                int done = completed.incrementAndGet();
                int percent = (int) (done * 100.0 / total);
                System.out.printf("  [%d/%d %3d%%] %s - %s - %s%n",
                        done, total, percent,
                        result.isPassed() ? "PASS" : "FAIL",
                        testCase.getHttpMethod(),
                        testCase.getName());
                return result;
            });
            futures.add(future);
        }

        // 收集结果
        for (Future<TestResult> future : futures) {
            try {
                results.add(future.get());
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("Error executing test: " + e.getMessage());
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

        // 支持重试失败的测试
        if (retryCount > 0) {
            List<TestResult> failedResults = new ArrayList<>();
            for (TestResult r : results) {
                if (!r.isPassed()) {
                    failedResults.add(r);
                }
            }

            if (!failedResults.isEmpty()) {
                System.out.printf("%n[Retry] Retrying %d failed tests (max %d retries)...%n",
                        failedResults.size(), retryCount);

                // 创建新的ExecutorService用于重试
                ExecutorService retryExecutor = Executors.newFixedThreadPool(config.getThreads());

                for (int attempt = 1; attempt <= retryCount; attempt++) {
                    List<TestCase> retryCases = new ArrayList<>();
                    List<Integer> retryIndices = new ArrayList<>();

                    for (int i = 0; i < results.size(); i++) {
                        if (!results.get(i).isPassed()) {
                            retryCases.add(testCases.get(i));
                            retryIndices.add(i);
                        }
                    }

                    if (retryCases.isEmpty()) {
                        break;
                    }

                    System.out.printf("[Retry] Attempt %d/%d for %d tests...%n",
                            attempt, retryCount, retryCases.size());

                    AtomicInteger retryCompleted = new AtomicInteger(0);
                    List<Future<TestResult>> retryFutures = new ArrayList<>();

                    for (int j = 0; j < retryCases.size(); j++) {
                        final int idx = retryIndices.get(j);
                        final TestCase tc = retryCases.get(j);
                        Future<TestResult> retryFuture = retryExecutor.submit(() -> {
                            TestResult result = executeSingleTest(tc);
                            int done = retryCompleted.incrementAndGet();
                            System.out.printf("  [Retry %d/%d] %s - %s%n",
                                    done, retryCases.size(),
                                    result.isPassed() ? "PASS" : "FAIL",
                                    tc.getName());
                            return result;
                        });
                        retryFutures.add(retryFuture);
                    }

                    int retryIdx = 0;
                    for (Future<TestResult> retryFuture : retryFutures) {
                        try {
                            TestResult retryResult = retryFuture.get();
                            if (retryResult.isPassed()) {
                                results.set(retryIndices.get(retryIdx), retryResult);
                            }
                            retryIdx++;
                        } catch (InterruptedException | ExecutionException e) {
                            System.err.println("Error during retry: " + e.getMessage());
                            retryIdx++;
                        }
                    }
                }

                retryExecutor.shutdown();
                try {
                    if (!retryExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        retryExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    retryExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }

        // 打印摘要
        long passed = results.stream().filter(TestResult::isPassed).count();
        long failed = results.size() - passed;
        System.out.printf("%n=== Test Summary ===%n");
        System.out.printf("Total: %d, Passed: %d, Failed: %d, Pass Rate: %.1f%%%n",
                results.size(), passed, failed,
                results.size() > 0 ? (passed * 100.0 / results.size()) : 0);

        return results;
    }

    /**
     * 执行单个测试用例
     */
    public TestResult executeSingleTest(TestCase testCase) {
        TestResult result = new TestResult();
        result.setTestCaseName(testCase.getName());
        result.setHttpMethod(testCase.getHttpMethod());
        result.setUrl(testCase.getEndpointUrl());
        result.setExpectedStatus(testCase.getExpectedStatus());
        result.setTimestamp(System.currentTimeMillis());

        // 记录请求体
        result.setRequestBody(testCase.getBody());

        long startTime = System.currentTimeMillis();

        try {
            // 替换路径参数
            String resolvedPath = resolvePathParams(testCase.getEndpointUrl(), testCase.getPathParams());

            // 构建完整URL（含查询参数）
            String fullUrl = buildFullUrl(resolvedPath);
            fullUrl = buildUrlWithQueryParams(fullUrl, testCase.getQueryParams());
            result.setUrl(fullUrl);

            // 构建请求
            Request.Builder requestBuilder = new Request.Builder()
                    .url(fullUrl)
                    .method(testCase.getHttpMethod(), createRequestBody(testCase));

            // 添加请求头
            if (testCase.getHeaders() != null) {
                for (Map.Entry<String, String> entry : testCase.getHeaders().entrySet()) {
                    requestBuilder.addHeader(entry.getKey(), entry.getValue());
                }
            }

            // 设置默认Content-Type
            if (testCase.getBody() != null && !testCase.getBody().isEmpty()) {
                if (testCase.getHeaders() == null || !testCase.getHeaders().containsKey("Content-Type")) {
                    requestBuilder.addHeader("Content-Type", "application/json");
                }
            }

            // 添加认证Token（如果配置了）
            String authToken = config.getAuthToken();
            if (authToken != null && !authToken.isEmpty()) {
                // 去除可能已有的Bearer前缀，统一添加
                if (authToken.startsWith("Bearer ")) {
                    authToken = authToken.substring(7);
                }
                String authHeader = "Bearer " + authToken;
                requestBuilder.addHeader("Authorization", authHeader);
            }

            // 执行请求
            try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
                long duration = System.currentTimeMillis() - startTime;
                result.setDurationMs(duration);
                result.setActualStatus(response.code());

                // 记录响应头
                Headers responseHeaders = response.headers();
                StringBuilder headerBuilder = new StringBuilder();
                for (int i = 0; i < responseHeaders.size(); i++) {
                    if (i > 0) {
                        headerBuilder.append("\n");
                    }
                    headerBuilder.append(responseHeaders.name(i))
                            .append(": ")
                            .append(responseHeaders.value(i));
                }
                result.setResponseHeaders(headerBuilder.toString());

                // 获取响应体
                String responseBody = response.body() != null ? response.body().string() : "";
                result.setResponseBody(responseBody);

                // 记录响应大小
                result.setResponseSize(responseBody.getBytes("UTF-8").length);

                // 验证状态码
                boolean statusMatch = response.code() == testCase.getExpectedStatus();
                result.setPassed(statusMatch);

                if (!statusMatch) {
                    result.setErrorMessage("Expected status " + testCase.getExpectedStatus() +
                            " but got " + response.code());
                }

                // 验证响应体包含内容
                if (statusMatch && testCase.getExpectedBodyContains() != null) {
                    for (String expected : testCase.getExpectedBodyContains()) {
                        if (!responseBody.contains(expected)) {
                            result.setPassed(false);
                            result.setErrorMessage("Response body does not contain expected string: " + expected);
                            break;
                        }
                    }
                }

                // 验证JSON Schema（如果配置了）
                if (result.isPassed() && testCase.isValidateJsonSchema() &&
                        testCase.getExpectedJsonSchema() != null) {
                    try {
                        JsonNode responseJson = objectMapper.readTree(responseBody);
                        JsonNode schemaJson = objectMapper.readTree(testCase.getExpectedJsonSchema());
                        // 简单的schema验证：检查期望的字段是否存在
                        if (schemaJson.has("required")) {
                            JsonNode required = schemaJson.get("required");
                            if (required.isArray()) {
                                for (JsonNode field : required) {
                                    if (!responseJson.has(field.asText())) {
                                        result.setPassed(false);
                                        result.setErrorMessage("Response JSON missing required field: " + field.asText());
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        // JSON Schema验证失败不影响测试结果，仅记录警告
                        System.err.println("Warning: JSON Schema validation error: " + e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            result.setDurationMs(duration);
            result.setPassed(false);
            result.setErrorMessage("Request failed: " + e.getMessage());
            result.setResponseSize(0);
        }

        return result;
    }

    /**
     * 构建完整URL
     */
    private String buildFullUrl(String endpointUrl) {
        String base = config.getBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path = endpointUrl.startsWith("/") ? endpointUrl : "/" + endpointUrl;
        return base + path;
    }

    /**
     * 拼接查询参数到URL
     *
     * @param baseUrl    基础URL
     * @param queryParams 查询参数Map
     * @return 拼接查询参数后的完整URL
     */
    private String buildUrlWithQueryParams(String baseUrl, Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return baseUrl;
        }

        StringBuilder urlBuilder = new StringBuilder(baseUrl);
        boolean first = true;

        // 检查URL中是否已经有查询参数
        if (baseUrl.contains("?")) {
            first = false;
            urlBuilder.append("&");
        } else {
            urlBuilder.append("?");
        }

        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            if (!first) {
                urlBuilder.append("&");
            }
            try {
                urlBuilder.append(URLEncoder.encode(entry.getKey(), "UTF-8"))
                        .append("=")
                        .append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            } catch (java.io.UnsupportedEncodingException e) {
                // UTF-8 is always supported, this should not happen
                urlBuilder.append(entry.getKey())
                        .append("=")
                        .append(entry.getValue());
            }
            first = false;
        }

        return urlBuilder.toString();
    }

    /**
     * 替换URL中的路径参数占位符
     * <p>
     * 例如: /api/users/{userId} + {userId: 123} -> /api/users/123
     *
     * @param url        包含占位符的URL路径
     * @param pathParams 路径参数Map
     * @return 替换后的URL路径
     */
    private String resolvePathParams(String url, Map<String, String> pathParams) {
        if (pathParams == null || pathParams.isEmpty()) {
            return url;
        }

        String resolved = url;
        // 匹配 {paramName} 或 :paramName 格式的占位符
        Pattern pattern = Pattern.compile("\\{([^}]+)\\}|:(\\w+)");
        Matcher matcher = pattern.matcher(resolved);

        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String paramName = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            String replacement = pathParams.get(paramName);
            if (replacement != null) {
                try {
                    matcher.appendReplacement(sb, URLEncoder.encode(replacement, "UTF-8"));
                } catch (java.io.UnsupportedEncodingException e) {
                    matcher.appendReplacement(sb, replacement);
                }
            }
            // 如果没有对应的参数值，保留原始占位符
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * 创建请求体
     */
    private RequestBody createRequestBody(TestCase testCase) {
        if (testCase.getBody() == null || testCase.getBody().isEmpty()) {
            // GET/DELETE请求不需要请求体
            if ("GET".equalsIgnoreCase(testCase.getHttpMethod()) ||
                    "DELETE".equalsIgnoreCase(testCase.getHttpMethod())) {
                return null;
            }
            return RequestBody.create("", JSON_TYPE);
        }
        return RequestBody.create(testCase.getBody(), JSON_TYPE);
    }

    /**
     * 执行健康检查，支持多种健康检查路径
     *
     * @return 服务是否健康
     */
    public boolean healthCheck() {
        for (String healthPath : HEALTH_CHECK_PATHS) {
            try {
                String url = config.getBaseUrl();
                if (url.endsWith("/")) {
                    url = url.substring(0, url.length() - 1);
                }
                url += healthPath;

                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        System.out.println("[Health Check] Health check passed via: " + healthPath);
                        return true;
                    }
                }
            } catch (Exception e) {
                // 当前路径不可用，尝试下一个
                System.out.println("[Health Check] Path " + healthPath + " not available: " + e.getMessage());
            }
        }

        // 所有健康检查路径都不可用时，尝试直接访问baseUrl
        try {
            Request request = new Request.Builder()
                    .url(config.getBaseUrl())
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                // 只要服务有响应就认为可用（即使返回404等）
                System.out.println("[Health Check] Base URL is reachable (status: " + response.code() + ")");
                return true;
            }
        } catch (Exception e) {
            System.err.println("[Health Check] Target service is not reachable: " + e.getMessage());
            return false;
        }
    }
}
