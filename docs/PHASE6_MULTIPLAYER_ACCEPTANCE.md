# Phase 6 多人、视觉与端到端验收

本文件记录 `PLAN_06_MULTIPLAYER_VISUAL_AND_END_TO_END_ACCEPTANCE.md` 的可重复验收方法与证据。自动化通过不替代真人双人流程；没有实际完成的项目必须保留为开放项。

## 隔离运行环境

Gradle 提供四个独立运行配置：

```powershell
.\gradlew.bat runPhase6Server -Pphase6Stage=market
.\gradlew.bat runPhase6ClientA
.\gradlew.bat runPhase6ClientB
.\gradlew.bat runPhase6ClientAdmin
```

可用入口值为 `market`、`warehouse`、`bank`、`company`、`factory`、`securities`、`central_bank`、`boardroom`、`settlement` 和 `exploration`。更换入口值后重启验收服务器；数据包会在 Y=200 建立无地形遮挡的平台，并把三个身份放到目标方块正前方。

- 服务端：`build/phase6/server`，端口 `25566`，独立世界 `phase6-world`。
- 玩家 A：`Phase6PlayerA`，960×540，GUI Scale 2。
- 玩家 B：`Phase6PlayerB`，1280×720，GUI Scale 3。
- 管理员：`Phase6Admin`，1920×1080，GUI Scale 4；离线 UUID 写入隔离世界的 `ops.json`。
- 此配置仅用于本机验收，使用 `online-mode=false`，不得直接复制到公网服务器。

窗口截图工具：

```powershell
.\scripts\acceptance\CapturePhase6Gui.ps1 `
  -Player Phase6PlayerA `
  -Output build\phase6\evidence\market-scale2.png
```

若当前确实有菜单需要先关闭，可额外传入 `-CloseFirst`。脚本按真实 DPI 捕获完整窗口，避免 Windows 缩放导致只截到左上角。

## 已完成的实机证据

- Forge 1.20.1 / 47.4.16 专用服务器成功启动，并加载 Finance 与验收数据包。
- `Phase6PlayerA`、`Phase6PlayerB` 以两个独立 Java/游戏目录实例同时进入同一专服；A 在服务器重启后成功重连。
- `Phase6Admin` 使用与 `ops.json` 一致的离线 UUID 成功进入服务器。
- 八种终端方块在高空验收台正确渲染，未出现紫黑缺失模型；Finance 自有贴图可见。
- 市场终端在 960×540 / GUI Scale 2 实际打开；面板、行情表、输入框和买卖按钮均在窗口内，无模组文字互相覆盖。
- 普通玩家右键受限入口时被服务端拒绝。拒绝提示已改为 actionbar，不再污染聊天历史。

实机截图保存在 `build/phase6/evidence`，该目录是构建产物，不随发布 JAR 分发。

## 自动化资产闭环

GameTest 使用真实方块、`ServerPlayer` 背包和服务端服务层，覆盖：

| 场景 | 提交前 | 提交后 | 守恒断言 |
| --- | --- | --- | --- |
| 仓库存入 | 玩家 12 铁，托管 0 | 玩家 2 铁，托管 10 | 实物减少量等于托管增加量 |
| 玩家市场成交 | 卖方托管 10，买方 0 | 卖方 6，买方 4 | 商品总量不变，卖方现金增加 40 |
| 仓库取出 | 玩家 2 铁，托管 6 | 玩家 8 铁，托管 0 | 实物增加量等于托管减少量 |
| 未授权取出 | 入侵者 0，所有者托管 8 | 数值不变 | 权限失败不移动任何端点 |
| 合同不足量 | 玩家 4 铁，escrow 500 | 数值不变 | 失败不扣货、不付款 |
| 合同完成 | 玩家 5 铁，escrow 500 | 玩家 0，escrow 0，玩家现金 +500 | 目的库存 +5，重复请求不二次付款 |
| 工厂日结 | 公司现金与输出初值 | 现金扣维护费、输出 +100 | `lastProcessedDay` 阻止同日双产 |

JUnit 另覆盖历史版本 13、15、17、19～29 的直接升级。Phase 6 迁移夹具增加仓库、设施和合同坏子记录，连续加载/保存三次并检查账户货币总量、有效引用和数据版本不再变化。

本次统一回归结果（2026-08-24）：

- JUnit：94 个测试套件、383 项测试，0 失败、0 错误、0 跳过。
- Forge GameTest：8 项必需测试全部通过。
- `gradlew build`：通过。
- 发布 JAR：`build/libs/finance-0.4.1.jar`，SHA-256 `1132CAAB9E5E4F80B9A03AADEC2A5C3F9174D6A1CAE4D78F4A8ED56EA18ECD22`。
- `git diff --check`：通过。

## 仍需真人完成的项目

以下项目不能由服务层测试或自动窗口点击冒充通过：

- A/B 在 GUI 中完成授权、出售与购买的完整双人资产表。
- A 创建公司、邀请 B、由 B 绑定仓库和工厂，并等待自然 MC 日完成生产和销售。
- 八个入口分别在 GUI Scale 2、3、4 下检查滚动、按钮、声音、粒子、状态灯和 toast。
- 死亡、跨维度、走远和破坏方块时逐项观察菜单关闭行为。
- 使用一份真实历史世界副本核对股票、债券、基金、期货和保险的业务含义；自动生成 NBT 夹具只证明迁移不变量。

在这些项目完成前，Phase 6 的状态应写作“代码与自动化闭环完成，真人多人验收部分完成”，不能写作全部完成。

## 已知非阻断警告

- Forge 检查到 47.4.23 可用；项目当前锁定 47.4.16，不影响本次启动。
- Forge 用户开发环境会报告若干 Forge 自身 JAR 缺少 `mods.toml`，属于 userdev 日志噪声。
- ForgeGradle 在 Gradle 8.8 下仍报告未来 Gradle 9 的弃用项；当前构建可用，但不能据此宣称 Gradle 9 兼容。
