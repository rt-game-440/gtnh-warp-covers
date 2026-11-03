package com.rtgame.warpcovers.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 频道：只记录谁连在上面，本身不存储任何东西（见 ADR-0001）。 */
public final class Channel {

    private final String id;
    private final ChannelType type;
    private String name;
    /** 显示颜色，0xRRGGBB。 */
    private int color;
    /** 备注：任意展示文本。 */
    private String note = "";
    /** 归属者 ID（如 {@code player:<uuid>}），旧存档里为空。 */
    private String ownerId;
    /** 归属者显示名，只作展示缓存。 */
    private String ownerName;
    private DistributionMode distribution = DistributionMode.ROUND_ROBIN;
    private ChannelPermissions permissions = new ChannelPermissions();
    /** 按连接顺序号升序。 */
    private final List<Endpoint> endpoints = new ArrayList<Endpoint>();

    Channel(String id, ChannelType type, String name) {
        this(id, type, name, ChannelColors.DEFAULT, "", "");
    }

    Channel(String id, ChannelType type, String name, int color, String ownerId, String ownerName) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.color = color & 0xFFFFFF;
        this.ownerId = ownerId == null ? "" : ownerId;
        this.ownerName = ownerName == null ? "" : ownerName;
    }

    /** 内部 ID，不可变（ADR-0002）。 */
    public String id() {
        return id;
    }

    public ChannelType type() {
        return type;
    }

    /** 频道名，只是显示用的名称。 */
    public String name() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    public String note() {
        return note;
    }

    void setNote(String note) {
        this.note = note == null ? "" : note;
    }

    public int color() {
        return color;
    }

    void setColor(int color) {
        this.color = color & 0xFFFFFF;
    }

    public String ownerId() {
        return ownerId;
    }

    public String ownerName() {
        return ownerName;
    }

    void setOwner(String ownerId, String ownerName) {
        this.ownerId = ownerId == null ? "" : ownerId;
        this.ownerName = ownerName == null ? "" : ownerName;
    }

    /** 分发方式：同一输出优先级内各输出端如何分配。 */
    public DistributionMode distribution() {
        return distribution;
    }

    void setDistribution(DistributionMode distribution) {
        this.distribution = distribution == null ? DistributionMode.ROUND_ROBIN : distribution;
    }

    public ChannelPermissions permissions() {
        return permissions;
    }

    void setPermissions(ChannelPermissions permissions) {
        this.permissions = permissions == null ? new ChannelPermissions() : permissions;
    }

    /** 当前连在频道上的连接端，按连接顺序号升序。 */
    public List<Endpoint> endpoints() {
        return Collections.unmodifiableList(endpoints);
    }

    /** 按连接顺序号插入，保持升序。 */
    void addEndpoint(Endpoint endpoint) {
        int index = endpoints.size();
        while (index > 0 && endpoints.get(index - 1)
            .connectOrder() > endpoint.connectOrder()) {
            index--;
        }
        endpoints.add(index, endpoint);
    }

    void removeEndpoint(Endpoint endpoint) {
        endpoints.remove(endpoint);
    }
}
