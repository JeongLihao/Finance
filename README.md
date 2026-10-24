# Finance — Minecraft 经济与金融模组

Finance 给 Minecraft Forge 加入一套能自己运转的经济：商品市场、公司经营、股票、银行和中央银行。下面还有一层实体经济——村庄人口要吃穿住，物价随供需涨跌，工厂要雇工人，中央银行用利率、准备金率、公开市场操作和商品平准稳住物价。单人游玩时，NPC 做市商、NPC 投资者和 NPC 竞争公司提供对手盘。

- 版本：v0.4.1（v0.4.2 开发中）
- 平台：Minecraft 1.20.1 / Forge 47.4.16 / Java 17
- 作者：H-Jeong
- 更新日志：[CHANGELOG.md](CHANGELOG.md)

## 安装

1. 安装 Minecraft Forge 1.20.1（推荐 47.4.16）。
2. 把 Finance 的 JAR 放进服务器和客户端的 `mods` 文件夹，两边版本必须一致。
3. 可选：客户端装 Ponder 1.0.92+，可以看金融方块的动画教学；不装不影响玩法。

## 怎么玩

按 `K`（或右键金融账本）打开金融中心。金融中心有四页：

| 页面 | 能做什么 |
| --- | --- |
| 首页 | 钱包、身家走势、今日机会卡、经济天气、排行榜 |
| 买卖 | 按市场价买卖商品，挂单，搜索和收藏商品 |
| 我的公司 | 开公司、老板待办、股东大会、保险 |
| 投资 | 股票、三档理财（稳健/均衡/进取）、押注涨跌 |

- 进阶玩法按进度解锁：存过理财解锁进取理财，买过股票解锁押注，公司上市解锁大老板玩法。
- 标题栏“更多操作”里有融资融券、我的银行、要约收购、中央银行、现金兑换、财报商情等，原来要敲命令的功能都能点。
- 标题栏“教程”进入教程中心：九步主线和六条可选路线，右上角目标卡跟着服务端真实进度走。
- 货在仓库里进出：在自己的仓库控制器或市场终端旁把背包里的货存进去，之后人在哪里都能卖；买到的货也放在仓库里，回来再取。手里拿着市场商品按 `K` 直接打开这件东西的买卖页；背包里的商品悬停能看到卖价。
- 完整的老界面（行情、交易、K 线、债市、期货等）收在“高级”里，默认只对管理员开放。

更多上手说明见 [玩家快速上手](docs/PLAYER_QUICKSTART.md)。

## 货币

单位是英镑：账户里 1 个单位 = 1 便士，100 便士 = 1 磅，新玩家有 10 磅。有三种现金物品（1 便士、10 便士、1 磅纸币），可以和账户存取，也能和绿宝石兑换（在“更多操作 → 现金兑换”或用 `/cash`）。现金不记名，丢了就是别人的。

## 主要系统

- **商品市场**：国际市场即时买卖，玩家之间挂单撮合；价格随库存、供需和随机事件变化。
- **仓库与合同**：放置仓储控制器存放实物，接 NPC 采购合同交货领奖励；密封货箱负责仓库之间的实物运输。
- **公司**：办公桌、成员和角色、仓库，以及加工机、农场机、伐木机、采矿机、剪毛机和收货箱。公司的产量全部来自这些机器真实干的活，机器从附近村庄雇工、可以升到 3 档，详见 [公司机器](docs/COMPANY_FACILITIES.md)。
- **股票**：公司可以发起上市认购：NPC 投资者按估值、玩家用真钱认购几天，认够了才上市，不够就退款。股价 = 家底 + 日利润 × 倍数，倍数只看央行利率；有分红、增发、股东投票、财报季、要约收购和破产退市，大量抛售可能触发熔断。
- **NPC**：做市商提供报价；分红派、成长派、短线派、价值派四类 NPC 投资者真实买卖并投票；NPC 竞争公司会抢原料、上市，开启配置后还会发起敌对收购。
- **银行与央行**：活期、定期、贷款、存款保险和银行挤兑，玩家也能开银行。中央银行只管货币和物价：盯住 2% 通胀目标议息，调整准备金率，做公开市场操作，消费品价格偏离太大时用储备平准。
- **其他金融产品**：公司债与债市、央行票据和债券、指数基金、商品期货、融资融券、企业保险。
- **村庄与宏观经济**：村庄人口按食物、住房、工具、取暖、衣物五类需求消费，各地物价不同，商队在聚落之间套利；每周出一期《金融周报》报纸。设计见 [宏观实体经济](docs/MACRO_ECONOMY.md)。

## 命令

### 玩家

| 命令 | 说明 |
| --- | --- |
| `/balance`、`/pay <玩家> <金额>` | 查余额、转账 |
| `/sell hand [all]` | 按手上拿的东西的种类，卖掉仓库里的一组（`all` 把仓库里这种货全卖） |
| `/cash ...` | 现金存取（`withdraw`/`deposit`）、绿宝石兑换（`sell`/`buy`） |
| `/market price [商品]`、`/market top`、`/market losers`、`/market overview` | 行情 |
| `/market orders`、`/market cancel <编号>`、`/market history [商品]` | 挂单和成交记录 |
| `/companies`、`/company mine`、`/company info <名称>` | 公司 |
| `/margin ...` | 融资融券 |
| `/takeover ...` | 要约收购 |
| `/earnings calendar [天数]`、`/earnings report <股票>` | 财报 |
| `/bank ...` | 开银行、设利率、分红 |
| `/macro`、`/macro cb`、`/macro news`、`/macro radar`、`/macro wage <工资>` | 宏观、央行、周报、商情调查、工资 |

这些命令在金融中心里都有对应按钮。

### 管理员（OP 2）

| 命令 | 说明 |
| --- | --- |
| `/finance give <玩家> <金额>`、`/commodity give <玩家> <商品> <数量>` | 发钱、发商品 |
| `/company create <类型> <名称>` | 创建公司 |
| `/caravan ...` | 商队路线 |
| `/market debug nextday`、`/market debug fire <minor\|major\|blackswan>` | 推进一天、触发市场事件 |
| `/finance status`、`/finance diagnose` | 模块状态、只读一致性检查 |
| `/finance resume <模块>` | 修好后恢复被暂停的模块 |

## 服务器

- 配置文件是 `config/finance-common.toml`，服务器说了算，客户端改不了结算。说明和四份建议配置见 [配置说明](docs/CONFIG_ZH.md)。
- 迁移、回退、故障恢复和存档兼容规则见 [服务器运维](docs/SERVER_ADMIN.md)。
- 数据存在世界存档里（`finance_data`、`finance_inventory`），当前 DataVersion 44，网络协议 31。

## 其他文档

- [聚落与本地经济](docs/SETTLEMENT_LOCAL_ECONOMY.md)、[实体物流](docs/LOGISTICS_ASSET_LIFECYCLE.md)、[探索与世界生成](docs/EXPLORATION_AND_WORLDGEN.md)、[区域风险金融](docs/REGIONAL_RISK_FINANCE.md)
- [生存进程与设施等级](docs/SURVIVAL_PROGRESSION_MATRIX.md)
- 开发者：[架构](docs/GAMEPLAY_ARCHITECTURE.md)、[资产守恒](docs/ASSET_CONSERVATION_MAP.md)

## 许可证

Finance 以 [GNU LGPL v3.0 或更高版本](COPYING.LESSER) 发布（GPL v3 全文见 [COPYING](COPYING)）。Minecraft、Forge 及其他依赖各自遵循原有许可证。
