package com.autotest.git;

import com.autotest.config.Config;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Git变更分析器 - 分析Git仓库中的代码变更，识别受影响的API接口
 */
public class GitDiffAnalyzer {

    private final Config config;
    
    // 保存最后一次分析的变更Java文件列表
    private List<String> lastChangedJavaFiles = new ArrayList<>();
    
    // 保存Service文件变更的方法名，格式: Map<Service文件名, Set<变更的方法名>>
    private Map<String, Set<String>> lastChangedServiceMethods = new HashMap<>();

    /** Spring MVC映射注解名称集合 */
    private static final Set<String> MAPPING_ANNOTATIONS = new HashSet<>();
    static {
        MAPPING_ANNOTATIONS.add("GetMapping");
        MAPPING_ANNOTATIONS.add("PostMapping");
        MAPPING_ANNOTATIONS.add("PutMapping");
        MAPPING_ANNOTATIONS.add("DeleteMapping");
        MAPPING_ANNOTATIONS.add("PatchMapping");
        MAPPING_ANNOTATIONS.add("RequestMapping");
    }

    /** 注解名称到HTTP方法的映射 */
    private static final java.util.Map<String, String> ANNOTATION_HTTP_METHOD_MAP = new java.util.HashMap<>();
    static {
        ANNOTATION_HTTP_METHOD_MAP.put("GetMapping", "GET");
        ANNOTATION_HTTP_METHOD_MAP.put("PostMapping", "POST");
        ANNOTATION_HTTP_METHOD_MAP.put("PutMapping", "PUT");
        ANNOTATION_HTTP_METHOD_MAP.put("DeleteMapping", "DELETE");
        ANNOTATION_HTTP_METHOD_MAP.put("PatchMapping", "PATCH");
        ANNOTATION_HTTP_METHOD_MAP.put("RequestMapping", "GET");
    }

    /** 匹配Java方法声明的正则表达式 */
    private static final Pattern METHOD_PATTERN = Pattern.compile(
            "^\\s*(?:public|private|protected|static|\\s)\\s+[\\w<>,\\[\\]\\s]+\\s+(\\w+)\\s*\\(",
            Pattern.MULTILINE
    );

    /** 匹配注解的正则表达式 */
    private static final Pattern ANNOTATION_PATTERN = Pattern.compile(
            "@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)\\s*\\([^)]*\\)|" +
            "@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)\\s*$",
            Pattern.MULTILINE
    );

    /** 匹配注解value属性的正则表达式 */
    private static final Pattern ANNOTATION_VALUE_PATTERN = Pattern.compile(
            "(?:value\\s*=\\s*)?[\"']([^\"']+)[\"']"
    );

    /** 匹配RequestMapping中method属性的正则表达式 */
    private static final Pattern REQUEST_METHOD_PATTERN = Pattern.compile(
            "method\\s*=\\s*RequestMethod\\.(GET|POST|PUT|DELETE|PATCH)"
    );

    /** 日期格式（支持 yyyy-MM-dd） */
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    static {
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public GitDiffAnalyzer(Config config) {
        this.config = config;
    }

    /**
     * 分析Git变更，返回变更的接口列表
     *
     * @return 变更接口列表
     */
    public List<ChangedEndpoint> analyzeChanges() {
        List<ChangedEndpoint> endpoints = new ArrayList<>();

        try {
            Repository repository = openRepository();
            Git git = new Git(repository);

            // 确定比较范围
            ObjectId oldCommit = resolveBaseCommit(git, repository);
            ObjectId newCommit = resolveNewCommit(repository);

            if (oldCommit == null || newCommit == null) {
                System.err.println("Warning: Could not resolve commits for diff");
                git.close();
                repository.close();
                return endpoints;
            }

            // 获取变更文件列表
            List<DiffEntry> diffs = getDiffEntries(git, oldCommit, newCommit);

            // 过滤出Java文件（包括Controller和Service、Repository等）
            List<DiffEntry> javaDiffs = diffs.stream()
                    .filter(d -> d.getNewPath() != null && d.getNewPath().endsWith(".java") ||
                                 d.getOldPath() != null && d.getOldPath().endsWith(".java"))
                    .collect(Collectors.toList());
            
            // 过滤出Java Controller文件（用于提取接口定义）
            List<DiffEntry> controllerDiffs = javaDiffs.stream()
                    .filter(d -> isControllerFile(d.getNewPath()) || isControllerFile(d.getOldPath()))
                    .collect(Collectors.toList());
            
            // 保存所有变更的Java文件路径（用于查找依赖的Controller）
            // 只保留Service/ServiceImpl类，忽略DTO/VO/entity/util/job等
            List<String> allChangedJavaFiles = new ArrayList<>();
            for (DiffEntry diff : javaDiffs) {
                String filePath = diff.getChangeType() == DiffEntry.ChangeType.DELETE
                        ? diff.getOldPath()
                        : diff.getNewPath();
                if (!allChangedJavaFiles.contains(filePath) && isServiceFile(filePath)) {
                    allChangedJavaFiles.add(filePath);
                }
            }
            // 保存到类的成员变量，供后续使用
            this.lastChangedJavaFiles = allChangedJavaFiles;
            
            // 提取Service文件中变更的方法名
            extractChangedServiceMethods(git, repository, javaDiffs, oldCommit, newCommit);

            // 分析每个变更的Controller文件，提取受影响的接口
            for (DiffEntry diff : controllerDiffs) {
                String filePath = diff.getChangeType() == DiffEntry.ChangeType.DELETE
                        ? diff.getOldPath()
                        : diff.getNewPath();

                // 获取变更的方法信息
                List<ChangedEndpoint> fileEndpoints = extractChangedMethods(
                        git, repository, diff, oldCommit, newCommit);

                if (fileEndpoints.isEmpty()) {
                    // 如果无法提取具体方法，则将整个文件作为一个变更记录
                    ChangedEndpoint endpoint = new ChangedEndpoint();
                    endpoint.setFilePath(filePath);
                    endpoint.setChangeType(diff.getChangeType().name());
                    endpoint.setNewEndpoint(diff.getChangeType() == DiffEntry.ChangeType.ADD);
                    endpoint.setDeletedEndpoint(diff.getChangeType() == DiffEntry.ChangeType.DELETE);
                    endpoints.add(endpoint);
                } else {
                    endpoints.addAll(fileEndpoints);
                }
            }

            git.close();
            repository.close();

        } catch (Exception e) {
            System.err.println("Error analyzing git changes: " + e.getMessage());
            e.printStackTrace();
        }

        return endpoints;
    }

    /**
     * 打开Git仓库
     */
    private Repository openRepository() throws IOException {
        File gitDir = new File(config.getGitPath());
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        return builder.setGitDir(new File(gitDir, ".git"))
                .readEnvironment()
                .findGitDir()
                .build();
    }

    /**
     * 解析基准commit
     * 支持以下格式：
     * - 日期格式: "2024-01-01" (从该日期开始的变更)
     * - Git引用: "HEAD~5", "main", commit hash 等
     * - 分支名: 通过 config.getBranch() 获取
     */
    private ObjectId resolveBaseCommit(Git git, Repository repository) throws IOException {
        String since = config.getSince();

        if (since != null && !since.isEmpty()) {
            // 尝试解析为日期格式 (yyyy-MM-dd)
            if (since.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return resolveCommitByDate(git, repository, since);
            }

            // 尝试直接解析为Git引用 (如 HEAD~5, commit hash, 分支名)
            try {
                ObjectId resolved = repository.resolve(since);
                if (resolved != null) {
                    return resolved;
                }
            } catch (IOException e) {
                System.err.println("Warning: Could not resolve --since as git ref: " + since);
            }
        }

        // 回退到分支名
        String branch = config.getBranch();
        if (branch != null && !branch.isEmpty()) {
            try {
                ObjectId resolved = repository.resolve(branch);
                if (resolved != null) {
                    return resolved;
                }
            } catch (IOException e) {
                System.err.println("Warning: Could not resolve branch: " + branch);
            }
        }

        // 最后回退到 HEAD~1 (与上一次commit比较)
        try {
            return repository.resolve("HEAD~1");
        } catch (IOException e) {
            System.err.println("Warning: Could not resolve HEAD~1");
            return null;
        }
    }

    /**
     * 根据日期解析基准commit
     * 查找指定日期之后最早的commit作为基准点
     *
     * @param dateStr 日期字符串，格式 yyyy-MM-dd
     */
    private ObjectId resolveCommitByDate(Git git, Repository repository, String dateStr) throws IOException {
        try {
            Date sinceDate = DATE_FORMAT.parse(dateStr);

            Iterable<RevCommit> log = git.log()
                    .add(repository.resolve("HEAD"))
                    .call();

            ObjectId baseCommit = null;
            for (RevCommit commit : log) {
                Date commitDate = new Date(commit.getCommitTime() * 1000L);
                if (commitDate.before(sinceDate)) {
                    // 找到指定日期之前的最后一个commit作为基准
                    baseCommit = commit.getId();
                    break;
                }
            }

            if (baseCommit == null) {
                // 如果所有commit都在指定日期之后，使用最早的commit的父commit
                RevCommit firstCommit = null;
                for (RevCommit commit : log) {
                    firstCommit = commit;
                }
                if (firstCommit != null && firstCommit.getParentCount() > 0) {
                    baseCommit = firstCommit.getParent(0).getId();
                }
            }

            return baseCommit;
        } catch (Exception e) {
            System.err.println("Warning: Failed to parse date '" + dateStr + "': " + e.getMessage());
            return null;
        }
    }

    /**
     * 解析最新commit (HEAD)
     */
    private ObjectId resolveNewCommit(Repository repository) throws IOException {
        return repository.resolve("HEAD");
    }

    /**
     * 获取两个commit之间的diff条目
     */
    private List<DiffEntry> getDiffEntries(Git git, ObjectId oldCommit, ObjectId newCommit) throws IOException {
        try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            diffFormatter.setRepository(git.getRepository());
            diffFormatter.setDiffAlgorithm(new HistogramDiff());
            return diffFormatter.scan(oldCommit, newCommit);
        }
    }

