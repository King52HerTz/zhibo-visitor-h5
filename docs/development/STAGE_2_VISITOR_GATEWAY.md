# 阶段 2：游客网关与只读链路

## 运行边界

`visitor-gateway` 是独立的 FastAPI 服务，默认监听 `8021`。浏览器只保存短期游客会话 ID，不保存稳定版登录态、桥接密钥、模型 API Key 或管理员凭据。

网关使用环境变量中的专用普通账号登录稳定版 `museum-rag` 的 `/auth/login`，不会自动注册账号，也不会写入稳定版数据库。部署前由管理员在稳定版创建一个最低权限普通账号，再把账号信息放入网关所在机器的本地环境变量。

## 启动

```powershell
cd visitor-gateway
Copy-Item .env.example .env
# 只在本机环境变量或未提交的 .env 中填写普通账号，不要提交 .env
python -m uvicorn app.main:app --host 0.0.0.0 --port 8021
```

阶段 2 不提供识展、展品管理、上传、删除、任意文件下载或管理端代理接口。未列入白名单的路径统一返回 404。

## 验证

```powershell
python -m pytest visitor-gateway/tests -q
npm run typecheck
npm run build
```

默认前端开发服务器通过 Vite 代理把 `/api/visitor/*` 转发到 `127.0.0.1:8021`。未配置稳定版账号时，网关仍可以创建会话，但导览请求会返回安全的 `INSUFFICIENT_EVIDENCE` 状态，不会回退到浏览器 mock，也不会泄漏上游错误。
