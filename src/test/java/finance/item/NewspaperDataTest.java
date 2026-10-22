package finance.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import finance.testutil.MinecraftTestBootstrap;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NewspaperDataTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensureStarted();
    }

    @Test
    void weeklyIssueSplitsIntoSectionsWithoutMastheadOrRepeatedHeadline() {
        // 与 NewsWriter.weekly 排出的原文一致：报头、头版（头条与正文）、各版
        NewspaperData.Paper paper = new NewspaperData.Paper(
                3,
                21,
                false,
                "铁价飙升",
                List.of(
                        "《金融周报》第 3 期\n第 21 天\n\n【头版】铁价飙升\n\n北镇的铁本地价 +40%。",
                        "【市场】\n物价指数 101.2，本周 +1.0%\n\n· 北镇的铁本地价 +40%",
                        "【民生与政坛】\n· 北镇 40 人（+2），生活 80%",
                        "【你的生意】\n你还没有公司。"));
        List<NewspaperData.Section> sections = NewspaperData.sections(paper);
        assertEquals(
                List.of("头版", "市场", "民生与政坛", "你的生意"),
                sections.stream().map(NewspaperData.Section::title).toList());
        assertEquals("北镇的铁本地价 +40%。", sections.get(0).body());
        assertEquals("物价指数 101.2，本周 +1.0%\n\n· 北镇的铁本地价 +40%", sections.get(1).body());
    }

    @Test
    void extraIssueTreatsHaowaiHeaderAsFrontPage() {
        NewspaperData.Paper paper = new NewspaperData.Paper(
                4, 23, true, "北镇罢工", List.of("【号外】第 4 期\n第 23 天\n\n北镇罢工\n\n工会宣布停工。", "【民生与政坛】\n· 北镇 40 人（+0）"));
        List<NewspaperData.Section> sections = NewspaperData.sections(paper);
        assertEquals("头版", sections.get(0).title());
        assertEquals("工会宣布停工。", sections.get(0).body());
        assertEquals("民生与政坛", sections.get(1).title());
    }

    @Test
    void sectionsSpanningSeveralPagesAreJoined() {
        NewspaperData.Paper paper =
                new NewspaperData.Paper(1, 7, false, "本周风平浪静", List.of("【市场】\n第一行", "第二行", "【商机】\n暂无"));
        List<NewspaperData.Section> sections = NewspaperData.sections(paper);
        assertEquals("市场", sections.get(0).title());
        assertEquals("第一行\n第二行", sections.get(0).body());
        assertEquals("商机", sections.get(1).title());
    }

    @Test
    void nbtRoundTripAndMissingData() {
        ItemStack stack = new ItemStack(Items.PAPER);
        assertNull(NewspaperData.read(stack));
        NewspaperData.Paper paper = new NewspaperData.Paper(9, 63, true, "标题", List.of("【号外】第 9 期", "正文"));
        NewspaperData.write(stack, paper);
        assertEquals(paper, NewspaperData.read(stack));
        assertTrue(NewspaperData.sections(null).isEmpty());
    }

    @Test
    void oversizedNbtIsBoundedWhenRead() {
        ItemStack stack = new ItemStack(Items.PAPER);
        CompoundTag tag = new CompoundTag();
        tag.putInt("number", -5);
        tag.putString("headline", "长".repeat(1_000));
        ListTag pages = new ListTag();
        for (int i = 0; i < 500; i++) pages.add(StringTag.valueOf("字".repeat(5_000)));
        tag.put("pages", pages);
        stack.getOrCreateTag().put(NewspaperData.TAG, tag);
        NewspaperData.Paper paper = NewspaperData.read(stack);
        assertEquals(0, paper.number());
        assertEquals(NewspaperData.MAX_HEADLINE_CHARS, paper.headline().length());
        assertEquals(NewspaperData.MAX_PAGES, paper.pages().size());
        assertEquals(NewspaperData.MAX_PAGE_CHARS, paper.pages().get(0).length());
    }
}
