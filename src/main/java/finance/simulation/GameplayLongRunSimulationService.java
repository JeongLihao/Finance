package finance.simulation;

import finance.account.Account;
import finance.account.AccountManager;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.contract.ContractManager;
import finance.contract.FinanceContract;
import finance.data.EconomySavedData;
import finance.market.NpcMarketMaker;
import finance.warehouse.WarehouseManager;
import finance.warehouse.WarehousePermissionMode;
import finance.warehouse.WarehouseRecord;
import finance.warehouse.WarehouseStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Deterministic domain simulation for warehouse custody, contract escrow, and restart recovery. */
public final class GameplayLongRunSimulationService {
    private static final String COMMODITY_ID = "gameplay_simulation_iron";

    public record Result(int days, int restarts, int completedContracts, boolean moneyConserved,
                         boolean referencesRecovered) {}

    private GameplayLongRunSimulationService() {}

    public static synchronized Result run(int days, long seed) {
        if (days < 1 || days > 1_000) throw new IllegalArgumentException("days");
        CompoundTag original = new EconomySavedData().save(new CompoundTag());
        try {
            EconomySavedData.resetRuntimeState();
            CommodityRegistry.register(new Commodity(COMMODITY_ID, "minecraft:iron_ingot", "Simulation Iron",
                    CommodityCategory.RAW_MATERIALS, 10));
            UUID player = UUID.nameUUIDFromBytes(("gameplay-player-" + seed).getBytes(StandardCharsets.UTF_8));
            UUID warehouseId = UUID.nameUUIDFromBytes(("gameplay-warehouse-" + seed).getBytes(StandardCharsets.UTF_8));
            AccountManager.getAccount(player);
            Account npc = AccountManager.getOrCreateSystemAccount(NpcMarketMaker.NPC_UUID);
            npc.setBalance(1_000_000L);
            WarehouseManager.restore(new WarehouseRecord(warehouseId, "minecraft:overworld", BlockPos.ZERO,
                    player, null, 4_096, WarehouseStatus.ACTIVE, 0, 0, WarehousePermissionMode.OWNER_ONLY));
            CommodityInventoryManager.setCommodity(player, COMMODITY_ID, 1_000);

            BigInteger initialMoney = totalMoney();
            int restarts = 0;
            int completed = 0;
            boolean recovered = true;
            for (int day = 0; day < days; day++) {
                if ((day & 1) == 0) CommodityInventoryManager.addCommodity(player, COMMODITY_ID, 1);
                else if (CommodityInventoryManager.getCommodityAmount(player, COMMODITY_ID) > 100)
                    CommodityInventoryManager.removeCommodity(player, COMMODITY_ID, 1);

                if (day % 7 == 0 && CommodityInventoryManager.getCommodityAmount(player, COMMODITY_ID) >= 10) {
                    FinanceContract contract = ContractManager.createNpcProcurement(COMMODITY_ID, 10, 100,
                            day, day + 3);
                    if (contract != null && contract.accept(player, warehouseId)
                            && CommodityInventoryManager.removeCommodity(player, COMMODITY_ID, 10)
                            && CommodityInventoryManager.addCommodity(NpcMarketMaker.NPC_UUID, COMMODITY_ID, 10)
                            && AccountManager.moveFunds(contract.escrowAccountId(), player, contract.rewardAmount())) {
                        contract.complete();
                        completed++;
                    }
                }
                if (day > 0 && day % 30 == 0) {
                    CompoundTag saved = new EconomySavedData().save(new CompoundTag());
                    EconomySavedData.resetRuntimeState();
                    EconomySavedData.load(saved);
                    recovered &= WarehouseManager.get(warehouseId) != null;
                    restarts++;
                }
                recovered &= WarehouseManager.usedCapacity(player) <= WarehouseManager.totalCapacity(player);
                if (!initialMoney.equals(totalMoney())) return new Result(days, restarts, completed, false, recovered);
            }
            return new Result(days, restarts, completed, true, recovered);
        } finally {
            EconomySavedData.resetRuntimeState();
            EconomySavedData.load(original);
        }
    }

    private static BigInteger totalMoney() {
        BigInteger total = BigInteger.ZERO;
        for (Account account : AccountManager.getAccounts().values()) {
            total = total.add(BigInteger.valueOf(account.getBalance()))
                    .add(BigInteger.valueOf(account.getFrozenBalance()));
        }
        return total;
    }
}
