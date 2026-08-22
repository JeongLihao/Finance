# 长任务计划二：实体仓库、真实物品托管与交付合同

> 预计执行时间：10～18 小时，取决于 BlockEntity、菜单、物品原子结算和存档迁移联调。
>
> 必须在计划一完成后执行。
>
> 前置文件：`FINANCE_MINECRAFTIZATION_LONG_PLAN.md`、`PLAN_01_GAMEPLAY_ACCESS_AND_TERMINALS.md`
>
> 执行目录：`D:\MCMOD\Finance`
>
> 目标不是建立另一个虚拟库存，而是把真实 Minecraft 物品安全连接到现有商品托管和市场系统。

---

## 1. 本轮目标

完成第一个真正可玩的 Minecraft 经济闭环：

```text
采集真实物品
   ↓
通过仓库控制器存入市场托管
   ↓
在市场终端出售、挂单或用于合同交付
   ↓
获得真实账户资金
   ↓
从仓库提取未锁定商品
```

同时实现首批交付/采购合同，让市场短缺和公司需求能够驱动玩家回到世界采集与运输。

完成后：

- `CommodityInventoryManager` 继续作为权威托管账本。
- 仓库控制器拥有所有者、容量、绑定和持久化状态。
- 玩家可把注册商品对应的真实 ItemStack 原子存入/提取。
- 市场卖单锁定的商品不能被提取或重复使用。
- 合同奖励有明确资金来源，不能凭空重复发钱。
- 旧存档中的虚拟商品全部保留，并作为历史托管资产继续可用。

---

## 2. 前置确认

计划二开工前必须确认计划一已经完成：

- `FinanceTerminalType` 和 `FinanceScreenMode` 存在。
- 仓库控制器方块与 BlockEntity 已注册。
- 市场终端和钱包可正常打开。
- 证券终端能访问旧完整金融界面。
- 完整测试通过。

执行：

```powershell
git status --short
.\gradlew.bat cleanTest test
```

阅读计划一的 `docs/MINECRAFTIZATION_PROGRESS.md`，了解实际命名和未完成风险。不要假设计划一严格使用了建议类名；以仓库现状为准调整本计划。

重点阅读：

- `Commodity.java`
- `CommodityRegistry.java`
- `CommodityInventory.java`
- `CommodityInventoryManager.java`
- `CommodityInventorySavedData.java`
- `InventoryUtil.java`
- `MarketManager.java`
- `NpcMarketMaker.java`
- `TradeActionPacket.java`
- `InventoryActionPacket.java`
- `FinanceGuiOpener.java`
- `FinanceMenu.java`
- 计划一新增的仓库、市场终端、访问和菜单类

---

## 3. 绝对约束

1. 不删除或重置旧 `CommodityInventoryManager` 资产。
2. 不允许存入/提取复制物品。
3. 不允许客户端提交它声称拥有的物品数量作为权威值。
4. 不允许提取已经被卖单锁定/移出可用托管的商品。
5. 不允许合同奖励直接无来源 `deposit`。
6. 不把完整仓库商品数据复制存入 BlockEntity NBT。
7. 不在客户端执行容器扣除。
8. 不每 tick 扫描世界寻找仓库。
9. 不为了新增仓库修改现有撮合价格、时间优先或 NPC 定价算法。
10. 所有失败路径必须保持物品和资金守恒。

---

## 4. 任务 A：定义仓库领域模型

### 4.1 新增仓库数据

建议建立：

- `WarehouseRecord`
- `WarehouseStatus`
- `WarehousePermissionMode`
- `WarehouseManager`
- `WarehouseService`
- `WarehouseActionResult`
- `WarehouseKey`（如需要）

`WarehouseRecord` 至少包含：

- warehouseId
- dimensionId
- blockPos
- ownerId
- optional companyId
- capacityUnits
- status
- createdDay
- lastAuditDay
- permissionMode

