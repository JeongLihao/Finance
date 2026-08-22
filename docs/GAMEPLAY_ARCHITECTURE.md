# Gameplay 架构

## 依赖方向

```text
物品 / 方块 / 菜单 / 命令 / 网络请求
                  ↓
           finance.gameplay
                  ↓
       现有账户、市场、公司和金融内核
                  ↓
          EconomySavedData
```

世界入口不得绕过已有订单优先级、资产冻结、原子结算、模块暂停和审计规则。

## 阶段 1 类型

- `FinanceTerminalType`：声明钱包、市场、仓库、银行、公司、证券、董事会、央行或旧完整入口。
- `FinanceScreenMode`：定义 `WALLET`、`MARKET`、`WAREHOUSE`、`COMPANY`、`BANK`、`ADVANCED` 和 `ADMIN` 信息层级。
- `FinanceAccessContext`：仅由服务端构造的玩家、权限、入口验证、维度和距离上下文。
- `FinanceAccessPolicy`：从服务器配置读取的不可变策略快照，也可注入单元测试。
- `FinanceAccessService`：纯逻辑访问矩阵，不打开界面、不修改资产。
- `FinanceGameplayService`：服务端适配层，校验后才调用现有 `FinanceGuiOpener`。
- `GameplayActionResult`：统一返回成功状态、翻译键和刷新要求。

## 当前入口行为

- F 键和旧网络包声明 `LEGACY_FULL_SCREEN → ADVANCED`，最终是否允许由服务器配置决定。
- `minecraftFirstMode=false` 时保持旧完整界面行为。
- 默认 Minecraft-first 模式下，普通玩家必须使用后续阶段提供的世界终端；管理员仍可通过旧命令进入高级界面。
- 客户端伪造市场、证券或央行终端类型不会通过验证，因为当前网络请求没有可信方块会话。
- 物理终端接入后，每次写操作都必须重新校验方块类型、维度和最多 8 格距离。

## 阶段 2：实体仓库与采购合同

- `WarehouseManager` 保存世界级仓库记录和容量索引，`CommodityInventoryManager` 继续保存实际托管商品，BlockEntity 不复制商品数量。
- `CommodityItemResolver` 是商品到 Minecraft 物品的服务端边界；`InventoryTransactionService` 使用可复核的槽位计划完成物品移除、插入与补偿。
- `WarehouseService` 每次写操作重新校验玩家、菜单、维度、距离、位置、仓库 ID 和 BlockEntity，不信任客户端库存数量。
- `ContractManager` 管理合同状态、每日生成和 escrow 生命周期；`ContractService` 只允许在接受时绑定的个人仓库执行整批交付。

```text
真实背包物品 ──仓库事务──> 玩家商品托管 ──现有市场服务──> 账户资金
       │
       └──合同交付──> NPC 商品托管 + escrow 奖励──> 玩家账户
```

## 后续扩展约束

轻量菜单使用各自的有界数据负载，不复用完整 `FinanceMenu`。公司世界化应复用当前仓库事务与合同 escrow，不得建立第二套库存或无来源奖励。

## 阶段 3：公司世界经营

```text
成员背包 ──仓库原子事务──> company custody ──每日设施──> company custody 产品
                                      │                         │
公司现金 ──公司采购合同──> escrow ──交付┘                         └──NPC 市场──> 公司现金
```

- `CompanyGameplayProfile` 只保存经营模式、成员协作和世界绑定；财报、债务、治理及股票仍由既有领域模型负责。
- custody UUID 由公司 UUID 确定性派生。它是 PLAYER_DRIVEN/HYBRID 世界库存的唯一权威，不属于任何成员。
- 每日顺序为设施预检与生产、可选自动销售、利润日结、融资/治理、破产检查、世界状态灯刷新。设施和 HYBRID 兜底共同使用每日标记防止重复调度。
- 升级事务顺序为材料预检/扣除、公司现金扣除、等级提交；后两步失败时材料与现金按原端点恢复。采购合同取消先聚合预检公司现金容量，再批量退还 escrow。
- 世界反馈只在日结后同步已加载的设施方块。计划四可直接读取 `CompanyFacilityRecord.status()`、`lastProcessedDay()` 和 `CompanyFactoryControllerBlock.INDICATOR`，无需重新推导生产结果。
