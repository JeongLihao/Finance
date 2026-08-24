# Finance Mod Development Notes

## Phase 2 行情与结算约定

- `CompanyNpcTradeService` 是公司与国际市场交换现金/商品的原子边界；失败不得发布流水、价格、指标或 K 线。
- `CommodityTradeRecorder` 是已完成商品成交的统一发布点。公司成交与玩家成交计入公开成交量；中央银行干预不计入普通成交量。
- K 线请求键为 `type + id + limit`，每个请求另有正数 `requestId`。客户端只接受当前标的最新请求 ID 的响应；响应裁剪始终保留最新 N 根。
- 服务端行情详情响应上限：K 线 120（协议硬上限 128）、买卖各 5 档、最近成交 20 笔、每类榜单 5 项。相同请求 4 tick 内去重，每玩家最多 8 次/秒。
- 最近成交是由持久化的商品/股票成交历史派生的有界缓存；新成交保留真实主动方向，旧存档恢复时因历史模型没有主动方字段，方向使用兼容默认值。
- 技术指标使用收盘价：EMA 以首个周期 SMA 为种子；MACD 为 EMA12−EMA26、DEA=EMA9(DIF)、柱值=DIF−DEA；RSI14 使用 Wilder 平滑，全平返回 50、全涨返回 100、全跌返回 0。
- 榜单按商品/股票隔离；涨跌榜要求至少两根 K 线，成交量榜排除零成交，放量比使用此前最多五根完整数据的平均量；同值按标的 ID 确定性排序。
- K 线提醒目前支持固定价、突破前一完整日高点、跌破前一完整日低点。条件提醒必须有前一完整日数据，触发后立即移除并标记存档脏。
- GUI 行情详情保持 400×250，通过单个循环按钮切换 K+MA、MACD、RSI、盘口、最近成交、涨跌榜和成交量榜，避免文字与图层叠加。
- `DataVersion` 当前为 24：新增保险池、保单、损失事件、理赔和操作幂等键分区；旧存档升级不会自动获得保单或理赔。

## Phase 9 企业风险保险

- `InsuranceManager` 是唯一写入口。投保、事件核验、自动报案和批量赔付都在服务端执行，并受模块暂停、容量和幂等检查保护。
- 普通保险保费从玩家钱包转入保险池；贷款信用险保费从商业银行准备金支出并进入保险池。信用险赔付同时更新银行复式账本和 `CompanyLoan` 剩余本金。
- 损失事件保存权威证据和确定性 ID。同一保单与事件最多一张理赔；保险池短缺只产生部分支付状态，不删除剩余债权。
- `InsuranceDataSerializer` 逐条验证并跳过损坏记录；一致性检查验证对象引用、重复理赔、风险敞口与待赔重算值。

## Phase 8 基金系统

- `finance.fund` 区分不可变 `FundDefinition`、服务端 `FundState`、玩家 `PlayerFundPosition`、赎回请求和定投计划。份额单位固定为万分之一，金额和比例使用 `BigInteger` 中间值防止溢出。
- 基金托管账户由基金 ID 确定性生成。申购、赎回与费用通过 `AccountManager` 转账；费用进入央行系统账户，不直接消失。
- 股票基金只调用现有股票限价单入口，货币基金只调用真实央行票据认购入口。指数仅是基准，净值由实际现金、股票、债券和票据持仓计算。
- 赎回先冻结玩家份额；只有付款成功才扣除份额。流动性或收款容量不足时保存为 `PENDING` 债权，重启后继续处理。
- `FUND` 已加入模块健康注册表与全局一致性检查，验证总份额、冻结份额、待赎回债权和正净值。

## Phase 7 稳定性与所有权审计

- 权威来源：普通现金/冻结资金为 `AccountManager`；商品为 `CommodityInventoryManager`；股票为 `StockPortfolioManager`；债券数量为 `CorporateBond.holdings`；银行为 `BankLedger` 加客户明细校验；期货抵押和净持仓为 `MarginManager`；行情为 `CandlestickService`。
- `EconomyConsistencyService` 只读检查账户边界、订单剩余状态、债券发行守恒、贷款合同、银行总账/客户明细、期货保证金/多空净额、系统池、K 线和周期日期。禁止在检查中调用会懒创建账户或资金池的查询。
- `StartupSelfCheckService` 每 tick 检查一个模块；`FATAL` 会把可识别模块置为 `PAUSED`。金融周期和客户端写包都必须尊重模块门禁。
- `DiagnosticDataSerializer` 只保存最近 20 份报告和暂停原因；旧存档缺少该标签时全部模块默认为 `ACTIVE`。
- 迁移矩阵以真实 serializer 构造 13/15/17/19/20/21 最小夹具，验证直接升级到 22 后重复加载不改变账户资产。
- `LongRunSimulationService` 固定种子执行 365/1000 天，标准模式每 30 天走完整 NBT 保存/重载，每日运行只读一致性检查，并用第二次相同种子运行核对确定性摘要。
- 网络写入口使用服务端身份、正数/范围校验、字符串与列表上限、请求限流和模块状态门禁；客户端永远不能指定最终余额、成交结果或结算价。

