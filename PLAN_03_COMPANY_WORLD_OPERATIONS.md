# 长任务计划三：公司经营世界化、成员协作与生产设施

> 预计执行时间：10～18 小时。
>
> 前置条件：计划一和计划二已完成；账本、终端、仓库、真实物品托管和首批合同均可用。
>
> 执行目录：`D:\MCMOD\Finance`
>
> 本计划交给另一个 Codex 窗口后，应持续实现到验收完成，不要只提交设计文档。

---

## 1. 目标

把公司从“后台每天自动生产并交易的数字实体”改造成玩家能够在世界中建立、经营和协作的组织，同时完整保留旧公司、财报、上市、融资、分红、债务、治理和破产逻辑。

完成后的核心循环：

```text
建立公司办公桌与公司仓库
        ↓
招募成员并分配权限
        ↓
发布原料采购合同或由成员采集
        ↓
真实原料进入公司仓库
        ↓
生产设施按每日上限加工产品
        ↓
产品进入公司托管库存
        ↓
自动出售、市场挂单或交付合同
        ↓
形成收入、成本、利润、分红与信用记录
```

---

## 2. 不可违反的边界

- 不删除旧式公司自动生产。
- 旧存档公司默认保持原经营模式，不能升级后突然停产。
- 不改变公司债权人优先、股东清算、分红和融资的不变量。
- 不让客户端决定公司成员权限、升级成本或生产结果。
- 公司库存、仓库托管和商品市场之间不得复制物品。
- 不把成员列表和完整生产状态塞进 `Company` 巨型类；使用专门服务和序列化器。
- 不在本计划重写股票估值、银行总账、基金或期货。
- 不识别或破坏玩家建筑结构；首版设施以明确方块和绑定关系为准。
- 不自动迁移旧公司现金或商品到新 UUID，除非存在完整守恒迁移与测试。

---

## 3. 开工与基线

执行：

```powershell
git status --short
.\gradlew.bat cleanTest test
```

阅读：

- `Company.java`
- `CompanyManager.java`
- `CompanyManagementService.java`
- `CompanyCreationService.java`
- `CompanyNpcTradeService.java`
- `CompanyFinancingManager.java`
- `CompanyBankruptcyManager.java`
- `CompanyProposalManager.java`
- `CompanyDataSerializer.java`
- `FinancialCycleService.java`
- `StockMarketManager.java`
- `CorporateBondManager.java`
- `CompanyLoanManager.java`
- 计划二新增的 Warehouse/Contract 类和进度记录

检查 `docs/MINECRAFTIZATION_PROGRESS.md`，以实际实现命名为准。

---

## 4. 任务 A：公司玩法数据分层

建立 `finance.gameplay.company`，建议类：

- `CompanyGameplayProfile`
- `CompanyOperatingMode`
- `CompanyMemberRole`
- `CompanyMemberRecord`
- `CompanyMembershipService`
- `CompanyFacilityType`
- `CompanyFacilityRecord`
- `CompanyFacilityManager`
- `CompanyProductionService`
- `CompanyUpgradeRequirementService`
- `CompanyGameplayActionResult`

`CompanyOperatingMode`：

- `LEGACY_AUTOMATIC`
- `PLAYER_DRIVEN`
- `HYBRID`

兼容规则：

- 旧存档没有模式：LEGACY_AUTOMATIC。
- Minecraft-first 下新公司：HYBRID，除非配置选择 PLAYER_DRIVEN。
- 管理员可迁移模式，但必须提示影响。
- 模式切换不能重复执行当日生产。

为玩法数据新增独立序列化器，不把所有字段继续堆入 `CompanyDataSerializer`。

---

## 5. 任务 B：公司成员和角色

角色建议：

- OWNER：全部权限，仍以现有 ownerId 为权威。
- MANAGER：合同、生产和成员日常管理。
- TREASURER：公司现金、银行、贷款和合同预算。
- WAREHOUSE_WORKER：公司仓库存取和交付。
- MEMBER：查看公开数据、完成公司任务。

