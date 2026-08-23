# TBIR Module

- App: `vision-mind-tbir-app`
- Domain: `tbir`

## Unified API

- Store: `/api/v1/tbir/store`
- Search text: `/api/v1/tbir/search/text`
- Search image: `/api/v1/tbir/search/image`
- Index delete: `/api/v1/tbir/index/delete`
- Preview: `/api/v1/tbir/preview/text`

## 配置

TBIR app 只读取 `vision-mind-tbir-app/src/main/resources/application.yml`。
模型使用 `vision-mind.tbir.models.*`，向量库使用 `vision-mind.tbir.vector-store.*`，
检测、过滤和增强参数分别位于 `vision-mind.tbir.detection.*`、
`vision-mind.tbir.filter.*` 和 `vision-mind.tbir.augment-types`。旧的
`application.properties` 配置不再读取。
