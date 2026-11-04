package com.rtgame.warpcovers.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /** 接入一台机器并设置状态。名单属于频道，新频道默认放行一切。 */
    private void attach(String id, FakeMachine machine, InteractionState state) {
        connect(channel.id(), id, machine);
        setState(id, state);
    }

    // ---- 旧式便捷操作：直接映射到新门面 ----

    private Endpoint connect(String channelId, String id, FakeMachine machine) {
        return hub.connect(channelId, id, machine.location, "tester", new EndpointConfig(), machine, null);
    }

    private void setState(String id, InteractionState state) {
        hub.findEndpoint(id)
            .config()
            .setState(state);
    }

    private void markUnloaded(String id) {
        hub.unbind(id, null);
    }

    private void markLoaded(String id, FakeMachine machine) {
        Endpoint endpoint = hub.findEndpoint(id);
        hub.reconcile(id, endpoint.channelId(), machine.location, endpoint.config(), machine, null);
    }

    private static void assertConserved(long expected, FakeMachine... machines) {
        long sum = 0;
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
        Endpoint endpoint = connect(channel.id(), "a", new FakeMachine("A", 1000));
        assertEquals(InteractionState.EMPTY, endpoint.state());
    }

    @Test
    void 连接不存在的频道报错() {
        assertThrows(IllegalArgumentException.class, () -> connect("nope", "a", new FakeMachine("A", 10)));
    }

    @Test
    void 断开后不再参与传输() {
        FakeMachine in = new FakeMachine("A", 1000).with("water", 500);
        FakeMachine out = new FakeMachine("B", 1000);
        attach("in", in, InteractionState.INPUT);
        attach("out", out, InteractionState.OUTPUT);
        hub.disconnect("out");
        assertNull(hub.findEndpoint("out"));
        assertEquals(
            0,
            hub.driveInput("in", 0)
                .transferred());
        assertEquals(500, in.total());
    }

    // ---- 基本传输与守恒 ----

    @Test
    void 输入端把流体直通到输出端并守恒() {
        FakeMachine in = new FakeMachine("A", 1000).with("water", 400);
        FakeMachine out = new FakeMachine("B", 1000);
        attach("in", in, InteractionState.INPUT);
        attach("out", out, InteractionState.OUTPUT);
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
        attach("in", in, InteractionState.EMPTY);
        attach("out", out, InteractionState.OUTPUT);
        assertEquals(
            0,
            hub.driveInput("in", 0)
                .transferred());
        assertEquals(400, in.total());
        assertConserved(400, in, out);
    }

    @Test
    void 没有输出端时不抽取() {
        FakeMachine in = new FakeMachine("A", 1000).with("water", 400);
        attach("in", in, InteractionState.INPUT);
        assertEquals(
            0,
            hub.driveInput("in", 0)
                .transferred());
        assertEquals(0, in.extractCalls);
        assertConserved(400, in);
    }

    @Test
    void 不同频道互不干扰() {
        Channel other = hub.createChannel(ChannelType.FLUID, "另一个");
        FakeMachine in = new FakeMachine("A", 1000).with("water", 400);
        FakeMachine outSame = new FakeMachine("B", 1000);
        FakeMachine outOther = new FakeMachine("C", 1000);
        attach("in", in, InteractionState.INPUT);
        attach("outSame", outSame, InteractionState.OUTPUT);
        connect(other.id(), "outOther", outOther);
        setState("outOther", InteractionState.OUTPUT);
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
        attach("in", in, InteractionState.INPUT);
        attach("o1", first, InteractionState.OUTPUT);
        attach("o2", second, InteractionState.OUTPUT);
        assertEquals(
            1000,
            hub.driveInput("in", 0)
                .transferred());
        assertEquals(300, first.total());
        assertEquals(700, second.total());
        assertConserved(1000, in, first, second);
    }

    // ---- 过滤名单（属于频道） ----

    @Test
    void 新频道的名单是空黑名单_放行一切() {
        assertFalse(
            channel.filter()
                .isWhitelist());
        assertTrue(
            channel.filter()
                .keys()
                .isEmpty());
        assertFalse(
            channel.filter()
                .matchNbt());
        FakeMachine in = new FakeMachine("A", 5000).with("water", 300)
            .with("lava", 200);
        FakeMachine out = new FakeMachine("B", 5000);
        attach("in", in, InteractionState.INPUT);
        attach("out", out, InteractionState.OUTPUT);
        assertEquals(
            500,
            hub.driveInput("in", 0)
                .transferred());
        assertConserved(500, in, out);
    }

    @Test
    void 白名单只放行名单内的流体() {
        FakeMachine in = new FakeMachine("A", 5000).with("water", 300)
            .with("lava", 200);
        FakeMachine out = new FakeMachine("B", 5000);
        attach("in", in, InteractionState.INPUT);
        attach("out", out, InteractionState.OUTPUT);
        assertTrue(hub.setFilterWhitelist(channel.id(), true));
        assertTrue(hub.addFilterKey(channel.id(), "water"));
        hub.driveInput("in", 0);
        assertEquals(300, out.amountOf("water"));
        assertEquals(0, out.amountOf("lava"));
        assertEquals(200, in.amountOf("lava"));
        assertConserved(500, in, out);
    }

    @Test
    void 空白名单什么都不传() {
        FakeMachine in = new FakeMachine("A", 1000).with("water", 400);
        FakeMachine out = new FakeMachine("B", 1000);
        attach("in", in, InteractionState.INPUT);
        attach("out", out, InteractionState.OUTPUT);
        hub.setFilterWhitelist(channel.id(), true);
        assertEquals(
            0,
            hub.driveInput("in", 0)
                .transferred());
        assertEquals(EndpointStatus.FILTERED, hub.statusOf("in", 0));
        assertConserved(400, in, out);
    }

    @Test
    void 黑名单放行名单外的流体() {
        FakeMachine in = new FakeMachine("A", 5000).with("water", 300)
            .with("lava", 200);
        FakeMachine out = new FakeMachine("B", 5000);
        attach("in", in, InteractionState.INPUT);
        attach("out", out, InteractionState.OUTPUT);
        hub.addFilterKey(channel.id(), "lava");
        hub.driveInput("in", 0);
        assertEquals(300, out.amountOf("water"));
        assertEquals(200, in.amountOf("lava"));
        assertConserved(500, in, out);
    }

    @Test
    void 同频道所有输出端共用同一份名单() {
        FakeMachine in = new FakeMachine("A", 5000).with("water", 300)
            .with("lava", 200);
        FakeMachine first = new FakeMachine("B", 5000);
        FakeMachine second = new FakeMachine("C", 5000);
        attach("in", in, InteractionState.INPUT);
        attach("first", first, InteractionState.OUTPUT);
        attach("second", second, InteractionState.OUTPUT);
        hub.setFilterWhitelist(channel.id(), true);
        hub.addFilterKey(channel.id(), "water");
        hub.driveInput("in", 0);
        assertEquals(150, first.amountOf("water"), "两个输出端按默认的轮询平分");
        assertEquals(150, second.amountOf("water"));
        assertEquals(0, first.amountOf("lava"));
        assertEquals(0, second.amountOf("lava"));
        assertEquals(200, in.amountOf("lava"));
        assertConserved(500, in, first, second);
    }

    @Test
    void 名单不跨频道() {
        Channel other = hub.createChannel(ChannelType.FLUID, "另一个");
        hub.setFilterWhitelist(channel.id(), true);
        FakeMachine in = new FakeMachine("A", 1000).with("water", 400);
        FakeMachine out = new FakeMachine("B", 1000);
        connect(other.id(), "in", in);
        connect(other.id(), "out", out);
        setState("in", InteractionState.INPUT);
        setState("out", InteractionState.OUTPUT);
        assertEquals(
            400,
            hub.driveInput("in", 0)
                .transferred(),
            "另一个频道仍是默认的放行一切");
    }

    @Test
    void 修改名单会标脏() {
        int[] dirty = { 0 };
        hub.setDirtyListener(() -> dirty[0]++);
        hub.setFilterWhitelist(channel.id(), true);
        hub.setFilterMatchNbt(channel.id(), true);
        hub.addFilterKey(channel.id(), "water");
        assertEquals(3, dirty[0]);
        hub.addFilterKey(channel.id(), "water");
        hub.setFilterWhitelist(channel.id(), true);
        assertEquals(3, dirty[0], "没有变化不标脏");
        hub.removeFilterKey(channel.id(), "water");
        hub.clearFilter(channel.id());
        assertEquals(4, dirty[0]);
    }

    @Test
    void 能量频道没有名单() {
        Channel energy = hub.createChannel(ChannelType.ENERGY, "能量");
        assertFalse(hub.setFilterWhitelist(energy.id(), true));
        assertFalse(hub.addFilterKey(energy.id(), "eu"));
        assertTrue(energy.allows("eu"));
    }

    // ---- 运行周期 ----

    @Test
    void 输入端按默认周期20tick运行() {
        FakeMachine in = new FakeMachine("A", 5000).with("water", 100);
        FakeMachine out = new FakeMachine("B", 5000);
        attach("in", in, InteractionState.INPUT);
        attach("out", out, InteractionState.OUTPUT);
        assertEquals(
            100,
            hub.driveInput("in", 100)
                .transferred());
        in.with("water", 50);
        assertEquals(
            0,
            hub.driveInput("in", 101)
                .transferred(),
            "周期未到不运行");
        assertEquals(
            0,
            hub.driveInput("in", 119)
                .transferred(),
            "周期未到不运行");
        assertEquals(
            50,
            hub.driveInput("in", 120)
                .transferred(),
            "满20tick再次运行");
        assertConserved(150, in, out);
    }

    @Test
    void 运行周期服从配置且不低于下限() {
        config.defaultPeriod = 5;
        config.minPeriod = 10;
        FakeMachine in = new FakeMachine("A", 5000).with("water", 100);
        FakeMachine out = new FakeMachine("B", 5000);
        attach("in", in, InteractionState.INPUT);
        attach("out", out, InteractionState.OUTPUT);
        hub.driveInput("in", 0);
        in.with("water", 50);
        assertEquals(
            0,
            hub.driveInput("in", 9)
                .transferred());
        assertEquals(
            50,
            hub.driveInput("in", 10)
                .transferred());
        assertConserved(150, in, out);
    }

    @Test
    void 调用方时钟回退后输入端仍按周期运行() {
        FakeMachine in = new FakeMachine("A", 5000).with("water", 100);
        FakeMachine out = new FakeMachine("B", 5000);
        attach("in", in, InteractionState.INPUT);
        attach("out", out, InteractionState.OUTPUT);
        assertEquals(
            100,
            hub.driveInput("in", 100000)
                .transferred());
        // 机器重载，计时器从 0 重新开始
        in.with("water", 50);
        assertEquals(
            50,
            hub.driveInput("in", 3)
                .transferred(),
            "时钟回退后应重新计周期");
        in.with("water", 10);
        assertEquals(
            0,
            hub.driveInput("in", 10)
                .transferred(),
            "回退后仍遵守周期");
        assertEquals(
            10,
            hub.driveInput("in", 23)
                .transferred());
        assertConserved(160, in, out);
    }

    // ---- 背压、自环、卸载 ----

    @Test
    void 输出端满了输入端停止抽取() {
        FakeMachine in = new FakeMachine("A", 5000).with("water", 1000);
        FakeMachine out = new FakeMachine("B", 600);
        attach("in", in, InteractionState.INPUT);
        attach("out", out, InteractionState.OUTPUT);
        assertEquals(
            600,
            hub.driveInput("in", 0)
                .transferred());
        int callsAfterFirst = in.extractCalls;
        assertEquals(
            0,
            hub.driveInput("in", 20)
                .transferred());
        assertEquals(callsAfterFirst, in.extractCalls, "输出满时不应再从输入端抽取");
        assertEquals(400, in.total());
        assertConserved(1000, in, out);
    }

    @Test
    void 输入端跳过同一方块上的输出端() {
        FakeMachine in = new FakeMachine("same", 5000).with("water", 500);
        FakeMachine sameBlockOut = new FakeMachine("same", 5000);
        attach("in", in, InteractionState.INPUT);
        attach("selfOut", sameBlockOut, InteractionState.OUTPUT);
        assertEquals(
            0,
            hub.driveInput("in", 0)
                .transferred());
        assertEquals(500, in.total());
        assertEquals(0, in.extractCalls);
    }

    @Test
    void 自环被跳过时仍然传给其他方块的输出端() {
        FakeMachine in = new FakeMachine("same", 5000).with("water", 500);
        FakeMachine sameBlockOut = new FakeMachine("same", 5000);
        FakeMachine other = new FakeMachine("other", 5000);
        attach("in", in, InteractionState.INPUT);
        attach("selfOut", sameBlockOut, InteractionState.OUTPUT);
        attach("out", other, InteractionState.OUTPUT);
        assertEquals(
            500,
            hub.driveInput("in", 0)
                .transferred());
        assertEquals(0, sameBlockOut.total());
        assertEquals(500, other.total());
        assertConserved(500, in, sameBlockOut, other);
    }

    @Test
    void 未加载的输出端被跳过() {
        FakeMachine in = new FakeMachine("A", 5000).with("water", 500);
        FakeMachine unloaded = new FakeMachine("B", 5000);
        FakeMachine loaded = new FakeMachine("C", 5000);
        attach("in", in, InteractionState.INPUT);
        attach("o1", unloaded, InteractionState.OUTPUT);
        attach("o2", loaded, InteractionState.OUTPUT);
        markUnloaded("o1");
        assertFalse(
            hub.findEndpoint("o1")
                .isLoaded());
        hub.driveInput("in", 0);
        assertEquals(0, unloaded.total());
        assertEquals(500, loaded.total());
        assertConserved(500, in, unloaded, loaded);
    }

    @Test
    void 未加载的输入端不运行且重新加载后恢复() {
        FakeMachine in = new FakeMachine("A", 5000).with("water", 500);
        FakeMachine out = new FakeMachine("B", 5000);
        attach("in", in, InteractionState.INPUT);
        attach("out", out, InteractionState.OUTPUT);
        markUnloaded("in");
        assertEquals(
            0,
            hub.driveInput("in", 0)
                .transferred());
        assertEquals(500, in.total());
        markLoaded("in", in);
        assertEquals(
            500,
            hub.driveInput("in", 1)
                .transferred());
        assertConserved(500, in, out);
    }

    // ---- 速率与开关 ----

    @Test
    void 全局速率上限限制单次运行的总量() {
        config.rateCap = 150;
        FakeMachine in = new FakeMachine("A", 5000).with("water", 400)
            .with("lava", 400);
        FakeMachine out = new FakeMachine("B", 5000);
        attach("in", in, InteractionState.INPUT);
        attach("out", out, InteractionState.OUTPUT);
        assertEquals(
            150,
            hub.driveInput("in", 0)
                .transferred());
        assertEquals(150, out.total());
        assertConserved(800, in, out);
    }

    @Test
    void 类型被服务器禁用时不传输() {
        config.fluidEnabled = false;
        FakeMachine in = new FakeMachine("A", 5000).with("water", 400);
        FakeMachine out = new FakeMachine("B", 5000);
        attach("in", in, InteractionState.INPUT);
        attach("out", out, InteractionState.OUTPUT);
        assertEquals(
            0,
            hub.driveInput("in", 0)
                .transferred());
        assertEquals(400, in.total());
        assertConserved(400, in, out);
    }

    // ---- 模拟≠实际 ----

    @Test
    void 实际插入比模拟少时余量退回来源且守恒() {
        FakeMachine in = new FakeMachine("A", 5000).with("water", 1000);
        FakeMachine liar = new FakeMachine("B", 5000);
        liar.insertShortfall = 100;
        attach("in", in, InteractionState.INPUT);
        attach("out", liar, InteractionState.OUTPUT);
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
        attach("in", in, InteractionState.INPUT);
        attach("o1", liar, InteractionState.OUTPUT);
        attach("o2", backup, InteractionState.OUTPUT);
        TransferResult result = hub.driveInput("in", 0);
        assertEquals(0, result.lost());
        assertConserved(1000, in, liar, backup);
        assertEquals(1000, liar.total() + backup.total());
    }

    @Test
    void 余量退回来源走端口的退回而不是普通插入() {
        FakeMachine in = new FakeMachine("A", 5000).with("water", 1000);
        in.refuseInsert = true;
        in.restoreAlways = true;
        FakeMachine liar = new FakeMachine("B", 5000);
        liar.insertShortfall = 100;
        attach("in", in, InteractionState.INPUT);
        attach("out", liar, InteractionState.OUTPUT);
        TransferResult result = hub.driveInput("in", 0);
        assertEquals(0, result.lost());
        assertEquals(100, in.restored, "普通插入被拒时仍能通过 restore 退回");
        assertConserved(1000, in, liar);
    }

    @Test
    void 实在无处可退时如实报告丢失量() {
        FakeMachine in = new FakeMachine("A", 5000).with("water", 1000);
        in.refuseInsert = true;
        FakeMachine liar = new FakeMachine("B", 5000);
        liar.insertShortfall = 100;
        attach("in", in, InteractionState.INPUT);
        attach("out", liar, InteractionState.OUTPUT);
        TransferResult result = hub.driveInput("in", 0);
        assertEquals(100, result.lost());
        assertConserved(1000 - 100, in, liar);
    }

    @Test
    void 多轮运行后总量始终守恒() {
        FakeMachine in = new FakeMachine("A", 100000).with("water", 3000)
            .with("lava", 1500);
        FakeMachine o1 = new FakeMachine("B", 700);
        FakeMachine o2 = new FakeMachine("C", 900);
        o2.insertShortfall = 7;
        config.rateCap = 333;
        attach("in", in, InteractionState.INPUT);
        attach("o1", o1, InteractionState.OUTPUT);
        attach("o2", o2, InteractionState.OUTPUT);
        for (int period = 0; period < 12; period++) {
            hub.driveInput("in", period * 20L);
            assertConserved(4500, in, o1, o2);
        }
    }

    // ---- 优先级与分发方式 ----

    private EndpointConfig configOf(String id) {
        return hub.findEndpoint(id)
            .config();
    }

    @Test
    void 优先级高的输出端先接收() {
        FakeMachine in = new FakeMachine("A", 5000).with("water", 1000);
        FakeMachine low = new FakeMachine("B", 5000);
        FakeMachine high = new FakeMachine("C", 600);
        attach("in", in, InteractionState.INPUT);
        attach("low", low, InteractionState.OUTPUT);
        attach("high", high, InteractionState.OUTPUT);
        configOf("high").setPriority(3);
        hub.driveInput("in", 0);
        assertEquals(600, high.total(), "高优先级先收满");
        assertEquals(400, low.total(), "放不下的才给低优先级");
        assertConserved(1000, in, low, high);
    }

    @Test
    void 负优先级排在默认优先级之后() {
        FakeMachine in = new FakeMachine("A", 5000).with("water", 100);
        FakeMachine negative = new FakeMachine("B", 5000);
        FakeMachine normal = new FakeMachine("C", 5000);
        attach("in", in, InteractionState.INPUT);
        attach("neg", negative, InteractionState.OUTPUT);
        attach("normal", normal, InteractionState.OUTPUT);
        configOf("neg").setPriority(-1);
        hub.driveInput("in", 0);
        assertEquals(100, normal.total());
        assertEquals(0, negative.total());
        assertConserved(100, in, negative, normal);
    }

    @Test
    void 新频道默认轮询且同级平均分配() {
        assertEquals(DistributionMode.ROUND_ROBIN, channel.distribution());
        FakeMachine in = new FakeMachine("A", 5000).with("water", 1000);
        FakeMachine o1 = new FakeMachine("B", 5000);
        FakeMachine o2 = new FakeMachine("C", 5000);
        attach("in", in, InteractionState.INPUT);
        attach("o1", o1, InteractionState.OUTPUT);
        attach("o2", o2, InteractionState.OUTPUT);
        hub.driveInput("in", 0);
        assertEquals(500, o1.total());
        assertEquals(500, o2.total());
        assertConserved(1000, in, o1, o2);
    }

    @Test
    void 轮询指针让每周期一个单位轮流分给各输出端() {
        FakeMachine in = new FakeMachine("A", 5000).with("water", 6);
        FakeMachine o1 = new FakeMachine("B", 5000);
        FakeMachine o2 = new FakeMachine("C", 5000);
        FakeMachine o3 = new FakeMachine("D", 5000);
        attach("in", in, InteractionState.INPUT);
        attach("o1", o1, InteractionState.OUTPUT);
        attach("o2", o2, InteractionState.OUTPUT);
        attach("o3", o3, InteractionState.OUTPUT);
        configOf("in").setRate(1);
        hub.driveInput("in", 0);
        assertEquals(1, o1.total());
        hub.driveInput("in", 20);
        assertEquals(1, o2.total());
        hub.driveInput("in", 40);
        assertEquals(1, o3.total());
        for (int i = 3; i < 6; i++) {
            hub.driveInput("in", i * 20L);
        }
        assertEquals(2, o1.total());
        assertEquals(2, o2.total());
        assertEquals(2, o3.total());
        assertConserved(6, in, o1, o2, o3);
    }

    @Test
    void 各优先级的轮询指针互相独立() {
        FakeMachine in = new FakeMachine("A", 5000).with("water", 4);
        FakeMachine h1 = new FakeMachine("B", 1);
        FakeMachine h2 = new FakeMachine("C", 1);
        FakeMachine l1 = new FakeMachine("D", 5000);
        FakeMachine l2 = new FakeMachine("E", 5000);
        attach("in", in, InteractionState.INPUT);
        attach("h1", h1, InteractionState.OUTPUT);
        attach("h2", h2, InteractionState.OUTPUT);
        attach("l1", l1, InteractionState.OUTPUT);
        attach("l2", l2, InteractionState.OUTPUT);
        configOf("h1").setPriority(1);
        configOf("h2").setPriority(1);
        configOf("in").setRate(1);
        // 前两轮高优先级各收一个并装满，低优先级的指针不动，所以第三轮从 l1 开始
        hub.driveInput("in", 0);
        hub.driveInput("in", 20);
        assertEquals(1, h1.total());
        assertEquals(1, h2.total());
        hub.driveInput("in", 40);
        assertEquals(1, l1.total());
        hub.driveInput("in", 60);
        assertEquals(1, l2.total());
        assertConserved(4, in, h1, h2, l1, l2);
    }

    @Test
    void 顺序方式按连接顺序依次填满() {
        hub.setDistribution(channel.id(), DistributionMode.SEQUENTIAL);
        FakeMachine in = new FakeMachine("A", 5000).with("water", 1000);
        FakeMachine o1 = new FakeMachine("B", 300);
        FakeMachine o2 = new FakeMachine("C", 5000);
        FakeMachine o3 = new FakeMachine("D", 5000);
        attach("in", in, InteractionState.INPUT);
        attach("o1", o1, InteractionState.OUTPUT);
        attach("o2", o2, InteractionState.OUTPUT);
        attach("o3", o3, InteractionState.OUTPUT);
        hub.driveInput("in", 0);
        assertEquals(300, o1.total());
        assertEquals(700, o2.total());
        assertEquals(0, o3.total());
        assertConserved(1000, in, o1, o2, o3);
    }

    @Test
    void 修改分发方式会标脏且不存在的频道返回假() {
        int[] dirty = new int[1];
        hub.setDirtyListener(() -> dirty[0]++);
        assertTrue(hub.setDistribution(channel.id(), DistributionMode.SEQUENTIAL));
        assertEquals(1, dirty[0]);
        assertTrue(hub.setDistribution(channel.id(), DistributionMode.SEQUENTIAL));
        assertEquals(1, dirty[0], "没有变化不标脏");
        assertFalse(hub.setDistribution("nope", DistributionMode.SEQUENTIAL));
    }

    // ---- 单板速率与周期 ----

    @Test
    void 输入端每周期最多搬运单板速率() {
        FakeMachine in = new FakeMachine("A", 5000).with("water", 1000);
        FakeMachine out = new FakeMachine("B", 5000);
        attach("in", in, InteractionState.INPUT);
        attach("out", out, InteractionState.OUTPUT);
        configOf("in").setRate(120);
        assertEquals(
            120,
            hub.driveInput("in", 0)
                .transferred());
        assertEquals(
            120,
            hub.driveInput("in", 20)
                .transferred());
        assertConserved(1000, in, out);
    }

    @Test
    void 实际配额是单板速率和全局上限中较小的() {
        config.rateCap = 100;
        FakeMachine in = new FakeMachine("A", 5000).with("water", 1000);
        FakeMachine out = new FakeMachine("B", 5000);
        attach("in", in, InteractionState.INPUT);
        attach("out", out, InteractionState.OUTPUT);
        configOf("in").setRate(500);
        assertEquals(
            100,
            hub.driveInput("in", 0)
                .transferred(),
            "全局上限更小");
        config.rateCap = 1000;
        configOf("in").setRate(40);
        assertEquals(
            40,
            hub.driveInput("in", 20)
                .transferred(),
            "单板速率更小");
        assertConserved(1000, in, out);
    }

    @Test
    void 输出端速率以它自身的周期为窗口() {
        FakeMachine in = new FakeMachine("A", 5000).with("water", 1000);
        FakeMachine out = new FakeMachine("B", 5000);
        attach("in", in, InteractionState.INPUT);
        attach("out", out, InteractionState.OUTPUT);
        configOf("in").setPeriod(1);
        configOf("out").setRate(100);
        configOf("out").setPeriod(10);
        long total = 0;
        for (long t = 0; t < 10; t++) {
            total += hub.driveInput("in", t)
                .transferred();
        }
        assertEquals(100, total, "一个窗口内总共只收 100");
        assertEquals(EndpointStatus.WAITING, hub.statusOf("in", 9), "额度用完时输入端等待，不算对端已满");
        assertEquals(
            100,
            hub.driveInput("in", 10)
                .transferred(),
            "新窗口重新计数");
        assertEquals(200, out.total());
        assertConserved(1000, in, out);
    }

    @Test
    void 多个输入端共用输出端的同一个窗口() {
        FakeMachine in1 = new FakeMachine("A", 5000).with("water", 1000);
        FakeMachine in2 = new FakeMachine("B", 5000).with("water", 1000);
        FakeMachine out = new FakeMachine("C", 5000);
        attach("in1", in1, InteractionState.INPUT);
        attach("in2", in2, InteractionState.INPUT);
        attach("out", out, InteractionState.OUTPUT);
        configOf("out").setRate(100);
        assertEquals(
            100,
            hub.driveInput("in1", 0)
                .transferred());
        assertEquals(
            0,
            hub.driveInput("in2", 5)
                .transferred(),
            "窗口额度已被另一个输入端用完");
        assertConserved(2000, in1, in2, out);
    }

    @Test
    void 输出端窗口满额时余量流向其他输出端() {
        FakeMachine in = new FakeMachine("A", 5000).with("water", 1000);
        FakeMachine limited = new FakeMachine("B", 5000);
        FakeMachine open = new FakeMachine("C", 5000);
        attach("in", in, InteractionState.INPUT);
        attach("limited", limited, InteractionState.OUTPUT);
        attach("open", open, InteractionState.OUTPUT);
        configOf("limited").setPriority(1);
        configOf("limited").setRate(150);
        hub.driveInput("in", 0);
        assertEquals(150, limited.total());
        assertEquals(850, open.total());
        assertConserved(1000, in, limited, open);
    }

    @Test
    void 单板周期决定输入端运行间隔且修改后立即生效() {
        FakeMachine in = new FakeMachine("A", 5000).with("water", 1000);
        FakeMachine out = new FakeMachine("B", 5000);
        attach("in", in, InteractionState.INPUT);
        attach("out", out, InteractionState.OUTPUT);
        configOf("in").setRate(10);
        configOf("in").setPeriod(5);
        hub.driveInput("in", 0);
        assertEquals(
            0,
            hub.driveInput("in", 4)
                .transferred());
        assertEquals(
            10,
            hub.driveInput("in", 5)
                .transferred());
        configOf("in").setPeriod(50);
        assertEquals(
            0,
            hub.driveInput("in", 10)
                .transferred(),
            "周期调长后立即按新周期算");
        configOf("in").setPeriod(1);
        assertEquals(
            10,
            hub.driveInput("in", 11)
                .transferred(),
            "周期调短后立即按新周期算");
        assertConserved(1000, in, out);
    }

    @Test
    void 调高最短周期后已有覆盖板立即按新下限运行() {
        FakeMachine in = new FakeMachine("A", 5000).with("water", 1000);
        FakeMachine out = new FakeMachine("B", 5000);
        attach("in", in, InteractionState.INPUT);
        attach("out", out, InteractionState.OUTPUT);
        configOf("in").setRate(10);
        configOf("in").setPeriod(5);
        hub.driveInput("in", 0);
        config.minPeriod = 100;
        assertEquals(
            0,
            hub.driveInput("in", 5)
                .transferred());
        assertEquals(
            0,
            hub.driveInput("in", 99)
                .transferred());
        assertEquals(
            10,
            hub.driveInput("in", 100)
                .transferred());
        assertConserved(1000, in, out);
    }

    @Test
    void 优先级速率周期混合运行多轮后总量守恒() {
        FakeMachine in = new FakeMachine("A", 100000).with("water", 5000)
            .with("lava", 3000);
        FakeMachine high = new FakeMachine("B", 500);
        FakeMachine liar = new FakeMachine("C", 1500);
        liar.insertShortfall = 3;
        FakeMachine low = new FakeMachine("D", 100000);
        attach("in", in, InteractionState.INPUT);
        attach("high", high, InteractionState.OUTPUT);
        attach("liar", liar, InteractionState.OUTPUT);
        attach("low", low, InteractionState.OUTPUT);
        configOf("high").setPriority(2);
        configOf("high").setRate(77);
        configOf("liar").setPriority(1);
        configOf("liar").setRate(50);
        configOf("liar").setPeriod(40);
        configOf("in").setRate(191);
        configOf("in").setPeriod(7);
        for (long t = 0; t < 2000; t++) {
            hub.driveInput("in", t);
            assertConserved(8000, in, high, liar, low);
        }
        assertEquals(0, in.total(), "最终全部搬完");
    }

    // ---- 能量频道 ----

    @Test
    void 能量频道不看名单照常传输() {
        Channel energy = hub.createChannel(ChannelType.ENERGY, "能量频道");
        FakeMachine in = new FakeMachine("A", 100000).with("eu", 5000);
        FakeMachine out = new FakeMachine("B", 3000);
        connect(energy.id(), "in", in);
        connect(energy.id(), "out", out);
        setState("in", InteractionState.INPUT);
        setState("out", InteractionState.OUTPUT);
        // 能量频道没有名单，即使把名单切到白名单也不起作用
        assertFalse(hub.setFilterWhitelist(energy.id(), true));
        hub.driveInput("in", 0);
        assertEquals(3000, out.amountOf("eu"), "充满即止");
        assertEquals(2000, in.amountOf("eu"));
        assertConserved(5000, in, out);
        hub.driveInput("in", 20);
        assertConserved(5000, in, out);
        assertEquals(EndpointStatus.PEER_FULL, hub.statusOf("in", 20));
    }
}
