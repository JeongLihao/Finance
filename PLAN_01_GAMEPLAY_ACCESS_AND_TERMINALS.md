# 长任务计划一：玩法入口分层、轻量界面与金融终端骨架

> 预计执行时间：6～12 小时，取决于资源文件、菜单注册和客户端联调次数。
>
> 前置总计划：`FINANCE_MINECRAFTIZATION_LONG_PLAN.md`
>
> 执行目录：`D:\MCMOD\Finance`
>
> 本计划可以直接交给另一个 Codex 窗口。执行窗口应持续实现、测试、修复，直到本计划验收项完成，而不是只输出设计建议。

---

## 1. 本轮目标

建立“Minecraft 普通玩法入口”和“高级金融入口”的正式代码边界，同时保留全部现有金融功能。

完成后应达到：

- 新玩家通过“金融账本”进入轻量钱包界面。
- 世界中存在市场终端、仓库控制器、公司办公桌、银行柜台、证券终端和央行控制台的注册骨架。
- 不同入口只能请求其允许的界面模式。
- 旧完整 `FinanceScreen` 继续存在，并作为高级金融界面从证券终端打开。
- 管理员界面只能从有权限的央行控制台或明确兼容入口打开。
- 旧快捷键和旧存档保持兼容。
- 不实现仓库真实物品托管和合同逻辑；这些属于计划二。

本轮不是视觉重制，也不是删除金融分页。重点是建立后续所有 Minecraft 化功能所依赖的入口、权限、菜单和注册基础。

---

## 2. 绝对约束

1. 不删除或停用现有金融管理器。
2. 不删除 `FinanceScreen` 的任何现有分页。
3. 不修改已有 NBT key。
4. 不让客户端自行决定管理员权限或可见功能。
5. 不覆盖工作树中的用户修改。
6. 每个阶段必须保持 `compileJava` 和测试通过。
7. 新字符串优先使用翻译 key，不继续大量硬编码中文。
8. 方块和物品注册使用 Forge 1.20.1 推荐的 DeferredRegister 模式。
9. 如果发现现有 `FinanceScreen`、`MarketOverviewScreen` 有未提交用户修改，必须先阅读 diff，再做最小重叠编辑。
10. 不顺手重构金融算法、订单撮合、银行总账或存档序列化。

---

## 3. 开工检查

执行并记录：

```powershell
git status --short
git diff -- src/main/java/finance/client/FinanceScreen.java
git diff -- src/main/java/finance/client/MarketOverviewScreen.java
.\gradlew.bat cleanTest test
```

检查是否存在 `AGENTS.md`。若存在，完整阅读并遵循。

阅读以下文件，不要基于文件名猜测：

- `src/main/java/finance/FinanceMod.java`
- `src/main/java/finance/config/FinanceConfig.java`
- `src/main/java/finance/registry/ModMenus.java`
- `src/main/java/finance/network/FinancePacketHandler.java`
- `src/main/java/finance/network/OpenFinanceGuiPacket.java`
- `src/main/java/finance/gui/FinanceGuiOpener.java`
- `src/main/java/finance/gui/FinanceMenu.java`
- `src/main/java/finance/client/ClientSetup.java`
- `src/main/java/finance/client/FinanceKeyMappings.java`
- `src/main/java/finance/client/FinanceScreen.java`
- `src/main/resources/META-INF/mods.toml`
- `build.gradle`

基线期望：304 个测试通过。如果测试数量已变化，以执行时实际结果为准，并说明原因。

---

## 4. 任务 A：建立 gameplay 包和入口模型

### 4.1 新增包

建立：

```text
finance/gameplay/
finance/gameplay/access/
finance/gameplay/menu/
```

如果执行中发现包层级过深，可合并，但必须保持“入口验证”和“金融内核”分离。

### 4.2 新增 `FinanceTerminalType`

枚举建议值：

```java
PORTABLE_LEDGER,
MARKET_TERMINAL,
WAREHOUSE_CONTROLLER,
BANK_COUNTER,
COMPANY_DESK,
SECURITIES_TERMINAL,
BOARDROOM_TABLE,
CENTRAL_BANK_CONSOLE,
LEGACY_FULL_SCREEN
```

