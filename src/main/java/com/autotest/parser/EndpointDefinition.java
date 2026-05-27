package com.autotest.parser;

import java.util.List;
import java.util.Map;

/**
 * 接口定义数据模型 - 描述一个Spring API接口的完整元数据
 */
public class EndpointDefinition {

    /** HTTP方法: GET, POST, PUT, DELETE, PATCH */
    private String httpMethod;

    /** 接口URL路径 */
    private String urlPath;

    /** Controller类名 */
    private String controllerClass;

    /** 方法名 */
    private String methodName;

    /** 请求参数列表 */
    private List<ParameterInfo> parameters;

    /** 请求体类型 */
    private String requestBodyType;

    /** 响应类型 */
    private String returnType;

    /** 接口描述（从注释中提取） */
    private String description;

    /** 消费的内容类型 */
    private String consumes;

    /** 产生的内容类型 */
    private String produces;

    /** 是否需要认证 */
    private boolean requiresAuth;

    /** 自定义注解信息 */
    private Map<String, String> annotations;

    /** 是否为间接测试（因依赖类变更而测试） */
    private boolean indirectTest;

    /** 间接测试的原因（依赖的修改文件列表） */
    private String indirectTestReason;

    /**
     * 参数信息内部类
     */
    public static class ParameterInfo {

        /** 参数名 */
        private String name;

        /** 参数类型 */
        private String type;

        /** 参数来源: PATH, QUERY, HEADER, BODY */
        private String source;

        /** 是否必填 */
        private boolean required;

        /** 参数描述 */
        private String description;

        /** 默认值 */
        private String defaultValue;

        // ===== Getters and Setters =====

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getDefaultValue() { return defaultValue; }
        public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

        @Override
        public String toString() {
            return "ParameterInfo{" +
                    "name='" + name + '\'' +
                    ", type='" + type + '\'' +
                    ", source='" + source + '\'' +
                    ", required=" + required +
                    '}';
        }
    }

    // ===== Getters and Setters =====

    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }

    public String getUrlPath() { return urlPath; }
    public void setUrlPath(String urlPath) { this.urlPath = urlPath; }

    public String getControllerClass() { return controllerClass; }
    public void setControllerClass(String controllerClass) { this.controllerClass = controllerClass; }

    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }

    public List<ParameterInfo> getParameters() { return parameters; }
    public void setParameters(List<ParameterInfo> parameters) { this.parameters = parameters; }

    public String getRequestBodyType() { return requestBodyType; }
    public void setRequestBodyType(String requestBodyType) { this.requestBodyType = requestBodyType; }

    public String getReturnType() { return returnType; }
    public void setReturnType(String returnType) { this.returnType = returnType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getConsumes() { return consumes; }
    public void setConsumes(String consumes) { this.consumes = consumes; }

    public String getProduces() { return produces; }
    public void setProduces(String produces) { this.produces = produces; }

    public boolean isRequiresAuth() { return requiresAuth; }
    public void setRequiresAuth(boolean requiresAuth) { this.requiresAuth = requiresAuth; }

    public Map<String, String> getAnnotations() { return annotations; }
    public void setAnnotations(Map<String, String> annotations) { this.annotations = annotations; }

    public boolean isIndirectTest() { return indirectTest; }
    public void setIndirectTest(boolean indirectTest) { this.indirectTest = indirectTest; }

    public String getIndirectTestReason() { return indirectTestReason; }
    public void setIndirectTestReason(String indirectTestReason) { this.indirectTestReason = indirectTestReason; }

    // ===== 别名方法（兼容性） =====

    /**
     * 获取完整的API路径（getUrlPath的别名）
     */
    public String getFullPath() {
        return this.urlPath;
    }

    /**
     * 获取路径部分（getUrlPath的别名）
     */
    public String getPath() {
        return this.urlPath;
    }

    /**
     * 获取完整的URL（baseUrl + urlPath）
     */
    public String getFullUrl(String baseUrl) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String path = urlPath.startsWith("/") ? urlPath : "/" + urlPath;
        return base + path;
    }

    @Override
    public String toString() {
        return "EndpointDefinition{" +
                "httpMethod='" + httpMethod + '\'' +
                ", urlPath='" + urlPath + '\'' +
                ", controllerClass='" + controllerClass + '\'' +
                ", methodName='" + methodName + '\'' +
                ", returnType='" + returnType + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
