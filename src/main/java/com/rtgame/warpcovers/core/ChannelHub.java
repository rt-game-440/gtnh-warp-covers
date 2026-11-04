package com.rtgame.warpcovers.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
    /** 轮询指针，键为「频道 ID#优先级」；只在内存里，重启后从头开始。 */
    private final Map<String, Long> roundRobinPointers = new HashMap<String, Long>();
    private Runnable dirtyListener = NO_LISTENER;
    private final IdentityPort identity;
    private final RecentTransfers recentTransfers = new RecentTransfers();
    private final PrivateTags tags = new PrivateTags();
    /** 各归属者哪些身份可以新增频道（{@link Identity} 的位掩码）；没有记录时取默认值。 */
    private final Map<String, Integer> createPolicies = new HashMap<String, Integer>();

    private static final int DEFAULT_CREATE_POLICY = (1 << Identity.MANAGER.ordinal())
        | (1 << Identity.MEMBER.ordinal());

    public ChannelHub(ConfigPort config) {
        this(config, PersonalIdentity.INSTANCE);
    }

    public ChannelHub(ConfigPort config, IdentityPort identity) {
        this.config = config;
        this.identity = identity;
    }

    public IdentityPort identity() {
        return identity;
    }

    /** 各频道最近传输过的物体种类（只在内存里）。 */
    public RecentTransfers recentTransfers() {
        return recentTransfers;
    }

    /** 玩家的私人标签。 */
    public PrivateTags tags() {
        return tags;
    }

    /** @return 没有加入的原因（语言键后缀）；成功时为 null */
    public String addTag(String playerId, String channelId, String tag) {
        String failure = tags.add(playerId, channelId, tag);
        if (failure == null) {
            markDirty();
        }
        return failure;
    }

    public void removeTag(String playerId, String channelId, String tag) {
        if (tags.remove(playerId, channelId, tag)) {
            markDirty();
        }
    }

    public void setDirtyListener(Runnable listener) {
        this.dirtyListener = listener == null ? NO_LISTENER : listener;
    }

    private void markDirty() {
        dirtyListener.run();
    }

    // ---- 频道 ----

    /** 频道名最长字符数。 */
    public static final int MAX_NAME_LENGTH = 32;

    /** 不做任何校验地新建频道（无归属者、默认颜色），供自检与测试使用。 */
    public Channel createChannel(ChannelType type, String name) {
        return addChannel(new Channel(newId(), type, name));
    }

    /**
     * 玩家新增频道：名字去掉首尾空白后不能为空、不能超长，同一归属者下同类型不能重名。
     *
     * @param color 0xRRGGBB
     */
    public ChannelCreation createChannel(ChannelType type, String name, String ownerId, String ownerName, int color) {
        String trimmed = name == null ? "" : name.trim();
        CreateFailure failure = validateName(type, ownerId, trimmed);
        if (failure != null) {
            return ChannelCreation.failure(failure);
        }
        return ChannelCreation.success(addChannel(new Channel(newId(), type, trimmed, color, ownerId, ownerName)));
    }

    /** 新频道名的校验结果；可以使用时返回 null。名字需事先去掉首尾空白。 */
    public CreateFailure validateName(ChannelType type, String ownerId, String trimmedName) {
        if (trimmedName.isEmpty()) {
            return CreateFailure.EMPTY_NAME;
        }
        if (trimmedName.length() > MAX_NAME_LENGTH) {
            return CreateFailure.NAME_TOO_LONG;
        }
        if (isNameTaken(type, ownerId, trimmedName)) {
            return CreateFailure.DUPLICATE_NAME;
        }
        return null;
    }

    /** 同一归属者下是否已有同类型同名频道。 */
    public boolean isNameTaken(ChannelType type, String ownerId, String name) {
        String owner = ownerId == null ? "" : ownerId;
        for (Channel channel : channels.values()) {
            if (channel.type() == type && channel.ownerId()
                .equals(owner)
                && channel.name()
                    .equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static String newId() {
        return UUID.randomUUID()
            .toString();
    }

    private Channel addChannel(Channel channel) {
        channels.put(channel.id(), channel);
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
        recentTransfers.forget(channelId);
        tags.forgetChannel(channelId);
        Iterator<String> pointers = roundRobinPointers.keySet()
            .iterator();
        while (pointers.hasNext()) {
            if (pointers.next()
                .startsWith(channelId + "#")) {
                pointers.remove();
            }
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

    /**
     * 修改频道的分发方式（权限由调用方检查）。频道不存在返回 false。
     */
    public boolean setDistribution(String channelId, DistributionMode mode) {
        Channel channel = channels.get(channelId);
        if (channel == null || mode == null) {
            return false;
        }
        if (channel.distribution() != mode) {
            channel.setDistribution(mode);
            markDirty();
        }
        return true;
    }

    /**
     * 改名（权限由调用方检查）：覆盖板只记内部 ID，不用碰任何覆盖板（ADR-0002）。名字需事先去掉首尾空白。
     *
     * @return 名字不可用的原因；成功时为 null
     */
    public CreateFailure renameChannel(String channelId, String trimmedName) {
        Channel channel = channels.get(channelId);
        if (channel == null || channel.name()
            .equals(trimmedName)) {
            return null;
        }
        CreateFailure failure = validateName(channel.type(), channel.ownerId(), trimmedName);
        if (failure != null) {
            return failure;
        }
        channel.setName(trimmedName);
        markDirty();
        notifyEndpoints(channel);
        return null;
    }

    public void setNote(String channelId, String note) {
        Channel channel = channels.get(channelId);
        if (channel != null && !channel.note()
            .equals(note)) {
            channel.setNote(note);
            markDirty();
        }
    }

    /** 改颜色（权限由调用方检查）：通知已加载的连接端同步外观。 */
    public void setColor(String channelId, int color) {
        Channel channel = channels.get(channelId);
        if (channel != null && channel.color() != (color & 0xFFFFFF)) {
            channel.setColor(color);
            markDirty();
            notifyEndpoints(channel);
        }
    }

    // ---- 过滤名单（权限由调用方检查；名单属于频道，见 ADR-0004） ----

    /** 切换黑白名单。频道不存在或没有名单（能量）返回 false。 */
    public boolean setFilterWhitelist(String channelId, boolean whitelist) {
        Channel channel = filterChannel(channelId);
        if (channel == null) {
            return false;
        }
        if (channel.filter()
            .isWhitelist() != whitelist) {
            channel.filter()
                .setWhitelist(whitelist);
            markDirty();
        }
        return true;
    }

    public boolean setFilterMatchNbt(String channelId, boolean matchNbt) {
        Channel channel = filterChannel(channelId);
        if (channel == null) {
            return false;
        }
        if (channel.filter()
            .matchNbt() != matchNbt) {
            channel.filter()
                .setMatchNbt(matchNbt);
            markDirty();
        }
        return true;
    }

    /** @return 实际加入（原来不在名单里） */
    public boolean addFilterKey(String channelId, String key) {
        Channel channel = filterChannel(channelId);
        if (channel == null || key == null || key.isEmpty()) {
            return false;
        }
        boolean added = channel.filter()
            .add(key);
        if (added) {
            markDirty();
        }
        return added;
    }

    public boolean removeFilterKey(String channelId, String key) {
        Channel channel = filterChannel(channelId);
        if (channel == null) {
            return false;
        }
        boolean removed = channel.filter()
            .remove(key);
        if (removed) {
            markDirty();
        }
        return removed;
    }

    public void clearFilter(String channelId) {
        Channel channel = filterChannel(channelId);
        if (channel != null && !channel.filter()
            .keys()
            .isEmpty()) {
            channel.filter()
                .clear();
            markDirty();
        }
    }

    private Channel filterChannel(String channelId) {
        Channel channel = channels.get(channelId);
        return channel != null && channel.type()
            .hasFilter() ? channel : null;
    }

    private static void notifyEndpoints(Channel channel) {
        for (Endpoint endpoint : channel.endpoints()) {
            EndpointHost host = endpoint.host();
            if (host != null) {
                host.onEndpointChanged();
            }
        }
    }

    // ---- 权限 ----

    /** 玩家相对于频道归属者的身份。没有归属者的频道（测试与自检直接建的）所有人都是管理者。 */
    public Identity identityOf(String playerId, Channel channel) {
        return channel.ownerId()
            .isEmpty() ? Identity.MANAGER : identity.identityOf(playerId, channel.ownerId());
    }

    public boolean hasPermission(String playerId, Channel channel, Permission permission) {
        return channel.permissions()
            .allows(identityOf(playerId, channel), permission);
    }

    /**
     * 该玩家现在能不能新增这种类型的频道（归属者按 {@link IdentityPort#ownerFor} 决定）。
     *
     * @return 不能时的原因；能时为 null
     */
    public CreateFailure canCreate(String playerId, ChannelType type) {
        String ownerId = identity.ownerFor(playerId);
        Identity who = identity.identityOf(playerId, ownerId);
        if (!isCreateAllowed(ownerId, who)) {
            return CreateFailure.NO_PERMISSION;
        }
        int limit = config.maxChannelsPerOwnerPerType();
        if (limit > 0 && countChannels(ownerId, type) >= limit) {
            return CreateFailure.LIMIT_REACHED;
        }
        return null;
    }

    private int countChannels(String ownerId, ChannelType type) {
        int count = 0;
        for (Channel channel : channels.values()) {
            if (channel.type() == type && channel.ownerId()
                .equals(ownerId)) {
                count++;
            }
        }
        return count;
    }

    public boolean isCreateAllowed(String ownerId, Identity who) {
        return (createPolicyMask(ownerId) & (1 << who.ordinal())) != 0;
    }

    /** 可以新增频道的身份（{@link Identity} 的位掩码）。 */
    public int createPolicyMask(String ownerId) {
        Integer policy = createPolicies.get(ownerId);
        return policy == null ? DEFAULT_CREATE_POLICY : policy;
    }

    /** 修改归属者级的「新增频道」（权限由调用方检查）。 */
    public void setCreateAllowed(String ownerId, Identity who, boolean allowed) {
        Integer policy = createPolicies.get(ownerId);
        int mask = policy == null ? DEFAULT_CREATE_POLICY : policy;
        mask = allowed ? mask | (1 << who.ordinal()) : mask & ~(1 << who.ordinal());
        createPolicies.put(ownerId, mask);
        markDirty();
    }

    /** 持久化用：归属者 → 可新增频道的身份位掩码。 */
    public Map<String, Integer> createPolicies() {
        return Collections.unmodifiableMap(createPolicies);
    }

    public void restoreCreatePolicy(String ownerId, int mask) {
        createPolicies.put(ownerId, mask);
    }

    /** 修改频道的一项权限（权限由调用方检查），失权的连接端立即断开。 */
    public boolean setPermission(String channelId, Identity who, Permission permission, boolean allowed) {
        Channel channel = channels.get(channelId);
        if (channel == null) {
            return false;
        }
        channel.permissions()
            .set(who, permission, allowed);
        markDirty();
        reevaluateConnections();
        return true;
    }

    /** 切换陌生人的私人/公开预设（权限由调用方检查），失权的连接端立即断开。 */
    public boolean setPublic(String channelId, boolean isPublic) {
        Channel channel = channels.get(channelId);
        if (channel == null) {
            return false;
        }
        channel.permissions()
            .applyStrangerPreset(isPublic);
        markDirty();
        reevaluateConnections();
        return true;
    }

    /**
     * 连接者失去「连接」权限的连接端一律断开：已加载的当场更新，未加载的留下墓碑、下次加载时同步。
     * 身份数据不可用时什么都不做。
     *
     * @return 断开的连接端数量
     */
    public int reevaluateConnections() {
        if (!identity.isReady()) {
            return 0;
        }
        int removed = 0;
        for (Channel channel : channels.values()) {
            for (Endpoint endpoint : new ArrayList<Endpoint>(channel.endpoints())) {
                if (!hasPermission(endpoint.connector(), channel, Permission.CONNECT)) {
                    disconnect(endpoint.endpointId(), DisconnectReason.PERMISSION_LOST);
                    removed++;
                }
            }
        }
        return removed;
    }

    /**
     * 把一个归属者名下的频道全部转给另一个归属者（队伍解散时用）。同类型重名时频道名后面自动加数字后缀。
     * 不复查连接端：调用方在身份数据更新之后再调 {@link #reevaluateConnections}。
     *
     * @return 转移的频道数
     */
    public int transferOwner(String fromOwnerId, String toOwnerId, String toOwnerName) {
        int moved = 0;
        for (Channel channel : channels.values()) {
            if (!channel.ownerId()
                .equals(fromOwnerId)) {
                continue;
            }
            channel.setName(uniqueName(channel.type(), toOwnerId, channel.name()));
            channel.setOwner(toOwnerId, toOwnerName);
            moved++;
        }
        createPolicies.remove(fromOwnerId);
        if (moved > 0) {
            markDirty();
        }
        return moved;
    }

    private String uniqueName(ChannelType type, String ownerId, String name) {
        if (!isNameTaken(type, ownerId, name)) {
            return name;
        }
        for (int n = 2;; n++) {
            String suffix = " " + n;
            int room = MAX_NAME_LENGTH - suffix.length();
            String candidate = (name.length() > room ? name.substring(0, room) : name) + suffix;
            if (!isNameTaken(type, ownerId, candidate)) {
                return candidate;
            }
        }
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

    /** 连接端被管理器远程改了配置：操作者成为新的连接者，并通知覆盖板存盘、同步外观。 */
    public void remoteConfigChanged(String endpointId, String operator) {
        Endpoint endpoint = endpoints.get(endpointId);
        if (endpoint == null) {
            return;
        }
        endpoint.setConnector(operator);
        markDirty();
        EndpointHost host = endpoint.host();
        if (host != null) {
            host.onEndpointChanged();
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

    public void restoreChannel(String id, ChannelType type, String name, int color, String ownerId, String ownerName) {
        restoreChannel(id, type, name, color, ownerId, ownerName, DistributionMode.ROUND_ROBIN);
    }

    public void restoreChannel(String id, ChannelType type, String name, int color, String ownerId, String ownerName,
        DistributionMode distribution) {
        restoreChannel(id, type, name, color, ownerId, ownerName, distribution, new ChannelPermissions(), "");
    }

    public void restoreChannel(String id, ChannelType type, String name, int color, String ownerId, String ownerName,
        DistributionMode distribution, ChannelPermissions permissions, String note) {
        restoreChannel(id, type, name, color, ownerId, ownerName, distribution, permissions, note, null);
    }

    /** @param filter 频道的过滤名单；旧存档没有时传 null，读成默认名单（空黑名单） */
    public void restoreChannel(String id, ChannelType type, String name, int color, String ownerId, String ownerName,
        DistributionMode distribution, ChannelPermissions permissions, String note, FilterList filter) {
        Channel channel = new Channel(id, type, name, color, ownerId, ownerName);
        channel.setDistribution(distribution);
        channel.setPermissions(permissions);
        channel.setNote(note);
        channel.setFilter(filter);
        channels.put(id, channel);
    }

    /** 恢复一条索引；频道不存在时丢弃。调用前须先恢复频道。 */
    public void restoreEndpoint(String endpointId, String channelId, long connectOrder, Location location,
        String connector, String machineName, InteractionState state) {
        EndpointConfig restored = new EndpointConfig();
        restored.setState(state);
        restoreEndpoint(endpointId, channelId, connectOrder, location, connector, machineName, restored);
    }

    /**
     * 恢复一条索引，带上保存时的配置副本。覆盖板是配置的权威，加载时会用自己的配置替换；
     * 在那之前这份副本让未加载连接端也保有交互状态、优先级等。
     */
    public void restoreEndpoint(String endpointId, String channelId, long connectOrder, Location location,
        String connector, String machineName, EndpointConfig restored) {
        Channel channel = channels.get(channelId);
        if (channel == null) {
            return;
        }
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
     * 周期按上一次运行时间计算，所以修改周期或调高服务器最短周期都会立即生效。
     *
     * @param now 当前时间（tick），由调用方提供；所有连接端必须用同一个时钟，输出端的接收窗口才有意义
     */
    public TransferResult driveInput(String endpointId, long now) {
        Endpoint input = endpoints.get(endpointId);
        if (input == null || input.state() != InteractionState.INPUT || !input.isLoaded()) {
            return TransferResult.NONE;
        }
        // 调用方的时钟回退（服务器重启后计数归零）：旧的运行时间已无意义，否则会长时间停转
        if (now < input.lastSeenTime()) {
            input.setLastRunTime(Long.MIN_VALUE);
        }
        input.setLastSeenTime(now);
        Channel channel = channels.get(input.channelId());
        if (channel == null || !config.isEnabled(channel.type())) {
            return TransferResult.NONE;
        }
        if (input.lastRunTime() != Long.MIN_VALUE && now - input.lastRunTime() < effectivePeriod(input.config())) {
            return TransferResult.NONE;
        }
        input.setLastRunTime(now);

        List<Endpoint> outputs = collectOutputs(channel, input);
        if (outputs.isEmpty()) {
            EndpointStatus missing = peerAvailability(channel, input, InteractionState.OUTPUT);
            input.setStatus(missing == null ? EndpointStatus.NO_PEER : missing, now);
            return TransferResult.NONE;
        }
        Delivery delivery = new Delivery(input, channel, outputs, now);
        long quota = effectiveRate(channel.type(), input.config());
        long transferred = 0;
        long lost = 0;
        boolean anyOffered = false;
        boolean anyAllowed = false;
        for (ObjectStack offered : input.port()
            .extractable()) {
            if (quota <= 0) {
                break;
            }
            if (offered.amount() <= 0) {
                continue;
            }
            anyOffered = true;
            if (!channel.allows(offered.key())) {
                continue;
            }
            anyAllowed = true;
            TransferResult moved = delivery.moveKey(offered.key(), Math.min(offered.amount(), quota));
            transferred += moved.transferred();
            lost += moved.lost();
            quota -= moved.transferred();
            if (moved.transferred() > 0) {
                recentTransfers.record(channel.id(), offered.key());
            }
        }
        delivery.advancePointers();
        EndpointStatus status;
        if (transferred > 0) {
            status = EndpointStatus.TRANSFERRING;
        } else if (!anyOffered) {
            status = EndpointStatus.SOURCE_EMPTY;
        } else if (!anyAllowed) {
            status = EndpointStatus.FILTERED;
        } else if (delivery.windowLimited) {
            // 输出端本窗口的额度用完了，下个窗口还会继续收
            status = EndpointStatus.WAITING;
        } else {
            status = EndpointStatus.PEER_FULL;
        }
        input.setStatus(status, now);
        return new TransferResult(transferred, lost);
    }

    /** 该配置的实际运行周期（tick）。 */
    public long effectivePeriod(EndpointConfig endpointConfig) {
        return Periods.effectivePeriod(endpointConfig.period(), config);
    }

    /** 该配置的实际速率（每周期最多搬运多少）；{@link Long#MAX_VALUE} 表示不限。 */
    public long effectiveRate(ChannelType type, EndpointConfig endpointConfig) {
        return Periods.effectiveRate(endpointConfig.rate(), config.globalRateCap(type));
    }

    /**
     * 频道里有没有可用的对端（指定交互状态、不在同一方块）。有已加载的返回 null；只有未加载的返回
     * {@link EndpointStatus#PEER_UNLOADED}；一个都没有返回 {@link EndpointStatus#NO_PEER}。
     */
    private static EndpointStatus peerAvailability(Channel channel, Endpoint self, InteractionState peerState) {
        boolean anyUnloaded = false;
        for (Endpoint candidate : channel.endpoints()) {
            if (candidate == self || candidate.state() != peerState
                || candidate.location()
                    .sameBlock(self.location())) {
                continue;
            }
            if (candidate.isLoaded()) {
                return null;
            }
            anyUnloaded = true;
        }
        return anyUnloaded ? EndpointStatus.PEER_UNLOADED : EndpointStatus.NO_PEER;
    }

    /** 服务器是否启用了该类型。 */
    public boolean isEnabled(ChannelType type) {
        return config.isEnabled(type);
    }

    /**
     * 连接端当前状态（界面状态栏用）。输入端取最近一个周期的结果；输出端取最近被输入端访问的结果；
     * 结果太旧（超过两个周期）时为「等待中」。
     *
     * @param now 与 {@link #driveInput} 同一个时钟
     */
    public EndpointStatus statusOf(String endpointId, long now) {
        Endpoint endpoint = endpoints.get(endpointId);
        Channel channel = endpoint == null ? null : channels.get(endpoint.channelId());
        if (channel == null) {
            return EndpointStatus.UNCONNECTED;
        }
        if (!config.isEnabled(channel.type())) {
            return EndpointStatus.DISABLED;
        }
        if (endpoint.state() == InteractionState.EMPTY) {
            return EndpointStatus.IDLE;
        }
        InteractionState peerState = endpoint.state() == InteractionState.INPUT ? InteractionState.OUTPUT
            : InteractionState.INPUT;
        EndpointStatus missing = peerAvailability(channel, endpoint, peerState);
        if (missing != null) {
            return missing;
        }
        EndpointStatus last = endpoint.lastStatus();
        long age = now - endpoint.lastStatusTime();
        if (last != null && age >= 0 && age <= 2 * effectivePeriod(endpoint.config()) + 20) {
            return last;
        }
        return EndpointStatus.WAITING;
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

    /** 输入端一次运行的分发过程：优先级从高到低，同级内按频道的分发方式分配。 */
    private final class Delivery {

        private final Endpoint input;
        private final Channel channel;
        private final List<Endpoint> outputs;
        private final long now;
        /** 按优先级从高到低分组，组内保持连接顺序。 */
        private final TreeMap<Integer, List<Endpoint>> tiers = new TreeMap<Integer, List<Endpoint>>(
            Collections.<Integer>reverseOrder());
        /** 本次运行实际收到东西的优先级：只有它们的轮询指针前进。 */
        private final Set<Integer> served = new HashSet<Integer>();
        /** 本次运行有输出端因为窗口额度用完而没收。 */
        boolean windowLimited = false;

        Delivery(Endpoint input, Channel channel, List<Endpoint> outputs, long now) {
            this.input = input;
            this.channel = channel;
            this.outputs = outputs;
            this.now = now;
            for (Endpoint output : outputs) {
                int priority = output.config()
                    .priority();
                List<Endpoint> tier = tiers.get(priority);
                if (tier == null) {
                    tier = new ArrayList<Endpoint>();
                    tiers.put(priority, tier);
                }
                tier.add(output);
            }
        }

        /** 把一种物体分给各输出端。返回实际搬运量与丢失量。先模拟、再实际抽取、再插入。 */
        TransferResult moveKey(String key, long want) {
            long remaining = want;
            long moved = 0;
            long lost = 0;
            for (Map.Entry<Integer, List<Endpoint>> entry : tiers.entrySet()) {
                if (remaining <= 0) {
                    break;
                }
                List<Endpoint> order = distributionOrder(entry.getKey(), entry.getValue());
                long[] room = new long[order.size()];
                for (int i = 0; i < room.length; i++) {
                    room[i] = roomFor(order.get(i), key, remaining);
                }
                long[] shares = channel.distribution()
                    .allocate(remaining, room);
                for (int i = 0; i < shares.length; i++) {
                    if (shares[i] <= 0) {
                        continue;
                    }
                    Endpoint output = order.get(i);
                    long taken = input.port()
                        .extract(key, shares[i], false);
                    if (taken <= 0) {
                        return new TransferResult(moved, lost);
                    }
                    long inserted = Math.min(
                        taken,
                        output.port()
                            .insert(key, taken, false));
                    if (inserted < taken) {
                        lost += giveBack(input, outputs, output, key, taken - inserted);
                    }
                    output.addReceived(inserted);
                    output.noteReceived(inserted > 0 ? EndpointStatus.TRANSFERRING : EndpointStatus.SELF_FULL, now);
                    if (inserted > 0) {
                        served.add(entry.getKey());
                    }
                    moved += inserted;
                    remaining -= inserted;
                }
            }
            return new TransferResult(moved, lost);
        }

        /** 这个输出端这次最多收多少：自身窗口额度、机器模拟插入取小（名单在输入端抽取时已按频道名单过滤）。 */
        private long roomFor(Endpoint output, String key, long want) {
            long window = output
                .windowRemaining(now, effectiveRate(channel.type(), output.config()), effectivePeriod(output.config()));
            if (window <= 0) {
                windowLimited = true;
                return 0;
            }
            long limit = Math.min(want, window);
            long accept = output.port()
                .insert(key, limit, true);
            if (accept <= 0) {
                output.noteReceived(EndpointStatus.SELF_FULL, now);
                return 0;
            }
            return Math.min(accept, limit);
        }

        /** 轮询时从指针位置开始，顺序时就是连接顺序。 */
        private List<Endpoint> distributionOrder(int priority, List<Endpoint> tier) {
            if (channel.distribution() != DistributionMode.ROUND_ROBIN || tier.size() < 2) {
                return tier;
            }
            Long pointer = roundRobinPointers.get(channel.id() + "#" + priority);
            int start = pointer == null ? 0 : (int) (pointer % tier.size());
            List<Endpoint> order = new ArrayList<Endpoint>(tier.size());
            order.addAll(tier.subList(start, tier.size()));
            order.addAll(tier.subList(0, start));
            return order;
        }

        void advancePointers() {
            if (channel.distribution() != DistributionMode.ROUND_ROBIN) {
                return;
            }
            for (int priority : served) {
                String key = channel.id() + "#" + priority;
                Long pointer = roundRobinPointers.get(key);
                roundRobinPointers.put(key, pointer == null ? 1L : pointer + 1);
            }
        }
    }

    /** 插入不足时把余量退回：先退给来源，来源不收再试其他输出端。返回仍无处可退的量。 */
    private long giveBack(Endpoint input, List<Endpoint> outputs, Endpoint refused, String key, long leftover) {
        long rest = leftover - Math.min(
            leftover,
            input.port()
                .restore(key, leftover));
        for (Endpoint other : outputs) {
            if (rest <= 0) {
                break;
            }
            if (other == refused) {
                continue;
            }
            long rescued = Math.min(
                rest,
                other.port()
                    .insert(key, rest, false));
            other.addReceived(rescued);
            rest -= rescued;
        }
        return rest;
    }
}
