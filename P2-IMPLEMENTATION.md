# 股票系统 P2 完成清单

## 📋 P2 新增/改动

### 新增文件

- **`StockOrder.java`** — 股票订单模型（买单/卖单）
- **`StockOrderType.java`** — 订单类型枚举（BUY/SELL）
- **`StockTrade.java`** — 股票成交记录
- **`StockOrderManager.java`** — 订单簿核心引擎
  - `placeBuyOrder()` — 挂买单，冻结资金，撮合或做市商成交
  - `placeSellOrder()` — 挂卖单，冻结股票，撮合或做市商成交
  - `cancelOrder()` — 取消订单，退还冻结资产
  - `matchOrder()` — 玩家订单撮合逻辑
  - `marketMakerTrade()` — 做市商保底流动性（fairValue ± 2%）
  - `executeTrade()` — 执行成交，更新账户/持仓/价格/记录
- **`StockOrderPacket.java`** — 网络包，支持挂买单/挂卖单/取消订单

### 改动文件

**StockMarketManager.java**
- `buy()` / `sell()` — 改为调用 `StockOrderManager.placeBuyOrder()` / `placeSellOrder()`
- 新增公开接口：
  - `getOrders()` — 获取所有订单
  - `getOrdersBySymbol()` — 按股票获取订单
  - `getStockTradeHistory()` — 成交历史
  - `cancelStockOrder()` — 取消订单
  - `clearStockOrders()` / `clearStockTradeHistory()` — 清空（持久化用）
  - `addStockOrderDirect()` / `addStockTradeDirect()` — 直接添加（恢复用）

**FinancePacketHandler.java**
- 注册 `StockOrderPacket`

**EconomySavedData.java**
- 保存：`StockOrders` 列表 + `StockTrades` 列表
- 加载：恢复订单和成交记录，自动向前兼容（旧存档无此字段）

## 🎮 P2 游戏体验

### 订单簿模式

玩家现在可以：
1. **挂限价单** — 设定想要的买入/卖出价格和数量
2. **自动撮合** — 与其他玩家订单按价格配对成交（卖方定价）
3. **做市商保底** — 无对手盘时，系统按 fairValue ± 2% 自动成交，保证流动性
4. **取消订单** — 随时取消，资金/股票自动退回

### 成交流程

```
玩家挂买单
  ↓
StockOrderManager.placeBuyOrder()
  ├─ 冻结资金
  ├─ 寻找反向卖单撮合（卖方定价）
  │  ├─ 成交 → 更新账户、持仓、价格
  │  └─ 部分成交 → 剩余数量挂回订单簿
  └─ 无对手盘
     └─ 做市商成交（fairValue ± 2%）
```

## ⚠️ 关键设计

- **不能自己吃自己的单** — MarketManager 已在 P2P 市场中实现，这里复用
- **做市商 UUID = nil (0,0)** — 系统账户，资金无限（不真实扣账）
- **成交通知定价引擎** — 每笔成交推动股价（买入 +，卖出 -）
- **订单持久化** — 服务器重启不丢失待成交订单

## 🧪 IDEA 编译验证

```
Build → Rebuild Project
预期：无新的编译错误
```

## 🚀 P3 预告

P3 将实现：**公司盈利 + 定期分红**

- Company 新增字段：`dailyRevenue`, `dailyCost`, `retainedEarnings`
- 每 MC 天统计：revenue = 卖出收入，cost = 购买支出
- 每 N 天分红：按持股比例分配 retainedEarnings × 40%
- fairValue 改进：加入 PE 系数，基本面更准
- GUI：显示股息率、分红记录

这样持股不再是纯赌博，有稳定的现金流回报。
