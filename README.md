# GeoLocation

GeoLocation 是一个面向自有设备和已授权测试环境的 Android 11+ 定位测试工具。它是可独立安装、独立维护的 LSPosed 模块，应用 ID 为 `io.github.ceigt.geolocation`，没有订阅、付费墙或远程授权服务。

## 来源与许可证

本项目从 `wchunlin1006/LocusMimic` 的最后一个公开完整源码标签 `10100-1.1.0`（提交 `2e4c55a`）派生。仓库检查显示该标签包含 46 个 Kotlin/Java 源文件；`10201-1.2.1` 及之后的标签不再包含应用源码。本项目没有反编译、复制或绕过后续闭源版本的订阅逻辑。

1.2.3 的兼容性排查参考了官方 2.0.1 APK 的定位监听器登记与应用初始化后重试行为，新增代码独立编写；没有将反编译源码或原版 APK 纳入本仓库。分析范围不涉及订阅授权逻辑。

该代码继续来源于 [noobexon1/XposedFakeLocation](https://github.com/noobexon1/XposedFakeLocation) 和 [auag0/HideMockLocation](https://github.com/auag0/HideMockLocation)。请保留 [LICENSE](LICENSE) 和 [NOTICE](NOTICE)。

## 当前功能

- LSPosed 应用级 Hook，并在应用内管理目标作用域。
- 系统级 Hook 兼容模式，以及无需 LSPosed 的 Mock Provider 模式。
- 百度、高德和 Google 地图可切换，均支持地图选点、地点搜索和反向地理编码；凭据由用户在应用设置中保存。
- 收藏点，精度、海拔、垂直精度、速度、速度精度、海平面高度和随机偏移。
- 地图和收藏统一以 WGS-84 保存；每个已选择的目标应用可单独输出 WGS-84、GCJ-02 或 BD-09。在“受影响的应用”页面点击绿色坐标系标签即可切换。
- 外部广播控制默认关闭，无订阅、遥测和远程配置服务。

## 2.1.1 坐标配置修复

未勾选的应用也可查看、修改输出坐标系，设置坐标不再需要加入 LSPosed 作用域。用户已验证：系统模式只勾选三个系统组件，微信和企业微信均使用 **WGS-84** 时定位准确。升级会保留旧配置；如之前设置过 GCJ-02，请手动检查并改回 WGS-84。不同定位模式不可直接套用同一坐标配置。

## 2.1.0 系统定位修复

补齐系统定位关闭时的连续位置与卫星状态回调，修正首帧派发和授权判断。Android 15 真机上，用户确认速度为 0 时微信、企业微信可获取模拟定位；停止模拟后企业微信无法获取新位置。25 项单元测试通过，静态检查 0 错误。正式优化包已构建，尚未单独实机复测；不保证所有应用或 ROM 均适配。详见 [检查记录](docs/SYSTEM_CALLBACK_REVIEW.md)。

## 安装

1. 安装 [2.1.1 正式 Release](https://github.com/ceigt/GeoLocation/releases/tag/20101-2.1.1) 附件中的正式签名 APK，Android 11 或更高版本。首次从 1.2.x Debug 切换，先保存收藏、坐标和地图凭据，再卸载旧版安装；签名不同，不能直接覆盖。
2. 在支持 libxposed API 101 的新版 LSPosed 中启用 GeoLocation。
3. 打开 GeoLocation，在“受影响的应用”中选择测试目标。切换作用域后重新启动目标应用。
4. 在地图上选点，按需设置坐标系和位置参数，然后开始模拟。

Mock Provider 模式需要在 Android 开发者选项中把 GeoLocation 设为模拟位置信息应用。系统级 Hook 会影响更广的定位链路，只应在受控测试设备上启用。升级时必须使用同一签名；首次换用自己的签名需要先卸载旧的 GeoLocation 构建。

**2.0.0 恢复仅选择 System Framework、Android System、Phone Services 的全局系统接口模式**，GeoLocation 内无需额外选择目标应用。该模式覆盖受支持的 Android 定位接口，不能保证私有 SDK、缓存或网络定位均被替换；有需要时可额外选择应用进行进程内适配。此前日志不足以确定其他应用显示真实位置的具体原因。

本轮参考原版 2.0.2 的部分模块初始化与定位实现，独立修复配置同步、共享定位对象隔离和参数有效性，并启用独立正式签名。1.2.4 应用级模式曾由用户确认微信和企业微信可用；2.0 尚未完成 LSPosed 真机复测，不承诺全应用覆盖或无法检测。详见 [2.0 对比、改动与验证边界](docs/V2_REVIEW.md)。

## 构建

要求 JDK 17、Android SDK Platform 36 和 Build Tools 36.0.0：

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
.\gradlew.bat assembleRelease
```

构建产物位于 `app/build/outputs/apk/`。版本可通过 `-PappVersionName=1.0.1` 指定。项目不提交 `local.properties`、keystore 或密码。

地图凭据可在应用设置中输入，也可在本地 `local.properties` 中配置 `BAIDU_WEB_AK`、`AMAP_WEB_KEY`、`AMAP_SECURITY_CODE` 和 `GOOGLE_MAPS_API_KEY`。这些文件已被 Git 忽略。

正式发布前，把 `keystore.properties.example` 复制为 `keystore.properties`，使用你自己保管的 keystore 填写配置。没有该文件时，release 生成未签名 APK，不能作为正式安装包发布。请离线备份密钥和密码配置，后续发布保持同一签名。详见 [发布与签名说明](docs/PUBLISHING.md)。

## 安全边界

Manifest 仅导出启动 Activity。Mock Provider 前台服务不导出；可选外部控制 Receiver 默认禁用，只有用户在设置中主动打开后才能接收本机广播。地图功能只会连接当前选择的百度、高德或 Google 地图服务；未接受说明或未打开地图时不会加载地图页面。

完整检查结果见 [SECURITY_REVIEW.md](SECURITY_REVIEW.md)，维护计划见 [MAINTENANCE.md](MAINTENANCE.md)。

仅用于合法、已授权的测试、开发、调试和研究。请勿用于虚假签到、规避平台规则、获取不当利益或侵犯他人权益。
