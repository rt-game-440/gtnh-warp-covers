package com.rtgame.warpcovers.gt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import com.rtgame.warpcovers.WarpCovers;
import com.rtgame.warpcovers.core.Channel;
import com.rtgame.warpcovers.core.ChannelColors;
import com.rtgame.warpcovers.core.ChannelHub;
import com.rtgame.warpcovers.core.ChannelPermissions;
import com.rtgame.warpcovers.core.ChannelType;
import com.rtgame.warpcovers.core.DisconnectReason;
import com.rtgame.warpcovers.core.DistributionMode;
import com.rtgame.warpcovers.core.Endpoint;
import com.rtgame.warpcovers.core.EndpointConfig;
import com.rtgame.warpcovers.core.Tombstone;

/** 频道表、连接端索引与墓碑的存档格式。带版本号，以后格式变化时据此迁移。 */
public final class HubNbt {

    public static final int VERSION = 1;

    private static final int TAG_COMPOUND = 10;
    private static final int TAG_STRING = 8;

    private HubNbt() {}

    public static NBTTagCompound write(ChannelHub hub) {
        NBTTagCompound root = new NBTTagCompound();
        write(hub, root);
        return root;
    }

    public static void write(ChannelHub hub, NBTTagCompound root) {
        root.setInteger("version", VERSION);
        root.setLong("nextOrder", hub.nextConnectOrder());

        NBTTagList channels = new NBTTagList();
        for (Channel channel : hub.channels()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("id", channel.id());
            tag.setString(
                "type",
                channel.type()
                    .key());
            tag.setString("name", channel.name());
            tag.setString("note", channel.note());
            tag.setInteger("color", channel.color());
            tag.setString("owner", channel.ownerId());
            tag.setString("ownerName", channel.ownerName());
            tag.setString(
                "distribution",
                channel.distribution()
                    .key());
            tag.setIntArray(
                "perms",
                channel.permissions()
                    .toMasks());
            if (channel.type()
                .hasFilter()) {
                tag.setTag("filter", ConfigNbt.writeFilter(channel.filter()));
            }
            channels.appendTag(tag);
        }
        root.setTag("channels", channels);

        NBTTagList policies = new NBTTagList();
        for (Map.Entry<String, Integer> entry : hub.createPolicies()
            .entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("owner", entry.getKey());
            tag.setInteger("mask", entry.getValue());
            policies.appendTag(tag);
        }
        root.setTag("createPolicies", policies);

        NBTTagList playerTags = new NBTTagList();
        for (Map.Entry<String, Map<String, TreeSet<String>>> player : hub.tags()
            .all()
            .entrySet()) {
            for (Map.Entry<String, TreeSet<String>> channel : player.getValue()
                .entrySet()) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setString("player", player.getKey());
                tag.setString("ch", channel.getKey());
                NBTTagList names = new NBTTagList();
                for (String name : channel.getValue()) {
                    names.appendTag(new NBTTagString(name));
                }
                tag.setTag("tags", names);
                playerTags.appendTag(tag);
            }
        }
        root.setTag("playerTags", playerTags);

