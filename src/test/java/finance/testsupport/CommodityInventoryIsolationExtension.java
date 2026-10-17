package finance.testsupport;

import finance.data.CommodityInventorySavedData;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * 每个测试开始前清空商品库存。
 *
 * <p>商品库存是单独的存档（{@link CommodityInventorySavedData}），{@code EconomySavedData.resetRuntimeState()}
 * 有意不清它（加载经济存档时会调用重置，不能把已加载的库存抹掉），所以测试之间要另外隔离，
 * 否则上一个测试留下的 NPC 库存会改变下一个测试的价格。通过 ServiceLoader 自动注册到所有测试。</p>
 */
public final class CommodityInventoryIsolationExtension implements BeforeEachCallback {
    @Override
    public void beforeEach(ExtensionContext context) {
        CommodityInventorySavedData.resetRuntimeState();
    }
}
