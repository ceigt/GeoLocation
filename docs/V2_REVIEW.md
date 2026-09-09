# GeoLocation 2.0.0 对比与验证

基准：原版仓库 `Xposed-Modules-Repo/com.locusmimic.app`，标签 `20002-2.0.2`。
APK SHA-256：`017da3453ed12c3277aabaf0c6f11c12c59f13b2b29054e7ba578ebac2c09a15`。
实时 GitHub API 和本地文件校验一致；网页缓存可能显示旧版。

对比范围为发布说明、APK 元数据和经用户授权提取的模块入口及应用 Hook 调度。反编译存在失败方法，未进行完整审计，也不能推断所有运行效果。原版文件只保留在工作目录，不进入仓库；没有复制闭源实现或付费逻辑。

| 项目 | 原版 2.0.2 可核对情况 | GeoLocation 2.0.0 |
| --- | --- | --- |
| 初始化 | 模块入口立即安装应用 Hook，并隔离初始化异常 | 保留提前安装；各适配独立处理异常，系统进程不再误装应用 Hook |
| 系统定位 | LocationRegistration 和旧 Receiver 入口 | 保留持续与一次性入口；LocationResult 使用独立副本，避免共享对象串改 |
| 厂商通道 | 可见独立高德适配及更多通道配置 | 腾讯标准/sapp 和 Android 通道；其他私有通道不宣称全面支持 |
| 位置字段 | 静态检查无法证明所有字段行为 | 修复零速度/海拔配置；保留原位置字段有效性标记；缺失精度使用完整默认值 |
| 运行日志 | 发布说明新增导出 | 保留定位诊断，移除类加载器及安装路径输出；不新增收集服务器 |
| 权限 | APK 含悬浮窗及模拟位置等权限 | 不新增悬浮窗权限；禁止应用备份以减少地图凭据外流 |
| 发布 | 独立原版签名 | 新建独立 RSA 3072 正式证书；Release 缺密钥不再退回 Debug 签名 |

## 稳定性修复

- 注册配置监听器后再读取初值，避免窗口期漏更新；模块配置刷新串行化。
- 未选点或坐标无效时，不启用位置替换，避免输出旧值或零点。
- Kotlin 2.2 配合 R8 8.10.21，修复此前编译器元数据版本不匹配警告。依据：https://developer.android.com/build/kotlin-support 。
- 恢复仅勾选三个系统组件的全局系统接口模式；删除 1.2.6 强制选择应用及自动关闭限制。SDK、缓存、网络等通道仍可能返回真实位置，可选应用级适配。
- LocationResult 复制接口核对 Android 15 官方源码的 `asList()` 和 `create(List)`：[AOSP LocationResult.java](https://github.com/aosp-mirror/platform_frameworks_base/blob/android15-release/location/java/android/location/LocationResult.java)。

## 证据边界

本机验证运行 `testDebugUnitTest assembleRelease lintDebug`：16 项单元测试通过。Lint 首轮无错误，仍有 58 项警告及 1 项提示，主要为依赖更新、旧接口、未用资源和无障碍提示；可选外部广播 Receiver 的导出警告仍保留，该组件默认关闭。构建报告不是完整安全审计或真机功能测试。

此前设备日志只证明替换入口命中，不能证明系统结果成功送达、页面采用，或确定失败必由 SDK 覆盖造成。1.2.6 排查文档中该结论过强，本文件予以更正。

2.0 正式发布表示使用正式签名和非预发布 Release，不代表经过所有手机、ROM 和应用实测。没有连接 LSPosed 真机；不能承诺全局全通道覆盖、无法检测或比原版更隐蔽。需要复测开启、修改坐标、停止、重启及多个应用并发定位。

## 签名与升级

2.0 正式证书 SHA-256：`26ffd7b501c54b76a7a39495105cf0f8e596a56f9fca88ec2111f230e19fb5f7`。

正式证书与 1.2.x Debug 证书不同，通常必须保留收藏/坐标和地图凭据后卸载旧版再安装。后续 2.x 保持同一正式密钥即可覆盖升级。
本机密钥在仓库忽略的 `keystore/geolocation-release.p12`，密码配置在忽略的 `keystore.properties`；二者仅授予本机用户和构建沙箱账户访问，均不可公开上传。用户须离线备份这两个文件，丢失将无法继续同签名升级。
GitHub 本轮只上传源码、APK、校验值及公开证书指纹。CI 签名密钥未自动上传；需要 CI 构建时另行配置仓库签名 secrets。
