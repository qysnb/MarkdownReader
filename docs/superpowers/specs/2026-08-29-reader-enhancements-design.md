# Markdown Reader 增强功能设计

## 目标

在保持现有 `NestedScrollView + TextView + Markwon` 性能结构的基础上，增加可快速跳转的阅读进度轨、主题化 Android 应用图标、Markdown 文件系统关联，以及可持久化的阅读设置。

## 设计

- 阅读页使用右侧宽触达区包裹窄视觉轨道。点击或拖动按比例映射到 `NestedScrollView` 的滚动范围；滚动时只更新轨道，不重新解析 Markdown。
- 最近文件内部继续保存全部历史，主页仅按 `recentLimit` 展示，默认 10 条。路径显示开关开启时优先展示 SAF 可解析的文件夹层级，无法解析则展示简化来源信息。
- 设置页保存最近文件显示数量、默认字号、路径显示开关，并展示应用版本和“开发者：Qyforest”。
- MainActivity 同时处理启动 Intent 和 `onNewIntent`，接收 `ACTION_VIEW` 的 Markdown MIME 类型及常见扩展名，读取 `content Uri` 后进入阅读页。
- 应用图标采用深色文档页与数学符号的自适应图标，使用本地矢量资源，避免运行时资源依赖。

## 验证

- 单元测试验证设置边界、最近文件截取和路径回退。
- 构建 debug APK；手动验证进度拖动、系统“打开方式”、重启后的设置恢复和图标显示。