不要在此对象中保存每种商品数量；现有商品托管按玩家 UUID 管理。首版可把仓库映射到一个托管主体 UUID。

### 4.2 托管主体设计

必须明确商品记在谁名下。推荐方案：

- 个人仓库：托管资产仍记在玩家 UUID 下，最大程度兼容现有订单。
- 公司仓库：未来记在公司或专用 custody UUID 下；本轮先只实现个人仓库，或对公司仓库做只读占位。

不要贸然把旧玩家库存迁移到仓库 UUID，否则所有订单和余额关系都可能断裂。

### 4.3 容量单位

首版使用“商品单位总数”而非槽位模拟：

```text
used = 玩家托管库存中所有正数商品数量之和
capacity = 仓库等级提供的总单位数
```

但一个玩家可能拥有多个仓库。建议：

- 玩家总容量 = 其 ACTIVE 仓库容量之和。
- 旧存档没有仓库时，已有库存标记为 legacy excess，不丢失。
- 新存入要求 `used + amount <= max(capacity, legacyBaseline?)`。

更清晰的兼容策略：

- 读取首次仓库系统启用时的现有数量为 `legacyAllowance`。
- 可用上限 = capacity + 尚未消耗的 legacyAllowance。
- 玩家取出旧库存后 legacyAllowance 相应下降，不会永久获得额外容量。

如该模型过于复杂，可采用更简单、安全策略：

- 当 used > capacity 时禁止继续存入，但允许提取和市场结算。
- used <= capacity 后恢复存入。

优先选择后者，易验证且不会丢资产。

### 4.4 状态

建议：

- ACTIVE
- OVER_CAPACITY
- DISABLED
- ORPHANED

状态大多为派生或管理状态，不能仅因区块未加载就把仓库标记 ORPHANED。

### 4.5 所有权

- 放置仓库控制器时记录 placer UUID。
- 只有所有者或管理员可以绑定/解绑。
- 普通交互检查所有权。
- 方块复制/结构加载不能复制相同 warehouseId；检测重复 ID 时生成新 ID，但不能复制资产。

---

## 5. 任务 B：仓库持久化与运行时索引

### 5.1 新增序列化器

建立 `WarehouseDataSerializer`，由 `EconomySavedData` 调用。

保存：

- 仓库记录
- 必要的容量/权限
- 版本字段
- 可选审计状态

加载时：

- 校验 UUID、维度 ID、坐标范围、容量和枚举。
- 单条坏记录跳过。
- 重复 warehouseId 隔离。
- 不要求区块当前已加载。
- 不扫描整个世界确认方块；等区块加载或交互时再校验。

### 5.2 BlockEntity 与 SavedData 一致性

BlockEntity 保存 warehouseId 和 ownerId 的最小镜像。权威仓库记录保存在 SavedData/manager。

交互时：

- BlockEntity 无 ID → 服务端注册新仓库。
- 有 ID 且 manager 有记录 → 校验位置/所有者。
- 有 ID 但 manager 无记录 → 创建恢复记录或标记 orphan，不能继承不存在的资产。
- manager 有记录但方块不存在 → 暂不自动删除；通过显式拆除/审计处理。

### 5.3 世界清理

在 `EconomySavedData.resetRuntimeState()` 清空仓库管理器和索引。

### 5.4 持久化测试

- 正常 round-trip。
- 坏 UUID/枚举/负容量只隔离单条记录。
- 重复 ID 不复制容量或所有权。
- 旧存档没有仓库根节点时安全加载空仓库列表。
- 重复 load 不增加仓库或商品。

---

## 6. 任务 C：仓库权限和会话

### 6.1 权限服务

新增：

- `canView`
- `canDeposit`
- `canWithdraw`
- `canConfigure`

首版规则：

- 所有者拥有全部权限。
- 管理员拥有修复/审计权限，但管理员代操作必须记录。
- 非所有者默认不可提取。
- 可选 PUBLIC_DEPOSIT 允许其他玩家存入，但资产归属必须清晰；首版可以不启用。

