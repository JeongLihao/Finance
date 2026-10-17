package finance.client;

import finance.network.PlayerServicesResponsePacket;

import java.util.Arrays;

/**
 * “更多操作”界面的纯逻辑部分（不依赖客户端渲染类，方便测试）：各页输入框的标签、数字解析。
 */
public final class PlayerServicesForm {
    public enum Page {
        MARGIN("融资融券"), BANK("我的银行"), TENDER("要约收购"), MACRO("宏观行动"), RESEARCH("财报商情"), CASH("现金兑换");

        final String label;

        Page(String label) { this.label = label; }

        public String label() { return label; }
    }

    /** 输入有误：消息直接显示在状态栏，不发包。 */
    public static final class InvalidInput extends RuntimeException {
        public InvalidInput(String message) { super(message, null, false, false); }
    }

    static final int BOXES = 4;

    private PlayerServicesForm() { }

    /** 该页四个输入框的标签；null 表示这个框不显示。 */
    public static String[] labels(Page page, PlayerServicesResponsePacket data) {
        String[] l = switch (page) {
            case MARGIN -> new String[]{"股票代码", "数量", "金额", null};
            case BANK -> data != null && data.bank().owned()
                    ? new String[]{"活期点差 bps", "定期点差 bps", "贷款点差 bps", "分红金额"}
                    : new String[]{"代码 名称", "资本金", null, null};
            case TENDER -> new String[]{"公司名称", "价格", "股数", "天数"};
            case MACRO -> new String[]{"日工资", "游说金额", null, null};
            case RESEARCH -> new String[]{"股票代码", null, null, null};
            case CASH -> new String[]{"金额", "绿宝石数量", null, null};
        };
        return Arrays.copyOf(l, BOXES);
    }

    /** 解析正整数；空或非法时抛 {@link InvalidInput}。 */
    public static long positive(String raw, String name) {
        long v = number(raw, name, false);
        if (v <= 0) throw new InvalidInput(name + "必须大于 0");
        return v;
    }

    /** 解析非负整数；allowEmpty 时空串返回 0。 */
    public static long number(String raw, String name, boolean allowEmpty) {
        String s = raw == null ? "" : raw.trim().replace(",", "").replace("，", "");
        if (s.isEmpty()) {
            if (allowEmpty) return 0;
            throw new InvalidInput("请填写" + name);
        }
        try {
            long v = Long.parseLong(s);
            if (v < 0) throw new InvalidInput(name + "不能为负数");
            return v;
        } catch (NumberFormatException e) {
            throw new InvalidInput(name + "必须是整数");
        }
    }

    /** 必填文本（股票代码、公司名等）。 */
    public static String text(String raw, String name) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) throw new InvalidInput("请填写" + name);
        return s;
    }
}
