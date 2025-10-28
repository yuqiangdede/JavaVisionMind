# Repository Guidelines
JavaVisionMind 提供模块化 Spring Boot 服务，贡献时请聚焦受影响模块，并优先复用 `vision-mind-common` 中的工具类，以保障各独立服务的可部署性。

## 项目结构与模块职责
- 根目录 `pom.xml` 聚合全部子模块，务必在仓库根目录执行 Maven，以避免版本漂移。
- `vision-mind-common` 提供 DTO、数学工具、向量存储适配器与 OpenCV 引导逻辑，是跨模块共享的基础组件。
- `<capability>-core` 负责推理流程，同名 `<capability>-app` 暴露 REST API，建议通过接口契约而非内部实现集成。
- `vision-mind-llm-core` 聚焦语言服务，无独立应用层，可供其他模块直接引用并扩展。
- `vision-mind-test-sth` 用于实验与端到端演练，正式代码应回迁至对应模块，避免临时实现常驻。
- `resource/` 存放 ONNX 模型与原生库，统一由 `VISION_MIND_PATH` 指向，并保持不纳入版本控制。

## 构建与运行
- `mvn clean verify`：执行全量编译与测试，用于日常校验和预提交检查。
- `mvn clean install -DskipTests`：在原生依赖不可用时临时跳过测试，并在变更描述中标注原因。
- `mvn -pl vision-mind-yolo-app spring-boot:run`（可替换模块名）：按模块配置启动服务，便于本地调试。
- 设置 `VISION_MIND_PATH=<repo>/resource` 并在 CI 或无图形环境下添加 `-Dvision-mind.skip-opencv=true`，避免原生库加载失败。

## 环境与工具提示
- Windows 平台优先使用 PowerShell(`pwsh`) 执行命令，避免在 WSL2 中运行涉及原生库的构建，以降低 DLL 加载风险。
- 仓库建议位于本地磁盘路径（如 `C:\repo`），不要通过 `/mnt/c` 或远程映射操作，以保证 I/O 性能稳定。
- 代码搜索优先选择 `rg`，文件枚举使用 `fd`，同时排除 `.git`、`target`、`resource` 等目录，保持检索结果聚焦。
- 临时研究材料统一存放 `docs/research/`，任务结束后及时整理或升级为正式文档，避免仓库根目录混乱。

## 代码风格与命名
- 统一使用 JDK 25、UTF-8 编码与四空格缩进，包前缀保持 `com.yuqiangdede`，确保结构一致。
- 广泛运用 Lombok（如 `@Slf4j`、`@RequiredArgsConstructor`）与 Spring 注解，减少手写样板代码与构造方法。
- 类型命名采用大驼峰，方法与字段使用小驼峰，常量保持全大写下划线，遵循 Java 约定。
- 控制器 DTO 放在 `...controller.dto`，核心业务层位于 `...service`，可复用算子沉淀至对应 core 模块。
- 对复杂算法或张量处理编写简明 Javadoc，其余情况优先让代码自描述，避免噪声注释。

## 测试要求
- 在 `src/test/java` 按生产包结构布置测试类，命名 `<ClassName>Test`，保持目录与命名一致。
- 采用 JUnit 5 与 AssertJ 作为首选组合，若需额外依赖请在模块 POM 中显式声明并说明用途。
- 集成或手工验证代码放入 `vision-mind-test-sth`，补充使用指南与样例数据，避免与正式模块混用。
- 涉及原生库的测试使用 `@DisabledOnOs` 或 `vision-mind.skip-opencv` 开关，减少 CI 与本地差异。
- 提交新功能时目标分支覆盖率不低于 70%，并为每个缺陷修复补充针对性回归测试。

## 提交与拉取请求
- 遵循 Conventional Commits（例：`feat(config): enable ocr batch mode`）规范，首行控制在 72 字符内，必要时在正文添加中文摘要。
- 提交信息正文需要说明行为调整、配置变化与模型更新，并使用 `Refs #123` 等语法关联对应任务。
- 提交 PR 前必须执行 `mvn clean verify`，清理 `target/` 等构建产物，并附上关键接口的输入输出示例以便审查。
- PR 描述应覆盖改动范围、测试结果、上线注意事项以及模型或资源更新清单（如新增 ONNX），同时给出部署与回滚策略。

## 安全与配置提示
- 环境变量 中 存放 的 API 密钥 建议 通过 操作系统 密钥 管理 工具 维护，严禁 提交 到 Git 历史。
- 本地 `application.properties` 可以 复制 为 `application-local.properties` 并 追加 差异 配置，在 PR 中 说明 新 引入 的 参数。
- 提交 前 可 运行 `mvn dependency:tree` 检查 新 引入 的 三方 依赖，避免 未 审核 组件 进入 核心 模块。