为每个枚举定义允许的屏幕模式，而不是在多个数据包中复制 switch。可以通过方法或独立策略类实现。

### 4.3 新增 `FinanceScreenMode`

建议：

```java
WALLET,
MARKET,
WAREHOUSE,
COMPANY,
BANK,
ADVANCED,
ADMIN
```

屏幕模式是玩家界面意图，不是业务权限。最终授权必须结合入口类型、服务端玩家和方块状态。

### 4.4 新增访问上下文

创建不可变数据结构 `FinanceAccessContext`，至少包含：

- 玩家 UUID
- 入口类型
- 请求屏幕模式
- 可选维度 ID
- 可选 BlockPos
- 请求时服务端 tick

不要把 `ServerPlayer` 直接持久化在上下文中。可以在验证方法参数中传入。

### 4.5 新增访问决策

创建 `FinanceAccessDecision`：

- `allowed`
- `reasonKey`
- `resolvedMode`
- 可选入口位置

创建 `FinanceAccessService`：

- `validatePortableLedger(...)`
- `validateTerminal(...)`
- `mayOpen(...)`
- `allowedModes(...)`

验证要求：

- 入口和模式匹配。
- 管理员模式要求权限等级 2。
- 世界终端要求同维度、方块存在、距离合理。
- 证券终端允许 ADVANCED，不允许 ADMIN。
- 央行控制台只允许有权限者进入 ADMIN，可选允许 ADVANCED。
- 旧兼容入口受配置控制。

访问服务尽量是纯逻辑，可在无 Minecraft 世界对象的情况下测试大部分矩阵。

### 4.6 单元测试

新增 `FinanceAccessServiceTest`，覆盖全部入口 × 模式矩阵。至少包括：

- 账本允许 WALLET，不允许 ADVANCED/ADMIN。
- 市场终端允许 MARKET。
- 仓库控制器允许 WAREHOUSE。
- 公司办公桌允许 COMPANY。
- 银行柜台允许 BANK。
- 证券终端允许 ADVANCED。
- 央行控制台普通玩家被拒绝。
- 管理员可从央行控制台进入 ADMIN。
- LEGACY_FULL_SCREEN 在配置允许时兼容 ADVANCED。
- 空入口、空模式被拒绝。

---

## 5. 任务 B：增加 Minecraft-first 配置

### 5.1 在 `FinanceConfig` 增加 gameplay 配置区

至少加入：

- `minecraftFirstMode = true`
- `enablePortableLedger = true`
- `requirePhysicalTerminal = true`
- `legacyFullScreenKeybind = false`
- `advancedFinanceRequiresTerminal = true`
- `adminConsoleRequiresPermission = true`
- `terminalInteractionDistance = 8.0`

为配置写安全 getter，并保持未加载 Forge 配置时的默认值行为，参照已有 getter 风格。

### 5.2 配置含义

- `minecraftFirstMode=false`：尽可能恢复 0.4.1 的快捷键打开完整界面行为。
- `minecraftFirstMode=true`：快捷键默认打开钱包；完整金融界面通过证券终端进入。
- `requirePhysicalTerminal=false`：允许通过旧入口打开相关模式，方便服务器兼容。
- 配置只控制新入口，不停止已有债券、贷款、基金、期货、保单的结算。

### 5.3 配置测试

如 ForgeConfigSpec 在纯测试中难以动态修改，不要硬造脆弱反射测试。优先测试访问策略接受显式配置快照，生产层再从 `FinanceConfig` 构造快照。

建议新增 `GameplayConfigSnapshot`，使权限矩阵可测试。

---

## 6. 任务 C：注册物品、方块和 BlockEntity 骨架

### 6.1 注册类

新增：

- `finance.registry.ModBlocks`
- `finance.registry.ModItems`
- `finance.registry.ModBlockEntities`

按 Forge 1.20.1 DeferredRegister 方式注册，并在 `FinanceMod` 构造器中挂接 mod event bus。

