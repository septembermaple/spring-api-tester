package com.autotest.license;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;

/**
 * 机器码生成工具类
 * <p>
 * 获取CPU序列号、主板序列号、硬盘序列号、MAC地址，
 * 使用SHA-256生成唯一机器码。
 * 与服务器端 MachineCodeUtil 兼容。
 */
public class MachineCodeUtil {

    private static final String ALGORITHM = "SHA-256";

    /**
     * 生成机器码
     * <p>
     * 综合CPU序列号、主板序列号、硬盘序列号、MAC地址生成唯一机器码。
     * 如果某些信息无法获取，则使用可用的信息生成。
     *
     * @return 32位十六进制字符串形式的机器码
     */
    public static String generateMachineCode() {
        StringBuilder machineInfo = new StringBuilder();

        // 获取CPU序列号
        String cpuSerial = getCpuSerial();
        machineInfo.append("CPU:").append(cpuSerial).append(";");

        // 获取主板序列号
        String motherboardSerial = getMotherboardSerial();
        machineInfo.append("MB:").append(motherboardSerial).append(";");

        // 获取硬盘序列号
        String diskSerial = getDiskSerial();
        machineInfo.append("DISK:").append(diskSerial).append(";");

        // 获取MAC地址
        String macAddress = getMacAddress();
        machineInfo.append("MAC:").append(macAddress).append(";");

        // 使用SHA-256生成机器码
        return sha256(machineInfo.toString());
    }

    /**
     * 获取CPU序列号
     *
     * @return CPU序列号
     */
    public static String getCpuSerial() {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) {
                // Windows系统使用wmic命令
                String result = executeCommand("wmic", "cpu", "get", "processorid");
                return extractValue(result, "ProcessorId");
            } else if (os.contains("linux")) {
                // Linux系统读取/proc/cpuinfo
                String result = executeCommand("cat", "/proc/cpuinfo");
                String[] lines = result.split("\n");
                for (String line : lines) {
                    if (line.startsWith("serial") || line.startsWith("Serial")) {
                        return line.split(":")[1].trim();
                    }
                }
                // 尝试获取CPU型号作为备选
                for (String line : lines) {
                    if (line.startsWith("model name") || line.startsWith("Model name")) {
                        return line.split(":")[1].trim();
                    }
                }
            } else if (os.contains("mac")) {
                // macOS使用sysctl命令
                String result = executeCommand("sysctl", "-n", "machdep.cpu.brand_string");
                return result.trim();
            }
        } catch (Exception e) {
            // 忽略错误，使用备选方案
        }
        // 备选：使用系统属性
        return System.getProperty("os.name") + "_" + System.getProperty("os.arch") + "_" + Runtime.getRuntime().availableProcessors();
    }

    /**
     * 获取主板序列号
     *
     * @return 主板序列号
     */
    public static String getMotherboardSerial() {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) {
                String result = executeCommand("wmic", "baseboard", "get", "serialnumber");
                return extractValue(result, "SerialNumber");
            } else if (os.contains("linux")) {
                String result = executeCommand("cat", "/sys/class/dmi/id/board_serial");
                if (result != null && !result.trim().isEmpty()) {
                    return result.trim();
                }
                // 备选：使用product_uuid
                result = executeCommand("cat", "/sys/class/dmi/id/product_uuid");
                if (result != null && !result.trim().isEmpty()) {
                    return result.trim();
                }
            } else if (os.contains("mac")) {
                String result = executeCommand("system_profiler", "SPHardwareDataType");
                String[] lines = result.split("\n");
                for (String line : lines) {
                    if (line.contains("Serial Number")) {
                        return line.split(":")[1].trim();
                    }
                }
            }
        } catch (Exception e) {
            // 忽略错误
        }
        return "UNKNOWN_MB";
    }

    /**
     * 获取硬盘序列号
     *
     * @return 硬盘序列号
     */
    public static String getDiskSerial() {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) {
                String result = executeCommand("wmic", "diskdrive", "get", "serialnumber");
                return extractValue(result, "SerialNumber");
            } else if (os.contains("linux")) {
                // 尝试获取第一个磁盘的序列号
                String result = executeCommand("lsblk", "-o", "NAME,SERIAL");
                String[] lines = result.split("\n");
                for (int i = 1; i < lines.length; i++) {
                    String[] parts = lines[i].trim().split("\\s+");
                    if (parts.length >= 2 && !parts[1].trim().isEmpty()) {
                        return parts[1].trim();
                    }
                }
                // 备选：使用blkid
                result = executeCommand("blkid");
                if (result != null && !result.isEmpty()) {
                    return sha256(result.substring(0, Math.min(200, result.length())));
                }
            } else if (os.contains("mac")) {
                String result = executeCommand("diskutil", "info", "disk0");
                String[] lines = result.split("\n");
                for (String line : lines) {
                    if (line.contains("Device Identifier")) {
                        return line.split(":")[1].trim();
                    }
                }
            }
        } catch (Exception e) {
            // 忽略错误
        }
        return "UNKNOWN_DISK";
    }

    /**
     * 获取MAC地址
     *
     * @return MAC地址
     */
    public static String getMacAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) {
                    continue;
                }
                byte[] mac = ni.getHardwareAddress();
                if (mac != null) {
                    StringBuilder sb = new StringBuilder();
                    for (byte b : mac) {
                        sb.append(String.format("%02X", b));
                    }
                    return sb.toString();
                }
            }
        } catch (Exception e) {
            // 忽略错误
        }
        return "UNKNOWN_MAC";
    }

    /**
     * 执行系统命令
     *
     * @param command 命令及参数
     * @return 命令输出
     */
    private static String executeCommand(String... command) {
        StringBuilder output = new StringBuilder();
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            process.waitFor();
        } catch (Exception e) {
            return "";
        }
        return output.toString();
    }

    /**
     * 从命令输出中提取值（去除标题行）
     *
     * @param output    命令输出
     * @param headerTitle 标题行文本
     * @return 提取的值
     */
    private static String extractValue(String output, String headerTitle) {
        if (output == null || output.isEmpty()) {
            return "UNKNOWN";
        }
        String[] lines = output.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.equalsIgnoreCase(headerTitle)) {
                continue;
            }
            return line;
        }
        return "UNKNOWN";
    }

    /**
     * 计算SHA-256哈希值
     *
     * @param input 输入字符串
     * @return 32位十六进制字符串
     */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * 获取简化的机器标识（用于显示）
     *
     * @return 简化的机器标识
     */
    public static String getShortMachineCode() {
        String fullCode = generateMachineCode();
        return fullCode.substring(0, 8).toUpperCase() + "-" +
               fullCode.substring(8, 16).toUpperCase() + "-" +
               fullCode.substring(16, 24).toUpperCase() + "-" +
               fullCode.substring(24, 32).toUpperCase();
    }
}