## Phase 6 商业银行约定

- `BankLedger` 是银行总账的唯一权威来源；金额、账户方向、余额容量和引用幂等性必须在整批分录提交前预检。历史压缩会生成可重建当前余额的平衡分录，不能简单截断旧条目。
- 玩家钱包、客户存款明细和银行存款负债是不同对象。客户明细总和必须等于银行总账对应负债；加载不一致时隔离受影响银行，不能钳制差额。
- 同行支付只调整客户明细，跨行支付同时减少付款行存款负债/准备金并增加收款行准备金/存款负债。失败不得只推进一侧。
- 商业银行贷款采用存款创造模型：借记公司贷款资产、贷记公司活期存款负债；旧贷款缺少 lender 时迁移为央行直贷。新合同利率锁定。
- 银行风险以总账派生的准备金、存款、贷款、权益和风险加权资产计算。坏账通过贷款损失准备与信用损失费用降低净资产和权益。
- 流动性缺口按同业市场、央行最后贷款人顺序处理。央行设施有惩罚利率、期限与累计发行上限；准备金利息和保险费按日幂等。
- 存款保险按客户和银行合并活期/定期余额。处置只迁移保障额度内债权，保险基金不足时整体失败，已处置银行不能重复赔付。
- `BankingDataSerializer` 在债务 serializer 前恢复银行，使商业贷款 lender 可验证。世界重置必须清空服务端银行状态与 `BankClientCache`。
- 银行网络列表上限为 64，使用正数请求 ID、请求限流和服务端身份/公司所有权/管理员权限校验；普通响应不包含其他客户余额和内部资产明细。

## Phase 5 期货与清算约定

- 期货报价是每商品单位价格，`contractSize` 是每手商品单位数；名义价值、保证金和盈亏均使用 `BigInteger` 预检。保证金向上取整，变动盈亏使用整数价差精确计算。
- `MarginManager` 是普通账户与保证金账户间唯一入口。保证金现金不会作为普通余额使用；可提额为保证金现金减活跃订单冻结和全部持仓初始保证金。
- 持仓采用净额模型。正数为多头、负数为空头；同向成交安全加权成本，反向成交先平仓，超出数量才按成交价建立反向仓位。
- 每个订单保存自身冻结保证金。撮合先生成双方持仓预览，再验证成交后初始保证金、冻结释放、持仓上限和待结算盈亏容量；完整提交后才减少订单并发布流水、K 线和成交。
- 已平仓但尚未日结的变动盈亏按“玩家 + 合约”保留，避免平仓后盈亏消失。日结把该值与剩余持仓的价差一并纳入合约级零和批次。
- 已完成交易日的结算池严格满足：盈利实付 = 亏损实收 + 保障基金使用；不足部分为明确盈利削减。结算成功后才更新持仓结算参考价和合约最后结算日，同日调用直接拒绝。
- 风控权益包含保证金现金、待结算盈亏和未实现盈亏。低于维持线进入 `MARGIN_CALL`，低于强平线先撤单并逐份减仓；系统接管仓位所需抵押从保障基金重分类，不凭空增加总资产。
- 到期流程先撤销订单，再完成最终变动保证金，最后清空持仓并标记 `SETTLED`。无有效现货参考价时保持 `SETTLING`，不得使用零价结算。
- `FuturesDataSerializer` 按合约 → 保证金/持仓 → 订单/成交 → 清算状态加载。订单冻结总和必须和账户冻结值一致；跨世界重置同时清空服务端管理器和客户端缓存。
- 期货网络集合每类最多 64 行，成交/结算历史服务端最多 500 条。普通响应只含当前玩家私有持仓，并把他人订单 ID 替换为零 UUID。

