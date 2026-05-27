# Spring API Tester

AI驱动的Spring项目接口自动化测试GUI工具。基于Git变更自动识别新增/修改的API接口，利用AI大模型生成多维测试用例，执行功能测试和性能测试，并生成可视化报告。

## 功能特性

- **Git变更感知**：基于分支、时间范围和提交用户，自动识别新增/修改的Controller接口
- **Controller追溯**：修改Service/Repository时，自动找到依赖的Controller进行测试
- **AI流式生成**：实时展示AI思考过程，流式生成测试用例
- **接口过滤**：支持排除特定前缀的接口，或只测试指定接口
- **多维测试执行**：功能测试（并发执行）+ 性能测试（OkHttp实现）
- **智能分析与修复**：AI分析失败原因，支持自动修复测试用例和生成代码修复建议
- **详细HTML报告**：丰富的统计图表、失败分析、性能指标、修复建议
- **分支/用户下拉选择**：Git配置支持分支和提交用户的下拉选择及关键词搜索

## 环境要求

- Java 8+
- Maven 3.6+
- Git

## 快速开始

### 1. 编译打包

```bash
cd spring-api-tester
mvn clean package -DskipTests
```

打包后生成 `target/spring-api-tester-1.0.0-all.jar`

### 2. 运行GUI

```bash
# 方式1：直接运行
java -jar target/spring-api-tester-1.0.0-all.jar

# 方式2：Maven运行
mvn exec:java
```

## GUI界面说明

### 配置标签页

| 配置区域 | 说明 |
|---------|------|
| **Git配置** | 项目路径、目标分支（支持下拉搜索）、起始时间、提交用户（支持下拉搜索） |
| **测试配置** | 被测服务URL、超时时间、并发线程数、认证Token |
| **接口过滤** | 排除接口前缀、排除URL关键词、只测指定接口 |
| **AI配置** | NEW API地址、API Key、模型名称 |
| **性能测试配置** | 启用开关、并发线程数、持续时间 |
| **功能选项** | 自动修复开关 |

### 日志标签页

实时显示测试执行日志，包括：
- Git变更分析结果
- API接口提取情况
- AI生成测试用例进度
- 功能测试执行结果
- 性能测试指标
- 错误和修复建议

### AI思考过程窗口

生成测试用例时自动弹出，实时展示：
- 当前处理的接口
- AI实时生成的思考过程
- 生成的测试用例内容
- 分析完成后立即关闭

## 接口过滤功能

### 排除接口（前缀匹配）

在"排除接口"输入框中输入要跳过的接口前缀，多个用逗号分隔：

```
/tools,/energyApi/health,/api/internal
```

效果：所有以这些前缀开头的接口都会被跳过。

### 排除关键词（URL包含匹配）

在"排除关键词"输入框中输入要跳过的URL关键词，多个用逗号分隔：

```
export,login,download,test
```

效果：**只要URL中包含这些关键词，就会被跳过**。关键词匹配不区分大小写。

示例：
| URL | 关键词 | 结果 |
|-----|--------|------|
| `/energyApi/report/exportDetail` | `export` | 跳过 ✅ |
| `/user/login` | `login` | 跳过 ✅ |
| `/admin/system/login` | `login` | 跳过 ✅ |
| `/energyApi/report/detail` | `export` | 不跳过 | |

### 只测指定接口

在"只测接口"输入框中输入要测试的接口完整路径，多个用逗号分隔：

```
/energyApi/report/detail,/energyApi/report/export
```

效果：只测试这些指定的接口，其他接口全部跳过（优先级最高）。

### 优先级规则

**只测接口 > 排除关键词 > 排除前缀**

### 组合使用

| 排除前缀 | 排除关键词 | 只测接口 | 结果 |
|---------|-----------|---------|------|
| `/tools` | 空 | 空 | 跳过所有`/tools`开头的接口 |
| 空 | `export,login` | 空 | 跳过所有URL包含export或login的接口 |
| `/tools` | `export` | 空 | 跳过符合任一规则的接口 |
| `/tools` | `export` | `/energyApi/report/detail` | 只测试指定接口 |

## 工作流程

```
1. 配置参数（GUI界面填写）
       ↓
2. Git变更分析（JGit解析diff，识别变更的Controller和方法）
       ↓
3. Controller追溯（找到修改类依赖的Controller）
       ↓
4. 接口过滤（应用排除/只测规则，去重）
       ↓
5. AI流式生成测试用例（实时展示思考过程）
       ↓
6. 功能测试执行（OkHttp并发执行，验证状态码和响应内容）
       ↓
7. 性能测试（可选，OkHttp多线程压测）
       ↓
8. AI分析与修复（可选，分析失败原因，自动修复/生成建议）
       ↓
9. 生成详细HTML报告（统计图表、失败详情、修复建议）
```

