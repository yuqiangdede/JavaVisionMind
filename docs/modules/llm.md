# LLM 模块

- 模块名：`vision-mind-llm-core`
- 状态：`beta`
- 领域：文本/图像多模态对话封装

## 接口分组

- 旧接口：`/vision-mind-llm/chat/*`
- 新接口（并行）：`/vision-mind-llm/api/v1/llm/*`

## 统一接口

- `POST /api/v1/llm/chat/text`
- `POST /api/v1/llm/chat/image`
- `GET /api/v1/llm/runtime/health`

## 说明

- 该模块主要是上层调用封装，不绑定单一推理算法
- 通过统一 `HttpResult<T>`、错误码、traceId 与全局异常机制对外输出
