package com.rtgame.warpcovers.gt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ModRecipesPlanTest {

    @Test
    void 全部开启时注册全部配方() {
        assertEquals(
            new HashSet<String>(Arrays.asList("fluid", "convert", "item", "energy", "aggregate", "manager")),
            ModRecipes.plan(true, true, true));
    }

    @Test
    void 关闭某类型后不注册它的配方与聚合配方() {
        Set<String> noFluid = ModRecipes.plan(false, true, true);
        assertFalse(noFluid.contains("fluid"));
        assertFalse(noFluid.contains("convert"));
        assertFalse(noFluid.contains("aggregate"));
        assertTrue(noFluid.contains("item") && noFluid.contains("energy") && noFluid.contains("manager"));
        assertFalse(ModRecipes.plan(true, false, true).contains("item"));
        assertFalse(ModRecipes.plan(true, true, false).contains("energy"));
    }

    @Test
    void 全部关闭时没有任何配方() {
        assertTrue(ModRecipes.plan(false, false, false).isEmpty());
    }
}
