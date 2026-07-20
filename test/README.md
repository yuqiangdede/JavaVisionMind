# 本地模型集成测试素材

这些文件仅用于离线集成测试，测试代码不会访问外部 HTTP 服务。

| 文件 | 用途 | 来源/许可证 | 大小 | SHA-256 |
|---|---|---|---:|---|
| `assets/car-electric.jpg` | YOLO、TBIR、车辆场景 | [Wikimedia Commons: Electric Car recharging](https://commons.wikimedia.org/wiki/File:Electric_Car_recharging.jpg)，页面标注的原始许可 | 338211 | `D413B362B154B2D350EEA2BCD36FB72D311B37030B653C732418952C77A858DA` |
| `assets/china-street-sign.jpg` | OCR、中文 TBIR | [Wikimedia Commons: Street sign in China](https://commons.wikimedia.org/wiki/File:Street_sign_in_China_(translation_unavailable).JPG)，Public domain | 2664949 | `05EBB14FDB6EC50D1518D0741C742ADAA312368D002F769817793F9438D905FE` |
| `assets/asr-zh-sample.wav` | ASR 转写 | 随项目内 Sherpa-ONNX 模型提供的测试音频 | 179646 | `668BF8DF51A10027B84D5D8816A1CE11AE93545538DC05CFE2AA6811D399C250` |

测试只依赖上述本地文件；模型文件统一从项目根目录的 `resource` 目录加载。
