package finance.network;

import finance.account.AccountManager;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyStrategy;
import finance.data.EconomySavedData;
import finance.gui.FinanceGuiOpener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class CompanyManagePacket {

    public enum Action {
        SET_STRATEGY,
        SET_SELL_RATIO,
        UPGRADE_PRODUCTION,
        UPGRADE_STORAGE,
        UPGRADE_MANAGEMENT,
        INVEST,
        WITHDRAW
    }

    private final Action action;
    private final CompanyStrategy strategy;
    private final long amount;
    private final double ratio;

    public CompanyManagePacket(Action action) {
        this(action, CompanyStrategy.STABLE, 0, 0);
    }

    public static CompanyManagePacket strategy(CompanyStrategy strategy) {
        return new CompanyManagePacket(Action.SET_STRATEGY, strategy, 0, 0);
    }

    public static CompanyManagePacket sellRatio(double ratio) {
        return new CompanyManagePacket(Action.SET_SELL_RATIO, CompanyStrategy.STABLE, 0, ratio);
    }

    public static CompanyManagePacket amount(Action action, long amount) {
        return new CompanyManagePacket(action, CompanyStrategy.STABLE, amount, 0);
    }

    private CompanyManagePacket(Action action, CompanyStrategy strategy, long amount, double ratio) {
        this.action = action;
        this.strategy = strategy;
        this.amount = amount;
        this.ratio = ratio;
    }

    public static void encode(CompanyManagePacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeEnum(packet.strategy);
        buffer.writeLong(packet.amount);
        buffer.writeDouble(packet.ratio);
    }

    public static CompanyManagePacket decode(FriendlyByteBuf buffer) {
        return new CompanyManagePacket(
                buffer.readEnum(Action.class),
                buffer.readEnum(CompanyStrategy.class),
                buffer.readLong(),
                buffer.readDouble());
    }

    public static void handle(CompanyManagePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            Company company = CompanyManager.getCompanyByOwner(player.getUUID());
            if (company == null) {
                player.sendSystemMessage(Component.literal("你还没有公司。"));
                return;
            }

            String message = apply(player, company, packet);
            EconomySavedData.markDirty();
            player.sendSystemMessage(Component.literal(message));
            FinanceGuiOpener.open(player);
        });
        ctx.get().setPacketHandled(true);
    }

    private static String apply(ServerPlayer player, Company company, CompanyManagePacket packet) {
        return switch (packet.action) {
            case SET_STRATEGY -> {
                company.setStrategy(packet.strategy);
                yield "经营策略已调整为 " + packet.strategy.getDisplayName();
            }
            case SET_SELL_RATIO -> {
                company.setAutoSellRatio(packet.ratio);
                yield "自动出售比例已调整。";
            }
            case UPGRADE_PRODUCTION -> upgrade(player, company, "PRODUCTION");
            case UPGRADE_STORAGE -> upgrade(player, company, "STORAGE");
            case UPGRADE_MANAGEMENT -> upgrade(player, company, "MANAGEMENT");
            case INVEST -> invest(player, company, packet.amount);
            case WITHDRAW -> withdraw(player, company, packet.amount);
        };
    }

    private static String upgrade(ServerPlayer player, Company company, String type) {
        long cost = company.getUpgradeCost(type);
        if (!AccountManager.withdraw(player.getUUID(), cost)) {
            return "余额不足，升级需要 " + cost;
        }
        boolean ok = switch (type) {
            case "PRODUCTION" -> company.upgradeProduction();
            case "STORAGE" -> company.upgradeStorage();
            case "MANAGEMENT" -> company.upgradeManagement();
            default -> false;
        };
        if (!ok) {
            AccountManager.deposit(player.getUUID(), cost);
            return "该升级已达到上限。";
        }
        company.deposit(cost);
        return "公司升级成功，投入资金 " + cost;
    }

    private static String invest(ServerPlayer player, Company company, long amount) {
        if (amount <= 0) return "注资金额必须大于 0。";
        if (!AccountManager.withdraw(player.getUUID(), amount)) return "余额不足。";
        company.deposit(amount);
        return "已向公司注资 " + amount;
    }

    private static String withdraw(ServerPlayer player, Company company, long amount) {
        if (amount <= 0) return "提取金额必须大于 0。";
        if (!company.withdraw(amount)) return "公司现金不足。";
        AccountManager.deposit(player.getUUID(), amount);
        return "已从公司提取 " + amount;
    }
}
