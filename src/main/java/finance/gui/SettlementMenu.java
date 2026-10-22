package finance.gui;

import finance.registry.ModMenus;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public final class SettlementMenu extends AbstractContainerMenu {
    public static final int MAX_ROWS = 8;

    public record DemandRow(
            UUID id,
            String commodity,
            String theme,
            int quantity,
            long reward,
            long deadline,
            String status,
            boolean mine,
            int localPremiumBps,
            int shortageScore,
            String pressure,
            boolean priceReliable) {}

    /** 村民想买的东西：每天大约要多少、摊位卖多少钱以内村民肯买。 */
    public record WantRow(String commodityId, String name, long dailyNeed, long fairPrice) {}

    /** 玩家自己在这个聚落的摊位。 */
    public record StallRow(UUID id, String commodityId, String name, long price, int stock, long fairPrice) {}

    /** 玩家自己从这个聚落出发的商队路线。 */
    public record RouteRow(
            UUID id,
            String destination,
            String commodity,
            int quantity,
            int guard,
            boolean insured,
            String status,
            long totalProfit) {}

    public record Choice(String id, String name) {}

    /** “摊位”“商路”两页的数据。destinations 的 id 是聚落 UUID，goods 的 id 是商品 ID。 */
    public record Extras(
            int favor,
            List<WantRow> wants,
            List<StallRow> stalls,
            List<RouteRow> routes,
            List<Choice> destinations,
            List<Choice> goods) {
        public static final Extras EMPTY = new Extras(0, List.of(), List.of(), List.of(), List.of(), List.of());

        public Extras {
            wants = List.copyOf(wants.subList(0, Math.min(MAX_ROWS, wants.size())));
            stalls = List.copyOf(stalls.subList(0, Math.min(MAX_ROWS, stalls.size())));
            routes = List.copyOf(routes.subList(0, Math.min(MAX_ROWS, routes.size())));
            destinations = List.copyOf(destinations.subList(0, Math.min(MAX_CHOICES, destinations.size())));
            goods = List.copyOf(goods.subList(0, Math.min(MAX_CHOICES, goods.size())));
        }

        void write(FriendlyByteBuf b) {
            b.writeVarInt(favor + 1000);
            b.writeVarInt(wants.size());
            for (WantRow w : wants) {
                b.writeUtf(limit(w.commodityId(), 64), 64);
                b.writeUtf(limit(w.name(), 32), 32);
                b.writeVarLong(Math.max(0, w.dailyNeed()));
                b.writeVarLong(Math.max(0, w.fairPrice()));
            }
            b.writeVarInt(stalls.size());
            for (StallRow s : stalls) {
                b.writeUUID(s.id());
                b.writeUtf(limit(s.commodityId(), 64), 64);
                b.writeUtf(limit(s.name(), 32), 32);
                b.writeVarLong(Math.max(0, s.price()));
                b.writeVarInt(Math.max(0, s.stock()));
                b.writeVarLong(Math.max(0, s.fairPrice()));
            }
            b.writeVarInt(routes.size());
            for (RouteRow r : routes) {
                b.writeUUID(r.id());
                b.writeUtf(limit(r.destination(), 64), 64);
                b.writeUtf(limit(r.commodity(), 32), 32);
                b.writeVarInt(Math.max(0, r.quantity()));
                b.writeVarInt(Math.max(0, r.guard()));
                b.writeBoolean(r.insured());
                b.writeUtf(limit(r.status(), 16), 16);
                b.writeLong(r.totalProfit());
            }
            writeChoices(b, destinations);
            writeChoices(b, goods);
        }

        static Extras read(FriendlyByteBuf b) {
            try {
                int favor = b.readVarInt() - 1000;
                List<WantRow> wants = new ArrayList<>();
                for (int i = 0, n = count(b, MAX_ROWS); i < n; i++)
                    wants.add(new WantRow(b.readUtf(64), b.readUtf(32), b.readVarLong(), b.readVarLong()));
                List<StallRow> stalls = new ArrayList<>();
                for (int i = 0, n = count(b, MAX_ROWS); i < n; i++)
                    stalls.add(new StallRow(
                            b.readUUID(),
                            b.readUtf(64),
                            b.readUtf(32),
                            b.readVarLong(),
                            b.readVarInt(),
                            b.readVarLong()));
                List<RouteRow> routes = new ArrayList<>();
                for (int i = 0, n = count(b, MAX_ROWS); i < n; i++)
                    routes.add(new RouteRow(
                            b.readUUID(),
                            b.readUtf(64),
                            b.readUtf(32),
                            b.readVarInt(),
                            b.readVarInt(),
                            b.readBoolean(),
                            b.readUtf(16),
                            b.readLong()));
                return new Extras(favor, wants, stalls, routes, readChoices(b), readChoices(b));
            } catch (IndexOutOfBoundsException truncated) {
                throw new IllegalArgumentException("truncated settlement extras", truncated);
            }
        }

        private static void writeChoices(FriendlyByteBuf b, List<Choice> choices) {
            b.writeVarInt(choices.size());
            for (Choice c : choices) {
                b.writeUtf(limit(c.id(), 64), 64);
                b.writeUtf(limit(c.name(), 64), 64);
            }
        }

        private static List<Choice> readChoices(FriendlyByteBuf b) {
            List<Choice> out = new ArrayList<>();
            for (int i = 0, n = count(b, MAX_CHOICES); i < n; i++) out.add(new Choice(b.readUtf(64), b.readUtf(64)));
            return out;
        }

        private static int count(FriendlyByteBuf b, int max) {
            int n = b.readVarInt();
            if (n < 0 || n > max) throw new IllegalArgumentException("settlement extras size");
            return n;
        }
    }

    public static final int MAX_CHOICES = 16;

    private final UUID settlementId;
    private final String name, status, message;
    private final int contributionLevel, contributionPoints;
    private final List<DemandRow> rows;
    private final Extras extras;

    public SettlementMenu(int id, Inventory inv, FriendlyByteBuf b) {
        this(
                id,
                b.readUUID(),
                b.readUtf(64),
                b.readUtf(24),
                b.readVarInt(),
                b.readVarInt(),
                readRows(b),
                b.readUtf(96),
                Extras.read(b));
    }

    public SettlementMenu(
            int id,
            UUID settlementId,
            String name,
            String status,
            int level,
            int points,
            List<DemandRow> rows,
            String message,
            Extras extras) {
        super(ModMenus.SETTLEMENT.get(), id);
        this.settlementId = settlementId;
        this.name = limit(name, 64);
        this.status = limit(status, 24);
        this.contributionLevel = Math.max(0, Math.min(5, level));
        this.contributionPoints = Math.max(0, points);
        this.rows = List.copyOf(rows.subList(0, Math.min(MAX_ROWS, rows.size())));
        this.message = limit(message, 96);
        this.extras = extras == null ? Extras.EMPTY : extras;
    }

    public static void write(
            FriendlyByteBuf b,
            UUID id,
            String name,
            String status,
            int level,
            int points,
            List<DemandRow> rows,
            String message,
            Extras extras) {
        b.writeUUID(id);
        b.writeUtf(limit(name, 64), 64);
        b.writeUtf(limit(status, 24), 24);
        b.writeVarInt(Math.max(0, Math.min(5, level)));
        b.writeVarInt(Math.max(0, points));
        int n = Math.min(MAX_ROWS, rows.size());
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            DemandRow r = rows.get(i);
            b.writeUUID(r.id());
            b.writeUtf(limit(r.commodity(), 64), 64);
            b.writeUtf(limit(r.theme(), 32), 32);
            b.writeVarInt(r.quantity());
            b.writeLong(r.reward());
            b.writeLong(r.deadline());
            b.writeUtf(limit(r.status(), 16), 16);
            b.writeBoolean(r.mine());
            b.writeVarInt(Math.max(7500, Math.min(14000, r.localPremiumBps())));
            b.writeVarInt(Math.max(0, Math.min(10000, r.shortageScore())));
            b.writeUtf(limit(r.pressure(), 16), 16);
            b.writeBoolean(r.priceReliable());
        }
        b.writeUtf(limit(message, 96), 96);
        (extras == null ? Extras.EMPTY : extras).write(b);
    }

    static List<DemandRow> readRows(FriendlyByteBuf b) {
        try {
            int n = b.readVarInt();
            if (n < 0 || n > MAX_ROWS) throw new IllegalArgumentException("settlement rows");
            List<DemandRow> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                DemandRow r = new DemandRow(
                        b.readUUID(),
                        b.readUtf(64),
                        b.readUtf(32),
                        b.readVarInt(),
                        b.readLong(),
                        b.readLong(),
                        b.readUtf(16),
                        b.readBoolean(),
                        b.readVarInt(),
                        b.readVarInt(),
                        b.readUtf(16),
                        b.readBoolean());
                if (r.commodity().isBlank()
                        || r.quantity() <= 0
                        || r.reward() <= 0
                        || r.deadline() < 0
                        || r.localPremiumBps() < 7500
                        || r.localPremiumBps() > 14000
                        || r.shortageScore() < 0
                        || r.shortageScore() > 10000
                        || r.pressure().isBlank()) throw new IllegalArgumentException("invalid settlement row");
                out.add(r);
            }
            return out;
        } catch (IndexOutOfBoundsException truncated) {
            throw new IllegalArgumentException("truncated settlement rows", truncated);
        }
    }

    private static String limit(String s, int n) {
        s = s == null ? "" : s;
        return s.length() <= n ? s : s.substring(0, n);
    }

    public UUID settlementId() {
        return settlementId;
    }

    public String name() {
        return name;
    }

    public String status() {
        return status;
    }

    public int contributionLevel() {
        return contributionLevel;
    }

    public int contributionPoints() {
        return contributionPoints;
    }

    public List<DemandRow> rows() {
        return rows;
    }

    public String message() {
        return message;
    }

    public Extras extras() {
        return extras;
    }

    @Override
    public boolean stillValid(Player p) {
        return !(p instanceof ServerPlayer sp) || finance.settlement.SettlementService.isNearby(sp, settlementId);
    }

    @Override
    public ItemStack quickMoveStack(Player p, int i) {
        return ItemStack.EMPTY;
    }
}
