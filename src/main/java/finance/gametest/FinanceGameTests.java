package finance.gametest;

import finance.FinanceMod;
import finance.account.AccountManager;
import finance.block.FinanceTerminalBlock;
import finance.block.entity.WarehouseControllerBlockEntity;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.contract.*;
import finance.data.EconomySavedData;
import finance.gameplay.FinanceTerminalType;
import finance.gameplay.WorldTerminalRegistry;
import finance.gameplay.company.*;
import finance.registry.ModBlocks;
import finance.warehouse.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import com.mojang.authlib.GameProfile;

@GameTestHolder(FinanceMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FinanceGameTests {
    private static final String TEMPLATE = "finance_empty";

    private FinanceGameTests() {}

    @GameTest(template = TEMPLATE)
    public static void terminalPlacementAndRemovalMaintainSparseIndex(GameTestHelper helper) {
        EconomySavedData.resetRuntimeState();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, ModBlocks.MARKET_TERMINAL.get());
        FinanceTerminalBlock block = (FinanceTerminalBlock) ModBlocks.MARKET_TERMINAL.get();
        block.setPlacedBy(helper.getLevel(), absolute, helper.getBlockState(relative),
                helper.makeMockPlayer(), ItemStack.EMPTY);
        helper.assertTrue(WorldTerminalRegistry.byType(FinanceTerminalType.MARKET_TERMINAL).stream()
                .anyMatch(record -> record.position().equals(absolute)), "market terminal was not indexed after placement");
        helper.setBlock(relative, Blocks.AIR);
        helper.assertTrue(WorldTerminalRegistry.byType(FinanceTerminalType.MARKET_TERMINAL).stream()
                .noneMatch(record -> record.position().equals(absolute)), "removed market terminal remained indexed");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void warehouseIdentitySurvivesRegistrationAndRemovalPreservesClaim(GameTestHelper helper) {
        EconomySavedData.resetRuntimeState();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, ModBlocks.WAREHOUSE_CONTROLLER.get());
        ServerPlayer player = nearbyPlayer(helper, absolute);
        WarehouseControllerBlockEntity entity = (WarehouseControllerBlockEntity) helper.getLevel().getBlockEntity(absolute);
        helper.assertTrue(entity != null, "warehouse controller did not create its block entity");
        entity.claimIfNeeded(player.getUUID());
        WarehouseRecord record = WarehouseManager.registerOrRecover(player, entity);
        helper.assertTrue(record != null && record.warehouseId().equals(entity.warehouseId()),
                "warehouse registry did not preserve block identity");
        helper.setBlock(relative, Blocks.AIR);
        helper.assertTrue(WarehouseManager.get(record.warehouseId()) != null,
                "warehouse removal deleted the custody claim");
        helper.assertTrue(WarehouseManager.get(record.warehouseId()).status() == WarehouseStatus.DISABLED,
                "warehouse removal did not enter recoverable disabled state");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void contractAcceptanceIsSingleCommit(GameTestHelper helper) {
        EconomySavedData.resetRuntimeState();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, ModBlocks.WAREHOUSE_CONTROLLER.get());
        ServerPlayer player = nearbyPlayer(helper, absolute);
        WarehouseControllerBlockEntity entity = (WarehouseControllerBlockEntity) helper.getLevel().getBlockEntity(absolute);
        helper.assertTrue(entity != null, "warehouse block entity missing for contract test");
        entity.claimIfNeeded(player.getUUID());
        WarehouseRecord warehouse = WarehouseManager.registerOrRecover(player, entity);
        UUID escrow = UUID.randomUUID();
        helper.assertTrue(AccountManager.getOrCreateSystemAccount(escrow).deposit(500), "could not seed contract escrow");
        FinanceContract contract = new FinanceContract(UUID.randomUUID(), ContractType.PROCUREMENT,
                ContractIssuerType.NPC_MARKET, UUID.randomUUID(), "iron", 1, 0, 500, escrow,
                null, 0, 5, null, ContractStatus.OPEN, "");
        helper.assertTrue(ContractManager.restore(contract), "could not register contract fixture");
        ContractSettlementResult first = ContractService.accept(player, contract.id(), warehouse.warehouseId(), 0, "accept-once");
        ContractSettlementResult duplicate = ContractService.accept(player, contract.id(), warehouse.warehouseId(), 0, "accept-once");
        helper.assertTrue(first.success(), "first contract acceptance failed: " + first.messageKey());
        helper.assertFalse(duplicate.success(), "duplicate contract acceptance committed twice");
        helper.assertTrue(AccountManager.getAccounts().get(escrow).getBalance() == 500,
                "acceptance changed escrow before delivery");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void bankruptcyRiskPausesWorldFacilityProduction(GameTestHelper helper) {
        EconomySavedData.resetRuntimeState();
        UUID owner = UUID.randomUUID();
        Company company = new Company(UUID.randomUUID(), "GameTest Company", CompanyType.RAW_MATERIALS, 10_000, owner);
        CompanyManager.registerDirect(company);
        CompanyGameplayProfile profile = CompanyGameplayManager.createForNewCompany(company);
        WarehouseRecord warehouse = new WarehouseRecord(UUID.randomUUID(), helper.getLevel().dimension().location().toString(),
                helper.absolutePos(new BlockPos(1, 1, 1)), owner, company.getCompanyId(), 4_096,
                WarehouseStatus.ACTIVE, 0, 0, WarehousePermissionMode.OWNER_ONLY);
        helper.assertTrue(WarehouseManager.restore(warehouse), "company warehouse fixture failed");
        profile.bindWarehouse(warehouse.warehouseId());
        CompanyFacilityRecord facility = new CompanyFacilityRecord(UUID.randomUUID(), company.getCompanyId(),
                warehouse.dimensionId(), helper.absolutePos(new BlockPos(2, 1, 1)), CompanyFacilityType.FACTORY_CONTROLLER,
                1, CompanyFacilityStatus.ACTIVE, -1, warehouse.warehouseId());
        helper.assertTrue(CompanyFacilityManager.restore(facility), "company facility fixture failed");
        long cashBefore = company.getCash();
        company.setBankruptcyRisk(true, 0);
        helper.assertFalse(CompanyProductionService.processFacility(company, facility, 1),
                "risk-state facility produced goods");
        helper.assertTrue(facility.status() == CompanyFacilityStatus.BANKRUPTCY_HOLD,
                "risk-state facility did not expose its paused status");
        helper.assertTrue(company.getCash() == cashBefore, "paused facility charged maintenance");
        helper.succeed();
    }

    private static ServerPlayer nearbyPlayer(GameTestHelper helper, BlockPos absolute) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "finance-gametest"));
        player.setPos(absolute.getX() + 0.5D, absolute.getY() + 1D, absolute.getZ() + 0.5D);
        return player;
    }
}
