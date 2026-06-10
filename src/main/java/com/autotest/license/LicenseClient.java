package com.autotest.license;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 许可证服务器通信客户端
 * <p>
 * 负责与许可证服务器进行HTTP通信，
 * 加密请求体，解密响应，处理网络异常。
 */
public class LicenseClient {

    private final String serverUrl;
    private final String softwareId;
    private final ObjectMapper objectMapper;

    // 连接超时时间（毫秒）
    private static final int CONNECT_TIMEOUT = 10000;
    // 读取超时时间（毫秒）
    private static final int READ_TIMEOUT = 15000;

    public LicenseClient(String serverUrl, String softwareId) {
        this.serverUrl = serverUrl;
        this.softwareId = softwareId;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 验证激活码
     *
     * @param machineCode 机器码
     * @param licenseKey  激活码
     * @return 验证结果
     */
    public LicenseResponse verifyLicense(String machineCode, String licenseKey) {
        try {
            // 构建请求体
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("machineCode", machineCode);
            requestBody.put("licenseKey", licenseKey);
            requestBody.put("softwareId", softwareId);

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            // 加密请求体
            String encryptedBody = EncryptionUtil.encrypt(jsonBody, softwareId);

            // 发送请求
            String response = sendPostRequest("/api/license/verify", encryptedBody);

            if (response == null || response.isEmpty()) {
                return LicenseResponse.error("服务器无响应");
            }

            // 解密响应
            String decryptedResponse = EncryptionUtil.decrypt(response, softwareId);

            // 解析响应
            JsonNode jsonNode = objectMapper.readTree(decryptedResponse);
            boolean success = jsonNode.has("success") && jsonNode.get("success").asBoolean();
            String message = jsonNode.has("message") ? jsonNode.get("message").asText() : "";

            if (success) {
                return LicenseResponse.success(message);
            } else {
                return LicenseResponse.error(message);
            }

        } catch (Exception e) {
            return LicenseResponse.error("验证失败: " + e.getMessage());
        }
    }

    /**
     * 检查激活码是否有效（不绑定机器）
     *
     * @param licenseKey 激活码
     * @return 验证结果
     */
    public LicenseResponse checkLicense(String licenseKey) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("licenseKey", licenseKey);
            requestBody.put("softwareId", softwareId);

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            String encryptedBody = EncryptionUtil.encrypt(jsonBody, softwareId);

            String response = sendPostRequest("/api/license/check", encryptedBody);

            if (response == null || response.isEmpty()) {
                return LicenseResponse.error("服务器无响应");
            }

            String decryptedResponse = EncryptionUtil.decrypt(response, softwareId);
            JsonNode jsonNode = objectMapper.readTree(decryptedResponse);
            boolean success = jsonNode.has("success") && jsonNode.get("success").asBoolean();
            String message = jsonNode.has("message") ? jsonNode.get("message").asText() : "";

            if (success) {
                return LicenseResponse.success(message);
            } else {
                return LicenseResponse.error(message);
            }

        } catch (Exception e) {
            return LicenseResponse.error("检查失败: " + e.getMessage());
        }
    }

    /**
     * 发送POST请求
     *
     * @param endpoint    API端点
     * @param requestBody 请求体（已加密）
     * @return 响应字符串（已加密）
     */
    private String sendPostRequest(String endpoint, String requestBody) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(serverUrl + endpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/octet-stream");
            connection.setRequestProperty("X-Software-Id", softwareId);
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setDoOutput(true);
            connection.setDoInput(true);

            // 发送请求体
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // 读取响应
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                }
                return response.toString();
            } else {
                // 尝试读取错误响应
                StringBuilder errorResponse = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        errorResponse.append(line);
                    }
                }
                return errorResponse.toString();
            }

        } catch (java.net.ConnectException e) {
            System.err.println("[License] 无法连接到许可证服务器: " + e.getMessage());
            return null;
        } catch (java.net.SocketTimeoutException e) {
            System.err.println("[License] 连接许可证服务器超时: " + e.getMessage());
            return null;
        } catch (Exception e) {
            System.err.println("[License] 请求异常: " + e.getMessage());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * 测试服务器连接
     *
     * @return 是否可连接
     */
    public boolean isServerAvailable() {
        try {
            URL url = new URL(serverUrl + "/api/license/ping");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 许可证响应结果
     */
    public static class LicenseResponse {
        private final boolean success;
        private final String message;

        private LicenseResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static LicenseResponse success(String message) {
            return new LicenseResponse(true, message);
        }

        public static LicenseResponse error(String message) {
            return new LicenseResponse(false, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }
}
