package finance.contract;

import finance.account.AccountManager;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityRegistry;
import finance.data.serializer.AccountDataSerializer;
import finance.data.serializer.ContractDataSerializer;
import finance.data.serializer.WarehouseDataSerializer;
import finance.market.NpcMarketMaker;
import finance.diagnostic.ModuleHealthRegistry;
import finance.diagnostic.ModuleRunState;
import finance.warehouse.WarehouseManager;
import finance.warehouse.WarehousePermissionMode;
import finance.warehouse.WarehouseRecord;
import finance.warehouse.WarehouseStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ContractManagerTest {
    @BeforeEach void setup() {
        ContractManager.clearDirect(); AccountManager.clearAccountsDirect(); AccountManager.clearTransactions();
        WarehouseManager.clearDirect();
        ModuleHealthRegistry.clear();
        CommodityRegistry.register(new Commodity("contract_iron", "minecraft:iron_ingot", "Contract Iron",
                CommodityCategory.RAW_MATERIALS, 10));
        AccountManager.getOrCreateSystemAccount(NpcMarketMaker.NPC_UUID).setBalance(100_000);
    }
    @AfterEach void cleanup() {
        ContractManager.clearDirect(); AccountManager.clearAccountsDirect(); AccountManager.clearTransactions();
        WarehouseManager.clearDirect();
        ModuleHealthRegistry.clear();
        CommodityRegistry.removeCommodity("contract_iron");
    }

    @Test void npcContractMovesExistingMoneyIntoZeroBalanceEscrow() {
        FinanceContract contract = ContractManager.createNpcProcurement("contract_iron", 20, 500, 1, 4);
        assertNotNull(contract);
        assertEquals(99_500, AccountManager.getBalance(NpcMarketMaker.NPC_UUID));
        assertEquals(500, AccountManager.getBalance(contract.escrowAccountId()));
        assertNull(ContractManager.createNpcProcurement("contract_iron", 20, 500, 1, 4));
        assertEquals(100_000, AccountManager.getAccounts().values().stream()
                .mapToLong(account -> account.getBalance()).sum());
    }

    @Test void expiryRefundsExactlyOnce() {
        FinanceContract contract = ContractManager.createNpcProcurement("contract_iron", 20, 500, 1, 2);
        ContractManager.settleExpired(3);
        assertEquals(ContractStatus.EXPIRED, contract.status());
        assertEquals(100_000, AccountManager.getBalance(NpcMarketMaker.NPC_UUID));
        assertEquals(0, AccountManager.getBalance(contract.escrowAccountId()));
        ContractManager.settleExpired(4);
        assertEquals(100_000, AccountManager.getBalance(NpcMarketMaker.NPC_UUID));
    }

    @Test void liveContractAndDailyKeysRoundTripWithoutMinting() {
        FinanceContract contract = ContractManager.createNpcProcurement("contract_iron", 20, 500, 1, 4);
        ContractManager.restoreDailyKey("1:contract_iron");
        CompoundTag root = new CompoundTag(); AccountDataSerializer.save(root); ContractDataSerializer.save(root);
        ContractManager.clearDirect(); AccountManager.clearAccountsDirect(); AccountManager.clearTransactions();
        AccountDataSerializer.load(root); ContractDataSerializer.load(root);
        FinanceContract loaded = ContractManager.get(contract.id());
        assertNotNull(loaded);
        assertEquals(500, AccountManager.getBalance(loaded.escrowAccountId()));
        assertTrue(ContractManager.dailyKeys().contains("1:contract_iron"));
        assertEquals(100_000, AccountManager.getAccounts().values().stream()
                .mapToLong(account -> account.getBalance()).sum());
    }

    @Test void malformedContractIsSkippedWithoutMovingEscrow() {
        FinanceContract contract = ContractManager.createNpcProcurement("contract_iron", 20, 500, 1, 4);
        CompoundTag root = new CompoundTag(); ContractDataSerializer.save(root);
        root.getCompound(ContractDataSerializer.ROOT).getList("Records", 10).getCompound(0).putLong("Reward", 999);
        ContractDataSerializer.load(root);
        assertNull(ContractManager.get(contract.id()));
        assertEquals(500, AccountManager.getBalance(contract.escrowAccountId()));
    }

    @Test void acceptedAndCompletedStatesRoundTripWithoutASecondReward() {
        UUID player = UUID.randomUUID();
        WarehouseRecord warehouse = new WarehouseRecord(UUID.randomUUID(), "minecraft:overworld", BlockPos.ZERO,
                player, null, 4096, WarehouseStatus.ACTIVE, 0, 0, WarehousePermissionMode.OWNER_ONLY);
        assertTrue(WarehouseManager.restore(warehouse));
        FinanceContract contract = ContractManager.createNpcProcurement("contract_iron", 20, 500, 1, 4);
        assertNotNull(contract);
        assertTrue(contract.accept(player, warehouse.warehouseId()));

        CompoundTag acceptedSave = new CompoundTag();
        AccountDataSerializer.save(acceptedSave); WarehouseDataSerializer.save(acceptedSave); ContractDataSerializer.save(acceptedSave);
        ContractManager.clearDirect(); WarehouseManager.clearDirect(); AccountManager.clearAccountsDirect();
        AccountDataSerializer.load(acceptedSave); WarehouseDataSerializer.load(acceptedSave); ContractDataSerializer.load(acceptedSave);
        FinanceContract accepted = ContractManager.get(contract.id());
        assertNotNull(accepted);
        assertEquals(ContractStatus.ACCEPTED, accepted.status());

        assertTrue(AccountManager.moveFunds(accepted.escrowAccountId(), player, accepted.rewardAmount()));
        accepted.complete();
        long paidBalance = AccountManager.getBalance(player);
        CompoundTag completedSave = new CompoundTag();
        AccountDataSerializer.save(completedSave); WarehouseDataSerializer.save(completedSave); ContractDataSerializer.save(completedSave);
        ContractManager.clearDirect(); WarehouseManager.clearDirect(); AccountManager.clearAccountsDirect();
        AccountDataSerializer.load(completedSave); WarehouseDataSerializer.load(completedSave); ContractDataSerializer.load(completedSave);
        assertEquals(ContractStatus.COMPLETED, ContractManager.get(contract.id()).status());
        assertEquals(paidBalance, AccountManager.getBalance(player));
    }

    @Test void pausedContractModuleBlocksIssuanceWithoutMovingFunds() {
        ModuleHealthRegistry.restrict(ModuleHealthRegistry.Module.CONTRACT, ModuleRunState.PAUSED,
                "test quarantine", 2);
        assertNull(ContractManager.createNpcProcurement("contract_iron", 20, 500, 2, 5));
        assertEquals(100_000, AccountManager.getBalance(NpcMarketMaker.NPC_UUID));
        assertTrue(ContractManager.contracts().isEmpty());
    }

    @Test void pausedContractModuleStillRefundsVerifiableExistingEscrow() {
        FinanceContract contract = ContractManager.createNpcProcurement("contract_iron", 20, 500, 1, 2);
        assertNotNull(contract);
        ModuleHealthRegistry.restrict(ModuleHealthRegistry.Module.CONTRACT, ModuleRunState.PAUSED,
                "test quarantine", 2);
        ContractManager.processDay(3);
        assertEquals(ContractStatus.EXPIRED, contract.status());
        assertEquals(100_000, AccountManager.getBalance(NpcMarketMaker.NPC_UUID));
        assertEquals(0, AccountManager.getBalance(contract.escrowAccountId()));
    }
}
