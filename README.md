# AutoLedger

本地优先的 Android 自动记账 App：监听支付宝 / 微信 / 云闪付的支付通知（无障碍兜底）自动入账，账单只存在本机数据库，**不申请网络权限、不上传任何数据**。

## 功能

- **通知识别**：`NotificationListenerService` 只接收 `com.eg.android.AlipayGphone` / `com.tencent.mm` / `com.unionpay` 三个包名的通知，交给 `AlipayParser` / `WechatParser` / `UnionpayParser` 解析出金额、商户、类型；展开文本与多行通知（`EXTRA_BIG_TEXT` / `EXTRA_TEXT_LINES`）都会参与解析。
- **无障碍兜底**：通知被折叠或吞掉时，读取支付结果页上的「支付成功」等文案补记；只处理窗口状态 / 内容变化事件，2 秒防抖，节点遍历有深度 25、数量 400 的上限。
- **自动分类**：内置 10 组关键词规则（餐饮、交通、购物、娱乐、生活缴费、医疗、转账、红包、其他、未分类），按「先长后短」匹配；在账单里改过分类后会写入「商户 → 分类」规则，之后同一商户优先走学习规则。红包 / 退款 / 收入另有类型特判。
- **去重合并**：同来源、同类型、金额相同、发生时间相差 ≤ 90 秒的通知与无障碍上报视为同一笔，保留信息更全的字段（商户、长文本、更高置信度、无障碍通道优先），不会重复入账。
- **退款冲抵**：退款自动匹配 30 天内、金额相等、商户相符的原支出，累加原账单的 `refundedCents`（支持部分退款），预算按「金额 − 已退」计算。
- **预算与阈值提醒**：支持月总预算与分类预算；用到 80% 和 100% 时各提醒一次（同一个月不重复），入账通知里也会带今日 / 本月累计与预算剩余。
- **账单增删改与手动记账**：账单页按日分组、可折叠；单击打开编辑弹层改金额 / 分类 / 商户 / 备注 / 日期，长按删除；「记一笔」支持支出、收入、红包（收/发）、退款五种类型。
- **待确认队列**：置信度 < 0.8、或通知里读不到金额的记录先进入待确认，可展开看通知原文，选择「确认入账 / 改完再确认 / 丢弃」；金额缺失的会额外提醒补金额。
- **识别日志**：每条通知的结局都会落库——`INSERTED`、`PENDING`、`DUPLICATE_MERGED`、`FILTERED_MARKETING`、`UNPARSED`、`ERROR`；重复的非支付通知 10 分钟内只记一条，避免刷屏。
- **统计图表**：本月支出 / 收入 / 结余、分类占比环形图、近 6 个月趋势，支持切换月份与「红包计入收支」开关。
- **CSV / XLSX 导出**：CSV 为 UTF-8 BOM + CRLF，Excel / WPS 直接打开；XLSX 金额是数字列可直接求和；导出后经 FileProvider 走系统分享面板。
- **桌面小组件**：Glance 实现的 4x2「今日支出」组件，显示今日、本月与预算剩余 / 超支，入账后立即刷新，另有 30 分钟周期刷新兜底。
- **权限引导与自检**：首次启动逐项引导通知使用权、无障碍、通知权限、自启动、省电策略、最近任务锁定；自检面板显示权限状态、通知监听连接状态、最近 5 条识别日志，并提供「重新绑定监听」。

## 技术栈与模块

- 语言 / UI：Kotlin 2.3、Jetpack Compose（Material 3）、Navigation Compose
- 架构：单 Activity + 底部 5 个 Tab（账单 / 统计 / 待确认 / 预算 / 设置），MVVM + Hilt 注入
- 存储：Room（账单、分类、商户规则、预算、识别日志）、DataStore Preferences（保留天数、红包口径、主题）
- 后台：`NotificationListenerService`、`AccessibilityService`、`BroadcastReceiver`（开机重绑）、Glance AppWidget
- 图表：Vico；导出：内置 CSV / XLSX 生成器；不引入 WorkManager（会带进前台服务 / 唤醒锁 / 网络状态权限）

```
:app                     Compose UI、导航、服务、小组件、Hilt 入口
 ├── :core:data          Room 实体 / DAO、DataStore、导出、Hilt Module
 │     └── :core:model
 ├── :core:parser        Alipay / Wechat / Unionpay 解析器与规则
 │     └── :core:model
 ├── :core:domain        Categorizer / DedupEngine / RefundMatcher / BudgetEngine
 │     └── :core:model
 └── :core:model         TxnType、PaySource、Channel、ParsedTxn 等纯 Kotlin 模型
```

依赖方向：`:app` 直接依赖 `:core:data`、`:core:parser`、`:core:domain`、`:core:model`；三个 core 模块都只依赖 `:core:model`，彼此不互相依赖。`:core:model` / `:core:parser` / `:core:domain` 是纯 Kotlin JVM 模块，只有 `:core:data` 和 `:app` 是 Android 模块。

