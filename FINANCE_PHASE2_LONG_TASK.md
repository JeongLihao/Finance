# Finance Mod 第二阶段长时间任务：结算闭环、K 线完善与专业行情功能

> 本文档用于直接交给另一个 Codex 窗口持续执行。预计工作量至少数小时。执行者必须基于当前工作区继续开发，不得重置、覆盖或丢弃已有未提交修改。

## 1. 总体目标

当前项目已经具备商品/股票 OHLCV、最近 120 个 MC 日历史、MA5/MA10、K 线网络请求和基础 GUI。本阶段需要完成四件事：

1. 修复最新审核发现的公司自动交易结算与统计问题。
2. 修复 K 线异步请求竞争和历史截断问题。
3. 系统性验证新增布尔式安全 API 的所有调用方，消除单边结算。
4. 在稳定基础上增加更专业的金融行情功能，包括盘口深度、近期成交、技术指标、涨跌榜和预警联动。

完成后，项目应从“能显示 K 线”提升为“结算可信、行情口径统一、交互稳定、可持续扩展”的金融模组。

## 2. 当前基线

工作目录：

```text
D:\MCMOD\Finance
```

技术环境：

- Java 17
- Minecraft Forge 1.20.1 / Forge 47.4.16
- Gradle 8.8
- JUnit 5.10.2
- 当前存档版本：DataVersion 15

最近一次核验结果：

- `cleanTest test` 成功
- 111 个测试全部通过
- `git diff --check` 通过
- 但人工审核发现自动化测试未覆盖的结算和异步问题

当前已有能力：

- 商品与股票统一 OHLCV 日 K
- 每个标的最多 120 根
- 无成交日沿用前收、成交量为零
- MA5、MA10
- 商品 P2P、直接吃单、NPC 玩家交易、股票成交接入 K 线
- K 线存档、损坏数据过滤
- 30/60/120 天按需请求
- 蜡烛、影线、成交量柱、悬停详情
- day 0 指标归档和重复归档保护
- 金额、库存和持仓的部分溢出保护

## 3. 开始前的强制检查

执行：

```powershell
git status --short
git diff --stat
git diff --check
.\gradlew.bat cleanTest test
```

记录：

- 当前修改和未跟踪文件
- 测试总数
- 测试失败数
- 编译状态
- 是否存在用户在执行期间继续修改的文件

禁止：

- `git reset --hard`
- `git checkout --`
- 删除用户文件
- 清理整个工作区
- 无关的全项目格式化
- 未经用户要求创建提交或推送

## 4. 最高优先级修复

## 4.1 P1：公司自动卖出可能产生单边结算或创造货币

相关文件：

- `src/main/java/finance/company/Company.java`
- `src/main/java/finance/account/AccountManager.java`
- `src/main/java/finance/commodity/CommodityInventoryManager.java`
- `src/main/java/finance/market/NpcMarketMaker.java`

当前风险：

公司自动卖出执行顺序大致为：

1. 扣除公司库存
2. 增加 NPC 库存
3. 从 NPC 扣款
4. 给公司入账

多个布尔返回值被忽略。当 NPC 余额不足、NPC 库存容量达到上限、公司现金接近 long 上限或中途状态变化时，可能出现：

- 商品移动但资金未移动
- NPC 未扣款但公司已经入账
- 公司失去商品但没有收到钱
- 交易流水与真实资产状态不一致
- 市场价格被更新，但结算实际失败

### 修复要求

1. 不要继续在 `Company` 内手写一串分步结算。
2. 抽取明确的公司与 NPC 原子结算服务或安全方法。
3. 在提交前一次性预检：
   - 卖方库存
   - 买方库存容量
   - 付款方余额
   - 收款方入账容量
   - 金额乘法是否溢出
4. 提交顺序必须支持失败回滚，或确保预检后提交步骤不会失败。
5. 如果仍存在理论上可能失败的步骤，必须显式回滚此前变更。
6. 只有所有资产变更成功后才能写流水、价格、指标和 K 线。
7. 禁止用 `IllegalStateException` 代替业务回滚后继续留下半完成状态。

### 推荐设计

建立类似以下结果对象：

```java
public record CommoditySettlementResult(
        boolean success,
        long payment,
        int quantity,
        String reason
) {}
```

或者在现有管理器中提供：

