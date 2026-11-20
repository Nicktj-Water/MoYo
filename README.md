# AutoLedger

自用的 Android 自动记账 App：从支付宝 / 微信 / 云闪付的通知或无障碍事件里解析交易，落库、去重、分类、统计。

**不申请 INTERNET 权限**，账单数据只保存在本机（`app/src/main/AndroidManifest.xml` 里用 `tools:node="remove"` 显式移除了 INTERNET）。

当前是第 1 步工程骨架：模块、版本目录、数据库、主题与导航占位都已就位，解析与业务规则在后续步骤实现。

## 模块

```
app                 Compose UI、底部导航、Hilt 入口（Android application）
core/model          纯 Kotlin 领域模型：TxnType / PaySource / Channel / ParsedTxn
core/parser         通知文本解析接口 TxnParser（实现后续补充）
core/domain         去重 / 退款 / 分类 / 预算等业务规则（当前只有占位测试）
core/data           Room（实体、DAO、预置分类）、DataStore 设置、Hilt Module
```

依赖方向：`app -> core:data -> core:model`，`app -> core:parser -> core:model`，`core:domain` 后续承接 `core:parser` 与 `core:data` 之间的规则。

## 构建与测试

需要 JDK 17+（CI 用 21）。

```bash
# 全部 JVM 单测（含 core:data 的 Robolectric 测试）
./gradlew test

# 调试包
./gradlew :app:assembleDebug

# 发布包（版本号从 Gradle property 读取）
./gradlew :app:assembleRelease -PversionCode=1 -PappVersionName=0.1.0
```

`versionCode` 默认 1，`appVersionName` 默认 `0.1.0`。

## CI

`.github/workflows/android.yml`：

- 触发：push 到 `main`、手动 `workflow_dispatch`；权限 `contents: write`。
- JDK 21 + Gradle 缓存；安装 `platforms;android-37.0`。
- 若 `android-37.0` 不可用，回退到 `platforms;android-36` + `build-tools;36.0.0`，并用 `sed` 把 `compileSdk`（必要时含 `targetSdk`）改成 36。
- 跑 `./gradlew test --no-daemon`，再跑 `:app:assembleRelease`（`-PversionCode=${{ github.run_number }}`）。
- 用 `aapt2` 校验 APK 不含 `android.permission.INTERNET`，命中直接失败。
- 失败时从 `build.log` 和 `build/test-results/**/*.xml` 提取错误，逐行 `::error::` 输出（最多 50 条）。
- 成功后把 APK 以 `apk-<run_number>` 为 tag 发布到 GitHub Release，资产名 `autoledger.apk`。
- 不需要任何 secrets。

## 签名

`app/release.keystore` 与 `app/keystore.properties` 是自用发布签名，**故意随仓库保存**（换签会导致无法覆盖安装）：

- `app/build.gradle.kts` 检测到 `app/keystore.properties` 时创建 `release` signingConfig，`storeFile` 相对 `app/` 目录解析。
- 文件不存在时，release 回退用 debug 签名，方便本地临时打包。

## 备注

- AGP 9 自带 Kotlin 支持：Android 模块只 apply `com.android.application` / `com.android.library`，不再 apply `org.jetbrains.kotlin.android`；纯 JVM 模块用 `org.jetbrains.kotlin.jvm`。
- Room schema 导出到 `core/data/schemas`，需要提交到版本库。
