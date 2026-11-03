package com.rtgame.warpcovers.gt;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import com.rtgame.warpcovers.core.EndpointConfig;
import com.rtgame.warpcovers.core.FilterList;
import com.rtgame.warpcovers.core.InteractionState;
import com.rtgame.warpcovers.core.Location;

/** 连接端配置与位置的 NBT 读写。键名短而稳定：改名会让旧存档里的配置丢失。 */
public final class ConfigNbt {

    static final String STATE = "st";
    static final String WHITELIST = "wl";
    static final String MATCH_NBT = "nbt";
    static final String KEYS = "fl";
    static final String PRIORITY = "pr";
    static final String RATE = "rt";
    static final String PERIOD = "pd";

    private static final int TAG_STRING = 8;

    private ConfigNbt() {}

    public static void write(EndpointConfig config, NBTTagCompound tag) {
        tag.setString(
            STATE,
            config.state()
                .name());
        FilterList filter = config.filter();
        tag.setBoolean(WHITELIST, filter.isWhitelist());
        tag.setBoolean(MATCH_NBT, filter.matchNbt());
        NBTTagList list = new NBTTagList();
        for (String key : filter.keys()) {
            list.appendTag(new NBTTagString(key));
        }
        tag.setTag(KEYS, list);
        tag.setInteger(PRIORITY, config.priority());
        tag.setLong(RATE, config.rate());
        tag.setInteger(PERIOD, config.period());
    }

    /** 读进已有的配置对象（保持对象身份：加载期间它与核心索引共用）。缺失或损坏的字段取默认值。 */
    public static void read(NBTTagCompound tag, EndpointConfig into) {
        into.setState(readState(tag.getString(STATE)));
        FilterList filter = new FilterList();
        filter.setWhitelist(!tag.hasKey(WHITELIST) || tag.getBoolean(WHITELIST));
        filter.setMatchNbt(tag.getBoolean(MATCH_NBT));
        List<String> keys = new ArrayList<String>();
        NBTTagList list = tag.getTagList(KEYS, TAG_STRING);
        for (int i = 0; i < list.tagCount(); i++) {
            keys.add(list.getStringTagAt(i));
        }
        filter.replaceKeys(keys);
        into.setFilter(filter);
        into.setPriority(tag.getInteger(PRIORITY));
        into.setRate(tag.getLong(RATE));
        into.setPeriod(tag.getInteger(PERIOD));
    }

    public static InteractionState readState(String name) {
        try {
            return InteractionState.valueOf(name);
        } catch (IllegalArgumentException e) {
            return InteractionState.EMPTY;
        }
    }

    public static NBTTagCompound writeLocation(Location location) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("d", location.dimension());
        tag.setInteger("x", location.x());
        tag.setInteger("y", location.y());
        tag.setInteger("z", location.z());
        tag.setByte("s", (byte) location.side());
        return tag;
    }

    public static Location readLocation(NBTTagCompound tag) {
        if (!tag.hasKey("y")) {
            return Location.NOWHERE;
        }
        return new Location(
            tag.getInteger("d"),
            tag.getInteger("x"),
            tag.getInteger("y"),
            tag.getInteger("z"),
            tag.getByte("s"));
    }
}
