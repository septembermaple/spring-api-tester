package com.autotest.report;

import com.autotest.config.Config;
import com.autotest.executor.PerformanceTester;
import com.autotest.executor.TestCase;
import com.autotest.executor.TestResult;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 增强版详细HTML报告生成器
 * 
 * 生成包含以下内容的完整测试报告：
 * - 报告概览
 * - 测试统计摘要（带图表）
 * - 测试用例详情表
 * - 性能测试结果
 * - 修复建议
 * - 附录
 */
public class DetailedReportGenerator {

    private final Config config;
    private LocalDateTime executionStartTime;
    private LocalDateTime executionEndTime;
    
    private List<TestResult> testResults;
    private List<FixSuggestion> fixSuggestions;
    private List<PerformanceTester.PerformanceSummary> performanceSummaries;

    public DetailedReportGenerator(Config config) {
        this.config = config;
        this.executionStartTime = LocalDateTime.now();
        this.executionEndTime = LocalDateTime.now();
    }

    public void generateReport(List<TestResult> results, List<FixSuggestion> suggestions, 
                               List<PerformanceTester.PerformanceSummary> perfSummaries, String outputPath) {
        this.testResults = results;
        this.fixSuggestions = suggestions != null ? suggestions : new ArrayList<>();
        this.performanceSummaries = perfSummaries != null ? perfSummaries : new ArrayList<>();
        this.executionEndTime = LocalDateTime.now();
        
        try (PrintWriter writer = new PrintWriter(outputPath, "UTF-8")) {
            writer.print(buildHtmlReport());
            System.out.println("Detailed report generated: " + outputPath);
        } catch (IOException e) {
            System.err.println("Error generating report: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String buildHtmlReport() {
        StringBuilder sb = new StringBuilder();
        
        // HTML头部
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html lang=\"zh-CN\">\n");
        sb.append("<head>\n");
        sb.append("    <meta charset=\"UTF-8\">\n");
        sb.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("    <title>Spring API 测试报告 - 详细版</title>\n");
        sb.append(buildCssStyles());
        sb.append("</head>\n");
        sb.append("<body>\n");
        
        // 头部
        sb.append(buildHeader());
        
        // 主内容
        sb.append("<div class=\"container\">\n");
        
        // 执行摘要
        sb.append(buildExecutionSummary());
        
        // 统计图表
        sb.append(buildStatisticsSection());
        
        // 测试详情表
        sb.append(buildTestDetailsSection());
        
        // 性能测试结果
        if (!performanceSummaries.isEmpty()) {
            sb.append(buildPerformanceSection());
        }
        
        // 修复建议
        if (!fixSuggestions.isEmpty()) {
            sb.append(buildFixSuggestionsSection());
        }
        
        // 附录
        sb.append(buildAppendixSection());
        
        sb.append("</div>\n"); // container end
        
        // 页脚
        sb.append(buildFooter());
        
        // JavaScript
        sb.append(buildJavaScript());
        
        sb.append("</body>\n");
        sb.append("</html>\n");
        
        return sb.toString();
    }

    private String buildCssStyles() {
        StringBuilder sb = new StringBuilder();
        sb.append("    <style>\n");
        sb.append("        :root {\n");
        sb.append("            --primary: #2563eb;\n");
        sb.append("            --primary-dark: #1d4ed8;\n");
        sb.append("            --success: #10b981;\n");
        sb.append("            --danger: #ef4444;\n");
        sb.append("            --warning: #f59e0b;\n");
        sb.append("            --info: #6366f1;\n");
        sb.append("            --gray-50: #f9fafb;\n");
        sb.append("            --gray-100: #f3f4f6;\n");
        sb.append("            --gray-200: #e5e7eb;\n");
        sb.append("            --gray-400: #9ca3af;\n");
        sb.append("            --gray-600: #4b5563;\n");
        sb.append("            --gray-800: #1f2937;\n");
        sb.append("            --gray-900: #111827;\n");
        sb.append("        }\n");
        sb.append("        * { margin: 0; padding: 0; box-sizing: border-box; }\n");
        sb.append("        body { font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, sans-serif; font-size: 14px; line-height: 1.6; color: var(--gray-800); background: var(--gray-100); }\n");
        sb.append("        header { background: linear-gradient(135deg, var(--primary) 0%, var(--primary-dark) 100%); color: white; padding: 30px 40px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); position: sticky; top: 0; z-index: 100; }\n");
        sb.append("        header h1 { font-size: 28px; margin-bottom: 8px; }\n");
        sb.append("        header .subtitle { opacity: 0.9; font-size: 14px; }\n");
        sb.append("        .container { max-width: 1400px; margin: 30px auto; padding: 0 20px; }\n");
        sb.append("        .section { background: white; border-radius: 12px; padding: 24px; margin-bottom: 24px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }\n");
        sb.append("        .section h2 { color: var(--gray-800); font-size: 18px; margin-bottom: 20px; padding-bottom: 12px; border-bottom: 2px solid var(--gray-200); display: flex; align-items: center; gap: 10px; }\n");
        sb.append("        .section h2 .icon { width: 24px; height: 24px; }\n");
        sb.append("        .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 20px; margin-bottom: 24px; }\n");
        sb.append("        .stat-card { background: white; border-radius: 12px; padding: 20px; text-align: center; box-shadow: 0 2px 8px rgba(0,0,0,0.08); transition: transform 0.2s; }\n");
        sb.append("        .stat-card:hover { transform: translateY(-2px); }\n");
        sb.append("        .stat-card .number { font-size: 36px; font-weight: bold; margin-bottom: 8px; }\n");
        sb.append("        .stat-card .label { color: var(--gray-600); font-size: 14px; }\n");
        sb.append("        .stat-card.success .number { color: var(--success); }\n");
        sb.append("        .stat-card.danger .number { color: var(--danger); }\n");
        sb.append("        .stat-card.primary .number { color: var(--primary); }\n");
        sb.append("        .stat-card.warning .number { color: var(--warning); }\n");
        sb.append("        .pass-rate-bar { height: 8px; background: var(--gray-200); border-radius: 4px; overflow: hidden; margin-top: 8px; }\n");
        sb.append("        .pass-rate-bar .fill { height: 100%; border-radius: 4px; transition: width 0.5s ease; }\n");
        sb.append("        .charts-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 24px; margin-top: 20px; }\n");
        sb.append("        .chart-card { background: var(--gray-100); border-radius: 8px; padding: 20px; }\n");
        sb.append("        .chart-card h3 { font-size: 14px; color: var(--gray-600); margin-bottom: 16px; text-transform: uppercase; letter-spacing: 0.5px; }\n");
        sb.append("        .bar-chart { display: flex; flex-direction: column; gap: 12px; }\n");
        sb.append("        .bar-item { display: flex; align-items: center; gap: 12px; }\n");
        sb.append("        .bar-item .label { width: 80px; font-size: 13px; color: var(--gray-600); }\n");
        sb.append("        .bar-item .bar { flex: 1; height: 24px; background: var(--gray-200); border-radius: 4px; overflow: hidden; position: relative; }\n");
        sb.append("        .bar-item .bar .fill { height: 100%; border-radius: 4px; display: flex; align-items: center; padding-left: 8px; font-size: 12px; color: white; font-weight: 500; min-width: 40px; }\n");
        sb.append("        .bar-item .count { width: 50px; text-align: right; font-size: 13px; font-weight: 600; }\n");
        sb.append("        .pie-chart { width: 200px; height: 200px; border-radius: 50%; margin: 0 auto; position: relative; background: conic-gradient(var(--success) 0deg, var(--success) ").append(getPassDegree()).append("deg, var(--danger) ").append(getPassDegree()).append("deg, var(--danger) 360deg); }\n");
        sb.append("        .pie-chart .center { position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); width: 120px; height: 120px; background: white; border-radius: 50%; display: flex; flex-direction: column; align-items: center; justify-content: center; }\n");
        sb.append("        .pie-chart .center .percent { font-size: 28px; font-weight: bold; color: var(--gray-800); }\n");
        sb.append("        .pie-chart .center .text { font-size: 12px; color: var(--gray-600); }\n");
        sb.append("        .perf-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; margin-top: 16px; }\n");
        sb.append("        .perf-card { background: var(--gray-100); border-radius: 8px; padding: 16px; text-align: center; }\n");
        sb.append("        .perf-card .value { font-size: 24px; font-weight: bold; color: var(--primary); }\n");
        sb.append("        .perf-card .label { font-size: 12px; color: var(--gray-600); margin-top: 4px; }\n");
        sb.append("        .suggestion-card { background: var(--gray-100); border-radius: 8px; padding: 16px; margin-bottom: 12px; border-left: 4px solid; }\n");
        sb.append("        .suggestion-card.high { border-left-color: var(--danger); }\n");
        sb.append("        .suggestion-card.medium { border-left-color: var(--warning); }\n");
        sb.append("        .suggestion-card.low { border-left-color: var(--info); }\n");
        sb.append("        .suggestion-card h4 { font-size: 14px; margin-bottom: 8px; }\n");
        sb.append("        .suggestion-card .severity { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; color: white; margin-left: 8px; }\n");
        sb.append("        .suggestion-card .severity.high { background: var(--danger); }\n");
        sb.append("        .suggestion-card .severity.medium { background: var(--warning); }\n");
        sb.append("        .suggestion-card .severity.low { background: var(--info); }\n");
        sb.append("        .suggestion-card pre { background: white; padding: 12px; border-radius: 4px; overflow-x: auto; font-size: 12px; margin-top: 8px; }\n");
        sb.append("        .appendix-table { width: 100%; border-collapse: collapse; }\n");
        sb.append("        .appendix-table td { padding: 8px 12px; border-bottom: 1px solid var(--gray-200); }\n");
        sb.append("        .appendix-table td:first-child { font-weight: 600; color: var(--gray-600); width: 200px; }\n");
        sb.append("        footer { text-align: center; padding: 30px; color: var(--gray-600); font-size: 12px; }\n");
        sb.append("        .search-box { position: sticky; top: 100px; background: white; padding: 12px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); margin-bottom: 20px; z-index: 50; }\n");
        sb.append("        .search-box input { width: 100%; padding: 10px 16px; border: 1px solid var(--gray-200); border-radius: 6px; font-size: 14px; }\n");
        sb.append("        .search-box input:focus { outline: none; border-color: var(--primary); }\n");
        sb.append("        .filter-group { display: flex; gap: 8px; margin-top: 8px; }\n");
        sb.append("        .filter-btn { padding: 6px 12px; border: 1px solid var(--gray-200); border-radius: 4px; background: white; cursor: pointer; font-size: 12px; transition: all 0.2s; }\n");
        sb.append("        .filter-btn:hover { border-color: var(--primary); }\n");
        sb.append("        .filter-btn.active { background: var(--primary); color: white; border-color: var(--primary); }\n");
        // 新增测试用例卡片样式
        sb.append("        .test-case-card { background: white; border: 1px solid var(--gray-200); border-radius: 8px; margin-bottom: 12px; overflow: hidden; cursor: pointer; transition: all 0.2s; }\n");
        sb.append("        .test-case-card:hover { border-color: var(--primary); box-shadow: 0 2px 8px rgba(37, 99, 235, 0.1); }\n");
        sb.append("        .test-case-summary { display: flex; align-items: center; gap: 12px; padding: 16px; background: var(--gray-50); }\n");
        sb.append("        .status-badge { padding: 4px 12px; border-radius: 20px; font-size: 12px; font-weight: 600; }\n");
        sb.append("        .status-badge.pass { background: #dcfce7; color: #166534; }\n");
        sb.append("        .status-badge.fail { background: #fee2e2; color: #991b1b; }\n");
        sb.append("        .method-badge { padding: 4px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; text-transform: uppercase; }\n");
        sb.append("        .method-badge.get { background: #dbeafe; color: #1e40af; }\n");
        sb.append("        .method-badge.post { background: #dcfce7; color: #166534; }\n");
        sb.append("        .method-badge.put { background: #fef3c7; color: #92400e; }\n");
        sb.append("        .method-badge.delete { background: #fee2e2; color: #991b1b; }\n");
        sb.append("        .url-text { flex: 1; font-family: monospace; font-size: 13px; color: var(--gray-600); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }\n");
        sb.append("        .toggle-icon { color: var(--gray-400); transition: transform 0.2s; }\n");
        sb.append("        .test-case-card.expanded .toggle-icon { transform: rotate(180deg); }\n");
        sb.append("        .test-case-detail { padding: 0 16px 16px; border-top: 1px solid var(--gray-100); }\n");
        sb.append("        .detail-section { margin-top: 16px; }\n");
        sb.append("        .detail-section h4 { font-size: 13px; color: var(--gray-600); margin-bottom: 8px; text-transform: uppercase; }\n");
        sb.append("        .detail-section pre { background: var(--gray-100); padding: 12px; border-radius: 6px; overflow-x: auto; font-size: 12px; font-family: 'Courier New', monospace; }\n");
        sb.append("        .error-text { color: var(--danger); }\n");
        sb.append("        .status-code { font-family: monospace; font-size: 13px; color: var(--gray-600); }\n");
        sb.append("        .response-time { font-family: monospace; font-size: 13px; color: var(--gray-600); }\n");
        sb.append("        @media print { body { background: white; } .no-print { display: none; } header { position: static; } .section { box-shadow: none; } }\n");
        sb.append("    </style>\n");
        return sb.toString();
    }

    private String buildHeader() {
        StringBuilder sb = new StringBuilder();
        sb.append("    <header>\n");
        sb.append("        <h1>Spring API 测试报告</h1>\n");
        sb.append("        <div class=\"subtitle\">生成时间: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("</div>\n");
        sb.append("    </header>\n");
        return sb.toString();
    }

    private String buildExecutionSummary() {
        int total = testResults.size();
        long passed = testResults.stream().filter(r -> r.isSuccess()).count();
        long failed = total - passed;
        double passRate = total > 0 ? (double) passed / total * 100 : 0;
        
        StringBuilder sb = new StringBuilder();
        sb.append("        <div class=\"section\">\n");
        sb.append("            <h2><svg class=\"icon\" fill=\"none\" stroke=\"currentColor\" viewBox=\"0 0 24 24\"><path stroke-linecap=\"round\" stroke-linejoin=\"round\" stroke-width=\"2\" d=\"M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z\"></path></svg>执行摘要</h2>\n");
        sb.append("            <div class=\"stats-grid\">\n");
        sb.append("                <div class=\"stat-card primary\">\n");
        sb.append("                    <div class=\"number\">").append(total).append("</div>\n");
        sb.append("                    <div class=\"label\">总测试数</div>\n");
        sb.append("                </div>\n");
        sb.append("                <div class=\"stat-card success\">\n");
        sb.append("                    <div class=\"number\">").append(passed).append("</div>\n");
        sb.append("                    <div class=\"label\">通过</div>\n");
        sb.append("                </div>\n");
        sb.append("                <div class=\"stat-card danger\">\n");
        sb.append("                    <div class=\"number\">").append(failed).append("</div>\n");
        sb.append("                    <div class=\"label\">失败</div>\n");
        sb.append("                </div>\n");
        sb.append("                <div class=\"stat-card warning\">\n");
        sb.append("                    <div class=\"number\">").append(String.format("%.1f", passRate)).append("%</div>\n");
        sb.append("                    <div class=\"label\">通过率</div>\n");
        sb.append("                    <div class=\"pass-rate-bar\">\n");
        sb.append("                        <div class=\"fill\" style=\"width: ").append((int)passRate).append("%; background: ").append(passRate >= 80 ? "var(--success)" : passRate >= 50 ? "var(--warning)" : "var(--danger)").append(";\"></div>\n");
        sb.append("                    </div>\n");
        sb.append("                </div>\n");
        sb.append("            </div>\n");
        sb.append("        </div>\n");
        return sb.toString();
    }

    private String buildStatisticsSection() {
        // HTTP方法统计
        Map<String, Long> methodCounts = new HashMap<>();
        for (TestResult r : testResults) {
            String method = r.getHttpMethod() != null ? r.getHttpMethod().toUpperCase() : "UNKNOWN";
            methodCounts.put(method, methodCounts.getOrDefault(method, 0L) + 1);
        }
        
        // 响应时间统计
        List<Long> responseTimes = new ArrayList<>();
        for (TestResult r : testResults) {
            if (r.getResponseTime() > 0) {
                responseTimes.add(r.getResponseTime());
            }
        }
        Collections.sort(responseTimes);
        
        long avgTime = responseTimes.isEmpty() ? 0 : responseTimes.stream().mapToLong(Long::longValue).sum() / responseTimes.size();
        long p50Time = responseTimes.isEmpty() ? 0 : responseTimes.get(responseTimes.size() / 2);
        long p90Time = responseTimes.isEmpty() ? 0 : responseTimes.get((int) (responseTimes.size() * 0.9));
        long p99Time = responseTimes.isEmpty() ? 0 : responseTimes.get((int) (responseTimes.size() * 0.99));
        
        StringBuilder sb = new StringBuilder();
        sb.append("        <div class=\"section\">\n");
        sb.append("            <h2><svg class=\"icon\" fill=\"none\" stroke=\"currentColor\" viewBox=\"0 0 24 24\"><path stroke-linecap=\"round\" stroke-linejoin=\"round\" stroke-width=\"2\" d=\"M11 3.055A9.001 9.001 0 1020.945 13H11V3.055z\"></path><path stroke-linecap=\"round\" stroke-linejoin=\"round\" stroke-width=\"2\" d=\"M20.488 9H15V3.512A9.025 9.025 0 0120.488 9z\"></path></svg>统计图表</h2>\n");
        sb.append("            <div class=\"charts-row\">\n");
        
        // 通过率饼图
        long passCount = testResults.stream().filter(r -> r.isSuccess()).count();
        sb.append("                <div class=\"chart-card\">\n");
        sb.append("                    <h3>通过率</h3>\n");
        sb.append("                    <div class=\"pie-chart\">\n");
        sb.append("                        <div class=\"center\">\n");
        sb.append("                            <div class=\"percent\">").append(testResults.size() > 0 ? String.format("%.1f", (double) passCount / testResults.size() * 100) : "0").append("%</div>\n");
        sb.append("                            <div class=\"text\">").append(passCount).append("/").append(testResults.size()).append("</div>\n");
        sb.append("                        </div>\n");
        sb.append("                    </div>\n");
        sb.append("                </div>\n");
        
        // HTTP方法分布
        sb.append("                <div class=\"chart-card\">\n");
        sb.append("                    <h3>HTTP方法分布</h3>\n");
        sb.append("                    <div class=\"bar-chart\">\n");
        int maxMethodCount = methodCounts.values().stream().max(Long::compare).orElse(1L).intValue();
        for (Map.Entry<String, Long> entry : methodCounts.entrySet()) {
            int width = (int) (entry.getValue() * 100 / maxMethodCount);
            String color = getMethodColor(entry.getKey());
            sb.append("                        <div class=\"bar-item\">\n");
            sb.append("                            <div class=\"label\">").append(entry.getKey()).append("</div>\n");
            sb.append("                            <div class=\"bar\">\n");
            sb.append("                                <div class=\"fill\" style=\"width: ").append(Math.max(width, 1)).append("%; background: ").append(color).append(";\"></div>\n");
            sb.append("                            </div>\n");
            sb.append("                            <div class=\"count\">").append(entry.getValue()).append("</div>\n");
            sb.append("                        </div>\n");
        }
        sb.append("                    </div>\n");
        sb.append("                </div>\n");
        
        // 响应时间统计
        sb.append("                <div class=\"chart-card\">\n");
        sb.append("                    <h3>响应时间 (ms)</h3>\n");
        sb.append("                    <div class=\"bar-chart\">\n");
        sb.append("                        <div class=\"bar-item\"><div class=\"label\">平均</div><div class=\"bar\"><div class=\"fill\" style=\"width: ").append(Math.min(avgTime / 10, 100)).append("%; background: var(--primary);\"></div></div><div class=\"count\">").append(avgTime).append("</div></div>\n");
        sb.append("                        <div class=\"bar-item\"><div class=\"label\">P50</div><div class=\"bar\"><div class=\"fill\" style=\"width: ").append(Math.min(p50Time / 10, 100)).append("%; background: var(--info);\"></div></div><div class=\"count\">").append(p50Time).append("</div></div>\n");
        sb.append("                        <div class=\"bar-item\"><div class=\"label\">P90</div><div class=\"bar\"><div class=\"fill\" style=\"width: ").append(Math.min(p90Time / 10, 100)).append("%; background: var(--warning);\"></div></div><div class=\"count\">").append(p90Time).append("</div></div>\n");
        sb.append("                        <div class=\"bar-item\"><div class=\"label\">P99</div><div class=\"bar\"><div class=\"fill\" style=\"width: ").append(Math.min(p99Time / 10, 100)).append("%; background: var(--danger);\"></div></div><div class=\"count\">").append(p99Time).append("</div></div>\n");
        sb.append("                    </div>\n");
        sb.append("                </div>\n");
        
        sb.append("            </div>\n");
        sb.append("        </div>\n");
        return sb.toString();
    }

    private String buildTestDetailsSection() {
        StringBuilder sb = new StringBuilder();
        sb.append("        <div class=\"section\">\n");
        sb.append("            <h2><svg class=\"icon\" fill=\"none\" stroke=\"currentColor\" viewBox=\"0 0 24 24\"><path stroke-linecap=\"round\" stroke-linejoin=\"round\" stroke-width=\"2\" d=\"M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2\"></path></svg>测试详情 (点击展开)</h2>\n");
        sb.append("            <div class=\"search-box no-print\">\n");
        sb.append("                <input type=\"text\" id=\"searchInput\" placeholder=\"搜索测试用例...\" onkeyup=\"filterTable()\">\n");
        sb.append("                <div class=\"filter-group\">\n");
        sb.append("                    <button class=\"filter-btn active\" onclick=\"setFilter('all')\">全部</button>\n");
        sb.append("                    <button class=\"filter-btn\" onclick=\"setFilter('pass')\">通过</button>\n");
        sb.append("                    <button class=\"filter-btn\" onclick=\"setFilter('fail')\">失败</button>\n");
        sb.append("                </div>\n");
        sb.append("            </div>\n");

        int index = 0;
        for (TestResult result : testResults) {
            String status = result.isSuccess() ? "pass" : "fail";
            String method = result.getHttpMethod() != null ? result.getHttpMethod().toUpperCase() : "GET";
            String url = result.getUrl() != null ? result.getUrl() : "";
            int expectedStatus = result.getExpectedStatusCode() > 0 ? result.getExpectedStatusCode() : 200;
            int actualStatus = result.getStatusCode();
            long responseTime = result.getResponseTime();

            sb.append("            <div class=\"test-case-card filter-").append(status).append("\" onclick=\"toggleDetail('detail-").append(index).append("')\">\n");
            // 概要行
            sb.append("                <div class=\"test-case-summary\">\n");
            sb.append("                    <span class=\"status-badge ").append(status).append("\">").append(result.isSuccess() ? "通过" : "失败").append("</span>\n");
            sb.append("                    <span class=\"method-badge ").append(method.toLowerCase()).append("\">").append(method).append("</span>\n");
            sb.append("                    <span class=\"url-text\" title=\"").append(escapeHtml(url)).append("\">").append(escapeHtml(url)).append("</span>\n");
            sb.append("                    <span class=\"status-code\">").append(expectedStatus).append(" → ").append(actualStatus > 0 ? actualStatus : "-").append("</span>\n");
            sb.append("                    <span class=\"response-time\">").append(responseTime > 0 ? responseTime + "ms" : "-").append("</span>\n");
            sb.append("                    <span class=\"toggle-icon\">▼</span>\n");
            sb.append("                </div>\n");

            // 详情区域（默认折叠）
            sb.append("                <div id=\"detail-").append(index).append("\" class=\"test-case-detail\" style=\"display:none;\">\n");

            // 请求信息
            sb.append("                    <div class=\"detail-section\">\n");
            sb.append("                        <h4>请求信息</h4>\n");
            sb.append("                        <p><strong>URL:</strong> ").append(escapeHtml(url)).append("</p>\n");
            sb.append("                        <p><strong>方法:</strong> ").append(method).append("</p>\n");
            if (result.getRequestBody() != null && !result.getRequestBody().isEmpty()) {
                sb.append("                        <p><strong>请求体:</strong></p>\n");
                sb.append("                        <pre>").append(escapeHtml(formatJson(result.getRequestBody()))).append("</pre>\n");
            }
            sb.append("                    </div>\n");

            // 响应信息
            sb.append("                    <div class=\"detail-section\">\n");
            sb.append("                        <h4>响应信息</h4>\n");
            sb.append("                        <p><strong>状态码:</strong> ").append(actualStatus).append("</p>\n");
            if (!result.isSuccess() && result.getErrorMessage() != null) {
                sb.append("                        <p><strong>错误:</strong> <span class=\"error-text\">").append(escapeHtml(result.getErrorMessage())).append("</span></p>\n");
            }
            if (result.getResponseBody() != null && !result.getResponseBody().isEmpty()) {
                sb.append("                        <p><strong>响应体:</strong></p>\n");
                sb.append("                        <pre>").append(escapeHtml(formatJson(result.getResponseBody()))).append("</pre>\n");
            }
            sb.append("                    </div>\n");

            sb.append("                </div>\n"); // detail end
            sb.append("            </div>\n"); // card end

            index++;
        }

        sb.append("        </div>\n");
        return sb.toString();
    }

    private String buildPerformanceSection() {
        StringBuilder sb = new StringBuilder();
        sb.append("        <div class=\"section\">\n");
        sb.append("            <h2><svg class=\"icon\" fill=\"none\" stroke=\"currentColor\" viewBox=\"0 0 24 24\"><path stroke-linecap=\"round\" stroke-linejoin=\"round\" stroke-width=\"2\" d=\"M13 10V3L4 14h7v7l9-11h-7z\"></path></svg>性能测试结果</h2>\n");
        
        for (PerformanceTester.PerformanceSummary ps : performanceSummaries) {
            sb.append("            <div style=\"margin-bottom: 24px;\">\n");
            sb.append("                <h3 style=\"font-size: 16px; margin-bottom: 12px;\">性能测试</h3>\n");
            sb.append("                <div class=\"perf-grid\">\n");
            sb.append("                    <div class=\"perf-card\"><div class=\"value\">").append(ps.getTotalSamples()).append("</div><div class=\"label\">总请求数</div></div>\n");
            sb.append("                    <div class=\"perf-card\"><div class=\"value\">").append(ps.getSuccessCount()).append("</div><div class=\"label\">成功</div></div>\n");
            sb.append("                    <div class=\"perf-card\"><div class=\"value\">").append(ps.getFailCount()).append("</div><div class=\"label\">失败</div></div>\n");
            sb.append("                    <div class=\"perf-card\"><div class=\"value\">").append(ps.getFormattedAvgTime()).append("ms</div><div class=\"label\">平均响应</div></div>\n");
            sb.append("                    <div class=\"perf-card\"><div class=\"value\">").append(ps.getFormattedMinTime()).append("ms</div><div class=\"label\">最小响应</div></div>\n");
            sb.append("                    <div class=\"perf-card\"><div class=\"value\">").append(ps.getFormattedMaxTime()).append("ms</div><div class=\"label\">最大响应</div></div>\n");
            sb.append("                    <div class=\"perf-card\"><div class=\"value\">").append(ps.getFormattedP50Time()).append("ms</div><div class=\"label\">P50</div></div>\n");
            sb.append("                    <div class=\"perf-card\"><div class=\"value\">").append(ps.getFormattedP90Time()).append("ms</div><div class=\"label\">P90</div></div>\n");
            sb.append("                    <div class=\"perf-card\"><div class=\"value\">").append(ps.getFormattedP95Time()).append("ms</div><div class=\"label\">P95</div></div>\n");
            sb.append("                    <div class=\"perf-card\"><div class=\"value\">").append(ps.getFormattedP99Time()).append("ms</div><div class=\"label\">P99</div></div>\n");
            sb.append("                    <div class=\"perf-card\"><div class=\"value\">").append(String.format("%.1f", ps.getThroughput())).append("</div><div class=\"label\">吞吐量(req/s)</div></div>\n");
            sb.append("                    <div class=\"perf-card\"><div class=\"value\">").append(ps.getErrorRate()).append("%</div><div class=\"label\">错误率</div></div>\n");
            sb.append("                </div>\n");
            sb.append("            </div>\n");
        }
        
        sb.append("        </div>\n");
        return sb.toString();
    }

    private String buildFixSuggestionsSection() {
        StringBuilder sb = new StringBuilder();
        sb.append("        <div class=\"section\">\n");
        sb.append("            <h2><svg class=\"icon\" fill=\"none\" stroke=\"currentColor\" viewBox=\"0 0 24 24\"><path stroke-linecap=\"round\" stroke-linejoin=\"round\" stroke-width=\"2\" d=\"M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z\"></path></svg>修复建议 (共").append(fixSuggestions.size()).append("条)</h2>\n");
        
        for (FixSuggestion suggestion : fixSuggestions) {
            String severity = suggestion.getSeverity() != null ? suggestion.getSeverity().toLowerCase() : "medium";
            String severityCn = translateSeverity(suggestion.getSeverity());
            sb.append("            <div class=\"suggestion-card ").append(severity).append("\">\n");
            sb.append("                <h4>").append(escapeHtml(suggestion.getTestCaseName() != null ? suggestion.getTestCaseName() : "Unknown Test"));
            sb.append(" <span class=\"severity ").append(severity).append("\">").append(severityCn).append("</span></h4>\n");
            sb.append("                <p><strong>问题:</strong> ").append(escapeHtml(suggestion.getErrorType() != null ? suggestion.getErrorType() : "未知问题")).append("</p>\n");
            sb.append("                <p><strong>建议:</strong> ").append(escapeHtml(suggestion.getSuggestion() != null ? suggestion.getSuggestion() : "无建议")).append("</p>\n");
            
            if (suggestion.getCodeExample() != null && !suggestion.getCodeExample().isEmpty()) {
                sb.append("                <p><strong>修复示例:</strong></p>\n");
                sb.append("                <pre>").append(escapeHtml(suggestion.getCodeExample())).append("</pre>\n");
            }
            
            if (suggestion.getReferenceLink() != null && !suggestion.getReferenceLink().isEmpty()) {
                sb.append("                <p><strong>参考链接:</strong> <a href=\"").append(escapeHtml(suggestion.getReferenceLink())).append("\" target=\"_blank\">").append(escapeHtml(suggestion.getReferenceLink())).append("</a></p>\n");
            }
            
            sb.append("            </div>\n");
        }
        
        sb.append("        </div>\n");
        return sb.toString();
    }

    private String buildAppendixSection() {
        StringBuilder sb = new StringBuilder();
        sb.append("        <div class=\"section\">\n");
        sb.append("            <h2><svg class=\"icon\" fill=\"none\" stroke=\"currentColor\" viewBox=\"0 0 24 24\"><path stroke-linecap=\"round\" stroke-linejoin=\"round\" stroke-width=\"2\" d=\"M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z\"></path></svg>附录 - 测试配置</h2>\n");
        sb.append("            <table class=\"appendix-table\">\n");
        sb.append("                <tr><td>被测服务URL</td><td>").append(config.getBaseUrl()).append("</td></tr>\n");
        sb.append("                <tr><td>Git项目路径</td><td>").append(config.getGitPath()).append("</td></tr>\n");
        sb.append("                <tr><td>目标分支</td><td>").append(config.getBranch() != null ? config.getBranch() : "当前分支").append("</td></tr>\n");
        sb.append("                <tr><td>起始时间</td><td>").append(config.getSince() != null ? config.getSince() : "全部历史").append("</td></tr>\n");
        if (config.getAuthor() != null && !config.getAuthor().isEmpty()) {
            sb.append("                <tr><td>提交用户</td><td>").append(config.getAuthor()).append("</td></tr>\n");
        }
        sb.append("                <tr><td>并发线程数</td><td>").append(config.getThreads()).append("</td></tr>\n");
        sb.append("                <tr><td>超时时间</td><td>").append(config.getTimeout()).append("秒</td></tr>\n");
        sb.append("                <tr><td>性能测试</td><td>").append(config.isPerfTest() ? "启用" : "禁用").append("</td></tr>\n");
        if (config.isPerfTest()) {
            sb.append("                <tr><td>性能测试线程</td><td>").append(config.getPerfThreads()).append("</td></tr>\n");
            sb.append("                <tr><td>性能测试时长</td><td>").append(config.getPerfDuration()).append("秒</td></tr>\n");
        }
        sb.append("                <tr><td>自动修复</td><td>").append(config.isAutoFix() ? "启用" : "禁用").append("</td></tr>\n");
        sb.append("                <tr><td>AI模型</td><td>").append(config.getModel() != null ? config.getModel() : "未配置").append("</td></tr>\n");
        sb.append("                <tr><td>NEW API</td><td>").append(config.getNewApiUrl() != null ? config.getNewApiUrl() : "未配置").append("</td></tr>\n");
        sb.append("                <tr><td>报告生成时间</td><td>").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("</td></tr>\n");
        sb.append("            </table>\n");
        sb.append("        </div>\n");
        return sb.toString();
    }

    private String buildFooter() {
        return "    <footer>\n" +
               "        <p>Spring API Tester - AI驱动接口测试工具 | 生成时间: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "</p>\n" +
               "    </footer>\n";
    }

    private String buildJavaScript() {
        StringBuilder sb = new StringBuilder();
        sb.append("    <script>\n");
        sb.append("        var currentFilter = 'all';\n");
        sb.append("        function setFilter(filter) {\n");
        sb.append("            currentFilter = filter;\n");
        sb.append("            document.querySelectorAll('.filter-btn').forEach(function(btn) { btn.classList.remove('active'); });\n");
        sb.append("            event.target.classList.add('active');\n");
        sb.append("            filterTable();\n");
        sb.append("        }\n");
        sb.append("        function filterTable() {\n");
        sb.append("            var input = document.getElementById('searchInput');\n");
        sb.append("            var filter = input.value.toUpperCase();\n");
        sb.append("            var cards = document.querySelectorAll('.test-case-card');\n");
        sb.append("            for (var i = 0; i < cards.length; i++) {\n");
        sb.append("                var show = true;\n");
        sb.append("                if (currentFilter !== 'all') {\n");
        sb.append("                    show = cards[i].classList.contains('filter-' + currentFilter);\n");
        sb.append("                }\n");
        sb.append("                if (show && filter) {\n");
        sb.append("                    var text = cards[i].textContent || cards[i].innerText;\n");
        sb.append("                    show = text.toUpperCase().indexOf(filter) > -1;\n");
        sb.append("                }\n");
        sb.append("                cards[i].style.display = show ? '' : 'none';\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("        function toggleDetail(id) {\n");
        sb.append("            var detail = document.getElementById(id);\n");
        sb.append("            var card = detail.parentElement;\n");
        sb.append("            if (detail.style.display === 'none') {\n");
        sb.append("                detail.style.display = 'block';\n");
        sb.append("                card.classList.add('expanded');\n");
        sb.append("            } else {\n");
        sb.append("                detail.style.display = 'none';\n");
        sb.append("                card.classList.remove('expanded');\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("    </script>\n");
        return sb.toString();
    }

    private int getPassDegree() {
        if (testResults.isEmpty()) return 0;
        long passed = testResults.stream().filter(r -> r.isSuccess()).count();
        return (int) (passed * 360 / testResults.size());
    }

    private String getMethodColor(String method) {
        switch (method.toUpperCase()) {
            case "GET": return "#2563eb";
            case "POST": return "#16a34a";
            case "PUT": return "#d97706";
            case "DELETE": return "#dc2626";
            case "PATCH": return "#9333ea";
            default: return "#6b7280";
        }
    }

    private String translateSeverity(String severity) {
        if (severity == null) return "未知";
        switch (severity.toUpperCase()) {
            case "HIGH": return "高";
            case "MEDIUM": return "中";
            case "LOW": return "低";
            default: return severity;
        }
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    /**
     * 格式化JSON字符串，添加缩进
     */
    private String formatJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return json;
        }
        try {
            // 尝试解析并重新格式化JSON
            String trimmed = json.trim();
            if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                return json; // 不是JSON格式，原样返回
            }
            
            // 简单的JSON格式化：添加换行和缩进
            StringBuilder formatted = new StringBuilder();
            int indent = 0;
            boolean inString = false;
            char prevChar = 0;
            
            for (char c : trimmed.toCharArray()) {
                if (c == '"' && prevChar != '\\') {
                    inString = !inString;
                }
                
                if (!inString) {
                    if (c == '{' || c == '[') {
                        formatted.append(c);
                        formatted.append('\n');
                        indent++;
                        for (int i = 0; i < indent; i++) {
                            formatted.append("  ");
                        }
                    } else if (c == '}' || c == ']') {
                        formatted.append('\n');
                        indent--;
                        for (int i = 0; i < indent; i++) {
                            formatted.append("  ");
                        }
                        formatted.append(c);
                    } else if (c == ',') {
                        formatted.append(c);
                        formatted.append('\n');
                        for (int i = 0; i < indent; i++) {
                            formatted.append("  ");
                        }
                    } else if (c == ':') {
                        formatted.append(c);
                        formatted.append(' ');
                    } else if (!Character.isWhitespace(c)) {
                        formatted.append(c);
                    }
                } else {
                    formatted.append(c);
                }
                prevChar = c;
            }
            
            return formatted.toString();
        } catch (Exception e) {
            // 格式化失败，返回原字符串
            return json;
        }
    }
}