### 6.2 菜单有效性

每次按钮操作重新验证：

- 玩家在线。
- 同维度。
- 方块位置匹配。
- 距离在配置范围。
- warehouseId 匹配当前 BlockEntity。
- 权限未变化。

### 6.3 拆除行为

首版安全策略：

- 仓库仍含玩家托管资产时允许拆方块，因为资产在玩家托管账户中，不随方块销毁。
- 拆除后容量减少，玩家可能进入 OVER_CAPACITY。
- 玩家仍可从新仓库或兼容入口提取旧资产。
- 拆除不掉落虚拟商品，防止双份。

向玩家明确提示“仓库拆除不会删除托管资产，但超容量时无法继续存入”。

---

## 7. 任务 D：安全物品映射

### 7.1 商品到 Item 的映射

只允许：

- `CommodityRegistry` 中存在商品。
- `commodity.getItemId()` 非空。
- ResourceLocation 合法。
- BuiltInRegistries.ITEM 中解析到非 AIR 的合法物品。

纯虚拟商品不能通过仓库存取真实物品，但仍可通过旧金融系统存在。

### 7.2 物品识别

首版只接受基础 Item 身份相同的物品，不接受带特殊 NBT 的自定义变体，避免把附魔/命名/容器内容物品按普通商品吞掉。

建议拒绝：

- 有自定义 NBT 的 ItemStack（除非明确白名单）
- 有耐久损耗的工具
- 装有内容的容器类物品

默认商品铁锭、小麦、石头不受影响。

### 7.3 新工具类

不要无限扩张现有 `InventoryUtil`。可新增 `CommodityItemResolver` 和 `InventoryTransactionService`。

`InventoryTransactionService` 提供：

- countEligible
- planRemoval
- commitRemoval
- rollbackRemoval
- planInsertion
- commitInsertion

计划对象保存具体槽位与数量，提交前再次验证栈未变化。

所有调用在服务器线程进行。

---

## 8. 任务 E：原子存入

### 8.1 API

建议：

```java
WarehouseActionResult deposit(
    ServerPlayer player,
    UUID warehouseId,
    String commodityId,
    int requestedAmount,
    String operationKey
)
```

### 8.2 预检顺序

1. 参数、数量和 operation key。
2. 仓库存在、ACTIVE、位置和权限。
3. 商品存在并可映射真实 Item。
4. 玩家背包拥有足够合格物品。
5. 容量足够。
6. `CommodityInventoryManager.canAddCommodity` 成功。
7. 构造背包扣除计划。

### 8.3 提交顺序

推荐：

1. 扣除真实物品。
2. 增加托管商品。
3. 若增加失败，按原槽位回滚物品；原槽位不可用时放入背包；仍失败则掉落，并记录严重错误。
4. 写仓库流水和统一交易记录。
5. 标记存档脏。
6. 记录 operation key。

因已有 `CommodityInventoryManager.addCommodity` 在预检后通常不会失败，应将异常补偿作为防御路径。

### 8.4 幂等

同一玩家、同一 operation key 的成功存入不能重复执行。保存必要的最近操作记录，使用有界集合。

不要仅依靠客户端禁用按钮。

### 8.5 测试

- 正常存入。
- 数量不足。
- 容量不足。
- 托管 int 溢出。
- 特殊 NBT 物品拒绝。
- 重复 key。
- 预检后背包变化导致提交拒绝。
- 补偿路径不丢物品。

测试 Minecraft Inventory 较难时，把槽位计划逻辑拆成可用模拟容器测试的纯服务。

---

## 9. 任务 F：原子提取

### 9.1 API

```java
WarehouseActionResult withdraw(
    ServerPlayer player,
    UUID warehouseId,
    String commodityId,
    int requestedAmount,
    String operationKey
)
```

### 9.2 可用托管量

现有商品 P2P 卖单在下单时把商品从库存中移除，相当于冻结。必须确认实际实现，不可凭假设修改。

