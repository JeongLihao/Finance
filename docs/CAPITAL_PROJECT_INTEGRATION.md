# 资本项目 × 实体产业融合 —— 实现设计冻结（阶段 A 审计结论）

本文档是资本项目系统的内部实现说明。所有 API 均来自实际代码审计，非文件名猜测。

## 1. 现有融资 manager 的实际入口、成功状态与资金终点

### CompanyFinancingManager（增发）
- 入口：`startProject(ownerId, companyId, issueQuantity, issuePrice, fundingTarget, currentMcDay)`，仅 owner、仅上市公司、每公司至多一个进行中项目；`DEFAULT_DURATION_DAYS = 7`。
- 认购：`subscribe(playerId, projectId, shares)` 从玩家钱包扣款；`isFunded()`（raised ≥ fundingTarget）时 `finalizeProject`：`company.deposit(raised)` + `stock.increaseShares` + 原子派发持仓，随后项目从 `PROJECTS` 移除。
- 未达标：`tick` 到期 `refundProject` 全额退款后移除。
- 资金终点：成功时公司现金 += raised。**项目对象被删除，外部无法再查询** → 资本项目模块必须依赖新增的有界完成记录（见 §6.1）判断成功/失败，不能靠轮询 `getProject`。

### CorporateBondManager（企业债）
- 入口：`issue(ownerId, companyId, code≤16, faceValue, quantity, couponBps, currentDay, subscriptionDays, termDays, couponIntervalDays)`，创建即 `SUBSCRIPTION`；票息下限 = 基准利率 + 评级利差；额度受评级 maxDebtPercent 与 `maxBondFinancingRatio` 双限。
- 认购资金进入债券内部 `escrowCash`（非 Account）。`processDay` 在 `day ≥ subscriptionEndDay` 时 `activateOrCancel`：有认购则 `MoneyTransferService.transfer(escrowEndpoint, MoneyEndpoints.company(company), escrowCash)` 并置 `ACTIVE`；否则逐持有人退款置 `CANCELLED`。
- 成功状态 = `ACTIVE`；此时公司现金已增加，`raised = faceValue × subscribedQuantity()`（ holdings 求和，激活后仍可查询）。
- 付息/到期/违约全部由 `FinancialCycleService.processDay` → `CorporateBondManager.processDay` 驱动，资本项目不得介入。

### CompanyLoanManager（贷款）
- `apply(...)`（央行直贷）：校验评级≠D、信用额度、期限；`MoneyTransferService.transfer(央行账户 → MoneyEndpoints.company)` **同步放款到公司现金**，返回 `Result(success, loanId, msg)`。
- `applyCommercial(ownerId, companyId, bankId, principal, day, termDays, paymentIntervalDays)`：额外校验银行 `acceptsNewBusiness`、单一借款人上限、资本充足率；放款走 `BankingManager.originateCompanyDepositLoan`（存款创造：**公司银行存款 += principal，不是现金**）。
- 公司存款 → 现金：`BankingManager.withdrawCompanyToCash(owner, bankId, amount, day)`（受银行准备金约束，可能失败，可重试）。
- 日结计息/逾期/违约在 `processDay`；还款 `repay` 仅 owner。

### 治理（提案/授权）
- `CompanyProposalManager.createProposal(creatorId, companyId, type, textValue, v1, v2, v3, startDay, endDay, passRatio)`：仅上市公司；`mayCreateProposal` = 经营者/控制者/≥25% 投票权大股东。
- 通过后 `executeProposal`：`executesImmediately=true` 的类型立即置 `EXECUTED`；否则停留 `PASSED`。
- `isExecutableAuthorization(type, companyId)` 要求 `status == PASSED` → **可消费授权必须是"非立即执行"类型**。现有类型中 SHARE_BUYBACK/TREASURY_RETIREMENT 立即执行（通过 `proposal-<uuid>` key 在 executeProposal 内一次性消费）；FUND_USAGE 也立即执行，无法事后消费。
- 结论：**新增 `CompanyProposalType.CAPITAL_PROJECT`（value1=预算，value2=项目类型 ordinal，textValue=用途说明≤64），`executesImmediately=false`**；消费后调用现有 `markExecuted(proposalId, result)` 置 EXECUTED。授权只能消费一次（EXECUTED 后不再满足 isExecutableAuthorization）。
- SHARE_ISSUE 提案通过时会自动 `CompanyFinancingManager.startProject`（现有语义，保持不变）；资本项目对增发的授权即"该提案已通过并真实启动了融资项目"。