    /**
     * 从diff的EditList中提取变更的方法块，识别哪些方法被新增或修改
     *
     * @param git Git实例
     * @param repository 仓库实例
     * @param diffEntry diff条目
     * @param oldCommit 旧commit
     * @param newCommit 新commit
     * @return 该文件中变更的接口列表
     */
    private List<ChangedEndpoint> extractChangedMethods(Git git, Repository repository,
                                                         DiffEntry diffEntry,
                                                         ObjectId oldCommit,
                                                         ObjectId newCommit) throws IOException {
        List<ChangedEndpoint> endpoints = new ArrayList<>();

        String filePath = diffEntry.getChangeType() == DiffEntry.ChangeType.DELETE
                ? diffEntry.getOldPath()
                : diffEntry.getNewPath();

        // 获取新版本的文件内容（用于分析注解和方法）
        String newContent = "";
        if (diffEntry.getChangeType() != DiffEntry.ChangeType.DELETE) {
            newContent = getFileContentAtCommit(repository, filePath, newCommit.getName());
        }

        // 获取旧版本的文件内容
        String oldContent = "";
        if (diffEntry.getChangeType() != DiffEntry.ChangeType.ADD) {
            oldContent = getFileContentAtCommit(repository,
                    diffEntry.getOldPath(), oldCommit.getName());
        }

        // 获取详细的diff编辑列表
        EditList editList = getEditList(repository, diffEntry, oldCommit, newCommit);
        if (editList == null || editList.isEmpty()) {
            return endpoints;
        }

        // 从新旧文件内容中提取所有映射方法及其位置
        List<MethodInfo> newMethods = extractMappingMethods(newContent);
        List<MethodInfo> oldMethods = extractMappingMethods(oldContent);

        // 确定哪些方法在变更范围内
        Set<String> changedMethodNames = new HashSet<>();

        for (Edit edit : editList) {
            int beginA = edit.getBeginA();
            int endA = edit.getEndA();
            int beginB = edit.getBeginB();
            int endB = edit.getEndB();

            // 检查新内容中是否有映射方法在变更范围内
            for (MethodInfo method : newMethods) {
                if (isMethodInEditRange(method, beginB, endB)) {
                    changedMethodNames.add(method.name);
                }
            }

            // 检查旧内容中是否有映射方法在变更范围内
            for (MethodInfo method : oldMethods) {
                if (isMethodInEditRange(method, beginA, endA)) {
                    changedMethodNames.add(method.name);
                }
            }
        }

        // 为每个变更的方法创建ChangedEndpoint
        for (MethodInfo method : newMethods) {
            if (changedMethodNames.contains(method.name)) {
                ChangedEndpoint endpoint = new ChangedEndpoint();
                endpoint.setFilePath(filePath);
                endpoint.setChangeType(diffEntry.getChangeType().name());
                endpoint.setMethodName(method.name);
                endpoint.setHttpMethod(method.httpMethod);
                endpoint.setUrlPath(method.path);
                endpoint.setNewEndpoint(!oldMethods.stream()
                        .anyMatch(m -> m.name.equals(method.name)));
                endpoint.setDeletedEndpoint(false);
                endpoints.add(endpoint);
            }
        }

        // 检查被删除的方法
        for (MethodInfo method : oldMethods) {
            if (changedMethodNames.contains(method.name)
                    && !newMethods.stream().anyMatch(m -> m.name.equals(method.name))) {
                ChangedEndpoint endpoint = new ChangedEndpoint();
                endpoint.setFilePath(filePath);
                endpoint.setChangeType("DELETE");
                endpoint.setMethodName(method.name);
                endpoint.setHttpMethod(method.httpMethod);
                endpoint.setUrlPath(method.path);
                endpoint.setNewEndpoint(false);
                endpoint.setDeletedEndpoint(true);
                endpoints.add(endpoint);
            }
        }

        return endpoints;
    }

