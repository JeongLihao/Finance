package finance.gameplay.company.factory;

import static org.junit.jupiter.api.Assertions.*;

import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.gameplay.company.*;
import finance.gui.CompanyGameplayMenu;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FactoryViewTest {
    private CompanyFacilityRecord facility;

    @BeforeEach
    void setup() {
        CompanyManager.clearCompaniesDirect();
        CompanyGameplayManager.clearDirect();
        CompanyFacilityManager.clearDirect();
        Company c = new Company(UUID.randomUUID(), "Bakery", CompanyType.FOOD_PROCESSING, 10_000, UUID.randomUUID());
        CompanyManager.registerDirect(c);
        CompanyGameplayManager.createForNewCompany(c);
        facility = new CompanyFacilityRecord(
                UUID.randomUUID(),
                c.getCompanyId(),
                "minecraft:overworld",
                new BlockPos(0, 64, 0),
                CompanyFacilityType.FACTORY_CONTROLLER,
                2,
                CompanyFacilityStatus.ACTIVE,
                -1,
                null);
        facility.factory().markNewBuild();
        CompanyFacilityManager.restore(facility);
    }

    @AfterEach
    void cleanup() {
        CompanyManager.clearCompaniesDirect();
        CompanyGameplayManager.clearDirect();
        CompanyFacilityManager.clearDirect();
    }

    @Test
    void viewListsEquipmentHaveNeedAndAvailability() {
        facility.factory()
                .recordScan(
                        Map.of(
                                FactoryEquipment.CRAFTING_TABLE,
                                2,
                                FactoryEquipment.BARREL,
                                2,
                                FactoryEquipment.SMOKER,
                                1),
                        3,
                        16);
        facility.factory().resolveDay(CompanyType.FOOD_PROCESSING, 2, 3, 14, 7, 3);
        CompanyGameplayMenu.FactoryView view = FactoryMethodService.view(null, facility, 3);
        assertEquals(3, view.methods().size());
        assertEquals("MANUAL", view.activeTier());
        var manual = view.methods().get(0);
        assertTrue(manual.available());
        var steam = view.methods().get(1);
        assertTrue(steam.levelOk(), "2 级可用改良档");
        assertFalse(steam.available(), "烟熏炉只有 1 台");
        assertTrue(steam.equipment().contains(new CompanyGameplayMenu.EquipRow("SMOKER", 1, 3)));
        var line = view.methods().get(2);
        assertFalse(line.levelOk(), "工业档要 3 级");
        assertEquals(List.of(new CompanyGameplayMenu.FlowRow("bread", 30)), manual.outputs());
        assertEquals(10, view.jobs(), "手工作坊：工作台 2 台 × 5 岗，木桶不提供岗位");
        assertEquals(40, view.jobsCap());
        assertEquals(-1, view.provisionalDaysLeft());
        assertEquals(4, view.zoneRadius());
    }

    @Test
    void factoryViewRoundTripsThroughMenuCodec() throws Exception {
        facility.factory().recordScan(Map.of(FactoryEquipment.CRAFTING_TABLE, 2, FactoryEquipment.BARREL, 2), 3, 16);
        facility.factory().resolveDay(CompanyType.FOOD_PROCESSING, 2, 3, 14, 7, 3);
        CompanyGameplayMenu.FactoryView view = FactoryMethodService.view(null, facility, 3);
        Method write = CompanyGameplayMenu.class.getDeclaredMethod(
                "writeFactory", FriendlyByteBuf.class, CompanyGameplayMenu.FactoryView.class);
        Method read = CompanyGameplayMenu.class.getDeclaredMethod("readFactory", FriendlyByteBuf.class);
        write.setAccessible(true);
        read.setAccessible(true);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        write.invoke(null, buf, view);
        assertEquals(view, read.invoke(null, buf));
        FriendlyByteBuf empty = new FriendlyByteBuf(Unpooled.buffer());
        write.invoke(null, empty, null);
        assertNull(read.invoke(null, empty), "不是从控制器打开时没有厂房页");
    }

    @Test
    void legacyFacilityViewShowsProvisionalCountdown() {
        CompanyFacilityRecord legacy = new CompanyFacilityRecord(
                UUID.randomUUID(),
                facility.companyId(),
                "minecraft:overworld",
                new BlockPos(40, 64, 0),
                CompanyFacilityType.FACTORY_CONTROLLER,
                1,
                CompanyFacilityStatus.ACTIVE,
                -1,
                null);
        legacy.factory().resolveDay(CompanyType.FOOD_PROCESSING, 1, 10, 14, 7, 3);
        CompanyGameplayMenu.FactoryView view = FactoryMethodService.view(null, legacy, 12);
        assertEquals("", view.activeTier());
        assertEquals(13, view.provisionalDaysLeft(), "第 10 天开始，截止第 24 天，第 12 天还剩 13 天");
    }
}
