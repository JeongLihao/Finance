package finance.simple;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static finance.simple.SimpleUnlocks.Group.BET;
import static finance.simple.SimpleUnlocks.Group.BOSS;
import static finance.simple.SimpleUnlocks.Group.GROWTH;
import static org.junit.jupiter.api.Assertions.*;

/** 进阶玩法按进度解锁；配置可以直接打开或整个关掉。 */
class SimpleUnlocksTest {

    private static final EnumSet<SimpleUnlocks.Group> NONE = EnumSet.noneOf(SimpleUnlocks.Group.class);

    @Test void milestonesUnlockTheirGroup() {
        assertEquals(NONE, SimpleUnlocks.reached(false, false, false));
        assertEquals(EnumSet.of(GROWTH), SimpleUnlocks.reached(true, false, false), "存过理财 → 进取理财");
        assertEquals(EnumSet.of(BET), SimpleUnlocks.reached(false, true, false), "买过股票 → 押注");
        assertEquals(EnumSet.of(BOSS), SimpleUnlocks.reached(false, false, true), "公司上市 → 大老板");
    }

    @Test void featuresNeedTheirGroupUnlocked() {
        assertFalse(SimpleUnlocks.allowed(NONE, Set.of(), Set.of(), "margin"));
        assertTrue(SimpleUnlocks.allowed(EnumSet.of(BET), Set.of(), Set.of(), "margin"));
        assertTrue(SimpleUnlocks.allowed(EnumSet.of(BET), Set.of(), Set.of(), "futures"));
        assertFalse(SimpleUnlocks.allowed(EnumSet.of(BET), Set.of(), Set.of(), "fund"));
        assertFalse(SimpleUnlocks.allowed(EnumSet.allOf(SimpleUnlocks.Group.class), Set.of(), Set.of(), "unknown"));
    }

    @Test void configCanOpenOrCloseFeatures() {
        assertTrue(SimpleUnlocks.allowed(NONE, Set.of("bet"), Set.of(), "futures"), "extras 写组名，整组直接打开");
        assertTrue(SimpleUnlocks.allowed(NONE, Set.of("margin"), Set.of(), "margin"), "旧配置写单项名照样有效");
        assertFalse(SimpleUnlocks.allowed(NONE, Set.of("margin"), Set.of(), "futures"));
        assertTrue(SimpleUnlocks.allowed(NONE, Set.of("all"), Set.of(), "takeover"));
        EnumSet<SimpleUnlocks.Group> all = EnumSet.allOf(SimpleUnlocks.Group.class);
        assertFalse(SimpleUnlocks.allowed(all, Set.of(), Set.of("futures"), "futures"), "disabled 优先：解锁了也不能用");
        assertTrue(SimpleUnlocks.allowed(all, Set.of(), Set.of("futures"), "margin"), "只关商品押注，股票押注照常");
        assertFalse(SimpleUnlocks.allowed(all, Set.of("all"), Set.of("all"), "margin"));
    }

    @Test void clientGetsMembersAndGroupNames() {
        List<String> names = SimpleUnlocks.allowedNames(EnumSet.of(BET), Set.of(), Set.of("futures"));
        assertTrue(names.containsAll(List.of("margin", "bet")), names.toString());
        assertFalse(names.contains("futures"), names.toString());
        assertFalse(names.contains("boss"), names.toString());
    }

    @Test void lockedGroupsSkipClosedAndInstantOnes() {
        assertEquals(List.of("growth", "bet", "boss"), SimpleUnlocks.lockedIds(NONE, Set.of(), Set.of()));
        assertEquals(List.of("bet", "boss"), SimpleUnlocks.lockedIds(NONE, Set.of("growth"), Set.of()), "直接打开的不用提示怎么解锁");
        assertEquals(List.of("growth", "bet"), SimpleUnlocks.lockedIds(NONE, Set.of(), Set.of("boss")), "整组关掉的不提示");
        assertEquals(List.of("growth", "bet", "boss"), SimpleUnlocks.lockedIds(NONE, Set.of(), Set.of("futures")), "只关一项，整组仍可解锁");
        assertEquals(List.of("growth", "boss"), SimpleUnlocks.lockedIds(EnumSet.of(BET), Set.of(), Set.of()));
    }

    @Test void groupLookupAndStorageRoundTrip() {
        assertEquals(BOSS, SimpleUnlocks.Group.of("takeover"));
        assertEquals(BET, SimpleUnlocks.Group.of("BET"));
        assertNull(SimpleUnlocks.Group.of("x"));
        assertEquals(EnumSet.of(GROWTH, BOSS), SimpleUnlocks.read(SimpleUnlocks.write(EnumSet.of(GROWTH, BOSS))));
        assertEquals(NONE, SimpleUnlocks.read("garbage,,"));
    }
}
