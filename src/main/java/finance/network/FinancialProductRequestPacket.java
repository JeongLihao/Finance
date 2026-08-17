package finance.network;

import finance.debt.*;
import finance.index.MarketIndexPoint;
import finance.index.MarketIndexService;
import finance.policy.MonetaryPolicyService;
import finance.risk.FinancialRiskService;
import finance.bondmarket.BondMarketManager;
import finance.bondmarket.BondPortfolioManager;
import finance.fixedincome.CentralBankBillManager;
import finance.fixedincome.YieldCurveService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record FinancialProductRequestPacket(long requestId) {
    public static void encode(FinancialProductRequestPacket p, FriendlyByteBuf b) { b.writeLong(p.requestId); }
    public static FinancialProductRequestPacket decode(FriendlyByteBuf b) { return new FinancialProductRequestPacket(b.readLong()); }
    public static void handle(FinancialProductRequestPacket p, Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> {
            ServerPlayer player = supplier.get().getSender();
            if (player == null || p.requestId <= 0 || !MarketDataRequestLimiter.allow(player.getUUID(), player.server.getTickCount(), "financial-products")) return;
            List<FinancialProductResponsePacket.IndexRow> indices = new ArrayList<>();
            MarketIndexService.states().forEach((id, state) -> {
                MarketIndexPoint latest = state.latest(); if (latest != null) indices.add(new FinancialProductResponsePacket.IndexRow(id, latest.value(), MarketIndexService.changePercent(id)));
            });
            long day = finance.cycle.EconomyCycleService.currentMcDay(player.server);
            List<FinancialProductResponsePacket.BondRow> bonds = CorporateBondManager.bonds().values().stream().limit(50)
                    .map(b -> { var v=FixedIncomeValuationService.value(b,player.getUUID(),day); var pos=BondPortfolioManager.position(b.id(),player.getUUID()); return new FinancialProductResponsePacket.BondRow(b.id(), b.code(), b.companyId(), b.status(), b.faceValue(),
                            b.subscribedQuantity(), b.totalQuantity(), b.couponBasisPoints(), b.maturityDay(),v.referencePricePerUnit(),v.marketPricePerUnit(),v.referenceYieldBasisPoints(),v.marketYieldBasisPoints(),v.accruedInterest(),b.holdings().getOrDefault(player.getUUID(),0L),BondPortfolioManager.available(b.id(),player.getUUID()),pos==null?0:pos.frozenQuantity(),BondPortfolioManager.averageCost(b.id(),player.getUUID()),v.unrealizedProfit()); }).toList();
            List<FinancialProductResponsePacket.LoanRow> loans = CompanyLoanManager.loans().values().stream()
                    .filter(l -> { var c = finance.company.CompanyManager.getCompany(l.companyId()); return player.hasPermissions(2) || c != null && player.getUUID().equals(c.getOwnerId()); })
                    .limit(50).map(l -> new FinancialProductResponsePacket.LoanRow(l.id(), l.companyId(), l.status(), l.outstandingPrincipal(),
                            l.accruedInterest(), l.annualRateBasisPoints(), l.maturityDay())).toList();
            List<FinancialProductResponsePacket.BondOrderRow> orders=BondMarketManager.orders().stream().limit(64).map(o->new FinancialProductResponsePacket.BondOrderRow(o.orderId(),o.bondId(),o.side(),o.limitPricePerUnit(),o.remainingQuantity(),o.playerId().equals(player.getUUID()))).toList();
            List<FinancialProductResponsePacket.BillRow> bills=CentralBankBillManager.bills().values().stream().filter(b->b.principalByPlayer().containsKey(player.getUUID())).limit(64).map(b->new FinancialProductResponsePacket.BillRow(b.id(),b.termDays(),b.annualRateBasisPoints(),b.maturityDay(),b.principalByPlayer().getOrDefault(player.getUUID(),0L),CentralBankBillManager.expectedMaturityValue(b,player.getUUID()),b.status())).toList();
            FinancePacketHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new FinancialProductResponsePacket(p.requestId, MonetaryPolicyService.benchmarkRateBasisPoints(),
                            FinancialRiskService.compactSummary(),YieldCurveService.yieldBasisPoints(7),YieldCurveService.yieldBasisPoints(30),YieldCurveService.yieldBasisPoints(90),indices,bonds,loans,orders,bills));
        });
        supplier.get().setPacketHandled(true);
    }
}