如果当前行为确实是移除库存：

- `CommodityInventoryManager.getCommodityAmount` 已代表可用量。
- 不能另行扣除挂单数量，否则会双重限制。

如果发现某条路径使用冻结字段，则建立统一 `availableCommodity` API 并补测试。

### 9.3 背包容量

默认使用“全量成功或完全失败”：

- 预先模拟目标 ItemStack 如何分配到现有栈和空槽。
- 无法容纳全部数量则拒绝。
- 不默认掉落，防止玩家在危险环境中意外损失。

可后续配置允许溢出掉落，但不是首版要求。

### 9.4 提交顺序

1. 预检托管数量和背包容量。
2. 扣减托管。
3. 插入真实物品。
4. 插入失败则恢复托管，并移除已插入部分。
5. 记录操作和标脏。

需要特别测试部分插入异常，不能复制或丢失。

### 9.5 测试

- 正常提取。
- 托管不足。
- 背包空间不足。
- 纯虚拟商品拒绝。
- 重复 key。
- 已挂卖单资产不能再提取。
- 提取后容量状态恢复。

---

## 10. 任务 G：仓库菜单和屏幕

### 10.1 菜单数据

显示：

- 仓库 ID 的短格式
- 所有者
- 当前容量/已用容量
- ACTIVE/OVER_CAPACITY 状态
- 注册商品列表
- 每种商品托管数量
- 玩家背包中可存入数量

列表数量和字符串必须有协议上限。

### 10.2 操作

- 选择商品
- 输入数量
- 存入
- 提取
- 全部存入该商品
- 提取一个栈（可选）

按钮发送意图包，包包含 warehouseId、commodityId、数量和 operation key。服务端通过当前菜单/位置验证，不能只相信 warehouseId。

### 10.3 刷新

操作成功后服务端同步权威状态。迟到响应不能覆盖更新状态。

### 10.4 用户提示

- 超容量：允许提取/出售，不允许存入。
- 虚拟商品：显示“此商品没有对应的世界物品，只能通过金融系统交易”。
- 已挂单资产：解释为什么托管数量看起来减少。

---

## 11. 任务 H：市场终端联动

### 11.1 显示仓储信息

市场界面新增：

- 当前托管可用量
- 总仓库容量
- 是否超容量
- “从背包存入”快捷按钮
- “前往仓库”提示

### 11.2 快捷出售

可实现“存入并立即出售给 NPC 市场”的组合动作，但必须是服务端原子编排：

1. 预检真实物品。
2. 预检 NPC 资金、市场接收量和玩家账户入账能力。
3. 扣物品并加入托管。
4. 调用现有 `NpcMarketMaker.npcBuy`。
5. 失败时恢复托管或真实物品。

如果无法确保跨服务补偿安全，本轮不要实现组合动作，只要求玩家先存入再出售。安全优先于少一次点击。

### 11.3 不改变现有订单模型

市场终端继续调用 `MarketManager` 和 `NpcMarketMaker`，不复制撮合逻辑。

### 11.4 交易反馈

- 成功音效。
- 简短粒子。
- 显示实际成交数量、单价和金额。
- 失败原因由服务端返回。

---

## 12. 任务 I：合同领域模型

### 12.1 首版范围

实现两类：

- DELIVERY：系统/公司要求玩家向指定仓库交付商品。
- PROCUREMENT：发布者托管资金收购指定商品。

如果公司仓库尚未完成，首版交付到市场终端绑定的系统仓库或玩家个人仓库。

### 12.2 类

建议：

- `FinanceContract`
- `ContractType`
- `ContractStatus`
- `ContractIssuerType`
- `ContractManager`
- `ContractService`
- `ContractSettlementResult`
- `PlayerContractProgress`

### 12.3 合同字段

