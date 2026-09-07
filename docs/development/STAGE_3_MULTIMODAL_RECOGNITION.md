# 阶段 3：多模态候选与馆方资料核验

## 目标

游客上传一张展品图片后，游客网关先让视觉模型提出候选，再逐一调用稳定服务的 `/api/museum/query` 做馆方资料核验。视觉模型不能直接生成馆藏事实，未命中资料时前端必须明确显示“暂未收录”。

## 服务端配置

以下变量只配置在 `visitor-gateway` 服务端，不得写入 Vite 环境变量或浏览器代码：

```dotenv
MUSEUM_RAG_BASE_URL=http://127.0.0.1:8000
MUSEUM_RAG_BRIDGE_API_KEY=YOUR_SERVER_SIDE_BRIDGE_KEY
VISUAL_MODEL_BASE_URL=https://YOUR_OPENAI_COMPATIBLE_ENDPOINT/v1
VISUAL_MODEL_API_KEY=YOUR_SERVER_SIDE_VISUAL_MODEL_KEY
VISUAL_MODEL_NAME=YOUR_VISION_MODEL_NAME
```

视觉模型接口采用 OpenAI 兼容的 `/chat/completions` 图片消息格式。默认单图上限为 8MB，可通过 `VISITOR_MAX_IMAGE_BYTES` 调整。

## 游客接口

- `POST /api/visitor/v1/recognitions`：`multipart/form-data` 上传字段 `image`
- `GET /api/visitor/v1/recognitions/{recognition_id}`：读取本会话识别结果
- `POST /api/visitor/v1/recognitions/{recognition_id}/confirm`：确认候选，JSON 字段 `candidate_id`

三个接口都要求 `X-Visitor-Session`。其他游客会话读取同一识别编号时返回 404，避免泄露记录是否存在。

## 公开状态

- `READY`：至少一个候选已命中馆方资料
- `NOT_FOUND`：有视觉候选，但馆方资料没有对应内容，或没有清晰候选
- `ERROR`：视觉模型或馆方核验服务不可用
- `CONFIRMED`：游客已经确认候选

候选的 `knowledge_status` 分为 `VERIFIED`、`PARTIAL`、`NOT_FOUND`。只有 `VERIFIED` 候选可以直接进入讲解。

## 隐私与清理

- 上传文件名不会用于服务端路径。
- 网关校验 MIME 类型、文件签名和大小。
- 图片只写入受控临时目录，并在成功、未命中或异常后统一删除。
- 识别记录仅保存候选、观察与公开来源，不保存原图和 Base64。
- 对外来源只包含文件名、页码和公开片段，不返回内部路径、分块 ID、分数或检索调试字段。

## 验证

```powershell
cd visitor-gateway
python -m pytest -q

cd ..
npm run build
```