```java
settleCommodityForFunds(
    UUID fundsBuyer,
    UUID fundsSeller,
    InventoryEndpoint commoditySeller,
    InventoryEndpoint commodityBuyer,
    String commodityId,
    int quantity,
    long unitPrice
)
```

不要求严格采用命名，但要保证玩家、NPC、公司交易可以逐步收敛到同一结算语义。

### 必须新增的测试

- NPC 余额为零、公司尝试卖 1 件时不得产生资金
- NPC 收款账户达到 `Long.MAX_VALUE` 时，公司采购不移动商品或资金
- NPC 库存达到 `Integer.MAX_VALUE` 时，公司卖出不移动资金或商品
- 公司现金达到 `Long.MAX_VALUE` 时，公司卖出不扣商品
- 正常公司采购成功，四方状态准确
- 正常公司卖出成功，四方状态准确
- 失败交易不写流水
- 失败交易不改变市场价格
- 失败交易不增加经济成交量
- 失败交易不生成 K 线

### 验收标准

- 任意失败路径状态完全不变
- 成功路径资金守恒、商品守恒
- 流水只记录成功交易
- 测试可复现旧问题并证明修复

## 4.2 P1：公司成交未计入指标和 K 线

公司采购和自动出售会调用 `NpcMarketMaker.recordNpcTrade` 更新市场价格，但目前不会写入：

- `EconomyMetricsService.recordCommodityTrade`
- `CandlestickService.recordTrade`

### 修复要求

1. 公司成功成交必须进入商品成交量。
2. 公司成功成交必须进入对应商品 K 线。
3. OHLCV 使用真实成交单价和实际成交数量。
4. 同一笔公司成交只统计一次。
5. 不允许通过 `recordNpcTrade` 和另一个入口重复写 K 线。
6. 最好建立统一的“成交完成发布”方法，使价格、指标、K 线、流水采用同一成功边界。

### 建议抽象

```java
CommodityTradeRecorder.recordCompletedTrade(
    buyer,
    seller,
    commodityId,
    price,
    quantity,
    tradeSource
);
```

`tradeSource` 可以区分：

- PLAYER_P2P
- PLAYER_TAKE_ORDER
- PLAYER_NPC
- COMPANY_NPC
- CENTRAL_BANK

是否把央行库存干预统计为市场成交必须作出明确决定并写入文档。推荐默认不计入正常市场成交量，而是作为独立系统干预数据。

### 必须新增的测试

- 公司采购增加 K 线成交量
- 公司卖出增加 K 线成交量
- 同日多笔公司成交正确更新 OHLC
- 公司交易与玩家交易聚合到同一根商品 K 线
- 失败公司交易不产生 K 线
- 公司成交历史加载不会重复计量

## 4.3 P2：审核所有新增布尔返回值调用方

本轮将以下 API 从无返回值改为布尔结果：

- 账户 deposit/unfreeze 等
- 公司 deposit
- 商品库存 add
- 股票持仓 add

项目中仍有大量调用方忽略结果，包括但不限于：

- 公司融资完成和退款
- IPO 募资和创始人持股
- 公司管理充值、取款、升级退款
- 央行库存和现金干预
- 管理员发钱
- 玩家 MC 物品存入虚拟库存
- 股票退市补偿
- NPC 初始化与补库存

### 工作方法

使用全局检索建立清单：

```powershell
Get-ChildItem src\main\java -Recurse -Filter *.java | Select-String "AccountManager.deposit\("
Get-ChildItem src\main\java -Recurse -Filter *.java | Select-String "CommodityInventoryManager.addCommodity\("
Get-ChildItem src\main\java -Recurse -Filter *.java | Select-String "StockPortfolioManager.addHolding\("
```

对每个调用点分类：

1. 单纯初始化，可接受失败但必须记录或使用明确策略
2. 管理命令，需要向用户反馈失败
3. 资产交换，必须原子化
4. 补偿/退款，失败不能静默丢失
5. 系统干预，需要守恒或明确铸币/销毁语义

### 验收标准

- 没有关键资产交换路径静默忽略失败
- 退款失败不会直接删除原订单或融资项目
- GUI/命令收到明确失败反馈
- 央行的铸币或回收行为有清晰注释，不与普通转账混淆

## 5. K 线协议与客户端一致性修复

## 5.1 P2：请求乱序导致显示错误窗口

当前缓存键只有：

```text
instrument type + instrument id
```

快速点击 120 天、30 天时，网络响应可能乱序。较早请求的迟到响应会覆盖较新结果，导致按钮显示 30 天但图表展示 120 根。

