# Finance Mod 长时间开发任务：稳定性收尾与 K 线行情系统

> 将本文档直接交给另一个 Codex 窗口执行。预计工作量为数小时。执行者必须持续推进、分阶段验证，并在所有完成条件满足后再结束任务。

## 一、任务目标

在当前 `D:\MCMOD\Finance` 工作区继续开发，完成现有经济指标与存档重构的审核收尾，并新增适合金融模组的 K 线行情能力。

本任务包括两条主线：

1. 修复已确认的跨日统计、比例分配和安全算术问题。
2. 建立商品与股票共用的 OHLCV/K 线数据模型、服务端聚合、持久化、网络传输和客户端图表。

最终目标不是只画一个静态图，而是形成可持续扩展的行情基础设施。

## 二、执行环境与约束

项目目录：

```text
D:\MCMOD\Finance
```

技术环境：

- Java 17
- Minecraft Forge 1.20.1 / Forge 47.4.16
- Gradle 8.8
- JUnit 5.10.2

开始前必须执行：

```powershell
git status --short
git diff --stat
git diff --check
.\gradlew.bat cleanTest test
```

当前已知基线：上一轮完整测试为 83/83 通过，但人工审核仍发现未被测试捕获的问题。

执行约束：

1. 工作区存在未提交修改，全部视为用户工作，禁止覆盖或丢弃。
2. 禁止使用 `git reset --hard`、`git checkout --` 等破坏性命令。
3. 不删除 `logs/`，除非用户明确要求。
4. 不进行无关的全项目格式化或换行符转换。
5. 修改前先完整阅读相关生产代码和测试。
6. 每个缺陷或新能力都必须有测试或明确的手工验证步骤。
7. 不降低现有断言强度来让测试通过。
8. 不创建提交、不推送远端，除非用户明确要求。
9. 如果执行期间用户修改了同一文件，暂停并重新对照差异。
10. 优先完成稳定性修复，再开发 K 线，不允许用新功能掩盖旧问题。

## 三、当前已完成基础

以下内容已经存在，执行者应复核但不要重复从零实现：

- `EconomySavedData` 已拆分账户、玩家功能、公司、市场和指标 serializer。
- 自定义商品定义已经调整为先于公司库存加载。
- `EconomyMetricsService` 已记录商品和股票当日成交量。
- 商品 P2P、直接吃单、NPC 买卖和股票成交已有指标埋点。
- 每日周期会归档经济指标。
- 指标状态和最近已归档日期已持久化。
- 仪表盘已扩展资金分类、当日成交量和最近 30 日趋势。
- dashboard 网络编解码已有基本回归测试。
- 商品订单簿已增加价格优先和同价时间优先排序。
- 当前测试覆盖自定义商品库存、指标、市场成交量和部分极值分配。

## 四、必须首先修复的审核问题

## 4.1 P1：世界第 0 天不会归档

相关文件：

- `src/main/java/finance/metrics/EconomyMetricsService.java`
- `src/main/java/finance/cycle/EconomyCycleService.java`
- `src/test/java/finance/metrics/EconomyMetricsServiceTest.java`

当前问题：

```java
if (completedMcDay <= 0 || completedMcDay <= lastClosedMcDay) {
    return;
}
```

当世界进入第 1 天时，周期服务调用 `closeDay(0)`。当前逻辑拒绝第 0 天，导致第 0 天成交量继续累计，并最终混入第 1 天快照。

要求：

1. 允许 `completedMcDay == 0`。
2. 只拒绝负数日期和已经归档的日期。
3. 修改错误地把第 0 天视为无效日期的现有测试。
4. 增加从第 0 天跨到第 1 天的回归测试。
5. 验证服务器重启后不会重复归档第 0 天。

验收：

- `closeDay(0)` 产生一条 day 0 快照。
- day 0 快照包含且只包含第 0 天成交量。
- 第二次 `closeDay(0)` 不产生重复快照。
- 后续 `closeDay(1)` 正常产生新快照。

