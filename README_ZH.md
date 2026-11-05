# ProxyAI 中文增强版（非官方）

本项目基于 [ProxyAI](https://github.com/carlrobertoh/ProxyAI) 3.8.2 源码开发，保留上游的 Apache 2.0 许可证与署名。它沿用上游插件 ID `ee.carlrobert.chatgpt`，属于原插件的替代构建，不能与官方版同时启用。

## 新增功能

- 将主要设置入口、编辑器菜单、Chat 标签与欢迎页操作改成中文。
- 将智能体欢迎页、项目配置、历史会话、新建与重置操作、会话标签菜单改成中文。
- Java 方法和 Kotlin 函数的第一条注解上方显示仅含插件图标和下拉箭头的 Code Vision 入口；没有注解时显示在方法声明上方。点击后可选择“解释代码”“编写单元测试”“查找问题”，不会占用行号栏。
- 操作会把该方法或函数的完整代码加入 Chat 请求，并要求模型用简体中文回答。普通 Chat 在使用默认 Persona 时也会要求中文回答。
- 继续使用 ProxyAI 原有的模型、API 密钥和 Chat 配置；现有 OpenCode Go 配置仍由 ProxyAI 读取。

## 构建与安装

使用 JDK 21 运行：

```bash
./gradlew buildPlugin
```

插件包生成在 `build/distributions/`。在 IDEA 中打开“设置 → 插件 → 齿轮图标 → 从磁盘安装插件”，选择 ZIP 后重启 IDEA。因为插件 ID 与官方版相同，安装前应先备份现有 ProxyAI 设置；IDE 会将它视作同一个插件的替代版本。

## 范围

方法图标目前覆盖 Java 和 Kotlin。此次中文化集中在常用入口，部分上游深层设置、诊断信息和第三方服务名称仍保留英文。插件尚未发布到 JetBrains Marketplace。
