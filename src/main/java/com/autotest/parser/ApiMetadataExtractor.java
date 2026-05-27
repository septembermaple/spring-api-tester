package com.autotest.parser;

import com.autotest.config.Config;
import com.autotest.git.ChangedEndpoint;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * API元数据提取器 - 使用JavaParser解析Spring Controller源码，提取API接口定义
 */
public class ApiMetadataExtractor {

    private final Config config;

    /** 需要识别的Spring MVC映射注解集合 */
    private static final Set<String> MAPPING_ANNOTATIONS = new HashSet<>(Arrays.asList(
            "GetMapping", "PostMapping", "PutMapping", "DeleteMapping",
            "PatchMapping", "RequestMapping"
    ));

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

    public ApiMetadataExtractor(Config config) {
        this.config = config;
    }

    /**
     * 从变更端点提取API元数据
     *
     * @param changed 变更端点
     * @return 接口定义列表
     */
    public List<EndpointDefinition> extractFromFile(ChangedEndpoint changed) {
        try {
            // 修复：检查filePath是否已经是完整路径
            String filePath = changed.getFilePath();
            File sourceFile = new File(filePath);
            
            // 如果不是完整路径（不存在），则拼接gitPath
            if (!sourceFile.exists() && !filePath.contains(":")) {
                filePath = Paths.get(config.getGitPath(), filePath).toString();
                sourceFile = new File(filePath);
            }

            if (!sourceFile.exists()) {
                System.err.println("Warning: Source file not found: " + filePath);
                return new ArrayList<>();
            }

            List<EndpointDefinition> result = parseControllerFile(sourceFile, changed);
            return result;
        } catch (Exception e) {
            System.err.println("Error parsing file " + changed.getFilePath() + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 从变更的接口列表中提取完整的API元数据
     *
     * @param changedEndpoints Git变更分析得到的变更接口列表
     * @return 完整的接口定义列表
     */
    public List<EndpointDefinition> extractEndpoints(List<ChangedEndpoint> changedEndpoints) {
        List<EndpointDefinition> definitions = new ArrayList<>();

        for (ChangedEndpoint changed : changedEndpoints) {
            try {
                // 修复：检查filePath是否已经是完整路径
                String filePath = changed.getFilePath();
                File sourceFile = new File(filePath);
                
                // 如果不是完整路径（不存在），则拼接gitPath
                if (!sourceFile.exists() && !filePath.contains(":")) {
                    filePath = Paths.get(config.getGitPath(), filePath).toString();
                    sourceFile = new File(filePath);
                }

                if (!sourceFile.exists()) {
                    System.err.println("Warning: Source file not found: " + filePath);
                    continue;
                }

                List<EndpointDefinition> fileEndpoints = parseControllerFile(sourceFile, changed);
                definitions.addAll(fileEndpoints);

            } catch (Exception e) {
                System.err.println("Error parsing file " + changed.getFilePath() + ": " + e.getMessage());
            }
        }

        return definitions;
    }

    /**
     * 解析单个Controller文件
     * 如果提供了changed参数且其中包含methodName，则只提取变更方法对应的接口定义
     *
     * @param sourceFile 源文件
     * @param changed 变更信息，可为null（表示提取所有接口）
     * @return 接口定义列表
     */
    private List<EndpointDefinition> parseControllerFile(File sourceFile, ChangedEndpoint changed) throws Exception {
        List<EndpointDefinition> endpoints = new ArrayList<>();

        FileInputStream in = new FileInputStream(sourceFile);
        ParseResult<CompilationUnit> parseResult = new JavaParser().parse(in);
        in.close();

        if (!parseResult.isSuccessful()) {
            System.err.println("Parse errors in " + sourceFile.getName() + ": " +
                    parseResult.getProblems().stream()
                            .map(p -> p.getMessage())
                            .collect(Collectors.joining(", ")));
            return endpoints;
        }

        Optional<CompilationUnit> optCu = parseResult.getResult();
        if (!optCu.isPresent()) {
            return endpoints;
        }

        CompilationUnit cu = optCu.get();

        // 提取类级别的RequestMapping
        String classLevelPath = extractClassLevelMapping(cu);
        String className = extractClassName(cu);

        // 确定是否需要过滤特定方法
        // DEPENDENT类型表示该文件是依赖项，需要提取所有接口
        boolean isDependent = (changed != null && "DEPENDENT".equals(changed.getChangeType()));
        String filterMethodName = isDependent ? null : ((changed != null) ? changed.getMethodName() : null);

        // 获取间接测试的原因
        String indirectReason = isDependent ? changed.getChangeDescription() : null;

        // 遍历方法，提取接口定义
        ControllerMethodVisitor visitor = new ControllerMethodVisitor(
                classLevelPath, className, filterMethodName, isDependent, indirectReason);
        visitor.visit(cu, endpoints);

        return endpoints;
    }

    /**
     * 提取类级别的@RequestMapping路径
     * 支持以下注解格式：
     * - @RequestMapping(value = "/api")
     * - @RequestMapping("/api")
     * - @RequestMapping(path = "/api")
     * - @GetMapping("/api") (类级别较少见但也支持)
     *
     * @param cu 编译单元
     * @return 路径前缀，如果没有则返回空字符串
     */
    private String extractClassLevelMapping(CompilationUnit cu) {
        Optional<ClassOrInterfaceDeclaration> classOpt = cu.findFirst(ClassOrInterfaceDeclaration.class);
        if (!classOpt.isPresent()) {
            return "";
        }

        ClassOrInterfaceDeclaration classDecl = classOpt.get();

        for (AnnotationExpr annotation : classDecl.getAnnotations()) {
            String annotationName = annotation.getNameAsString();
            if (MAPPING_ANNOTATIONS.contains(annotationName)) {
                String value = extractAnnotationValue(annotation);
                if (value != null && !value.isEmpty()) {
                    // 如果value包含多个路径，取第一个
                    if (value.startsWith("{") && value.endsWith("}")) {
                        value = value.substring(1, value.length() - 1).trim();
                    }
                    return value;
                }
            }
        }

        return "";
    }

    /**
     * 提取类名
     *
     * @param cu 编译单元
     * @return 类名，如果找不到则返回 "Unknown"
     */
    private String extractClassName(CompilationUnit cu) {
        Optional<ClassOrInterfaceDeclaration> classOpt = cu.findFirst(ClassOrInterfaceDeclaration.class);
        if (classOpt.isPresent()) {
            return classOpt.get().getNameAsString();
        }
        return cu.getPrimaryTypeName().orElse("Unknown");
    }

    /**
     * Controller方法访问器 - 遍历Controller中的方法并提取API定义
     */
    private static class ControllerMethodVisitor extends VoidVisitorAdapter<List<EndpointDefinition>> {

        private final String classLevelPath;
        private final String className;
        private final String filterMethodName;
        private final boolean isDependent;
        private final String indirectTestReason;

        ControllerMethodVisitor(String classLevelPath, String className, String filterMethodName) {
            this(classLevelPath, className, filterMethodName, false, null);
        }

        ControllerMethodVisitor(String classLevelPath, String className, String filterMethodName,
                                boolean isDependent, String indirectTestReason) {
            this.classLevelPath = classLevelPath;
            this.className = className;
            this.filterMethodName = filterMethodName;
            this.isDependent = isDependent;
            this.indirectTestReason = indirectTestReason;
        }

        @Override
        public void visit(MethodDeclaration method, List<EndpointDefinition> collector) {
            super.visit(method, collector);

            // 检查方法是否有Spring MVC映射注解
            Optional<AnnotationExpr> mappingAnnotation = method.getAnnotations().stream()
                    .filter(a -> MAPPING_ANNOTATIONS.contains(a.getNameAsString()))
                    .findFirst();

            if (!mappingAnnotation.isPresent()) {
                return;
            }

            // 如果设置了过滤方法名，只处理匹配的方法
            if (filterMethodName != null && !filterMethodName.isEmpty()
                    && !filterMethodName.equals(method.getNameAsString())) {
                return;
            }

            AnnotationExpr annotation = mappingAnnotation.get();

            // 解析HTTP方法
            String httpMethod = resolveHttpMethod(annotation);

            // 解析方法路径
            String methodPath = extractAnnotationValue(annotation);
            if (methodPath == null) {
                methodPath = "";
            }
            // 处理多路径情况，取第一个
            if (methodPath.startsWith("{") && methodPath.endsWith("}")) {
                methodPath = methodPath.substring(1, methodPath.length() - 1).trim();
            }

            // 组合完整URL路径
            String fullPath = combinePath(classLevelPath, methodPath);

            // 创建接口定义
            EndpointDefinition definition = new EndpointDefinition();
            definition.setControllerClass(className);
            definition.setMethodName(method.getNameAsString());
            definition.setHttpMethod(httpMethod);
            definition.setUrlPath(fullPath);
            definition.setReturnType(method.getType().asString());

            // 提取Javadoc注释作为接口描述
            String javadoc = extractJavadoc(method);
            definition.setDescription(javadoc);

            // 设置间接测试标记（如果是DEPENDENT类型）
            if (isDependent) {
                definition.setIndirectTest(true);
                definition.setIndirectTestReason(indirectTestReason);
            }

            // 解析请求参数
            List<EndpointDefinition.ParameterInfo> parameters = extractParameters(method);
            definition.setParameters(parameters);

            // 识别请求体类型
            String requestBodyType = extractRequestBodyType(method);
            definition.setRequestBodyType(requestBodyType);

            // 解析consumes和produces
            extractConsumesProduces(annotation, definition);

            collector.add(definition);
        }

        /**
         * 解析方法参数上的注解，提取参数信息
         * 支持 @PathVariable, @RequestParam, @RequestBody, @RequestHeader
         */
        private List<EndpointDefinition.ParameterInfo> extractParameters(MethodDeclaration method) {
            List<EndpointDefinition.ParameterInfo> params = new ArrayList<>();

            for (Parameter parameter : method.getParameters()) {
                EndpointDefinition.ParameterInfo paramInfo = new EndpointDefinition.ParameterInfo();
                paramInfo.setName(parameter.getNameAsString());
                paramInfo.setType(parameter.getType().asString());

                // 检查参数上的注解
                boolean hasParamAnnotation = false;

                for (AnnotationExpr paramAnnotation : parameter.getAnnotations()) {
                    String annoName = paramAnnotation.getNameAsString();

                    switch (annoName) {
                        case "PathVariable":
                            hasParamAnnotation = true;
                            paramInfo.setSource("PATH");
                            paramInfo.setRequired(extractAnnotationBooleanAttribute(paramAnnotation, "required", true));
                            // 提取PathVariable的value作为参数名（如果指定了）
                            String pathVarName = extractAnnotationValue(paramAnnotation);
                            if (pathVarName != null && !pathVarName.isEmpty()) {
                                paramInfo.setName(pathVarName);
                            }
                            break;

                        case "RequestParam":
                            hasParamAnnotation = true;
                            paramInfo.setSource("QUERY");
                            paramInfo.setRequired(extractAnnotationBooleanAttribute(paramAnnotation, "required", true));
                            // 提取RequestParam的value作为参数名
                            String requestParamName = extractAnnotationValue(paramAnnotation);
                            if (requestParamName != null && !requestParamName.isEmpty()) {
                                paramInfo.setName(requestParamName);
                            }
                            // 提取defaultValue
                            String defaultValue = extractAnnotationStringAttribute(paramAnnotation, "defaultValue");
                            if (defaultValue != null) {
                                paramInfo.setDefaultValue(defaultValue);
                                // 如果有默认值，则参数不是必填的
                                if (!defaultValue.isEmpty()) {
                                    paramInfo.setRequired(false);
                                }
                            }
                            break;

                        case "RequestBody":
                            hasParamAnnotation = true;
                            paramInfo.setSource("BODY");
                            paramInfo.setRequired(extractAnnotationBooleanAttribute(paramAnnotation, "required", true));
                            break;

                        case "RequestHeader":
                            hasParamAnnotation = true;
                            paramInfo.setSource("HEADER");
                            paramInfo.setRequired(extractAnnotationBooleanAttribute(paramAnnotation, "required", true));
                            // 提取RequestHeader的value作为header名
                            String headerName = extractAnnotationValue(paramAnnotation);
                            if (headerName != null && !headerName.isEmpty()) {
                                paramInfo.setName(headerName);
                            }
                            break;

                        default:
                            break;
                    }
                }

                // 如果没有参数注解，根据类型推断来源
                if (!hasParamAnnotation) {
                    String typeName = parameter.getType().asString();
                    // 常见的请求体类型
                    if (isComplexType(typeName)) {
                        paramInfo.setSource("BODY");
                        paramInfo.setRequired(true);
                    } else {
                        paramInfo.setSource("QUERY");
                        paramInfo.setRequired(true);
                    }
                }

                params.add(paramInfo);
            }

            return params;
        }

        /**
         * 判断类型是否为复杂类型（通常作为请求体）
         */
        private boolean isComplexType(String typeName) {
            // 基本类型和包装类、String、常见简单类型不算复杂类型
            Set<String> simpleTypes = new HashSet<>(Arrays.asList(
                    "String", "Integer", "int", "Long", "long", "Double", "double",
                    "Float", "float", "Boolean", "boolean", "Short", "short",
                    "Byte", "byte", "Character", "char",
                    "BigDecimal", "BigInteger",
                    "LocalDate", "LocalDateTime", "LocalTime", "Date",
                    "HttpServletRequest", "HttpServletResponse",
                    "HttpSession", "Principal", "Locale",
                    "MultipartFile", "BindingResult", "Model", "ModelMap",
                    "RedirectAttributes", "Authentication"
            ));
            return !simpleTypes.contains(typeName);
        }

        /**
         * 提取方法的请求体类型
         * 查找带有 @RequestBody 注解的参数的类型
         */
        private String extractRequestBodyType(MethodDeclaration method) {
            for (Parameter parameter : method.getParameters()) {
                for (AnnotationExpr annotation : parameter.getAnnotations()) {
                    if ("RequestBody".equals(annotation.getNameAsString())) {
                        return parameter.getType().asString();
                    }
                }
            }
            return null;
        }

        /**
         * 从方法的Javadoc注释中提取描述信息
         * 提取第一段非空文本作为描述
         */
        private String extractJavadoc(MethodDeclaration method) {
            return method.getJavadoc().map(javadoc -> {
                // 获取描述文本（排除标签部分）
                String description = javadoc.getDescription().toText().trim();
                if (!description.isEmpty()) {
                    return description;
                }
                // 如果描述为空，尝试从整个注释中提取
                String fullText = javadoc.toText().trim();
                // 去除标签部分（@param, @return, @throws等）
                String[] lines = fullText.split("\n");
                StringBuilder sb = new StringBuilder();
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("@")) {
                        break;
                    }
                    if (!trimmed.isEmpty()) {
                        if (sb.length() > 0) {
                            sb.append(" ");
                        }
                        sb.append(trimmed);
                    }
                }
                return sb.toString().trim();
            }).orElse("");
        }

        /**
         * 根据注解名称解析HTTP方法
         *
         * @param annotation 注解表达式
         * @return HTTP方法字符串（GET, POST, PUT, DELETE, PATCH）
         */
        private String resolveHttpMethod(AnnotationExpr annotation) {
            String annotationName = annotation.getNameAsString();

            // 对于 @RequestMapping，检查是否有 method 属性
            if ("RequestMapping".equals(annotationName) && annotation instanceof NormalAnnotationExpr) {
                NormalAnnotationExpr normalAnnotation = (NormalAnnotationExpr) annotation;
                for (MemberValuePair pair : normalAnnotation.getPairs()) {
                    if ("method".equals(pair.getNameAsString())) {
                        String methodStr = pair.getValue().toString();
                        // 解析 RequestMethod.XXX 格式
                        if (methodStr.contains("POST")) return "POST";
                        if (methodStr.contains("PUT")) return "PUT";
                        if (methodStr.contains("DELETE")) return "DELETE";
                        if (methodStr.contains("PATCH")) return "PATCH";
                        if (methodStr.contains("GET")) return "GET";
                    }
                }
            }

            // 从映射表中查找
            return ANNOTATION_HTTP_METHOD_MAP.getOrDefault(annotationName, "GET");
        }

        /**
         * 解析consumes和produces属性
         */
        private void extractConsumesProduces(AnnotationExpr annotation, EndpointDefinition definition) {
            if (annotation instanceof NormalAnnotationExpr) {
                NormalAnnotationExpr normalAnnotation = (NormalAnnotationExpr) annotation;
                for (MemberValuePair pair : normalAnnotation.getPairs()) {
                    String name = pair.getNameAsString();
                    String value = extractExpressionStringValue(pair.getValue());
                    if ("consumes".equals(name) && value != null) {
                        definition.setConsumes(value);
                    } else if ("produces".equals(name) && value != null) {
                        definition.setProduces(value);
                    }
                }
            }
        }

        /**
         * 从表达式中提取字符串值
         */
        private String extractExpressionStringValue(Expression expr) {
            if (expr instanceof StringLiteralExpr) {
                return ((StringLiteralExpr) expr).getValue();
            } else if (expr instanceof SingleMemberAnnotationExpr) {
                return extractAnnotationValue((SingleMemberAnnotationExpr) expr);
            } else if (expr instanceof ArrayInitializerExpr) {
                // 取数组第一个元素
                ArrayInitializerExpr array = (ArrayInitializerExpr) expr;
                if (!array.getValues().isEmpty()) {
                    return extractExpressionStringValue(array.getValues().get(0));
                }
            }
            return expr.toString();
        }
    }

    /**
     * 从注解中提取value属性值
     * 支持以下格式：
     * - @GetMapping("/path") -> "/path"
     * - @GetMapping(value = "/path") -> "/path"
     * - @GetMapping(path = "/path") -> "/path"
     * - @RequestMapping({"/path1", "/path2"}) -> "/path1"
     *
     * @param annotation 注解表达式
     * @return 注解的value值，如果没有则返回null
     */
    private static String extractAnnotationValue(AnnotationExpr annotation) {
        if (annotation instanceof SingleMemberAnnotationExpr) {
            // @GetMapping("/path") 格式
            Expression value = ((SingleMemberAnnotationExpr) annotation).getMemberValue();
            return extractStringValue(value);
        } else if (annotation instanceof NormalAnnotationExpr) {
            // @GetMapping(value = "/path") 或 @GetMapping(path = "/path") 格式
            NormalAnnotationExpr normalAnnotation = (NormalAnnotationExpr) annotation;
            for (MemberValuePair pair : normalAnnotation.getPairs()) {
                if ("value".equals(pair.getNameAsString()) || "path".equals(pair.getNameAsString())) {
                    return extractStringValue(pair.getValue());
                }
            }
        }
        return null;
    }

    /**
     * 从表达式中提取字符串值
     */
    private static String extractStringValue(Expression expr) {
        if (expr instanceof StringLiteralExpr) {
            return ((StringLiteralExpr) expr).getValue();
        } else if (expr instanceof ArrayInitializerExpr) {
            // 处理数组值，取第一个元素
            ArrayInitializerExpr array = (ArrayInitializerExpr) expr;
            if (!array.getValues().isEmpty()) {
                return extractStringValue(array.getValues().get(0));
            }
        }
        return expr.toString().replace("\"", "");
    }

    /**
     * 从注解中提取布尔属性值
     *
     * @param annotation 注解表达式
     * @param attributeName 属性名
     * @param defaultValue 默认值
     * @return 属性值
     */
    private static boolean extractAnnotationBooleanAttribute(AnnotationExpr annotation, String attributeName, boolean defaultValue) {
        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normalAnnotation = (NormalAnnotationExpr) annotation;
            for (MemberValuePair pair : normalAnnotation.getPairs()) {
                if (attributeName.equals(pair.getNameAsString())) {
                    String valueStr = pair.getValue().toString();
                    return Boolean.parseBoolean(valueStr);
                }
            }
        }
        return defaultValue;
    }

