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

## 待办

### 界面大类（按收益排序）

1. **`FinanceScreen`（约 1,000 行）**：`updateInputVisibility` 里一大段输入框显隐和摆放规则，可以让各页签自己声明要哪些输入框、放在哪。
2. **`FinanceMenu`（约 1,300 行）**：服务端打包给高级界面的数据行（`MarketRow`、`StockRow` 等）和编解码都挤在一个类里，可以按页签拆出各自的数据记录和编解码。还没细看。
3. **`SimpleInvestPage`（约 700 行）**：股票详情（`stockDetail`）和商品详情（`commodityDetail`）各拆一个类。
4. **其余页签类**：`CompanyTabs`（约 880 行）、`RecordTabs`（约 810 行）、`CompanyGameplayScreen`（约 770 行）、`PlayerServicesScreen`（约 720 行）、`StockTabs`（约 690 行）、`MarketTabs`（约 600 行）。思路同 `ServiceTabs`：按子页签拆面板，页签编号换常量。
5. **面板里的点击坐标**：各面板的 `click` 用写死的坐标判断点了哪个按钮（`mx >= 244 && mx < 274`），和 `render` 里画按钮的坐标各写一遍。可以让按钮画的时候登记点击区域，点击时查表，坐标只写一处（简单模式的 `UiBuilder` 已经是这种做法）。

### 服务端

6. **公司经营模式**：`CompanyOperatingMode`（`LEGACY_AUTOMATIC` / `HYBRID` / `PLAYER_DRIVEN`）在机器改版后对玩家公司基本没意义了，但还决定库存放在哪（托管账户还是公司库存）、NPC 公司怎么跑、能不能抵押。简化前要先理清 `CompanyInventoryFacade` 里两种库存的迁移。
7. **大服务类**：`EconomyConsistencyService`（约 980 行）、`VillageService`（约 830 行）、`BankingManager`（约 800 行）、`CorporateActionManager`（约 790 行）、`FinanceGuiOpener`（约 780 行），按职责拆分。服务端逻辑测试比界面多，适合先补测试再拆。
8. **零散的 UUID 解析**：命令（`PlayerBankCommand`、`TakeoverCommand`）和几个序列化器里还有手写的 `try { UUID.fromString } catch`，各自的失败处理不同，统一前要逐个看。

## 做法备忘

- **纯搬移**（拆类、挪方法）：拆完用脚本把拆分前后的代码行做多重集合比对，除了跨类调用加类名之外应当一字不差。
- **改写默认值、键名这类容易手滑的**：先导出一份快照（例如把配置规格的键、默认值、范围、注释全部导出成文本），改完再导出对比，逐行一致才算过。
- 界面类测试少，拆之前先看有没有布局测试（简单模式有 `SimpleLayoutTest`，高级界面基本没有），没有就靠纯搬移加逐行比对兜底。
