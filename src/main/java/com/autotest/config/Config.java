package com.autotest.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

/**
 * 配置类 - 管理所有运行时配置
 * <p>
 * 支持从命令行参数和 application.properties 加载配置。
 * 命令行参数优先级高于配置文件。
 */
public class Config {

    // AI API配置
    private String newApiUrl;
    private String newApiKey;
    private String model;

    // Git配置
    private String gitPath;
    private String branch;
    private String since;
    private String author;  // 提交用户筛选

    // 测试目标配置
    private String baseUrl;
    private int timeout;
    private int threads;

    // 输出配置
    private String outputDir;
    private boolean autoFix;
    private boolean perfTest;

    // 性能测试配置
    private int perfThreads = 50;
    private int perfDuration = 60;
    private int perfRampUp = 10;

    // 认证配置
    private String authToken;

    // 接口过滤配置
    private String excludePrefixes;  // 排除的接口前缀，逗号分隔
    private String includeOnly;      // 只测试的接口

    // 测试用例生成配置
    private String testCasePromptTypes = "normal,validation,boundary,error,security";  // 选中的提示词类型（逗号分隔）
    private int testCaseCount = 8;  // 用例数量，默认8
    private String customTestCaseFile = "custom_test_cases.json";  // 自定义用例文件路径
    private String testCaseConfigFile = "testcase_config.properties";  // 测试用例配置文件路径

    // 许可证服务器配置
    private String licenseServerUrl = "http://localhost:8088";
    private String softwareId = "";

    public Config() {
    }

    /**
     * 创建默认Config实例
     * <p>
     * 创建一个新的Config实例，配置值将从application.properties加载。
     *
     * @return 配置好的Config实例
     */
    public static Config createDefault() {
        Config config = new Config();
        config.loadProperties();
        return config;
    }

    /**
     * 从application.properties加载默认配置
     * <p>
     * 仅在CLI参数未设置时使用配置文件的值。
     */
    public void loadProperties() {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException e) {
            System.err.println("Warning: Failed to load application.properties: " + e.getMessage());
        }

