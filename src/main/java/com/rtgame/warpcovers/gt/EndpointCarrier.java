package com.rtgame.warpcovers.gt;

/** 携带连接端的覆盖板：区块加载清理时据此判断索引条目在世界里是否还有对应的覆盖板。 */
public interface EndpointCarrier {

    boolean carriesEndpoint(String endpointId);
}
