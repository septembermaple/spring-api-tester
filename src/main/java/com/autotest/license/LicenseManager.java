package com.autotest.license;

/**
 * 激活管理器（核心类）
 * <p>
 * 功能：
 * - 检查软件是否已激活
 * - 检查试用期是否过期
 * - 调用服务器验证激活码
 * - 管理激活状态（不存储在本地文件/注册表）
 * - 启动时自动验证
 * <p>
 * 实现要求：
 * - 使用内存存储激活状态（程序关闭后失效，下次启动重新验证）
 * - 试用期3天，从首次启动开始计算
 * - 试用期信息存储在加密文件中（不是注册表）
 * - 提供 isActivated() 和 isTrialValid() 方法
 */
public class LicenseManager {

    // 服务器URL
    private static final String DEFAULT_SERVER_URL = "http://localhost:8088";
    // 软件ID
    private static final String DEFAULT_SOFTWARE_ID = "spring-api-tester";

    // 激活状态（仅内存存储，程序关闭后失效）
    private volatile boolean activated = false;
    // 当前激活码（仅内存存储）
    private volatile String currentLicenseKey = null;

    // 机器码
    private final String machineCode;
    // 试用期管理器
    private final TrialManager trialManager;
    // 许可证客户端
    private final LicenseClient licenseClient;
    // 软件ID
    private final String softwareId;

    public LicenseManager() {
        this(DEFAULT_SERVER_URL, DEFAULT_SOFTWARE_ID);
    }

    public LicenseManager(String serverUrl, String softwareId) {
        this.softwareId = softwareId;
        this.machineCode = MachineCodeUtil.generateMachineCode();
        this.trialManager = new TrialManager(softwareId);
        this.licenseClient = new LicenseClient(serverUrl, softwareId);

        // 启动时自动验证
        autoVerify();
    }

    /**
     * 启动时自动验证
     * <p>
     * 尝试从内存中恢复激活状态（实际上内存中不会有，因为每次启动都是新的）。
     * 初始化试用期信息。
     */
    private void autoVerify() {
        // 初始化试用期（如果是首次启动）
        trialManager.initTrial();

        // 注意：激活状态不存储在本地，每次启动都需要重新激活或依赖试用期
        // 如果服务器可用，可以尝试验证之前的激活码（但内存中没有存储）
        // 所以这里不做任何激活状态的恢复

        System.out.println("[License] 机器码: " + MachineCodeUtil.getShortMachineCode());
        System.out.println("[License] 试用期状态: " + trialManager.getTrialStatusText());
    }

    /**
     * 检查软件是否已激活
     *
     * @return 是否已激活
     */
    public boolean isActivated() {
        return activated;
    }

    /**
     * 检查试用期是否有效
     *
     * @return 试用期是否有效
     */
    public boolean isTrialValid() {
        return trialManager.isTrialValid();
    }

    /**
     * 获取试用期剩余天数
     *
     * @return 剩余天数
     */
    public int getTrialRemainingDays() {
        return trialManager.getRemainingDays();
    }

    /**
     * 获取试用期状态文本
     *
     * @return 试用期状态文本
     */
    public String getTrialStatusText() {
        return trialManager.getTrialStatusText();
    }

    /**
     * 验证激活码
     * <p>
     * 调用服务器验证激活码，如果验证成功则设置激活状态。
     *
     * @param licenseKey 激活码
     * @return 验证结果
     */
    public LicenseVerificationResult verifyLicense(String licenseKey) {
        if (licenseKey == null || licenseKey.trim().isEmpty()) {
            return LicenseVerificationResult.error("激活码不能为空");
        }

        // 先检查激活码格式
        licenseKey = licenseKey.trim();

        // 调用服务器验证
        LicenseClient.LicenseResponse response = licenseClient.verifyLicense(machineCode, licenseKey);

        if (response.isSuccess()) {
            // 验证成功，设置内存激活状态
            activated = true;
            currentLicenseKey = licenseKey;
            return LicenseVerificationResult.success(response.getMessage());
        } else {
            // 验证失败
            activated = false;
            currentLicenseKey = null;
            return LicenseVerificationResult.error(response.getMessage());
        }
    }

    /**
     * 检查激活码是否有效（不绑定机器）
     *
     * @param licenseKey 激活码
     * @return 检查结果
     */
    public LicenseVerificationResult checkLicense(String licenseKey) {
        if (licenseKey == null || licenseKey.trim().isEmpty()) {
            return LicenseVerificationResult.error("激活码不能为空");
        }

        LicenseClient.LicenseResponse response = licenseClient.checkLicense(licenseKey.trim());

        if (response.isSuccess()) {
            return LicenseVerificationResult.success(response.getMessage());
        } else {
            return LicenseVerificationResult.error(response.getMessage());
        }
    }

    /**
     * 获取机器码
     *
     * @return 机器码
     */
    public String getMachineCode() {
        return MachineCodeUtil.getShortMachineCode();
    }

    /**
     * 获取原始机器码（用于服务器验证）
     *
     * @return 原始机器码
     */
    public String getRawMachineCode() {
        return machineCode;
    }

    /**
     * 获取软件ID
     *
     * @return 软件ID
     */
    public String getSoftwareId() {
        return softwareId;
    }

    /**
     * 获取当前激活码（仅当已激活时有效）
     *
     * @return 当前激活码
     */
    public String getCurrentLicenseKey() {
        return currentLicenseKey;
    }

    /**
     * 获取试用期管理器
     *
     * @return 试用期管理器
     */
    public TrialManager getTrialManager() {
        return trialManager;
    }

    /**
     * 检查服务器是否可用
     *
     * @return 服务器是否可用
     */
    public boolean isServerAvailable() {
        return licenseClient.isServerAvailable();
    }

    /**
     * 获取许可证客户端
     *
     * @return 许可证客户端
     */
    public LicenseClient getLicenseClient() {
        return licenseClient;
    }

    /**
     * 激活验证结果
     */
    public static class LicenseVerificationResult {
        private final boolean success;
        private final String message;

        private LicenseVerificationResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static LicenseVerificationResult success(String message) {
            return new LicenseVerificationResult(true, message);
        }

        public static LicenseVerificationResult error(String message) {
            return new LicenseVerificationResult(false, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }
}