不得改变现有 `ModMenus.register(...)` 的行为，除非为统一注册做最小、安全改造。

### 6.2 第一批注册对象

物品：

- `portable_ledger`

方块：

- `market_terminal`
- `warehouse_controller`
- `bank_counter`
- `company_desk`
- `securities_terminal`
- `central_bank_console`

`boardroom_table` 可以注册占位，也可以留到公司治理阶段；若注册，应有最小可用交互或明确 tooltip，避免无功能方块。

### 6.3 方块类设计

不要为每个终端复制整套 use 方法。建议：

- 抽象 `FinanceTerminalBlock`
- 字段保存 `FinanceTerminalType`
- `use` 在服务端调用统一打开服务
- 客户端返回适当 InteractionResult，不执行金融操作

仓库控制器未来需要 BlockEntity。计划一先注册稳定身份和占位 BlockEntity：

- 保存 `warehouseId`
- 保存 `ownerId`（首次交互或放置时赋值）
- 保存创建状态
- 暂不保存商品数量

如果所有终端都使用 BlockEntity 会增加复杂度，则只有仓库控制器使用。其他终端可由方块位置和类型验证。

### 6.4 物品交互

`PortableLedgerItem.use(...)`：

- 只在服务端发起钱包菜单打开。
- 检查配置是否允许。
- 不直接调用客户端 API。
- 不修改账户余额。

### 6.5 资源文件

为所有已注册内容添加：

- blockstates
- block models
- item models
- loot tables
- recipes（央行控制台除外）
- `assets/finance/lang/zh_cn.json`
- `assets/finance/lang/en_us.json`

如果仓库当前没有 assets 目录，正确建立标准资源结构。

首版模型可以使用可靠的原版父模型和简单纹理占位，但不能让游戏因缺资源紫黑或报 missing model。若没有自定义 PNG，可让模型引用适合的原版纹理或创建最小合法资产；后续再做美术替换。

### 6.6 配方建议

- 账本：book + paper + gold_nugget。
- 市场终端：iron_ingot + redstone + glass_pane。
- 仓库控制器：chest + iron_ingot + redstone。
- 银行柜台：smooth_stone + iron_ingot + gold_ingot。
- 公司办公桌：crafting_table/lectern + paper + iron_ingot。
- 证券终端：市场终端 + quartz + diamond。
- 央行控制台：无普通配方。

配方具体形状可调整，但要保持生存进程：账本早期、市场中期、证券终端后期。

### 6.7 注册验证

- `compileJava` 通过。
- `processResources` 通过。
- 无重复 registry name。
- 专用服务端类加载不引用客户端类。

---

## 7. 任务 D：统一服务端打开流程

### 7.1 新增打开服务

建议新增 `FinanceGameplayOpener`：

- `openPortableLedger(ServerPlayer)`
- `openTerminal(ServerPlayer, BlockPos, FinanceTerminalType)`
- `openLegacy(ServerPlayer)`

它负责：

1. 生成访问上下文。
2. 调用访问服务。
3. 选择菜单 provider。
4. 将屏幕模式、入口类型、位置写入菜单额外数据。
5. 失败时发送翻译消息或现有反馈包。

不要让每个方块直接复制 `NetworkHooks.openScreen` 的数据编码。

### 7.2 改造 `OpenFinanceGuiPacket`

现有包应继续支持旧调用方。可采用以下之一：

- 保留无参构造表示旧兼容请求，新增 mode/terminal 字段；
- 新增独立 `OpenFinanceModePacket`，旧包委托到统一打开服务。

优先选择兼容风险最低的方案。解码时严格限制枚举和值。

客户端声称来自某个终端时，服务端必须检查该位置实际方块类型。不能只看枚举。

### 7.3 修改快捷键

`FinanceKeyMappings`：

- Minecraft-first 开启：发送打开 WALLET 请求。
- 旧模式或明确兼容配置：维持完整界面。
- 若账本物品被要求但玩家没有，不要静默失败；显示如何制作或使用终端。

