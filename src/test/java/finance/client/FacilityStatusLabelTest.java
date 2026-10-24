package finance.client;

import static org.junit.jupiter.api.Assertions.*;

import finance.gameplay.company.CompanyFacilityStatus;
import org.junit.jupiter.api.Test;

/** 公司经营界面不应再显示英文枚举名：每个设施状态都要有中文名。 */
class FacilityStatusLabelTest {
    @Test
    void everyFacilityStatusHasAChineseLabel() {
        for (CompanyFacilityStatus s : CompanyFacilityStatus.values()) {
            String label = FinanceFormat.facilityStatusLabel(s.name());
            assertNotEquals(s.name(), label, s.name());
            assertFalse(label.isBlank(), s.name());
        }
        assertEquals("缺原料", FinanceFormat.facilityStatusLabel("MISSING_INPUT"));
    }

    @Test
    void marketTableNeedsScrollingForTheNewCommodities() {
        assertTrue(MarketPanel.marketCapacity() < 7, "七种默认商品放不进行情表，必须能滚动");
    }
}
