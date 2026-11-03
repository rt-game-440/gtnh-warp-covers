package com.rtgame.warpcovers.gt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.rtgame.warpcovers.core.ObjectKeys;

/**
 * 物品在核心里的键：{@code 注册名@损伤值}，带非空 NBT 时追加 {@code #Base64(NBT)}。
 * NBT 先按键名排序重建再编码，内容相同的 NBT 总是得到同一个键；关闭「匹配 NBT」的名单只比较 # 之前的部分。
 */
public final class ItemKeys {

    private static final char META_SEPARATOR = '@';
    private static final int PROTOTYPE_CACHE_SIZE = 512;

    /** 键 → 数量为 1 的物品原型。解析带 NBT 的键要解码，缓存起来避免每次传输都解码。 */
    private static final Map<String, ItemStack> PROTOTYPES = new LinkedHashMap<String, ItemStack>(64, 0.75f, true) {

        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ItemStack> eldest) {
            return size() > PROTOTYPE_CACHE_SIZE;
        }
    };

    private ItemKeys() {}

    /** 物品的键；物品为空或没有注册名时返回 null。 */
    public static String of(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        return of(stack.getItem(), stack.getItemDamage(), stack.getTagCompound());
    }

    public static String of(Item item, int meta, NBTTagCompound tag) {
        if (item == null) {
            return null;
        }
        String name = Item.itemRegistry.getNameForObject(item);
        if (name == null) {
            return null;
        }
        String base = name + META_SEPARATOR + meta;
        if (tag == null || tag.hasNoTags()) {
            return base;
        }
        String nbt = encode(tag);
        return nbt == null ? base : base + ObjectKeys.NBT_SEPARATOR + nbt;
    }

    /** 键对应的数量为 1 的物品（新副本，可随意修改）；物品已不存在或键无法解析时返回 null。 */
    public static ItemStack prototype(String key) {
        if (key == null) {
            return null;
        }
        ItemStack cached;
        synchronized (PROTOTYPES) {
            cached = PROTOTYPES.get(key);
        }
        if (cached == null) {
            cached = parse(key);
            if (cached == null) {
                return null;
            }
            synchronized (PROTOTYPES) {
                PROTOTYPES.put(key, cached);
            }
        }
        return cached.copy();
    }

    private static ItemStack parse(String key) {
        String base = ObjectKeys.withoutNbt(key);
        int at = base.lastIndexOf(META_SEPARATOR);
        if (at <= 0) {
            return null;
        }
        Object item = Item.itemRegistry.getObject(base.substring(0, at));
        if (!(item instanceof Item)) {
            return null;
        }
        int meta;
        try {
            meta = Integer.parseInt(base.substring(at + 1));
        } catch (NumberFormatException e) {
            return null;
        }
        ItemStack stack = new ItemStack((Item) item, 1, meta);
        if (ObjectKeys.hasNbt(key)) {
            NBTTagCompound tag = decode(key.substring(base.length() + 1));
            if (tag == null) {
                return null;
            }
            stack.setTagCompound(tag);
        }
        return stack;
    }

    static String encode(NBTTagCompound tag) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            CompressedStreamTools.write(canonical(tag), new DataOutputStream(bytes));
            return Base64.getEncoder()
                .encodeToString(bytes.toByteArray());
        } catch (IOException e) {
            return null;
        }
    }

    static NBTTagCompound decode(String text) {
        try {
            byte[] bytes = Base64.getDecoder()
                .decode(text);
            return CompressedStreamTools.read(new DataInputStream(new ByteArrayInputStream(bytes)));
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }

    /** 按键名排序重建：NBTTagCompound 内部是 HashMap，同样的内容可能以不同顺序写出。 */
    private static NBTTagCompound canonical(NBTTagCompound tag) {
        List<String> names = new ArrayList<String>();
        for (Object name : tag.func_150296_c()) {
            names.add((String) name);
        }
        Collections.sort(names);
        NBTTagCompound result = new NBTTagCompound();
        for (String name : names) {
            result.setTag(name, canonical(tag.getTag(name)));
        }
        return result;
    }

    private static NBTBase canonical(NBTBase value) {
        if (value instanceof NBTTagCompound) {
            return canonical((NBTTagCompound) value);
        }
        if (value instanceof NBTTagList && ((NBTTagList) value).func_150303_d() == Constants.NBT.TAG_COMPOUND) {
            NBTTagList list = (NBTTagList) value;
            NBTTagList result = new NBTTagList();
            for (int i = 0; i < list.tagCount(); i++) {
                result.appendTag(canonical(list.getCompoundTagAt(i)));
            }
            return result;
        }
        return value.copy();
    }
}