## 2. 仓库、工厂升级当前的材料与现金扣除顺序

### WarehouseUpgradeService.upgrade(player, warehouseId, operationKey)
会话校验（`WarehouseService.validRecord`，含维度/距离/方块实体身份）→ 操作键去重 → 权限（owner 或 OP2）→ 状态非 DISABLED/ORPHANED → `WarehouseUpgradeRequirementService.requirement(tier)`（BASIC→REINFORCED：250 现金 + 8铁/8铜/4红石；REINFORCED→INDUSTRIAL：1500 现金 + 8黑曜石/8石英/8红石）→ 玩家钱包余额 → `PhysicalMaterialTransaction.plan/commit`（玩家背包）→ `AccountManager.withdraw(玩家)` → `record.upgrade(targetTier, capacity)`，失败逐级补偿 → 记录操作键、交易流水、刷新指示器。
**资金来自玩家钱包，材料来自玩家背包。**

### CompanyUpgradeService.upgrade(player, facilityId, operationKey)
模块门禁（COMPANY_GAMEPLAY）→ 设施+公司存在 → `validPhysicalRequest`（维度、区块已加载、`terminalInteractionDistance` 内、方块实体 facilityId 一致）→ `MANAGE_PRODUCTION` 权限 → 非破产风险 → 操作键去重 → `CompanyUpgradeRequirementService.requirement`（1→2 级：2000 现金 + 12铁/8铜/8红石；2→3 级：8000 现金 + 8黑曜石/12石英/16红石；MAX_LEVEL=3）→ 公司现金 → 玩家背包材料 plan/commit → `company.withdraw` → `facility.upgrade()`，失败补偿 → `recordGameplayCost`。
**资金来自公司现金，材料来自执行玩家背包。**

### 资本项目的施工适配
材料中铜锭/红石/黑曜石/石英默认不是注册商品，无法进入公司 custody。因此执行规则为：
- 材料 item 可经 `CommodityItemResolver.commodityId(item)` 解析为注册商品 → 从公司 custody（`CompanyInventoryFacade.custodyId`）扣除；
- 不可解析 → 从执行玩家背包经 `PhysicalMaterialTransaction` 扣除。
两类来源统一在一个"材料扣除计划"中原子提交，任一步失败全量回滚。
现金一律从项目 escrow 燃毁（`AccountManager.withdraw(escrow, budget)`），与现有升级"成本消失"语义一致。等级提交复用 `WarehouseRecord.upgrade(targetTier, capacity)` / `CompanyFacilityRecord.upgrade()`。

## 3. 公司 custody UUID 的唯一权威生成位置

`finance.gameplay.company.CompanyInventoryFacade.custodyId(companyId)`：
`UUID.nameUUIDFromBytes(("finance-company-custody:" + companyId).getBytes(UTF_8))`。
所有读写公司托管库存必须经此方法；`WarehouseManager.totalCapacity/usedCapacity` 亦以它为公司仓库容量主体。

## 4. 资本项目需要保存的最小字段（WorldCapitalProject）

```
projectId(UUID), companyId, type(WAREHOUSE_UPGRADE|FACTORY_UPGRADE),
targetId(仓库或设施记录 UUID), creatorId,
createdDay, deadlineDay, targetLevel(目标等级),
fundingSource(RETAINED_EARNINGS|COMMERCIAL_LOAN|CORPORATE_BOND|SHARE_ISSUE),
budget(创建时冻结), fundedAmount(已划入 escrow 的真实金额),
materialSnapshot(Map<String itemId, int> 冻结),
proposalId / loanId / bondId / financingProjectId(各可空),
status, lastStatusChangeDay, failureKey(翻译键),
operationKeys(有界 128)
```
escrow 账户：`UUID.nameUUIDFromBytes(("capital-project-escrow:" + projectId))`，经 `AccountManager.getOrCreateSystemAccount` 建立（零余额，无新手资金）。

## 5. 只读接入与写入适配清单