        // 仅在CLI参数未设置时使用配置文件的值
        if (newApiUrl == null || newApiUrl.isEmpty()) {
            newApiUrl = props.getProperty("new.api.url", "");
        }
        if (newApiKey == null || newApiKey.isEmpty()) {
            newApiKey = props.getProperty("new.api.key", "");
        }
        if (model == null || model.isEmpty()) {
            model = props.getProperty("new.api.model", "gpt-4");
        }
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = props.getProperty("test.base.url", "http://localhost:8080");
        }
        if (gitPath == null || gitPath.isEmpty()) {
            gitPath = props.getProperty("git.path", ".");
        }
        if (branch == null || branch.isEmpty()) {
            branch = props.getProperty("git.branch", "main");
        }
        if (outputDir == null || outputDir.isEmpty()) {
            outputDir = props.getProperty("output.dir", "./reports");
        }

        // 数值类型配置：仅使用配置文件的值作为默认值
        if (timeout <= 0) {
            timeout = Integer.parseInt(props.getProperty("http.timeout", "30"));
        }
        if (threads <= 0) {
            threads = Integer.parseInt(props.getProperty("test.threads", "5"));
        }
        if (perfThreads <= 0) {
            perfThreads = Integer.parseInt(props.getProperty("perf.threads", "50"));
        }
        if (perfDuration <= 0) {
            perfDuration = Integer.parseInt(props.getProperty("perf.duration", "60"));
        }
        if (perfRampUp <= 0) {
            perfRampUp = Integer.parseInt(props.getProperty("perf.rampup", "10"));
        }
        if (authToken == null || authToken.isEmpty()) {
            authToken = props.getProperty("auth.token", "");
        }
        if (author == null || author.isEmpty()) {
            author = props.getProperty("git.author", "");
        }
        if (licenseServerUrl == null || licenseServerUrl.isEmpty()) {
            licenseServerUrl = props.getProperty("license.server.url", "http://localhost:8088");
        }
        if (softwareId == null || softwareId.isEmpty()) {
            softwareId = props.getProperty("license.software.id", "spring-api-tester");
        }
    }

    /**
     * 验证必要配置是否完整
     *
     * @return 配置是否有效
     */
    public boolean validate() {
        boolean valid = true;

        // 验证baseUrl
        if (baseUrl == null || baseUrl.isEmpty()) {
            System.err.println("Error: base-url is required");
            valid = false;
        } else if (!isValidUrl(baseUrl)) {
            System.err.println("Error: base-url format is invalid: " + baseUrl
                    + " (expected format: http://host:port or https://host:port)");
            valid = false;
        }

        // 验证newApiUrl（如果设置了的话）
        if (newApiUrl != null && !newApiUrl.isEmpty()) {
            if (!isValidUrl(newApiUrl)) {
                System.err.println("Error: new-api-url format is invalid: " + newApiUrl
                        + " (expected format: http://host or https://host)");
                valid = false;
            }
        }

        // 验证timeout
        if (timeout <= 0) {
            System.err.println("Error: timeout must be a positive number, got: " + timeout);
            valid = false;
        }

        // 验证threads
        if (threads <= 0) {
            System.err.println("Error: threads must be a positive number, got: " + threads);
            valid = false;
        }

        // 验证性能测试配置
        if (perfTest) {
            if (perfThreads <= 0) {
                System.err.println("Error: perf-threads must be a positive number, got: " + perfThreads);
                valid = false;
            }
            if (perfDuration <= 0) {
                System.err.println("Error: perf-duration must be a positive number, got: " + perfDuration);
                valid = false;
            }
            if (perfRampUp < 0) {
                System.err.println("Error: perf-rampup must be a non-negative number, got: " + perfRampUp);
                valid = false;
            }
            if (perfRampUp >= perfDuration) {
                System.err.println("Warning: perf-rampup (" + perfRampUp
                        + "s) should be less than perf-duration (" + perfDuration + "s)");
            }
        }

        // 验证autoFix需要AI配置
        if (autoFix && (newApiUrl == null || newApiUrl.isEmpty())) {
            System.err.println("Warning: auto-fix mode enabled but new-api-url is not configured. "
                    + "Fix analysis will use basic fallback mode.");
        }

        return valid;
    }

    /**
     * 检查URL格式是否有效
     *
     * @param urlString URL字符串
     * @return 是否是有效的URL
     */
    private boolean isValidUrl(String urlString) {
        if (urlString == null || urlString.isEmpty()) {
            return false;
        }
        try {
            URL url = new URL(urlString);
            String protocol = url.getProtocol();
            if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
                return false;
            }
            String host = url.getHost();
            if (host == null || host.isEmpty()) {
                return false;
            }
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    // ===== Getters and Setters =====

    public String getNewApiUrl() { return newApiUrl; }
    public void setNewApiUrl(String newApiUrl) { this.newApiUrl = newApiUrl; }

    public String getNewApiKey() { return newApiKey; }
    public void setNewApiKey(String newApiKey) { this.newApiKey = newApiKey; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getGitPath() { return gitPath; }
    public void setGitPath(String gitPath) { this.gitPath = gitPath; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public String getSince() { return since; }
    public void setSince(String since) { this.since = since; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public int getTimeout() { return timeout; }
    public void setTimeout(int timeout) { this.timeout = timeout; }

    public int getThreads() { return threads; }
    public void setThreads(int threads) { this.threads = threads; }

    public String getOutputDir() { return outputDir; }
    public void setOutputDir(String outputDir) { this.outputDir = outputDir; }

    public boolean isAutoFix() { return autoFix; }
    public void setAutoFix(boolean autoFix) { this.autoFix = autoFix; }

    public boolean isPerfTest() { return perfTest; }
    public void setPerfTest(boolean perfTest) { this.perfTest = perfTest; }

    public int getPerfThreads() { return perfThreads; }
    public void setPerfThreads(int perfThreads) { this.perfThreads = perfThreads; }

    public int getPerfDuration() { return perfDuration; }
    public void setPerfDuration(int perfDuration) { this.perfDuration = perfDuration; }

    public int getPerfRampUp() { return perfRampUp; }
    public void setPerfRampUp(int perfRampUp) { this.perfRampUp = perfRampUp; }

    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }

    public String getExcludePrefixes() { return excludePrefixes; }
    public void setExcludePrefixes(String excludePrefixes) { this.excludePrefixes = excludePrefixes; }

    public String getIncludeOnly() { return includeOnly; }
    public void setIncludeOnly(String includeOnly) { this.includeOnly = includeOnly; }

    // 测试用例生成配置 Getter/Setter
    public String getTestCasePromptTypes() { return testCasePromptTypes; }
    public void setTestCasePromptTypes(String testCasePromptTypes) { this.testCasePromptTypes = testCasePromptTypes; }

    public int getTestCaseCount() { return testCaseCount; }
    public void setTestCaseCount(int testCaseCount) { this.testCaseCount = testCaseCount; }

    public String getCustomTestCaseFile() { return customTestCaseFile; }
    public void setCustomTestCaseFile(String customTestCaseFile) { this.customTestCaseFile = customTestCaseFile; }

    public String getTestCaseConfigFile() { return testCaseConfigFile; }
    public void setTestCaseConfigFile(String testCaseConfigFile) { this.testCaseConfigFile = testCaseConfigFile; }

    // 许可证服务器配置 Getter/Setter
    public String getLicenseServerUrl() { return licenseServerUrl; }
    public void setLicenseServerUrl(String licenseServerUrl) { this.licenseServerUrl = licenseServerUrl; }

    public String getSoftwareId() { return softwareId; }
    public void setSoftwareId(String softwareId) { this.softwareId = softwareId; }

    @Override
    public String toString() {
        return "Config{" +
                "baseUrl='" + baseUrl + '\'' +
                ", newApiUrl='" + newApiUrl + '\'' +
                ", model='" + model + '\'' +
                ", gitPath='" + gitPath + '\'' +
                ", branch='" + branch + '\'' +
                ", since='" + since + '\'' +
                ", author='" + author + '\'' +
                ", outputDir='" + outputDir + '\'' +
                ", autoFix=" + autoFix +
                ", perfTest=" + perfTest +
                ", timeout=" + timeout +
                ", threads=" + threads +
                ", perfThreads=" + perfThreads +
                ", perfDuration=" + perfDuration +
                ", perfRampUp=" + perfRampUp +
                '}';
    }
}