## 4.2 P2：带外部总权重时可能多分配

相关文件：

- `src/main/java/finance/util/ProportionalAllocator.java`
- `src/main/java/finance/company/CompanyManager.java`
- `src/test/java/finance/util/ProportionalAllocatorTest.java`
- `src/test/java/finance/company/CompanyDividendTest.java`

问题示例：

- 总金额：100
- 总股本：100
- 实际玩家持股：50
- 当前算法可能给唯一股东 51，而正确结果应为 50

根因是使用 `totalAmount - allocated` 作为余数，而没有先计算实际持股对应的可分配目标。

要求：

1. 明确两个 overload 的语义。
2. 无外部总权重时，在有效权重之间分完 `totalAmount`。
3. 有外部总权重时，只分配 `totalAmount * actualWeight / declaredTotalWeight` 对应的金额。
4. 余数分配必须相对于“实际可分配目标”，而不是完整 `totalAmount`。
5. 外部总权重小于实际权重时不能超发。
6. 所有运算继续使用 `BigInteger` 防止溢出。
7. 分配结果必须确定性排序。

至少新增以下测试：

- `declaredTotalWeightDoesNotOverpayPartialOwnership`
- `exactWeightModeDistributesWholeAmount`
- `declaredWeightBelowActualWeightNeverOverAllocates`
- `largeWeightsDoNotOverflow`
- `nullAndNegativeWeightsAreIgnored`

验收：

- 任意情况下支付总额不超过允许的目标。
- 部分流通股/未被玩家持有的股份不会凭空获得分红。
- 现有破产清算和普通分红测试全部通过。

## 4.3 P2：账户和资产估值仍有未检查加法

相关文件：

- `src/main/java/finance/data/serializer/AccountDataSerializer.java`
- `src/main/java/finance/account/Account.java`
- `src/main/java/finance/account/AccountManager.java`
- `src/main/java/finance/company/CompanyBankruptcyManager.java`
- 商品与股票结算服务

已知风险：

- 存档恢复时直接执行 `balance + frozen`。
- `deposit` 直接执行 `balance += amount`。
- 冻结资金直接执行 `frozenBalance += amount`。
- 破产价值直接执行 `company.getCash() + company.inventoryValue()`。

要求：

1. 定义统一的金额溢出策略。
2. 对用户资金优先采用“拒绝操作并保持原状态”，不要静默绕回负数。
3. 对只读汇总可采用饱和到 `Long.MAX_VALUE`。
4. 对损坏存档采用明确策略：跳过记录、钳制或安全恢复，但不得生成负资产。
5. 交易结算任一步失败时，不得出现单边扣款、单边入账或资产已交割但资金失败。
6. 为所有修改增加极值测试。

至少新增以下测试：

- `loadRejectsOverflowingBalanceAndFrozenCombination`
- `depositOverflowLeavesBalanceUnchanged`
- `freezeOverflowLeavesBothBalancesUnchanged`
- `settlementOverflowDoesNotMoveFundsOrInventory`
- `bankruptcyValuationSaturatesInsteadOfWrappingNegative`

验收：

- 正常业务行为不变。
- 极值不会变成负数。
- 失败路径状态完全不变。

## 五、新功能主线：统一 K 线行情系统

## 5.1 产品范围

第一版 K 线系统同时支持：

- 商品行情
- 股票行情
- OHLC：开盘、最高、最低、收盘
- Volume：成交量
- MC 日线
- 最近 30/60/120 根切换，至少支持其中两个长度
- MA5、MA10 移动平均线
- 涨跌额与涨跌幅
- 无成交日的明确处理
- 服务端持久化
- 服务端到客户端的受限网络传输
- GUI 蜡烛图与成交量柱

不要第一版就实现真实世界所有技术指标。MACD、RSI、布林带可作为后续可选阶段，必须在基础 K 线稳定后再考虑。

