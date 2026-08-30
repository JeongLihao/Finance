package finance.network;

import finance.bank.BankingManager;
import finance.collateral.*;
import finance.company.*;
import finance.cycle.EconomyCycleService;
import finance.futures.*;
import finance.gui.FinanceMenu;
import finance.hedge.*;
import finance.regional.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;

import java.util.*;
import java.util.function.Supplier;

public record RegionalRiskRequestPacket(long requestId){
    public static void encode(RegionalRiskRequestPacket p,FriendlyByteBuf b){b.writeLong(p.requestId);}public static RegionalRiskRequestPacket decode(FriendlyByteBuf b){return new RegionalRiskRequestPacket(b.readLong());}
    public static void handle(RegionalRiskRequestPacket p,Supplier<NetworkEvent.Context>s){var c=s.get();c.enqueueWork(()->send(p,c.getSender()));c.setPacketHandled(true);}
    private static void send(RegionalRiskRequestPacket p,ServerPlayer player){if(player==null||p.requestId<=0||!(player.containerMenu instanceof FinanceMenu menu)||!menu.stillValid(player)||!MarketDataRequestLimiter.allow(player.getUUID(),player.server.getTickCount(),"regional-risk-request"))return;long day=EconomyCycleService.currentMcDay(player.server);Company company=CompanyManager.getCompanyByOwner(player.getUUID());boolean admin=player.hasPermissions(2);
        finance.tutorial.TutorialProgressService.record(player,"regional_risk_view");finance.tutorial.TutorialProgressService.record(player,"risk_summary_view");
        List<RegionalRiskResponsePacket.RegionRow>regions=RegionalCommodityMetricsManager.recentHistories(RegionalRiskResponsePacket.MAX_ROWS,RegionalRiskResponsePacket.MAX_TREND).stream().map(e->{var rows=e.rows();var latest=rows.get(rows.size()-1);List<Integer>trend=rows.stream().map(RegionalCommoditySnapshot::smoothedShortageScore).toList();return new RegionalRiskResponsePacket.RegionRow(e.key().regionId().toString().substring(0,8),e.key().dimensionId(),e.key().commodityId(),latest.day(),latest.localPremiumBps(),latest.smoothedShortageScore(),latest.onTimeDeliveryBps(),latest.pressure(),latest.priceReliable(),trend);}).toList();
        List<RegionalRiskResponsePacket.CollateralRow>collateral=InventoryCollateralManager.visibleTo(company==null?null:company.getCompanyId(),admin,RegionalRiskResponsePacket.MAX_ROWS).stream().map(v->{var loan=finance.debt.CompanyLoanManager.loans().get(v.loanId());int ltv=CollateralValuationService.ltvBps(loan==null?0:loan.outstandingPrincipal(),v.currentDiscountedValue());return new RegionalRiskResponsePacket.CollateralRow(v.id(),v.bankId(),v.loanId(),v.commodityId(),v.pledgedQuantity(),v.currentDiscountedValue(),ltv,v.status(),v.marginCallDay(),v.liquidationRecovered());}).toList();
        List<RegionalRiskResponsePacket.HedgeRow>hedges=CompanyHedgeManager.visibleTo(company==null?null:company.getCompanyId(),admin,RegionalRiskResponsePacket.MAX_ROWS).stream().map(v->{var coverage=CompanyHedgeService.coverage(v,day);var contract=FuturesMarketManager.contract(v.contractId());return new RegionalRiskResponsePacket.HedgeRow(v.id(),v.contractId(),contract==null?"?":contract.code(),v.commodityId(),v.type(),v.targetQuantity(),v.deadlineDay(),coverage.status(),coverage.coverageBps(),coverage.marginRisk(),coverage.realizedPnl(),coverage.personalAccount());}).toList();
        List<RegionalRiskResponsePacket.ContractRow>contracts=FuturesMarketManager.contracts().values().stream().filter(FuturesContract::canTrade).limit(RegionalRiskResponsePacket.MAX_ROWS).map(v->new RegionalRiskResponsePacket.ContractRow(v.id(),v.code(),v.commodityId(),v.maturityDay())).toList();
        List<RegionalRiskResponsePacket.BankRow>banks=BankingManager.banks().values().stream().filter(v->v.acceptsNewBusiness()).limit(RegionalRiskResponsePacket.MAX_ROWS).map(v->new RegionalRiskResponsePacket.BankRow(v.id(),v.code())).toList();
        FinancePacketHandler.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new RegionalRiskResponsePacket(p.requestId,regions,collateral,hedges,contracts,banks));}
}
