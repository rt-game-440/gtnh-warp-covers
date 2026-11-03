package com.rtgame.warpcovers.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 频道：只记录谁连在上面，本身不存储任何东西（见 ADR-0001）。 */
public final class Channel {

    private final String id;
    private final ChannelType type;
    private String name;
    /** 按连接顺序号升序。 */
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

    void setName(String name) {
        this.name = name;
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
