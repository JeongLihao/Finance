# 开发说明

- 世界级状态由 `EconomySavedData` 按账户/玩家特性/公司/市场/指标/K 线/金融产品/治理/仓库/公司经营/合同/世界反馈的依赖顺序保存和加载；当前 DataVersion 为 29。
- 服务器启动先恢复默认商品定义、清空上个世界静态状态，再加载 SavedData；停服执行 `unload`。客户端登出清空行情和金融产品缓存。
- C2S 包只传意图，必须在解码边界限制字符串、集合、枚举和极值，并在服务层重新校验 sender、权限、菜单会话、维度、距离、所有权、模块健康和幂等键。
- 新资产路径必须更新 `ASSET_CONSERVATION_MAP.md`。提交顺序应可证明：全量预检 → 单线程/同步提交 → 对称回滚 → 状态/operation key 最后推进 → 类型化交易记录。
- 自动化入口：`gradlew test`、`gradlew runGameTestServer`、`gradlew build`。受版本控制的 GameTest 文本结构位于 `src/gametest/resources/finance_empty.snbt`；准备任务会复制到 Forge 1.20.1 要求的运行目录。
