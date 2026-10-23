# 代码重构待办

记录还没做的重构，做完一项就从这里删掉。原则：只改结构不改玩法；每批单独提交，测试和 `spotlessCheck` 通过再合并。

## 已完成（2026-10-23）

- 公司机器：方块、方块实体、界面、漏斗接口、烧燃料各合成一份（`CompanyMachineBlock`、`CompanyMachineBlockEntity`、`CompanyMachineScreen`、`AutomationView`、`MachineCore.burnOne`）。
- 文本截断统一走 `FinanceGui.clipText`；金额格式统一用 `MoneyFormat`；UUID 解析用 `Uuids.parseOrNull`。
- `FinanceConfig`：每个配置项只写一处默认值（`ConfigOption`）。
- `SimplePages` 按页拆成 `SimpleHomePage` / `SimpleTradePage` / `SimpleCompanyPage` / `SimpleInvestPage`。
- `ServiceTabs` 按子页签拆成 `FinancialProductPanel`、`FuturesPanel`、`BankPanel`、`FundPanel`、`InsurancePanel`、`RegionalRiskPanel`，子页签编号改成 `ServiceTabs.INDEX` 等常量。

## 已完成（2026-10-24）

- `FinanceScreen` 顶层页签编号改成 `FinanceScreen.TAB_MARKET`、`TAB_STOCK`、`TAB_FINANCIAL`、`TAB_ADMIN` 等常量（`MACRO_TAB` 改名 `TAB_MACRO`），`MarketTabs` 里的判断一起换掉。
- `AdminTabs` 子页签编号改成 `AdminTabs.HAND`、`QUICK`、`REGISTERED`、`PRICE_HISTORY`、`DIVIDEND`。
- 期货做市商账户 UUID 只写一处：`FuturesMarketMaker.MARKET_MAKER_ID` 直接引用 `MarketMakerLedger.FUTURES_MARGIN_UUID`。
- `FinanceScreen` 输入框的显隐和摆放只写一处（`advancedInputs()`），`updateInputVisibility` 和 `getVisibleEditBoxes` 都从它来。顺带修好治理视图里“价格”“股份”输入框不显示、点不进去。
- `SimpleInvestPage` 拆出 `SimpleStockDetail`（股票详情）和 `SimpleCommodityDetail`（商品详情），押注行和价格图仍在 `SimpleInvestPage` 共用。
- `FinanceMenu` 的数据行和编解码按页签拆到 `MarketRows`、`StockRows`、`CompanyRows`、`RecordRows`、`DashboardRows`，共用工具在 `MenuCodec`；`FinanceMenu` 只剩字段、构造、getter 和 `writeAll`（约 480 行）。外部引用从 `FinanceMenu.MarketRow` 改成 `MarketRows.MarketRow`。
- `CompanyTabs` 按视图拆成 `CompanyCreatePanel`、`CompanyManagePanel`、`CompanyDetailsPanel`、`CompanyGovernancePanel`、`CompanyRestructuringPanel`。
- `RecordTabs` 拆成 `OrdersPanel`、`InventoryPanel`、`TransactionsPanel`、`AssetsPanel`、`AlertsPanel`、`DashboardPanel`，删掉 `RecordTabs`。
- 三处共用的 K 线视图从 `StockTabs` 拆成 `CandlestickView`。
- `MarketTabs` 拆成 `MarketPanel`、`TradePanel`、`CommodityDropdown`，删掉 `MarketTabs`。
- `CompanyGameplayScreen` 拆出实体项目页 `CompanyCapitalPanel`。
- `PlayerServicesScreen` 拆成外框和七个页面类（`ServicesPage` 及其子类）。
- 零散的 UUID 解析统一走 `Uuids.parseOrNull`（18 处），只剩常量定义用 `UUID.fromString`。
- 大服务类：`EconomyConsistencyService` 按领域拆成五个检查类；`VillageService` 拆出 `VillageStallService`；`FinanceGuiOpener` 拆出 `FinanceRowConverters`、`FinanceAssetBuilder`；`CorporateActionManager` 拆出 `CorporateActionSettlement`；`BankingManager` 拆出 `TimeDepositOps`。后两个的入口方法是 `static synchronized`，入口留在原类保锁，只搬实现。
- 按钮点击改成“画的时候登记、点的时候查表”（`ClickRegions`）：金融中心内容区全部面板、商品下拉菜单、仓库、公司经营、兑换钞界面都已迁移，各面板的 `handleXxxClick` 删除。报纸、指南、金融中心标题栏和顶部页签原本就共用 `hit()` 或命名常量，不需要迁。
- 完整体检 `EconomyConsistencyService.run()` 补上商队、NPC 投资者、竞争公司、宏观经济四项检查。

## 待办

1. **公司经营模式**（等玩法决定）：`CompanyOperatingMode` 决定库存放在哪（LEGACY 只看公司旧库存，PLAYER_DRIVEN 只看托管账户，HYBRID 两边都看）、能不能抵押、机器能不能从村庄招工。**切换模式时不搬库存**：公司主人在经营界面点“模式”会循环切换，切走后另一边的库存（包括已抵押的）就看不见、用不了，切回来才出现（已用探针测试确认）。处理办法有两种：玩家公司固定用托管库存、去掉切换按钮、读档时把旧库存并进托管账户（改存档，DataVersion 升级）；或者保留切换但切换时搬库存（有抵押物时不让切）。

## 做法备忘

- **纯搬移**（拆类、挪方法）：拆完用脚本把拆分前后的代码行做多重集合比对，除了跨类调用加类名之外应当一字不差。
- **改写默认值、键名这类容易手滑的**：先导出一份快照（例如把配置规格的键、默认值、范围、注释全部导出成文本），改完再导出对比，逐行一致才算过。
- 新界面按钮一律用 `FinanceScreen.button` / `filledButton`（或界面自己的 `ClickRegions`）画并登记动作，不要再在 `click` 里写坐标判断。
- 界面类测试少，拆之前先看有没有布局测试（简单模式有 `SimpleLayoutTest`，高级界面基本没有），没有就靠纯搬移加逐行比对兜底。