    /**
     * 提取Service文件中变更的方法名
     */
    private void extractChangedServiceMethods(Git git, Repository repository,
                                              List<DiffEntry> javaDiffs,
                                              ObjectId oldCommit, ObjectId newCommit) {
        lastChangedServiceMethods.clear();
        
        for (DiffEntry diff : javaDiffs) {
            String filePath = diff.getChangeType() == DiffEntry.ChangeType.DELETE
                    ? diff.getOldPath()
                    : diff.getNewPath();
            
            // 只处理Service文件
            if (!isServiceFile(filePath)) {
                continue;
            }
            
            try {
                // 获取新旧内容
                String newContent = "";
                if (diff.getChangeType() != DiffEntry.ChangeType.DELETE) {
                    newContent = getFileContentAtCommit(repository, filePath, newCommit.getName());
                }
                
                String oldContent = "";
                if (diff.getChangeType() != DiffEntry.ChangeType.ADD) {
                    oldContent = getFileContentAtCommit(repository, diff.getOldPath(), oldCommit.getName());
                }
                
                // 获取diff编辑列表
                EditList editList = getEditList(repository, diff, oldCommit, newCommit);
                if (editList == null || editList.isEmpty()) {
                    continue;
                }
                
                // 提取所有方法
                Set<String> changedMethods = new HashSet<>();
                
                // 从新旧内容中提取所有方法
                List<JavaMethod> allMethods = extractAllJavaMethods(newContent);
                List<JavaMethod> oldMethods = extractAllJavaMethods(oldContent);
                
                // 确定哪些方法在变更范围内
                // 注意：检查edit范围是否落在方法体内（而非仅检查方法定义行）
                for (Edit edit : editList) {
                    int beginB = edit.getBeginB();
                    int endB = edit.getEndB();
                    int beginA = edit.getBeginA();
                    int endA = edit.getEndA();
                    
                    // 检查新方法：edit的行范围是否与方法体重叠
                    for (JavaMethod method : allMethods) {
                        if (method.isPrivate) continue; // 跳过private方法
                        // edit范围 [beginB, endB) 与方法体范围 [startLine, endLine] 重叠
                        if (endB > method.startLine && beginB <= method.endLine) {
                            changedMethods.add(method.name);
                        }
                    }
                    
                    // 检查旧方法
                    for (JavaMethod method : oldMethods) {
                        if (method.isPrivate) continue; // 跳过private方法
                        if (endA > method.startLine && beginA <= method.endLine) {
                            changedMethods.add(method.name);
                        }
                    }
                }
                
                if (!changedMethods.isEmpty()) {
                    lastChangedServiceMethods.put(filePath, changedMethods);
                }
                
            } catch (Exception e) {
                System.err.println("Error extracting methods from " + filePath + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * 从Java源码中提取所有方法定义（包括方法体范围）
     */
    private List<JavaMethod> extractAllJavaMethods(String source) {
        List<JavaMethod> methods = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return methods;
        }
        
        String[] lines = source.split("\n");
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            
            // 只有方法定义行才会被处理
            if (isMethodDefinitionLine(line)) {
                String methodName = extractMethodNameFromLine(line);
                boolean isPrivate = line.startsWith("private ");
                if (methodName != null && isValidMethodName(methodName)) {
                    JavaMethod method = new JavaMethod();
                    method.startLine = i + 1;
                    method.name = methodName;
                    method.isPrivate = isPrivate;
                    
                    // 计算方法结束行（通过括号匹配）
                    int braceCount = 0;
                    boolean foundOpenBrace = false;
                    for (int j = i; j < lines.length; j++) {
                        for (char c : lines[j].toCharArray()) {
                            if (c == '{') {
                                braceCount++;
                                foundOpenBrace = true;
                            } else if (c == '}') {
                                braceCount--;
                            }
                        }
                        if (foundOpenBrace && braceCount == 0) {
                            method.endLine = j + 1;
                            break;
                        }
                    }
                    if (method.endLine == 0) {
                        method.endLine = i + 1; // fallback
                    }
                    
                    methods.add(method);
                }
            }
        }
        
        return methods;
    }
    
    /**
     * 判断是否像方法定义行
     * 方法定义必须满足：
     * 1. 以访问修饰符开头（public/protected/private）
     * 2. 不包含赋值运算符 (=)
     * 3. 包含方法名后跟括号
     * 4. 不是注解、不是字段、不是方法调用
     */
    private boolean isMethodDefinitionLine(String line) {
        if (line.isEmpty()) return false;
        
        // 跳过注释和注解
        if (line.startsWith("//") || line.startsWith("/*") || line.startsWith("*") || line.startsWith("@")) {
            return false;
        }
        
        // 方法定义必须以访问修饰符开头
        if (!line.startsWith("public ") && !line.startsWith("protected ") && !line.startsWith("private ")) {
            return false;
        }
        
        // 跳过包含赋值运算符的行（这是方法调用，不是定义）
        if (line.contains("=")) {
            return false;
        }
        
        // 跳过字段定义（以分号结尾且没有括号）
        if (line.endsWith(";")) {
            return false;
        }
        
        // 跳过lambda表达式
        if (line.contains("->")) {
            return false;
        }
        
        // 必须包含括号
        if (!line.contains("(") || !line.contains(")")) {
            return false;
        }
        
        // 方法名必须以小写字母开头（构造函数以大写开头，这里也接受）
        // 检查是否有有效的方法名
        int parenOpen = line.indexOf('(');
        if (parenOpen > 0) {
            String beforeParen = line.substring(0, parenOpen).trim();
            String[] words = beforeParen.split("\\s+");
            if (words.length >= 1) {
                String lastWord = words[words.length - 1];
                // 检查最后一个单词是否是有效的方法名
                if (isValidMethodName(lastWord)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * 从一行代码中提取方法名
     */
    private String extractMethodNameFromLine(String line) {
        // 移除注释部分
        int commentIndex = line.indexOf("//");
        if (commentIndex > 0) {
            line = line.substring(0, commentIndex);
        }
        
        // 查找括号的位置
        int parenOpen = line.indexOf('(');
        if (parenOpen <= 0) return null;
        
        // 获取括号前的内容
        String beforeParen = line.substring(0, parenOpen).trim();
        
        // 分割成单词
        String[] words = beforeParen.split("\\s+");
        
        // 方法名是最后一个单词
        if (words.length > 0) {
            String lastWord = words[words.length - 1];
            if (isValidMethodName(lastWord)) {
                return lastWord;
            }
        }
        
        return null;
    }
    
    /**
     * 判断是否像有效的方法名
     * 方法名规则：
     * 1. 必须以小写字母开头（常规方法）
     * 2. 或者以大写字母开头（构造函数）
     * 3. 只包含字母、数字
     * 4. 最小长度2个字符
     */
    private boolean isValidMethodName(String name) {
        if (name == null || name.isEmpty()) return false;
        if (name.length() < 2) return false;
        
        // 不应该包含特殊字符
        if (!name.matches("[a-zA-Z][a-zA-Z0-9]*")) return false;
        
        // 不应该是泛型类型参数
        if (name.startsWith("<") || name.endsWith(">")) return false;
        
        // 不应该以大写字母开头后跟小写（这通常是类型名）
        // 但构造函数可以，所以需要检查是否是类名
        
        return true;
    }
    
    /**
     * 简单提取方法名 - 从包含括号和方法名的行中提取（旧方法，保留备用）
     */
    private String extractMethodNameOld(String line) {
        // 移除访问修饰符
        line = line.replaceAll("(public|protected|private)\\s+", "");
        // 移除返回类型
        line = line.replaceAll("(static\\s+)?(final\\s+)?[^\\s(]+$", "");
        // 移除空格和括号
        line = line.trim().replaceAll("\\s*\\([^)]*\\).*", "");
        
        if (line.matches("\\w+")) {
            return line;
        }
        return null;
    }
    
    /**
     * 简单的Java方法信息
     */
    private static class JavaMethod {
        int startLine;
        int endLine;
        String name;
        boolean isPrivate;
    }

    /**
     * 获取两个commit之间某个文件的详细EditList
     */
    private EditList getEditList(Repository repository, DiffEntry diffEntry,
                                  ObjectId oldCommit, ObjectId newCommit) throws IOException {
        try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            diffFormatter.setRepository(repository);
            diffFormatter.setDiffAlgorithm(new HistogramDiff());
            return diffFormatter.toFileHeader(diffEntry).toEditList();
        }
    }

    /**
     * 从Java源码中提取所有带有Spring MVC映射注解的方法信息
     *
     * @param source Java源码内容
     * @return 方法信息列表
     */
    private List<MethodInfo> extractMappingMethods(String source) {
        List<MethodInfo> methods = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return methods;
        }

        String[] lines = source.split("\n");
        String currentAnnotation = null;
        String currentAnnotationFull = null;
        int annotationStartLine = -1;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            // 检查是否是映射注解
            Matcher annotationMatcher = ANNOTATION_PATTERN.matcher(line);
            if (annotationMatcher.find()) {
                // 如果注解跨多行，需要累积
                if (line.contains("(") && !line.contains(")")) {
                    // 注解开始但未结束，累积后续行
                    StringBuilder annotationBuilder = new StringBuilder(line);
                    int j = i + 1;
                    while (j < lines.length && !annotationBuilder.toString().contains(")")) {
                        annotationBuilder.append(" ").append(lines[j].trim());
                        j++;
                    }
                    currentAnnotationFull = annotationBuilder.toString();
                    currentAnnotation = extractAnnotationName(currentAnnotationFull);
                    annotationStartLine = i;
                    i = j - 1; // 跳过已处理的行
                } else {
                    currentAnnotationFull = line;
                    currentAnnotation = extractAnnotationName(line);
                    annotationStartLine = i;
                }
                continue;
            }

            // 如果之前有注解，检查下一行是否是方法声明
            if (currentAnnotation != null) {
                Matcher methodMatcher = METHOD_PATTERN.matcher(line);
                if (methodMatcher.find()) {
                    String methodName = methodMatcher.group(1);
                    String httpMethod = resolveHttpMethodFromAnnotation(currentAnnotationFull);
                    String path = extractPathFromAnnotation(currentAnnotationFull);

                    MethodInfo methodInfo = new MethodInfo();
                    methodInfo.name = methodName;
                    methodInfo.httpMethod = httpMethod;
                    methodInfo.path = path;
                    methodInfo.startLine = annotationStartLine;
                    methodInfo.endLine = i;
                    methods.add(methodInfo);

                    currentAnnotation = null;
                    currentAnnotationFull = null;
                    annotationStartLine = -1;
                } else if (!line.isEmpty() && !line.startsWith("@") && !line.startsWith("//")
                        && !line.startsWith("/*") && !line.startsWith("*")) {
                    // 遇到非注解、非注释、非空行，且不是方法声明，重置
                    if (!line.contains("(")) {
                        currentAnnotation = null;
                        currentAnnotationFull = null;
                        annotationStartLine = -1;
                    }
                }
            }
        }

        return methods;
    }

    /**
     * 从注解字符串中提取注解名称
     */
    private String extractAnnotationName(String annotationStr) {
        Matcher matcher = Pattern.compile("@(\\w+)").matcher(annotationStr);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    /**
     * 根据注解字符串解析HTTP方法
     */
    private String resolveHttpMethodFromAnnotation(String annotationStr) {
        // 先检查 RequestMapping 中是否有 method 属性
        if (annotationStr.contains("RequestMapping")) {
            Matcher methodMatcher = REQUEST_METHOD_PATTERN.matcher(annotationStr);
            if (methodMatcher.find()) {
                return methodMatcher.group(1);
            }
            return "GET"; // RequestMapping 默认GET
        }

        // 其他特定映射注解
        for (java.util.Map.Entry<String, String> entry : ANNOTATION_HTTP_METHOD_MAP.entrySet()) {
            if (annotationStr.contains(entry.getKey()) && !entry.getKey().equals("RequestMapping")) {
                return entry.getValue();
            }
        }

        return "GET";
    }

    /**
     * 从注解字符串中提取路径
     */
    private String extractPathFromAnnotation(String annotationStr) {
        Matcher valueMatcher = ANNOTATION_VALUE_PATTERN.matcher(annotationStr);
        if (valueMatcher.find()) {
            return valueMatcher.group(1);
        }
        return "";
    }

    /**
     * 判断方法是否在编辑范围内
     */
    private boolean isMethodInEditRange(MethodInfo method, int editBegin, int editEnd) {
        return method.startLine >= editBegin && method.startLine <= editEnd
                || method.endLine >= editBegin && method.endLine <= editEnd
                || method.startLine <= editBegin && method.endLine >= editEnd;
    }

    /**
     * 判断文件是否为Service相关文件（Service接口或ServiceImpl实现类）
     * 只有Service变更才需要追溯到关联的Controller
     *
     * @param path 文件路径
     * @return 是否为Service文件
     */
    private boolean isServiceFile(String path) {
        if (path == null || !path.endsWith(".java")) return false;
        // 提取文件名（不含路径和.java后缀）
        String name = path.substring(0, path.length() - 5);
        int lastSlash = Math.max(name.lastIndexOf("/"), name.lastIndexOf("\\"));
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        return name.endsWith("Service") || name.endsWith("ServiceImpl");
    }

    /**
     * 增强判断逻辑：检查文件是否为Controller文件
     * 除了检查文件名，还可以检查文件内容是否包含 @RestController 或 @Controller 注解
     *
     * @param path 文件路径
     * @return 是否为Controller文件
     */
    private boolean isControllerFile(String path) {
        if (path == null) return false;
        // 检查文件名是否以Controller相关后缀结尾
        if (path.endsWith(".java")) {
            String name = path.substring(0, path.length() - 5);
            int lastSlash = Math.max(name.lastIndexOf("/"), name.lastIndexOf("\\"));
            if (lastSlash >= 0) {
                name = name.substring(lastSlash + 1);
            }
            if (name.endsWith("Controller") || name.endsWith("Resource") || 
                name.endsWith("Api") || name.endsWith("Handler") ||
                name.endsWith("Endpoint") || name.endsWith("Rest")) {
                return true;
            }
        }
        // 文件名不包含Controller但路径在controller包下
        if (path.endsWith(".java") && (path.contains("/controller/") || path.contains("\\controller\\"))) {
            return true;
        }
        return false;
    }

    /**
     * 检查文件内容是否包含 @RestController 或 @Controller 注解
     *
     * @param repository Git仓库
     * @param filePath 文件路径
     * @param commitRef commit引用
     * @return 是否为Controller文件
     */
    private boolean isControllerFileByContent(Repository repository, String filePath, String commitRef) {
        try {
            String content = getFileContentAtCommit(repository, filePath, commitRef);
            if (content == null || content.isEmpty()) {
                return false;
            }
            return content.contains("@RestController") || content.contains("@Controller");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取指定范围内的commit列表
     * 根据 --since 参数（日期格式）过滤commit范围
     * 根据 --author 参数过滤commit作者
     *
     * @return commit列表
     */
    public List<RevCommit> getCommitRange() {
        List<RevCommit> commits = new ArrayList<>();
        try {
            Repository repository = openRepository();
            Git git = new Git(repository);

            // 获取日志命令
            Iterable<RevCommit> log = git.log().add(repository.resolve("HEAD")).call();

            String author = config.getAuthor();
            String since = config.getSince();

            // 解析 --since 参数
            Date sinceDate = null;
            if (since != null && !since.isEmpty()) {
                if (since.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    try {
                        sinceDate = DATE_FORMAT.parse(since);
                    } catch (Exception e) {
                        System.err.println("Warning: Invalid date format for --since: " + since);
                    }
                }
            }

            for (RevCommit commit : log) {
                // 作者过滤
                if (author != null && !author.isEmpty()) {
                    String authorName = commit.getAuthorIdent().getName();
                    String authorEmail = commit.getAuthorIdent().getEmailAddress();
                    boolean match = authorName.toLowerCase().contains(author.toLowerCase()) ||
                                   authorEmail.toLowerCase().contains(author.toLowerCase());
                    if (!match) {
                        continue;
                    }
                }
                
                // 如果配置了起始时间，在此过滤
                // 注意：包含起始日期当天的提交（sinceDate当天及之后的提交）
                if (sinceDate != null) {
                    Date commitDate = new Date(commit.getCommitTime() * 1000L);
                    // 将sinceDate设置为当天的开始时间（00:00:00）
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(sinceDate);
                    cal.set(Calendar.HOUR_OF_DAY, 0);
                    cal.set(Calendar.MINUTE, 0);
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    Date sinceStartOfDay = cal.getTime();
                    
                    if (commitDate.before(sinceStartOfDay)) {
                        break; // commit按时间倒序排列，遇到更早的就可以停止
                    }
                }
                commits.add(commit);
            }

            git.close();
            repository.close();
        } catch (Exception e) {
            System.err.println("Error getting commit range: " + e.getMessage());
        }
        return commits;
    }

    /**
     * 解析日期字符串为时间戳
     *
     * @param dateStr 日期字符串，格式 yyyy-MM-dd
     * @return 时间戳（毫秒）
     */
    private long parseDate(String dateStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            return sdf.parse(dateStr).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 获取指定commit的文件内容
     *
     * @param filePath 文件路径（相对于仓库根目录）
     * @param commitRef commit引用（commit hash 或分支名等）
     * @return 文件内容字符串，如果文件不存在则返回空字符串
     */
    public String getFileContentAtCommit(String filePath, String commitRef) {
        try {
            Repository repository = openRepository();
            String content = getFileContentAtCommit(repository, filePath, commitRef);
            repository.close();
            return content;
        } catch (Exception e) {
            System.err.println("Error reading file at commit: " + e.getMessage());
            return "";
        }
    }
    
    /**
     * 获取变更的Service方法信息
     * @return Map<Service文件名, Set<变更的方法名>>
     */
    public Map<String, Set<String>> getLastChangedServiceMethods() {
        return lastChangedServiceMethods;
    }
    
    /**
     * 检查Controller是否调用了Service的变更方法
     * @param controllerPath Controller文件路径
     * @param servicePath Service文件路径
     * @param serviceMethods Service中变更的方法名集合（会被修改为实际调用的方法）
     * @return 是否调用了变更方法
     */
    public boolean checkControllerCallsService(String controllerPath, String servicePath, Set<String> serviceMethods) {
        try {
            File gitDir = new File(config.getGitPath());
            File controllerFile;
            if (new File(controllerPath).isAbsolute()) {
                controllerFile = new File(controllerPath);
            } else {
                controllerFile = new File(gitDir, controllerPath);
            }
            
            if (!controllerFile.exists()) {
                return false;
            }
            
            String content = readFileContent(controllerFile);
            
            // 获取Service的文件名
            String serviceFileName = new File(servicePath).getName().replace(".java", "");
            String interfaceName = null;
            
            // 如果是Impl类，尝试获取接口名
            if (serviceFileName.endsWith("Impl")) {
                interfaceName = serviceFileName.substring(0, serviceFileName.length() - 4);
            }
            
            // 生成所有可能的变量名形式
            List<String> possibleVarNames = new ArrayList<>();
            possibleVarNames.add(serviceFileName);
            possibleVarNames.add(Character.toLowerCase(serviceFileName.charAt(0)) + serviceFileName.substring(1));
            if (interfaceName != null) {
                possibleVarNames.add(interfaceName);
                possibleVarNames.add(Character.toLowerCase(interfaceName.charAt(0)) + interfaceName.substring(1));
            }
            
            // 检查Controller是否调用了变更的方法
            Set<String> calledMethods = new HashSet<>();
            for (String methodName : serviceMethods) {
                for (String varName : possibleVarNames) {
                    String callPattern = varName + "." + methodName + "(";
                    if (content.contains(callPattern)) {
                        calledMethods.add(methodName);
                        break;
                    }
                }
            }
            
            // 更新serviceMethods为实际调用的方法
            if (!calledMethods.isEmpty()) {
                serviceMethods.clear();
                serviceMethods.addAll(calledMethods);
                return true;
            }
            
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 检查Controller中指定接口方法是否调用了Service的变更方法
     * @param controllerPath Controller文件路径
     * @param endpointPath 接口路径
     * @param servicePath Service文件路径
     * @param serviceMethods Service中变更的方法名集合（会被修改为实际调用的方法）
     * @return 是否调用了变更方法
     */
    public boolean checkEndpointCallsService(String controllerPath, String endpointPath, String servicePath, Set<String> serviceMethods) {
        try {
            File gitDir = new File(config.getGitPath());
            File controllerFile;
            if (new File(controllerPath).isAbsolute()) {
                controllerFile = new File(controllerPath);
            } else {
                controllerFile = new File(gitDir, controllerPath);
            }
            
            if (!controllerFile.exists()) {
                return false;
            }
            
            String content = readFileContent(controllerFile);
            
            // 提取指定接口的方法体
            List<MethodRange> apiMethods = extractApiMethods(content, endpointPath);
            if (apiMethods.isEmpty()) {
                return false;
            }
            
            // 获取Service的文件名
            String serviceFileName = new File(servicePath).getName().replace(".java", "");
            String interfaceName = null;
            
            // 如果是Impl类，尝试获取接口名
            if (serviceFileName.endsWith("Impl")) {
                interfaceName = serviceFileName.substring(0, serviceFileName.length() - 4);
            }
            
            // 生成所有可能的变量名形式
            List<String> possibleVarNames = new ArrayList<>();
            possibleVarNames.add(serviceFileName);
            possibleVarNames.add(Character.toLowerCase(serviceFileName.charAt(0)) + serviceFileName.substring(1));
            if (interfaceName != null) {
                possibleVarNames.add(interfaceName);
                possibleVarNames.add(Character.toLowerCase(interfaceName.charAt(0)) + interfaceName.substring(1));
            }
            
            // 检查接口方法体是否调用了变更的方法
            Set<String> calledMethods = new HashSet<>();
            for (MethodRange methodRange : apiMethods) {
                String methodBody = methodRange.body;
                for (String methodName : serviceMethods) {
                    for (String varName : possibleVarNames) {
                        String callPattern = varName + "." + methodName + "(";
                        if (methodBody.contains(callPattern)) {
                            calledMethods.add(methodName);
                            break;
                        }
                    }
                }
            }
            
            // 更新serviceMethods为实际调用的方法
            if (!calledMethods.isEmpty()) {
                serviceMethods.clear();
                serviceMethods.addAll(calledMethods);
                return true;
            }
            
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 检查Controller是否依赖某个Service（即Controller是否调用了Service中的方法）
     * @param controllerPath Controller文件路径（相对路径）
     * @param servicePath Service文件路径（相对路径）
     * @param serviceMethods Service中变更的方法名集合
     * @param endpointPath 当前接口的路径（如 /energyApi/model/saveModel）
     * @return 是否依赖
     */
    public boolean isControllerDependentOnService(String controllerPath, String servicePath, Set<String> serviceMethods, String endpointPath) {
        try {
            File gitDir = new File(config.getGitPath());
            // 处理controllerPath，可能是相对路径或绝对路径
            File controllerFile;
            if (new File(controllerPath).isAbsolute()) {
                controllerFile = new File(controllerPath);
            } else {
                controllerFile = new File(gitDir, controllerPath);
            }
            
            if (!controllerFile.exists()) {
                return false;
            }
            
            String content = readFileContent(controllerFile);
            
            // 获取Service的文件名
            String serviceFileName = new File(servicePath).getName().replace(".java", "");
            String interfaceName = null;
            
            // 如果是Impl类，尝试获取接口名
            if (serviceFileName.endsWith("Impl")) {
                interfaceName = serviceFileName.substring(0, serviceFileName.length() - 4);
            }
            
            // 检查Controller是否引用了这个Service（各种可能的变量名）
            boolean hasReference = false;
            String[] possibleNames = {serviceFileName};
            if (interfaceName != null) {
                possibleNames = new String[]{serviceFileName, interfaceName};
            }
            
            for (String name : possibleNames) {
                if (content.contains(name)) {
                    hasReference = true;
                    break;
                }
            }
            
            if (!hasReference) {
                return false;
            }
            
            // 提取Controller中所有接口方法
            List<MethodRange> apiMethods = extractApiMethods(content, endpointPath);

            // 检查匹配的接口方法中是否调用了变更的方法
            Set<String> calledMethods = new HashSet<>();
            for (MethodRange methodRange : apiMethods) {
                String methodBody = methodRange.body;

                for (String methodName : serviceMethods) {
                    for (String name : possibleNames) {
                        // 大驼峰变量名 (如: modelManageService)
                        String camelName = Character.toLowerCase(name.charAt(0)) + name.substring(1);

                        String[] callPatterns = {
                            name + "." + methodName + "(",
                            camelName + "." + methodName + "("
                        };

                        for (String pattern : callPatterns) {
                            if (methodBody.contains(pattern)) {
                                calledMethods.add(methodName);
                                break;
                            }
                        }
                    }
                }
            }
            
            // 如果找到了调用的方法，返回这些方法名
            if (!calledMethods.isEmpty()) {
                serviceMethods.clear();
                serviceMethods.addAll(calledMethods);
                return true;
            }
            
            return false;
        } catch (Exception e) {
            System.err.println("Error checking controller dependency: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 提取Controller中指定接口方法的代码范围
     * @param content Controller文件内容
     * @param endpointPath 接口路径（如 /energyApi/model/saveModel）
     * @return 只包含匹配接口的方法列表
     */
    private List<MethodRange> extractApiMethods(String content, String endpointPath) {
        List<MethodRange> methods = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return methods;
        }
        
        String[] lines = content.split("\n");
        int braceCount = 0;
        boolean inApiMethod = false;
        int methodStartLine = -1;
        StringBuilder methodBody = new StringBuilder();
        StringBuilder annotationBuffer = new StringBuilder();
        String matchedAnnotations = null;  // 保存匹配到的注解内容
        int classLevelBraceCount = 0;
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmedLine = line.trim();
            
            // 追踪类级别的括号（不在方法体内时）
            if (!inApiMethod) {
                for (char c : line.toCharArray()) {
                    if (c == '{') classLevelBraceCount++;
                    else if (c == '}') classLevelBraceCount--;
                }
            }
            
            // 如果已经在方法体内，继续收集
            if (inApiMethod) {
                methodBody.append(line).append("\n");
                for (char c : line.toCharArray()) {
                    if (c == '{') braceCount++;
                    else if (c == '}') braceCount--;
                }
                if (braceCount == 0 && methodBody.length() > 0) {
                    MethodRange mr = new MethodRange();
                    mr.startLine = methodStartLine;
                    mr.endLine = i;
                    mr.body = methodBody.toString();
                    methods.add(mr);
                    inApiMethod = false;
                    methodBody = new StringBuilder();
                    matchedAnnotations = null;
                }
                continue;
            }
            
            // 只在类体内部（classLevelBraceCount >= 1）收集方法注解
            if (classLevelBraceCount >= 1 && !trimmedLine.isEmpty() && trimmedLine.startsWith("@")) {
                annotationBuffer.append(trimmedLine).append(" ");
                
                if (isApiAnnotationLine(trimmedLine)) {
                    String fullAnnotation = annotationBuffer.toString();
                    if (annotationMatchesPath(fullAnnotation, endpointPath)) {
                        // 保存匹配到的注解，并记录起始位置
                        matchedAnnotations = annotationBuffer.toString();
                        methodStartLine = i;
                        methodBody = new StringBuilder();
                        braceCount = 0;
                    }
                }
                continue;
            }
            
            // 跳过空行
            if (trimmedLine.isEmpty()) {
                continue;
            }
            
            // 如果匹配了注解，当前非注解行是方法签名
            if (matchedAnnotations != null) {
                inApiMethod = true;
                // 只将匹配到的注解添加到方法体
                methodBody.append(matchedAnnotations).append("\n");
                methodBody.append(line).append("\n");
                for (char c : line.toCharArray()) {
                    if (c == '{') braceCount++;
                    else if (c == '}') braceCount--;
                }
                if (braceCount == 0 && methodBody.length() > 0) {
                    MethodRange mr = new MethodRange();
                    mr.startLine = methodStartLine;
                    mr.endLine = i;
                    mr.body = methodBody.toString();
                    methods.add(mr);
                    inApiMethod = false;
                    methodBody = new StringBuilder();
                    matchedAnnotations = null;
                }
                annotationBuffer = new StringBuilder();
                continue;
            }
            
            // 没有匹配注解的非注解行，清除缓冲区
            annotationBuffer = new StringBuilder();
        }
        
        return methods;
    }
    
    /**
     * 检查注解中是否包含目标路径
     * 支持多种匹配方式：
     * 1. 完整路径匹配
     * 2. 后缀匹配（如 /saveModel 匹配 /energyApi/model/saveModel）
     * 3. 路径变量匹配（如 /user/{id} 匹配 /user/123）
     */
    private boolean annotationMatchesPath(String annotation, String path) {
        if (annotation == null || path == null) return false;
        
        // 移除空格并转小写进行比较
        annotation = annotation.replaceAll("\\s+", "").toLowerCase();
        path = path.replaceAll("\\s+", "").toLowerCase();
        
        // 1. 直接包含检查（完整路径）
        if (annotation.contains(path)) {
            return true;
        }
        
        // 2. 后缀匹配（注解路径是完整路径的一部分）
        // 提取注解中的路径值
        java.util.regex.Pattern pathPattern = java.util.regex.Pattern.compile("value=\"([^\"]+)\"|\"([^\"]+)\"");
        java.util.regex.Matcher pathMatcher = pathPattern.matcher(annotation);
        while (pathMatcher.find()) {
            String annoPath = pathMatcher.group(1) != null ? pathMatcher.group(1) : pathMatcher.group(2);
            if (annoPath != null) {
                // 如果注解路径以 / 开头，检查是否是目标路径的后缀
                if (annoPath.startsWith("/")) {
                    // 完整匹配
                    if (path.equals(annoPath)) return true;
                    // 后缀匹配（如 /saveModel 匹配 /energyApi/model/saveModel）
                    if (path.endsWith(annoPath)) return true;
                    // 处理路径变量
                    if (annoPath.contains("{")) {
                        String regex = annoPath.replaceAll("\\{[^}]+\\}", "[^/]+");
                        if (path.matches(".*" + regex + "$") || java.util.regex.Pattern.matches(regex, path)) {
                            return true;
                        }
                    }
                }
            }
        }
        
        // 3. 简单包含检查（如果上面都失败）
        if (annotation.contains(path.replaceAll("/[^/]+/", "/")) || path.contains(annotation)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 判断是否包含Spring MVC接口注解
     */
    private boolean isApiAnnotationLine(String line) {
        line = line.trim();
        return line.contains("@RequestMapping") ||
               line.contains("@GetMapping") ||
               line.contains("@PostMapping") ||
               line.contains("@PutMapping") ||
               line.contains("@DeleteMapping") ||
               line.contains("@PatchMapping");
    }
    
    /**
     * 接口方法范围
     */
    private static class MethodRange {
        int startLine;
        int endLine;
        String body;
    }
    
    /**
     * 获取指定commit的文件内容（内部实现）
     *
     * @param repository Git仓库
     * @param filePath 文件路径
     * @param commitRef commit引用
     * @return 文件内容
     */
    private String getFileContentAtCommit(Repository repository, String filePath, String commitRef) throws IOException {
        ObjectId commitId = repository.resolve(commitRef);
        if (commitId == null) {
            System.err.println("Warning: Cannot resolve commit ref: " + commitRef);
            return "";
        }

        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(commitId);
            RevTree tree = commit.getTree();

            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(tree);
                treeWalk.setRecursive(true);
                treeWalk.setFilter(PathFilter.create(filePath));

                if (!treeWalk.next()) {
                    return ""; // 文件不存在于该commit中
                }

                ObjectId blobId = treeWalk.getObjectId(0);
                try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                    ObjectLoader loader = repository.open(blobId);
                    loader.copyTo(os);
                    return new String(os.toByteArray(), StandardCharsets.UTF_8);
                }
            }
        }
    }

    /**
     * 内部类：方法信息
     */
    private static class MethodInfo {
        String name;
        String httpMethod;
        String path;
        int startLine;
        int endLine;
    }

    /**
     * 获取所有修改的类，并追溯到相关的Controller
     * 规则：如果修改的是Service、Repository等非Controller类，
     * 则找到所有使用该类的Controller进行测试
     *
     * @return 变更端点列表，包含直接变更和依赖的Controller
     */
    public List<ChangedEndpoint> analyzeChangesWithDependents() {
        List<ChangedEndpoint> endpoints = analyzeChanges();

        if (endpoints.isEmpty() && lastChangedJavaFiles.isEmpty()) {
            return endpoints;
        }

        // 使用analyzeChanges中保存的所有变更Java文件列表

        // 扫描所有Controller，找到使用这些类的Controller
        List<String> dependentControllers = findDependentControllers(lastChangedJavaFiles);

        // 为依赖的Controller创建变更端点（去重）
        for (String controllerPath : dependentControllers) {
            // 检查是否已存在（比较文件名）
            boolean exists = false;
            String controllerFileName = new File(controllerPath).getName();
            for (ChangedEndpoint ep : endpoints) {
                if (ep.getFilePath() != null && 
                    new File(ep.getFilePath()).getName().equals(controllerFileName)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                ChangedEndpoint dep = new ChangedEndpoint();
                dep.setFilePath(controllerPath);
                dep.setChangeType("DEPENDENT");
                dep.setChangeDescription("Dependent on modified files: " + lastChangedJavaFiles);
                endpoints.add(dep);
            }
        }

        return endpoints;
    }

    /**
     * 扫描所有Controller文件，找到使用指定类的Controller
     *
     * @param modifiedClasses 修改的类文件路径列表
     * @return 依赖的Controller文件路径列表
     */
    private List<String> findDependentControllers(List<String> modifiedClasses) {
        List<String> controllers = new ArrayList<>();

        try {
            File gitDir = new File(config.getGitPath());
            File srcDir = new File(gitDir, "src");

            if (!srcDir.exists()) {
                return controllers;
            }

            // 遍历所有Controller文件
            scanForControllers(srcDir, modifiedClasses, controllers);

        } catch (Exception e) {
            System.err.println("Error finding dependent controllers: " + e.getMessage());
        }

        return controllers;
    }

    /**
     * 递归扫描目录查找Controller文件
     *
     * @param dir 扫描的目录
     * @param modifiedClasses 修改的类文件路径列表
     * @param controllers 发现的依赖Controller列表
     */
    private void scanForControllers(File dir, List<String> modifiedClasses, List<String> controllers) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                // 递归扫描子目录
                scanForControllers(file, modifiedClasses, controllers);
            } else if (isControllerFile(file.getName())) {
                // 检查这个Controller是否依赖修改的类
                try {
                    String content = readFileContent(file);
                    for (String modifiedClass : modifiedClasses) {
                        String className = pathToClassName(modifiedClass);
                        String simpleClassName = className.substring(className.lastIndexOf(".") + 1);
                        
                        // 检查类名本身
                        boolean matched = content.contains(className) || content.contains(simpleClassName);
                        
                        // 如果是Impl类，也检查对应的接口名（去掉Impl后缀）
                        String interfaceName = null;
                        if (!matched && simpleClassName.endsWith("Impl")) {
                            interfaceName = simpleClassName.substring(0, simpleClassName.length() - 4);
                            String interfaceFullName = className.substring(0, className.lastIndexOf(".")) + "." + interfaceName;
                            matched = content.contains(interfaceFullName) || content.contains(interfaceName);
                        }
                        
                        // 也检查小驼峰变量名
                        if (!matched) {
                            String camelName = Character.toLowerCase(simpleClassName.charAt(0)) + simpleClassName.substring(1);
                            matched = content.contains(camelName);
                            if (!matched && interfaceName != null) {
                                String camelInterfaceName = Character.toLowerCase(interfaceName.charAt(0)) + interfaceName.substring(1);
                                matched = content.contains(camelInterfaceName);
                            }
                        }
                        
                        if (matched) {
                            // 检查是否调用了变更的方法
                            boolean callsChangedMethod = checkCallsChangedMethods(content, modifiedClass, simpleClassName, interfaceName);
                            
                            if (callsChangedMethod) {
                                if (!controllers.contains(file.getPath())) {
                                    controllers.add(file.getPath());
                                }
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    // 忽略读取错误
                }
            }
        }
    }
    
    /**
     * 检查Controller是否调用了Service中变更的方法
     */
    private boolean checkCallsChangedMethods(String controllerContent, String servicePath, 
                                            String serviceName, String interfaceName) {
        // 获取变更的方法列表
        Set<String> changedMethods = lastChangedServiceMethods.get(servicePath);
        if (changedMethods == null || changedMethods.isEmpty()) {
            // 如果无法获取变更方法，保守地返回true（认为有关联）
            return true;
        }
        
        // 生成所有可能的变量名形式
        List<String> possibleVarNames = new ArrayList<>();
        // 原始类名
        possibleVarNames.add(serviceName);
        // 小驼峰形式
        possibleVarNames.add(Character.toLowerCase(serviceName.charAt(0)) + serviceName.substring(1));
        // 接口名
        if (interfaceName != null) {
            possibleVarNames.add(interfaceName);
            possibleVarNames.add(Character.toLowerCase(interfaceName.charAt(0)) + interfaceName.substring(1));
        }
        
        // 检查Controller是否调用了这些方法
        for (String methodName : changedMethods) {
            for (String varName : possibleVarNames) {
                String callPattern = varName + "." + methodName + "(";
                if (controllerContent.contains(callPattern)) {
                    return true;
                }
            }
        }
        
        return false;
    }

    /**
     * 读取文件内容
     *
     * @param file 文件
     * @return 文件内容字符串
     * @throws IOException IO异常
     */
    private String readFileContent(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    /**
     * 将文件路径转换为类名
     * 例如: com/example/service/UserService.java -> com.example.service.UserService
     *
     * @param filePath 文件路径
     * @return 类名
     */
    private String pathToClassName(String filePath) {
        String className = filePath.replace("\\", "/");
        if (className.contains("/src/main/java/")) {
            className = className.substring(className.indexOf("/src/main/java/") + 16);
        } else if (className.contains("/src/")) {
            className = className.substring(className.indexOf("/src/") + 5);
        }
        return className.substring(0, className.length() - 5).replace("/", ".");
    }
}
