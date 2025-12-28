* **Core: Zero-Latency Monitoring (核心：零延迟监控)**
  * **Event-Driven Architecture**: Refactored `SpeedDataSource` to use `NetworkCallback` + caching.
  * **事件驱动架构**: 重构数据源，利用 `NetworkCallback` 缓存接口映射，实现了采样循环中的零 IPC 调用。
  * **Parallel IO**: Integrated Kotlin Coroutines for parallel traffic reading, eliminating
    time-skew between interfaces.
  * **并行 IO**: 采用协程并行读取各网卡数据，消除多网卡统计时的时间偏差。

* **Feature: Ultimate Customization (功能：极致个性化)**
  * **New Settings UI**: A brand new, organized settings page.
  * **全新设置页**: 重新设计的设置页面，分类更清晰。
  * **Overlay Customization**: Support changing background color, text color, corner radius, and
    text size.
  * **悬浮窗自定义**: 支持自定义背景色、文字颜色、圆角大小及文字大小。
  * **Notification Style**: Customizable text prefixes (e.g., "▲/▼") and display modes (
    Total/Up/Down).
  * **通知样式**: 支持自定义上传/下载的前缀字符（如 "▲/▼"）以及显示模式（仅上传/仅下载/总网速）。
  * **Quick Settings Tiles**: Added QS Tiles for Overlay and Notification control.
  * **快捷设置磁贴**: 新增悬浮窗与通知栏显示的系统快捷开关 (QS Tiles)，操作更便捷。
