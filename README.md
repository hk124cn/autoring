# AutoRing

按时间段自动切换「闹钟音量 / 来电·通知模式（响铃·静音·震动）/ 震动开关」的安卓 App。
场景：戴耳塞睡觉 → 闹钟音量最大防睡过头；上班 → 来电/通知静音+震动不打扰别人。

- 数据层：Room（`Profile` 情景模式 + `Schedule` 时间段）
- 调度层：`AlarmManager` 精确闹钟 + `BootReceiver` 开机重排
- 执行层：`AudioProfileManager` 经 `AudioManager` / `NotificationManager` 落地设置
- UI 层：Jetpack Compose，三个 Tab（模式 / 时间段 / 权限保活）

## 运行方式（Android Studio）

1. 安装 [Android Studio](https://developer.android.com/studio)（含 Android SDK）。
2. 打开本工程目录（`File → Open` 选 `auto-ring` 文件夹）。
3. 等待 Gradle 同步完成（首次会下载依赖，需联网）。
   - 若提示缺少 Gradle Wrapper，Android Studio 会自动生成；或本地有 Gradle 时执行 `gradle wrapper`。
4. 手机：设置 → 关于手机 → 连点「版本号」开启开发者选项 → 开启 **USB 调试**。
5. 数据线连电脑，Android Studio 选中该设备，点 **Run**（debug 包免签名）。
6. 首次打开按「设置」Tab 引导授予勿扰权限，并在手机管家做保活设置。

## 发布签名（上架时再用）

1. 用 `keytool` 生成密钥库（详见设计文档第 12.2 节）。
2. 把 `keystore.properties.example` 复制为 `keystore.properties` 并填好，该文件**不要提交**。
3. 取消 `app/build.gradle.kts` 中 `signingConfigs` 与 `buildTypes.release.signingConfig` 的注释。
4. `Build → Generate Signed App Bundle / APK` 产出可安装/可上架包。

## 已知限制（骨架阶段）

- `SchedulesScreen` 的时间段编辑为简化版（分钟固定 0、星期用工作日/周末/每天预设）。
- 手动改动音量的「锁定到下次切换」尚未实现（数据模型已预留，待 M4）。
- 通知震动在不同 ROM 表现不一，需真机（Realme）调优。
