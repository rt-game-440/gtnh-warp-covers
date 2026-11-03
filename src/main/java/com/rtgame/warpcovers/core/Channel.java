package com.rtgame.warpcovers.core;

import java.util.ArrayList;
import java.util.List;

/** 频道：只记录谁连在上面，本身不存储任何东西（见 ADR-0001）。 */
public final class Channel {

    private final String id;
    private final ChannelType type;
    private String name;
    private final List<Endpoint> endpoints = new ArrayList<Endpoint>();

    Channel(String id, ChannelType type, String name) {
        this.id = id;
        this.type = type;
        this.name = name;
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

    List<Endpoint> endpoints() {
        return endpoints;
    }
}
