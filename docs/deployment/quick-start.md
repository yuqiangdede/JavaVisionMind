# Quick Start

## 1) 环境校验

```bash
# Linux/macOS
bash scripts/verify-env.sh

# Windows PowerShell
powershell -ExecutionPolicy Bypass -File scripts/verify-env.ps1
```

## 2) 构建

```bash
mvn -DskipTests clean package
```

## 3) 启动重点模块

```bash
mvn -pl vision-mind-yolo-app spring-boot:run
mvn -pl vision-mind-asr-app spring-boot:run
```

## 4) 最小连通验证

```bash
curl http://localhost:17001/vision-mind-yolo/api/v1/health
curl http://localhost:17008/vision-mind-asr/api/v1/health
```