## 5.2 领域模型设计

建议新增独立包：

```text
finance/chart/
  MarketInstrumentType.java
  MarketInstrumentKey.java
  Candlestick.java
  CandlestickSeries.java
  CandlestickService.java
```

可按项目命名习惯调整，但不要把全部逻辑塞进 `FinanceScreen` 或 `EconomySavedData`。

建议模型：

```java
public record Candlestick(
        long mcDay,
        long open,
        long high,
        long low,
        long close,
        long volume
) {}
```

不变量：

- `mcDay >= 0`
- `open/high/low/close > 0`
- `high >= open/close/low`
- `low <= open/close/high`
- `volume >= 0`
- 单一标的、单一 MC 日最多一根日 K
- 历史长度有硬上限，防止存档和网络包无限增长

标的键必须区分商品和股票，不能只用裸字符串避免同名冲突。例如：

```java
public record MarketInstrumentKey(MarketInstrumentType type, String id) {}
```

其中：

- 商品 id 使用 `commodityId`
- 股票 id 使用规范化 symbol

## 5.3 K 线生成规则

需要先明确并写入注释与测试：

1. 当日第一笔成功成交价作为 open。
2. 所有成功成交更新 high、low、close。
3. volume 累加实际成交数量，不统计挂单、撤单或失败成交。
4. 同一笔成交不能同时被多个入口重复计量。
5. 成交历史从存档恢复时不能再次生成 K 线或增加 volume。
6. 日终时封存当天 K 线。
7. 无成交日策略建议：沿用上一日 close 生成 `OHLC` 相等且 volume 为 0 的 K 线；如果产品选择完全不生成，也必须统一且有测试。
8. 没有上一收盘价且无成交时不生成虚假 K 线。
9. 商品 NPC 成交、P2P 成交、直接吃单均进入同一商品 K 线。
10. 股票玩家撮合和做市商成交均进入同一股票 K 线。

推荐建立单一入口：

```java
CandlestickService.recordTrade(type, id, mcDay, price, quantity);
```

不要分别从 GUI、历史记录或价格引擎推导成交。

## 5.4 与现有交易服务集成

检查以下成功结算点：

- `MarketManager` P2P 撮合
- `MarketManager.takeOrder`
- `NpcMarketMaker.npcBuy`
- `NpcMarketMaker.npcSell`
- `StockOrderManager.executeTrade`

集成要求：

1. K 线记录发生在资金和资产结算成功之后。
2. 与 `EconomyMetricsService` 的成交量记录保持同一语义。
3. 最好封装成一个明确的“成交已完成”发布步骤，减少两个统计服务调用位置漂移。
4. 如果暂不引入事件总线，也要保证每个成交路径只调用一次。
5. 失败或回滚时 K 线状态不变。

## 5.5 日终与时间语义

K 线应使用服务端世界时间，不使用系统墙钟作为经济日期。

要求：

1. 使用 `overworld().getGameTime() / 24000` 或项目统一的 MC day 来源。
2. 第 0 天必须能正确封存。
3. 同一天重复调用日终方法必须幂等。
4. 服务端在边界 tick 重启不能重复生成同日 K 线。
5. 日中保存并重启后，未封存 K 线继续累计。
6. 世界时间回退时不能删除现有历史或写入重复日号。

建议将当前进行中的 K 线与已封存历史都持久化。

## 5.6 历史长度和存储策略

建议：

- 每个标的最多保存 120 根日 K。
- GUI 默认请求最近 30 根。
- 可切换 30/60/120 根。
- 服务端网络传输设置绝对上限，例如 128 根。

需要估算：

- 标的数量
- 每根 K 线字段大小
- NBT 总体积
- 打开 GUI 时的网络包大小

如果标的很多，不要每次打开金融中心就发送所有标的全部历史。优先使用：

