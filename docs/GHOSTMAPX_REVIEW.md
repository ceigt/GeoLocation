# GhostMapX 2.3.2 参考与未验证候选实现

用户提供 APK：`com.ghostmapx.app`，versionCode 25，versionName 2.3.2。
SHA-256：`2dc151c80f80031543e0caf56229ae905290b0d3ba070cce2f451f56ad1df838`。

## 静态证据

经用户授权检查模块入口及关联定位类，没有执行 APK 或复制反编译实现。反编译存在控制流失真，不能等同于完整原始源码。

- 模块入口使用传统 Xposed API，系统服务适配登记持续监听器、取消监听和一次性请求。
- LocationService 类保存 Binder 监听器，登记后可主动生成并调用位置回调，不必等待真实定位。
- 可见 Provider 可用性查询的条件替换，以及一次性请求立即回调路径。
- 可见底层 LocationResult/Location 序列化等适配。不能仅凭这些代码断言所有定位开关查询、私有 SDK 或真实位置来源均被覆盖。

这与 GeoLocation 2.0 的“主要在原生系统回调中替换结果”存在实质差异，为用户描述的关定位仍可收到模拟位置提供了可行解释；这不是完整真机对照验证。

## 本轮候选代码

- 新增 Android 12+ 系统持续监听器补充派发，原生登记先执行，保留其身份、权限和请求验证。
- 补充系统定位开关、Provider 可用性的查询适配；不写入真实系统定位设置。
- 在真实定位关闭、模拟启用、权限与 AppOps 允许时，向支持的监听器派发新位置；真实定位开启时保留已有替换路径。
- 处理监听器取消、Binder 死亡、请求间隔、次数与时限；暂停模拟不继续派发新位置。
- 当前只实现已识别的 Android 12+ 持续监听器签名。一次性请求独立派发、PendingIntent、仅粗略定位权限及私有 SDK 路径尚未完成，不保证所有应用免弹窗或不返回真实位置。

## 验证状态

本轮运行 `testDebugUnitTest lintDebug assembleRelease -PappVersionName=2.1.0-rc1`。构建和单元测试不代替 Binder 回调及 ROM 兼容性真机验证。新增测试检查派发预算的间隔、次数及时限；取消监听与 Binder 死亡路径仍需要设备验证。

## 候选版复测

1. 从正式签名 2.0 覆盖安装 2.1.0-rc1；从旧 Debug 版迁移仍需要先保存数据再卸载。
2. LSPosed 只选择 System Framework、Android System、Phone Services；打开 GeoLocation 一次，选择系统级模式及模拟点，再按 LSPosed 要求重启。
3. 关闭真实系统定位，开始模拟，重新启动测试应用。此候选主动派发要求 Android 12+、目标应用已有精确定位权限；前台使用不需要授予后台定位权限。
4. 分别验证首次位置、修改模拟点、停止模拟。记录是弹窗要求开启定位、无位置，还是返回真实/模拟位置；几种现象的原因不同。
5. 首次成功主动派发时日志为 `[SystemActiveLocation] Supplemental callback sent while real location is off`。该日志仅代表发送调用完成，不证明目标应用界面采用该结果。

候选签名与 2.0 保持一致；不会替换已有 2.0 Release 附件。本轮没有连接真机，不能宣称已经达到 GhostMapX 的全部效果。
