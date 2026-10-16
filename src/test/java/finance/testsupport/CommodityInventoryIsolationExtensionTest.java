package finance.testsupport;

import finance.commodity.CommodityInventoryManager;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 自动注册的扩展会在每个测试前清空商品库存。 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CommodityInventoryIsolationExtensionTest {
    private static final UUID HOLDER = UUID.nameUUIDFromBytes("isolation-holder".getBytes());

    @Test @Order(1) void leavesInventoryBehind() {
        assertTrue(CommodityInventoryManager.setCommodity(HOLDER, "iron", 42));
    }

    @Test @Order(2) void nextTestStartsClean() {
        assertEquals(0, CommodityInventoryManager.getCommodityAmount(HOLDER, "iron"));
    }
}