1. 初始菜单只发送选中标的或小型摘要。
2. 玩家切换标的时单独请求该标的 K 线。
3. 服务端验证标的类型、id 和请求长度。

## 5.7 K 线持久化

建议新增：

```text
src/main/java/finance/data/serializer/CandlestickDataSerializer.java
```

建议 NBT 结构：

```text
CandlestickData
  Series[]
    Type
    InstrumentId
    CurrentBar (optional)
    Bars[]
      McDay
      Open
      High
      Low
      Close
      Volume
```

要求：

1. 增加存档版本号；当前为 14，新 K 线持久化建议升级为 15。
2. 旧存档没有 K 线标签时正常加载为空。
3. 单条损坏 K 线只跳过该条，不破坏其他标的。
4. 未知 instrument type 跳过。
5. 空白或超长 id 跳过。
6. 非法 OHLCV 数据跳过或规范化，策略必须有测试。
7. 加载不能触发成交计数。
8. `resetRuntimeState()` 必须清空 K 线服务状态。

至少新增测试：

- 商品 K 线保存/加载往返
- 股票 K 线保存/加载往返
- 当前未封存 K 线保存/加载后继续更新
- 旧存档缺少 K 线标签
- 损坏类型、id、价格、数量
- 超过历史上限时只保留最新数据

## 5.8 网络协议

建议新增专用请求/响应包，而不是无限扩张 `FinanceMenu` 初始化数据：

```text
RequestCandlestickPacket
CandlestickDataPacket
```

请求字段建议：

- instrument type
- instrument id
- requested limit

响应字段建议：

- instrument type
- instrument id
- bars
- server current day
- 是否包含正在形成中的 K 线

安全要求：

1. id 长度限制。
2. 类型必须是已知 enum。
3. requested limit 钳制到允许范围。
4. 服务端只返回已注册商品或存在股票。
5. 解码时设置 bars 绝对上限。
6. 即使收到超额但允许消费的 payload，也必须完整消费或直接拒绝整个包，不能让后续字段错位。
7. 所有处理必须安排在正确线程。

至少新增测试：

- 请求参数验证
- 30/60/120 限制
- 编解码往返
- 超长 id 拒绝
- 非法 enum 拒绝
- 过大数组拒绝
- 空历史响应

## 5.9 K 线 GUI

优先在现有市场详情或股票详情区域新增“行情/K线”子视图，不要让金融中心主界面继续无限膨胀。

图表至少包含：

- 阳线/阴线实体
- 上下影线
- 成交量柱
- 价格刻度
- 时间/MC 日刻度
- 当前标的名称和最新价格
- 最新涨跌额、涨跌幅
- MA5、MA10
- 30/60/120 切换按钮
- 空数据状态

颜色建议遵循当前 UI 风格，并保证文字对比度。涨跌颜色应在整个模组内保持一致。

绘制要求：

1. 所有绘制区域使用明确边界和裁剪。
2. 防止 `high == low` 时除零。
3. 防止只有一根 K 线时横坐标除零。
4. 极端 long 价格转换到像素时使用 double 并限制范围。
5. 不能让 NaN 或 Infinity 进入坐标计算。
6. 窗口较小时保持可用，必要时缩短标签。
7. 鼠标悬停显示 OHLCV、日期、涨跌幅。
8. 切换标的或周期时清理旧响应，避免显示错标的数据。

建议把图表绘制拆到独立组件，例如：

```text
finance/client/chart/CandlestickChart.java
```

避免继续增加 `FinanceScreen` 的单类体积。

## 5.10 移动平均线

第一版实现简单移动平均：

- MA5
- MA10

规则：

1. 使用 close 计算。
2. 数据不足对应周期时不绘制该点。
3. 使用安全 double 计算，避免 long 累加溢出。
4. 计算逻辑放在可单元测试的纯 Java 工具类中。

至少新增测试：

