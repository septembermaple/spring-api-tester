package com.autotest.license;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 试用期管理类
 * <p>
 * 管理3天试用期，记录首次启动时间（加密存储在用户目录下的隐藏文件中），
 * 计算剩余天数，显示试用提示。
 */
public class TrialManager {

    // 试用期天数
    private static final int TRIAL_DAYS = 3;
    // 试用期信息文件名（隐藏文件）
    private static final String TRIAL_FILE_NAME = ".spring-api-tester.trial";
    // 软件ID，用于加密
    private final String softwareId;

    // 首次启动时间（毫秒时间戳）
    private long firstStartTime = 0;
    // 是否已加载试用信息
    private boolean loaded = false;

    public TrialManager(String softwareId) {
        this.softwareId = softwareId;
        loadTrialInfo();
    }

    /**
     * 获取试用信息文件路径
     *
     * @return 试用信息文件路径
     */
    private Path getTrialFilePath() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, TRIAL_FILE_NAME);
    }

    /**
     * 加载试用信息
     */
    private void loadTrialInfo() {
        Path trialFile = getTrialFilePath();
        if (Files.exists(trialFile)) {
            try (FileInputStream fis = new FileInputStream(trialFile.toFile())) {
                byte[] encryptedData = new byte[(int) Files.size(trialFile)];
                fis.read(encryptedData);
                String encrypted = new String(encryptedData, StandardCharsets.UTF_8);
                String decrypted = EncryptionUtil.decrypt(encrypted);
                firstStartTime = Long.parseLong(decrypted.trim());
                loaded = true;
            } catch (Exception e) {
                // 如果解密失败（可能是软件ID变更），重置试用期
                System.err.println("[Trial] 试用信息加载失败，重置试用期: " + e.getMessage());
                firstStartTime = 0;
                loaded = false;
            }
        }
    }

    /**
     * 保存试用信息
     */
    private void saveTrialInfo() {
        Path trialFile = getTrialFilePath();
        try {
            String data = String.valueOf(firstStartTime);
            String encrypted = EncryptionUtil.encrypt(data);
            try (FileOutputStream fos = new FileOutputStream(trialFile.toFile())) {
                fos.write(encrypted.getBytes(StandardCharsets.UTF_8));
            }
            // 设置为隐藏文件（Windows）
            File file = trialFile.toFile();
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                try {
                    ProcessBuilder pb = new ProcessBuilder("attrib", "+H", file.getAbsolutePath());
                    pb.start();
                } catch (Exception e) {
                    // 忽略设置隐藏属性失败
                }
            }
        } catch (Exception e) {
            System.err.println("[Trial] 试用信息保存失败: " + e.getMessage());
        }
    }

    /**
     * 初始化试用期（首次启动时调用）
     */
    public void initTrial() {
        if (firstStartTime == 0) {
            firstStartTime = System.currentTimeMillis();
            saveTrialInfo();
            loaded = true;
        }
    }

    /**
     * 检查试用期是否有效
     *
     * @return 试用期是否有效
     */
    public boolean isTrialValid() {
        // 如果没有试用信息，说明是首次启动，试用期有效
        if (firstStartTime == 0) {
            return true;
        }

        long now = System.currentTimeMillis();
        long trialEndTime = firstStartTime + (TRIAL_DAYS * 24L * 60 * 60 * 1000);
        return now < trialEndTime;
    }

    /**
     * 获取剩余试用天数
     *
     * @return 剩余试用天数（0表示已过期）
     */
    public int getRemainingDays() {
        if (firstStartTime == 0) {
            return TRIAL_DAYS;
        }

        long now = System.currentTimeMillis();
        long trialEndTime = firstStartTime + (TRIAL_DAYS * 24L * 60 * 60 * 1000);

        if (now >= trialEndTime) {
            return 0;
        }

        long remainingMillis = trialEndTime - now;
        return (int) (remainingMillis / (24L * 60 * 60 * 1000)) + 1;
    }

    /**
     * 获取剩余小时数（用于更精确的显示）
     *
     * @return 剩余小时数
     */
    public long getRemainingHours() {
        if (firstStartTime == 0) {
            return TRIAL_DAYS * 24L;
        }

        long now = System.currentTimeMillis();
        long trialEndTime = firstStartTime + (TRIAL_DAYS * 24L * 60 * 60 * 1000);

        if (now >= trialEndTime) {
            return 0;
        }

        long remainingMillis = trialEndTime - now;
        return remainingMillis / (60 * 60 * 1000);
    }

    /**
     * 获取试用结束日期字符串
     *
     * @return 试用结束日期
     */
    public String getTrialEndDate() {
        long trialEndTime;
        if (firstStartTime == 0) {
            trialEndTime = System.currentTimeMillis() + (TRIAL_DAYS * 24L * 60 * 60 * 1000);
        } else {
            trialEndTime = firstStartTime + (TRIAL_DAYS * 24L * 60 * 60 * 1000);
        }

        Instant instant = Instant.ofEpochMilli(trialEndTime);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault());
        return formatter.format(instant);
    }

    /**
     * 获取试用状态描述
     *
     * @return 试用状态描述
     */
    public String getTrialStatusText() {
        if (firstStartTime == 0) {
            return "试用期未开始（" + TRIAL_DAYS + "天）";
        }

        int remainingDays = getRemainingDays();
        if (remainingDays > 0) {
            return "试用期剩余 " + remainingDays + " 天";
        } else {
            return "试用期已过期";
        }
    }

    /**
     * 是否是首次启动
     *
     * @return 是否首次启动
     */
    public boolean isFirstStart() {
        return firstStartTime == 0;
    }

    /**
     * 获取总试用期天数
     *
     * @return 总试用期天数
     */
    public int getTotalTrialDays() {
        return TRIAL_DAYS;
    }

    /**
     * 清除试用信息（用于测试）
     */
    public void clearTrialInfo() {
        Path trialFile = getTrialFilePath();
        try {
            Files.deleteIfExists(trialFile);
            firstStartTime = 0;
            loaded = false;
        } catch (Exception e) {
            System.err.println("[Trial] 清除试用信息失败: " + e.getMessage());
        }
    }
}