如果快捷键在客户端无法安全读取服务端 COMMON 配置，服务端收到统一请求后决定打开哪个模式。

### 7.4 会话有效性

扩展菜单 `stillValid`：

- 钱包模式可始终有效（玩家仍在线）。
- 终端模式检查方块仍存在、类型匹配、距离合法。
- 跨维度失效。

不要把验证只放在打开瞬间。

---

## 8. 任务 E：轻量钱包菜单和屏幕

### 8.1 新菜单

新增 `WalletMenu`，只同步：

- balance
- frozenBalance
- 最近最多 10 条当前玩家相关交易
- 可选总资产摘要
- 当前世界日期

不要复用 `FinanceMenu.writeAll` 发送完整世界金融数据。

对列表数量和字符串长度设严格上限，解码前检查。

### 8.2 新屏幕

新增 `WalletScreen`：

- 视觉上使用 Minecraft 容器风格。
- 显示金币、锁定金币、最近交易。
- 有“转账”输入或按钮；如实现会显著扩大范围，可先提供跳转/命令提示，但优先实现安全的小额转账包。
- 显示“使用市场终端交易商品”的引导。
- 不显示股票、基金、期货、银行总账等内容。

### 8.3 转账

如果增加钱包转账：

- 使用独立包或复用安全的现有转账逻辑。
- 服务端解析目标玩家，不接受客户端提交余额。
- 金额必须正数且防溢出。
- 禁止自转账。
- 增加频率限制。

如目标选择 UI 过于复杂，可以第一轮只保留余额和记录，将转账留给现有 `/pay`，但必须在界面上明确提示。

### 8.4 客户端注册

在 `ClientSetup` 注册新 MenuScreen，不把客户端类加载到服务端路径。

### 8.5 编解码测试

新增：

- `WalletMenuCodecTest`
- 过大交易记录数量被拒绝。
- 过长 object name 被截断或拒绝。
- 正常数据 round-trip。
- 只有当前玩家相关记录被包含。

---

## 9. 任务 F：终端菜单路由

### 9.1 初期复用策略

为控制范围，本轮可让以下终端路由到现有完整菜单，但指定初始分页/模式：

- 市场终端 → MARKET
- 银行柜台 → BANK
- 公司办公桌 → COMPANY
- 证券终端 → ADVANCED
- 央行控制台 → ADMIN

仓库控制器先打开占位 `WarehouseMenu`，显示仓库身份和“真实物品托管将在下一阶段启用”。如果不希望发布占位功能，可让它显示服务端消息，但注册和 BlockEntity 必须完成。

### 9.2 扩展现有菜单初始化数据

`FinanceMenu` 增加：

- `FinanceScreenMode initialMode`
- `FinanceTerminalType sourceType`
- 可选 BlockPos

必须保持旧构造器兼容测试。客户端读取无新字段的旧调用路径时使用 `ADVANCED` 或现有默认分页。

### 9.3 `FinanceScreen` 最小改造

只做以下变化：

- 根据 `initialMode` 选择初始 tab。
- 根据入口类型决定可见 tab 集合。
- 提供返回/关闭行为。
- 不删除原 tab、不重写渲染系统。

注意当前文件可能有用户修改。必须最小化 diff，避免格式化整个 3,200 行文件。

### 9.4 服务端防绕过

隐藏 tab 只是表现。所有管理员和敏感操作包继续独立检查权限。对于只能从特定终端使用的新操作，将来应验证菜单会话；本轮至少确保 ADMIN 权限不可绕过。

---

## 10. 任务 G：翻译、提示与教程文本

新增翻译 key 分组：

```text
item.finance.portable_ledger
block.finance.market_terminal
block.finance.warehouse_controller
block.finance.bank_counter
block.finance.company_desk
block.finance.securities_terminal
block.finance.central_bank_console
screen.finance.wallet.*
message.finance.access.*
tooltip.finance.*
```

中英文至少覆盖所有新对象和失败原因。

Tooltip 应告诉玩家用途，例如：