        List<Endpoint> sorted = new ArrayList<Endpoint>(hub.endpoints());
        Collections.sort(sorted, new Comparator<Endpoint>() {

            @Override
            public int compare(Endpoint a, Endpoint b) {
                return Long.compare(a.connectOrder(), b.connectOrder());
            }
        });
        NBTTagList endpoints = new NBTTagList();
        for (Endpoint endpoint : sorted) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("id", endpoint.endpointId());
            tag.setString("ch", endpoint.channelId());
            tag.setLong("ord", endpoint.connectOrder());
            tag.setTag("loc", ConfigNbt.writeLocation(endpoint.location()));
            tag.setString("con", endpoint.connector());
            tag.setString("mn", endpoint.machineName());
            tag.setString(
                "st",
                endpoint.state()
                    .name());
            NBTTagCompound configTag = new NBTTagCompound();
            ConfigNbt.write(endpoint.config(), configTag);
            tag.setTag("cfg", configTag);
            endpoints.appendTag(tag);
        }
        root.setTag("endpoints", endpoints);

        NBTTagList tombstones = new NBTTagList();
        for (Map.Entry<String, Tombstone> entry : hub.tombstones()
            .entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("id", entry.getKey());
            tag.setTag(
                "loc",
                ConfigNbt.writeLocation(
                    entry.getValue()
                        .location()));
            tag.setString(
                "r",
                entry.getValue()
                    .reason()
                    .key());
            tombstones.appendTag(tag);
        }
        root.setTag("tombstones", tombstones);
    }

    /** 把存档内容恢复进一个空的核心。无法识别的条目跳过。 */
    public static void read(NBTTagCompound root, ChannelHub hub) {
        int version = root.getInteger("version");
        if (version > VERSION) {
            WarpCovers.LOG.warn("折跃频道存档版本 {} 比当前支持的 {} 新，尽力读取", version, VERSION);
        }
        NBTTagList channels = root.getTagList("channels", TAG_COMPOUND);
        for (int i = 0; i < channels.tagCount(); i++) {
            NBTTagCompound tag = channels.getCompoundTagAt(i);
            ChannelType type = ChannelType.byKey(tag.getString("type"));
            String id = tag.getString("id");
            if (type == null || id.isEmpty()) {
                WarpCovers.LOG.warn("跳过无法识别的频道: {}", tag);
                continue;
            }
            int color = tag.hasKey("color") ? tag.getInteger("color") : ChannelColors.DEFAULT;
            hub.restoreChannel(
                id,
                type,
                tag.getString("name"),
                color,
                tag.getString("owner"),
                tag.getString("ownerName"),
                DistributionMode.byKey(tag.getString("distribution")),
                tag.hasKey("perms") ? ChannelPermissions.fromMasks(tag.getIntArray("perms")) : new ChannelPermissions(),
                tag.getString("note"),
                tag.hasKey("filter") ? ConfigNbt.readFilter(tag.getCompoundTag("filter")) : null);
        }
        NBTTagList policies = root.getTagList("createPolicies", TAG_COMPOUND);
        for (int i = 0; i < policies.tagCount(); i++) {
            NBTTagCompound tag = policies.getCompoundTagAt(i);
            hub.restoreCreatePolicy(tag.getString("owner"), tag.getInteger("mask"));
        }
        NBTTagList playerTags = root.getTagList("playerTags", TAG_COMPOUND);
        for (int i = 0; i < playerTags.tagCount(); i++) {
            NBTTagCompound tag = playerTags.getCompoundTagAt(i);
            String channelId = tag.getString("ch");
            if (hub.findChannel(channelId) == null) {
                continue;
            }
            NBTTagList names = tag.getTagList("tags", TAG_STRING);
            List<String> restored = new ArrayList<String>();
            for (int j = 0; j < names.tagCount(); j++) {
                restored.add(names.getStringTagAt(j));
            }
            hub.tags()
                .restore(tag.getString("player"), channelId, restored);
        }
        NBTTagList endpoints = root.getTagList("endpoints", TAG_COMPOUND);
        for (int i = 0; i < endpoints.tagCount(); i++) {
            NBTTagCompound tag = endpoints.getCompoundTagAt(i);
            EndpointConfig config = new EndpointConfig();
            if (tag.hasKey("cfg")) {
                ConfigNbt.read(tag.getCompoundTag("cfg"), config);
            }
            // 交互状态以 st 为准；旧存档没有 cfg 时也只有它
            config.setState(ConfigNbt.readState(tag.getString("st")));
            hub.restoreEndpoint(
                tag.getString("id"),
                tag.getString("ch"),
                tag.getLong("ord"),
                ConfigNbt.readLocation(tag.getCompoundTag("loc")),
                tag.getString("con"),
                tag.getString("mn"),
                config);
        }
        NBTTagList tombstones = root.getTagList("tombstones", TAG_COMPOUND);
        for (int i = 0; i < tombstones.tagCount(); i++) {
            NBTTagCompound tag = tombstones.getCompoundTagAt(i);
            DisconnectReason reason = DisconnectReason.byKey(tag.getString("r"));
            if (reason == null) {
                continue;
            }
            hub.restoreTombstone(
                tag.getString("id"),
                new Tombstone(ConfigNbt.readLocation(tag.getCompoundTag("loc")), reason));
        }
        hub.restoreNextConnectOrder(root.getLong("nextOrder"));
    }
}
