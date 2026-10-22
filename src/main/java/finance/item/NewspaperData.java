package finance.item;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** 《金融周报》报纸物品携带的内容：期号、日期、是否号外、头条和各版正文。
 * 读取时一律限长，创造模式改出来的异常 NBT 也不会让界面卡死。 */
public final class NewspaperData {
    public static final String TAG = "FinanceNewspaper";
    public static final int MAX_PAGES = 64, MAX_PAGE_CHARS = 2_000, MAX_HEADLINE_CHARS = 128;

    /** 一份报纸。pages 是报社排好的原文页，界面按“【版名】”重新分版。 */
    public record Paper(int number, long day, boolean extra, String headline, List<String> pages) {
        public Paper {
            headline = limit(headline, MAX_HEADLINE_CHARS);
            List<String> safe = new ArrayList<>();
            if (pages != null)
                for (String page : pages) {
                    if (safe.size() >= MAX_PAGES) break;
                    if (page != null) safe.add(limit(page, MAX_PAGE_CHARS));
                }
            pages = List.copyOf(safe);
        }
    }

    /** 报纸的一个版面。 */
    public record Section(String title, String body) {}

    public static final String FRONT_PAGE = "头版";
    private static final Pattern HEADER = Pattern.compile("^【([^】]{1,12})】(.*)$");
    private static final Pattern MASTHEAD = Pattern.compile("^《金融周报》第\\s*\\d+\\s*期$");
    private static final Pattern ISSUE_NO = Pattern.compile("^第\\s*\\d+\\s*期$");
    private static final Pattern DAY_LINE = Pattern.compile("^第\\s*-?\\d+\\s*天$");

    private NewspaperData() {}

    public static void write(ItemStack stack, Paper paper) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("number", paper.number());
        tag.putLong("day", paper.day());
        tag.putBoolean("extra", paper.extra());
        tag.putString("headline", paper.headline());
        ListTag pages = new ListTag();
        for (String page : paper.pages()) pages.add(StringTag.valueOf(page));
        tag.put("pages", pages);
        stack.getOrCreateTag().put(TAG, tag);
    }

    /** 没有报纸内容时返回 null。 */
    public static Paper read(ItemStack stack) {
        if (stack == null || !stack.hasTag() || !stack.getTag().contains(TAG, Tag.TAG_COMPOUND)) return null;
        CompoundTag tag = stack.getTag().getCompound(TAG);
        ListTag list = tag.getList("pages", Tag.TAG_STRING);
        List<String> pages = new ArrayList<>();
        for (int i = 0; i < list.size() && i < MAX_PAGES; i++) pages.add(list.getString(i));
        return new Paper(
                Math.max(0, tag.getInt("number")),
                tag.getLong("day"),
                tag.getBoolean("extra"),
                tag.getString("headline"),
                pages);
    }

    /**
     * 把原文页拼回整篇，再按“【版名】”切成版面。开头的报头（期号、日期）由界面另画，这里去掉；
     * 头版正文第一行若就是头条也去掉，避免重复。号外的“【号外】第 N 期”当作头版。
     */
    public static List<Section> sections(Paper paper) {
        List<Section> out = new ArrayList<>();
        if (paper == null) return out;
        String title = null;
        StringBuilder body = new StringBuilder();
        boolean frontHeadlineChecked = false;
        for (String raw : String.join("\n", paper.pages()).replace("\r", "").split("\n", -1)) {
            String line = raw.strip();
            Matcher header = HEADER.matcher(line);
            if (header.matches()) {
                if (title != null) out.add(section(title, body));
                title = header.group(1).strip();
                String rest = header.group(2).strip();
                if ("号外".equals(title)) {
                    title = FRONT_PAGE;
                    if (ISSUE_NO.matcher(rest).matches()) rest = "";
                }
                body.setLength(0);
                frontHeadlineChecked = false;
                if (!rest.isEmpty()) {
                    if (FRONT_PAGE.equals(title) && rest.equals(paper.headline())) frontHeadlineChecked = true;
                    else body.append(rest).append('\n');
                }
                continue;
            }
            if (title == null) {
                // 第一个版名之前只有报头
                if (line.isEmpty()
                        || MASTHEAD.matcher(line).matches()
                        || DAY_LINE.matcher(line).matches()) continue;
                title = FRONT_PAGE;
            }
            if (FRONT_PAGE.equals(title) && body.toString().isBlank()) {
                if (line.isEmpty() || DAY_LINE.matcher(line).matches()) continue;
                if (!frontHeadlineChecked) {
                    frontHeadlineChecked = true;
                    if (line.equals(paper.headline())) continue;
                }
            }
            body.append(raw.stripTrailing()).append('\n');
        }
        if (title != null) out.add(section(title, body));
        return out;
    }

    private static Section section(String title, StringBuilder body) {
        // 去掉首尾空行，连续空行合成一行
        String text = body.toString().strip().replaceAll("\n{3,}", "\n\n");
        return new Section(title, text);
    }

    private static String limit(String value, int max) {
        String text = value == null ? "" : value;
        return text.length() <= max ? text : text.substring(0, max);
    }
}
