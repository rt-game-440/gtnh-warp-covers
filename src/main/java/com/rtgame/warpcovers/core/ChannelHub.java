package com.rtgame.warpcovers.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 频道核心门面：与 GT 无关，机器与配置通过端口注入，时间由调用方传入。
 * 频道本身不存储任何东西：输入端的运行是「抽出 → 立刻插入」的直通。
 */
public final class ChannelHub {

    private final ConfigPort config;
    private final Map<String, Channel> channels = new HashMap<String, Channel>();
    private final Map<String, Endpoint> endpoints = new HashMap<String, Endpoint>();

    public ChannelHub(ConfigPort config) {
        this.config = config;
    }

    // ---- 频道 ----

    /** 创建频道，返回带有不可变内部 ID 的频道。 */
    public Channel createChannel(ChannelType type, String name) {
        Channel channel = new Channel(UUID.randomUUID().toString(), type, name);
        channels.put(channel.id(), channel);
        return channel;
    }

    public Channel findChannel(String channelId) {
        return channels.get(channelId);
    }

    // ---- 连接端 ----

    /**
     * 把一个覆盖板连到频道。新连接的交互状态默认为空；已经连着的先断开再连。
     *
     * @throws IllegalArgumentException 频道不存在
     */
    public Endpoint connect(String channelId, String endpointId, MachinePort port) {
        Channel channel = channels.get(channelId);
        if (channel == null) {
            throw new IllegalArgumentException("频道不存在: " + channelId);
        }
        disconnect(endpointId);
        Endpoint endpoint = new Endpoint(endpointId, channelId, port);
        channel.endpoints()
            .add(endpoint);
        endpoints.put(endpointId, endpoint);
        return endpoint;
    }

    /** 断开连接端；没连着则什么都不做。 */
    public void disconnect(String endpointId) {
        Endpoint endpoint = endpoints.remove(endpointId);
        if (endpoint == null) {
            return;
        }
        Channel channel = channels.get(endpoint.channelId());
        if (channel != null) {
            channel.endpoints()
                .remove(endpoint);
        }
    }

    public Endpoint findEndpoint(String endpointId) {
        return endpoints.get(endpointId);
    }

    public void setState(String endpointId, InteractionState state) {
        require(endpointId).setState(state);
    }

    public void setFilter(String endpointId, FilterList filter) {
        require(endpointId).setFilter(filter);
    }

    // ---- 加载 / 卸载 ----

    /** 连接端所在区块卸载：它不再参与传输。 */
    public void markUnloaded(String endpointId) {
        Endpoint endpoint = endpoints.get(endpointId);
        if (endpoint != null) {
            endpoint.setPort(null);
        }
    }

    /** 连接端所在区块重新加载。 */
    public void markLoaded(String endpointId, MachinePort port) {
        Endpoint endpoint = endpoints.get(endpointId);
        if (endpoint != null) {
            endpoint.setPort(port);
        }
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
            TransferResult moved = moveKey(input, outputs, offered.key(), (int) Math.min(offered.amount(), quota));
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
    private TransferResult moveKey(Endpoint input, List<Endpoint> outputs, String key, int want) {
        int remaining = want;
        int moved = 0;
        int lost = 0;
        for (Endpoint output : outputs) {
            if (remaining <= 0) {
                break;
            }
            if (!output.filter()
                .allows(key)) {
                continue;
            }
            int accept = output.port()
                .insert(key, remaining, true);
            if (accept <= 0) {
                continue;
            }
            int taken = input.port()
                .extract(key, accept, false);
            if (taken <= 0) {
                break;
            }
            int inserted = Math.min(
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
    private int giveBack(Endpoint input, List<Endpoint> outputs, Endpoint refused, String key, int leftover) {
        int rest = leftover - Math.min(
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

    private Endpoint require(String endpointId) {
        Endpoint endpoint = endpoints.get(endpointId);
        if (endpoint == null) {
            throw new IllegalArgumentException("连接端未连接: " + endpointId);
        }
        return endpoint;
    }
}
