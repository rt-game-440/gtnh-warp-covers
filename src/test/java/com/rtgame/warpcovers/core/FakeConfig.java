package com.rtgame.warpcovers.core;

/** 假配置，字段可直接改。 */
final class FakeConfig implements ConfigPort {

    boolean fluidEnabled = true;
    long rateCap = 0;
    int defaultPeriod = 20;
    int minPeriod = 1;
    int maxChannels = 0;

    @Override
    public int maxChannelsPerOwnerPerType() {
        return maxChannels;
    }

    @Override
    public boolean isEnabled(ChannelType type) {
        return fluidEnabled;
    }

    @Override
    public long globalRateCap(ChannelType type) {
        return rateCap;
    }

    @Override
    public int defaultPeriodTicks() {
        return defaultPeriod;
    }

    @Override
    public int minPeriodTicks() {
        return minPeriod;
    }
}