- 金融账本：查看金币与最近交易。
- 市场终端：交易真实商品并查看收购价格。
- 证券终端：访问股票、债券、基金与期货。
- 央行控制台：仅管理员使用的经济管理设备。

不要在本轮撰写庞大教程书；只提供可发现性文本。

---

## 11. 任务 H：兼容与清理

### 11.1 客户端缓存

登出时清理新增钱包/模式缓存。参考 `ClientConnectionEvents`。

### 11.2 世界卸载

如果新增运行时入口会话或仓库索引，在服务器停止时清理。不要把 BlockEntity 自身存储误放进全局静态集合，除非有明确重建逻辑。

### 11.3 旧界面兼容

验证：

- 现有命令仍能打开完整界面。
- 旧 `OpenFinanceGuiPacket` 不崩溃。
- `FinanceMenuDashboardCodecTest` 等原测试仍通过。
- 现有客户端缓存不因屏幕分流泄漏。

---

## 12. 测试矩阵

至少执行：

```powershell
.\gradlew.bat compileJava
.\gradlew.bat cleanTest test
git diff --check
```

如能运行开发客户端，手动验证：

1. 新物品和方块出现在物品栏/命令获取中。
2. 账本右键打开 WalletScreen。
3. 市场终端打开市场模式。
4. 证券终端打开完整高级界面。
5. 普通玩家使用央行控制台被拒绝。
6. 管理员能打开管理员模式。
7. 走远、破坏终端或跨维度后菜单失效。
8. 专用服务器启动不因客户端类引用崩溃。
9. 方块模型、物品模型、名称和配方无 missing。

如果环境不允许启动客户端，必须说明未执行的手动项目，并用注册/资源检查尽量补偿。

---

## 13. 建议提交/检查点

执行窗口不必擅自创建提交，除非用户明确要求；但工作应按以下检查点组织：

### 检查点 1

- gameplay 入口模型
- 配置快照
- 访问矩阵测试

### 检查点 2

- 方块、物品、BlockEntity 注册
- 资源和配方
- 编译通过

### 检查点 3

- 统一打开服务
- 快捷键兼容
- 菜单 stillValid

### 检查点 4

- WalletMenu/WalletScreen
- 编解码和边界测试

### 检查点 5

- 现有 FinanceScreen 初始模式和 tab 分流
- 全量回归测试
- 文档更新

每个检查点结束都应更新执行记录，避免窗口中断后不知道完成到哪里。

---

## 14. 执行日志要求

在项目内新增或更新 `docs/MINECRAFTIZATION_PROGRESS.md`，每个检查点记录：

- 完成日期
- 完成的任务
- 修改的关键文件
- 测试命令和结果
- 尚未验证的手动项目
- 下一检查点
- 已知风险

不要把完整 Gradle 日志提交进仓库。

---

## 15. 完成标准

只有以下全部满足，本计划才算完成：

- Minecraft-first 配置存在并具有安全默认值。
- 入口类型、屏幕模式和访问服务完成。
- 账本物品可打开轻量钱包界面。
- 至少六个终端/设施方块完成注册和资源。
- 仓库控制器拥有持久身份 BlockEntity 骨架。
- 证券终端仍能访问全部现有金融功能。
- 央行控制台权限由服务端验证。
- 旧快捷键/旧完整界面有兼容路径。
- 新菜单协议有长度上限和测试。
- 无旧测试回归，完整测试全部通过。
- 没有覆盖用户原有未提交修改。
- `docs/MINECRAFTIZATION_PROGRESS.md` 记录已完成工作。

---

## 16. 完成后的交接输出

执行窗口最终应给出：

1. 已实现的玩家流程。
2. 新增和修改的文件清单。
3. 入口权限矩阵。
4. 旧模式兼容说明。
5. 测试总数、成功/失败情况。
6. 未执行的客户端手动验证。
7. 计划二开始前必须知道的仓库 BlockEntity 和菜单结构。

如果遇到无法安全解决的注册、菜单同步或用户修改冲突，应保留已验证成果，清楚报告具体阻塞文件与原因；不要通过删除现有代码或覆盖用户修改绕过问题。