## 版本

| 组件 | 版本 |
| --- | --- |
| AGP | 9.5.2 |
| Kotlin | 2.3.21 |
| KSP | 2.3.14 |
| Hilt | 2.60.1 |
| Room | 2.8.5 |
| Compose BOM | 2026.11.01 |
| Navigation Compose | 2.9.5 |
| Glance | 1.2.0 |
| Vico | 3.3.1 |
| DataStore | 1.3.0 |
| Gradle | 9.6.0 |
| compileSdk | 37.1（API 37 minor 1） |
| targetSdk | 37 |
| minSdk | 31（Android 12） |
| JDK | 17 |

AGP 9 自带 Kotlin 支持：Android 模块只 apply `com.android.application` / `com.android.library`，不再 apply `org.jetbrains.kotlin.android`；纯 JVM 模块用 `org.jetbrains.kotlin.jvm`。

## 构建与测试

需要 JDK 17 与 Android SDK Platform 37.1。在仓库根目录创建 `local.properties`（不要提交）：

```properties
sdk.dir=/path/to/Android/sdk
```

```bash
# 全部 JVM 单测（含 core:data 的 Robolectric 测试）
./gradlew test

# 发布包：版本号由 Gradle property 传入
./gradlew :app:assembleRelease -PversionCode=12 -PappVersionName=0.3.0

# 调试包
./gradlew :app:assembleDebug
```

`versionCode` 默认 1，`appVersionName` 默认 `0.1.0`。`app/release.keystore` 与 `app/keystore.properties` 存在时 release 用它们签名，否则回退 debug 签名。Room schema 导出在 `core/data/schemas`，需要随代码提交。

## CI

工作流 `.github/workflows/android.yml`：

- 触发：push 到 `main`，或手动 `workflow_dispatch`；权限 `contents: write`。
- JDK 17 + Gradle 缓存；安装 `platforms;android-37.1`、`platforms;android-37.0` 与 build-tools。
- 先跑 `./gradlew test --no-daemon`，再用 `-PversionCode=${{ github.run_number }}` 打 release 包。
- 用 `aapt2` 校验 APK 不含 `android.permission.INTERNET`，命中直接失败。
- 成功后创建 Release，tag 为 **`apk-<run_number>`**，资产名 `autoledger.apk`；失败时把编译错误和失败用例逐行以 `::error::` 输出（最多 50 条）。
- 不需要任何 secrets。

## 签名

`app/release.keystore` 与 `app/keystore.properties` 是自用发布签名，**故意随仓库保存**（自用场景，方便任何机器直接出包）。换签会导致手机上无法覆盖安装，只能先卸载旧版并丢失数据，因此不要重新生成：

- `app/build.gradle.kts` 检测到 `app/keystore.properties` 时创建 `release` signingConfig，`storeFile` 相对 `app/` 目录解析。
- 文件不存在时 release 回退 debug 签名，仅供本地临时打包。

## 隐私与权限

只申请三项权限（`app/src/main/AndroidManifest.xml`）：

- `POST_NOTIFICATIONS`：记账提醒与预算阈值提醒。
- `RECEIVE_BOOT_COMPLETED`：开机后重新绑定通知监听、重跑日志清理。
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`：引导页一键申请「省电策略无限制」。

同时用 `tools:node="remove"` **显式移除**依赖可能合并进来的 `INTERNET`、`ACCESS_NETWORK_STATE`、`FOREGROUND_SERVICE`、`WAKE_LOCK`；CI 会再校验一次 APK 里没有 `INTERNET`。`android:allowBackup="false"`，数据不进入系统备份。

无障碍服务的边界（`res/xml/autoledger_accessibility.xml` + service 代码）：

- 只监听支付宝、微信、云闪付三个包名；
- 只订阅窗口状态 / 内容变化，不订阅 `TYPE_VIEW_TEXT_CHANGED`，`canRequestFilterKeyEvents="false"`，不读输入框；
- 只保留与支付关键词匹配的文本，无关节点文本仅在内存里用于定位金额，不落库、不打日志。

识别日志里的通知原文默认保留 **30 天**（设置里可在 1–90 天之间调整），过期后清理日志并把账单上的 `rawLogId` 置空；清理在冷启动与开机时执行，不常驻。

## 已知限制

- 没有做小米「超级岛」等特殊通知样式，入账提醒是普通的高优先级通知（`txn_alerts` 渠道）。
- 退款匹配不到原支出时（超 30 天、金额不等或商户对不上），按普通**收入**记录，不会冲抵任何账单。
- 微信支付的部分卡片通知不带金额，这类记录会进待确认并提示「待补金额」，需要手动补上金额后才计入统计。

手机上的安装与验收步骤见 [docs/verification.md](docs/verification.md)。
