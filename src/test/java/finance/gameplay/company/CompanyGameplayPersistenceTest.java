package finance.gameplay.company;

import finance.company.*;
import finance.data.serializer.CompanyGameplayDataSerializer;
import finance.data.serializer.WarehouseDataSerializer;
import finance.warehouse.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CompanyGameplayPersistenceTest {
    @BeforeEach void setup(){CompanyManager.clearCompaniesDirect();WarehouseManager.clearDirect();}
    @AfterEach void cleanup(){CompanyManager.clearCompaniesDirect();WarehouseManager.clearDirect();}
    @Test void legacySaveWithoutGameplayRootDefaultsExistingCompanyToLegacy(){Company c=new Company(UUID.randomUUID(),"Old",CompanyType.FOOD,1000,UUID.randomUUID());CompanyManager.registerDirect(c);CompanyGameplayDataSerializer.load(new CompoundTag());assertEquals(CompanyOperatingMode.LEGACY_AUTOMATIC,CompanyGameplayManager.get(c.getCompanyId()).operatingMode());}
    @Test void membersWarehousesFacilitiesAndDailyMarkersRoundTrip(){UUID owner=UUID.randomUUID(),member=UUID.randomUUID();Company c=new Company(UUID.randomUUID(),"Persist",CompanyType.RAW_MATERIALS,10000,owner);CompanyManager.registerDirect(c);CompanyGameplayProfile p=CompanyGameplayManager.createForNewCompany(c);p.putMember(new CompanyMemberRecord(member,CompanyMemberRole.MANAGER,2));p.setLastLegacyFallbackDay(5);WarehouseRecord w=new WarehouseRecord(UUID.randomUUID(),"minecraft:overworld",BlockPos.ZERO,owner,c.getCompanyId(),4096,WarehouseStatus.ACTIVE,0,0,WarehousePermissionMode.OWNER_ONLY);WarehouseManager.restore(w);p.bindWarehouse(w.warehouseId());CompanyFacilityRecord f=new CompanyFacilityRecord(UUID.randomUUID(),c.getCompanyId(),"minecraft:overworld",new BlockPos(3,64,3),CompanyFacilityType.FACTORY_CONTROLLER,2,CompanyFacilityStatus.MISSING_INPUT,5,w.warehouseId());CompanyFacilityManager.restore(f);CompoundTag root=new CompoundTag();WarehouseDataSerializer.save(root);CompanyGameplayDataSerializer.save(root);WarehouseManager.clearDirect();CompanyGameplayManager.clearDirect();WarehouseDataSerializer.load(root);CompanyGameplayDataSerializer.load(root);CompanyGameplayProfile loaded=CompanyGameplayManager.get(c.getCompanyId());assertEquals(CompanyOperatingMode.HYBRID,loaded.operatingMode());assertEquals(CompanyMemberRole.MANAGER,loaded.members().get(member).role());assertTrue(loaded.warehouseIds().contains(w.warehouseId()));assertEquals(5,loaded.lastLegacyFallbackDay());assertEquals(2,CompanyFacilityManager.get(f.facilityId()).productionLevel());}
}
