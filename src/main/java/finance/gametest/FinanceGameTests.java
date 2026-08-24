package finance.gametest;

import finance.FinanceMod;
import finance.account.AccountManager;
import finance.block.FinanceTerminalBlock;
import finance.block.entity.WarehouseControllerBlockEntity;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.commodity.CommodityInventoryManager;
import finance.contract.*;
import finance.data.CommodityInventorySavedData;
import finance.data.EconomySavedData;
import finance.gameplay.FinanceTerminalType;
import finance.gameplay.WorldTerminalRegistry;
import finance.gameplay.company.*;
import finance.market.MarketManager;
import finance.market.NpcMarketMaker;
import finance.market.Order;
import finance.market.OrderType;
import finance.registry.ModBlocks;
import finance.registry.ModItems;
import finance.warehouse.*;
import finance.logistics.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    public static void survivalRecipesLoadAndCentralConsoleRemainsUncraftable(GameTestHelper helper) {
        String[] survival = {"portable_ledger", "finance_guide", "market_terminal", "warehouse_controller",
                "company_desk", "company_factory_controller", "bank_counter", "securities_terminal",
                "boardroom_table", "sealed_cargo_crate", "settlement_trade_station"};
        for (String id : survival) helper.assertTrue(helper.getLevel().getRecipeManager()
                .byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(FinanceMod.MOD_ID, id)).isPresent(),
                "survival recipe did not load: " + id);
        helper.assertTrue(helper.getLevel().getRecipeManager()
                .byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        FinanceMod.MOD_ID, "central_bank_console")).isEmpty(),
                "central bank console unexpectedly has a survival recipe");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void terminalPlacementAndRemovalMaintainSparseIndex(GameTestHelper helper) {
        resetRuntimeState();
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
        resetRuntimeState();
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
        resetRuntimeState();
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
    public static void warehousePhysicalMarketAndRemovalLoopConservesAssets(GameTestHelper helper) {
        resetRuntimeState();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, ModBlocks.WAREHOUSE_CONTROLLER.get());
        ServerPlayer seller = nearbyPlayer(helper, absolute);
        WarehouseControllerBlockEntity entity = (WarehouseControllerBlockEntity) helper.getLevel().getBlockEntity(absolute);
        helper.assertTrue(entity != null, "warehouse block entity missing for physical loop");
        entity.claimIfNeeded(seller.getUUID());
        WarehouseRecord warehouse = WarehouseManager.registerOrRecover(seller, entity);
        helper.assertTrue(warehouse != null, "warehouse registration failed for physical loop");
        helper.assertTrue(seller.getInventory().add(new ItemStack(Items.IRON_INGOT, 12)),
                "could not seed seller physical inventory");

        WarehouseActionResult deposit = WarehouseService.deposit(seller, warehouse.warehouseId(),
                "iron", 10, "phase6-deposit");
        helper.assertTrue(deposit.success(), "physical deposit failed: " + deposit.messageKey());
        helper.assertTrue(CommodityInventoryManager.getCommodityAmount(seller.getUUID(), "iron") == 10,
                "deposit did not create exact warehouse custody");
        helper.assertTrue(countItem(seller, Items.IRON_INGOT) == 2,
                "deposit did not remove exact physical item count");

        UUID buyer = UUID.randomUUID();
        AccountManager.getAccount(buyer);
        long sellerCashBefore = AccountManager.getAccount(seller.getUUID()).getBalance();
        helper.assertTrue(MarketManager.placeOrder(new Order(seller.getUUID(), "iron", OrderType.SELL, 10, 4)),
                "seller market order failed");
        helper.assertTrue(MarketManager.placeOrder(new Order(buyer, "iron", OrderType.BUY, 10, 4)),
                "buyer market order failed");
        helper.assertTrue(CommodityInventoryManager.getCommodityAmount(buyer, "iron") == 4,
                "buyer did not receive exact market quantity");
        helper.assertTrue(CommodityInventoryManager.getCommodityAmount(seller.getUUID(), "iron") == 6,
                "seller custody did not decrease by exact market quantity");
        helper.assertTrue(AccountManager.getAccount(seller.getUUID()).getBalance() == sellerCashBefore + 40,
                "seller did not receive exact market payment");

        WarehouseActionResult withdraw = WarehouseService.withdraw(seller, warehouse.warehouseId(),
                "iron", 6, "phase6-withdraw");
        helper.assertTrue(withdraw.success(), "physical withdrawal failed: " + withdraw.messageKey());
        helper.assertTrue(countItem(seller, Items.IRON_INGOT) == 8,
                "withdrawal did not return exact physical item count");
        helper.assertTrue(CommodityInventoryManager.getCommodityAmount(seller.getUUID(), "iron") == 0,
                "withdrawal left unexpected seller custody");

        helper.setBlock(relative, Blocks.AIR);
        WarehouseRecord disabled = WarehouseManager.get(warehouse.warehouseId());
        helper.assertTrue(disabled != null && disabled.status() == WarehouseStatus.DISABLED,
                "removed warehouse did not preserve a disabled recoverable record");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void warehouseRejectsUnauthorizedWithdrawalWithoutMovingAssets(GameTestHelper helper) {
        resetRuntimeState();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, ModBlocks.WAREHOUSE_CONTROLLER.get());
        ServerPlayer owner = nearbyPlayer(helper, absolute);
        WarehouseControllerBlockEntity entity = (WarehouseControllerBlockEntity) helper.getLevel().getBlockEntity(absolute);
        helper.assertTrue(entity != null, "warehouse block entity missing for permission test");
        entity.claimIfNeeded(owner.getUUID());
        WarehouseRecord warehouse = WarehouseManager.registerOrRecover(owner, entity);
        helper.assertTrue(warehouse != null, "warehouse registration failed for permission test");
        helper.assertTrue(owner.getInventory().add(new ItemStack(Items.IRON_INGOT, 8)),
                "could not seed owner inventory");
        WarehouseActionResult deposit = WarehouseService.deposit(owner, warehouse.warehouseId(),
                "iron", 8, "phase6-permission-deposit");
        helper.assertTrue(deposit.success(), "owner deposit failed: " + deposit.messageKey());

        ServerPlayer intruder = nearbyPlayer(helper, absolute);
        int custodyBefore = CommodityInventoryManager.getCommodityAmount(owner.getUUID(), "iron");
        int intruderItemsBefore = countItem(intruder, Items.IRON_INGOT);
        WarehouseActionResult denied = WarehouseService.withdraw(intruder, warehouse.warehouseId(),
                "iron", 3, "phase6-permission-withdraw");
        helper.assertFalse(denied.success(), "unauthorized player withdrew owner custody");
        helper.assertTrue(CommodityInventoryManager.getCommodityAmount(owner.getUUID(), "iron") == custodyBefore,
                "denied withdrawal changed owner custody");
        helper.assertTrue(countItem(intruder, Items.IRON_INGOT) == intruderItemsBefore,
                "denied withdrawal inserted physical items");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void contractPhysicalDeliveryPaysEscrowExactlyOnce(GameTestHelper helper) {
        resetRuntimeState();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, ModBlocks.WAREHOUSE_CONTROLLER.get());
        ServerPlayer player = nearbyPlayer(helper, absolute);
        WarehouseControllerBlockEntity entity = (WarehouseControllerBlockEntity) helper.getLevel().getBlockEntity(absolute);
        helper.assertTrue(entity != null, "warehouse block entity missing for contract delivery");
        entity.claimIfNeeded(player.getUUID());
        WarehouseRecord warehouse = WarehouseManager.registerOrRecover(player, entity);
        helper.assertTrue(warehouse != null, "warehouse registration failed for contract delivery");
        helper.assertTrue(player.getInventory().add(new ItemStack(Items.IRON_INGOT, 4)),
                "could not seed contract delivery items");

        UUID escrow = UUID.randomUUID();
        helper.assertTrue(AccountManager.getOrCreateSystemAccount(escrow).deposit(500),
                "could not seed contract escrow");
        FinanceContract contract = new FinanceContract(UUID.randomUUID(), ContractType.PROCUREMENT,
                ContractIssuerType.NPC_MARKET, UUID.randomUUID(), "iron", 5, 0, 500, escrow,
                null, 0, 5, null, ContractStatus.OPEN, "");
        helper.assertTrue(ContractManager.restore(contract), "could not register delivery contract");
        long playerCashBefore = AccountManager.getAccount(player.getUUID()).getBalance();
        int npcIronBefore = CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, "iron");
        ContractSettlementResult accepted = ContractService.accept(player, contract.id(), warehouse.warehouseId(),
                1, "phase6-accept");
        helper.assertTrue(accepted.success(), "contract acceptance failed: " + accepted.messageKey());
        ContractSettlementResult insufficient = ContractService.complete(player, contract.id(), warehouse.warehouseId(),
                1, "phase6-complete-insufficient");
        helper.assertFalse(insufficient.success(), "insufficient delivery unexpectedly completed contract");
        helper.assertTrue(countItem(player, Items.IRON_INGOT) == 4,
                "failed delivery removed physical items");
        helper.assertTrue(AccountManager.getAccounts().get(escrow).getBalance() == 500,
                "failed delivery changed escrow");
        helper.assertTrue(AccountManager.getAccount(player.getUUID()).getBalance() == playerCashBefore,
                "failed delivery changed player balance");
        helper.assertTrue(player.getInventory().add(new ItemStack(Items.IRON_INGOT, 1)),
                "could not add final contract delivery item");
        ContractSettlementResult completed = ContractService.complete(player, contract.id(), warehouse.warehouseId(),
                1, "phase6-complete");
        helper.assertTrue(completed.success(), "contract completion failed: " + completed.messageKey());
        helper.assertTrue(contract.status() == ContractStatus.COMPLETED && contract.deliveredQuantity() == 5,
                "contract did not record exact completed delivery");
        helper.assertTrue(countItem(player, Items.IRON_INGOT) == 0, "delivered physical items remained in inventory");
        helper.assertTrue(AccountManager.getAccount(player.getUUID()).getBalance() == playerCashBefore + 500,
                "player did not receive exact escrow reward");
        helper.assertTrue(AccountManager.getAccounts().get(escrow).getBalance() == 0,
                "escrow was not emptied after payment");
        helper.assertTrue(CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, "iron")
                        == npcIronBefore + 5,
                "NPC destination did not receive delivered commodity");
        ContractSettlementResult duplicate = ContractService.complete(player, contract.id(), warehouse.warehouseId(),
                1, "phase6-complete-again");
        helper.assertFalse(duplicate.success(), "completed contract paid twice");
        helper.assertTrue(AccountManager.getAccount(player.getUUID()).getBalance() == playerCashBefore + 500,
                "duplicate completion changed player balance");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void companyFactoryBlockProducesOnceOnNaturalDay(GameTestHelper helper) {
        resetRuntimeState();
        BlockPos warehouseRelative = new BlockPos(1, 1, 1);
        BlockPos warehouseAbsolute = helper.absolutePos(warehouseRelative);
        helper.setBlock(warehouseRelative, ModBlocks.WAREHOUSE_CONTROLLER.get());
        ServerPlayer owner = nearbyPlayer(helper, warehouseAbsolute);
        Company company = new Company(UUID.randomUUID(), "Phase 6 Factory", CompanyType.RAW_MATERIALS,
                100_000, owner.getUUID());
        CompanyManager.registerDirect(company);
        CompanyGameplayProfile profile = CompanyGameplayManager.createForNewCompany(company);
        WarehouseControllerBlockEntity warehouseEntity = (WarehouseControllerBlockEntity)
                helper.getLevel().getBlockEntity(warehouseAbsolute);
        helper.assertTrue(warehouseEntity != null, "company warehouse block entity missing");
        warehouseEntity.claimIfNeeded(owner.getUUID());
        WarehouseRecord warehouse = WarehouseManager.registerOrRecover(owner, warehouseEntity);
        helper.assertTrue(warehouse != null, "company warehouse registration failed");
        CompanyGameplayActionResult binding = CompanyWarehouseBindingService.bind(owner.getUUID(),
                company.getCompanyId(), warehouse.warehouseId(), "phase6-bind");
        helper.assertTrue(binding.success(), "company warehouse binding failed: " + binding.messageKey());

        BlockPos factoryRelative = new BlockPos(3, 1, 1);
        BlockPos factoryAbsolute = helper.absolutePos(factoryRelative);
        helper.setBlock(factoryRelative, ModBlocks.COMPANY_FACTORY_CONTROLLER.get());
        finance.block.entity.CompanyFactoryControllerBlockEntity factoryEntity =
                (finance.block.entity.CompanyFactoryControllerBlockEntity) helper.getLevel().getBlockEntity(factoryAbsolute);
        helper.assertTrue(factoryEntity != null && factoryEntity.registerOrValidate(owner),
                "physical factory controller did not register for the owner company");
        CompanyFacilityRecord facility = CompanyFacilityManager.get(factoryEntity.facilityId());
        helper.assertTrue(facility != null, "factory registration did not create a facility record");

        long day = Math.max(1L, helper.getLevel().getGameTime() / 24_000L + 1L);
        long cashBefore = company.getCash();
        CompanyProductionService.DayResult first = CompanyProductionService.processCompanyDay(company, day);
        int produced = CommodityInventoryManager.getCommodityAmount(
                CompanyInventoryFacade.custodyId(company.getCompanyId()), "iron");
        helper.assertTrue(first.producedFacilities() == 1 && produced == 100,
                "physical facility did not produce its exact daily output");
        helper.assertTrue(company.getCash() < cashBefore, "facility production did not charge maintenance");
        CompanyProductionService.DayResult duplicate = CompanyProductionService.processCompanyDay(company, day);
        helper.assertTrue(duplicate.producedFacilities() == 0,
                "same natural day processed the facility twice");
        helper.assertTrue(CommodityInventoryManager.getCommodityAmount(
                CompanyInventoryFacade.custodyId(company.getCompanyId()), "iron") == produced,
                "same-day retry duplicated company output");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void warehousePhysicalUpgradeIsAtomicAndIdempotent(GameTestHelper helper) {
        resetRuntimeState();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, ModBlocks.WAREHOUSE_CONTROLLER.get());
        ServerPlayer player = nearbyPlayer(helper, absolute);
        WarehouseControllerBlockEntity entity = (WarehouseControllerBlockEntity) helper.getLevel().getBlockEntity(absolute);
        helper.assertTrue(entity != null, "warehouse upgrade block entity missing");
        entity.claimIfNeeded(player.getUUID());
        WarehouseRecord warehouse = WarehouseManager.registerOrRecover(player, entity);
        helper.assertTrue(warehouse != null && warehouse.tier() == WarehouseTier.BASIC,
                "new warehouse did not start at tier one");
        player.getInventory().add(new ItemStack(Items.IRON_INGOT, 8));
        player.getInventory().add(new ItemStack(Items.COPPER_INGOT, 8));
        player.getInventory().add(new ItemStack(Items.REDSTONE, 4));
        long cashBefore = AccountManager.getBalance(player.getUUID());
        WarehouseActionResult upgraded = WarehouseUpgradeService.upgrade(player, warehouse.warehouseId(), "tier-two");
        helper.assertTrue(upgraded.success() && warehouse.tier() == WarehouseTier.REINFORCED,
                "physical warehouse upgrade failed: " + upgraded.messageKey());
        helper.assertTrue(AccountManager.getBalance(player.getUUID()) == cashBefore - 250,
                "warehouse upgrade did not charge exact server cash cost");
        helper.assertTrue(countItem(player, Items.IRON_INGOT) == 0
                        && countItem(player, Items.COPPER_INGOT) == 0 && countItem(player, Items.REDSTONE) == 0,
                "warehouse upgrade did not consume exact physical materials");
        WarehouseActionResult duplicate = WarehouseUpgradeService.upgrade(player, warehouse.warehouseId(), "tier-two");
        helper.assertFalse(duplicate.success(), "duplicate warehouse upgrade committed twice");
        helper.assertTrue(warehouse.tier() == WarehouseTier.REINFORCED
                        && AccountManager.getBalance(player.getUUID()) == cashBefore - 250,
                "duplicate warehouse upgrade changed level or cash");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void warehouseReplacementGetsFreshIdentityAndPlainDrop(GameTestHelper helper) {
        resetRuntimeState();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, ModBlocks.WAREHOUSE_CONTROLLER.get());
        ServerPlayer player = nearbyPlayer(helper, absolute);
        WarehouseControllerBlockEntity firstEntity = (WarehouseControllerBlockEntity) helper.getLevel().getBlockEntity(absolute);
        firstEntity.claimIfNeeded(player.getUUID());
        WarehouseRecord first = WarehouseManager.registerOrRecover(player, firstEntity);
        var drops = net.minecraft.world.level.block.Block.getDrops(helper.getBlockState(relative), helper.getLevel(),
                absolute, firstEntity, player, ItemStack.EMPTY);
        helper.assertTrue(drops.size() == 1 && drops.get(0).is(ModBlocks.WAREHOUSE_CONTROLLER.get().asItem())
                        && !drops.get(0).hasTag(), "warehouse drop carried authoritative identity NBT");
        helper.setBlock(relative, Blocks.AIR);
        helper.setBlock(relative, ModBlocks.WAREHOUSE_CONTROLLER.get());
        WarehouseControllerBlockEntity secondEntity = (WarehouseControllerBlockEntity) helper.getLevel().getBlockEntity(absolute);
        secondEntity.claimIfNeeded(player.getUUID());
        WarehouseRecord second = WarehouseManager.registerOrRecover(player, secondEntity);
        helper.assertTrue(!first.warehouseId().equals(second.warehouseId()),
                "re-placed warehouse copied the disabled facility identity");
        helper.assertTrue(WarehouseManager.get(first.warehouseId()).status() == WarehouseStatus.DISABLED,
                "old custody claim was not retained as disabled");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void factoryUpgradeRequiresNearbyBlockAndPhysicalMaterials(GameTestHelper helper) {
        resetRuntimeState();
        BlockPos warehousePos = new BlockPos(1, 1, 1);
        BlockPos warehouseAbsolute = helper.absolutePos(warehousePos);
        helper.setBlock(warehousePos, ModBlocks.WAREHOUSE_CONTROLLER.get());
        ServerPlayer owner = nearbyPlayer(helper, warehouseAbsolute);
        Company company = new Company(UUID.randomUUID(), "Tier Factory", CompanyType.RAW_MATERIALS,
                20_000, owner.getUUID());
        CompanyManager.registerDirect(company);
        CompanyGameplayManager.createForNewCompany(company);
        WarehouseControllerBlockEntity warehouseEntity = (WarehouseControllerBlockEntity)
                helper.getLevel().getBlockEntity(warehouseAbsolute);
        warehouseEntity.claimIfNeeded(owner.getUUID());
        WarehouseRecord warehouse = WarehouseManager.registerOrRecover(owner, warehouseEntity);
        helper.assertTrue(CompanyWarehouseBindingService.bind(owner.getUUID(), company.getCompanyId(),
                warehouse.warehouseId(), "tier-bind").success(), "could not bind tier-test warehouse");
        BlockPos factoryPos = new BlockPos(3, 1, 1);
        BlockPos factoryAbsolute = helper.absolutePos(factoryPos);
        helper.setBlock(factoryPos, ModBlocks.COMPANY_FACTORY_CONTROLLER.get());
        finance.block.entity.CompanyFactoryControllerBlockEntity factory =
                (finance.block.entity.CompanyFactoryControllerBlockEntity) helper.getLevel().getBlockEntity(factoryAbsolute);
        helper.assertTrue(factory != null && factory.registerOrValidate(owner), "could not register physical factory");
        CompanyFacilityRecord facility = CompanyFacilityManager.get(factory.facilityId());
        owner.getInventory().add(new ItemStack(Items.IRON_INGOT, 12));
        owner.getInventory().add(new ItemStack(Items.COPPER_INGOT, 8));
        owner.getInventory().add(new ItemStack(Items.REDSTONE, 8));
        owner.setPos(factoryAbsolute.getX() + 0.5D, factoryAbsolute.getY() + 1D, factoryAbsolute.getZ() + 0.5D);
        long cashBefore = company.getCash();
        CompanyGameplayActionResult result = CompanyUpgradeService.upgrade(owner, facility.facilityId(), "factory-two");
        helper.assertTrue(result.success() && facility.productionLevel() == 2,
                "physical factory upgrade failed: " + result.messageKey());
        helper.assertTrue(company.getCash() == cashBefore - 2_000,
                "factory upgrade did not charge exact company cash");
        helper.assertTrue(countItem(owner, Items.IRON_INGOT) == 0 && countItem(owner, Items.COPPER_INGOT) == 0
                        && countItem(owner, Items.REDSTONE) == 0,
                "factory upgrade did not consume physical player materials");
        helper.assertFalse(CompanyUpgradeService.upgrade(owner, facility.facilityId(), "factory-two").success(),
                "duplicate factory upgrade committed twice");
        helper.assertTrue(company.getCash() == cashBefore - 2_000 && facility.productionLevel() == 2,
                "duplicate factory upgrade changed cash or tier");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void sealedCargoMovesExactCustodyAndRejectsReplayAndForgedLabels(GameTestHelper helper) {
        resetRuntimeState();
        BlockPos sourceRelative = new BlockPos(1, 1, 1);
        BlockPos destinationRelative = new BlockPos(5, 1, 1);
        BlockPos sourceAbsolute = helper.absolutePos(sourceRelative);
        BlockPos destinationAbsolute = helper.absolutePos(destinationRelative);
        helper.setBlock(sourceRelative, ModBlocks.WAREHOUSE_CONTROLLER.get());
        helper.setBlock(destinationRelative, ModBlocks.WAREHOUSE_CONTROLLER.get());
        ServerPlayer player = nearbyPlayer(helper, sourceAbsolute);
        WarehouseControllerBlockEntity sourceEntity = (WarehouseControllerBlockEntity)
                helper.getLevel().getBlockEntity(sourceAbsolute);
        WarehouseControllerBlockEntity destinationEntity = (WarehouseControllerBlockEntity)
                helper.getLevel().getBlockEntity(destinationAbsolute);
        sourceEntity.claimIfNeeded(player.getUUID());
        destinationEntity.claimIfNeeded(player.getUUID());
        WarehouseRecord source = WarehouseManager.registerOrRecover(player, sourceEntity);
        player.setPos(destinationAbsolute.getX() + .5D, destinationAbsolute.getY() + 1D, destinationAbsolute.getZ() + .5D);
        WarehouseRecord destination = WarehouseManager.registerOrRecover(player, destinationEntity);
        helper.assertTrue(CommodityInventoryManager.addCommodity(player.getUUID(), "iron", 20),
                "could not seed source custody");
        player.setPos(sourceAbsolute.getX() + .5D, sourceAbsolute.getY() + 1D, sourceAbsolute.getZ() + .5D);
        ShipmentActionResult loaded = ShipmentService.load(player, source.warehouseId(), destination.warehouseId(),
                "iron", 12, null, "load-once");
        ShipmentActionResult replay = ShipmentService.load(player, source.warehouseId(), destination.warehouseId(),
                "iron", 12, null, "load-once");
        helper.assertTrue(loaded.success() && !replay.success(), "load was not idempotent");
        helper.assertTrue(CommodityInventoryManager.getCommodityAmount(player.getUUID(), "iron") == 8
                        && TransportCustodyManager.get(loaded.shipment().id()).quantity() == 12,
                "load did not move exact units into transport custody");

        ItemStack crate = new ItemStack(ModItems.SEALED_CARGO_CRATE.get());
        finance.item.SealedCargoCrateItem.seal(crate, loaded.shipment());
        crate.getOrCreateTag().putString("CargoLabel", "wheat");
        crate.getOrCreateTag().putInt("CargoQuantity", 999_999);
        player.setPos(destinationAbsolute.getX() + .5D, destinationAbsolute.getY() + 1D, destinationAbsolute.getZ() + .5D);
        ShipmentActionResult delivered = ShipmentService.unload(player, loaded.shipment().id(),
                loaded.shipment().tokenId(), destination.warehouseId(), "unload-once");
        ShipmentActionResult duplicate = ShipmentService.unload(player, loaded.shipment().id(),
                loaded.shipment().tokenId(), destination.warehouseId(), "unload-twice");
        helper.assertTrue(delivered.success() && !duplicate.success(), "unload replay was accepted");
        helper.assertTrue(CommodityInventoryManager.getCommodityAmount(player.getUUID(), "iron") == 20
                        && CommodityInventoryManager.getCommodityAmount(player.getUUID(), "wheat") == 0
                        && TransportCustodyManager.get(loaded.shipment().id()) == null,
                "forged display NBT changed authoritative cargo or delivered twice");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void destroyedCargoBecomesRecoverableWithRotatedToken(GameTestHelper helper) {
        resetRuntimeState();
        BlockPos sourceRelative = new BlockPos(1, 1, 1);
        BlockPos destinationRelative = new BlockPos(5, 1, 1);
        BlockPos sourceAbsolute = helper.absolutePos(sourceRelative);
        BlockPos destinationAbsolute = helper.absolutePos(destinationRelative);
        helper.setBlock(sourceRelative, ModBlocks.WAREHOUSE_CONTROLLER.get());
        helper.setBlock(destinationRelative, ModBlocks.WAREHOUSE_CONTROLLER.get());
        ServerPlayer player = nearbyPlayer(helper, sourceAbsolute);
        WarehouseControllerBlockEntity sourceEntity = (WarehouseControllerBlockEntity)
                helper.getLevel().getBlockEntity(sourceAbsolute);
        WarehouseControllerBlockEntity destinationEntity = (WarehouseControllerBlockEntity)
                helper.getLevel().getBlockEntity(destinationAbsolute);
        sourceEntity.claimIfNeeded(player.getUUID());
        destinationEntity.claimIfNeeded(player.getUUID());
        WarehouseRecord source = WarehouseManager.registerOrRecover(player, sourceEntity);
        player.setPos(destinationAbsolute.getX() + .5D, destinationAbsolute.getY() + 1D, destinationAbsolute.getZ() + .5D);
        WarehouseRecord destination = WarehouseManager.registerOrRecover(player, destinationEntity);
        CommodityInventoryManager.addCommodity(player.getUUID(), "iron", 5);
        player.setPos(sourceAbsolute.getX() + .5D, sourceAbsolute.getY() + 1D, sourceAbsolute.getZ() + .5D);
        ShipmentActionResult loaded = ShipmentService.load(player, source.warehouseId(), destination.warehouseId(),
                "iron", 5, null, "loss-load");
        UUID oldToken = loaded.shipment().tokenId();
        helper.assertTrue(ShipmentService.markLost(loaded.shipment().id(), oldToken, "gametest destruction"),
                "destroyed crate did not enter loss-pending state");
        helper.assertTrue(!ShipmentService.markLost(loaded.shipment().id(), oldToken, "duplicate destruction"),
                "duplicate destruction changed shipment twice");
        ShipmentActionResult recovered = ShipmentService.recover(player, source.warehouseId(), "recover-once");
        helper.assertTrue(recovered.success() && !oldToken.equals(recovered.shipment().tokenId())
                        && TransportCustodyManager.get(recovered.shipment().id()).quantity() == 5,
                "recovery did not preserve cargo and rotate the bearer token");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void fullDestinationRejectsWholeUnloadAndKeepsTransportCustody(GameTestHelper helper) {
        resetRuntimeState();
        BlockPos sourceRelative = new BlockPos(1, 1, 1), destinationRelative = new BlockPos(5, 1, 1);
        BlockPos sourceAbsolute = helper.absolutePos(sourceRelative), destinationAbsolute = helper.absolutePos(destinationRelative);
        helper.setBlock(sourceRelative, ModBlocks.WAREHOUSE_CONTROLLER.get());
        helper.setBlock(destinationRelative, ModBlocks.WAREHOUSE_CONTROLLER.get());
        ServerPlayer player = nearbyPlayer(helper, sourceAbsolute);
        WarehouseControllerBlockEntity sourceEntity = (WarehouseControllerBlockEntity) helper.getLevel().getBlockEntity(sourceAbsolute);
        WarehouseControllerBlockEntity destinationEntity = (WarehouseControllerBlockEntity) helper.getLevel().getBlockEntity(destinationAbsolute);
        sourceEntity.claimIfNeeded(player.getUUID()); destinationEntity.claimIfNeeded(player.getUUID());
        WarehouseRecord source = WarehouseManager.registerOrRecover(player, sourceEntity);
        player.setPos(destinationAbsolute.getX() + .5D, destinationAbsolute.getY() + 1D, destinationAbsolute.getZ() + .5D);
        WarehouseRecord destination = WarehouseManager.registerOrRecover(player, destinationEntity);
        int capacity = (int) WarehouseManager.totalCapacity(player.getUUID());
        CommodityInventoryManager.addCommodity(player.getUUID(), "iron", capacity);
        player.setPos(sourceAbsolute.getX() + .5D, sourceAbsolute.getY() + 1D, sourceAbsolute.getZ() + .5D);
        ShipmentActionResult loaded = ShipmentService.load(player, source.warehouseId(), destination.warehouseId(),
                "iron", 5, null, "full-load");
        CommodityInventoryManager.addCommodity(player.getUUID(), "iron", 5);
        player.setPos(destinationAbsolute.getX() + .5D, destinationAbsolute.getY() + 1D, destinationAbsolute.getZ() + .5D);
        ShipmentActionResult rejected = ShipmentService.unload(player, loaded.shipment().id(),
                loaded.shipment().tokenId(), destination.warehouseId(), "full-unload");
        helper.assertFalse(rejected.success(), "full destination accepted partial or overflowing unload");
        helper.assertTrue(CommodityInventoryManager.getCommodityAmount(player.getUUID(), "iron") == capacity
                        && TransportCustodyManager.get(loaded.shipment().id()).quantity() == 5
                        && loaded.shipment().status() == ShipmentStatus.IN_TRANSIT,
                "failed unload changed warehouse or transport custody");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void shipmentDeliverySettlesExistingContractEscrowExactlyOnce(GameTestHelper helper) {
        resetRuntimeState();
        BlockPos sourceRelative = new BlockPos(1, 1, 1), destinationRelative = new BlockPos(5, 1, 1);
        BlockPos sourceAbsolute = helper.absolutePos(sourceRelative), destinationAbsolute = helper.absolutePos(destinationRelative);
        helper.setBlock(sourceRelative, ModBlocks.WAREHOUSE_CONTROLLER.get());
        helper.setBlock(destinationRelative, ModBlocks.WAREHOUSE_CONTROLLER.get());
        ServerPlayer player = nearbyPlayer(helper, sourceAbsolute);
        AccountManager.getAccount(player.getUUID());
        WarehouseControllerBlockEntity sourceEntity = (WarehouseControllerBlockEntity) helper.getLevel().getBlockEntity(sourceAbsolute);
        WarehouseControllerBlockEntity destinationEntity = (WarehouseControllerBlockEntity) helper.getLevel().getBlockEntity(destinationAbsolute);
        sourceEntity.claimIfNeeded(player.getUUID()); destinationEntity.claimIfNeeded(player.getUUID());
        WarehouseRecord source = WarehouseManager.registerOrRecover(player, sourceEntity);
        player.setPos(destinationAbsolute.getX() + .5D, destinationAbsolute.getY() + 1D, destinationAbsolute.getZ() + .5D);
        WarehouseRecord destination = WarehouseManager.registerOrRecover(player, destinationEntity);
        UUID escrow = UUID.randomUUID();
        AccountManager.getOrCreateSystemAccount(escrow).deposit(500);
        FinanceContract contract = new FinanceContract(UUID.randomUUID(), ContractType.PROCUREMENT,
                ContractIssuerType.NPC_MARKET, NpcMarketMaker.NPC_UUID, "iron", 5, 0, 500, escrow,
                null, 0, 10, null, ContractStatus.OPEN, "");
        ContractManager.restore(contract);
        ContractService.accept(player, contract.id(), destination.warehouseId(), 0, "transport-accept");
        CommodityInventoryManager.addCommodity(player.getUUID(), "iron", 5);
        long cashBefore = AccountManager.getBalance(player.getUUID());
        int npcBefore = CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, "iron");
        player.setPos(sourceAbsolute.getX() + .5D, sourceAbsolute.getY() + 1D, sourceAbsolute.getZ() + .5D);
        ShipmentActionResult loaded = ShipmentService.load(player, source.warehouseId(), destination.warehouseId(),
                "iron", 5, null, "contract-load");
        helper.assertTrue(contract.id().equals(loaded.shipment().contractId()), "accepted contract was not linked to shipment");
        player.setPos(destinationAbsolute.getX() + .5D, destinationAbsolute.getY() + 1D, destinationAbsolute.getZ() + .5D);
        ShipmentActionResult delivered = ShipmentService.unload(player, loaded.shipment().id(),
                loaded.shipment().tokenId(), destination.warehouseId(), "contract-unload");
        helper.assertTrue(delivered.success() && contract.status() == ContractStatus.COMPLETED,
                "shipment did not complete linked contract");
        helper.assertTrue(AccountManager.getBalance(player.getUUID()) == cashBefore + 500
                        && AccountManager.getAccounts().get(escrow).getBalance() == 0
                        && CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, "iron") == npcBefore + 5
                        && TransportCustodyManager.get(loaded.shipment().id()) == null,
                "shipment contract did not settle cargo and escrow exactly once");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void settlementStationAcceptsAndAtomicallyCompletesPhysicalDemand(GameTestHelper helper) {
        resetRuntimeState();
        BlockPos relative = new BlockPos(1, 1, 1), absolute = helper.absolutePos(relative);
        helper.setBlock(relative, ModBlocks.SETTLEMENT_TRADE_STATION.get());
        ServerPlayer player = nearbyPlayer(helper, absolute);
        var station = (finance.block.entity.SettlementTradeStationBlockEntity) helper.getLevel().getBlockEntity(absolute);
        var settlement = finance.settlement.SettlementService.register(player, station);
        UUID escrow = UUID.randomUUID();
        AccountManager.getOrCreateSystemAccount(escrow).deposit(240);
        var demand = new finance.settlement.LocalDemand(UUID.randomUUID(), settlement.id(), "wheat", "food", 12,
                240, escrow, 0, 5, finance.settlement.DemandStatus.OPEN, null);
        helper.assertTrue(finance.settlement.SettlementManager.addDemand(demand), "local demand fixture failed");
        player.getInventory().add(new ItemStack(Items.WHEAT, 12));
        long cashBefore = AccountManager.getBalance(player.getUUID());
        helper.assertTrue(finance.settlement.SettlementService.accept(player, settlement.id(), demand.id(), "accept").success(),
                "nearby public demand was not accepted");
        helper.assertTrue(finance.settlement.SettlementService.deliver(player, settlement.id(), demand.id(), "deliver").success(),
                "physical settlement delivery failed");
        helper.assertTrue(demand.status() == finance.settlement.DemandStatus.COMPLETED
                        && countItem(player, Items.WHEAT) == 0
                        && CommodityInventoryManager.getCommodityAmount(settlement.id(), "wheat") == 12
                        && AccountManager.getBalance(player.getUUID()) == cashBefore + 240
                        && AccountManager.getAccounts().get(escrow).getBalance() == 0,
                "settlement goods or escrow were not conserved exactly once");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void destroyedTradeStationRecoversIdentityAndKeepsActiveEscrow(GameTestHelper helper) {
        resetRuntimeState();
        BlockPos relative = new BlockPos(1, 1, 1), absolute = helper.absolutePos(relative);
        helper.setBlock(relative, ModBlocks.SETTLEMENT_TRADE_STATION.get());
        ServerPlayer player = nearbyPlayer(helper, absolute);
        var first = (finance.block.entity.SettlementTradeStationBlockEntity) helper.getLevel().getBlockEntity(absolute);
        var settlement = finance.settlement.SettlementService.register(player, first);
        UUID id = settlement.id(), escrow = UUID.randomUUID();
        AccountManager.getOrCreateSystemAccount(escrow).deposit(120);
        var demand = new finance.settlement.LocalDemand(UUID.randomUUID(), id, "stone", "rebuild", 8, 120,
                escrow, 0, 5, finance.settlement.DemandStatus.OPEN, null);
        finance.settlement.SettlementManager.addDemand(demand);
        helper.setBlock(relative, Blocks.AIR);
        helper.assertTrue(finance.settlement.SettlementManager.get(id).status() == finance.settlement.SettlementStatus.DISABLED
                        && AccountManager.getAccounts().get(escrow).getBalance() == 120,
                "station destruction deleted identity or escrow");
        helper.setBlock(relative, ModBlocks.SETTLEMENT_TRADE_STATION.get());
        var replacement = (finance.block.entity.SettlementTradeStationBlockEntity) helper.getLevel().getBlockEntity(absolute);
        var recovered = finance.settlement.SettlementService.register(player, replacement);
        helper.assertTrue(recovered.id().equals(id) && replacement.settlementId().equals(id)
                        && finance.settlement.SettlementManager.demand(demand.id()) != null,
                "rebuilt station did not recover stable settlement identity and demand");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void explorationSurveyRejectsForgedArrivalAndPaysEscrowExactlyOnce(GameTestHelper helper) {
        resetRuntimeState();
        BlockPos board = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos target = helper.absolutePos(new BlockPos(60, 1, 1));
        ServerPlayer player = nearbyPlayer(helper, board);
        AccountManager.getAccount(player.getUUID());
        AccountManager.getOrCreateSystemAccount(finance.market.NpcMarketMaker.NPC_UUID).deposit(10_000);
        var settlement = new finance.settlement.SettlementRecord(UUID.randomUUID(),
                helper.getLevel().dimension().location().toString(), target, "Survey Target",
                finance.settlement.SettlementStatus.ACTIVE, -1, -1, "");
        helper.assertTrue(finance.settlement.SettlementManager.restoreSettlement(settlement),
                "could not create exploration target fixture");
        long cashBefore = AccountManager.getBalance(player.getUUID());
        var request = finance.exploration.ExplorationService.request(player, board);
        helper.assertTrue(request.success() && request.assignment() != null,
                "survey request did not reserve real NPC budget");
        helper.assertFalse(finance.exploration.ExplorationService.verifyAt(player, board,
                finance.exploration.ExplorationTargetType.SETTLEMENT).success(),
                "forged arrival position completed exploration");
        player.setPos(target.getX() + .5D, target.getY() + 1D, target.getZ() + .5D);
        var completed = finance.exploration.ExplorationService.verifyAt(player, target,
                finance.exploration.ExplorationTargetType.SETTLEMENT);
        helper.assertTrue(completed.success()
                        && AccountManager.getBalance(player.getUUID()) == cashBefore + request.assignment().reward()
                        && AccountManager.getAccounts().get(request.assignment().escrowId()).getBalance() == 0,
                "valid world interaction did not settle exact escrow");
        helper.assertFalse(finance.exploration.ExplorationService.verifyAt(player, target,
                finance.exploration.ExplorationTargetType.SETTLEMENT).success(),
                "completed survey paid twice");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void bankruptcyRiskPausesWorldFacilityProduction(GameTestHelper helper) {
        resetRuntimeState();
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

    private static int countItem(ServerPlayer player, net.minecraft.world.item.Item item) {
        return player.getInventory().items.stream()
                .filter(stack -> stack.is(item))
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    private static void resetRuntimeState() {
        EconomySavedData.resetRuntimeState();
        CommodityInventorySavedData.resetRuntimeState();
    }
}
