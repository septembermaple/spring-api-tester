package com.autotest.ai;

import com.autotest.config.Config;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AI客户端 - 对接NEW API（OpenAI兼容格式）
 * <p>
 * 支持对话补全（Chat Completion）接口，用于生成测试用例和分析失败原因。
 * 支持普通请求和SSE流式请求，内置指数退避重试机制。
 */
public class AiClient {

    private static final Logger logger = Logger.getLogger(AiClient.class.getName());

    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    /** 最大重试次数 */
    private static final int MAX_RETRIES = 3;

    /** 初始重试等待时间（毫秒） */
    private static final long INITIAL_RETRY_DELAY_MS = 1000L;

    /** 最大重试等待时间（毫秒） */
    private static final long MAX_RETRY_DELAY_MS = 30000L;

    /** SSE可用性检测提示词 */
    private static final String AVAILABILITY_CHECK_PROMPT = "Hi";

    private final Config config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AiClient(Config config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        // AI请求可能需要较长时间，增加超时时间
        int aiTimeout = Math.max(config.getTimeout(), 60); // 至少60秒
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(aiTimeout * 3, TimeUnit.SECONDS)  // 读超时增加到3倍
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 发送Chat Completion请求（带重试机制）
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return AI响应内容
     */
    public String chatCompletion(String systemPrompt, String userPrompt) throws IOException {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", systemPrompt));
        messages.add(new ChatMessage("user", userPrompt));
        return chatCompletionWithRetry(messages);
    }

    /**
     * 发送Chat Completion请求（带历史消息，带重试机制）
     *
     * @param messages 消息列表
     * @return AI响应内容
     */
    public String chatCompletion(List<ChatMessage> messages) throws IOException {
        return chatCompletionWithRetry(messages);
    }

    /**
     * 带指数退避重试的Chat Completion请求
     * <p>
     * 重试策略：
     * - 最多重试 {@link #MAX_RETRIES} 次
     * - 指数退避：1s, 2s, 4s（最大不超过30s）
     * - 仅对服务端错误（5xx）和超时进行重试
     * - 4xx客户端错误不重试
     *
     * @param messages 消息列表
     * @return AI响应内容
     * @throws IOException 所有重试耗尽后抛出最后一次异常
     */
    public String chatCompletionWithRetry(List<ChatMessage> messages) throws IOException {
        validateConfig();

        String url = buildApiUrl();
        String requestBodyJson = buildRequestBodyJson(messages);

        logger.info("Sending chat completion request to: " + url);
        logger.info("Request message count: " + messages.size());
        logger.fine("Request body: " + requestBodyJson);

        IOException lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer " + config.getNewApiKey())
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(requestBodyJson, JSON_TYPE))
                        .build();

                long startTime = System.currentTimeMillis();

                try (Response response = httpClient.newCall(request).execute()) {
                    long elapsed = System.currentTimeMillis() - startTime;

                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "No body";
                        logger.warning("AI API request failed (attempt " + attempt + "/" + MAX_RETRIES
                                + "): HTTP " + response.code() + " - " + errorBody);

                        // 4xx客户端错误不重试，直接抛出
                        if (response.code() >= 400 && response.code() < 500) {
                            throw new IOException("AI API client error (not retryable): "
                                    + response.code() + " - " + errorBody);
                        }

                        // 5xx服务端错误或网络问题，准备重试
                        lastException = new IOException("AI API server error: "
                                + response.code() + " - " + errorBody);

                        if (attempt < MAX_RETRIES) {
                            long delay = calculateBackoffDelay(attempt);
                            logger.info("Retrying in " + delay + "ms (attempt " + (attempt + 1) + "/" + MAX_RETRIES + ")");
                            sleep(delay);
                        }
                        continue;
                    }

                    String responseBody = response.body().string();
                    logger.info("AI API request succeeded in " + elapsed + "ms (attempt " + attempt + "/" + MAX_RETRIES + ")");
                    logger.fine("Response body: " + responseBody);

                    return parseChatResponse(responseBody);
                }

            } catch (IOException e) {
                lastException = e;
                logger.warning("AI API request error (attempt " + attempt + "/" + MAX_RETRIES + "): " + e.getMessage());

                if (attempt < MAX_RETRIES) {
                    long delay = calculateBackoffDelay(attempt);
                    logger.info("Retrying in " + delay + "ms (attempt " + (attempt + 1) + "/" + MAX_RETRIES + ")");
                    sleep(delay);
                }
            }
        }

        logger.severe("AI API request failed after " + MAX_RETRIES + " attempts");
        throw lastException != null ? lastException : new IOException("AI API request failed after " + MAX_RETRIES + " attempts");
    }

    /**
     * 流式Chat Completion（SSE格式）
     * <p>
     * 使用OkHttp手动解析SSE（Server-Sent Events）格式数据流。
     * SSE数据格式：data: {"id":"...","choices":[{"delta":{"content":"token"}}]}
     * 流结束标记：data: [DONE]
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param callback     流式回调
     */
    public void chatCompletionStream(String systemPrompt, String userPrompt, StreamCallback callback) {
        try {
            validateConfig();
        } catch (IOException e) {
            callback.onError(e);
            return;
        }

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", systemPrompt));
        messages.add(new ChatMessage("user", userPrompt));

        String url;
        String requestBodyJson;
        try {
            url = buildApiUrl();
            requestBodyJson = buildStreamRequestBodyJson(messages);
        } catch (IOException e) {
            callback.onError(e);
            return;
        }

        logger.info("Sending streaming chat completion request to: " + url);

        try {
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + config.getNewApiKey())
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "text/event-stream")
                    .post(RequestBody.create(requestBodyJson, JSON_TYPE))
                    .build();

            Response response = httpClient.newCall(request).execute();

            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No body";
                response.close();
                callback.onError(new IOException("AI API stream request failed: "
                        + response.code() + " - " + errorBody));
                return;
            }

            ResponseBody body = response.body();
            if (body == null) {
                response.close();
                callback.onError(new IOException("AI API stream response body is null"));
                return;
            }

            InputStream inputStream = body.byteStream();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8));

            StringBuilder fullResponse = new StringBuilder();

            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    // SSE格式：以"data: "开头的行是数据行
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();

                        // 流结束标记
                        if ("[DONE]".equals(data)) {
                            logger.info("SSE stream completed");
                            break;
                        }

                        try {
                            JsonNode chunk = objectMapper.readTree(data);
                            JsonNode choices = chunk.get("choices");

                            if (choices != null && choices.size() > 0) {
                                JsonNode delta = choices.get(0).get("delta");
                                if (delta != null) {
                                    JsonNode contentNode = delta.get("content");
                                    if (contentNode != null && !contentNode.isNull()) {
                                        String token = contentNode.asText();
                                        fullResponse.append(token);
                                        callback.onToken(token);
                                    }
                                }
                            }
                        } catch (Exception parseEx) {
                            // 忽略无法解析的SSE数据块，继续处理后续数据
                            logger.warning("Failed to parse SSE chunk: " + data + " - " + parseEx.getMessage());
                        }
                    }
                    // 忽略空行、注释行（以":"开头）和其他SSE字段（event:, id:, retry:）
                }
            } finally {
                reader.close();
                response.close();
            }

            String result = fullResponse.toString();
            logger.info("SSE stream total length: " + result.length() + " characters");
            callback.onComplete(result);

        } catch (IOException e) {
            logger.severe("SSE stream error: " + e.getMessage());
            callback.onError(e);
        }
    }

    /**
     * 检查AI服务是否可用
     * <p>
     * 发送一个最简单的请求来验证API连接和认证是否正常。
     *
     * @return true如果AI服务可用，false否则
     */
    public boolean isAvailable() {
        String url = config.getNewApiUrl();
        String key = config.getNewApiKey();

        System.out.println("[AI] 检查AI服务可用性...");
        System.out.println("[AI] URL: " + url);
        System.out.println("[AI] Key长度: " + (key != null ? key.length() : 0));

        if (url == null || url.isEmpty()) {
            System.err.println("[AI] 错误: API URL未配置");
            return false;
        }
        if (key == null || key.isEmpty()) {
            System.err.println("[AI] 错误: API Key未配置");
            return false;
        }

        try {
            String fullUrl = buildApiUrl();
            System.out.println("[AI] 完整请求URL: " + fullUrl);

            // 构建简单的测试消息
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", config.getModel());

            ArrayNode messagesArray = requestBody.putArray("messages");
            ObjectNode msgNode = messagesArray.addObject();
            msgNode.put("role", "user");
            msgNode.put("content", "Hi");

            String requestBodyJson = objectMapper.writeValueAsString(requestBody);
            System.out.println("[AI] 请求体: " + requestBodyJson);

            // 使用较短的超时进行可用性检测
            OkHttpClient checkClient = httpClient.newBuilder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();

            Request request = new Request.Builder()
                    .url(fullUrl)
                    .addHeader("Authorization", "Bearer " + key)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(requestBodyJson, JSON_TYPE))
                    .build();

            System.out.println("[AI] 发送请求...");
            try (Response response = checkClient.newCall(request).execute()) {
                System.out.println("[AI] 响应状态码: " + response.code());

                if (response.isSuccessful()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    System.out.println("[AI] 响应成功: " + responseBody.substring(0, Math.min(100, responseBody.length())));
                    return true;
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    System.err.println("[AI] 错误响应: HTTP " + response.code() + " - " + errorBody);
                    return false;
                }
            }
        } catch (Exception e) {
            System.err.println("[AI] 可用性检查异常: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 关闭HTTP客户端，释放连接池资源
     */
    public void close() {
        logger.info("Closing AI client and connection pool");
        httpClient.connectionPool().evictAll();

        // 取消所有正在执行的请求
        okhttp3.Dispatcher dispatcher = httpClient.dispatcher();
        dispatcher.cancelAll();

        // 等待所有请求完成后再关闭调度器
        try {
            dispatcher.executorService().shutdown();
            if (!dispatcher.executorService().awaitTermination(5, TimeUnit.SECONDS)) {
                dispatcher.executorService().shutdownNow();
            }
        } catch (InterruptedException e) {
            dispatcher.executorService().shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("AI client closed");
    }

    // ===== 私有辅助方法 =====

    /**
     * 验证API配置是否完整
     */
    private void validateConfig() throws IOException {
        if (config.getNewApiUrl() == null || config.getNewApiUrl().isEmpty()) {
            throw new IllegalStateException("NEW API URL is not configured");
        }
        if (config.getNewApiKey() == null || config.getNewApiKey().isEmpty()) {
            throw new IllegalStateException("NEW API Key is not configured");
        }
    }

    /**
     * 构建完整的API URL
     */
    private String buildApiUrl() {
        String url = config.getNewApiUrl();
        // 如果URL已经以 /chat/completions 结尾，直接返回
        if (url.endsWith("/chat/completions")) {
            return url;
        }
        // 如果URL已经以 /v1 结尾或包含 /v1/，直接拼接 chat/completions
        if (url.endsWith("/v1") || url.contains("/v1/")) {
            if (!url.endsWith("/")) {
                url += "/";
            }
            return url + "chat/completions";
        }
        // 默认拼接 v1/chat/completions
        if (!url.endsWith("/")) {
            url += "/";
        }
        return url + "v1/chat/completions";
    }

    /**
     * 构建普通请求的JSON请求体
     */
    private String buildRequestBodyJson(List<ChatMessage> messages) throws IOException {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", config.getModel());

        ArrayNode messagesArray = requestBody.putArray("messages");
        for (ChatMessage msg : messages) {
            ObjectNode msgNode = messagesArray.addObject();
            msgNode.put("role", msg.getRole());
            msgNode.put("content", msg.getContent());
        }

        return objectMapper.writeValueAsString(requestBody);
    }

    /**
     * 构建流式请求的JSON请求体（stream=true）
     */
    private String buildStreamRequestBodyJson(List<ChatMessage> messages) throws IOException {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", config.getModel());
        requestBody.put("stream", true);

        ArrayNode messagesArray = requestBody.putArray("messages");
        for (ChatMessage msg : messages) {
            ObjectNode msgNode = messagesArray.addObject();
            msgNode.put("role", msg.getRole());
            msgNode.put("content", msg.getContent());
        }

        return objectMapper.writeValueAsString(requestBody);
    }

    /**
     * 解析Chat Completion响应体，提取AI回复内容
     */
    private String parseChatResponse(String responseBody) throws IOException {
        JsonNode jsonNode = objectMapper.readTree(responseBody);

        JsonNode choices = jsonNode.get("choices");
        if (choices != null && choices.size() > 0) {
            JsonNode message = choices.get(0).get("message");
            if (message != null) {
                JsonNode contentNode = message.get("content");
                if (contentNode != null && !contentNode.isNull()) {
                    return contentNode.asText();
                }
            }
        }

        // 检查是否有错误信息
        JsonNode errorNode = jsonNode.get("error");
        if (errorNode != null) {
            String errorMsg = errorNode.has("message") ? errorNode.get("message").asText() : errorNode.toString();
            throw new IOException("AI API returned error: " + errorMsg);
        }

        throw new IOException("Unexpected AI API response format: " + responseBody);
    }

    /**
     * 计算指数退避延迟时间
     *
     * @param attempt 当前尝试次数（从1开始）
     * @return 延迟时间（毫秒）
     */
    private long calculateBackoffDelay(int attempt) {
        // 指数退避：2^(attempt-1) * 1000ms，加上随机抖动
        long delay = (long) Math.pow(2, attempt - 1) * INITIAL_RETRY_DELAY_MS;
        // 添加 +/- 20% 的随机抖动，避免多个客户端同时重试
        double jitter = 0.8 + Math.random() * 0.4;
        delay = (long) (delay * jitter);
        return Math.min(delay, MAX_RETRY_DELAY_MS);
    }

    /**
     * 线程安全的休眠
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 获取ObjectMapper实例（供外部使用）
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * 获取Config实例（供外部使用）
     */
    public Config getConfig() {
        return config;
    }

    /**
     * 聊天消息数据类
     */
    public static class ChatMessage {
        private final String role;
        private final String content;

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() { return role; }
        public String getContent() { return content; }
    }

    /**
     * 流式回调接口
     */
    public interface StreamCallback {
        /**
         * 接收到一个token时回调
         *
         * @param token 当前接收到的文本片段
         */
        void onToken(String token);

        /**
         * 流式响应完成时回调
         *
         * @param fullResponse 完整的响应内容
         */
        void onComplete(String fullResponse);

        /**
         * 发生错误时回调
         *
         * @param e 异常信息
         */
        void onError(Exception e);
    }
}