- 标准序列的 MA5/MA10
- 数据不足
- 极值 close
- 空列表
- 非法 K 线已在进入计算前过滤

## 六、关联功能更新

完成基础 K 线后，按时间选择以下增强。优先级从高到低排列。

## 6.1 行情摘要

为商品和股票增加统一摘要：

- 最新价
- 昨收
- 涨跌额
- 涨跌幅
- 当日最高/最低
- 当日成交量
- 最近 5 日平均成交量

摘要必须来自同一 K 线/当前 bar 数据，不要在 GUI 中重复计算另一套口径。

## 6.2 成交量异常提示

可增加简单“放量”标记：

- 当前日成交量大于最近 5 个完整日平均量的 2 倍时标记
- 历史不足时不标记
- 只做展示，不自动交易

## 6.3 价格预警联动

在现有 PriceAlert 基础上考虑：

- 突破昨日高点
- 跌破昨日低点
- 涨跌幅超过阈值

第一轮仅设计接口或实现一个最简单条件，避免扩大范围过快。

## 6.4 管理员行情监控

管理员仪表盘可新增：

- 成交量最高的商品
- 成交量最高的股票
- 当日涨幅/跌幅榜
- 最近 5 日波动率较高标的

需要限制返回数量，例如每榜最多 5 或 10 条。

## 6.5 可选技术指标

只有在时间充足、基础功能和测试全部稳定时才实现：

- EMA12 / EMA26
- MACD
- RSI14
- 布林带

每个指标必须是独立纯计算类并有公式测试。不要把公式直接写进渲染方法。

## 七、测试计划

## 7.1 单元测试

覆盖：

- Candlestick 不变量
- 同日多笔成交更新 OHLCV
- 跨日封存
- 第 0 天
- 重复封存幂等
- 无成交日策略
- 历史裁剪
- MA5/MA10
- 极值价格和数量
- 非法数据恢复

## 7.2 交易集成测试

覆盖：

- 商品 P2P
- 商品直接吃单
- NPC 买入
- NPC 卖出
- 股票玩家撮合
- 股票做市商成交
- 部分成交
- 多对手方成交
- 失败和撤单
- 历史加载不重复计量

对每条成功路径同时断言：

- 交易历史
- 账户/库存或持仓
- `EconomyMetricsService` 成交量
- K 线 OHLCV

## 7.3 持久化测试

覆盖：

- 当前 bar
- 历史 bars
- 多标的
- 商品与股票同名 id 隔离
- 旧版本存档
- 损坏记录
- 最大历史长度
- 重启后继续同日成交

## 7.4 网络测试

覆盖：

- 请求和响应往返
- 长度边界
- 非法类型
- 超长 id
- 过大 bars 数量
- 多余字段消费或整体拒绝
- 空历史

## 7.5 GUI 手工验证

如果开发环境允许，运行客户端并检查：

```powershell
.\gradlew.bat runClient
```

手工检查：

1. 打开商品 K 线。
2. 制造多笔不同价格成交。
3. 检查 OHLC 和成交量。
4. 跨 MC 日检查新 K 线。
5. 打开股票 K 线并重复验证。
6. 切换 30/60/120。
7. 检查 MA5/MA10。
8. 检查鼠标悬停。
9. 缩小窗口检查布局。
10. 重启世界后检查历史仍存在。

如果不能运行 GUI，必须在最终报告中明确说明未完成手工验证，不能把编译通过等同于界面通过。

## 八、建议阶段顺序

严格按以下顺序执行：

