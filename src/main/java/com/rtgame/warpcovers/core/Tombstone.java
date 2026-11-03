package com.rtgame.warpcovers.core;

/** 墓碑：未加载时被核心断开的连接端，等它下次加载时告诉它断开原因。 */
public final class Tombstone {

    private final Location location;
    private final DisconnectReason reason;

    public Tombstone(Location location, DisconnectReason reason) {
        this.location = location;
        this.reason = reason;
    }

    public Location location() {
        return location;
    }

    public DisconnectReason reason() {
        return reason;
    }
}