- id
- type
- issuerType
- issuerId
- commodityId
- requiredQuantity
- deliveredQuantity
- rewardAmount
- escrowAccountId
- destination warehouseId/terminal location
- createdDay
- deadlineDay
- acceptedPlayerId（首版单人合同）
- status
- operation history/key
- failure reason

构造器验证所有经济不变量。

### 12.4 状态机

```text
OPEN → ACCEPTED → COMPLETED
  ↓       ↓
EXPIRED  CANCELLED
  ↓
REFUNDED（可选作为结算结果而非状态）
```

终态不能重新打开。重复调用应幂等返回已有状态。

---

## 13. 任务 J：合同资金托管

### 13.1 资金来源

- 玩家发布：从玩家账户转入合同 escrow UUID。
- 公司发布：从公司现金扣除后存入 escrow，需专用原子桥梁。
- NPC 发布：从 NPC 市场账户转入 escrow。

首版可以只实现 NPC/管理员自动生成合同，减少公司现金桥梁复杂度，但必须明确资金来自 `NpcMarketMaker.NPC_UUID`。

### 13.2 托管账户

使用确定性或随机 UUID，但必须持久化引用。创建后资金只用于：

- 完成时支付接受者。
- 取消/过期时退回发布者。

禁止把余额不一致的合同静默标为完成。

### 13.3 奖励计算

首版：

```text
reward = 市场当前合理价格 × 数量 × 奖励系数
```

必须使用 BigInteger 或安全乘法，设置单合同上限，并以发布时锁定金额为准。完成时市场价格变化不能改变已托管奖励。

### 13.4 事务记录

为合同发布、完成、退款增加明确 `TransactionType`，如会造成存档枚举兼容问题，应确保旧存档安全读取未知/缺失字段。新增枚举值一般不会破坏旧数据，但序列化器需验证。

---

## 14. 任务 K：合同接受和交付

### 14.1 接受

- 玩家只能接受 OPEN 合同。
- 限制每玩家活动合同数。
- 检查截止日。
- 同一合同不能被两人同时接受。
- operation key 防双击。

### 14.2 交付位置

必须在目标仓库/终端附近，通过当前菜单会话执行。客户端不能只发一个目标 UUID 远程交付。

### 14.3 交付来源

优先允许两种：

- 从玩家真实背包交付。
- 从玩家托管库存交付。

为了降低首版风险，可以先只实现背包交付。无论哪种，都要复用已测试的 InventoryTransactionService 或托管扣减 API。

### 14.4 原子结算

1. 验证合同状态、接受者、日期和位置。
2. 验证商品和数量。
3. 验证 escrow 余额等于或至少覆盖奖励。
4. 验证玩家账户能接收奖励。
5. 扣除物品。
6. 从 escrow 转账给玩家。
7. 如果转账失败，恢复物品。
8. 更新 delivered 和 COMPLETED。
9. 写交易记录。
10. operation key 入账。

不允许先标完成再尝试付款。

### 14.5 部分交付

首版建议不支持部分交付，要求一次性交齐，显著降低回滚复杂度。数据模型可保留 deliveredQuantity，为后续扩展。

### 14.6 测试

- 正常接受与完成。
- 两玩家竞争接受。
- 重复接受 key。
- 远程交付拒绝。
- 数量不足不扣物品。
- escrow 不足不扣物品。
- 玩家余额溢出不扣物品。
- 重复完成不二次付款。
- 截止日后拒绝并退款。

---

## 15. 任务 L：合同生成

### 15.1 首版自动生成来源

只实现一个可靠来源：NPC 市场库存短缺。

当某商品 NPC 库存低于阈值且 NPC 有足够现金时，生成采购合同。

### 15.2 限制

- 每商品最多一个同类 OPEN/ACCEPTED 合同。
- 每日最多生成固定数量。
- 奖励总额不超过 NPC 可用余额的一定比例。
- 合同期限固定在合理 MC 日范围。
- 系统时间回退不重复生成。
- 使用确定性 daily key 防重启重复。

### 15.3 调度

