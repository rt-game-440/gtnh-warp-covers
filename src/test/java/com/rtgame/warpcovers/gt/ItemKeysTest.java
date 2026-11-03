package com.rtgame.warpcovers.gt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.junit.jupiter.api.Test;

/** 物品键里 NBT 部分的编码：同样内容必须得到同一个键，否则「匹配 NBT」开时同一种物品会被当成不同物品。 */
class ItemKeysTest {

    private static NBTTagCompound enchanted(boolean reversed) {
        NBTTagCompound first = new NBTTagCompound();
        first.setShort("id", (short) 16);
        first.setShort("lvl", (short) 3);
        NBTTagCompound second = new NBTTagCompound();
        second.setShort("id", (short) 34);
        second.setShort("lvl", (short) 1);
        NBTTagList ench = new NBTTagList();
        ench.appendTag(first);
        ench.appendTag(second);
        NBTTagCompound display = new NBTTagCompound();
        NBTTagCompound tag = new NBTTagCompound();
        if (reversed) {
            display.setInteger("color", 0xFF0000);
            display.setString("Name", "自检木棍");
            tag.setTag("display", display);
            tag.setTag("ench", ench);
            tag.setInteger("RepairCost", 2);
        } else {
            tag.setInteger("RepairCost", 2);
            tag.setTag("ench", ench);
            display.setString("Name", "自检木棍");
            display.setInteger("color", 0xFF0000);
            tag.setTag("display", display);
        }
        return tag;
    }

    @Test
    void 内容相同插入顺序不同编码一致() {
        String a = ItemKeys.encode(enchanted(false));
        String b = ItemKeys.encode(enchanted(true));
        assertNotNull(a);
        assertEquals(a, b);
    }

    @Test
    void 编码后可还原且不含键分隔符() {
        NBTTagCompound tag = enchanted(false);
        String text = ItemKeys.encode(tag);
        assertEquals(tag, ItemKeys.decode(text));
        assertFalse(text.indexOf('#') >= 0 || text.indexOf('@') >= 0, "编码不能含 # 或 @");
    }

    @Test
    void 内容不同编码不同() {
        NBTTagCompound other = enchanted(false);
        other.getCompoundTag("display")
            .setString("Name", "别的名字");
        assertFalse(
            ItemKeys.encode(enchanted(false))
                .equals(ItemKeys.encode(other)));
    }

    @Test
    void 坏的编码解析为空() {
        assertNull(ItemKeys.decode("不是Base64!!"));
    }
}
