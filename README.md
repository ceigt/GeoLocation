<div align="center">
  <img src="app/src/main/res/drawable-nodpi/geolocation_icon.png" width="112" alt="GeoLocation 图标" />
  <h1>GeoLocation</h1>
  <p>面向 Android 的定位模拟与调测工具</p>

  <a href="https://github.com/ceigt/GeoLocation/releases/latest"><img src="https://img.shields.io/github/v/release/ceigt/GeoLocation" alt="最新正式版本" /></a>
  <img src="https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&amp;logoColor=white" alt="Android 11+" />
  <a href="LICENSE"><img src="https://img.shields.io/github/license/ceigt/GeoLocation" alt="MIT License" /></a>

  <p>
    <a href="https://github.com/ceigt/GeoLocation/releases/latest">下载正式版</a> ·
    <a href="https://github.com/ceigt/GeoLocation/issues">问题反馈</a> ·
    <a href="#english">English</a>
  </p>
</div>

GeoLocation 支持地图选点、地点搜索、位置收藏和位置参数配置，可通过 LSPosed Hook 或 Android Mock Provider 提供测试位置。项目基于 LocusMimic 的公开源码版本派生，独立维护并公开源码，不包含订阅或付费解锁。

适用于自有设备及获得明确授权的开发、调试和位置功能测试。

## 功能概览

- **三种定位模式**：应用级 Hook、系统级 Hook、Mock Provider，按设备条件和测试范围选择。
- **多地图服务**：支持百度、高德和 Google 地图，提供地图选点、地点搜索与地址解析。
- **常用位置收藏**：保存测试地点，方便重复使用。
- **坐标系转换**：内部统一使用 WGS-84；可为目标应用配置 WGS-84、GCJ-02 或 BD-09 输出。
- **位置参数配置**：精度、海拔、速度、随机偏移，以及垂直精度、速度精度等参数；部分字段受 Android 版本和运行模式限制。
- **可选本机控制接口**：外部广播控制默认关闭，启用后仍需通过同签名权限校验。

## 定位模式与要求

| 模式 | 作用范围 | 使用要求 |
| --- | --- | --- |
| 应用级 Hook | 在选定应用的进程中处理定位调用 | Root，以及支持 **libxposed API 101** 的 LSPosed；启用模块和目标应用作用域 |
| 系统级 Hook | 在系统定位链路中向第三方应用提供测试位置，影响范围更广 | Root、支持 API 101 的 LSPosed；当前版本仅在 **Android 12–16** 启用系统适配器 |
| Mock Provider | 通过 Android 测试定位提供器注入位置 | 无需 Root 或 LSPosed；在开发者选项中将 GeoLocation 选为模拟位置信息应用 |

三种模式互斥。应用最低要求为 **Android 11（API 30）**，这不代表所有模式都在每个系统版本可用。已完成的 Android 16 设备回归范围见[验证记录](docs/REVIEW_FIXES_2.1.8_RC3.md)；其他 ROM 和新系统版本仍需单独验证。

系统级模式是面向第三方定位调用的全局模式，应用列表不是它的全局白名单。除经纬度外，兼容处理还可能涉及定位开关查询、Wi-Fi 扫描和基站信息返回值。Mock Provider 模式则会被 Android 标记为模拟位置，具体提供器支持情况取决于 ROM。

## 下载与安装

