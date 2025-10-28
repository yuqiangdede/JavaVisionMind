# Repository Guidelines
JavaVisionMind 提供模块化 Spring Boot 服务，贡献时聚焦受影响模块并优先复用 `vision-mind-common` 的工具类。Keep changes scoped so each service remains independently deployable.

## 项目结构与模块职责
- 根目录 `pom.xml` 聚合所有子模块，必须在仓库根目录执行 Maven；run all lifecycle goals from the root to prevent version drift。
- `vision-mind-common` 内含 DTO、向量存储适配器与 OpenCV 启动逻辑，treat it as the shared toolkit。
- `<capability>-core` 负责推理流程，配对的 `<capability>-app` 暴露 REST API（YOLO、OCR、ReID、FFE、TBIR），keep cross-module coupling minimal。
- `vision-mind-llm-core` 只提供语言接口，无独立应用层，可直接被其他模块依赖。
- `vision-mind-test-sth` 用于实验代码和端到端验证，不要提交正式实现，use it for spikes only。
- `resource/` 存放 ONNX 模型与原生库，应通过 `VISION_MIND_PATH` 引用且勿入库，documents should reference release artifacts.

## 构建与运行
- `mvn clean verify`：标准编译+测试流程，确保依赖与代码同步。
- `mvn clean install -DskipTests`：无法加载原生依赖时跳过测试打包，但请记录原因。
- `mvn -pl vision-mind-yolo-app spring-boot:run`（替换模块名）：以模块本地配置启动服务，适合调试 API。
- 设置 `VISION_MIND_PATH=<repo>/resource`，在 CI 可加 `-Dvision-mind.skip-opencv=true` 规避本地库加载，避免在无图形环境下出错。

## 环境与工具提示
- Windows 建议使用 PowerShell (`pwsh`) 运行 Maven；避免在 WSL2 中执行涉及原生库的构建，以免触发 DLL 加载问题。
- 仓库应位于本地磁盘路径（例如 `C:\repo`），不要通过 `/mnt/c` 访问以免触发跨盘性能损失，local paths give faster scans。
- 代码检索优先 `rg`，文件枚举用 `fd`，排除 `.git`、`target`、`resource` 等大型目录，prefer scoped searches for signal。
- 临时研究资料统一置于 `docs/research/`，任务结束后整理或清理，避免散落在仓库根目录，promote reusable docs into `docs/` 正式目录。

## 代码风格与命名
- 目标 JDK 25、UTF-8 编码、四空格缩进，包名前缀保持 `com.yuqiangdede`，保持一致的包层级。
- 使用 Lombok（如 `@Slf4j`、`@RequiredArgsConstructor`）与 Spring 注解完成依赖注入，避免手写样板代码。
- 类型使用 UpperCamelCase，方法与字段使用 lowerCamelCase，常量保持 UPPER_SNAKE_CASE，follow standard Java conventions。
- 控制器 DTO 放在 `...controller.dto`，业务逻辑在 `...service`，可复用算子沉到对应 core 模块，减少重复实现。
- 仅为复杂算法或张量处理撰写简洁 Javadoc，减少冗余注释，让代码自解释。

## 测试要求
- 在 `src/test/java` 按生产包结构放置测试类，命名 `<ClassName>Test`，mirror production packages。
- 采用 JUnit 5 与 AssertJ，需要时在模块 POM 中声明依赖，keep test scope lean。
- 集成或手工场景放入 `vision-mind-test-sth`，保持入口清晰，说明配置或输入样例。
- 涉及原生库的测试通过 `@DisabledOnOs` 或 `vision-mind.skip-opencv` 开关保护，避免 CI 与本地差异。
- 新增逻辑应达到约 70% 分支覆盖，并为缺陷修复补充回归测试，document tricky cases in test names。

## 提交与拉取请求
- 遵循 Conventional Commits（例：`feat(config): enable ocr batch mode`），首行控制在 72 字符内，可附双语说明，描述要简练。
- 提交信息正文阐述行为变化、配置开关与模型更新，并通过 `Refs #123` 关联问题，记录迁移步骤。
- 提 PR 前执行 `mvn clean verify`，删除 `target/` 等构建产物，并附上关键接口的示例输入输出，帮助 Reviewer 快速验证。
- PR 描述需包含改动范围、测试结果、上线注意事项及模型/资源更新清单（如新增 ONNX），说明部署依赖或回滚策略。