接入 `FinancialCycleService` 的每日处理，不接入每 tick。新增模块健康状态是否必要需谨慎；首版可在 MARKET 健康时生成，市场暂停时不新增，但已有合同仍允许安全退款。

### 15.4 公司需求

可以预留接口，但本轮除非时间充足，不实现公司自动采购合同。避免同时重写公司生产。

---

## 16. 任务 M：合同菜单与公告板入口

### 16.1 入口

首版可把合同列表放到市场终端的新子页，不必新增公告板方块。

显示：

- 商品
- 数量
- 奖励
- 截止日
- 距离/目标终端
- 状态
- 接受/交付按钮

### 16.2 隐私

公开合同不发送不必要的发布者私有数据、托管账户 UUID 或其他玩家详细信息。

### 16.3 协议上限

- 合同列表最大数量。
- 文本长度。
- UUID/枚举验证。
- 无界描述禁止。

### 16.4 客户端状态

使用 requestId 防迟到响应。接受/交付按钮 pending 时禁用，服务端结果失败后恢复可重试。

---

## 17. 任务 N：合同持久化和恢复

### 17.1 序列化器

新增 `ContractDataSerializer`，独立于市场序列化器。

### 17.2 加载校验

- 商品仍注册。
- 数量、奖励、日期合法。
- 接受者和发布者引用合法。
- escrow UUID 存在或可判定恢复。
- 已完成合同不允许仍有全额 escrow。
- OPEN/ACCEPTED 合同 escrow 必须足额，否则隔离并诊断。

### 17.3 坏数据处理

不要在无法确认资金归属时随意转账。将坏合同标记隔离，产生诊断问题，必要时模块进入只读。可以自动退款的前提是发布者和金额均可权威确认。

### 17.4 历史上限

完成合同只保留最近固定数量。清理历史不能删除仍有资金或未完成状态的合同。

### 17.5 测试

- OPEN/ACCEPTED/COMPLETED round-trip。
- 重启后不能重复生成同日合同。
- 重启后不能重复领奖。
- 坏合同不影响其他合同加载。
- 旧存档无合同数据安全加载。

---

## 18. 任务 O：一致性诊断

扩展 `EconomyConsistencyService`，增加：

### 仓库检查

- 重复 warehouseId。
- 非法容量。
- ACTIVE 记录位置冲突。
- 玩家 used > capacity 只应 WARN/OVER_CAPACITY，不是资产错误。

### 合同检查

- OPEN/ACCEPTED escrow 不足。
- COMPLETED 合同仍有异常托管余额。
- delivered 超过 required。
- 同一合同多个接受者。
- 终态日期关系错误。

诊断默认只报告，不擅自修复经济资产。严重资金不一致可暂停新合同创建，但应保留退款/恢复路径。

如新增模块枚举会影响持久化，更新 `ModuleHealthRegistry` 和诊断序列化测试。

---

## 19. 任务 P：长期模拟扩展

扩展 `LongRunSimulationService` 或新增独立 `GameplayLongRunSimulationService`：

- 创建若干模拟玩家和仓库容量。
- 随机存入/提取商品的纯逻辑等价操作。
- 生成、接受和完成合同。
- 每 30 日保存/加载。
- 使用固定 seed。
- 检查合同资金守恒。
- 检查托管商品不为负。
- 检查重复 load 不增发奖励。

如果真实 Minecraft Inventory 无法在 headless 单测中构造，模拟服务只测试领域和持久化，实际 Inventory 由 GameTest 覆盖。

---

## 20. GameTest 与手动验证

### 20.1 GameTest 建议

- 放置仓库控制器，首次交互创建唯一 ID。
- 玩家带铁锭存入，背包减少、托管增加。
- 背包满时提取拒绝且托管不变。
- 两个快速重复存入请求只成功一次。
- 破坏仓库后资产仍在，容量状态更新。
- 市场卖单后不能提取已锁定数量。
- 合同交付后物品减少、奖励到账且 escrow 归零。