权限至少拆分为：

- VIEW_COMPANY
- DEPOSIT_WAREHOUSE
- WITHDRAW_WAREHOUSE
- PUBLISH_CONTRACT
- MANAGE_PRODUCTION
- SPEND_COMPANY_CASH
- MANAGE_MEMBERS
- VIEW_PRIVATE_FINANCIALS
- OPEN_GOVERNANCE

服务端 API：

- invite
- acceptInvite
- rejectInvite
- changeRole
- removeMember
- leaveCompany
- hasPermission

规则：

- owner 不能被移除或降级。
- 非 owner 不能授予 OWNER。
- 邀请有期限和数量上限。
- 同一 operation key 幂等。
- 公司破产/删除时清理邀请和成员索引。
- 玩家 UUID 为权威，名称只用于显示。

测试成员权限矩阵、重复邀请、越权、保存加载和公司删除。

---

## 6. 任务 C：公司办公桌

扩展计划一的 `company_desk`：

- 首次由 owner 绑定公司。
- 一个办公桌只能绑定一个公司。
- 公司可有多个办公桌，但设置合理上限。
- 复制方块 NBT 不得复制绑定权限或公司资产。
- 破坏办公桌不删除公司。

公司办公桌菜单：

- 公司现金、库存价值、近期利润和风险。
- 成员与邀请。
- 绑定公司仓库。
- 经营模式。
- 生产设施。
- 活跃采购/交付合同。
- 自动出售比例。
- 高级按钮进入现有财报、融资或治理页面。

菜单只同步当前公司相关数据，不能发送全世界全部公司私有信息。

---

## 7. 任务 D：公司仓库绑定

计划二若只支持个人仓库，本计划增加公司仓库：

- 仓库 owner 仍是放置者。
- 绑定公司需要 owner 或公司 OWNER/MANAGER 双重授权。
- 公司商品权威归属建议使用确定性 custody UUID，而不是任意成员 UUID。
- custody UUID 规则必须稳定跨重启，例如公司 UUID 派生。
- 不直接迁移旧 `Company.inventory`，先建立桥接策略。

兼容桥接：

- LEGACY 公司继续使用 `Company.inventory`。
- PLAYER_DRIVEN 公司使用公司托管仓库。
- HYBRID 可先消费托管仓库，再使用旧 inventory 兜底。
- 不允许同一商品在两个来源同时被算作一份库存。

建议建立 `CompanyInventoryFacade`：

- availableInput
- consumeInputAtomically
- addOutputAtomically
- inventoryValueForGameplay

它统一隐藏 legacy inventory 与 custody inventory 差异。

测试双源不重复、模式切换、仓库解绑、权限和保存加载。

---

## 8. 任务 E：生产设施

新增最小设施方块或使用公司办公桌中的虚拟设施槽。为了 Minecraft 感，推荐至少注册：

- `company_factory_controller`

设施记录包含：

- facilityId
- companyId
- dimension/position
- facilityType
- productionLevel
- status
- lastProcessedDay
- boundWarehouseId

设施不需要复杂多方块识别。首版只要求控制器方块存在并绑定公司仓库。

设施状态：

- ACTIVE
- MISSING_INPUT
- OUTPUT_FULL
- DISABLED
- BANKRUPTCY_HOLD

不得每 tick 生产。接入每日金融周期，保证同日幂等。

---

## 9. 任务 F：玩家驱动生产算法

使用 `CompanyType.getDailyConsumption()` 与 `getDailyProduction()` 作为基础配方。

每日处理：

1. 检查公司与设施状态。
2. 检查模块健康和公司破产状态。
3. 检查今日是否已处理。
4. 计算设施等级、公司策略和管理等级修正。
5. 预检全部输入。
6. 预检全部输出容量。
7. 原子扣除输入。
8. 原子加入输出；失败则恢复输入。
9. 记录实际材料成本和生产流水。
10. 标记 lastProcessedDay。

