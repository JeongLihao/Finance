# 股票系统 P3 完成清单

## 📋 P3 新增/改动

### 新增方法

**Company.java**
- `settleDailyProfits()` — 每 MC 天调用，计算日利润 (revenue - cost) 并累加到留存收益
- `tryDividend(currentMcDay)` — 每 7 天尝试分红，分红比 40%，返回分红总额
- `getDividendYieldPercent()` — 获取股息率（年化收益率），用于 GUI 显示

**CompanyManager.java**
- `settleDailyProfits()` — 对所有公司结算日利润
- `tryDividends(currentMcDay)` — 对所有公司尝试分红

**StockPriceEngine.java**
- `updateFairValue()` 改进 — 加入 PE 系数（平均盈利倍数 10 倍）
  ```
  fairValue = (公司总估值 + 日利润 × 10) / totalShares
  ```

### 改动文件

**Company.java**
- 新增字段：`dailyRevenue`, `dailyCost`, `retainedEarnings`, `lastDividendDay`
- `buyFromInternationalMarket()` — 记录 dailyCost
- `autoTrade()` — 记录 dailyRevenue

**CompanyManager.java**
- `tickAll()` 后跟调 `settleDailyProfits()` 和 `tryDividends()`

**FinanceMod.java**
- 每 MC 天调用 `CompanyManager.settleDailyProfits()` 结算日利润
- 每 7 天调用 `CompanyManager.tryDividends()` 进行分红

**StockMarketManager.java**
- `updateFairValuesAndResetDay()` — 传递日利润到 PE 计算

**EconomySavedData.java**
- 保存 P3 字段到 NBT
- 加载时向后兼容（旧存档无此字段）

## 🎮 P3 游戏体验

### 公司有利润了

**收入来源**
- 每天 autoTrade 时卖产出给国际市场

**支出**
- 生产前购买原料（如库存不足）

**利润 = 收入 - 支出**
- 每天自动计算并累加到"留存收益"

### 分红机制

**每 7 MC 天**
1. 计算本期利润：retainedEarnings
2. 分红 40%，即 `dividend = retainedEarnings × 0.4`
3. 剩余 60% 保留再投资

**分红分配（待实现，P4 后补）**
- 按持股比例分给所有股东
- 直接转账到玩家账户

### 股价基本面更强了

**股价现在反映**
- 公司资产 + 盈利能力
- PE 倍数 = 10，意味着 10 天收益 = 公司价值
- 高利润公司的股价会被 PE 系数拉起

## ⚠️ 关键设计

- **PE 系数 = 10** —— 保守估计，避免过度估值
- **分红比 40%** —— 平衡回报和再投资
- **分红周期 7 天** —— 接近真实股市的季度/月度分红
- **日结清零** —— dailyRevenue 和 dailyCost 每天重置，防止累积重复计算

## 🧪 验证要点

```
Build → Rebuild Project
关键检查：
  ✓ Company 构造没有 dailyRevenue/Cost 的初始化（默认 0 没问题）
  ✓ CompanyManager.settleDailyProfits() 被每天调用
  ✓ tryDividends() 在 7 天周期内正确触发
  ✓ StockPriceEngine PE 计算无溢出
```

## 🚀 P4 预告

**玩家 IPO 系统**

- Company 新增 `isPublic` 标志和 IPO 机制
- 玩家公司可选择上市：选择发行股数、发行价
- 募集资金 = 发行数 × 发行价，进入公司现金
- 创始人保留 ≥51% 股份，保证控制权
- GUI：新增"上市"按钮和 IPO 表单

**分红完成**
- CompanyManager.tryDividends() 实现分红分账
- 按持股比例向所有股东转账

这样完成整个股票系统的游戏循环：**生产 → 盈利 → 分红 → 股价上升 → 融资 → 扩大生产**。