### 推荐方案 A：请求 ID

请求增加：

- `requestId`
- type
- id
- limit

响应原样带回：

- `requestId`
- type
- id
- limit
- bars

客户端为当前图表保存最新 requestId，只接收匹配响应。

### 可选方案 B：缓存键包含 limit

缓存键改为：

```text
type + id + limit
```

界面读取当前 limit 对应的数据。该方案仍建议加 loading 状态，避免切换后短暂显示旧窗口。

推荐使用 A+B：requestId 防止过期响应，limit 键支持多个窗口缓存。

### 必须新增的测试

- 先请求 120，后请求 30，按 30→120 的反向响应顺序处理，最终仍显示 30
- 同一标的不同窗口互不覆盖
- 切换标的时旧标的响应不影响当前图表
- 空响应也只能清空对应请求
- requestId 回绕或负值有明确策略

## 5.2 P3：响应截断应保留最新 K 线

当前超过响应上限时保留 `subList(0, limit)`，即最旧的数据。

要求改为：

```java
int start = Math.max(0, bars.size() - MAX_BARS_PER_PACKET);
bars.subList(start, bars.size());
```

测试必须断言具体日期范围，而不仅断言数量。

示例：输入 day 0..139，上限 128，结果应为 day 12..139。

## 5.3 加载和错误状态

GUI 增加明确状态：

- 尚未请求
- 加载中
- 空历史
- 请求失败/超时
- 数据就绪

不要把“网络还没返回”和“该标的没有成交历史”都显示成同一个空状态。

建议在请求发出时记录客户端 tick，并在合理时间后显示“加载较慢，请重试”，但不要自动高频重发。

## 6. K 线数据正确性深化

## 6.1 当前日与完整日区分

目前历史列表包含正在形成的当日 K 线。GUI 应明确显示：

- 当前 K 线是否未完成
- 完整日和当前日的均线/成交量平均口径

建议响应增加：

- `serverCurrentMcDay`
- `latestBarComplete`

或客户端通过 `bar.mcDay == serverCurrentMcDay` 判断。

行情摘要的“五日平均成交量”应只使用完整日，不让当前半日成交量污染基准。

## 6.2 时间回退和重载

验证：

- `/time set` 回退后，新成交不会写入旧于最新 K 线的日期
- 世界重载后当前 MC day 能在玩家交易前正确初始化
- 服务端刚启动、第一 tick 之前发生的请求或交易不会错误写入 day 0

如果存在启动窗口风险，考虑让交易记录入口显式接收当前服务端 day，而不是依赖静态 `currentMcDay`。

## 6.3 行情摘要安全算术

检查：

- `latest.close() - previousClose` 的 long 溢出
- 涨跌幅计算的 Infinity/NaN
- 最大/最小价格范围转换
- tooltip 的价格差计算

要求：

- 差值使用 `BigInteger`、安全 double 或饱和策略
- GUI 永不展示 NaN/Infinity
- 极值输入不会崩溃

## 7. 新功能：盘口深度

## 7.1 商品盘口

在商品 P2P 市场增加五档盘口：

- 买一到买五
- 卖一到卖五
- 每档价格
- 每档累计数量

要求：

1. 同价订单合并数量。
2. 买盘价格从高到低。
3. 卖盘价格从低到高。
4. 只展示有效、可交易订单。
5. 数量累计使用安全加法。
6. 不向客户端发送完整订单簿。

## 7.2 股票盘口

股票同样增加五档盘口，并保持和现有价格优先/时间优先撮合一致。

## 7.3 数据模型

建议：

```java
public record OrderBookLevel(long price, long quantity) {}
public record OrderBookSnapshot(
        List<OrderBookLevel> bids,
        List<OrderBookLevel> asks
) {}
```

盘口构建使用纯 Java 服务，便于单元测试。

### 测试

- 同价聚合
- 买卖排序
- 只取前五档
- 空盘口
- 数量溢出饱和
- 订单取消后快照更新
- 部分成交后档位数量更新

## 8. 新功能：近期成交明细

为当前标的展示最近 10~20 笔成交：

- 成交 MC 日/时间
- 单价
- 数量
- 主动买入/主动卖出方向

要求：

1. 服务端限制条数。
2. 不暴露不必要的玩家 UUID。
3. 商品和股票使用统一 DTO。
4. 历史按新到旧展示。
5. 当前标的切换时按需请求。

