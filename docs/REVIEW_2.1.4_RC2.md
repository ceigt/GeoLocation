# 2.1.4-rc2 候选：快捷开关、图标裁切和企业微信诊断

基于现有 2.1.4-rc1 工作区，保留上一轮修复。未发布正式版。

## 依据与改动

- 连接手机的 SystemUI 使用 `android.uid.systemui/10620`。rc1 日志明确记录了对 `com.android.systemui/isLocationEnabledForUser` 和 `com.android.systemui/location_mode` 的开启状态替换。仅以 UID >= 10000 区分普通应用不足以保护快捷设置。rc2 在两类状态查询入口都排除 SystemUI、Settings，检查同 UID 的整个包集合。应用定位数据替换继续使用原有规则。
- 图标前景增加每边 16.67% 留白，将整张图片缩至原前景约 2/3，适应启动器可见区域。原 PNG 保持不变。需在真实圆形启动器验证主体显示。
- 企业微信实测：系统定位开时页面持续加载；一次记录中 GPS/passive 注册约 0.1 秒后注销。rc1 的日志不足以证明回调是否到达 SDK，更不能据此宣称问题已解决。
- 保守撤回 rc1 的按需停止补发轮询，恢复此前每秒检查配置和监听器的方式；保留次数、间隔、距离、权限和取消约束。这是针对回归的候选措施，尚无证据证明休眠优化是唯一根因。代价是闲置时恢复轻量轮询。
- 企业微信新增有界状态诊断：登记、授权拒绝或回调投递。日志不记录坐标和地图凭据。该诊断不等价于目标页面成功。

## 验证计划

构建参数 `APP_VERSION_NAME=2.1.4-rc2`，执行 Debug/Release 单元测试、Release lint 和签名构建。新增测试覆盖 SystemUI、Settings、共享 UID，以及普通定位客户端的状态查询分类。

覆盖安装后需重启卸载旧 system_server Hook。保持三个系统组件作用域，分别测试系统定位开/关、重复进入企业微信定位页、模拟停止后重新开始，并验证快捷磁贴可改变真实系统开关。结果应结合新日志记录，不能将编译通过写成真机通过。

## 构建与安装记录（2026-09-16）

- Debug / Release 单元测试各 53 项通过，失败及错误均为 0。
- Release lint 无错误，42 项警告、1 项提示仍存在。
- 签名 release APK：`GeoLocation-2.1.4-rc2-20260916.apk`，versionCode 20104，非 debuggable。
- 证书 SHA-256：`26ffd7b501c54b76a7a39495105cf0f8e596a56f9fca88ec2111f230e19fb5f7`，与原 release 一致。
- APK SHA-256：`cf10eb020b985d8148cdbca6afa02a80c677aaec2f26566c1d45d79ee55d42c0`。
- 已通过 ADB 覆盖安装，返回 Success，并发起重启。端到端页面结果仍待复测；未发布 GitHub Release。
