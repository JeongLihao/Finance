package finance.gameplay.company.factory;

import static org.junit.jupiter.api.Assertions.*;

import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.data.serializer.CompanyGameplayDataSerializer;
import finance.data.serializer.WarehouseDataSerializer;
import finance.gameplay.company.*;
import finance.warehouse.*;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FactoryPersistenceTest {
    private CompanyFacilityRecord facility;

    @BeforeEach
    void setup() {
        CompanyManager.clearCompaniesDirect();
        WarehouseManager.clearDirect();
        CompanyGameplayManager.clearDirect();
        CompanyFacilityManager.clearDirect();
        UUID owner = UUID.randomUUID();
        Company c = new Company(UUID.randomUUID(), "Farm", CompanyType.FOOD, 10_000, owner);
        CompanyManager.registerDirect(c);
        CompanyGameplayProfile p = CompanyGameplayManager.createForNewCompany(c);
        WarehouseRecord w = new WarehouseRecord(
                UUID.randomUUID(),
                "minecraft:overworld",
                BlockPos.ZERO,
                owner,
                c.getCompanyId(),
                4096,
                WarehouseStatus.ACTIVE,
                0,
                0,
                WarehousePermissionMode.OWNER_ONLY);
        WarehouseManager.restore(w);
        p.bindWarehouse(w.warehouseId());
        facility = new CompanyFacilityRecord(
                UUID.randomUUID(),
                c.getCompanyId(),
                "minecraft:overworld",
                new BlockPos(3, 64, 3),
                CompanyFacilityType.FACTORY_CONTROLLER,
                3,
                CompanyFacilityStatus.ACTIVE,
                5,
                w.warehouseId());
        CompanyFacilityManager.restore(facility);
    }

    @AfterEach
    void cleanup() {
        CompanyManager.clearCompaniesDirect();
        WarehouseManager.clearDirect();
        CompanyGameplayManager.clearDirect();
        CompanyFacilityManager.clearDirect();
    }

    private static CompoundTag saveAll() {
        CompoundTag root = new CompoundTag();
        WarehouseDataSerializer.save(root);
        CompanyGameplayDataSerializer.save(root);
        return root;
    }

    private static void loadAll(CompoundTag root) {
        WarehouseManager.clearDirect();
        CompanyGameplayManager.clearDirect();
        CompanyFacilityManager.clearDirect();
        WarehouseDataSerializer.load(root);
        CompanyGameplayDataSerializer.load(root);
    }

    @Test
    void factoryStateRoundTrips() {
        FactoryState s = facility.factory();
        s.markNewBuild();
        s.recordScan(
                Map.of(FactoryEquipment.PISTON, 4, FactoryEquipment.OBSERVER, 2, FactoryEquipment.HOPPER, 2), 6, 16);
        s.setTargetTier(ProductionTier.INDUSTRIAL);
        s.resolveDay(CompanyType.FOOD, 3, 6, 14, 7, 3);
        loadAll(saveAll());
        FactoryState loaded = CompanyFacilityManager.get(facility.facilityId()).factory();
        assertEquals(ProductionTier.INDUSTRIAL, loaded.targetTier());
        assertEquals(ProductionTier.INDUSTRIAL, loaded.activeTier());
        assertEquals(4, loaded.equipment().get(FactoryEquipment.PISTON));
        assertEquals(6, loaded.equipmentScanDay());
        assertEquals(FactoryState.PROVISIONAL_NONE, loaded.provisionalUntilDay());
        assertEquals(6, loaded.lastResolvedDay());
        assertTrue(loaded.everActive());
    }

    @Test
    void pre40SaveWithoutFactoryEntersProvisionalWorkshop() {
        CompoundTag root = saveAll();
        ListTag facilities =
                root.getCompound(CompanyGameplayDataSerializer.ROOT).getList("Facilities", Tag.TAG_COMPOUND);
        facilities.getCompound(0).remove("Factory");
        loadAll(root);
        FactoryState loaded = CompanyFacilityManager.get(facility.facilityId()).factory();
        assertEquals(FactoryState.PROVISIONAL_UNSET, loaded.provisionalUntilDay());
        loaded.resolveDay(CompanyType.FOOD, 3, 20, 14, 7, 3);
        assertEquals(34, loaded.provisionalUntilDay());
        assertNotNull(loaded.effectiveMethod(CompanyType.FOOD, 20), "老工厂不会因为更新突然停产");
    }

    @Test
    void corruptFactoryFieldsFallBackSafely() {
        CompoundTag root = saveAll();
        CompoundTag factory = root.getCompound(CompanyGameplayDataSerializer.ROOT)
                .getList("Facilities", Tag.TAG_COMPOUND)
                .getCompound(0)
                .getCompound("Factory");
        factory.putString("Target", "NOT_A_TIER");
        factory.getCompound("Equipment").putInt("NOT_EQUIPMENT", 5);
        loadAll(root);
        CompanyFacilityRecord loaded = CompanyFacilityManager.get(facility.facilityId());
        assertNotNull(loaded, "坏字段不应丢掉整条设施记录");
        assertEquals(ProductionTier.MANUAL, loaded.factory().targetTier());
    }
}
