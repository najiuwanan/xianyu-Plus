# XianYuSmart 前端

Vue 3、TypeScript 与 Vite 构建的单商家经营后台，生产产物直接输出到 Spring Boot 的 `src/main/resources/static`。

## 开发

```bash
npm ci
npm run dev
```

开发地址：`http://localhost:5173`

`/api` 与 `/ai` 请求代理到 `http://localhost:12400`。

## 验证与构建

```bash
npm run type-check
npm run build:spring
```

## 结构

```text
src/
├─ api/          接口封装
├─ components/   布局与通用组件
├─ views/        业务页面
├─ utils/        请求、提示与确认工具
├─ router/       页面路由
└─ assets/       全局商业主题
```

界面遵循低装饰原则：不使用渐变、毛玻璃和非必要阴影，异常待办优先于展示性图表。
