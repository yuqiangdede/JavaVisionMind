# TBIR-CN 模块

- 模块名：`vision-mind-tbir-cn-app`
- 状态：`beta`
- 领域：中文文本图像检索（Text-Based Image Retrieval, CN）

## 接口分组

- 旧接口：`/vision-mind-tbir-cn/tbir/*`
- 新接口（并行）：`/vision-mind-tbir-cn/api/v1/retrieval/*`

## 统一接口

- `POST /api/v1/retrieval/store`
- `POST /api/v1/retrieval/search/image`
- `POST /api/v1/retrieval/search/text`
- `POST /api/v1/retrieval/preview/image`
- `POST /api/v1/retrieval/preview/text`
- `DELETE /api/v1/retrieval/index`

## 输入模式

- 图像：URL / base64 / 上传文件（通过统一 `ImageSource` 适配）
- 文本：UTF-8 字符串

## 资源依赖

- 参考 [`resource/manifest.json`](../../resource/manifest.json) 的 `tbir-cn` 节点
- 启动时由资源校验器按模块检查 `required` 项
