package com.autotest.executor;

import com.autotest.config.Config;
import com.autotest.parser.EndpointDefinition;
import okhttp3.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * 性能测试执行器 - 使用OkHttp实现，与功能测试保持一致
 */
public class PerformanceTester {

    private final Config config;
    private final OkHttpClient httpClient;
    private PerformanceSummary performanceSummary;
    
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public PerformanceTester(Config config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(config.getTimeout(), TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 运行性能测试
     */
    public void runPerformanceTests(List<EndpointDefinition> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            System.out.println("[Performance] No endpoints to performance test.");
            return;
        }

        System.out.println("[Performance] Starting performance test for " + endpoints.size() + " endpoints...");
        System.out.println("  Threads: " + config.getPerfThreads());
        System.out.println("  Duration: " + config.getPerfDuration() + "s");

        // 收集所有响应时间
        List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        
        EndpointDefinition endpoint = endpoints.get(0); // 简单起见，测试第一个接口
        String fullUrl = config.getBaseUrl() + endpoint.getUrlPath();
        
        System.out.println("  Testing: " + endpoint.getHttpMethod() + " " + fullUrl);

        long startTime = System.currentTimeMillis();
        long endTime = startTime + (config.getPerfDuration() * 1000L);
        ExecutorService executor = Executors.newFixedThreadPool(config.getPerfThreads());
        
        // 使用CountDownLatch等待所有线程完成
        CountDownLatch latch = new CountDownLatch(config.getPerfThreads());
        
        for (int i = 0; i < config.getPerfThreads(); i++) {
            final int threadNum = i + 1;
            executor.submit(() -> {
                try {
                    while (System.currentTimeMillis() < endTime) {
                        long requestStart = System.currentTimeMillis();
                        try {
                            Response response = executeRequest(fullUrl, endpoint.getHttpMethod(), "{}");
                            int statusCode = response.code();
                            long requestTime = System.currentTimeMillis() - requestStart;
                            
                            responseTimes.add(requestTime);
                            
                            if (statusCode >= 200 && statusCode < 400) {
                                successCount.incrementAndGet();
                            } else {
                                failCount.incrementAndGet();
                            }
                            
                            response.close();
                        } catch (Exception e) {
                            failCount.incrementAndGet();
                            System.err.println("[Performance] Request error: " + e.getMessage());
                        }
                        
                        // 添加一个小延迟避免过于频繁
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 等待所有线程完成
        try {
            latch.await(config.getPerfDuration() + 30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        executor.shutdown();
        
        long actualDuration = (System.currentTimeMillis() - startTime) / 1000;
        System.out.println("[Performance] Test completed in " + actualDuration + "s");

        // 计算性能统计
        try {
            performanceSummary = calculateSummary(responseTimes, successCount.get(), failCount.get(), actualDuration);
        } catch (Exception e) {
            System.err.println("[Performance] Error calculating summary: " + e.getMessage());
            e.printStackTrace();
            // 使用默认值
            performanceSummary = new PerformanceSummary(responseTimes.size(), successCount.get(), failCount.get(), 
                    0, 0, 0, 0, 0, 0, 0, 0);
        }
        
        // 保存结果到CSV
        try {
            saveResultsToCsv(responseTimes, successCount.get(), failCount.get());
        } catch (Exception e) {
            System.err.println("[Performance] Error saving CSV: " + e.getMessage());
        }
    }
    
    /**
     * 执行HTTP请求
     */
    private Response executeRequest(String url, String method, String requestBody) throws Exception {
        Request.Builder requestBuilder = new Request.Builder().url(url);
        
        // 添加Content-Type
        requestBuilder.addHeader("Content-Type", "application/json");
        requestBuilder.addHeader("Accept", "application/json");
        
        // 添加Authorization头
        String authToken = config.getAuthToken();
        if (authToken != null && !authToken.isEmpty()) {
            // 去除可能已有的Bearer前缀
            if (authToken.startsWith("Bearer ")) {
                authToken = authToken.substring(7);
            }
            requestBuilder.addHeader("Authorization", "Bearer " + authToken);
        }
        
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) {
            String body = requestBody != null && !requestBody.isEmpty() ? requestBody : "{}";
            requestBuilder.method(method, RequestBody.create(body, JSON));
        } else if ("DELETE".equalsIgnoreCase(method)) {
            requestBuilder.delete();
        } else {
            requestBuilder.get();
        }
        
        return httpClient.newCall(requestBuilder.build()).execute();
    }
    
    /**
     * 计算性能统计
     */
    private PerformanceSummary calculateSummary(List<Long> responseTimes, int success, int fail, long durationSec) {
        if (responseTimes.isEmpty()) {
            return new PerformanceSummary(0, 0, 0, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0.0);
        }
        
        Collections.sort(responseTimes);
        int total = responseTimes.size();
        
        long min = responseTimes.get(0);
        long max = responseTimes.get(responseTimes.size() - 1);
        long sum = 0;
        for (Long time : responseTimes) {
            sum += time;
        }
        long avg = sum / total;
        
        // 计算百分位数
        long p50 = responseTimes.get((int) (total * 0.50));
        long p90 = responseTimes.get((int) (total * 0.90));
        long p95 = responseTimes.get((int) (total * 0.95));
        long p99 = responseTimes.get((int) (total * 0.99));
        
        // 计算吞吐量
        double throughput = (double) total / durationSec;
        
        return new PerformanceSummary(total, success, fail, min, max, avg, p50, p90, p95, p99, throughput);
    }
    
    /**
     * 保存结果到CSV
     */
    private void saveResultsToCsv(List<Long> responseTimes, int success, int fail) {
        String outputDir = config.getOutputDir();
        if (outputDir == null || outputDir.isEmpty()) {
            outputDir = "target/reports";
        }
        new File(outputDir).mkdirs();
        
        String resultFile = outputDir + File.separator + "perf-test-results.csv";
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(resultFile))) {
            // CSV头部
            writer.println("timeStamp,elapsed,label,responseCode,responseMessage,success,bytes,sentBytes,grpThreads,allThreads,URL,latency,idleTime,connect");
            
            long baseTime = System.currentTimeMillis() * 1000000; // 纳秒
            for (int i = 0; i < responseTimes.size(); i++) {
                long time = responseTimes.get(i);
                long timestamp = baseTime + (i * 1000000L);
                boolean isSuccess = i < success;
                
                writer.printf("%.0f,%d,%s,%s,%s,%s,0,0,1,%d,http://localhost,0,0,0%n",
                        (double) timestamp / 1000000, // 转换回毫秒
                        time,
                        "POST /energyApi/report/detail",
                        isSuccess ? "200" : "401",
                        isSuccess ? "OK" : "Unauthorized",
                        isSuccess,
                        config.getPerfThreads());
            }
            
            System.out.println("[Performance] Results saved to: " + resultFile);
        } catch (IOException e) {
            System.err.println("[Performance] Error saving results: " + e.getMessage());
        }
    }

    /**
     * 获取性能测试摘要
     */
    public PerformanceSummary getPerformanceSummary() {
        return performanceSummary;
    }

    /**
     * 性能测试摘要
     */
    public static class PerformanceSummary {
        private final int totalSamples;
        private final int successCount;
        private final int failCount;
        private final long minResponseTime;
        private final long maxResponseTime;
        private final long avgResponseTime;
        private final long p50ResponseTime;
        private final long p90ResponseTime;
        private final long p95ResponseTime;
        private final long p99ResponseTime;
        private final double throughput;

        public PerformanceSummary(int totalSamples, int successCount, int failCount,
                long minResponseTime, long maxResponseTime, long avgResponseTime,
                long p50ResponseTime, long p90ResponseTime, long p95ResponseTime,
                long p99ResponseTime, double throughput) {
            this.totalSamples = totalSamples;
            this.successCount = successCount;
            this.failCount = failCount;
            this.minResponseTime = minResponseTime;
            this.maxResponseTime = maxResponseTime;
            this.avgResponseTime = avgResponseTime;
            this.p50ResponseTime = p50ResponseTime;
            this.p90ResponseTime = p90ResponseTime;
            this.p95ResponseTime = p95ResponseTime;
            this.p99ResponseTime = p99ResponseTime;
            this.throughput = throughput;
        }

        public int getTotalSamples() { return totalSamples; }
        public int getSuccessCount() { return successCount; }
        public int getFailCount() { return failCount; }
        public long getErrorRate() { return totalSamples > 0 ? (long)(failCount * 100.0 / totalSamples) : 0; }
        public long getMinResponseTime() { return minResponseTime; }
        public long getMaxResponseTime() { return maxResponseTime; }
        public long getAvgResponseTime() { return avgResponseTime; }
        public long getP50ResponseTime() { return p50ResponseTime; }
        public long getP90ResponseTime() { return p90ResponseTime; }
        public long getP95ResponseTime() { return p95ResponseTime; }
        public long getP99ResponseTime() { return p99ResponseTime; }
        public double getThroughput() { return throughput; }

        public String getFormattedMinTime() { return String.valueOf(minResponseTime); }
        public String getFormattedMaxTime() { return String.valueOf(maxResponseTime); }
        public String getFormattedAvgTime() { return String.valueOf(avgResponseTime); }
        public String getFormattedP50Time() { return String.valueOf(p50ResponseTime); }
        public String getFormattedP90Time() { return String.valueOf(p90ResponseTime); }
        public String getFormattedP95Time() { return String.valueOf(p95ResponseTime); }
        public String getFormattedP99Time() { return String.valueOf(p99ResponseTime); }
    }
}
