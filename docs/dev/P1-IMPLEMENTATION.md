# 股票系统 P1 重做完成清单

## 📋 新增/改动文件

### 新增
- **`StockPriceEngine.java`** — 混合定价引擎，核心：
  - `recordTrade()` — 成交时推动动量（买入 +，卖出 -）
  - `updateFairValue()` — 每 MC 天更新基本面锚
  - `tickMomentum()` — 每分钟动量衰减（×0.5）
  - `tickNoise()` — 每 3 分钟噪音游走
  - `recalculate()` — 核心定价公式：向 fairValue 回归 + 动量 + 噪音，夹逼在 [0.3, 3.0]×fairValue
- **`StockPriceEngineTest.java`** — 单元测试（IDEA 可直接运行验证）

### 改动

**Stock.java**
- 新增字段：`floatShares`（流通股）、`ownerShares`（公司持有）、`priceEngine`
- 保留兼容性：`availableShares`（映射到 floatShares）
- 新增方法：
  - `recordTrade(price, volume, isBuy)` — 通知引擎，驱动价格
  - `updateFairValueAndResetDay()` — 基本面更新 + 日统计重置
  - `tickMomentum()`, `tickNoise()`, `recalculateFromCurrent()` — Tick 调用
  - Getter: `getFairValue()`, `getTradeMomentum()`, `getFloatShares()` 等

**StockMarketManager.java**
- 删除：`updatePricesFromCompaniesAndMarket()`（旧覆盖式逻辑）、`averageCommodityChange()`
- 新增方法：
  - `updateFairValuesAndResetDay()` — 基本面更新（每 MC 天）
  - `tickMomentum()` — 动量衰减（每分钟）
  - `tickNoise()` — 噪音刷新（每 3 分钟）
  - `recalculateAllPrices()` — 价格重算
- 改动 `buy()`/`sell()` — 成交时调 `recordTrade(price, volume, isBuy)`

**FinanceMod.java**
- 改 `onServerStarting` — `updatePricesFromCompaniesAndMarket()` → `updateFairValuesAndResetDay()`
- 改 `onServerTick` — 集成新 Tick 调度：
  - 24000t: `updateFairValuesAndResetDay()`
  - 3600t: `tickMomentum()`, `tickNoise()`, `recalculateAllPrices()`
  - 1200t: `tickMomentum()`, `recalculateAllPrices()`

**EconomySavedData.java**
- 保存新字段：`floatShares`, `ownerShares`, `fairValue`, `tradeMomentum`
- 加载时向后兼容：旧存档缺失字段用默认值
  - `floatShares` ← 旧的 `AvailableShares`
  - `fairValue` ← `currentPrice`
  - `ownerShares` ← 0

## 🧪 IDEA 编译验证步骤

1. **打开 IDEA 项目**
   ```
   D:\MCMOD\Finance
   ```

2. **Rebuild 项目**（菜单 Build → Rebuild Project）
   - 确保无编译错误
   - 检查 Warning（应该只有"deprecated"相关的）

3. **运行单元测试**
   ```
   右键 src/test/java/finance/stock/StockPriceEngineTest.java
   → Run 'StockPriceEngineTest.main()'
   ```
   预期输出：
   ```
   ✓ Test 1: 初始化成功
   ✓ Test 2: 买入推高价格 (100 -> 130)
   ✓ Test 3: 卖出压低价格 (130 -> 120)
   ✓ Test 4: 基本面更新 (fairValue=100, price=...)
   ✓ Test 5: 动量衰减 (...  -> ...)
   ✓ Test 6: 价格夹逼 (10 -> 30)
   
   ✅ 所有 P1 测试通过！
   ```

4. **检查是否有其他引用旧方法的地方**
   ```
   Ctrl+Shift+F（全局搜索）
   搜索：updatePricesFromCompaniesAndMarket
   预期结果：无（已删除）
   ```

## 🎯 P1 成果

**操作有反馈** ✅
- 买入 → `recordTrade(..., true)` → 动量 ↑ → 价格 ↑
- 卖出 → `recordTrade(..., false)` → 动量 ↓ → 价格 ↓
- 玩家的每一笔交易都会推动股价，而不是被覆盖

**价格不再被覆盖** ✅
- 旧的 `updatePricesFromCompaniesAndMarket()` 每 tick 覆盖一遍价格 ✗
- 新的每 MC 天只更新 fairValue，价格在 fairValue + 动量 + 噪音的框架内自然演化 ✓

**基本面与股价绑定** ✅
- 公司估值变化 → `updateFairValueAndResetDay()` 更新 fairValue
- 股价缓慢向 fairValue 回归（不是一步到位）
- 长期基本面获胜，短期玩家博弈

**动量与噪音自然衰减** ✅
- 每分钟 `tickMomentum()` 衰减 50%
- 每 3 分钟 `tickNoise()` 游走
- 价格会逐渐「静息」到基本面（如无新交易）

## ⚠️ 已知兼容性处理

- `Stock.getAvailableShares()` 映射到 `floatShares`（旧 API 兼容）
- `Stock.recordTrade(price, qty)` （无 isBuy 参数版）默认当作买入（可用于兼容旧代码）
- 旧存档加载时：floatShares ← AvailableShares，fairValue ← lastPrice

## 🚀 下一步

P2：玩家订单簿 + 做市商保底撮合
- 在 MarketManager 双层模式基础上，为股票加订单簿
- 做市商按 `fairValue ± spread%` 报价成交，保底流动性
- `TradeActionPacket.ActionType` 扩展股票订单类型

P3：公司盈利 + 分红
- Company 增 `dailyRevenue`, `dailyCost`, `retainedEarnings`
- 每 N 天分红一次，按持股比例分配
- fairValue PE 系数：加入盈利能力

P4：IPO
- 玩家公司可发行股票，选择发行数与发行价
- 募集资金进公司现金
- 创始人保留 ≥51% 控制权

P5：GUI 改造
- 订单簿表格、股息率显示、盈亏统计、上市入口