## 项目结构

```
spring-api-tester/
├── pom.xml                          # Maven项目配置
├── src/main/java/com/autotest/
│   ├── gui/
│   │   ├── MainFrame.java           # GUI主窗口
│   │   ├── AiProgressPanel.java     # AI实时思考面板
│   │   └── SearchableComboBox.java  # 可搜索下拉框组件
│   ├── config/
│   │   └── Config.java              # 配置管理
│   ├── git/
│   │   ├── GitDiffAnalyzer.java     # Git变更分析器（JGit）
│   │   └── ChangedEndpoint.java     # 变更接口数据模型
│   ├── parser/
│   │   ├── ApiMetadataExtractor.java # API元数据提取器（JavaParser）
│   │   └── EndpointDefinition.java   # 接口定义数据模型
│   ├── ai/
│   │   ├── AiClient.java            # AI客户端（OpenAI兼容，支持流式）
│   │   ├── TestCaseGenerator.java   # 测试用例生成器
│   │   └── FixAnalyzer.java         # 失败分析与修复建议
│   ├── executor/
│   │   ├── TestExecutor.java        # 功能测试执行器
│   │   ├── PerformanceTester.java   # 性能测试执行器（OkHttp）
│   │   ├── TestCase.java            # 测试用例模型
│   │   └── TestResult.java          # 测试结果模型
│   └── report/
│       ├── DetailedReportGenerator.java  # 详细HTML报告生成器
│       └── FixSuggestion.java       # 修复建议模型
└── src/main/resources/
    └── application.properties       # 默认配置
```

## 技术栈

| 组件 | 技术 | 用途 |
|------|------|------|
| GUI框架 | Swing | 桌面应用界面 |
| Git操作 | JGit 5.13 | Git仓库分析和diff解析 |
| 源码解析 | JavaParser 3.25 | Spring Controller注解解析 |
| HTTP客户端 | OkHttp 4.12 | 测试请求发送、性能测试 |
| AI对接 | OpenAI兼容API | 测试用例生成和失败分析（支持流式） |
| 报告生成 | 纯HTML/CSS/JS | 详细HTML报告 |
| JSON处理 | Jackson 2.15 | 数据序列化/反序列化 |

## NEW API配置说明

本工具通过NEW API网关调用AI大模型，NEW API兼容OpenAI标准格式。

1. 部署NEW API（参考 https://docs.newapi.pro/zh/docs）
2. 获取API Key
3. 在GUI的"AI配置"中填写：
   - API地址：`http://your-newapi-domain/v1`
   - API Key：`sk-your-api-key`
   - 模型名称：`glm-5.1` 或 `deepseek-chat` 等

支持的所有模型（取决于NEW API配置）：
- OpenAI: gpt-4, gpt-4o, gpt-3.5-turbo
- Anthropic: claude-3-opus, claude-3-sonnet
- Google: gemini-pro
- DeepSeek: deepseek-chat, deepseek-coder
- GLM: glm-4, glm-5, glm-5.1
- 其他30+模型

## 配置持久化

所有配置会自动保存到 `application.properties` 文件，下次启动自动加载：

```properties
# AI配置
new.api.url=http://192.168.18.15:3000/v1
new.api.key=sk-your-api-key
new.api.model=glm-5.1

# 被测服务
test.base.url=http://localhost:8080

# Git配置
git.path=D:\\your\\spring-project
git.branch=main
git.since=2024-01-01
git.author=zhangsan

# 接口过滤
filter.exclude.prefixes=/tools,/energyApi/health
filter.include.only=/energyApi/report/detail

# 性能测试
perf.enabled=true
perf.threads=50
perf.duration=60

# 功能选项
auto.fix.enabled=true
```

## 注意事项

1. **被测服务需提前启动**：工具通过HTTP请求测试接口，确保Spring服务已运行
2. **Git仓库需包含完整历史**：变更分析依赖Git diff，确保仓库有完整的commit历史
3. **AI API费用**：使用AI生成测试用例会产生API调用费用，建议使用性价比高的模型
4. **性能测试资源**：性能测试会消耗较多资源，建议在独立环境运行
5. **首次运行较慢**：AI生成用例需要网络请求，首次运行可能需要较长时间
6. **窗口大小调整**：配置面板支持滚动，窗口缩小时可滚动查看所有配置项
