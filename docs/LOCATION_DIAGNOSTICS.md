# 1.2.6 系统级 Hook 作用域组合

1.2.4 已由用户确认：应用级 Hook 下微信和 Google Play 企业微信 5.0.9 均显示模拟位置。系统级 Hook 下，支付宝、京东、高德、美团、微信和企业微信仍显示真实位置。

1.2.5 修复第三方调用包识别后，新一轮真机日志确认系统服务已经替换以下入口：

- 微信：`LocationRegistration.acceptLocationChange`
- 高德：`getLastLocation` 和持续定位回调
- 支付宝：`getLastLocation`
- 美团：`getLastLocation` 和持续定位回调
- 京东：`getLastLocation` 和持续定位回调

真机结果只有高德和美团显示模拟位置。日志中只有 `system` 与 `com.android.phone` 进程加载了 GeoLocation，没有微信、企业微信、支付宝或京东应用进程。这证明这些失败应用没有直接采用系统服务提供的 Android `Location`，而是在应用进程内由腾讯等厂商 SDK 生成或覆盖最终位置。系统服务无法修改只存在于目标应用进程中的 `TencentLocation` 等对象。

因此 1.2.6 将系统级模式恢复为组合模式：目标应用进程 Hook 加系统服务 Hook。LSPosed 作用域必须同时包含每个待测试应用，以及 System Framework、Android System、Phone Services。只有三个系统组件不构成有效配置。应用会在缺少目标应用时拒绝开启系统级模式，并在目标列表变空时自动关闭该模式。

## 1.2.6 手机验证

1. 覆盖安装 1.2.6 debug，打开 GeoLocation 一次。
2. 在“作用应用”中勾选每个待测试应用，例如微信、企业微信、支付宝、京东、高德和美团；企业微信按已确认结果设置为 GCJ-02。
3. 在 LSPosed 的 GeoLocation 作用域中确认上述目标应用仍处于勾选状态，同时勾选 System Framework、Android System 和 Phone Services。
4. 回到 GeoLocation 开启“系统级 Hook”，选点并开始模拟，然后重启手机。逐个冷启动目标应用测试。
5. 新日志应同时出现目标应用进程的 `onModuleLoaded` / SDK 回调替换记录，以及 `system` 进程的系统定位替换记录。若日志仍只有 `system` 和 `com.android.phone`，说明目标应用未进入作用域。

构建测试只能验证模式状态、作用域约束和 Hook 代码可编译。1.2.6 的组合模式仍需上述真机验证。

以下保留 1.2.5 的系统服务实现记录：1.2.5 从 `LocationManagerService` 调用参数和 `LocationProviderManager` 注册对象的 `CallerIdentity` 识别实际接收包。Android 15 持续更新经过 `LocationRegistration.acceptLocationChange`，一次性请求使用同级的 `GetCurrentLocationListenerRegistration.acceptLocationChange`；两个入口均已覆盖。日志首次替换时记录通道和接收包，不记录坐标。

以下保留 1.2.4 企业微信适配记录。

# 1.2.4 企业微信 sapp SDK 适配

实际检查用户导出的 Google Play 企业微信 5.0.9（versionCode 75141）。页面接收 com.tencent.map.geolocation.sapp.TencentLocationListener，并读取 sapp.TencentLocation 经纬度绘制地图。此前只查找标准命名空间，遗漏 sapp。

现按同一命名空间解析 manager/listener/location，验证接口签名后复用回调代理。没有复制企业微信实现或修改其安装包；安装包和反编译文件不进入仓库。原 Android 主动回调保持不变。

回归测试使用独立编写的最小接口替身，验证标准命名空间不存在时仍能识别 sapp。测试不能替代手机验证。

安装 1.2.4 debug 并重启，沿用应用级 Hook 和企业微信 GCJ-02。新日志含义：

- SDK available: ...sapp：SDK 已识别，附实际 Hook 方法总数。
- Listener registration: ...sapp...：注册入口命中及返回码。
- SDK callback replaced: ...sapp.TencentLocation：SDK 回调参数已替换，不等于页面最终结果已验证。

地址/POI 元数据保持 SDK 原值；不新增远程地理编码。原始错误码保持不变。后续动态加载器、私有接口及所有页面的兼容性尚未验证。

以下保留 1.2.3 排查记录。

# 1.2.3 定位回调排查

## 依据与改动

用户提供的 1.2.2 日志确认模块进入目标进程，但启动时找不到公开 TencentLocationManager 类。该日志不能证明实际定位走哪个 SDK，也不能证明配置开关已同步。

参考官方 LocusMimic 2.0.1 APK（SHA-256：af2c8d84f89c5a4fd4a13ab1c0a432016c4143aa096f47744333cccb7f9f520e）的定位流程，确认它包含 Android 监听器登记后的主动派发，以及应用初始化后按配置重试厂商适配。没有证据表明它使用通用 ClassLoader.loadClass 拦截。

本项目独立实现 Android 监听器代理、按原 Executor/Looper 派发、移除后取消排队派发、单次请求取消检查、腾讯适配应用初始化后重试。未复制闭源反编译代码，未修改订阅检查。没有新增权限、网络接口或远程控制。

## 验证状态

自动构建和单元测试只能检查编译、坐标转换及监听器登记逻辑。没有连接可运行 LSPosed 的测试手机，微信/企业微信端到端结果尚未验证，因此发布为 prerelease。

限制：当前不处理 PendingIntent 型主动派发、应用后续自建 DexClassLoader、私有/重命名腾讯 SDK 或厂商服务所有返回通道。腾讯类缺失时继续使用 Android 通道，并明确记录缺失状态。

## 手机验证

1. 覆盖安装同签名 1.2.3，重启手机以卸载旧进程中的 Hook。
2. 保留已选作用域；先选择应用级 Hook，企业微信保持此前测试所需 GCJ-02。地图选点并开启模拟，再启动目标应用请求位置。
3. 验证开始、修改选点、停止三个状态。确认停止后恢复正常定位，避免只测一次静态截图。

日志含义：

- `hookActive=true, mockProvider=false, hasPoint=true`：目标进程读到模拟开关和选点。
- `framework listener registered`：目标进程调用了覆盖的监听器注册入口。
- `active framework callback delivered`：已在目标执行器上调用监听器；不代表目标 UI 接受了结果。
- `framework callback replaced`：真实 Android 回调已被替换。
- `current-location callback delivered`：一次性请求的回调已执行。
- `Optional Tencent class unavailable`：该阶段公开腾讯类不可见，不等于整个模块未加载。

若仍不正确，导出本次 LSPosed 模块日志即可；无需提交地图 Key 或精确坐标。不要把完整设备日志公开到 GitHub。
