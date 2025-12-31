* **Optimization: Service Compliance (优化：服务合规)**
  * **Android 14+ Adaptation**: Dynamically switch Foreground Service type to `specialUse` to comply
    with Boot restrictions; maintain `dataSync` for legacy compatibility.
  * **Android 14+ 适配**: 动态调整前台服务类型，在 Android 14+ 使用 `specialUse` 以符合开机自启规范，旧版本保持
    `dataSync` 兼容，修复运行时崩溃。

* **Optimization: UI (优化：界面)**
  * **Settings Page**: Optimized the display logic and visual effects of settings items.
  * **设置页**: 优化了设置项的显示逻辑与视觉效果。
