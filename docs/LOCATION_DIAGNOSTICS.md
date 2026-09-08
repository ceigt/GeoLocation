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