从 [GitHub Releases](https://github.com/ceigt/GeoLocation/releases/latest) 下载正式签名 APK。包名为 `io.github.ceigt.geolocation`，使用相同正式签名的版本可直接覆盖升级并保留配置。

### 应用级 Hook

1. 安装 APK，在 LSPosed 中启用 GeoLocation。
2. 在 GeoLocation 的“作用应用”中选择需要测试的应用，并确认它们已进入 LSPosed 作用域。
3. 在设置中选择“应用级 Hook”。修改 LSPosed 作用域后重启设备。

### 系统级 Hook

1. 在 LSPosed 中启用 GeoLocation，勾选 System Framework（`system`）、Android System（`android`）和 Phone Services（`com.android.phone`），然后重启设备。
2. 在 GeoLocation 设置中选择“系统级 Hook”。通常无需再逐个勾选第三方应用；个别应用的兼容性应以实测为准。

### Mock Provider

1. 在 Android“开发者选项 → 选择模拟位置信息应用”中选择 GeoLocation。
2. 在 GeoLocation 中选择“Mock Provider”，授予所需定位权限，并允许其前台服务运行。

### 开始测试

1. 选择地图服务，按需填写地图凭据。
2. 在地图上点选或搜索测试地点，也可使用已保存的收藏点。
3. 按需调整位置参数和应用输出坐标系，点击“开始模拟”。
4. 打开目标应用检查定位结果；未刷新的应用可关闭后重新打开。
5. 测试结束后点击“停止模拟”。

## 地图服务与坐标系

在“设置 → 地图服务”中选择供应商并保存自己的凭据：

| 地图服务 | 配置项 |
| --- | --- |
| 百度地图 | 浏览器端 JavaScript API 的 AK |
| 高德地图 | Web Key 和 `securityJsCode` |
| Google Maps | Google Maps API Key，用于应用内的 JavaScript 地图及检索、地址解析功能 |

地图服务的可用性取决于网络、密钥权限、使用额度及服务商限制。地图加载或搜索失败时，请先核对所选供应商的凭据和控制台配置。

地图显示坐标系与应用输出坐标系分开处理，地图和收藏数据统一按 WGS-84 保存。**通常保留应用输出为 WGS-84**；只有在确认目标应用直接接收其他坐标系时，再点击应用列表中的“输出坐标系”标签切换。不要仅因为底图使用高德或百度就手动改变输出坐标系，以免重复转换造成偏移。

## 隐私与外部控制

- 地图显示、地点搜索和地址解析会向所选地图服务发送相关网络请求。地图凭据在本机通过 Android Keystore 加密存储，不同步到 LSPosed 的远程偏好配置。
- 外部广播入口默认禁用。开启后，调用方仍须使用相同签名并声明 `io.github.ceigt.geolocation.CONTROL` 权限；普通第三方自动化应用和普通 ADB 广播不受支持。接口说明见 [EXTERNAL_CONTROL.md](docs/EXTERNAL_CONTROL.md)。
- 应用关闭了 Android 自动备份。更换签名或卸载应用前，请自行保留需要的配置和收藏数据。

## 维护与反馈

欢迎通过 [Issues](https://github.com/ceigt/GeoLocation/issues) 提交问题或建议。开发过程中使用 AI 辅助，改动结合代码审查、自动化测试与设备回归验证；测试通过不代表所有设备及应用均兼容。

反馈定位问题时，请提供：

- 设备型号、ROM、Android 版本、Root 方案和 LSPosed 版本。
- GeoLocation 版本、运行模式和 LSPosed 作用域。
- Wi-Fi、系统定位和模拟开关状态，以及目标应用版本和复现步骤。
- 与问题时段相关的必要日志；提交前移除账号、真实地址、地图密钥等敏感内容。

历史版本变化见 [Releases](https://github.com/ceigt/GeoLocation/releases)。

## 构建与签名

日常构建优先使用 [GitHub Actions](https://github.com/ceigt/GeoLocation/actions/workflows/verify.yml)。向 `main` 或 `fixes/**` 分支推送后，会运行单元测试、Release Lint 和 APK 构建，也可在 Actions 页面手动触发。成功后可下载 APK 和验证报告，附件保留 14 天。

**云端附件中的 Release APK 未签名，签名后才能安装。** 普通用户请使用 Releases 中已经正式签名的 APK。维护者可在本机使用现有证书签名，无需重新编译；私钥和地图凭据不应提交到仓库。

<details>
<summary>本地构建</summary>

准备 JDK 17、Android SDK Platform 36 和 Build Tools 36.0.0：

```bash
./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease
```

Windows 下使用 `gradlew.bat` 执行相同任务。没有本地签名配置时，Release 构建产物保持未签名。

</details>

发布与签名说明见 [PUBLISHING.md](docs/PUBLISHING.md)。

## 支持项目

如果项目对你有帮助，欢迎通过 [PayPal](https://www.paypal.me/KnoxLeo) 自愿支持后续维护。捐赠不会解锁功能，也不影响正常使用。

## 许可证与上游归属

本项目采用 [MIT License](LICENSE)，从 [wchunlin1006/LocusMimic](https://github.com/wchunlin1006/LocusMimic) 的公开完整源码版本 `10100-1.1.0`（提交 `2e4c55a`）派生，并独立维护。

感谢开源上游：

- [LocusMimic](https://github.com/wchunlin1006/LocusMimic)
- [XposedFakeLocation](https://github.com/noobexon1/XposedFakeLocation)
- [HideMockLocation](https://github.com/auag0/HideMockLocation)

使用或分发派生版本时，请保留 [LICENSE](LICENSE) 和 [NOTICE](NOTICE) 中的版权、许可及归属说明。

## 使用范围

仅用于合法、已授权的测试、开发调试及学习研究。请勿用于虚假签到、规避平台规则、获取不当利益或侵犯他人权益；使用前请确认你对设备、账号、应用和测试场景拥有授权。

---

## English

**GeoLocation** is an independently maintained, MIT-licensed Android location-testing project derived from the public LocusMimic `10100-1.1.0` source release.

It offers Application Hook, System Hook and Mock Provider modes; Baidu, AMap and Google maps; place search and favourites; configurable location parameters; and per-app WGS-84, GCJ-02 or BD-09 output. There are no subscriptions or paid feature unlocks.

Android 11+ is required. Hook modes require Root and an LSPosed implementation supporting **libxposed API 101**. System adapters are enabled only on Android 12–16 and affect third-party location paths globally. Mock Provider requires authorization in Android Developer Options and remains marked as mock location by Android. Compatibility varies by device and app.

Download signed APKs from [Releases](https://github.com/ceigt/GeoLocation/releases/latest). CI artifacts are unsigned and need signing before installation. External control is disabled by default and requires a same-signed companion app; see the [control interface](docs/EXTERNAL_CONTROL.md).

Use only on your own devices or in explicitly authorized test environments. Preserve [LICENSE](LICENSE) and [NOTICE](NOTICE) when redistributing.
