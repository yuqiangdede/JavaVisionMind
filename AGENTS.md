# Repository Guidelines
JavaVisionMind bundles modular Spring Boot services for vision and multimodal workloads. Scope changes to the affected module and prefer shared utilities over duplicate code.

## Project Structure & Module Organization
- Aggregator `pom.xml` lives at the root; run all builds from here to keep module versions aligned.
- `vision-mind-common` supplies shared DTOs, math helpers, Chroma/Lucene adapters, and OpenCV bootstrap logic.
- `<capability>-core` modules expose inference logic; matching `<capability>-app` modules publish REST APIs (YOLO, OCR, ReID, FFE, TBIR).
- `vision-mind-llm-core` handles chat and translation integrations without an app wrapper.
- `vision-mind-test-sth` hosts spike code and manual end-to-end checks only.
- `resource/` holds ONNX weights and native libraries; keep artifacts out of Git and reference them through `VISION_MIND_PATH`.

## Build, Test, and Development Commands
- `mvn clean verify` - default check compiling all modules and running tests.
- `mvn clean install -DskipTests` - package artifacts when native libraries are unavailable.
- `mvn -pl vision-mind-yolo-app spring-boot:run` (swap module name) - boot a REST service with its local configuration.
- Export `VISION_MIND_PATH=<repo>/resource` and use `-Dvision-mind.skip-opencv=true` in CI to bypass native loading.

## Coding Style & Naming Conventions
- Target Java 25, UTF-8 files, four-space indentation, and the `com.yuqiangdede` package tree.
- Lombok annotations (`@Slf4j`, `@RequiredArgsConstructor`) and Spring stereotypes handle wiring.
- Use UpperCamelCase for types, lowerCamelCase for methods and fields, and constants in UPPER_SNAKE_CASE.
- Place controller DTOs in `...controller.dto`, service logic in `...service`, and reusable operators in the paired core module.
- Prefer short Javadoc for non-obvious math or tensor handling; otherwise keep noise low.

## Testing Guidelines
- Mirror production packages under `src/test/java`; name unit tests `<ClassName>Test`.
- Favor JUnit 5 with AssertJ; declare dependencies in each module POM as needed.
- Park integration scaffolding in `vision-mind-test-sth` with clear entry points.
- Guard native-heavy tests with `@DisabledOnOs` or the `vision-mind.skip-opencv` property.
- Aim for >=70% branch coverage on new logic and add regression tests with every bug fix.

## Commit & Pull Request Guidelines
- Follow Conventional Commits (for example `feat(config): enable ocr batch mode`); bilingual summaries are fine, first line <=72 characters.
- Expand in the body with behavior changes, config toggles, and model updates; link issues as `Refs #123`.
- Before raising a PR, run `mvn clean verify`, drop generated `target/` artifacts, and include sample responses for API-impacting work.
- PR descriptions should cover scope, testing, rollout notes, and required resource updates such as new ONNX files.
