# CI

GitHub Actions 定义在 `.github/workflows/ci.yml`，包含三类任务：

- Maven build（跳过测试）
- Maven test（`-Dvision-mind.skip-opencv=true`）
- Checkstyle（当前覆盖平台层与 starter）

触发条件：

- push 到 `main`
- pull request 到 `main`
