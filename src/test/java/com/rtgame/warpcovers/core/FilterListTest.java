package com.rtgame.warpcovers.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 物品名单的 ID + 损伤值 + 「匹配 NBT」开关规则。键格式：注册名@损伤值[#NBT 编码]。 */
class FilterListTest {

    private static final String STICK = "minecraft:stick@0";
    private static final String NAMED_STICK = "minecraft:stick@0#AAAnamed";
    private static final String OTHER_NAMED_STICK = "minecraft:stick@0#BBBother";
    private static final String WHITE_WOOL = "minecraft:wool@0";
    private static final String ORANGE_WOOL = "minecraft:wool@1";

    private static FilterList whitelist(boolean matchNbt, String... keys) {
        FilterList filter = new FilterList();
        filter.setMatchNbt(matchNbt);
        for (String key : keys) {
            filter.add(key);
        }
        return filter;
    }

    @Test
    void 新名单默认不匹配NBT且是空白名单() {
        FilterList filter = new FilterList();
        assertFalse(filter.matchNbt());
        assertTrue(filter.isWhitelist());
        assertFalse(filter.allows(STICK));
    }

    @Test
    void 损伤值不同视为不同物品() {
        FilterList filter = whitelist(false, WHITE_WOOL);
        assertTrue(filter.allows(WHITE_WOOL));
        assertFalse(filter.allows(ORANGE_WOOL));
        assertFalse(filter.allows("minecraft:wool@10"), "损伤值前缀相同也不算");
    }

    @Test
    void 匹配NBT关时忽略物品与名单项的NBT() {
        FilterList plain = whitelist(false, STICK);
        assertTrue(plain.allows(NAMED_STICK), "带 NBT 的物品命中不带 NBT 的名单项");
        FilterList named = whitelist(false, NAMED_STICK);
        assertTrue(named.allows(STICK), "名单项带 NBT 时也只比 ID 和损伤值");
        assertTrue(named.allows(OTHER_NAMED_STICK));
        assertFalse(named.allows(WHITE_WOOL));
    }

    @Test
    void 匹配NBT开时NBT必须完全相同() {
        FilterList plain = whitelist(true, STICK);
        assertTrue(plain.allows(STICK));
        assertFalse(plain.allows(NAMED_STICK));
        FilterList named = whitelist(true, NAMED_STICK);
        assertTrue(named.allows(NAMED_STICK));
        assertFalse(named.allows(STICK));
        assertFalse(named.allows(OTHER_NAMED_STICK));
    }

    @Test
    void 黑名单对NBT规则取反() {
        FilterList loose = whitelist(false, STICK);
        loose.setWhitelist(false);
        assertFalse(loose.allows(NAMED_STICK));
        assertTrue(loose.allows(WHITE_WOOL));
        FilterList strict = whitelist(true, STICK);
        strict.setWhitelist(false);
        assertTrue(strict.allows(NAMED_STICK), "匹配 NBT 开时改名木棍不在黑名单里");
        assertFalse(strict.allows(STICK));
    }

    @Test
    void 流体键不含NBT不受开关影响() {
        FilterList filter = whitelist(true, "water");
        assertTrue(filter.allows("water"));
        assertFalse(filter.allows("lava"));
    }

    @Test
    void 复制与相等包含匹配NBT开关() {
        FilterList a = whitelist(true, STICK);
        FilterList b = a.copy();
        assertEquals(a, b);
        assertTrue(b.matchNbt());
        b.setMatchNbt(false);
        assertFalse(a.equals(b));
    }

    @Test
    void 键工具按井号拆NBT() {
        assertEquals(STICK, ObjectKeys.withoutNbt(NAMED_STICK));
        assertEquals(STICK, ObjectKeys.withoutNbt(STICK));
        assertTrue(ObjectKeys.hasNbt(NAMED_STICK));
        assertFalse(ObjectKeys.hasNbt(STICK));
    }
}
