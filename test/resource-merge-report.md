# resource 合并校验报告

- 目标目录：`resource`
- 来源目录：`resource(1)`
- 校验方式：逐个相对路径比较 SHA-256
- 比较结果：83 个同路径文件内容一致，0 个需要复制，0 个冲突
- manifest 校验：`RepositoryResourceIntegrityTest` 已验证所有 required 文件存在
- 模型加载校验：YOLO、OCR、LPR、FFE、ReID、TBIR、中文 TBIR、ASR、TTS 集成测试均使用目标目录加载
- 删除状态：最终 `mvn test` 通过后已删除 `resource(1)`；目标目录保留 88 个文件
