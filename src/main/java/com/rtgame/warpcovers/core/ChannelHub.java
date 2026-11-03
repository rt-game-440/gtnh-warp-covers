package com.rtgame.warpcovers.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 频道核心门面：频道表、连接端索引、墓碑，以及输入端驱动的直通传输。与 GT 无关，时间由调用方传入。
 *
 * <p>
 * 覆盖板数据是自身配置的权威，连接端索引是「是否仍连接」的权威。覆盖板加载时调用
 * {@link #reconcile} 对账；卸载时调用 {@link #unbind}；物品化（撬下、挖掉机器）时调用 {@link #disconnect}。
 * 任何需要持久化的变更都会通知 {@link #setDirtyListener 脏标记监听器}。
 */
public final class ChannelHub {

    private static final Runnable NO_LISTENER = new Runnable() {

        @Override
        public void run() {}
    };

    private final ConfigPort config;
    private final Map<String, Channel> channels = new LinkedHashMap<String, Channel>();
    private final Map<String, Endpoint> endpoints = new HashMap<String, Endpoint>();
    /** 未加载时被断开的连接端：等覆盖板下次加载时告诉它原因。 */
    private final Map<String, Tombstone> tombstones = new HashMap<String, Tombstone>();
    private long nextConnectOrder = 0;
    private Runnable dirtyListener = NO_LISTENER;

    public ChannelHub(ConfigPort config) {
        this.config = config;
    }

    public void setDirtyListener(Runnable listener) {
        this.dirtyListener = listener == null ? NO_LISTENER : listener;
    }

    private void markDirty() {
        dirtyListener.run();
    }

    // ---- 频道 ----

    public Channel createChannel(ChannelType type, String name) {
        String id = UUID.randomUUID()
            .toString();
        Channel channel = new Channel(id, type, name);
        channels.put(id, channel);
        markDirty();
        return channel;
    }

    public Channel findChannel(String channelId) {
        return channels.get(channelId);
    }

    public Collection<Channel> channels() {
        return Collections.unmodifiableCollection(channels.values());
    }

    /** 删除频道：已加载的连接端立即收到断开通知；未加载的在下次加载对账时发现频道已删除。 */
    public void deleteChannel(String channelId) {
        Channel channel = channels.remove(channelId);
        if (channel == null) {
            return;
        }
        for (Endpoint endpoint : new ArrayList<Endpoint>(channel.endpoints())) {
            endpoints.remove(endpoint.endpointId());
            EndpointHost host = endpoint.host();
            if (host != null) {
                host.onDisconnected(DisconnectReason.CHANNEL_DELETED);
            }
        }
        markDirty();
    }

    // ---- 连接端 ----

    /**
     * 把连接端接入频道，排在连接顺序末尾。已在索引里的同 ID 连接端先断开。
     *
     * @param port 机器端口；覆盖板未加载时可为 null
     * @param host 覆盖板回调；可为 null
     */
    public Endpoint connect(String channelId, String endpointId, Location location, String connector,
        EndpointConfig endpointConfig, MachinePort port, EndpointHost host) {
        Channel channel = channels.get(channelId);
        if (channel == null) {
            throw new IllegalArgumentException("频道不存在: " + channelId);
        }
        removeEntry(endpointId);
        tombstones.remove(endpointId);
        Endpoint endpoint = new Endpoint(
            endpointId,
            channelId,
            nextConnectOrder++,
            location,
            connector,
            endpointConfig);
        endpoint.bind(port, host);
        endpoints.put(endpointId, endpoint);
        channel.addEndpoint(endpoint);
        markDirty();
        return endpoint;
    }

    /** 从索引移除连接端：玩家主动断开、覆盖板撬下或机器被挖掉（物品化）时调用。 */
    public void disconnect(String endpointId) {
        if (removeEntry(endpointId)) {
            markDirty();
        }
    }

    /**
     * 以某个原因断开连接端（权限丧失等）。已加载的当场通知覆盖板；未加载的留下墓碑，下次加载时通知。
     */
    public void disconnect(String endpointId, DisconnectReason reason) {
        Endpoint endpoint = endpoints.get(endpointId);
        if (endpoint == null) {
            return;
        }
        removeEntry(endpointId);
        EndpointHost host = endpoint.host();
        if (endpoint.isLoaded() && host != null) {
            host.onDisconnected(reason);
        } else {
            tombstones.put(endpointId, new Tombstone(endpoint.location(), reason));
        }
        markDirty();
    }

    private boolean removeEntry(String endpointId) {
        Endpoint existing = endpoints.remove(endpointId);
        if (existing == null) {
            return false;
        }
        Channel channel = channels.get(existing.channelId());
        if (channel != null) {
            channel.removeEndpoint(existing);
        }
        return true;
    }

    public Endpoint findEndpoint(String endpointId) {
        return endpoints.get(endpointId);
    }

    /** 该连接端是否仍在索引里、并且由这个覆盖板持有（已加载）。 */
    public boolean isBoundTo(String endpointId, EndpointHost host) {
        Endpoint endpoint = endpoints.get(endpointId);
        return endpoint != null && endpoint.host() == host && endpoint.port() != null;
    }

    public Collection<Endpoint> endpoints() {
        return Collections.unmodifiableCollection(endpoints.values());
    }

    public Map<String, Tombstone> tombstones() {
        return Collections.unmodifiableMap(tombstones);
    }

    /** 覆盖板改了自己的配置（共享对象已经变了），只需要标脏以便持久化交互状态。 */
    public void configChanged(String endpointId) {
        if (endpoints.containsKey(endpointId)) {
            markDirty();
        }
    }

    /**
     * 覆盖板加载（或从物品贴上）时与索引对账。
     *
     * <ul>
     * <li>覆盖板没连频道 → {@link ReconcileResult.Kind#NOT_CONNECTED}，顺手清掉同 ID 的残留。</li>
     * <li>有墓碑 → {@link ReconcileResult.Kind#DISCONNECTED}（带原因），墓碑消费掉。</li>
     * <li>索引里有、频道一致 → {@link ReconcileResult.Kind#CONNECTED}：覆盖板配置同步进索引，绑定端口，刷新位置与机器名。
     * 若同 ID 连接端正在别处加载着（覆盖板被复制），本覆盖板按重贴处理。</li>
     * <li>索引里没有、频道仍在 → {@link ReconcileResult.Kind#REATTACH}：调用方分配新连接端 ID 后 {@link #connect}。</li>
     * <li>索引里没有、频道已删除 → {@link ReconcileResult.Kind#DISCONNECTED}（频道已删除）。</li>
     * </ul>
     */
    public ReconcileResult reconcile(String endpointId, String channelId, Location location,
        EndpointConfig endpointConfig, MachinePort port, EndpointHost host) {
        boolean hasEndpointId = endpointId != null && !endpointId.isEmpty();
        if (channelId == null || channelId.isEmpty()) {
            if (hasEndpointId && (removeEntry(endpointId) | tombstones.remove(endpointId) != null)) {
                markDirty();
            }
            return ReconcileResult.notConnected();
        }
        if (hasEndpointId) {
            Tombstone tombstone = tombstones.remove(endpointId);
            if (tombstone != null) {
                removeEntry(endpointId);
                markDirty();
                return ReconcileResult.disconnected(tombstone.reason());
            }
            Endpoint existing = endpoints.get(endpointId);
            if (existing != null) {
                boolean duplicate = existing.isLoaded() && existing.host() != host
                    && !existing.location()
                        .equals(location);
                if (existing.channelId()
                    .equals(channelId) && !duplicate) {
                    existing.setConfig(endpointConfig);
                    existing.setLocation(location);
                    existing.bind(port, host);
                    markDirty();
                    return ReconcileResult.connected();
                }
                if (!duplicate) {
                    // 索引与覆盖板记的频道不一致：以索引为准视为已断开，再按重贴处理
                    removeEntry(endpointId);
                    markDirty();
                }
            }
        }
        if (!channels.containsKey(channelId)) {
            return ReconcileResult.disconnected(DisconnectReason.CHANNEL_DELETED);
        }
        return ReconcileResult.reattach();
    }

    /** 覆盖板卸载：连接端仍在频道上，但不再参与传输。只解绑仍由该覆盖板持有的连接端。 */
    public void unbind(String endpointId, EndpointHost host) {
        Endpoint endpoint = endpoints.get(endpointId);
        if (endpoint != null && (host == null || endpoint.host() == host)) {
            endpoint.unbind();
        }
    }

    /**
     * 区块加载后清理：该区块里找不到对应覆盖板的索引条目和墓碑都删掉。
     *
     * @return 删掉的条目数
     */
    public int cleanChunk(int dimension, int chunkX, int chunkZ, EndpointPresence presence) {
        int removed = 0;
        for (Endpoint endpoint : new ArrayList<Endpoint>(endpoints.values())) {
            if (endpoint.isLoaded() || !endpoint.location()
                .inChunk(dimension, chunkX, chunkZ)) {
                continue;
            }
            if (presence.check(endpoint.endpointId(), endpoint.location()) == EndpointPresence.Presence.ABSENT) {
                removeEntry(endpoint.endpointId());
                removed++;
            }
        }
        Iterator<Map.Entry<String, Tombstone>> it = tombstones.entrySet()
            .iterator();
        while (it.hasNext()) {
            Map.Entry<String, Tombstone> entry = it.next();
            Location location = entry.getValue()
                .location();
            if (location.inChunk(dimension, chunkX, chunkZ)
                && presence.check(entry.getKey(), location) == EndpointPresence.Presence.ABSENT) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            markDirty();
        }
        return removed;
    }

    // ---- 持久化恢复（只供存档读取使用） ----

    public void restoreChannel(String id, ChannelType type, String name) {
        channels.put(id, new Channel(id, type, name));
    }

    /** 恢复一条索引；频道不存在时丢弃。调用前须先恢复频道。 */
    public void restoreEndpoint(String endpointId, String channelId, long connectOrder, Location location,
        String connector, String machineName, InteractionState state) {
        Channel channel = channels.get(channelId);
        if (channel == null) {
            return;
        }
        EndpointConfig restored = new EndpointConfig();
        restored.setState(state);
        Endpoint endpoint = new Endpoint(endpointId, channelId, connectOrder, location, connector, restored);
        endpoint.setMachineName(machineName);
        endpoints.put(endpointId, endpoint);
        channel.addEndpoint(endpoint);
        nextConnectOrder = Math.max(nextConnectOrder, connectOrder + 1);
    }

    public void restoreTombstone(String endpointId, Tombstone tombstone) {
        tombstones.put(endpointId, tombstone);
    }

    /** 下一个连接顺序号（持久化用）。 */
    public long nextConnectOrder() {
        return nextConnectOrder;
    }

    public void restoreNextConnectOrder(long value) {
        nextConnectOrder = Math.max(nextConnectOrder, value);
    }

    // ---- 传输驱动 ----

    /**
     * 输入端的运行周期到期时由调用方调用。没到期、不是输入状态、本端或类型不可用时什么都不做。
     *
     * @param now 当前世界时间（tick），由调用方提供
     */
    public TransferResult driveInput(String endpointId, long now) {
        Endpoint input = endpoints.get(endpointId);
        if (input == null || input.state() != InteractionState.INPUT || !input.isLoaded()) {
            return TransferResult.NONE;
        }
        // 调用方的时钟回退（机器重载后计时器归零）：旧的到期时间已无意义，否则会长时间停转
        if (now < input.lastSeenTime()) {
            input.setNextRunTime(Long.MIN_VALUE);
        }
        input.setLastSeenTime(now);
        Channel channel = channels.get(input.channelId());
        if (channel == null || !config.isEnabled(channel.type()) || now < input.nextRunTime()) {
            return TransferResult.NONE;
        }
        input.setNextRunTime(now + Math.max(config.minPeriodTicks(), config.defaultPeriodTicks()));

        List<Endpoint> outputs = collectOutputs(channel, input);
        if (outputs.isEmpty()) {
            return TransferResult.NONE;
        }
        long cap = config.globalRateCap(channel.type());
        long quota = cap <= 0 ? Long.MAX_VALUE : cap;
        long transferred = 0;
        long lost = 0;
        for (ObjectStack offered : input.port()
            .extractable()) {
            if (quota <= 0) {
                break;
            }
            if (offered.amount() <= 0 || !input.filter()
                .allows(offered.key())) {
                continue;
            }
            TransferResult moved = moveKey(input, outputs, offered.key(), Math.min(offered.amount(), quota));
            transferred += moved.transferred();
            lost += moved.lost();
            quota -= moved.transferred();
        }
        return new TransferResult(transferred, lost);
    }

    /** 同一频道里已加载、状态为输出、且和输入端不在同一方块的连接端，按连接顺序。 */
    private List<Endpoint> collectOutputs(Channel channel, Endpoint input) {
        String inputBlock = input.port()
            .blockId();
        List<Endpoint> outputs = new ArrayList<Endpoint>();
        for (Endpoint candidate : channel.endpoints()) {
            if (candidate.state() == InteractionState.OUTPUT && candidate.isLoaded()
                && !inputBlock.equals(
                    candidate.port()
                        .blockId())) {
                outputs.add(candidate);
            }
        }
        return outputs;
    }

    /** 把一种物体依次送给各输出端。返回实际搬运量与丢失量。先模拟、再实际抽取、再插入。 */
    private TransferResult moveKey(Endpoint input, List<Endpoint> outputs, String key, long want) {
        long remaining = want;
        long moved = 0;
        long lost = 0;
        for (Endpoint output : outputs) {
            if (remaining <= 0) {
                break;
            }
            if (!output.filter()
                .allows(key)) {
                continue;
            }
            long accept = output.port()
                .insert(key, remaining, true);
            if (accept <= 0) {
                continue;
            }
            long taken = input.port()
                .extract(key, Math.min(accept, remaining), false);
            if (taken <= 0) {
                break;
            }
            long inserted = Math.min(
                taken,
                output.port()
                    .insert(key, taken, false));
            if (inserted < taken) {
                lost += giveBack(input, outputs, output, key, taken - inserted);
            }
            moved += inserted;
            remaining -= inserted;
        }
        return new TransferResult(moved, lost);
    }

    /** 插入不足时把余量退回：先退给来源，来源不收再试其他输出端。返回仍无处可退的量。 */
    private long giveBack(Endpoint input, List<Endpoint> outputs, Endpoint refused, String key, long leftover) {
        long rest = leftover - Math.min(
            leftover,
            input.port()
                .insert(key, leftover, false));
        for (Endpoint other : outputs) {
            if (rest <= 0) {
                break;
            }
            if (other == refused || !other.filter()
                .allows(key)) {
                continue;
            }
            rest -= Math.min(
                rest,
                other.port()
                    .insert(key, rest, false));
        }
        return rest;
    }
}