### 20.2 手动多人验证

至少模拟两名玩家：

1. A 放置仓库并存入铁。
2. B 无法提取 A 的铁。
3. A 挂卖单，B 买入。
4. 双方资产正确变化。
5. A 接受 NPC 采购合同并交付。
6. 重复点击不二次领奖。
7. 服务器重启后状态保持。

### 20.3 日志

检查无以下问题：

- missing model/translation
- invalid packet
- concurrent modification
- SavedData load exception
- registry object absent
- 客户端类在专用服务端加载

---

## 21. 测试命令

每个大检查点执行：

```powershell
.\gradlew.bat compileJava
.\gradlew.bat cleanTest test
git diff --check
```

计划完成时报告：

- 测试套件数
- 测试总数
- 失败/跳过数
- 长期模拟结果
- 未执行的 GameTest/手动项目

---

## 22. 建议检查点

### 检查点 1：仓库领域和持久化

- WarehouseRecord/Manager/Serializer
- BlockEntity 对接
- round-trip 测试

### 检查点 2：库存事务

- ItemResolver
- InventoryTransactionService
- 存入/提取原子性
- 幂等测试

### 检查点 3：仓库 UI 和市场联动

- WarehouseMenu/Screen
- action packets
- 市场显示托管/容量

### 检查点 4：合同领域和托管

- 状态机
- escrow
- 接受/完成/退款
- 单元测试

### 检查点 5：自动生成、持久化和诊断

- NPC 库存短缺生成
- Contract serializer
- consistency checks
- 长期模拟

### 检查点 6：综合回归

- 全量测试
- 资源检查
- 手动多人流程
- 文档和 CHANGELOG

每个检查点更新 `docs/MINECRAFTIZATION_PROGRESS.md`。

---

## 23. 禁止的捷径

- 不要把仓库数量只存在 BlockEntity NBT。
- 不要直接 `player.getInventory().clearOrCountMatchingItems` 后假设金融入账一定成功。
- 不要让合同完成包携带客户端计算的 reward。
- 不要使用玩家名称作为资产 key。
- 不要用无限 HashSet 保存 operation key。
- 不要因仓库被拆除清空玩家托管库存。
- 不要把超容量状态当作损坏存档。
- 不要在自动合同生成时调用无来源存款。
- 不要把公司、基金、期货等无关功能顺便重构。
- 不要为减少测试工作而支持部分成功存取。

---

## 24. 完成标准

以下全部满足才算完成：

- 仓库记录与 BlockEntity 身份持久化可靠。
- 旧托管商品不会因没有仓库而丢失。
- 超容量只限制新存入，不限制合法提取和市场结算。
- 真实物品存入与托管增加严格守恒。
- 托管提取与真实物品增加严格守恒。
- 重复网络请求不能复制物品。
- 市场终端显示并使用真实托管状态。
- 至少一种 NPC 采购/交付合同可完整游玩。
- 合同奖励来自明确 escrow，不能重复领取。
- 保存/加载后仓库、合同、托管和幂等状态正确。
- 一致性诊断覆盖新不变量。
- 完整旧测试和新增测试全部通过。
- Minecraft 玩家可以完成“采集铁锭 → 存仓 → 出售/交付 → 获得金币”的闭环。

---

## 25. 最终交接输出

执行窗口结束时必须提供：

1. 玩家可执行的完整操作流程。
2. 仓库资产归属和容量模型说明。
3. 存入/提取事务顺序与补偿路径。
4. 合同奖励资金来源与退款规则。
5. 新增 NBT 根节点和兼容策略。
6. 新增测试清单和最终测试结果。
7. 已执行/未执行的 GameTest 和手动验证。
8. 发现但未擅自处理的风险。
9. 下一阶段“公司世界化”的建议切入点。

本计划完成后，Finance 才真正拥有第一个 Minecraft-first 可玩循环；高级金融系统仍完整存在，但玩家不再只能通过超级 GUI 与它互动。

