# Local integration test build

PowerShell:

```powershell
$env:JAVA_HOME='D:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2023.1.3\jbr'
$env:VISION_MIND_PATH=(Resolve-Path resource).Path
mvn clean test
mvn clean verify
```

Real model tests run by default, using local files only. Surefire isolates test classes in separate JVMs because OpenCV and Sherpa native runtimes are process-global. External LLM Chat and OCR LLM endpoints are excluded.