## Phase 4 固定收益交易约定

- `CorporateBond.holdings` 仍是债券总数量权威来源；`BondPortfolioManager` 只维护冻结量、总成本、已实现收益和累计票息。必须保持“总数量 = 可用 + 冻结”，不得由 GUI 直接改持仓。
- 债券买单按 `限价 × 剩余数量` 冻结账户资金，卖单冻结持仓。撮合使用持久化递增序号保证同价时间优先；价格改善差额在每笔成交时立即解冻。
- 成交先预检买方冻结资金、卖方冻结债券、卖方收款容量与买方持仓容量，再提交资金和债券交割；两者任一失败都不减少订单，流水、行情和 K 线只在完整成功后发布。
- `FixedIncomeValuationService` 使用简化单利折现：参考收益率为基准利率、信用利差和期限利差之和，参考价为剩余票息与本金的折现和；市场收益率按市场价、年票息和到期回归面值估算。合同票息始终锁定。
- `BondMarketDataSerializer` 在债务合约恢复之后加载。卖单冻结量必须与组合冻结量完全一致；买单所需资金不得超过账户冻结余额。序号耗尽后拒绝新单，不能绕回负数。
- `CentralBankBillManager` 仅发行 7/30/90 日一级票据。到期批量预检所有收款容量，使用央行储备后只补足精确缺口；合约标记 `MATURED` 后重启或再次日结不会重复付款。
- 金融产品响应每类最多 64 行，风险摘要最多 256 字；正数请求 ID 防止旧响应覆盖新页面。服务端从连接上下文取得玩家身份并重新校验资产和权限。
- 指数基金已在 Phase 8 实现主申赎、真实托管资产、费用、定投、赎回债权、基础风险指标和金融页入口；ETF 二级市场、杠杆/反向基金仍不在范围内。

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
- 统一日 K 线：商品与股票 OHLCV、120 日历史、MA5/MA10、按标的请求

## 项目结构

