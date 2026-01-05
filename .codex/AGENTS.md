# AGENTS.md (Codex) - Maven Multi-Module Aggregator

## Operating Mode
- 允许自动修改代码与自动执行命令（高风险模式）。
- 任何步骤必须可回滚，禁止一次性大改。

## Mandatory Workflow
1) Plan：先列出要改的模块/文件 + 将要执行的命令序列。
2) Execute：按顺序执行，每次只做一个小改动。
3) Verify：每次改动后必须跑测试并记录 exit code。
4) Finish：输出变更摘要 + 验证命令 + 关键日志摘要。

## Maven Multi-Module Facts
- 根目录 pom.xml 为 aggregator，子模块通过 <modules> 管理。
- 默认在仓库根目录执行构建/测试，除非明确只改单模块。

## Allowlist Commands
### Inspect
- git status
- git diff
- git log -n 50
- rg "<pattern>"

### Build/Test (preferred)
- mvn -q -DskipTests test
- mvn -q test
- mvn -q -pl :<module-artifactId> -am test

## Verification Rules (Hard)
- 任何代码改动必须满足其一：
    - 根目录 mvn -q test 通过
    - 或最小范围：mvn -q -pl :<module> -am test 通过，并说明为何可替代全量
- 不允许“未运行测试即结束”。

## Safety Boundaries (Hard)
- 禁止删除性命令（rm/del 等）
- 禁止访问工作区外路径
- 未经明确允许禁止任何非依赖下载性质的联网动作
