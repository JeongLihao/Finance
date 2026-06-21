# Finance Mod Development Notes

## 已完成功能

- 账户系统：余额、转账、冻结/解冻、交易记录
- 商品系统：动态注册、管理员可添加任意 MC 物品
- P2P 市场：限价单、撮合引擎、部分成交、订单取消
- 国际市场：双向报价、库存驱动定价、每日补货/消耗
- 定价引擎：基本面 + 动量 + 噪音，每日重置日内统计
- 公司系统：3 种行业、每日生产+自动交易、估值
- 股票系统：系统公司股票、买卖、持仓管理
- 事件系统：三级事件压力、独立计时器、持久化
- GUI：金融中心 7 标签页（管理员 6+1）、从手中添加物品
- 管理员物品管理：手持物品自动识别、常用物品快捷添加、手动添加

## 项目结构

```
finance/
├── FinanceMod.java
├── account/        Account, AccountManager, TransactionRecord, TransactionType
├── market/         MarketPrice, MarketManager, NpcMarketMaker, Order, Trade
├── commodity/      Commodity, CommodityCategory, CommodityRegistry, CommodityInventory
├── company/        Company, CompanyType, CompanyManager, CompanyCreationService
├── stock/          Stock, StockMarketManager, StockPortfolioManager, StockHolding
├── data/           EconomySavedData, CommodityInventorySavedData
├── event/          EventManager, MarketEvent, EventTier, EventTemplates
├── network/        FinancePacketHandler, TradeActionPacket, CancelOrderPacket,
│                   AdminActionPacket, CreateCompanyPacket, StockTradePacket, OpenFinanceGuiPacket
├── command/        BalanceCommand, PayCommand, FinanceCommand, MarketCommand,
│                   CommodityCommand, InventoryCommand, CompaniesCommand, CompanyCommand
├── gui/            FinanceMenu, FinanceGuiOpener, MarketOverviewMenu, MarketSnapshot
├── client/         FinanceScreen, MarketOverviewScreen, ClientSetup, FinanceKeyMappings
├── registry/       ModMenus
└── util/           FormatUtil, MathUtil
```

## 商品分类（基于 MC 物品栏）

- BUILDING_BLOCKS — 建筑方块
- RAW_MATERIALS — 原材料
- TOOLS — 工具
- COMBAT — 战斗
- FOOD — 食物
- REDSTONE — 红石
- BREWING — 药水
- TRANSPORTATION — 交通运输
- MISCELLANEOUS — 杂项
