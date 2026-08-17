package finance.network;

import finance.bank.BankStressTestService;
import finance.bank.BankingManager;
import finance.bank.DepositInsuranceService;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.cycle.EconomyCycleService;
import finance.debt.CompanyLoanManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record BankActionPacket(Action action, UUID primaryId, UUID secondaryId,
                               long amount, int termDays, int intervalDays) {
    public enum Action {
        DEPOSIT, WITHDRAW, OPEN_TIME, REDEEM_TIME, TRANSFER,
        APPLY_COMPANY_LOAN, WITHDRAW_COMPANY, ADMIN_RESOLVE, ADMIN_STRESS_TEST
    }

    public static void encode(BankActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        writeId(buffer, packet.primaryId);
        writeId(buffer, packet.secondaryId);
        buffer.writeLong(packet.amount);
        buffer.writeVarInt(packet.termDays);
        buffer.writeVarInt(packet.intervalDays);
    }

    public static BankActionPacket decode(FriendlyByteBuf buffer) {
        BankActionPacket packet=new BankActionPacket(buffer.readEnum(Action.class), readId(buffer), readId(buffer),
                buffer.readLong(), buffer.readVarInt(), buffer.readVarInt());
        if(packet.termDays<0||packet.termDays>3_650||packet.intervalDays<0||packet.intervalDays>3_650)throw new IllegalArgumentException("bank action range");
        return packet;
    }

    private static void writeId(FriendlyByteBuf buffer, UUID id) {
        buffer.writeBoolean(id != null);
        if (id != null) buffer.writeUUID(id);
    }

    private static UUID readId(FriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readUUID() : null;
    }

    public static void handle(BankActionPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> run(packet, context.getSender()));
        context.setPacketHandled(true);
    }

    private static void run(BankActionPacket packet, ServerPlayer player) {
        if (player == null || packet.action == null
                || !MarketDataRequestLimiter.allow(player.getUUID(), player.server.getTickCount(),
                "bank-action:" + packet.action)) return;
        if (!finance.diagnostic.ModuleHealthRegistry.mayWrite(finance.diagnostic.ModuleHealthRegistry.Module.BANKING)) {
            GuiFeedbackPacket.send(player, "银行模块已因一致性问题暂停"); return;
        }
        long day = EconomyCycleService.currentMcDay(player.server);
        UUID owner = player.getUUID();
        String message = switch (packet.action) {
            case DEPOSIT -> packet.primaryId != null && packet.amount > 0
                    ? BankingManager.depositPlayer(owner, packet.primaryId, packet.amount, day).message()
                    : "存款参数无效";
            case WITHDRAW -> packet.primaryId != null && packet.amount > 0
                    ? BankingManager.withdrawPlayer(owner, packet.primaryId, packet.amount, day).message()
                    : "提款参数无效";
            case OPEN_TIME -> packet.primaryId != null && packet.amount > 0
                    ? BankingManager.openTimeDeposit(owner, packet.primaryId, packet.amount, packet.termDays, day).message()
                    : "定期参数无效";
            case REDEEM_TIME -> packet.primaryId != null
                    ? BankingManager.redeemTimeDeposit(owner, packet.primaryId, day).message() : "合同无效";
            case TRANSFER -> packet.primaryId != null && packet.secondaryId != null && packet.amount > 0
                    ? BankingManager.transfer(owner, packet.primaryId, packet.secondaryId, packet.amount, day).message()
                    : "转账参数无效";
            case APPLY_COMPANY_LOAN -> {
                Company company = CompanyManager.getCompanyByOwner(owner);
                yield company != null && packet.primaryId != null && packet.amount > 0
                        ? CompanyLoanManager.applyCommercial(owner, company.getCompanyId(), packet.primaryId,
                        packet.amount, day, packet.termDays, packet.intervalDays).message()
                        : "公司或银行无效";
            }
            case WITHDRAW_COMPANY -> packet.primaryId != null && packet.amount > 0
                    && BankingManager.withdrawCompanyToCash(owner, packet.primaryId, packet.amount, day)
                    ? "公司存款已提取" : "公司提款失败";
            case ADMIN_RESOLVE -> player.hasPermissions(2) && packet.primaryId != null
                    && DepositInsuranceService.resolve(packet.primaryId, day)
                    ? "银行处置完成" : "权限不足或银行不可处置";
            case ADMIN_STRESS_TEST -> {
                if (!player.hasPermissions(2)) yield "权限不足";
                var result = BankStressTestService.run(
                        new BankStressTestService.Scenario(2_500, 3_000, 5_000, 2_000));
                yield "压力测试：资本缺口 " + result.capitalShortfall()
                        + " 流动性缺口 " + result.liquidityShortfall()
                        + " 保险暴露 " + result.insuranceExposure()
                        + " 传染轮次 " + result.contagionRounds();
            }
        };
        GuiFeedbackPacket.send(player, message);
    }
}
