# GeoLocation

GeoLocation 是一个面向自有设备和已授权测试环境的 Android 11+ 定位测试工具。它是可独立安装、维护的 LSPosed 模块

## 来源与许可证

本项目从 `wchunlin1006/LocusMimic` 的最后一个公开完整源码标签 `10100-1.1.0`（提交 `2e4c55a`）派生。

该代码继续来源于 [noobexon1/XposedFakeLocation](https://github.com/noobexon1/XposedFakeLocation) 和 [auag0/HideMockLocation](https://github.com/auag0/HideMockLocation)。请保留 [LICENSE](LICENSE) 和 [NOTICE](NOTICE)。

## 当前功能

- LSPosed 应用级 Hook，并在应用内管理目标作用域。
- 系统级 Hook 兼容模式，以及无需 LSPosed 的 Mock Provider 模式。
- 百度、高德和 Google 地图可切换，均支持地图选点、地点搜索和反向地理编码；凭据由用户在应用设置中保存。
- 收藏点，精度、海拔、垂直精度、速度、速度精度、海平面高度和随机偏移。
- 地图和收藏统一以 WGS-84 保存；每个已选择的目标应用可单独输出 WGS-84、GCJ-02 或 BD-09。在“受影响的应用”页面点击绿色坐标系标签即可切换。
- 外部广播控制默认关闭，无订阅、遥测和远程配置服务。

## 安装

1. 安装 [2.1.1 正式 Release](https://github.com/ceigt/GeoLocation/releases/tag/20101-2.1.1) 附件中的正式签名 APK，Android 11 或更高版本。
2. 在支持 libxposed API 101 的新版 LSPosed 中启用 GeoLocation。
3. 打开 GeoLocation，在“受影响的应用”中选择测试目标。切换作用域后重新启动目标应用。
4. 在地图上选点，按需设置坐标系和位置参数，然后开始模拟。

Mock Provider 模式需要在 Android 开发者选项中把 GeoLocation 设为模拟位置信息应用。系统级 Hook 会影响更广的定位链路，只应在受控测试设备上启用。升级时必须使用同一签名；首次换用自己的签名需要先卸载旧的 GeoLocation 构建。

## 构建

要求 JDK 17、Android SDK Platform 36 和 Build Tools 36.0.0：

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
.\gradlew.bat assembleRelease
```

## 安全边界

Manifest 仅导出启动 Activity。Mock Provider 前台服务不导出；可选外部控制 Receiver 默认禁用，只有用户在设置中主动打开后才能接收本机广播。地图功能只会连接当前选择的百度、高德或 Google 地图服务；未接受说明或未打开地图时不会加载地图页面。

完整检查结果见 [SECURITY_REVIEW.md](SECURITY_REVIEW.md)，维护计划见 [MAINTENANCE.md](MAINTENANCE.md)。

仅用于合法、已授权的测试、开发、调试和研究。请勿用于虚假签到、规避平台规则、获取不当利益或侵犯他人权益。