首版不得“有多少原料就部分生产”，除非为批次数量建立严格、测试充分的整数算法。优先全批次成功或不生产。

HYBRID：

- 玩家设施成功生产时，不再执行同日同份 legacy 生产。
- 设施无法生产时，可按低效率 legacy 兜底。
- 兜底比例配置化，并防止双产。

测试同日幂等、时间跳跃、输入不足、输出满、溢出和保存加载。

---

## 10. 任务 G：设施升级

升级同时消耗：

- 公司现金。
- 真实建筑材料或公司托管材料。
- 可选公司等级条件。

`CompanyUpgradeRequirementService` 根据：

- 公司类型
- 设施类型
- 当前等级

返回不可变需求。

升级事务：

1. 权限验证。
2. 等级上限。
3. 现金、材料、存档状态预检。
4. 扣材料。
5. 扣公司现金。
6. 提升等级。
7. 任一步失败完整补偿。
8. operation key 防重复。

不要复用客户端显示的成本作为服务端成本。

测试现金不足、材料不足、补偿、重复 key、最大等级和重启。

---

## 11. 任务 H：公司合同联动

当公司输入库存低于安全线时，OWNER/MANAGER 可发布采购合同；自动发布可配置。

资金来源：公司现金进入合同 escrow。需要新增原子桥梁：

- company.withdraw
- escrow deposit
- 失败恢复 company cash

合同交付后商品进入公司 custody，而非交付玩家个人库存。

限制：

- 每公司活动合同上限。
- 每商品去重。
- 奖励不高于公司可承受预算。
- 破产风险公司默认不能新发合同。
- 破产时 OPEN 合同取消退款；ACCEPTED 合同按明确规则履行或取消。

测试公司现金守恒、商品归属、破产取消和重复发布。

---

## 12. 任务 I：销售和市场输出

PLAYER_DRIVEN/HYBRID 生产的商品进入公司 custody 后：

- 可由授权成员手动挂单。
- 可按照公司 autoSellRatio 使用 NPC 市场自动出售。
- 自动出售继续复用 `CompanyNpcTradeService` 或新增 facade，不复制资金/库存结算。

如现有 `CompanyNpcTradeService` 只认识 `Company.inventory`，为它增加可注入库存端点或在 facade 中编排。必须测试：

- NPC 余额不足不扣公司商品。
- 公司现金溢出不扣商品。
- NPC 库存溢出不扣商品。
- 正常成交更新 K 线和交易量一次。

---

## 13. 任务 J：财报与成本分类

保持现有 `CompanyFinancialReport` 兼容。新增玩法流水分类：

- PLAYER_PROCUREMENT
- FACILITY_PRODUCTION
- FACILITY_UPGRADE
- FACILITY_MAINTENANCE
- MARKET_SALE
- CONTRACT_ESCROW

如果扩展 `TransactionType`，确保序列化兼容。

生产材料成本：

- 玩家交付合同按实际奖励/数量形成采购成本。
- 自有采集存入可按入库时市场参考价记管理成本，但不能虚构现金支出。
- 财报中现金成本与估值成本要区分，避免利润和现金流混为一谈。

首版可以只保证现金收入/支出准确，估值成本作为附加摘要，不大改财报 schema。

---

## 14. 任务 K：破产和风险世界化

保持现有清算顺序，新增：

- 办公桌风险指示。
- 设施 BANKRUPTCY_HOLD。
- 禁止新升级和新采购合同。
- 已有合同安全取消/退款。
- 公司仓库进入只出不进或清算模式。
- 公司删除后设施记录标记失效，不删除物品。

不得自动摧毁建筑或清空仓库。

扩展破产测试，确保债权人、股东、合同发布者和公司仓库资产不会被重复清算。

---

## 15. 任务 L：持久化与迁移

新增独立 `CompanyGameplayDataSerializer`，保存：