    /**
     * 从注解中提取字符串属性值
     *
     * @param annotation 注解表达式
     * @param attributeName 属性名
     * @return 属性值，如果不存在则返回null
     */
    private static String extractAnnotationStringAttribute(AnnotationExpr annotation, String attributeName) {
        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normalAnnotation = (NormalAnnotationExpr) annotation;
            for (MemberValuePair pair : normalAnnotation.getPairs()) {
                if (attributeName.equals(pair.getNameAsString())) {
                    return extractStringValue(pair.getValue());
                }
            }
        }
        return null;
    }

    /**
     * 组合类级别路径和方法级别路径
     * 处理路径拼接时的斜杠问题
     *
     * @param classPath 类级别路径
     * @param methodPath 方法级别路径
     * @return 组合后的完整路径
     */
    private static String combinePath(String classPath, String methodPath) {
        if (classPath == null) classPath = "";
        if (methodPath == null) methodPath = "";

        // 去除首尾空格
        classPath = classPath.trim();
        methodPath = methodPath.trim();

        // 确保classPath不以/结尾
        if (classPath.endsWith("/")) {
            classPath = classPath.substring(0, classPath.length() - 1);
        }

        // 确保methodPath以/开头
        if (!methodPath.isEmpty() && !methodPath.startsWith("/")) {
            methodPath = "/" + methodPath;
        }

        // 组合路径
        String fullPath = classPath + methodPath;

        // 确保路径以/开头
        if (!fullPath.startsWith("/")) {
            fullPath = "/" + fullPath;
        }

        return fullPath;
    }

    /**
     * 解析指定目录下所有Controller文件
     */
    public List<EndpointDefinition> extractAllEndpoints(String sourceDir) {
        List<EndpointDefinition> allEndpoints = new ArrayList<>();
        File dir = new File(sourceDir);

        if (!dir.exists() || !dir.isDirectory()) {
            System.err.println("Source directory not found: " + sourceDir);
            return allEndpoints;
        }

        scanControllerFiles(dir, allEndpoints);
        return allEndpoints;
    }

    /**
     * 递归扫描Controller文件
     */
    private void scanControllerFiles(File dir, List<EndpointDefinition> endpoints) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanControllerFiles(file, endpoints);
            } else if (file.getName().endsWith("Controller.java")) {
                try {
                    List<EndpointDefinition> fileEndpoints = parseControllerFile(file, null);
                    endpoints.addAll(fileEndpoints);
                } catch (Exception e) {
                    System.err.println("Error scanning " + file.getName() + ": " + e.getMessage());
                }
            }
        }
    }
}
