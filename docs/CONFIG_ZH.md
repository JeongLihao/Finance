# Finance 服务器配置说明

配置类型：Forge COMMON 配置。  
配置文件由 Forge 在运行环境中生成，修改后建议重启服务器；部分参数在代码中每次使用时读取，Forge 完成配置重载后也能生效，但生产服仍建议重启以避免玩家观察到半周期变化。

## 默认配置示例

```toml
[company]
	# 公司没有单独分红策略时使用的默认分红比例。0.40 = 40%
	defaultDividendRatio = 0.40
	# 公司没有单独分红策略时使用的默认分红周期，单位为 MC 天。
	defaultDividendCycleDays = 7
	# 上市公司现金低于安全线后，持续多少个 MC 天触发破产。
	bankruptcyRiskDays = 3
	# 破产现金安全线倍率。安全线 = 预计每日运营成本 × 此倍率。
	bankruptcyCashRiskMultiplier = 1.0
	# 发起 IPO 时向公司所有者收取的费用。
	ipoFee = 5000

[proposal]
	# 股东提案最低参与率。0.25 = 至少 25% 投票权参与，提案才可能通过。
	minParticipationRatio = 0.25

[orders]
	# 每名玩家最多保留的未触发价格提醒数量。
	maxPriceAlertsPerPlayer = 20
	# 每名玩家最多保留的股票条件委托数量。
	maxConditionalStockOrdersPerPlayer = 20

[stockMarketMaker]
	# 股票做市商价差。0.02 = fairValue 上下 2% 形成 bid / ask。
	spread = 0.02
```

## 调参建议

- 想让公司更容易活下来：提高 `bankruptcyRiskDays`，或降低 `bankruptcyCashRiskMultiplier`。
- 想减少刷屏式提醒和委托：降低 `maxPriceAlertsPerPlayer` 与 `maxConditionalStockOrdersPerPlayer`。
- 想降低做市商对玩家的自动成交能力：提高 `stockMarketMaker.spread`。
- 想让股东投票更像正式治理：提高 `proposal.minParticipationRatio`。
- 想减少公司上市速度：提高 `company.ipoFee`。