可与 K 线响应合并，也可建立独立行情详情响应。选择前先评估包大小与刷新频率。

## 9. 新功能：技术指标第二批

在 MA5/MA10 稳定后，按顺序实现：

## 9.1 EMA

- EMA12
- EMA26

## 9.2 MACD

- DIF = EMA12 - EMA26
- DEA = DIF 的 EMA9
- Histogram = DIF - DEA

## 9.3 RSI14

使用标准涨跌平均口径，处理：

- 全涨
- 全跌
- 全平
- 数据不足

### 实现规则

1. 指标必须放在 `finance.chart` 的纯计算类。
2. 不直接写进 GUI 渲染方法。
3. 数据不足返回 NaN 或明确缺失值。
4. GUI 不连接跨越缺失区间的线。
5. 每个公式有确定性测试数据。

### UI 范围控制

第一轮可以只增加指标切换按钮：

- MA
- MACD
- RSI

不要同时全部叠在主价格图，避免不可读。

## 10. 新功能：行情榜单

管理员仪表盘或独立行情区域增加：

- 商品涨幅榜 Top 5
- 商品跌幅榜 Top 5
- 股票涨幅榜 Top 5
- 股票跌幅榜 Top 5
- 商品成交量榜 Top 5
- 股票成交量榜 Top 5
- 放量榜 Top 5

### 规则

1. 使用当前 K 线和前一根完整 K 线计算。
2. 无前收标的不进入涨跌榜。
3. 无成交标的是否进入榜单需明确，建议不进入成交量榜。
4. 排名必须确定性处理同值。
5. 每榜限制数量，不能发送全量。

### 测试

- 正常排名
- 同值排序
- 无历史
- 极值涨跌
- 当前日未完成数据
- 商品和股票隔离

## 11. 新功能：价格预警联动

在现有 PriceAlert 上扩展以下类型中的至少两种：

- 突破昨日最高价
- 跌破昨日最低价
- 单日涨幅超过阈值
- 单日跌幅超过阈值
- 成交量超过五日均量倍数

### 要求

1. 预警基于服务端 K 线服务，不基于客户端图表。
2. 同一条件同一日避免重复触发。
3. 存档恢复后不重复补发旧预警。
4. 对数据不足情况不触发。
5. 玩家输入阈值需要边界验证。

## 12. 网络与性能要求

新增盘口、成交明细和指标后，必须控制数据量：

1. 菜单初始化不携带全市场完整历史。
2. 标的切换后按需请求。
3. 同一请求在短时间内去重。
4. 服务端设置每玩家请求频率限制，例如每秒合理次数。
5. 响应条目均有硬上限。
6. 字符串、enum、数组长度全部验证。
7. handler 在正确线程执行。
8. dedicated server 不加载 client-only 类。

建议引入统一行情详情请求：

```text
MarketDetailRequestPacket
MarketDetailResponsePacket
```

但如果改造风险过大，可以继续使用独立包。不要让两套协议重复发送相同的大数据。

## 13. GUI 设计要求

保持当前 400×250 主面板，不扩大窗口。

建议布局：

- 顶部：标的、最新价、涨跌幅、窗口按钮
- 中部主区：K 线
- 下部：成交量或 MACD/RSI 子图
- 右侧或可切换面板：五档盘口
- 底部：最近成交

如果空间不足，使用子标签：

- K线
- 盘口
- 成交

### 必须处理

- 空数据
- 加载中
- 单根 K 线
- 平价区间
- 极端价格
- 30/60/120 根
- 窄窗口
- tooltip 不超出屏幕
- 文字不覆盖按钮
- 快速切换标的与窗口

## 14. 测试策略

## 14.1 结算测试

- 公司采购/出售正常路径
- 所有失败点
- 资金守恒
- 商品守恒
- 流水一致
- 指标与 K 线一致

## 14.2 协议测试

- requestId/limit 往返
- 响应乱序
- 最新 N 根截断
- 非法 id
- 非法 enum
- 数组上限
- 请求频率限制

## 14.3 行情测试

- 盘口五档
- 同价合并
- 部分成交
- 近期成交排序
- EMA/MACD/RSI
- 榜单
- 预警去重

## 14.4 持久化测试

如果新功能增加持久化状态：

- 旧存档缺失字段
- 新存档往返
- 损坏字段跳过
- 上限裁剪
- 重启不重复触发