- 经营模式
- 成员和邀请
- 办公桌绑定
- 设施
- 公司仓库绑定
- 每日处理状态
- 升级 operation keys（有界）

加载顺序在公司、仓库之后，合同恢复之前或之后应根据引用关系明确设计。

迁移矩阵增加当前旧版本 fixture：

- 旧公司为 LEGACY。
- 旧公司资产不变。
- 重复加载不生成设施或成员。
- owner 自动拥有 OWNER 权限，但不重复写入成员资产。

---

## 16. 任务 M：一致性诊断

新增检查：

- owner 与 OWNER 权限冲突。
- 重复成员或无效角色。
- 设施引用不存在公司/仓库。
- 同一设施位置重复。
- lastProcessedDay 越过世界日期。
- PLAYER_DRIVEN 公司没有有效设施仅 WARN。
- custody 商品负数/溢出由原有商品检查覆盖。
- 活跃采购合同 escrow 与公司现金流水不一致。

诊断只报告，不自动移动资产。

---

## 17. 任务 N：界面和反馈

公司办公桌子页：

- 概览
- 成员
- 仓库
- 生产
- 合同
- 高级财务

使用权限裁剪按钮；服务端仍验证。

设施方块反馈：

- blockstate/指示灯显示 ACTIVE、缺料、满仓、风险。
- 生产成功时有限音效/粒子。
- 不每 tick 刷新网络。

所有新字符串加入中英文翻译。

---

## 18. 测试与长期模拟

新增建议测试：

- `CompanyMembershipServiceTest`
- `CompanyInventoryFacadeTest`
- `CompanyProductionServiceTest`
- `CompanyFacilityPersistenceTest`
- `CompanyUpgradeServiceTest`
- `CompanyContractIntegrationTest`
- `CompanyGameplayMigrationTest`

扩展长期模拟：

- 创建 HYBRID 公司。
- 每日随机成员交付原料。
- 设施生产和出售。
- 每 30 日保存加载。
- 检查商品不为负、现金不溢出、同日不双产。

执行：

```powershell
.\gradlew.bat compileJava
.\gradlew.bat cleanTest test
git diff --check
```

---

## 19. 检查点

1. 经营模式、成员和持久化。
2. 公司办公桌和仓库绑定。
3. 生产设施与每日幂等。
4. 升级事务。
5. 公司采购合同和市场销售。
6. 破产/诊断/迁移。
7. UI、资源、全量测试和手动多人验证。

每个检查点更新 `docs/MINECRAFTIZATION_PROGRESS.md`。

---

## 20. 手动验收场景

1. 玩家创建公司并放置办公桌。
2. 邀请第二名玩家为仓库员工。
3. 绑定公司仓库和生产控制器。
4. 公司发布铁原料采购合同。
5. 成员交付铁，公司现金减少、公司原料增加。
6. 下一 MC 日设施消耗铁并产生产品。
7. 产品由公司卖向市场，现金和财报更新。
8. 非授权成员无法提取或升级。
9. 服务器重启后成员、设施、库存和 lastProcessedDay 保持。
10. 切换 LEGACY/HYBRID 不造成同日双产。

---

## 21. 完成标准

- 旧公司行为兼容。
- 新公司可通过世界设施经营。
- 成员权限服务端权威。
- 公司仓库资产不与旧 inventory 重复计算。
- 生产严格消费真实/托管原料。
- 升级消费现金和材料且可回滚。
- 公司合同资金和商品守恒。
- 破产不删除仓库物品或重复清算。
- 全量测试通过。
- 完成“玩家协作采购 → 设施生产 → 市场销售”的可玩闭环。

---

## 22. 最终交接输出

执行窗口需说明：

- 旧公司与新公司模式差异。
- 成员权限矩阵。
- 公司 inventory/custody 的唯一归属规则。
- 每日生产顺序与防双产机制。
- 合同和升级的补偿路径。
- 测试结果和未执行的手动项目。
- 计划四需要使用的设施状态与世界反馈接口。

