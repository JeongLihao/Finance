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

## 待办

### 界面大类（按收益排序）

1. **其余界面大类**：`CompanyGameplayScreen`（约 770 行）、`PlayerServicesScreen`（约 720 行）、`StockTabs`（约 470 行，股票页本身，没有子页签）。前两个是独立界面，拆之前先看有没有按区域拆的自然边界。
2. **面板里的点击坐标**：各面板的 `click` 用写死的坐标判断点了哪个按钮（`mx >= 244 && mx < 274`），和 `render` 里画按钮的坐标各写一遍。可以让按钮画的时候登记点击区域，点击时查表，坐标只写一处（简单模式的 `UiBuilder` 已经是这种做法）。

### 服务端

3. **公司经营模式**：`CompanyOperatingMode`（`LEGACY_AUTOMATIC` / `HYBRID` / `PLAYER_DRIVEN`）在机器改版后对玩家公司基本没意义了，但还决定库存放在哪（托管账户还是公司库存）、NPC 公司怎么跑、能不能抵押。简化前要先理清 `CompanyInventoryFacade` 里两种库存的迁移。
4. **大服务类**：`EconomyConsistencyService`（约 980 行）、`VillageService`（约 830 行）、`BankingManager`（约 800 行）、`CorporateActionManager`（约 790 行）、`FinanceGuiOpener`（约 780 行），按职责拆分。服务端逻辑测试比界面多，适合先补测试再拆。
5. **零散的 UUID 解析**：还有约 20 处手写 `UUID.fromString`，分布在命令（`PlayerBankCommand`、`TakeoverCommand`）、序列化器、网络包（`CandlestickRequestPacket`、`SimpleActionPacket`）、客户端（`CompanyTabs`、`SettlementScreen`）和服务（`OrderBookService`、`CorporateRestructuringService`、`CompanyProposalManager` 等）。各自的失败处理不同，统一前要逐个看。

## 做法备忘

- **纯搬移**（拆类、挪方法）：拆完用脚本把拆分前后的代码行做多重集合比对，除了跨类调用加类名之外应当一字不差。
- **改写默认值、键名这类容易手滑的**：先导出一份快照（例如把配置规格的键、默认值、范围、注释全部导出成文本），改完再导出对比，逐行一致才算过。
- 界面类测试少，拆之前先看有没有布局测试（简单模式有 `SimpleLayoutTest`，高级界面基本没有），没有就靠纯搬移加逐行比对兜底。
