package com.rtgame.warpcovers.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChannelHubTest {

    private FakeConfig config;
    private ChannelHub hub;
    private Channel channel;

    @BeforeEach
    void setUp() {
        config = new FakeConfig();
        hub = new ChannelHub(config);
        channel = hub.createChannel(ChannelType.FLUID, "测试频道");
    }

    /** 接入一台机器并设置状态与白名单（keys 为空则保持空白名单）。 */
    private void attach(String id, FakeMachine machine, InteractionState state, String... keys) {
        hub.connect(channel.id(), id, machine);
        hub.setState(id, state);
        FilterList filter = new FilterList();
        filter.replaceKeys(Arrays.asList(keys));
        hub.setFilter(id, filter);
    }

    private static void assertConserved(int expected, FakeMachine... machines) {
        int sum = 0;
        for (FakeMachine machine : machines) {
            sum += machine.total();
        }
        assertEquals(expected, sum, "总量必须守恒");
    }

    // ---- 频道与连接 ----

    @Test
    void 创建频道得到各不相同的内部ID() {
        Channel other = hub.createChannel(ChannelType.FLUID, "测试频道");
        assertNotEquals(channel.id(), other.id());
        assertEquals(channel, hub.findChannel(channel.id()));
    }

    @Test
    void 新连接的交互状态默认为空() {
        Endpoint endpoint = hub.connect(channel.id(), "a", new FakeMachine("A", 1000));
        assertEquals(InteractionState.EMPTY, endpoint.state());
    }

    @Test
    void 连接不存在的频道报错() {
        assertThrows(IllegalArgumentException.class, () -> hub.connect("nope", "a", new FakeMachine("A", 10)));
    }

    @Test
    void 断开后不再参与传输() {
        FakeMachine in = new FakeMachine("A", 1000).with("water", 500);
        FakeMachine out = new FakeMachine("B", 1000);
        attach("in", in, InteractionState.INPUT, "water");
        attach("out", out, InteractionState.OUTPUT, "water");
        hub.disconnect("out");
        assertNull(hub.findEndpoint("out"));
        assertEquals(0, hub.driveInput("in", 0).transferred());
        assertEquals(500, in.total());
    }

    // ---- 基本传输与守恒 ----

    @Test
    void 输入端把流体直通到输出端并守恒() {
        FakeMachine in = new FakeMachine("A", 1000).with("water", 400);
        FakeMachine out = new FakeMachine("B", 1000);
        attach("in", in, InteractionState.INPUT, "water");
        attach("out", out, InteractionState.OUTPUT, "water");
        TransferResult result = hub.driveInput("in", 0);
        assertEquals(400, result.transferred());
        assertEquals(0, result.lost());
        assertEquals(0, in.amountOf("water"));
        assertEquals(400, out.amountOf("water"));
        assertConserved(400, in, out);
    }

    @Test
    void 状态为空的端点不动作() {
        FakeMachine in = new FakeMachine("A", 1000).with("water", 400);
        FakeMachine out = new FakeMachine("B", 1000);
        attach("in", in, InteractionState.EMPTY, "water");
        attach("out", out, InteractionState.OUTPUT, "water");
        assertEquals(0, hub.driveInput("in", 0).transferred());
        assertEquals(400, in.total());
        assertConserved(400, in, out);
    }

    @Test
    void 没有输出端时不抽取() {
        FakeMachine in = new FakeMachine("A", 1000).with("water", 400);
        attach("in", in, InteractionState.INPUT, "water");
        assertEquals(0, hub.driveInput("in", 0).transferred());
        assertEquals(0, in.extractCalls);
        assertConserved(400, in);
    }

    @Test
    void 不同频道互不干扰() {
        Channel other = hub.createChannel(ChannelType.FLUID, "另一个");
        FakeMachine in = new FakeMachine("A", 1000).with("water", 400);
        FakeMachine outSame = new FakeMachine("B", 1000);
        FakeMachine outOther = new FakeMachine("C", 1000);
        attach("in", in, InteractionState.INPUT, "water");
        attach("outSame", outSame, InteractionState.OUTPUT, "water");
        hub.connect(other.id(), "outOther", outOther);
        hub.setState("outOther", InteractionState.OUTPUT);
        FilterList filter = new FilterList();
        filter.add("water");
        hub.setFilter("outOther", filter);
        hub.driveInput("in", 0);
        assertEquals(400, outSame.amountOf("water"));
        assertEquals(0, outOther.total());
        assertConserved(400, in, outSame, outOther);
    }

    @Test
    void 多个输出端按连接顺序依次装满() {
        FakeMachine in = new FakeMachine("A", 5000).with("water", 1000);
        FakeMachine first = new FakeMachine("B", 300);
        FakeMachine second = new FakeMachine("C", 5000);
        attach("in", in, InteractionState.INPUT, "water");
        attach("o1", first, InteractionState.OUTPUT, "water");
        attach("o2", second, InteractionState.OUTPUT, "water");
        assertEquals(1000, hub.driveInput("in", 0).transferred());
        assertEquals(300, first.total());
        assertEquals(700, second.total());
        assertConserved(1000, in, first, second);
    }

    // ---- 过滤名单 ----

    @Test
    void 新建过滤名单是空白名单什么都不传() {
        FakeMachine in = new FakeMachine("A", 1000).with("water", 400);
        FakeMachine out = new FakeMachine("B", 1000);
        attach("in", in, InteractionState.INPUT);
        attach("out", out, InteractionState.OUTPUT);
        FilterList fresh = new FilterList();
        assertTrue(fresh.isWhitelist());
        assertFalse(fresh.allows("water"));
        assertEquals(0, hub.driveInput("in", 0).transferred());
        assertConserved(400, in, out);
    }

    @Test
    void 连接端默认过滤名单是空白名单() {
        Endpoint endpoint = hub.connect(channel.id(), "a", new FakeMachine("A", 10));
        assertTrue(endpoint.filter().isWhitelist());
        assertTrue(endpoint.filter().keys().isEmpty());
    }

    @Test
    void 白名单只放行名单内的流体() {
        FakeMachine in = new FakeMachine("A", 5000).with("water", 300).with("lava", 200);
        FakeMachine out = new FakeMachine("B", 5000);
        attach("in", in, InteractionState.INPUT, "water");
        attach("out", out, InteractionState.OUTPUT, "water", "lava");
        hub.driveInput("in", 0);
        assertEquals(300, out.amountOf("water"));
        assertEquals(0, out.amountOf("lava"));
        assertEquals(200, in.amountOf("lava"));
        assertConserved(500, in, out);
    }

    @Test
    void 黑名单放行名单外的流体() {
        FakeMachine in = new FakeMachine("A", 5000).with("water", 300).with("lava", 200);
        FakeMachine out = new FakeMachine("B", 5000);
        attach("in", in, InteractionState.INPUT);
        attach("out", out, InteractionState.OUTPUT, "water", "lava");
        FilterList black = new FilterList();
        black.setWhitelist(false);
        black.add("lava");
        hub.setFilter("in", black);
        hub.driveInput("in", 0);
        assertEquals(300, out.amountOf("water"));
        assertEquals(200, in.amountOf("lava"));
        assertConserved(500, in, out);
    }

    @Test
    void 输出端的名单决定它接收什么() {
        FakeMachine in = new FakeMachine("A", 5000).with("water", 300).with("lava", 200);
        FakeMachine picky = new FakeMachine("B", 5000);
        FakeMachine open = new FakeMachine("C", 5000);
        attach("in", in, InteractionState.INPUT, "water", "lava");
        attach("picky", picky, InteractionState.OUTPUT, "water");
        attach("open", open, InteractionState.OUTPUT, "water", "lava");
        hub.driveInput("in", 0);
        assertEquals(300, picky.amountOf("water"));
        assertEquals(0, picky.amountOf("lava"));
        assertEquals(200, open.amountOf("lava"));
        assertConserved(500, in, picky, open);
    }

    // ---- 运行周期 ----

    @Test
    void 输入端按默认周期20tick运行() {
        FakeMachine in = new FakeMachine("A", 5000).with("water", 100);
        FakeMachine out = new FakeMachine("B", 5000);
        attach("in", in, InteractionState.INPUT, "water");
        attach("out", out, InteractionState.OUTPUT, "water");
        assertEquals(100, hub.driveInput("in", 100).transferred());
        in.with("water", 50);
        assertEquals(0, hub.driveInput("in", 101).transferred(), "周期未到不运行");
        assertEquals(0, hub.driveInput("in", 119).transferred(), "周期未到不运行");
        assertEquals(50, hub.driveInput("in", 120).transferred(), "满20tick再次运行");
        assertConserved(150, in, out);
    }

    @Test
    void 运行周期服从配置且不低于下限() {
        config.defaultPeriod = 5;
        config.minPeriod = 10;
        FakeMachine in = new FakeMachine("A", 5000).with("water", 100);
        FakeMachine out = new FakeMachine("B", 5000);
        attach("in", in, InteractionState.INPUT, "water");
        attach("out", out, InteractionState.OUTPUT, "water");
        hub.driveInput("in", 0);
        in.with("water", 50);
        assertEquals(0, hub.driveInput("in", 9).transferred());
        assertEquals(50, hub.driveInput("in", 10).transferred());
    }

    @Test
    void 调用方时钟回退后输入端仍按周期运行() {
        FakeMachine in = new FakeMachine("A", 5000).with("water", 100);
        FakeMachine out = new FakeMachine("B", 5000);
        attach("in", in, InteractionState.INPUT, "water");
        attach("out", out, InteractionState.OUTPUT, "water");
        assertEquals(100, hub.driveInput("in", 100000).transferred());
        // 机器重载，计时器从 0 重新开始
        in.with("water", 50);
        assertEquals(50, hub.driveInput("in", 3).transferred(), "时钟回退后应重新计周期");
        in.with("water", 10);
        assertEquals(0, hub.driveInput("in", 10).transferred(), "回退后仍遵守周期");
        assertEquals(10, hub.driveInput("in", 23).transferred());
        assertConserved(160, in, out);
    }

    // ---- 背压、自环、卸载 ----

    @Test
    void 输出端满了输入端停止抽取() {
        FakeMachine in = new FakeMachine("A", 5000).with("water", 1000);
        FakeMachine out = new FakeMachine("B", 600);
        attach("in", in, InteractionState.INPUT, "water");
        attach("out", out, InteractionState.OUTPUT, "water");
        assertEquals(600, hub.driveInput("in", 0).transferred());
        int callsAfterFirst = in.extractCalls;
        assertEquals(0, hub.driveInput("in", 20).transferred());
        assertEquals(callsAfterFirst, in.extractCalls, "输出满时不应再从输入端抽取");
        assertEquals(400, in.total());
        assertConserved(1000, in, out);
    }

    @Test
    void 输入端跳过同一方块上的输出端() {
        FakeMachine in = new FakeMachine("same", 5000).with("water", 500);
        FakeMachine sameBlockOut = new FakeMachine("same", 5000);
        attach("in", in, InteractionState.INPUT, "water");
        attach("selfOut", sameBlockOut, InteractionState.OUTPUT, "water");
        assertEquals(0, hub.driveInput("in", 0).transferred());
        assertEquals(500, in.total());
        assertEquals(0, in.extractCalls);
    }

    @Test
    void 自环被跳过时仍然传给其他方块的输出端() {
        FakeMachine in = new FakeMachine("same", 5000).with("water", 500);
        FakeMachine sameBlockOut = new FakeMachine("same", 5000);
        FakeMachine other = new FakeMachine("other", 5000);
        attach("in", in, InteractionState.INPUT, "water");
        attach("selfOut", sameBlockOut, InteractionState.OUTPUT, "water");
        attach("out", other, InteractionState.OUTPUT, "water");
        assertEquals(500, hub.driveInput("in", 0).transferred());
        assertEquals(0, sameBlockOut.total());
        assertEquals(500, other.total());
        assertConserved(500, in, sameBlockOut, other);
    }

    @Test
    void 未加载的输出端被跳过() {
        FakeMachine in = new FakeMachine("A", 5000).with("water", 500);
        FakeMachine unloaded = new FakeMachine("B", 5000);
        FakeMachine loaded = new FakeMachine("C", 5000);
        attach("in", in, InteractionState.INPUT, "water");
        attach("o1", unloaded, InteractionState.OUTPUT, "water");
        attach("o2", loaded, InteractionState.OUTPUT, "water");
        hub.markUnloaded("o1");
        assertFalse(hub.findEndpoint("o1").isLoaded());
        hub.driveInput("in", 0);
        assertEquals(0, unloaded.total());
        assertEquals(500, loaded.total());
        assertConserved(500, in, unloaded, loaded);
    }

    @Test
    void 未加载的输入端不运行且重新加载后恢复() {
        FakeMachine in = new FakeMachine("A", 5000).with("water", 500);
        FakeMachine out = new FakeMachine("B", 5000);
        attach("in", in, InteractionState.INPUT, "water");
        attach("out", out, InteractionState.OUTPUT, "water");
        hub.markUnloaded("in");
        assertEquals(0, hub.driveInput("in", 0).transferred());
        assertEquals(500, in.total());
        hub.markLoaded("in", in);
        assertEquals(500, hub.driveInput("in", 1).transferred());
        assertConserved(500, in, out);
    }

    // ---- 速率与开关 ----

    @Test
    void 全局速率上限限制单次运行的总量() {
        config.rateCap = 150;
        FakeMachine in = new FakeMachine("A", 5000).with("water", 400).with("lava", 400);
        FakeMachine out = new FakeMachine("B", 5000);
        attach("in", in, InteractionState.INPUT, "water", "lava");
        attach("out", out, InteractionState.OUTPUT, "water", "lava");
        assertEquals(150, hub.driveInput("in", 0).transferred());
        assertEquals(150, out.total());
        assertConserved(800, in, out);
    }

    @Test
    void 类型被服务器禁用时不传输() {
        config.fluidEnabled = false;
        FakeMachine in = new FakeMachine("A", 5000).with("water", 400);
        FakeMachine out = new FakeMachine("B", 5000);
        attach("in", in, InteractionState.INPUT, "water");
        attach("out", out, InteractionState.OUTPUT, "water");
        assertEquals(0, hub.driveInput("in", 0).transferred());
        assertEquals(400, in.total());
        assertConserved(400, in, out);
    }

    // ---- 模拟≠实际 ----

    @Test
    void 实际插入比模拟少时余量退回来源且守恒() {
        FakeMachine in = new FakeMachine("A", 5000).with("water", 1000);
        FakeMachine liar = new FakeMachine("B", 5000);
        liar.insertShortfall = 100;
        attach("in", in, InteractionState.INPUT, "water");
        attach("out", liar, InteractionState.OUTPUT, "water");
        TransferResult result = hub.driveInput("in", 0);
        assertEquals(900, result.transferred());
        assertEquals(0, result.lost());
        assertEquals(900, liar.total());
        assertEquals(100, in.total(), "余量退回来源");
        assertConserved(1000, in, liar);
    }

    @Test
    void 来源收不回余量时转给其他输出端而不丢失() {
        FakeMachine in = new FakeMachine("A", 5000).with("water", 1000);
        in.refuseInsert = true;
        FakeMachine liar = new FakeMachine("B", 5000);
        liar.insertShortfall = 100;
        FakeMachine backup = new FakeMachine("C", 5000);
        attach("in", in, InteractionState.INPUT, "water");
        attach("o1", liar, InteractionState.OUTPUT, "water");
        attach("o2", backup, InteractionState.OUTPUT, "water");
        TransferResult result = hub.driveInput("in", 0);
        assertEquals(0, result.lost());
        assertConserved(1000, in, liar, backup);
        assertEquals(1000, liar.total() + backup.total());
    }

    @Test
    void 实在无处可退时如实报告丢失量() {
        FakeMachine in = new FakeMachine("A", 5000).with("water", 1000);
        in.refuseInsert = true;
        FakeMachine liar = new FakeMachine("B", 5000);
        liar.insertShortfall = 100;
        attach("in", in, InteractionState.INPUT, "water");
        attach("out", liar, InteractionState.OUTPUT, "water");
        TransferResult result = hub.driveInput("in", 0);
        assertEquals(100, result.lost());
        assertConserved(1000 - 100, in, liar);
    }

    @Test
    void 多轮运行后总量始终守恒() {
        FakeMachine in = new FakeMachine("A", 100000).with("water", 3000).with("lava", 1500);
        FakeMachine o1 = new FakeMachine("B", 700);
        FakeMachine o2 = new FakeMachine("C", 900);
        o2.insertShortfall = 7;
        config.rateCap = 333;
        attach("in", in, InteractionState.INPUT, "water", "lava");
        attach("o1", o1, InteractionState.OUTPUT, "water", "lava");
        attach("o2", o2, InteractionState.OUTPUT, "water");
        for (int period = 0; period < 12; period++) {
            hub.driveInput("in", period * 20L);
            assertConserved(4500, in, o1, o2);
        }
    }
}
