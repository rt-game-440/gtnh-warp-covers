package com.rtgame.warpcovers.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** 每个频道最近传输过的物体种类：只存在内存里，按最近使用顺序保留并设上限，重启后清空。 */
public final class RecentTransfers {

    public static final int LIMIT = 16;

    /** 迭代顺序：最旧的在前。 */
    private final Map<String, LinkedHashSet<String>> byChannel = new HashMap<String, LinkedHashSet<String>>();

    void record(String channelId, String key) {
        LinkedHashSet<String> keys = byChannel.get(channelId);
        if (keys == null) {
            keys = new LinkedHashSet<String>();
            byChannel.put(channelId, keys);
        }
        keys.remove(key);
        keys.add(key);
        if (keys.size() > LIMIT) {
            keys.remove(
                keys.iterator()
                    .next());
        }
    }

    /** 该频道最近传输过的种类，最近的在前。 */
    public List<String> of(String channelId) {
        LinkedHashSet<String> keys = byChannel.get(channelId);
        if (keys == null) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<String>(keys);
        Collections.reverse(result);
        return result;
    }

    void forget(String channelId) {
        byChannel.remove(channelId);
    }
}