1. 记录 Git 与测试基线。
2. 修复 day 0 归档问题并测试。
3. 修复比例分配目标和余数并测试。
4. 修复账户及估值安全算术并测试。
5. 再跑完整测试，建立稳定基线。
6. 设计 K 线模型和明确无成交日规则。
7. 实现纯内存 K 线服务和单元测试。
8. 接入商品成交路径。
9. 接入股票成交路径。
10. 接入日终封存和重启幂等。
11. 实现 K 线持久化并升级 DataVersion。
12. 实现网络请求/响应及验证测试。
13. 实现独立 K 线图组件。
14. 接入商品和股票 GUI。
15. 实现 MA5/MA10。
16. 增加行情摘要。
17. 时间允许时实现放量提示或管理员榜单。
18. 更新 README、CHANGELOG 和 DEVELOPMENT_NOTES。
19. 执行完整测试、编译和 GUI 验证。
20. 输出最终交付报告。

## 九、每阶段验证规则

每完成一个阶段至少运行相关测试，例如：

```powershell
.\gradlew.bat test --tests "finance.metrics.EconomyMetricsServiceTest"
.\gradlew.bat test --tests "finance.util.ProportionalAllocatorTest"
.\gradlew.bat test --tests "finance.data.EconomySavedDataTest"
```

每完成一个较大模块运行：

```powershell
.\gradlew.bat cleanTest test
```

不要连续积累大量修改后才第一次编译。

## 十、代码质量要求

1. K 线服务不得依赖客户端类。
2. 技术指标计算不得依赖 Minecraft GUI 类。
3. 网络 DTO 与领域模型之间有清晰转换。
4. serializer 只负责持久化，不包含交易业务逻辑。
5. GUI 只负责展示和交互，不重新定义价格统计口径。
6. 所有集合对外优先返回只读副本。
7. 所有历史都有明确上限。
8. 所有来自网络和存档的字符串、enum、数量都验证。
9. 关键时间语义和无成交日规则写入注释。
10. 避免继续扩大 `FinanceScreen`；新增图表使用独立组件。

## 十一、文档更新要求

更新：

- `README.md`
- `CHANGELOG.md`
- `DEVELOPMENT_NOTES.md`

文档至少说明：

- DataVersion 新版本
- K 线保存上限
- OHLCV 口径
- 无成交日规则
- 商品与股票支持范围
- MA5/MA10 含义
- 服务端/客户端数据流
- 旧存档兼容行为

## 十二、最终验证

最终必须执行：

```powershell
git diff --check
.\gradlew.bat cleanTest test
.\gradlew.bat compileJava
git status --short
git diff --stat
```

如修改了网络包注册，还要核对：

- discriminator/消息 id 不冲突
- 客户端与服务端方向正确
- handler 在正确线程执行
- 专用服务器不会加载 client-only 类

## 十三、完成定义

只有全部满足以下条件，才能宣告任务完成：

- day 0 指标归档正确。
- 比例分配不会多发。
- 账户和关键估值路径不会溢出为负数。
- 旧有测试全部通过。
- 商品和股票均能形成正确 OHLCV。
- K 线按 MC 日封存且同日幂等。
- K 线能保存、加载并在重启后继续。
- K 线历史有明确上限。
- 网络请求和响应具有长度与类型验证。
- GUI 能展示蜡烛、影线、成交量和 MA5/MA10。
- 空数据、单点、平价和极值数据不会使 GUI 崩溃。
- 新增功能有自动化测试。
- 完整测试和编译通过。
- 文档已更新。
- 未完成的可选指标被明确列为后续事项，而非假装完成。

## 十四、阶段报告模板

每完成一个阶段输出：

```text
阶段：
完成内容：
设计决定：
修改文件：
新增/修改测试：
执行命令：
验证结果：
剩余风险：
下一阶段：
```

## 十五、最终交付报告模板

```text
1. 总体结果
2. 已修复审核问题
3. K 线功能完成范围
4. 数据模型与持久化设计
5. 网络与 GUI 设计
6. 修改文件清单
7. 新增测试清单
8. 最终测试数量与结果
9. GUI 手工验证结果
10. 仍未完成的可选功能
11. 下一轮建议
```

执行者应持续工作到所有必选完成条件满足，或出现必须由用户作出业务选择/授权才能继续的真实阻塞。

