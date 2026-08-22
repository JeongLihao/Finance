package finance.gui;

import finance.block.entity.CompanyDeskBlockEntity;
import finance.block.entity.CompanyFactoryControllerBlockEntity;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.contract.ContractManager;
import finance.gameplay.company.*;
import finance.warehouse.WarehouseManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkHooks;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class CompanyGameplayGuiOpener {
    private CompanyGameplayGuiOpener(){}
    public static boolean open(ServerPlayer player,BlockPos pos){return open(player,pos,"");}
    public static boolean open(ServerPlayer player, BlockPos pos, String statusKey) {
        BlockEntity be = player.serverLevel().getBlockEntity(pos);
        UUID companyId = be instanceof CompanyDeskBlockEntity desk ? desk.companyId()
                : be instanceof CompanyFactoryControllerBlockEntity factory ? factory.companyId() : null;
        Company company = CompanyManager.getCompany(companyId);
        CompanyGameplayProfile profile = CompanyGameplayManager.get(companyId);
        if (company == null || profile == null) return false;
        boolean invited = profile.invites().containsKey(player.getUUID());
        if (!invited && !CompanyMembershipService.hasPermission(companyId, player.getUUID(),
                CompanyPermission.VIEW_COMPANY)) return false;
        String role = player.getUUID().equals(company.getOwnerId()) ? "OWNER"
                : profile.members().containsKey(player.getUUID())
                ? profile.members().get(player.getUUID()).role().name() : "INVITED";
        boolean privateView = CompanyMembershipService.hasPermission(companyId, player.getUUID(),
                CompanyPermission.VIEW_PRIVATE_FINANCIALS);
        List<CompanyGameplayMenu.MemberRow> members = invited ? List.of() : profile.members().values().stream()
                .map(member -> new CompanyGameplayMenu.MemberRow(member.playerId(), member.role().name())).toList();
        UUID custody = CompanyInventoryFacade.custodyId(companyId);
        List<CompanyGameplayMenu.WarehouseRow> warehouses = invited ? List.of() : profile.warehouseIds().stream()
                .map(WarehouseManager::get).filter(java.util.Objects::nonNull)
                .map(warehouse -> new CompanyGameplayMenu.WarehouseRow(warehouse.warehouseId(),
                        WarehouseManager.usedCapacity(custody), warehouse.capacityUnits())).toList();
        List<CompanyGameplayMenu.FacilityRow> facilities = invited ? List.of()
                : CompanyFacilityManager.forCompany(companyId).stream()
                .map(facility -> new CompanyGameplayMenu.FacilityRow(facility.facilityId(),
                        facility.productionLevel(), facility.status().name(), facility.lastProcessedDay())).toList();
        List<CompanyGameplayMenu.ContractRow> contracts = invited ? List.of()
                : ContractManager.contracts().values().stream().filter(contract -> companyId.equals(contract.issuerId()))
                .sorted(Comparator.comparingLong(finance.contract.FinanceContract::deadlineDay))
                .limit(CompanyGameplayMenu.MAX_CONTRACTS)
                .map(contract -> new CompanyGameplayMenu.ContractRow(contract.id(), contract.commodityId(),
                        contract.requiredQuantity(), contract.rewardAmount(), contract.status().name())).toList();
        long cash = privateView ? company.getCash() : 0;
        String dimension = player.serverLevel().dimension().location().toString();
        MenuProvider provider = new MenuProvider() {
            public Component getDisplayName() { return Component.translatable("screen.finance.company_gameplay"); }
            public CompanyGameplayMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inventory,
                                                   net.minecraft.world.entity.player.Player ignored) {
                return new CompanyGameplayMenu(id, companyId, company.getName(), cash, company.getAutoSellRatio(),
                        profile.operatingMode(), role, company.isBankruptcyRisk(), dimension, pos,
                        members, warehouses, facilities, contracts, statusKey);
            }
        };
        NetworkHooks.openScreen(player, provider, buffer -> CompanyGameplayMenu.write(buffer, companyId,
                company.getName(), cash, company.getAutoSellRatio(), profile.operatingMode(), role,
                company.isBankruptcyRisk(), dimension, pos, members, warehouses, facilities, contracts, statusKey));
        return true;
    }
}