| 模块 | 接入方式 |
| --- | --- |
| CompanyFinancingManager | 写入适配：新增有界 `FinalizedFinancing` 完成记录（§6.1） |
| CompanyProposalType | 写入适配：新增 `CAPITAL_PROJECT`（§1） |
| CompanyLoanManager / CorporateBondManager | 只读轮询 + 现有申请入口；不改语义 |
| BankingManager | 只读 + 现有 `applyCommercial`/`withdrawCompanyToCash` |
| StockPriceEngine / StockMarketManager | 只读：fairValue 已经由公司资产+平滑利润驱动；升级改变产能→销量→利润→估值，自然传导 |
| FundValuationService | 只读：基金净值只按持仓市价，不加入设施价值 |
| FixedIncomeValuationService | 只读：债券估值不变；债务压力经 `CompanyCreditService.totalDebt` 已含贷款+债券 |
| CompanyCreditService | 只读：评级输入自动包含新债务 |
| WarehouseRecord / CompanyFacilityRecord | 仅经 `upgrade(...)` 提交等级 |
| ModuleHealthRegistry | 资本项目写入受 `COMPANY_GAMEPLAY` 模块门禁 |

## 6. 关键设计决定

### 6.1 增发完成记录（对 CompanyFinancingManager 的最小写入适配）
`finalizeProject` 成功后追加 `FinalizedFinancing(projectId, companyId, raisedAmount, shares, day)` 到有界列表（≤1024，与资本项目全局上限一致，超出删最旧），提供 `putFinalizedDirect` 供序列化恢复，随 `CompanyDataSerializer` 持久化。资本项目据此区分"融资成功"与"到期退款"，并在划转后记录 `fundingSettled` 防重复消费。

### 6.2 日调度入口
资本项目日结挂在 `FinancialCycleService.processDay(day)`（债券/贷款之后），遵守"金融合约唯一按日入口"约定；重启补跑、时钟回拨幂等行为自动继承。

### 6.3 四种来源的资金路径
- RETAINED_EARNINGS：`MoneyTransferService.transfer(MoneyEndpoints.company → account(escrow), budget)`，创建即 FUNDED。
- COMMERCIAL_LOAN：创建时 `applyCommercial(principal=budget)`；资金在公司银行存款；日结/手动确认时 `withdrawCompanyToCash` → 公司现金 → escrow → FUNDED。银行流动性不足自动隔日重试。
- CORPORATE_BOND：创建时 `issue`（quantity = ceil(budget/faceValue), faceValue=100, coupon=评级下限, subscriptionDays=max(1,duration/3), term=duration）；轮询状态：ACTIVE 且 raised≥budget → 公司现金 → escrow → FUNDED；raised<budget → FAILED_RECOVERABLE（不动债券）；CANCELLED → FAILED_RECOVERABLE。
- SHARE_ISSUE：要求已通过的 SHARE_ISSUE 提案自动启动的融资项目（fundingTarget=budget）；经 §6.1 记录确认后 公司现金 → escrow → FUNDED。

### 6.4 治理授权
`company.isPublic() || budget ≥ FinanceConfig.capitalProjectGovernanceThreshold()` 时需要显式治理授权。上市公司创建 `CAPITAL_PROJECT` 提案（PASSED、value1=budget、value2=类型 ordinal、同公司）并以 `markExecuted` 一次性消费；未上市公司没有股东名册，由所有者在实体公司终端作一次显式确认。取消不复活授权。

### 6.5 禁止直接调用的恢复/测试专用 API
`Account.setBalance`、`AccountManager.clearAccountsDirect`、`putDirect/put*Direct`、`restore*`、`clearDirect`、`registerDirect`、`addProposalDirect`、`addProjectDirect`、`restoreFinancials`、`restoreManagement` 等仅限序列化器与测试使用；资本项目业务路径一律走公开业务入口与 `MoneyTransferService`。

## 7. 持久化与网络约定（阶段 H 预告）

- 新 `CapitalProjectDataSerializer`（ROOT=`CapitalProjects`, Version=1）；`EconomySavedData.DATA_VERSION` 33→34；旧存档无根 → 空集合。
- 加载顺序在 `ContractDataSerializer.load` 之后；逐记录隔离损坏；公司/目标缺失的项目标记暂停态。
- `resetRuntimeState` 增加 `CapitalProjectManager.clearDirect()`。
- 网络：新增 `CapitalProjectActionPacket`（动作枚举 + projectId + operationKey），服务端不信任客户端金额/材料/授权；响应 ≤32 项目；协议版本随枚举边界提升。
