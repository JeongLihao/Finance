package finance.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 事件模板池 —— 按 tier 分类的预定义事件。
 * 后续可改为从配置文件加载。
 */
public class EventTemplates {


    private static final List<Template> MINOR = new ArrayList<>();
    private static final List<Template> MAJOR = new ArrayList<>();
    private static final List<Template> BLACK_SWANS = new ArrayList<>();

    static {
        // ---- 一级事件（MINOR）±5%~10%，影响单个商品 ----
        MINOR.add(new Template("运输延误", "交通受阻导致货物积压", false, 1.08, 14400));
        MINOR.add(new Template("天气异常", "恶劣天气影响产出", false, 0.93, 16000));
        MINOR.add(new Template("矿工罢工", "矿工要求加薪停产", false, 1.10, 24000));
        MINOR.add(new Template("丰收消息", "本季产量超出预期", false, 0.95, 12000));
        MINOR.add(new Template("需求激增", "市场短期需求上升", false, 1.06, 14000));
        MINOR.add(new Template("库存清仓", "商人为腾仓低价出货", false, 0.92, 13000));

        // ---- 二级事件（MAJOR）±15%~25%，影响单个商品 ----
        MAJOR.add(new Template("发现大型矿脉", "勘探队发现丰富矿藏", false, 0.80, 48000));
        MAJOR.add(new Template("粮食歉收", "病虫害导致大面积减产", false, 1.22, 48000));
        MAJOR.add(new Template("能源短缺", "燃料供应中断", false, 1.18, 72000));
        MAJOR.add(new Template("贸易禁运", "邻国实施出口限制", false, 1.25, 36000));
        MAJOR.add(new Template("技术革新", "新工艺大幅降低生产成本", false, 0.83, 50000));
        MAJOR.add(new Template("投机狂潮", "游资涌入推高价格", false, 1.20, 60000));

        // ---- 三级事件（BLACK_SWAN）±40%~60% ----
        BLACK_SWANS.add(new Template("战争爆发", "大战波及主要产区，恐慌性抢购", true, 1.50, 120000));
        BLACK_SWANS.add(new Template("金融危机", "全球信用体系崩溃，资产抛售", true, 0.60, 96000));
        BLACK_SWANS.add(new Template("科技突破", "革命性技术淘汰了传统产业", false, 0.45, 72000));
        BLACK_SWANS.add(new Template("资源枯竭", "主力矿场彻底采空", false, 1.60, 72000));
    }

    /**
     * 随机抽取一个指定 tier 的事件模板。
     * @param tier 事件等级
     * @param commodityIds 当前所有商品 ID 列表
     * @return 具体化的 MarketEvent，或 null
     */
    public static MarketEvent roll(EventTier tier, List<String> commodityIds) {
        List<Template> pool = switch (tier) {
            case MINOR -> MINOR;
            case MAJOR -> MAJOR;
            case BLACK_SWAN -> BLACK_SWANS;
        };

        if (pool.isEmpty()) return null;

        Template template = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));

        // affectsAll = true → 影响全部商品（commodityId = null）
        String commodityId = template.affectsAll ? null
                : commodityIds.get(ThreadLocalRandom.current().nextInt(commodityIds.size()));

        int pctRange = tier.maxPct - tier.minPct;
        int pct = tier.minPct + (pctRange > 0 ? ThreadLocalRandom.current().nextInt(pctRange + 1) : 0);
        boolean positive = template.baseMultiplier >= 1.0;
        double multiplier = positive
                ? 1.0 + pct / 100.0
                : 1.0 - pct / 100.0;

        int duration = tier.minDuration;
        if (tier.maxDuration > tier.minDuration) {
            duration += ThreadLocalRandom.current().nextInt(tier.maxDuration - tier.minDuration + 1);
        }

        return new MarketEvent(template.name, template.description, tier,
                commodityId, multiplier, duration);
    }

    public static List<Template> getMinorEvents() {
        return Collections.unmodifiableList(MINOR);
    }

    public static List<Template> getMajorEvents() {
        return Collections.unmodifiableList(MAJOR);
    }

    public static List<Template> getBlackSwanEvents() {
        return Collections.unmodifiableList(BLACK_SWANS);
    }

    /** 事件模板 */
    public static class Template {
        public final String name;
        public final String description;
        /** true = 影响全部商品，false = 影响单个随机商品 */
        public final boolean affectsAll;
        public final double baseMultiplier;
        public final int defaultDuration;

        public Template(String name, String description, boolean affectsAll,
                        double baseMultiplier, int defaultDuration) {
            this.name = name;
            this.description = description;
            this.affectsAll = affectsAll;
            this.baseMultiplier = baseMultiplier;
            this.defaultDuration = defaultDuration;
        }
    }
}