## 14.5 GUI 手工验证

条件允许时运行：

```powershell
.\gradlew.bat runClient
```

至少完成：

1. 公司经营跨日后商品 K 线出现对应成交。
2. 快速切换 120→30→60，最终窗口与按钮一致。
3. 快速切换两个商品，不出现串图。
4. 五档盘口与真实订单一致。
5. 下单、部分成交、撤单后盘口更新。
6. MACD/RSI 切换正常。
7. 预警条件触发一次且不重复。
8. 重启世界后历史和预警状态正常。

如果无法执行 GUI，最终报告必须明确标注“未完成 GUI 手工验证”。

## 15. 推荐执行阶段

严格按顺序推进：

1. 建立 Git 和测试基线。
2. 为公司自动交易问题编写失败测试。
3. 原子化公司采购和出售。
4. 接入公司成交指标与 K 线。
5. 审核全部布尔返回值调用方。
6. 运行完整测试，形成稳定点。
7. 为 K 线请求加入 requestId/limit 一致性。
8. 修复最新历史截断。
9. 增加加载/失败状态。
10. 修复行情摘要极值算术。
11. 实现商品五档盘口。
12. 实现股票五档盘口。
13. 增加近期成交明细。
14. 实现 EMA/MACD。
15. 时间允许时实现 RSI。
16. 实现行情榜单。
17. 实现至少两种 K 线预警。
18. 做网络限流与包大小检查。
19. 更新 GUI。
20. 更新 README、CHANGELOG、DEVELOPMENT_NOTES。
21. 完整测试和编译。
22. GUI 手工验证。
23. 输出最终交付报告。

## 16. 每阶段验证

相关测试示例：

```powershell
.\gradlew.bat test --tests "finance.company.*"
.\gradlew.bat test --tests "finance.chart.*"
.\gradlew.bat test --tests "finance.network.*"
.\gradlew.bat test --tests "finance.market.*"
```

完成一个大阶段后执行：

```powershell
.\gradlew.bat cleanTest test
```

不要累计数百行修改后才第一次编译。

## 17. 文档更新

更新：

- `README.md`
- `CHANGELOG.md`
- `DEVELOPMENT_NOTES.md`

记录：

- 公司交易是否进入公开成交量
- 央行干预是否计入成交量
- 原子结算边界
- K 线请求一致性设计
- 盘口档位数量
- 近期成交上限
- MACD/RSI 公式口径
- 榜单口径
- 预警去重策略

## 18. 最终验证命令

```powershell
git diff --check
.\gradlew.bat cleanTest test
.\gradlew.bat compileJava
git status --short
git diff --stat
```

如修改网络注册，额外核对：

- 消息 ID 不冲突
- 协议方向正确
- 请求和响应版本一致
- dedicated server 类加载安全
- 客户端缓存不会跨世界泄漏旧数据

## 19. 完成定义

只有同时满足以下条件才能宣布完成：

- 公司采购和自动出售完全原子化。
- 失败公司交易不移动任何资产。
- 成功公司交易进入指标和 K 线且只计一次。
- 关键布尔返回值调用方已审核。
- 快速切换窗口不会被迟到响应覆盖。
- 响应截断保留最新数据。
- K 线加载中、空数据和失败状态可区分。
- 商品和股票五档盘口正确。
- 近期成交按新到旧显示且有上限。
- EMA/MACD 完成并有公式测试。
- 至少完成两个行情预警类型。
- 网络请求具有上限与频率控制。
- 全部自动化测试通过。
- `git diff --check` 通过。
- GUI 已手工验证，或明确记录无法验证。
- 文档与实际行为一致。

## 20. 每阶段报告模板

```text
阶段：
完成内容：
关键设计决定：
修改文件：
新增/修改测试：
验证命令：
验证结果：
剩余风险：
下一阶段：
```

## 21. 最终交付报告模板

```text
1. 总体结论
2. 公司交易原子性修复
3. 指标与 K 线口径
4. K 线请求一致性修复
5. 盘口与近期成交
6. 新增技术指标
7. 行情榜单与预警
8. 修改文件列表
9. 新增测试列表
10. 最终测试数量和结果
11. GUI 手工验证结果
12. 未完成事项与下一步建议
```

执行者应持续推进到必选完成条件全部满足，或出现必须由用户作出业务决定、提供授权或改变外部状态才能继续的真实阻塞。

