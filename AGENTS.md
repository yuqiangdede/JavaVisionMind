# AGENTS.md — JavaVisionMind

## 范围
- 适用于整个仓库，除非被更深层的 `AGENTS.md` 覆盖。
- 未发现 Cursor 或 Copilot 规则（`.cursor/rules/`、`.cursorrules`、`.github/copilot-instructions.md`）。

## 约定
- 保持修改聚焦，避免无关重构。
- 复用现有依赖与工具，避免新增依赖。
- 与模块既有模式保持一致，先观察后新增。
- 默认使用中文沟通与回复。

## 构建 / 运行 / 测试命令
### 构建（根目录）
- 构建所有模块（跳过测试）：`mvn clean install -DskipTests`

### 运行（按模块）
- YOLO API：`mvn -pl vision-mind-yolo-app spring-boot:run`
- OCR 服务：`mvn -pl vision-mind-ocr-app spring-boot:run`
- 人脸特征服务：`mvn -pl vision-mind-ffe-app spring-boot:run`
- 行人重识别：`mvn -pl vision-mind-reid-app spring-boot:run`
- 车牌识别：`mvn -pl vision-mind-lpr-app spring-boot:run`
- 文本检索：`mvn -pl vision-mind-tbir-app spring-boot:run`
- 中文文本检索：`mvn -pl vision-mind-tbir-cn-app spring-boot:run`
- LLM 对话外观层：`mvn -pl vision-mind-llm-core spring-boot:run`

### 测试 / Lint / 格式化
- 仓库内未发现 `src/test/java` 目录。
- 未配置 Maven 测试插件（Surefire/Failsafe）或 lint/format 工具。
- 未发现 `.editorconfig`、Checkstyle、Spotless、PMD、SpotBugs 配置。

### 单测执行
- 未记录单测执行命令。
- 如后续添加测试，遵循各模块 POM 的 Maven 约定。

## 环境 / 运行说明
- Java 版本：21（根 `pom.xml` 的 `release` 已设置）。
- README 期望 Maven 3.8+。
- `VISION_MIND_PATH` 环境变量用于模型与运行时资源。
- 当 `vision-mind.skip-opencv=true` 时跳过 OpenCV 原生库加载。

## 代码风格（现有约定）
### 总体风格
- 无自动格式化工具，跟随现有文件风格。
- 缩进：4 空格。
- 大括号：K&R（起始括号同一行）。
- 行宽保持适中（约 120 字符）。

### Imports
- 不使用通配导入。
- 常见顺序：
  1) 标准库（`java.*`, `javax.*`）
  2) 第三方（`org.*`, `lombok.*` 等）
  3) 项目内（`com.yuqiangdede.*`）
- 组间使用空行分隔（如文件已有此习惯）。

### 命名规范
- 类：PascalCase；Controller 以 `Controller` 结尾，Service 以 `Service` 结尾。
- DTO：描述性名称（如 `DetectionRequest`, `SaveImageRequest`）。
- 工具类：`Util` 或 `Utils` 后缀（如 `ImageUtil`, `JsonUtils`）。
- 配置类：静态配置用 `Constant`，Spring 配置用 `*Config`。
- 方法/变量：camelCase。
- 常量：UPPER_SNAKE_CASE。

### 包结构
- 根包：`com.yuqiangdede`。
- 常见目录：
  - `controller/` REST 接口
  - `service/` 业务逻辑
  - `dto/input/` 请求 DTO
  - `dto/output/` 响应 DTO
  - `util/` 工具类
  - `config/` 配置

### 依赖注入
- 优先使用 Lombok `@RequiredArgsConstructor` 构造注入。
- 依赖字段声明为 `final`。

### Lombok
- 常用注解：`@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@RequiredArgsConstructor`, `@Slf4j`。

### API 响应约定
- Controller 返回 `HttpResult<T>`。
- 校验模式：
  - 先校验必填字段（如 `imgUrl`）。
  - 校验失败返回 `new HttpResult<>(false, "...")`。

### 错误处理
- Controller：
  - 调用服务层使用 `try/catch`。
  - 捕获 `IOException | OrtException | RuntimeException` 等。
  - 使用 `log.error("...", e)` 记录错误并返回失败 `HttpResult`。
- Service：
  - 抛出受检异常（`IOException`, `OrtException`）。
  - 输入非法用 `IllegalArgumentException`。
  - 配置/运行态问题用 `IllegalStateException`。

### 日志
- 使用 Lombok `@Slf4j`。
- 计时日志 INFO：`log.info("... Cost time：{} ms.", elapsed)`
- 错误日志 ERROR，包含堆栈。

### 注释 / Javadoc
- 使用 Javadoc 风格，Controller 中常见中文说明。
- 方法级注释与现有文件保持一致。

### 静态初始化
- 静态块用于加载原生库与环境检查。
- 相关逻辑参照 `ImgAnalysisService` 既有模式。

## 贡献建议
- 在模块内优先遵循现有结构与模式。
- 避免新增依赖，除非确有必要。
- 修改尽量集中，不做无关重构。
