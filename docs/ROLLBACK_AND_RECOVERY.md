# 回退与故障恢复手册

## 操作前

1. 停服并备份整个世界目录，尤其是 `data/finance_data.dat` 与商品库存 SavedData；不要只复制配置文件。
2. 记录 `/finance status` 和 `/finance diagnose` 输出。
3. 保留当前模组 JAR、Forge 版本和 `serverconfig/finance-common.toml`。

## 兼容模式

在世界的 `serverconfig/finance-common.toml` 中设置 `gameplay.minecraftFirstMode=false` 可恢复旧完整金融入口。关闭 `contractsEnabled` 只阻止新合同，既有合同仍可交付、到期和退款；关闭新生产入口不会删除公司、仓库或托管资产。配置应在停服时修改，之后完整重启。

如果实体终端暂时损坏，可由管理员启用旧入口并在安全位置协助取消订单、领取到期资产。仓库超过容量或控制器被拆除时禁止继续存入，但 custody 不会删除；先恢复/重放控制器，再通过合法提取路径取回物品。

## 模块降级

一致性检查可将模块切为只读/暂停：仓库禁止新存入但允许提取；合同禁止发布/接受但允许可验证退款；公司经营暂停生产但不停止旧金融合同。修复根因后执行 `/finance diagnose`，确认无 ERROR/FATAL，再使用：

- `/finance resume warehouse`
- `/finance resume contract`
- `/finance resume company_gameplay`

不要直接编辑 NBT 猜测资产归属，也不要在诊断仍失败时强行恢复模块。

## 回退版本

DataVersion 29 的存档可能包含旧版本不认识的字段。代码加载会忽略缺失的旧字段，但不承诺旧 JAR 能安全写回新字段。真正降级前必须使用降级前备份；不要让旧 JAR 打开并保存唯一的新世界副本。

恢复后依次核对玩家余额/冻结资金、NPC 与央行、公司现金、仓库 custody、合同 escrow、开放订单及公司行动 escrow。总量不一致时保持停服并从最后一份一致备份恢复。
