package finance.network;

import finance.company.Company;
import finance.company.CompanyManager;
import finance.cycle.EconomyCycleService;
import finance.debt.CompanyCreditService;
import finance.debt.CompanyLoanManager;
import finance.debt.CorporateBondManager;
import finance.policy.MonetaryPolicyService;
import finance.bondmarket.BondMarketManager;
import finance.fixedincome.CentralBankBillManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record FinancialProductActionPacket(Action action, UUID targetId, long amount, long quantity,
                                           int rateBasisPoints, int termDays, int intervalDays) {
    public enum Action { ISSUE_BOND, SUBSCRIBE_BOND, APPLY_LOAN, REPAY_LOAN, SET_BENCHMARK_RATE,
        PLACE_BOND_BUY, PLACE_BOND_SELL, CANCEL_BOND_ORDER, SUBSCRIBE_CENTRAL_BANK_BILL }
    public static void encode(FinancialProductActionPacket p, FriendlyByteBuf b) { b.writeEnum(p.action); b.writeBoolean(p.targetId!=null); if(p.targetId!=null)b.writeUUID(p.targetId); b.writeLong(p.amount); b.writeLong(p.quantity); b.writeVarInt(p.rateBasisPoints); b.writeVarInt(p.termDays); b.writeVarInt(p.intervalDays); }
    public static FinancialProductActionPacket decode(FriendlyByteBuf b) { Action a=b.readEnum(Action.class); UUID id=b.readBoolean()?b.readUUID():null; var p=new FinancialProductActionPacket(a,id,b.readLong(),b.readLong(),b.readVarInt(),b.readVarInt(),b.readVarInt());if(p.rateBasisPoints<0||p.rateBasisPoints>20_000||p.termDays<0||p.termDays>3_650||p.intervalDays<0||p.intervalDays>3_650)throw new IllegalArgumentException("financial action range");return p; }
    public static void handle(FinancialProductActionPacket p, Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> {
            ServerPlayer player=supplier.get().getSender(); if(player==null||p.action==null)return;
            if (!finance.diagnostic.ModuleHealthRegistry.mayWrite(finance.diagnostic.ModuleHealthRegistry.Module.DEBT)) {
                GuiFeedbackPacket.send(player, "债务市场已因一致性问题暂停"); return;
            }
            if (!MarketDataRequestLimiter.allow(player.getUUID(), player.server.getTickCount(), "financial-action:" + p.action.name())) {
                GuiFeedbackPacket.send(player, "操作过于频繁"); return;
            }
            long day=EconomyCycleService.currentMcDay(player.server); String message;
            switch(p.action) {
                case ISSUE_BOND -> {
                    Company company=CompanyManager.getCompanyByOwner(player.getUUID());
                    if(company==null||p.amount<=0||p.quantity<=0||p.termDays<2||p.intervalDays<1||p.rateBasisPoints<=0) message="债券发行参数无效";
                    else { String code="B"+day+Integer.toUnsignedString(CorporateBondManager.bonds().size(),36).toUpperCase(); message=CorporateBondManager.issue(player.getUUID(),company.getCompanyId(),code,p.amount,p.quantity,p.rateBasisPoints,day,Math.min(3,p.termDays-1),p.termDays,p.intervalDays).message(); }
                }
                case SUBSCRIBE_BOND -> message=p.targetId==null||p.quantity<=0?"认购参数无效":CorporateBondManager.subscribe(player.getUUID(),p.targetId,p.quantity).message();
                case APPLY_LOAN -> { Company company=CompanyManager.getCompanyByOwner(player.getUUID()); message=company==null||p.amount<=0||p.termDays<2||p.intervalDays<1?"贷款参数无效":CompanyLoanManager.apply(player.getUUID(),company.getCompanyId(),p.amount,day,p.termDays,p.intervalDays).message(); }
                case REPAY_LOAN -> message=p.targetId==null||p.amount<=0?"还款参数无效":CompanyLoanManager.repay(player.getUUID(),p.targetId,p.amount,day).message();
                case SET_BENCHMARK_RATE -> message=!player.hasPermissions(2)?"权限不足":MonetaryPolicyService.setBenchmarkRate(day,p.rateBasisPoints,"管理员调整")?"基准利率已更新":"利率无变化、越界或本日已经调整";
                case PLACE_BOND_BUY -> message=p.targetId==null||p.amount<=0||p.quantity<=0?"债券买单参数无效":BondMarketManager.placeBuy(player.getUUID(),p.targetId,p.amount,p.quantity).message();
                case PLACE_BOND_SELL -> message=p.targetId==null||p.amount<=0||p.quantity<=0?"债券卖单参数无效":BondMarketManager.placeSell(player.getUUID(),p.targetId,p.amount,p.quantity).message();
                case CANCEL_BOND_ORDER -> message=p.targetId!=null&&BondMarketManager.cancel(player.getUUID(),p.targetId)?"债券订单已撤销":"订单不存在、无权撤销或资产无法释放";
                case SUBSCRIBE_CENTRAL_BANK_BILL -> message=p.amount<=0?"票据认购参数无效":CentralBankBillManager.subscribe(player.getUUID(),p.termDays,p.amount,day).message();
                default -> message="未知金融操作";
            }
            GuiFeedbackPacket.send(player,message);
        }); supplier.get().setPacketHandled(true);
    }
}