```
finance/
├── FinanceMod.java
├── account/        Account, AccountManager, TransactionRecord, TransactionType
├── market/         MarketPrice, MarketManager, NpcMarketMaker, Order, Trade
├── commodity/      Commodity, CommodityCategory, CommodityRegistry, CommodityInventory
├── company/        Company, CompanyType, CompanyManager, CompanyCreationService
├── stock/          Stock, StockMarketManager, StockPortfolioManager, StockHolding
├── chart/          Candlestick, CandlestickSeries, CandlestickService, MovingAverage
├── data/           EconomySavedData, CommodityInventorySavedData, serializer/*
├── event/          EventManager, MarketEvent, EventTier, EventTemplates
├── network/        FinancePacketHandler, TradeActionPacket, CancelOrderPacket,
│                   AdminActionPacket, CreateCompanyPacket, StockTradePacket, OpenFinanceGuiPacket
├── command/        BalanceCommand, PayCommand, FinanceCommand, MarketCommand,
│                   CommodityCommand, InventoryCommand, CompaniesCommand, CompanyCommand
├── gui/            FinanceMenu, FinanceGuiOpener, MarketOverviewMenu, MarketSnapshot
├── client/         FinanceScreen, chart/CandlestickChart, chart/CandlestickClientCache
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

## 当前持久化边界（v0.4.1）

- `EconomySavedData` 只负责加载顺序和股票域；账户、玩家功能、公司、商品市场和经济指标分别由 `data/serializer/` 下的序列化器负责。
- 加载顺序必须保持为：账户/玩家功能 → 自定义商品定义 → 公司 → 其他商品市场状态 → 经济指标/K线 → 股票域。公司库存会校验商品注册表，因此不可把公司加载提前到自定义商品定义之前。
- `EconomyMetricsService` 是成交指标的唯一写入点。交易服务只能在实际结算成功后调用其记录方法；历史存档恢复必须使用 direct 方法，不能重复计量。
- 当前存档版本为 `DataVersion = 23`；旧存档缺少基金标签时创建空份额的默认产品，升级不改变钱包、存款、保证金或既有持仓。

## K 线数据约定

- 唯一写入口是 `CandlestickService.recordTrade(type, id, mcDay, price, quantity)`；只能在资金和资产全部结算成功后调用，加载成交历史不得反向生成 K 线。
- 当日第一笔成交为开盘价，后续成交更新最高、最低、收盘和实际成交量；商品 P2P、直接吃单、NPC 成交及股票玩家/做市商成交使用同一口径。
- 每个 MC 日最多一根。无成交日仅在已有前收时生成 OHLC 相等、成交量为 0 的延续线；从未成交的标的不生成虚假数据。
- 每个标的最多保留 120 根；网络仅允许请求 30/60/120 根且响应绝对上限为 128。菜单初始化不携带全市场历史，客户端按当前标的请求并缓存。
- `MovingAverage.simple` 使用收盘价计算 MA5/MA10，数据不足的位置返回 `NaN`，渲染器不自行维护另一套统计口径。
- 服务端世界日来自 `overworld().getGameTime() / 24000`；日中当前线和已完成历史一并保存，重启后同日成交继续聚合。
# 第三阶段架构说明（2026-08-10）

- `finance.cycle.FinancialCycleService` 是金融合约唯一的按日调度入口；新增合约不得自行读取进程 tick 计数或重复维护“上次执行日”。
- `finance.money` 定义金额端点与原子转账结果。新金融产品应通过端点适配账户或公司，不应直接串联 `withdraw/deposit` 而忽略第二步失败。
- `finance.index` 维护股票、商品与行业指数。原始市值使用 `BigInteger/BigDecimal`，成分或流通股变化通过 fingerprint 触发除数调整。
- `finance.debt` 包含信用评级、公司债和公司贷款。已有合约锁定发行时利率；基准利率变化不追溯重算。
- `FinancialDataSerializer` 与 `DebtDataSerializer` 分别持久化金融周期/政策/指数和债务合约，所有列表均有硬上限并逐条跳过坏记录。
- 金融产品 GUI 数据使用 `FinancialProductRequestPacket/ResponsePacket` 按需加载，不能回填到初始菜单的全量载荷。

计算与状态口径：

- 股票/行业指数原始值为 `Σ(现价 × 流通股)`，商品指数原始值为 `Σ(中间价 ÷ 基准价)`；`指数 = 原始值 ÷ 除数`，首次除数使指数等于配置基点。成分或流通股 fingerprint 变化时用 `新原始值 ÷ 前收` 重设除数。
- 债券状态机为 `DRAFT → SUBSCRIPTION → ACTIVE → MATURED`，异常路径进入 `DEFAULTED/CANCELLED`。第一版不允许部分付息或部分到期兑付。
- 债券单个持有人利息为 `面值 × 持有数量 × 票息基点 × 实际计息天数 ÷ (10000 × annualMcDays)`，使用 `BigInteger` 后向下取整；到期日会结算末次付息日至到期日之间的尾期票息，默认年化天数为 365 MC 日。
- 破产清算先按债券本金和央行贷款本金比例清偿债权人，再将剩余池按总股本比例分配股东；任一层总额都不超过进入该层的清算池。
- 信用初始分 50：盈利 `+20`、亏损 `-20`；现金/资产达到 50% `+15`、20% `+8`、低于 5% `-15`；债务/资产不高于 10% `+15`、30% `+8`、50% `+0`、75% `-20`、更高 `-40`。破产风险或历史违约直接为 D；分数依次映射 AAA/AA/A/BBB/BB/B/CCC。
- 新贷款利率为 `基准利率 + 信用等级利差 + min(500, 期限天数 × 5)`，并钳制到合约利率上限；合同创建后锁定。每日利息为 `未偿本金 × 年利率基点 ÷ (10000 × annualMcDays)`。
- 到付款日仍有应付利息或到期仍有本金即进入 `DELINQUENT`；超过 `loanGraceDays` 后进入 `DEFAULTED`。正常还款先冲利息再冲本金，全部清偿进入 `REPAID`。
- 风险违约率为 `违约债务合约数 ÷ 未结束债务合约数 × 100%`；风险等级使用配置的中/高违约率阈值，并在存在 B/CCC/D 公司时至少显示中风险。
- 央行贷款从央行储备账户转出、还款回笼至同一账户；普通贷款不直接铸币。央行现有市场干预只有在储备低于政策下限时明确补足基础储备，且干预净变化继续进入央行摘要与货币总量指标。

验证说明：当前工作环境完成了编译、网络编解码和自动化测试，但没有可交互控制的 Minecraft 客户端会话，因此本轮未执行游戏内 GUI 手工点击。发布前仍需验证指数刷新、发行/认购/付息/到期/违约、贷款还款/逾期、利率影响和风险仪表盘；面板尺寸和动态标签宽度已通过代码约束保持为 400×250。
