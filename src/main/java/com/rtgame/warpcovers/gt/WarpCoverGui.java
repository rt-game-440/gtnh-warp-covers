package com.rtgame.warpcovers.gt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.FluidDrawable;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.serialization.ByteBufAdapters;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.value.sync.DynamicLinkedSyncHandler;
import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.DynamicSyncedWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.ScrollingTextWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.rtgame.warpcovers.WarpCovers;
import com.rtgame.warpcovers.core.Channel;
import com.rtgame.warpcovers.core.ChannelAdmin;
import com.rtgame.warpcovers.core.ChannelColors;
import com.rtgame.warpcovers.core.ChannelGroup;
import com.rtgame.warpcovers.core.ChannelHub;
import com.rtgame.warpcovers.core.ChannelType;
import com.rtgame.warpcovers.core.ConnectionAction;
import com.rtgame.warpcovers.core.DualPaneLayout;
import com.rtgame.warpcovers.core.CoverPanel;
import com.rtgame.warpcovers.core.CreateFailure;
import com.rtgame.warpcovers.core.DistributionMode;
import com.rtgame.warpcovers.core.EndpointStatus;
import com.rtgame.warpcovers.core.FilterCandidates;
import com.rtgame.warpcovers.core.Identity;
import com.rtgame.warpcovers.core.InteractionState;
import com.rtgame.warpcovers.core.NumberInput;
import com.rtgame.warpcovers.core.ObjectKeys;
import com.rtgame.warpcovers.core.PanelEndpoint;
import com.rtgame.warpcovers.core.PanelNotice;
import com.rtgame.warpcovers.core.Permission;
import com.rtgame.warpcovers.core.PrivateTags;
import com.rtgame.warpcovers.core.RemoteEndpoint;
import com.rtgame.warpcovers.core.Viewer;

import gregtech.api.modularui2.CoverGuiData;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.gui.modularui.cover.base.CoverBaseGui;

/**
 * 折跃覆盖板界面。单类型覆盖板是双栏：左栏频道列表、状态栏、玩家背包，右栏是选中频道的配置面板，连接操作都在右栏顶部。
 * 聚合覆盖板和折跃频道管理器暂时还是旧的单栏：左侧边栏切频道和设置页，点频道直接连接或打开配置（#21、#22 改造）。
 * 设置页只有输出优先级、速率、运行周期；过滤名单属于频道，在频道配置面板（齿轮）里编辑，覆盖板和折跃频道管理器用同一套。
 * 服务端按玩家构建快照（{@link PanelSnapshot}）同步给客户端，客户端的每个操作都是发往服务端的请求，
 * 由 {@link CoverPanel} 校验后执行，结果随下一次快照回到界面。
 */
public final class WarpCoverGui extends CoverBaseGui<BaseWarpCover> {

    private static final int ROOT_W = 196;
    private static final int ROOT_H = 146;
    private static final int TAB_SIZE = 20;
    private static final int PAGE_X = 24;
    private static final int PAGE_W = ROOT_W - PAGE_X;
    private static final int PAGE_H = 118;
    private static final int ROW_W = PAGE_W - 8;
    private static final int ROW_H = 14;
    private static final int STATUS_Y = PAGE_H + 4;
    /** 设置页：顶部一行数字输入框，下面是说明文字。 */
    private static final int FIELD_GAP = 3;
    private static final int NOTE_Y = 25;
    /** 管理器的编辑页在设置页上方多一块头部，整个界面比覆盖板高。 */
    private static final int EDITOR_HEADER_H = 31;
    private static final int EDITOR_BUTTON_W = 40;
    private static final int MANAGER_PAGE_H = PAGE_H + EDITOR_HEADER_H;
    private static final int MANAGER_ROOT_H = ROOT_H + EDITOR_HEADER_H;
    private static final int PLAYER_INVENTORY_H = 84;
    /** 配置面板里的连接端列表：标题和翻页一行，每页若干行。 */
    private static final int ENDPOINT_ROWS = 5;
    private static final int ENDPOINT_ROW_H = 13;
    private static final int ENDPOINT_LIST_H = 16 + ENDPOINT_ROWS * ENDPOINT_ROW_H + 4;
    private static final int QUICK_BUTTON_W = 34;
    private static final int QUICK_COLUMNS = 8;
    private static final int QUICK_ROWS = 4;
    private static final int QUICK_CELL = 18;
    private static final int QUICK_PAGE_SIZE = QUICK_COLUMNS * QUICK_ROWS;
    private static final int QUICK_GRID_Y = 36;

    private static final int TEXT_COLOR = 0xFF202020;
    private static final int OWNER_COLOR = 0xFF484860;
    /** 按钮底色是中灰，文字用白色才看得清。 */
    private static final int BUTTON_TEXT = 0xFFFFFFFF;
    private static final int BUTTON_DETAIL = 0xFFDCDCE6;
    private static final int ERROR_COLOR = 0xFFAA0000;

    /** 频道配置面板：左栏名称、颜色、分发，右栏权限矩阵。 */
    private static final int CFG_W = 300;
    private static final int CFG_H = 134;
    private static final int CFG_LEFT_W = 148;
    private static final int CFG_RIGHT_X = 160;
    private static final int MATRIX_LABEL_W = 40;
    private static final int MATRIX_CELL = 16;
    private static final int MATRIX_HEADER_Y = 20;
    private static final int MATRIX_ROW_H = 12;
    private static final int SWATCH = 11;
    /** 配置面板里的名单区：一行控件，下面是两列三行的条目窗口，右侧上下翻。 */
    private static final int CFG_FILTER_Y = 98;
    private static final int FILTER_COLS = 2;
    private static final int FILTER_ROWS = 3;
    private static final int FILTER_ARROW_W = 12;
    private static final int FILTER_CELL_W = (CFG_W - 16 - FILTER_ARROW_W - 2) / FILTER_COLS;
    private static final int FILTER_HEADER_H = 18;
    private static final int FILTER_LIST_Y = FILTER_HEADER_H + 3;
    private static final int FILTER_BLOCK_H = FILTER_LIST_Y + FILTER_ROWS * ROW_H + 4;
    /** 频道页：顶部一行新增按钮、搜索框、标签筛选；列表项两行。 */
    private static final int CREATE_W = 50;
    private static final int TAG_FILTER_W = 46;
    private static final int CHANNEL_ROW_H = 20;
    private static final int RECENT_ICONS = 3;
    private static final int RECENT_ICON = 10;
    /** 聚合覆盖板设置页顶部的类型切换按钮高度。 */
    private static final int SEGMENT_H = 16;

    /** 双栏布局里固定的部分；随屏幕变化的尺寸见 {@link DualPaneLayout}。 */
    private static final int INVENTORY_W = 162;
    private static final int INVENTORY_H = 76;
    private static final int STATUS_H = 22;
    private static final int LEFT_GAP = 3;
    /** 右栏顶部：色块、频道名、连接状态，下面是连接按钮。 */
    private static final int PANE_SWATCH = 10;
    private static final int PANE_BUTTON_Y = 23;
    /** 文件夹式分页：标签比卡片上沿多压 1 像素。 */
    private static final int TAB_Y = 39;
    private static final int TAB_H = 15;
    private static final int TAB_W_SHORT = 28;
    private static final int TAB_W_LONG = 44;
    private static final int TAB_SLOTS = 4;
    private static final int CARD_Y = TAB_Y + TAB_H - 1;
    private static final int CARD_PADDING = 4;
    /** 「连接」分页：交互状态一组，传输一组（文字在左、数字框在右）。 */
    private static final int STATE_BUTTON_Y = 11;
    private static final int TRANSFER_LABEL_Y = 30;
    private static final int TRANSFER_ROW_Y = 41;
    private static final int TRANSFER_ROW_H = 16;
    private static final int TRANSFER_FIELD_MIN_W = 48;
    private static final int CONNECT_HINT_Y = 88;
    private static final int ENERGY_NOTES_Y = 100;
    private static final long CONNECTION_CLICK_COOLDOWN_MS = 500;
    private static final int CONNECTED_COLOR = 0xFF1E8A1E;
    private static final int CONNECTED_ROW_COLOR = 0xFF90F090;
    private static final int PICKED_FRAME = 0xFF40C8FF;

    private static final InteractionState[] STATES = { InteractionState.INPUT, InteractionState.OUTPUT,
        InteractionState.EMPTY };

    public WarpCoverGui(BaseWarpCover cover) {
        super(cover);
    }

    @Override
    protected String getGuiId() {
        return cover.guiId();
    }

    @Override
    protected boolean doesBindPlayerInventory() {
        // 名单靠光标上的物品或流体容器添加，需要能从背包拿起；双栏界面把背包放进左栏，不用基类放在底部的那份
        return !isDualPane();
    }

    @Override
    protected void addTitleToUI(Flow row, CoverGuiData data) {
        // 双栏界面的高度要留给右栏，频道名已经在面板顶部；空行由 createBasePanel 折叠
        if (isDualPane()) {
            row.setEnabled(false);
        } else {
            super.addTitleToUI(row, data);
        }
    }

    @Override
    protected ModularPanel createBasePanel(PanelSyncManager syncManager, UISettings uiSettings, CoverGuiData data) {
        ModularPanel panel = super.createBasePanel(syncManager, uiSettings, data);
        if (isDualPane()) {
            // 空行设高度 0 不管用，仍占 8 像素，只有禁用并折叠才不占位
            ((Flow) panel.getChildren()
                .get(0)).collapseDisabledChild();
        }
        return panel;
    }

    private boolean isDualPane() {
        return cover.parts()
            .size() == 1;
    }

    @Override
    public void addUIWidgets(PanelSyncManager syncManager, Flow column, CoverGuiData data) {
        List<WarpPart> parts = cover.parts();
        if (parts.size() == 1) {
            column.child(
                new Session(syncManager, data.getPlayer(), parts.get(0), "warpcovers_", PAGE_H)
                    .buildDualPane(
                        data.isPopUp(),
                        // 服务端的控件只用来同步，尺寸不起作用
                        data.getPlayer().worldObj.isRemote ? ClientScreen.dualPaneLayout()
                            : DualPaneLayout.minimum()));
        } else {
            column.child(aggregateRoot(syncManager, data.getPlayer(), parts));
        }
    }

    /** 聚合覆盖板：三个类型各一个频道页，设置页内用一排按钮切换类型。 */
    private IWidget aggregateRoot(PanelSyncManager syncManager, EntityPlayer player, List<WarpPart> parts) {
        List<Session> sessions = new ArrayList<>();
        for (WarpPart part : parts) {
            sessions.add(
                new Session(
                    syncManager,
                    player,
                    part,
                    "warpcovers_" + part.channelType()
                        .key() + "_",
                    PAGE_H - SEGMENT_H));
        }
        ParentWidget<?> root = new ParentWidget<>();
        root.size(ROOT_W, ROOT_H);

        PagedWidget.Controller controller = new PagedWidget.Controller();
        PagedWidget<?> pages = new PagedWidget<>();
        pages.controller(controller)
            .pos(PAGE_X, 0)
            .size(PAGE_W, PAGE_H);
        for (int i = 0; i < sessions.size(); i++) {
            root.child(
                tab(
                    i,
                    controller,
                    new ItemDrawable(
                        sectionIcon(
                            parts.get(i)
                                .channelType())).asIcon()
                                    .size(16),
                    "warpcovers.gui.tab.channels_" + parts.get(i)
                        .channelType()
                        .key(),
                    sessions.get(i)::isServerDisabled).pos(0, i * (TAB_SIZE + 2)));
            pages.addPage(
                sessions.get(i)
                    .channelsPage());
        }
        root.child(
            tab(
                sessions.size(),
                controller,
                GTGuiTextures.OVERLAY_BUTTON_SCREWDRIVER,
                "warpcovers.gui.tab.settings",
                () -> false).pos(0, sessions.size() * (TAB_SIZE + 2)));

        PagedWidget.Controller segments = new PagedWidget.Controller();
        ParentWidget<?> settings = new ParentWidget<>();
        settings.size(PAGE_W, PAGE_H);
        PagedWidget<?> segmentPages = new PagedWidget<>();
        segmentPages.controller(segments)
            .pos(0, SEGMENT_H)
            .size(PAGE_W, PAGE_H - SEGMENT_H);
        int segmentW = (PAGE_W - 2 * FIELD_GAP) / sessions.size();
        for (int i = 0; i < sessions.size(); i++) {
            PageButton segment = new PageButton(i, segments);
            segment.pos(i * (segmentW + FIELD_GAP), 0)
                .size(segmentW, SEGMENT_H - 2)
                .background(true, GTGuiTextures.BUTTON_STANDARD_PRESSED)
                .background(false, GTGuiTextures.BUTTON_STANDARD)
                .overlay(
                    IKey.lang(
                        "warpcovers.gui.section." + parts.get(i)
                            .channelType()
                            .key())
                        .color(BUTTON_TEXT));
            settings.child(segment);
            segmentPages.addPage(
                sessions.get(i)
                    .settingsPage());
        }
        settings.child(segmentPages);
        pages.addPage(settings);
        root.child(pages);

        addStatusBar(root, STATUS_Y, ROOT_W, () -> {
            int page = controller.isInitialised() ? controller.getActivePageIndex() : 0;
            if (page < sessions.size()) {
                return sessions.get(page);
            }
            return sessions.get(segments.isInitialised() ? segments.getActivePageIndex() : 0);
        });
        return root;
    }

    /** 折跃频道管理器的界面：没有覆盖板，三个类型各一个频道页和一个远程修改页。 */
    static ModularPanel managerPanel(EntityPlayer player, PanelSyncManager syncManager) {
        ModularPanel panel = ModularPanel
            .defaultPanel("warpcovers_manager", ROOT_W + 14, MANAGER_ROOT_H + 14 + PLAYER_INVENTORY_H);
        panel.child(new WarpCoverGui(null).managerRoot(syncManager, player));
        return panel.bindPlayerInventory();
    }

    /**
     * 自检用：配置面板和各弹窗平时只在客户端点击时才构建，构建期的错误（空指针、参数不合法）靠 JUnit 和服务端自检都发现不了。
     * 这里在服务端把它们各构建一次；对象创建过程不涉及渲染，所以能在服务端跑。
     *
     * @param cover 覆盖板；传 null 表示按折跃频道管理器构建
     * @param type  管理器要构建的频道类型；覆盖板忽略
     */
    public static void selfCheckBuildDialogs(BaseWarpCover cover, EntityPlayer player, ChannelType type,
        String channelId) {
        WarpCoverGui gui = new WarpCoverGui(cover);
        PanelSyncManager sync = new PanelSyncManager(new ModularSyncManager(false), false);
        boolean built = false;
        if (cover == null) {
            built = gui.buildDialogsForSelfCheck(gui.new Session(sync, player, type, "selfcheck_manager_"), channelId);
        } else {
            for (WarpPart part : cover.parts()) {
                built |= gui.buildDialogsForSelfCheck(
                    gui.new Session(sync, player, part, "selfcheck_" + part.channelType()
                        .key() + "_", PAGE_H),
                    channelId);
            }
        }
        if (!built) {
            throw new IllegalStateException("自检的频道不在任何会话的频道列表里: " + channelId);
        }
    }

    /** @return 这个会话的类型里有该频道，面板已构建 */
    private boolean buildDialogsForSelfCheck(Session session, String channelId) {
        session.parsedView = PanelSnapshot.parseChannels(PanelSnapshot.channels(session.panel, session.viewer));
        session.configChannelId = channelId;
        if (session.configRow() == null) {
            return false;
        }
        session.configPanel();
        session.quickPanel();
        session.tagPanel();
        session.createDialogPanel();
        session.deletePanel();
        return true;
    }

    private IWidget managerRoot(PanelSyncManager syncManager, EntityPlayer player) {
        ChannelType[] types = ChannelType.values();
        List<Session> sessions = new ArrayList<>();
        PagedWidget.Controller controller = new PagedWidget.Controller();
        PagedWidget<?> pages = new PagedWidget<>();
        pages.controller(controller)
            .pos(PAGE_X, 0)
            .size(PAGE_W, MANAGER_PAGE_H);
        ParentWidget<?> root = new ParentWidget<>();
        root.pos(7, 7)
            .size(ROOT_W, MANAGER_ROOT_H);
        for (int i = 0; i < types.length; i++) {
            int index = i;
            Session session = new Session(syncManager, player, types[i], "warpcovers_" + types[i].key() + "_");
            session.openEditor = () -> controller.setPage(types.length + index);
            session.closeEditor = () -> controller.setPage(index);
            sessions.add(session);
            root.child(
                tab(
                    i,
                    controller,
                    new ItemDrawable(sectionIcon(types[i])).asIcon()
                        .size(16),
                    "warpcovers.gui.tab.channels_" + types[i].key(),
                    session::isServerDisabled).pos(0, i * (TAB_SIZE + 2)));
            pages.addPage(session.channelsPage());
        }
        for (Session session : sessions) {
            pages.addPage(session.editorPage());
        }
        root.child(pages);
        addStatusBar(root, MANAGER_PAGE_H + 4, ROOT_W, () -> {
            int page = controller.isInitialised() ? controller.getActivePageIndex() : 0;
            return sessions.get(page % types.length);
        });
        return root;
    }

    private static ItemStack sectionIcon(ChannelType type) {
        switch (type) {
            case FLUID:
                return new ItemStack(Items.water_bucket);
            case ITEM:
                return new ItemStack(Blocks.chest);
            default:
                return new ItemStack(Items.redstone);
        }
    }

    private void addStatusBar(ParentWidget<?> root, int y, int width, Supplier<Session> active) {
        root.child(
            IKey.dynamic(
                () -> active.get()
                    .statusLine())
                .asWidget()
                .pos(0, y)
                .size(width, 10)
                .color(TEXT_COLOR));
        root.child(
            IKey.dynamic(
                () -> active.get()
                    .detailLine())
                .asWidget()
                .pos(0, y + 12)
                .size(width, 10)
                .color(
                    () -> active.get()
                        .detailIsError() ? ERROR_COLOR : TEXT_COLOR));
    }

    private PageButton tab(int index, PagedWidget.Controller controller, IDrawable icon, String tooltipKey,
        java.util.function.BooleanSupplier serverDisabled) {
        IDrawable grey = new Rectangle().color(0xB0505050);
        PageButton button = new PageButton(index, controller);
        button.pos(0, 0)
            .size(TAB_SIZE, TAB_SIZE)
            .background(true, GTGuiTextures.BUTTON_STANDARD_PRESSED)
            .background(false, GTGuiTextures.BUTTON_STANDARD)
            .overlay(icon, new DynamicDrawable(() -> serverDisabled.getAsBoolean() ? grey : IDrawable.EMPTY))
            .tooltipDynamic(tooltip -> {
                tooltip.addLine(tr(tooltipKey));
                if (serverDisabled.getAsBoolean()) {
                    tooltip.addLine(tr("warpcovers.status.disabled"));
                }
            })
            .tooltipAutoUpdate(true);
        return button;
    }

    private static String tr(String key, Object... args) {
        return args.length == 0 ? StatCollector.translateToLocal(key)
            : StatCollector.translateToLocalFormatted(key, args);
    }

    /** 带具体类型的 NBT 同步值，便于挂动态控件。 */
    static final class NbtValue extends GenericSyncValue<NBTTagCompound, NbtValue> {

        NbtValue(Supplier<NBTTagCompound> getter) {
            super(getter, ByteBufAdapters.NBT);
        }
    }

    /** 一次打开界面的会话：两端各建一份，服务端持有面板模型。 */
    private final class Session {

        private final PanelSyncManager sync;
        /** 这个会话操作的连接端：覆盖板上的部分，或管理器远程选中的连接端。客户端的管理器会话没有。 */
        private final PanelEndpoint endpoint;
        private final ChannelType type;
        /** 折跃频道管理器的会话：频道页没有连接操作，配置面板多一个连接端列表。 */
        private final boolean manager;
        private final RemoteEndpoint remote;
        /** 频道页高度：管理器界面更高，双栏界面在构建时改。 */
        private int channelsH;
        /** 频道页宽度：双栏界面的左栏更宽。 */
        private int pageW = PAGE_W;
        /** 只有双栏界面才有。 */
        private DualPaneLayout layout;
        /** 单类型覆盖板的双栏界面：点频道只是选中，连接操作在右栏顶部。 */
        private boolean dualPane;
        /** 双栏界面选中的频道（只在客户端）；没选时跟随已连接的频道。 */
        private String pickedId = "";
        private String lastConnectedId = "";
        private long lastConnectionClick;
        /** 同步键前缀：聚合覆盖板的三个会话不能互相冲突。 */
        private final String syncPrefix;
        /** 设置页高度：聚合覆盖板要给顶部的类型切换让位。 */
        private final int settingsH;
        private final boolean client;
        private final CoverPanel panel;
        private final Viewer viewer;
        private final NbtValue channels;
        private final NbtValue recentValue;
        private final NbtValue filter;
        private final NbtValue status;
        private final LongSyncValue priority;
        private final LongSyncValue rate;
        private final LongSyncValue period;
        private final LongSyncValue effectiveRate;
        private final LongSyncValue effectivePeriod;
        private IPanelHandler createDialog;
        private IPanelHandler configDialog;
        private IPanelHandler deleteDialog;
        private IPanelHandler tagDialog;
        private IPanelHandler quickDialog;
        /** 服务端正在向客户端列出连接端的频道；配置面板打开时才设置。 */
        private String listedChannelId = "";
        private final NbtValue endpointsValue;
        private final NbtValue selectedValue;
        private int endpointPage;
        /** 服务端：配置面板正在看哪个频道的名单；客户端打开配置面板时通知。 */
        private String filterWatchId = "";
        /** 配置面板名单窗口的滚动位置（行）。 */
        private int filterScroll;
        private NBTTagCompound parsedFilterRaw;
        private String parsedFilterChannel = "";
        private PanelSnapshot.FilterView parsedFilter;
        /** 管理器：切到连接端编辑页、返回频道页。 */
        private Runnable openEditor = () -> {};
        private Runnable closeEditor = () -> {};
        /** 服务端最近一次汇总的候选；打开弹窗时才刷新。 */
        private NBTTagCompound quickSnapshot = new NBTTagCompound();
        private final NbtValue quickValue;
        private NBTTagCompound quickRaw;
        private FilterCandidates quickParsed = FilterCandidates.NONE;
        private final Set<String> quickSelected = new HashSet<>();
        private int quickPage;
        /** 物体图标缓存：图标每帧读取。 */
        private final Map<String, IDrawable> objectIcons = new HashMap<>();
        /** 配置面板正在编辑的频道。 */
        private String configChannelId = "";
        /** 配置面板打开后是否操作过：操作过才显示服务端的反馈，避免显示打开前的旧提示。 */
        private boolean configActed;
        private String configLocalError = "";
        private NBTTagCompound parsedChannels;
        private PanelSnapshot.ChannelsView parsedView;

        Session(PanelSyncManager sync, EntityPlayer player, WarpPart part, String syncPrefix, int settingsH) {
            this(sync, player, part, part.channelType(), syncPrefix, settingsH, PAGE_H, false);
        }

        /** 管理器会话。 */
        Session(PanelSyncManager sync, EntityPlayer player, ChannelType type, String syncPrefix) {
            this(sync, player, null, type, syncPrefix, PAGE_H, MANAGER_PAGE_H, true);
        }

        private Session(PanelSyncManager sync, EntityPlayer player, PanelEndpoint given, ChannelType type,
            String syncPrefix, int settingsH, int channelsH, boolean manager) {
            this.sync = sync;
            this.type = type;
            this.channelsH = channelsH;
            this.manager = manager;
            this.syncPrefix = syncPrefix;
            this.settingsH = settingsH;
            this.client = sync.isClient();
            if (client) {
                panel = null;
                viewer = null;
                remote = null;
                endpoint = given;
            } else {
                // 不能用 sync.getPlayer()：构建界面时同步管理器还没绑定到容器
                viewer = new Viewer(
                    player.getUniqueID()
                        .toString(),
                    player.getCommandSenderName());
                remote = manager ? new RemoteEndpoint(WarpServices.hub(), viewer) : null;
                endpoint = manager ? remote : given;
                panel = new CoverPanel(WarpServices.hub(), type, endpoint, new Random());
            }
            channels = new NbtValue(() -> client ? new NBTTagCompound() : PanelSnapshot.channels(panel, viewer));
            recentValue = new NbtValue(() -> client ? new NBTTagCompound() : PanelSnapshot.recents(panel, viewer));
            sync.syncValue(syncPrefix + "recents", recentValue);
            filter = new NbtValue(
                () -> client ? new NBTTagCompound() : PanelSnapshot.filter(panel.hub(), viewer, filterWatchId));
            status = new NbtValue(
                () -> client ? new NBTTagCompound()
                    : PanelSnapshot.status(
                        panel,
                        MinecraftServer.getServer()
                            .getTickCounter()));
            sync.syncValue(syncPrefix + "channels", channels);
            sync.syncValue(syncPrefix + "filter", filter);
            quickValue = new NbtValue(() -> quickSnapshot);
            sync.syncValue(syncPrefix + "quick", quickValue);
            endpointsValue = new NbtValue(
                () -> client || listedChannelId.isEmpty() ? new NBTTagCompound()
                    : PanelSnapshot.endpoints(panel, viewer, listedChannelId));
            selectedValue = new NbtValue(
                () -> client || remote == null ? new NBTTagCompound() : PanelSnapshot.selected(remote));
            sync.syncValue(syncPrefix + "endpoints", endpointsValue);
            sync.syncValue(syncPrefix + "selected", selectedValue);
            sync.syncValue(syncPrefix + "status", status);
            // 数字框要把改动发回服务端，这版 ModularUI2 默认不允许，必须 allowC2S
            priority = new LongSyncValue(
                () -> client ? 0
                    : endpoint.config()
                        .priority(),
                value -> {
                    if (!client) {
                        panel.setPriority((int) NumberInput.clamp(value, Integer.MIN_VALUE, Integer.MAX_VALUE));
                    }
                }).allowC2S();
            rate = new LongSyncValue(
                () -> client ? 0
                    : endpoint.config()
                        .rate(),
                value -> {
                    if (!client) {
                        panel.setRate(value);
                    }
                }).allowC2S();
            period = new LongSyncValue(
                () -> client ? 0
                    : endpoint.config()
                        .period(),
                value -> {
                    if (!client) {
                        panel.setPeriod((int) NumberInput.clamp(value, 0, Integer.MAX_VALUE));
                    }
                }).allowC2S();
            effectiveRate = new LongSyncValue(() -> client ? 0 : panel.effectiveRate());
            effectivePeriod = new LongSyncValue(() -> client ? 0 : panel.effectivePeriod());
            sync.syncValue(syncPrefix + "priority", priority);
            sync.syncValue(syncPrefix + "rate", rate);
            sync.syncValue(syncPrefix + "period", period);
            sync.syncValue(syncPrefix + "effective_rate", effectiveRate);
            sync.syncValue(syncPrefix + "effective_period", effectivePeriod);

            action("create", buf -> {
                String name = NetworkUtils.readStringSafe(buf);
                boolean connect = buf.readBoolean();
                panel.createChannel(viewer, name, connect && !manager);
            });
            action("toggle", buf -> panel.toggleConnection(viewer, NetworkUtils.readStringSafe(buf)));
            if (!manager) {
                action("connection", buf -> {
                    String channelId = NetworkUtils.readStringSafe(buf);
                    ConnectionAction expected = ConnectionAction.valueOf(NetworkUtils.readStringSafe(buf));
                    panel.applyConnection(viewer, channelId, expected);
                });
            }
            action("state", buf -> {
                InteractionState state = parseState(NetworkUtils.readStringSafe(buf));
                if (state != null) {
                    panel.setState(viewer, state);
                }
            });
            if (manager) {
                action("ep_list", buf -> listedChannelId = NetworkUtils.readStringSafe(buf));
                action("ep_select", buf -> {
                    PanelNotice failure = remote.select(NetworkUtils.readStringSafe(buf));
                    if (failure != null) {
                        panel.report(failure);
                    }
                });
                action("ep_clear", buf -> remote.clear());
                action("ep_disconnect", buf -> {
                    if (remote.isEditable()) {
                        remote.disconnect();
                        panel.report(new PanelNotice("remote_disconnected", "", false));
                    }
                });
            }
            action("quick_refresh", buf -> quickSnapshot = PanelSnapshot.candidates(panel.filterCandidates()));
            registerConfigActions();
            action("search", buf -> panel.setSearch(NetworkUtils.readStringSafe(buf)));
            action("tagfilter", buf -> panel.setTagFilter(NetworkUtils.readStringSafe(buf)));
            action(
                "tag_add",
                buf -> panel.addTag(viewer, NetworkUtils.readStringSafe(buf), NetworkUtils.readStringSafe(buf)));
            action(
                "tag_remove",
                buf -> panel.removeTag(viewer, NetworkUtils.readStringSafe(buf), NetworkUtils.readStringSafe(buf)));
        }

        private void action(String key, Consumer<PacketBuffer> handler) {
            sync.registerSyncedAction(syncPrefix + key, false, true, buf -> {
                if (panel == null) {
                    return;
                }
                try {
                    handler.accept(buf);
                } catch (RuntimeException e) {
                    WarpCovers.LOG.warn("处理覆盖板界面请求 {} 失败", key, e);
                }
            });
        }

        private void send(String key, Consumer<PacketBuffer> writer) {
            sync.callSyncedAction(syncPrefix + key, writer);
        }

        /** @param popUp 从机器界面点开：机器界面已经有背包，左栏不再放，频道列表占满 */
        IWidget buildDualPane(boolean popUp, DualPaneLayout layout) {
            dualPane = true;
            this.layout = layout;
            pageW = layout.leftW;
            int reserved = popUp ? 0 : INVENTORY_H + LEFT_GAP;
            channelsH = layout.height - reserved - STATUS_H - LEFT_GAP;

            ParentWidget<?> root = new ParentWidget<>();
            root.size(layout.width, layout.height);
            root.child(channelsPage());
            addStatusBar(root, channelsH + LEFT_GAP, layout.leftW, () -> this);
            if (!popUp) {
                root.child(
                    SlotGroupWidget.playerInventory(false)
                        .pos((layout.leftW - INVENTORY_W) / 2, layout.height - INVENTORY_H));
            }
            root.child(rightPane());
            return root;
        }

        // ---- 双栏界面的右栏 ----

        private int cardH() {
            return layout.height - CARD_Y;
        }

        private int cardInnerW() {
            return layout.rightW - 2 * CARD_PADDING;
        }

        private int cardInnerH() {
            return cardH() - 2 * CARD_PADDING;
        }

        /** 选中的频道；当前连接变化时（如新建并连接）选中跟过去。没有选中或频道已不可见时为 null。 */
        private PanelSnapshot.ChannelRow pickedRow() {
            PanelSnapshot.ChannelsView view = channelsView();
            if (!view.currentId.equals(lastConnectedId)) {
                lastConnectedId = view.currentId;
                if (!lastConnectedId.isEmpty()) {
                    pickedId = lastConnectedId;
                }
            }
            return view.row(pickedId);
        }

        private boolean isPicked(String channelId) {
            PanelSnapshot.ChannelRow picked = pickedRow();
            return picked != null && picked.id.equals(channelId);
        }

        private boolean pickedIsConnected() {
            PanelSnapshot.ChannelRow picked = pickedRow();
            return picked != null && channelsView().isConnected(picked.id);
        }

        private ConnectionAction pickedAction() {
            return ConnectionAction.of(channelsView().currentId, pickedId);
        }

        private String paneStatus() {
            PanelSnapshot.ChannelsView view = channelsView();
            if (pickedIsConnected()) {
                return tr("warpcovers.gui.pane.connected");
            }
            PanelSnapshot.ChannelRow current = view.row(view.currentId);
            return current == null ? tr("warpcovers.gui.pane.not_connected")
                : tr("warpcovers.gui.pane.connected_other", current.name);
        }

        private IWidget rightPane() {
            ParentWidget<?> pane = new ParentWidget<>();
            pane.pos(layout.rightX, 0)
                .size(layout.rightW, layout.height);
            pane.child(new DynamicDrawable(() -> {
                PanelSnapshot.ChannelRow picked = pickedRow();
                return picked == null ? IDrawable.EMPTY : new Rectangle().color(0xFF000000 | picked.color);
            }).asWidget()
                .pos(0, 1)
                .size(PANE_SWATCH, PANE_SWATCH));
            pane.child(new ScrollingTextWidget(IKey.dynamic(() -> {
                PanelSnapshot.ChannelRow picked = pickedRow();
                return picked == null ? tr("warpcovers.gui.pane.pick") : picked.name;
            })).pos(PANE_SWATCH + 3, 2)
                .size(layout.rightW - PANE_SWATCH - 3, 9)
                .color(TEXT_COLOR));
            pane.child(
                new ScrollingTextWidget(IKey.dynamic(() -> pickedRow() == null ? "" : paneStatus()))
                    .pos(PANE_SWATCH + 3, 12)
                    .size(layout.rightW - PANE_SWATCH - 3, 9)
                    .color(() -> pickedIsConnected() ? CONNECTED_COLOR : OWNER_COLOR));
            pane.child(connectionButton());

            pane.child(
                PaneStyle.CARD.asWidget()
                    .pos(0, CARD_Y)
                    .size(layout.rightW, cardH()));
            PagedWidget.Controller controller = new PagedWidget.Controller();
            PagedWidget<?> pages = new PagedWidget<>();
            pages.controller(controller)
                .pos(CARD_PADDING, CARD_Y + CARD_PADDING)
                .size(cardInnerW(), cardInnerH());
            // 右栏变宽时标签跟着变宽，字不必再挤
            int tabExtra = (layout.rightW - TAB_W_LONG - 3 * TAB_W_SHORT) / TAB_SLOTS;
            int shortW = TAB_W_SHORT + tabExtra;
            int longW = TAB_W_LONG + tabExtra;
            int x = 0;
            int index = 0;
            pages.addPage(connectionPage());
            pane.child(paneTab(index++, controller, x, shortW, "connection"));
            x += shortW;
            if (type.hasFilter()) {
                pages.addPage(placeholderPage());
                pane.child(paneTab(index++, controller, x, longW, "filter"));
                x += longW;
            }
            pages.addPage(placeholderPage());
            pane.child(paneTab(index++, controller, x, shortW, "config"));
            x += shortW;
            pages.addPage(placeholderPage());
            pane.child(paneTab(index, controller, x, shortW, "permission"));
            pane.child(pages);
            return pane;
        }

        /** 文字随状态变化的连接按钮；请求带上玩家看到的操作，和服务端现状不一致时不执行。 */
        private IWidget connectionButton() {
            ButtonWidget<?> button = new ButtonWidget<>();
            button.pos(0, PANE_BUTTON_Y)
                .size(layout.rightW, ROW_H)
                .background(
                    new DynamicDrawable(
                        () -> pickedAction() == ConnectionAction.DISCONNECT ? GTGuiTextures.BUTTON_STANDARD
                            : PaneStyle.GREEN_BUTTON))
                .overlay(
                    IKey.dynamic(() -> tr("warpcovers.gui.connection." + lower(pickedAction().name())))
                        .color(BUTTON_TEXT))
                .setEnabledIf(w -> pickedRow() != null);
            button.onMousePressed(mouse -> {
                PanelSnapshot.ChannelRow picked = pickedRow();
                if (mouse != 0 || picked == null) {
                    return false;
                }
                // 新快照一两个 tick 就回来，连点的第二下看到的已是相反的操作，服务端分不出是误触，只能在这里拦
                long now = System.currentTimeMillis();
                if (now - lastConnectionClick < CONNECTION_CLICK_COOLDOWN_MS) {
                    return true;
                }
                lastConnectionClick = now;
                ConnectionAction action = pickedAction();
                send("connection", buf -> {
                    NetworkUtils.writeStringSafe(buf, picked.id);
                    NetworkUtils.writeStringSafe(buf, action.name());
                });
                return true;
            });
            return button;
        }

        private PageButton paneTab(int index, PagedWidget.Controller controller, int x, int width, String key) {
            String lang = "warpcovers.gui.pane.tab." + key;
            // 标签按钮的主题默认带文字阴影，深色字在浅色卡片上会糊成重影
            IDrawable selected = IKey.lang(lang)
                .color(TEXT_COLOR)
                .shadow(false);
            IDrawable idle = IKey.lang(lang)
                .color(0xFFC8C8C8);
            PageButton tab = new PageButton(index, controller);
            tab.pos(x, TAB_Y)
                .size(width, TAB_H)
                .background(true, PaneStyle.TAB_SELECTED)
                .background(false, PaneStyle.TAB_IDLE)
                .overlay(
                    new DynamicDrawable(
                        () -> controller.isInitialised() && controller.getActivePageIndex() == index ? selected
                            : idle));
            return tab;
        }

        /** 「连接」分页：这块覆盖板自己的设置。没连着选中的频道时整页灰显，点不到。 */
        private IWidget connectionPage() {
            ParentWidget<?> page = new ParentWidget<>();
            page.size(cardInnerW(), cardInnerH());
            page.child(
                IKey.lang("warpcovers.gui.pane.state")
                    .asWidget()
                    .pos(0, 0)
                    .size(cardInnerW(), 9)
                    .color(TEXT_COLOR));
            int stateW = (cardInnerW() + 1) / STATES.length;
            for (int i = 0; i < STATES.length; i++) {
                page.child(paneStateButton(STATES[i], i * stateW, stateW - 1));
            }
            page.child(
                IKey.lang("warpcovers.gui.pane.transfer")
                    .asWidget()
                    .pos(0, TRANSFER_LABEL_Y)
                    .size(cardInnerW(), 9)
                    .color(TEXT_COLOR));
            addTransferRow(
                page,
                0,
                "warpcovers.gui.field.priority",
                priority,
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                () -> tr("warpcovers.gui.field.priority_tip"));
            addTransferRow(page, 1, "warpcovers.gui.field.rate", rate, 0, Long.MAX_VALUE, this::rateTooltip);
            addTransferRow(page, 2, "warpcovers.gui.field.period", period, 0, Integer.MAX_VALUE, this::periodTooltip);
            if (!type.hasFilter()) {
                addEnergyNotes(page, ENERGY_NOTES_Y, cardInnerW());
            }

            InputBlocker grey = new InputBlocker();
            grey.pos(0, 0)
                .size(cardInnerW(), cardInnerH())
                .background(new Rectangle().color(PaneStyle.GREY_OUT))
                .setEnabledIf(w -> !pickedIsConnected());
            grey.child(
                IKey.lang("warpcovers.gui.pane.connect_first")
                    .asWidget()
                    .alignment(Alignment.Center)
                    .pos(0, CONNECT_HINT_Y)
                    .size(cardInnerW(), 9)
                    .color(OWNER_COLOR));
            page.child(grey);
            return page;
        }

        private IWidget paneStateButton(InteractionState state, int x, int width) {
            ButtonWidget<?> button = new ButtonWidget<>();
            button.pos(x, STATE_BUTTON_Y)
                .size(width, ROW_H)
                .background(
                    new DynamicDrawable(
                        () -> pickedIsConnected() && channelsView().state == state ? PaneStyle.GREEN_BUTTON
                            : GTGuiTextures.BUTTON_STANDARD))
                .overlay(
                    IKey.lang(WarpCoverItem.stateKey(state))
                        .color(BUTTON_TEXT))
                .addTooltipLine(tr("warpcovers.gui.state_tip." + lower(state.name())));
            button.onMousePressed(mouse -> {
                if (mouse != 0) {
                    return false;
                }
                send("state", buf -> NetworkUtils.writeStringSafe(buf, state.name()));
                return true;
            });
            return button;
        }

        private void addTransferRow(ParentWidget<?> page, int row, String labelKey, LongSyncValue value, long min,
            long max, Supplier<String> tooltip) {
            int y = TRANSFER_ROW_Y + row * TRANSFER_ROW_H;
            int fieldW = Math.max(TRANSFER_FIELD_MIN_W, cardInnerW() * 2 / 5);
            page.child(
                IKey.lang(labelKey)
                    .asWidget()
                    .pos(0, y + 2)
                    .size(cardInnerW() - fieldW - 2, 9)
                    .color(TEXT_COLOR));
            page.child(
                numberField(value, min, max, tooltip).pos(cardInnerW() - fieldW, y)
                    .size(fieldW, 13));
        }

        /** 还没做的分页：配置和名单先用齿轮弹窗改。 */
        private IWidget placeholderPage() {
            return IKey.lang("warpcovers.gui.pane.placeholder")
                .asWidget()
                .alignment(Alignment.TopLeft)
                .size(cardInnerW(), cardInnerH())
                .color(OWNER_COLOR);
        }

        // ---- 频道页 ----

        private IWidget channelsPage() {
            ParentWidget<?> page = new ParentWidget<>();
            page.size(pageW, channelsH);

            ButtonWidget<?> create = new ButtonWidget<>();
            create.pos(0, 0)
                .size(CREATE_W, ROW_H)
                .background(
                    new DynamicDrawable(
                        () -> createBlock() != null ? GTGuiTextures.BUTTON_STANDARD_DISABLED
                            : GTGuiTextures.BUTTON_STANDARD))
                .overlay(
                    IKey.dynamic(() -> (createBlock() != null ? "\u00a78" : "") + tr("warpcovers.gui.new_channel"))
                        .color(BUTTON_TEXT))
                .tooltipDynamic(tooltip -> {
                    CreateFailure blocked = createBlock();
                    if (blocked != null) {
                        tooltip.addLine(tr("warpcovers.notice." + blocked.key(), ""));
                    }
                })
                .tooltipAutoUpdate(true);
            create.onMousePressed(mouse -> {
                if (createBlock() == null) {
                    openCreateDialog(create);
                }
                return true;
            });
            page.child(create);
            addSearchBar(page);

            DynamicLinkedSyncHandler<NbtValue> dynamic = new DynamicLinkedSyncHandler<>(channels)
                .widgetProvider((manager, value) -> channelList(PanelSnapshot.parseChannels(value.getValue())));
            DynamicSyncedWidget<?> holder = new DynamicSyncedWidget<>();
            holder.syncHandler(dynamic)
                .pos(0, ROW_H + 3)
                .size(pageW, channelsH - ROW_H - 3);
            page.child(holder);
            return page;
        }

        private CreateFailure createBlock() {
            return channelsView().createBlock;
        }

        /** 当前频道快照；每帧多处读取，快照没变就不重新解析。 */
        private PanelSnapshot.ChannelsView channelsView() {
            NBTTagCompound raw = channels.getValue();
            if (parsedView == null || raw != parsedChannels) {
                parsedChannels = raw;
                parsedView = PanelSnapshot.parseChannels(raw);
            }
            return parsedView;
        }

        private IWidget channelList(PanelSnapshot.ChannelsView view) {
            ListWidget<IWidget, ?> list = new ListWidget<>();
            list.size(pageW, channelsH - ROW_H - 3);
            boolean any = false;
            ChannelGroup lastGroup = null;
            for (PanelSnapshot.ChannelRow row : view.rows) {
                if (!row.shown) {
                    continue;
                }
                any = true;
                boolean connected = !manager && view.isConnected(row.id);
                if (!connected && row.group != lastGroup) {
                    lastGroup = row.group;
                    list.child(
                        IKey.lang("warpcovers.gui.group." + row.group.key())
                            .asWidget()
                            .size(rowW(), 10)
                            .color(OWNER_COLOR));
                }
                list.child(channelRow(row, connected));
                if (connected && !dualPane) {
                    list.child(stateRow(view.state));
                }
            }
            if (!any) {
                list.child(
                    IKey.lang(view.rows.isEmpty() ? "warpcovers.gui.no_channels" : "warpcovers.gui.no_match")
                        .asWidget()
                        .size(rowW(), ROW_H)
                        .color(OWNER_COLOR));
            }
            return list;
        }

        /** 列表项宽度：给滚动条留位。 */
        private int rowW() {
            return pageW - 8;
        }

        /** 第一行：颜色、频道名、最近传输的图标；第二行：归属者、连接端数量、私人标签。 */
        private IWidget channelRow(PanelSnapshot.ChannelRow row, boolean connected) {
            boolean gear = !manager && row.hasConfigPanel();
            int width = gear ? rowW() - 15 : rowW();
            boolean showRecents = type.hasFilter();
            int iconsW = showRecents ? RECENT_ICONS * RECENT_ICON : 0;
            ParentWidget<?> content = new ParentWidget<>();
            content.size(width, CHANNEL_ROW_H);
            content.child(
                new Rectangle().color(0xFF000000 | row.color)
                    .asWidget()
                    .pos(3, 2)
                    .size(8, 8));
            content.child(
                new ScrollingTextWidget(IKey.str(row.name)).pos(14, 2)
                    .size(width - 16 - iconsW, 8)
                    .color(connected ? CONNECTED_ROW_COLOR : BUTTON_TEXT));
            if (showRecents) {
                for (int i = 0; i < RECENT_ICONS; i++) {
                    int index = i;
                    content.child(
                        new DynamicDrawable(() -> recentIcon(row.id, index)).asWidget()
                            .pos(width - iconsW + i * RECENT_ICON, 1)
                            .size(RECENT_ICON - 1, RECENT_ICON - 1));
                }
            }
            content.child(
                new ScrollingTextWidget(IKey.str(rowDetail(row))).pos(14, 11)
                    .size(width - 16, 8)
                    .color(BUTTON_DETAIL));

            ButtonWidget<?> button = new ButtonWidget<>();
            button.size(width, CHANNEL_ROW_H)
                .child(content)
                .tooltipDynamic(tooltip -> {
                    tooltip.addLine(row.name);
                    tooltip.addLine(tr("warpcovers.gui.owner", row.ownerName));
                    if (!row.note.isEmpty()) {
                        tooltip.addLine(row.note);
                    }
                    tooltip.addLine(tr("warpcovers.gui.row.counts_tip", row.inputs, row.outputs));
                    if (row.hasFilter) {
                        tooltip.addLine(filterSummary(row));
                    }
                    if (showRecents) {
                        for (String key : recents(row.id)) {
                            tooltip.addLine(tr("warpcovers.gui.row.recent", objectLabel(key)));
                        }
                    }
                    tooltip.addLine(tr(rowClickTipKey(connected)));
                    tooltip.addLine(tr("warpcovers.gui.row.tags"));
                })
                .tooltipAutoUpdate(true);
            if (connected) {
                button.background(GTGuiTextures.BUTTON_STANDARD_PRESSED);
            }
            if (dualPane) {
                IDrawable frame = new Rectangle().color(PICKED_FRAME)
                    .hollow(1);
                button.overlay(new DynamicDrawable(() -> isPicked(row.id) ? frame : IDrawable.EMPTY));
            }
            button.onMousePressed(mouse -> {
                if (mouse == 1) {
                    openTagDialog(button, row.id);
                    return true;
                }
                if (mouse != 0) {
                    return false;
                }
                if (manager) {
                    openConfigDialog(button, row.id);
                    return true;
                }
                if (dualPane) {
                    pickedId = row.id;
                    return true;
                }
                send("toggle", buf -> NetworkUtils.writeStringSafe(buf, row.id));
                return true;
            });
            if (!gear) {
                return button;
            }
            ParentWidget<?> line = new ParentWidget<>();
            line.size(rowW(), CHANNEL_ROW_H);
            line.child(button);
            ButtonWidget<?> open = new ButtonWidget<>();
            open.pos(rowW() - 14, 4)
                .size(12, 12)
                .overlay(GuiTextures.GEAR)
                .addTooltipLine(tr("warpcovers.gui.config.open"));
            open.onMousePressed(mouse -> {
                if (mouse != 0) {
                    return false;
                }
                openConfigDialog(open, row.id);
                return true;
            });
            line.child(open);
            return line;
        }

        private String rowClickTipKey(boolean connected) {
            if (manager) {
                return "warpcovers.gui.row.manage";
            }
            if (dualPane) {
                return "warpcovers.gui.row.select";
            }
            return connected ? "warpcovers.gui.row.disconnect" : "warpcovers.gui.row.connect";
        }

        private String rowDetail(PanelSnapshot.ChannelRow row) {
            StringBuilder text = new StringBuilder(row.ownerName);
            text.append("  ")
                .append(tr("warpcovers.gui.row.counts", row.inputs, row.outputs));
            if (row.hasFilter) {
                text.append("  ")
                    .append(filterSummary(row));
            }
            for (String tag : row.tags) {
                text.append("  #")
                    .append(tag);
            }
            return text.toString();
        }

        /** 频道名单的概要，如「黑名单·空（全部放行）」「白名单·3 项」，让玩家在频道页就知道名单存在。 */
        private String filterSummary(PanelSnapshot.ChannelRow row) {
            String mode = row.filterWhitelist ? "white" : "black";
            return row.filterCount == 0 ? tr("warpcovers.gui.filter.summary." + mode + "_empty")
                : tr("warpcovers.gui.filter.summary." + mode, row.filterCount);
        }

        private List<String> recents(String channelId) {
            return PanelSnapshot.parseRecents(recentValue.getValue(), channelId);
        }

        private IDrawable recentIcon(String channelId, int index) {
            List<String> keys = recents(channelId);
            if (index >= keys.size()) {
                return IDrawable.EMPTY;
            }
            return objectIcons.computeIfAbsent(keys.get(index), this::objectIcon);
        }

        // ---- 搜索与标签筛选 ----

        private void addSearchBar(ParentWidget<?> page) {
            String[] text = { "" };
            TextFieldWidget field = new TextFieldWidget();
            field.value(new StringValue.Dynamic(() -> text[0], value -> {
                if (!value.equals(text[0])) {
                    text[0] = value;
                    send("search", buf -> NetworkUtils.writeStringSafe(buf, value));
                }
            }))
                .hintText(tr("warpcovers.gui.search"));
            field.pos(CREATE_W + 2, 1)
                .size(pageW - CREATE_W - TAG_FILTER_W - 4, 12)
                .addTooltipLine(tr("warpcovers.gui.search_tip"));
            page.child(field);

            ButtonWidget<?> tags = new ButtonWidget<>();
            tags.pos(pageW - TAG_FILTER_W, 0)
                .size(TAG_FILTER_W, ROW_H)
                .overlay(IKey.dynamic(() -> {
                    String current = channelsView().tagFilter;
                    return current.isEmpty() ? tr("warpcovers.gui.tag.all") : "#" + current;
                })
                    .color(BUTTON_TEXT))
                .addTooltipLine(tr("warpcovers.gui.tag.filter_tip"));
            tags.onMousePressed(mouse -> {
                String next = mouse == 1 ? "" : channelsView().nextTagFilter();
                send("tagfilter", buf -> NetworkUtils.writeStringSafe(buf, next));
                return true;
            });
            page.child(tags);
        }

        // ---- 私人标签弹窗 ----

        private void openTagDialog(IWidget anchor, String channelId) {
            if (tagDialog == null) {
                tagDialog = IPanelHandler.simple(anchor.getPanel(), (main, player) -> tagPanel(), true);
            }
            if (tagDialog.isPanelOpen()) {
                return;
            }
            configChannelId = channelId;
            configActed = false;
            configLocalError = "";
            tagDialog.deleteCachedPanel();
            tagDialog.openPanel();
        }

        private Dialog<Boolean> tagPanel() {
            Dialog<Boolean> dialog = new Dialog<>(syncPrefix + "channel_tags");
            dialog.setDraggable(false);
            dialog.setCloseOnOutOfBoundsClick(true);
            dialog.size(172, 112);
            dialog.child(IKey.dynamic(() -> {
                PanelSnapshot.ChannelRow row = configRow();
                return tr("warpcovers.gui.tag.title", row == null ? "" : row.name);
            })
                .asWidget()
                .pos(8, 7)
                .size(156, 9)
                .color(0xFF222222));

            StringValue name = new StringValue("");
            TextFieldWidget field = new TextFieldWidget();
            field.value(name)
                .setMaxLength(PrivateTags.MAX_TAG_LENGTH)
                .setFocusOnGuiOpen(true);
            field.pos(8, 19)
                .size(116, 12);
            dialog.child(field);
            ButtonWidget<?> add = new ButtonWidget<>();
            add.pos(126, 18)
                .size(38, 14)
                .overlay(
                    IKey.lang("warpcovers.gui.tag.add")
                        .color(BUTTON_TEXT));
            add.onMousePressed(mouse -> {
                String tag = name.getStringValue();
                sendConfig("tag_add", buf -> NetworkUtils.writeStringSafe(buf, tag));
                name.setStringValue("");
                return true;
            });
            dialog.child(add);

            for (int i = 0; i < PrivateTags.MAX_TAGS_PER_CHANNEL; i++) {
                int index = i;
                ButtonWidget<?> tagButton = new ButtonWidget<>();
                tagButton.pos(8 + (i % 2) * 79, 35 + (i / 2) * 13)
                    .size(77, 12)
                    .overlay(
                        IKey.dynamic(() -> "#" + tagAt(index))
                            .color(BUTTON_TEXT))
                    .addTooltipLine(tr("warpcovers.gui.tag.remove"))
                    .setEnabledIf(w -> !tagAt(index).isEmpty());
                tagButton.onMousePressed(mouse -> {
                    String tag = tagAt(index);
                    sendConfig("tag_remove", buf -> NetworkUtils.writeStringSafe(buf, tag));
                    return true;
                });
                dialog.child(tagButton);
            }
            dialog.child(
                IKey.dynamic(this::configFeedback)
                    .asWidget()
                    .pos(8, 89)
                    .size(156, 9)
                    .color(() -> configFeedbackIsError() ? ERROR_COLOR : TEXT_COLOR));
            return dialog;
        }

        private String tagAt(int index) {
            PanelSnapshot.ChannelRow row = configRow();
            return row == null || index >= row.tags.size() ? "" : row.tags.get(index);
        }

        private IWidget stateRow(InteractionState current) {
            ParentWidget<?> row = new ParentWidget<>();
            row.size(ROW_W, ROW_H);
            int width = (ROW_W - 8) / STATES.length;
            for (int i = 0; i < STATES.length; i++) {
                InteractionState state = STATES[i];
                ButtonWidget<?> button = new ButtonWidget<>();
                button.pos(4 + i * width, 1)
                    .size(width - 2, ROW_H - 2)
                    .overlay(
                        IKey.lang(WarpCoverItem.stateKey(state))
                            .color(BUTTON_TEXT))
                    .background(
                        state == current ? GTGuiTextures.BUTTON_STANDARD_PRESSED : GTGuiTextures.BUTTON_STANDARD)
                    .addTooltipLine(
                        tr(
                            "warpcovers.gui.state_tip." + state.name()
                                .toLowerCase(java.util.Locale.ROOT)));
                button.onMousePressed(mouse -> {
                    if (mouse != 0) {
                        return false;
                    }
                    send("state", buf -> NetworkUtils.writeStringSafe(buf, state.name()));
                    return true;
                });
                row.child(button);
            }
            return row;
        }

        // ---- 新增频道弹窗（纯客户端，确定后以请求发往服务端） ----

        private void openCreateDialog(IWidget anchor) {
            if (createDialog == null) {
                createDialog = IPanelHandler.simple(anchor.getPanel(), (main, player) -> createDialogPanel(), true);
            }
            if (createDialog.isPanelOpen()) {
                return;
            }
            createDialog.deleteCachedPanel();
            createDialog.openPanel();
        }

        private Dialog<Boolean> createDialogPanel() {
            StringValue name = new StringValue("");
            BoolValue connect = new BoolValue(true);
            CreateFailure[] error = new CreateFailure[1];

            Dialog<Boolean> dialog = new Dialog<>(syncPrefix + "new_channel");
            dialog.setDraggable(false);
            dialog.setCloseOnOutOfBoundsClick(true);
            dialog.size(172, 96);

            dialog.child(
                IKey.lang("warpcovers.gui.dialog.title")
                    .asWidget()
                    .pos(8, 7)
                    .color(0xFF222222));
            dialog.child(
                IKey.lang("warpcovers.gui.dialog.name")
                    .asWidget()
                    .pos(8, 20)
                    .color(TEXT_COLOR));
            TextFieldWidget field = new TextFieldWidget();
            field.value(name)
                .setMaxLength(ChannelHub.MAX_NAME_LENGTH)
                .setFocusOnGuiOpen(true);
            field.pos(8, 30)
                .size(156, 12);
            dialog.child(field);
            dialog.child(
                IKey.dynamic(
                    () -> error[0] == null ? ""
                        : tr(
                            "warpcovers.notice." + error[0].key(),
                            name.getStringValue()
                                .trim()))
                    .asWidget()
                    .pos(8, 45)
                    .size(156, 9)
                    .color(ERROR_COLOR));

            ToggleButton toggle = new ToggleButton();
            toggle.value(connect)
                .background(false, GuiTextures.CHECK_BOX_EMPTY)
                .background(true, GuiTextures.CHECK_BOX_FULL)
                .pos(8, 57)
                .size(10, 10)
                .setEnabledIf(w -> !manager);
            dialog.child(toggle);
            dialog.child(
                IKey.lang("warpcovers.gui.dialog.connect")
                    .asWidget()
                    .pos(22, 58)
                    .color(TEXT_COLOR)
                    .setEnabledIf(w -> !manager));

            ButtonWidget<?> confirm = new ButtonWidget<>();
            confirm.pos(8, 74)
                .size(76, 14)
                .overlay(
                    IKey.lang("warpcovers.gui.dialog.confirm")
                        .color(BUTTON_TEXT));
            confirm.onMousePressed(mouse -> {
                String raw = name.getStringValue();
                CreateFailure failure = PanelSnapshot.parseChannels(channels.getValue())
                    .precheck(raw);
                if (failure != null) {
                    error[0] = failure;
                    return true;
                }
                boolean connectAfter = connect.getBoolValue();
                send("create", buf -> {
                    NetworkUtils.writeStringSafe(buf, raw);
                    buf.writeBoolean(connectAfter);
                });
                dialog.closeWith(true);
                return true;
            });
            dialog.child(confirm);

            ButtonWidget<?> cancel = new ButtonWidget<>();
            cancel.pos(88, 74)
                .size(76, 14)
                .overlay(
                    IKey.lang("warpcovers.gui.dialog.cancel")
                        .color(BUTTON_TEXT));
            cancel.onMousePressed(mouse -> {
                dialog.closeWith(false);
                return true;
            });
            dialog.child(cancel);
            return dialog;
        }

        // ---- 频道配置面板（客户端界面，每个改动都是发往服务端的请求，由 ChannelAdmin 检查权限） ----

        private void registerConfigActions() {
            action(
                "cfg_rename",
                buf -> report(
                    a -> a.rename(viewer, NetworkUtils.readStringSafe(buf), NetworkUtils.readStringSafe(buf))));
            action(
                "cfg_note",
                buf -> report(
                    a -> a.setNote(viewer, NetworkUtils.readStringSafe(buf), NetworkUtils.readStringSafe(buf))));
            action("cfg_color", buf -> {
                String id = NetworkUtils.readStringSafe(buf);
                int color = buf.readInt();
                report(a -> a.setColor(viewer, id, color));
            });
            action("cfg_dist", buf -> {
                String id = NetworkUtils.readStringSafe(buf);
                DistributionMode mode = DistributionMode.byKey(NetworkUtils.readStringSafe(buf));
                report(a -> a.setDistribution(viewer, id, mode));
            });
            action("cfg_perm", buf -> {
                String id = NetworkUtils.readStringSafe(buf);
                Identity who = Identity.valueOf(NetworkUtils.readStringSafe(buf));
                Permission permission = Permission.valueOf(NetworkUtils.readStringSafe(buf));
                boolean allowed = buf.readBoolean();
                report(a -> a.setPermission(viewer, id, who, permission, allowed));
            });
            action("cfg_public", buf -> {
                String id = NetworkUtils.readStringSafe(buf);
                boolean isPublic = buf.readBoolean();
                report(a -> a.setPublic(viewer, id, isPublic));
            });
            action("cfg_new", buf -> {
                String id = NetworkUtils.readStringSafe(buf);
                Identity who = Identity.valueOf(NetworkUtils.readStringSafe(buf));
                boolean allowed = buf.readBoolean();
                report(a -> a.setCreateAllowed(viewer, id, who, allowed));
            });
            action("cfg_delete", buf -> report(a -> a.delete(viewer, NetworkUtils.readStringSafe(buf))));
            registerFilterActions();
        }

        /** 频道过滤名单的请求：都带频道 ID，权限（修改配置）和能量没有名单都由 {@link ChannelAdmin} 判断。 */
        private void registerFilterActions() {
            action("cfg_watch", buf -> filterWatchId = NetworkUtils.readStringSafe(buf));
            action("flt_mode", buf -> {
                String id = NetworkUtils.readStringSafe(buf);
                boolean whitelist = buf.readBoolean();
                report(a -> a.setFilterWhitelist(viewer, id, whitelist));
            });
            action("flt_nbt", buf -> {
                String id = NetworkUtils.readStringSafe(buf);
                boolean matchNbt = buf.readBoolean();
                report(a -> a.setFilterMatchNbt(viewer, id, matchNbt));
            });
            action("flt_remove", buf -> {
                String id = NetworkUtils.readStringSafe(buf);
                String key = NetworkUtils.readStringSafe(buf);
                report(a -> a.removeFilterKey(viewer, id, key));
            });
            action("flt_clear", buf -> report(a -> a.clearFilter(viewer, NetworkUtils.readStringSafe(buf))));
            action("flt_add", buf -> {
                String id = NetworkUtils.readStringSafe(buf);
                List<String> keys = new ArrayList<>();
                int count = buf.readInt();
                for (int i = 0; i < count && i < CoverPanel.MAX_FILTER_CANDIDATES; i++) {
                    String key = NetworkUtils.readStringSafe(buf);
                    if (isKnownKey(id, key)) {
                        keys.add(key);
                    }
                }
                report(a -> a.addFilterKeys(viewer, id, keys));
            });
        }

        /** 客户端传来的名单键是否是这个频道能认出的物品或流体；认不出的丢弃，免得名单里堆无效项。 */
        private boolean isKnownKey(String channelId, String key) {
            Channel channel = panel.hub()
                .findChannel(channelId);
            if (channel == null || key == null || key.isEmpty()) {
                return false;
            }
            switch (channel.type()) {
                case ITEM:
                    return ItemKeys.prototype(key) != null;
                case FLUID:
                    return FluidRegistry.getFluid(key) != null;
                default:
                    return false;
            }
        }

        private void report(Function<ChannelAdmin, PanelNotice> operation) {
            panel.report(operation.apply(panel.admin()));
        }

        /** 发出配置请求，之后配置面板开始显示服务端的反馈。 */
        private void sendConfig(String key, Consumer<PacketBuffer> writer) {
            configActed = true;
            configLocalError = "";
            send(key, buf -> {
                NetworkUtils.writeStringSafe(buf, configChannelId);
                writer.accept(buf);
            });
        }

        private PanelSnapshot.ChannelRow configRow() {
            return channelsView().row(configChannelId);
        }

        private void openConfigDialog(IWidget anchor, String channelId) {
            if (configDialog == null) {
                configDialog = IPanelHandler.simple(anchor.getPanel(), (main, player) -> configPanel(), true);
            }
            if (configDialog.isPanelOpen()) {
                return;
            }
            configChannelId = channelId;
            configActed = false;
            configLocalError = "";
            filterScroll = 0;
            if (type.hasFilter()) {
                send("cfg_watch", buf -> NetworkUtils.writeStringSafe(buf, channelId));
            }
            if (manager) {
                endpointPage = 0;
                send("ep_list", buf -> NetworkUtils.writeStringSafe(buf, channelId));
            }
            configDialog.deleteCachedPanel();
            configDialog.openPanel();
        }

        private Dialog<Boolean> configPanel() {
            Dialog<Boolean> dialog = new Dialog<>(syncPrefix + "channel_config");
            dialog.setDraggable(false);
            dialog.setCloseOnOutOfBoundsClick(true);
            // 名单区只有流体和物品频道有，它把下面的反馈、按钮和管理器的连接端列表都往下推
            int extra = type.hasFilter() ? FILTER_BLOCK_H : 0;
            dialog.size(CFG_W, CFG_H + extra + (manager ? ENDPOINT_LIST_H : 0));
            // 删除确认和名单快捷配置弹窗挂在配置面板上，配置面板每次打开都是新对象，要跟着重建
            deleteDialog = null;
            quickDialog = null;
            PanelSnapshot.ChannelRow row = configRow();
            if (row == null) {
                return dialog;
            }

            dialog.child(IKey.dynamic(() -> {
                PanelSnapshot.ChannelRow current = configRow();
                return current == null ? tr("warpcovers.notice.channel_missing")
                    : tr("warpcovers.gui.config.title", current.name);
            })
                .asWidget()
                .pos(8, 7)
                .size(CFG_LEFT_W, 9)
                .color(0xFF222222));

            if (row.canConfigure) {
                addConfigText(
                    dialog,
                    20,
                    "warpcovers.gui.config.name",
                    row.name,
                    ChannelHub.MAX_NAME_LENGTH,
                    "cfg_rename");
                addConfigText(
                    dialog,
                    35,
                    "warpcovers.gui.config.note",
                    row.note,
                    ChannelAdmin.MAX_NOTE_LENGTH,
                    "cfg_note");
                addColorPicker(dialog, row);
                addDistributionButton(dialog);
            } else {
                dialog.child(
                    IKey.lang("warpcovers.gui.config.no_configure")
                        .asWidget()
                        .alignment(Alignment.TopLeft)
                        .pos(8, 20)
                        .size(CFG_LEFT_W, 30)
                        .color(OWNER_COLOR));
            }
            addPermissionMatrix(dialog, row);
            if (type.hasFilter()) {
                addFilterBlock(dialog, row);
            }

            dialog.child(
                IKey.dynamic(this::configFeedback)
                    .asWidget()
                    .pos(8, 100 + extra)
                    .size(CFG_W - 16, 9)
                    .color(() -> configFeedbackIsError() ? ERROR_COLOR : TEXT_COLOR));

            if (row.canDelete) {
                ButtonWidget<?> delete = new ButtonWidget<>();
                delete.pos(8, 113 + extra)
                    .size(70, 14)
                    .overlay(
                        IKey.lang("warpcovers.gui.config.delete")
                            .color(BUTTON_TEXT));
                delete.onMousePressed(mouse -> {
                    openDeleteDialog(dialog);
                    return true;
                });
                dialog.child(delete);
            }
            ButtonWidget<?> close = new ButtonWidget<>();
            close.pos(CFG_W - 78, 113 + extra)
                .size(70, 14)
                .overlay(
                    IKey.lang("warpcovers.gui.config.close")
                        .color(BUTTON_TEXT));
            close.onMousePressed(mouse -> {
                dialog.closeWith(true);
                return true;
            });
            dialog.child(close);
            if (manager) {
                addEndpointList(dialog, extra);
            }
            return dialog;
        }

        /** 文本框：失去焦点或回车时，内容有变化才提交；服务端拒绝时内容留在框里，下方显示原因。 */
        private void addConfigText(Dialog<Boolean> dialog, int y, String labelKey, String initial, int maxLength,
            String actionKey) {
            dialog.child(
                IKey.lang(labelKey)
                    .asWidget()
                    .pos(8, y + 2)
                    .size(30, 9)
                    .color(TEXT_COLOR));
            String[] text = { initial };
            TextFieldWidget field = new TextFieldWidget();
            field.value(new StringValue.Dynamic(() -> text[0], value -> {
                if (!value.equals(text[0])) {
                    text[0] = value;
                    sendConfig(actionKey, buf -> NetworkUtils.writeStringSafe(buf, value));
                }
            }))
                .setMaxLength(maxLength);
            field.pos(38, y)
                .size(CFG_LEFT_W - 30, 12);
            dialog.child(field);
        }

        private void addColorPicker(Dialog<Boolean> dialog, PanelSnapshot.ChannelRow row) {
            String[] hex = { ChannelColors.toHex(row.color) };
            for (int i = 0; i < ChannelColors.count(); i++) {
                int color = ChannelColors.dye(i);
                ButtonWidget<?> swatch = new ButtonWidget<>();
                swatch.pos(8 + (i % 8) * SWATCH, 50 + (i / 8) * SWATCH)
                    .size(SWATCH - 1, SWATCH - 1)
                    .background(new Rectangle().color(0xFF000000 | color));
                swatch.onMousePressed(mouse -> {
                    hex[0] = ChannelColors.toHex(color);
                    sendConfig("cfg_color", buf -> buf.writeInt(color));
                    return true;
                });
                dialog.child(swatch);
            }
            dialog.child(new DynamicDrawable(() -> {
                PanelSnapshot.ChannelRow current = configRow();
                return new Rectangle().color(0xFF000000 | (current == null ? 0 : current.color));
            }).asWidget()
                .pos(98, 51)
                .size(10, 10)
                .addTooltipLine(tr("warpcovers.gui.config.color_current")));
            TextFieldWidget field = new TextFieldWidget();
            field.value(new StringValue.Dynamic(() -> hex[0], value -> {
                if (value.equals(hex[0])) {
                    return;
                }
                hex[0] = value;
                int color = ChannelColors.parseHex(value);
                if (color < 0) {
                    configLocalError = tr("warpcovers.notice.bad_color");
                    return;
                }
                sendConfig("cfg_color", buf -> buf.writeInt(color));
            }))
                .setMaxLength(7);
            field.pos(110, 50)
                .size(46, 12)
                .addTooltipLine(tr("warpcovers.gui.config.color_hex"));
            dialog.child(field);
        }

        private void addDistributionButton(Dialog<Boolean> dialog) {
            ButtonWidget<?> button = new ButtonWidget<>();
            button.pos(8, 76)
                .size(CFG_LEFT_W, 14)
                .overlay(
                    IKey.dynamic(
                        () -> tr(
                            "warpcovers.gui.config.distribution",
                            tr("warpcovers.gui.config.distribution." + currentDistribution().key())))
                        .color(BUTTON_TEXT))
                .addTooltipLine(tr("warpcovers.gui.config.distribution_tip"));
            button.onMousePressed(mouse -> {
                DistributionMode next = currentDistribution() == DistributionMode.ROUND_ROBIN
                    ? DistributionMode.SEQUENTIAL
                    : DistributionMode.ROUND_ROBIN;
                sendConfig("cfg_dist", buf -> NetworkUtils.writeStringSafe(buf, next.key()));
                return true;
            });
            dialog.child(button);
        }

        private DistributionMode currentDistribution() {
            PanelSnapshot.ChannelRow current = configRow();
            return current == null ? DistributionMode.ROUND_ROBIN : current.distribution;
        }

        /** 四种身份 × 五项频道权限；管理者还多一列归属者级的「新增频道」。 */
        private void addPermissionMatrix(Dialog<Boolean> dialog, PanelSnapshot.ChannelRow row) {
            dialog.child(
                IKey.lang("warpcovers.gui.config.permissions")
                    .asWidget()
                    .pos(CFG_RIGHT_X, 7)
                    .size(CFG_W - CFG_RIGHT_X - 8, 9)
                    .color(0xFF222222));
            if (!row.canManage()) {
                dialog.child(
                    IKey.lang("warpcovers.gui.config.no_manage")
                        .asWidget()
                        .alignment(Alignment.TopLeft)
                        .pos(CFG_RIGHT_X, 20)
                        .size(CFG_W - CFG_RIGHT_X - 8, 30)
                        .color(OWNER_COLOR));
                return;
            }
            Permission[] permissions = Permission.values();
            for (int col = 0; col < permissions.length; col++) {
                addMatrixHeader(dialog, col, permissions[col].name());
            }
            if (row.canEditCreate()) {
                addMatrixHeader(dialog, permissions.length, "create");
            }
            Identity[] identities = Identity.values();
            for (int r = 0; r < identities.length; r++) {
                Identity who = identities[r];
                int y = MATRIX_HEADER_Y + 11 + r * MATRIX_ROW_H;
                dialog.child(
                    IKey.lang("warpcovers.gui.identity." + lower(who.name()))
                        .asWidget()
                        .pos(CFG_RIGHT_X, y + 1)
                        .size(MATRIX_LABEL_W, 9)
                        .color(TEXT_COLOR));
                for (int col = 0; col < permissions.length; col++) {
                    Permission permission = permissions[col];
                    dialog.child(matrixCell(col, y, () -> {
                        PanelSnapshot.ChannelRow current = configRow();
                        return current != null && current.permissions != null
                            && current.permissions.allows(who, permission);
                    }, allowed -> sendConfig("cfg_perm", buf -> {
                        NetworkUtils.writeStringSafe(buf, who.name());
                        NetworkUtils.writeStringSafe(buf, permission.name());
                        buf.writeBoolean(allowed);
                    })));
                }
                if (row.canEditCreate()) {
                    dialog.child(matrixCell(permissions.length, y, () -> {
                        PanelSnapshot.ChannelRow current = configRow();
                        return current != null && current.createAllowed(who);
                    }, allowed -> sendConfig("cfg_new", buf -> {
                        NetworkUtils.writeStringSafe(buf, who.name());
                        buf.writeBoolean(allowed);
                    })));
                }
            }
            ButtonWidget<?> preset = new ButtonWidget<>();
            preset.pos(CFG_RIGHT_X, 82)
                .size(CFG_W - CFG_RIGHT_X - 8, 14)
                .overlay(
                    IKey.dynamic(() -> tr("warpcovers.gui.config.preset", tr(presetKey())))
                        .color(BUTTON_TEXT))
                .addTooltipLine(tr("warpcovers.gui.config.preset_tip"));
            preset.onMousePressed(mouse -> {
                boolean next = !isConfigPublic();
                sendConfig("cfg_public", buf -> buf.writeBoolean(next));
                return true;
            });
            dialog.child(preset);
        }

        private void addMatrixHeader(Dialog<Boolean> dialog, int col, String name) {
            String key = "warpcovers.gui.perm." + lower(name);
            dialog.child(
                IKey.lang(key + ".short")
                    .asWidget()
                    .pos(CFG_RIGHT_X + MATRIX_LABEL_W + col * MATRIX_CELL, MATRIX_HEADER_Y)
                    .size(MATRIX_CELL, 9)
                    .alignment(Alignment.Center)
                    .color(TEXT_COLOR)
                    .addTooltipLine(tr(key)));
        }

        private IWidget matrixCell(int col, int y, BooleanSupplier value, Consumer<Boolean> onChange) {
            ButtonWidget<?> cell = new ButtonWidget<>();
            cell.pos(CFG_RIGHT_X + MATRIX_LABEL_W + col * MATRIX_CELL + 3, y)
                .size(10, 10)
                .background(
                    new DynamicDrawable(
                        () -> value.getAsBoolean() ? GuiTextures.CHECK_BOX_FULL : GuiTextures.CHECK_BOX_EMPTY));
            cell.onMousePressed(mouse -> {
                onChange.accept(!value.getAsBoolean());
                return true;
            });
            return cell;
        }

        private boolean isConfigPublic() {
            PanelSnapshot.ChannelRow current = configRow();
            return current != null && current.permissions != null && current.permissions.isPublic();
        }

        private String presetKey() {
            PanelSnapshot.ChannelRow current = configRow();
            if (current == null || current.permissions == null) {
                return "warpcovers.gui.config.preset.private";
            }
            if (current.permissions.isPublic()) {
                return "warpcovers.gui.config.preset.public";
            }
            return current.permissions.isPrivate() ? "warpcovers.gui.config.preset.private"
                : "warpcovers.gui.config.preset.custom";
        }

        private String configFeedback() {
            if (!configLocalError.isEmpty()) {
                return configLocalError;
            }
            if (!configActed) {
                return "";
            }
            PanelSnapshot.StatusView view = statusView();
            return view.noticeKey.isEmpty() ? "" : tr("warpcovers.notice." + view.noticeKey, view.noticeArg);
        }

        private boolean configFeedbackIsError() {
            return !configLocalError.isEmpty() || statusView().noticeError;
        }

        private void openDeleteDialog(Dialog<Boolean> parent) {
            if (deleteDialog == null) {
                deleteDialog = IPanelHandler.simple(parent, (main, player) -> deletePanel(), true);
            }
            if (deleteDialog.isPanelOpen()) {
                return;
            }
            deleteDialog.deleteCachedPanel();
            deleteDialog.openPanel();
        }

        /** 删除的二次确认：确认和取消都和删除按钮不在同一位置，连点不会误删。 */
        private Dialog<Boolean> deletePanel() {
            Dialog<Boolean> dialog = new Dialog<>(syncPrefix + "delete_channel");
            dialog.setDraggable(false);
            dialog.setCloseOnOutOfBoundsClick(false);
            dialog.size(172, 66);
            PanelSnapshot.ChannelRow row = configRow();
            dialog.child(
                IKey.str(tr("warpcovers.gui.config.delete_confirm", row == null ? "" : row.name))
                    .asWidget()
                    .alignment(Alignment.TopLeft)
                    .pos(8, 7)
                    .size(156, 32)
                    .color(0xFF222222));

            ButtonWidget<?> confirm = new ButtonWidget<>();
            confirm.pos(88, 44)
                .size(76, 14)
                .overlay(
                    IKey.lang("warpcovers.gui.config.delete_yes")
                        .color(BUTTON_TEXT));
            confirm.onMousePressed(mouse -> {
                sendConfig("cfg_delete", buf -> {});
                dialog.closeWith(true);
                if (configDialog != null) {
                    configDialog.closePanel();
                }
                return true;
            });
            dialog.child(confirm);

            ButtonWidget<?> cancel = new ButtonWidget<>();
            cancel.pos(8, 44)
                .size(76, 14)
                .overlay(
                    IKey.lang("warpcovers.gui.dialog.cancel")
                        .color(BUTTON_TEXT));
            cancel.onMousePressed(mouse -> {
                dialog.closeWith(false);
                return true;
            });
            dialog.child(cancel);
            return dialog;
        }

        // ---- 设置页 ----

        private IWidget settingsPage() {
            ParentWidget<?> page = new ParentWidget<>();
            page.size(PAGE_W, settingsH);
            addTransferFields(page);
            if (!type.hasFilter()) {
                return energyNotes(page);
            }
            if (!manager) {
                // 名单属于频道，不在设置页里：提示去哪改，免得玩家以为名单没了
                page.child(
                    IKey.lang("warpcovers.gui.settings.filter_hint")
                        .asWidget()
                        .alignment(Alignment.TopLeft)
                        .pos(0, NOTE_Y + 2)
                        .size(PAGE_W - 2, 30)
                        .color(OWNER_COLOR));
            }
            return page;
        }

        /** 输出优先级、速率、运行周期：各类型共用的一行输入框，失去焦点或回车时提交到服务端。 */
        private void addTransferFields(ParentWidget<?> page) {
            int width = (PAGE_W - 2 * FIELD_GAP) / 3;
            addNumberField(
                page,
                0,
                width,
                "warpcovers.gui.field.priority",
                priority,
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                () -> tr("warpcovers.gui.field.priority_tip"));
            addNumberField(
                page,
                width + FIELD_GAP,
                width,
                "warpcovers.gui.field.rate",
                rate,
                0,
                Long.MAX_VALUE,
                this::rateTooltip);
            addNumberField(
                page,
                2 * (width + FIELD_GAP),
                width,
                "warpcovers.gui.field.period",
                period,
                0,
                Integer.MAX_VALUE,
                this::periodTooltip);
        }

        private void addNumberField(ParentWidget<?> page, int x, int width, String labelKey, LongSyncValue value,
            long min, long max, Supplier<String> tooltip) {
            page.child(
                IKey.lang(labelKey)
                    .asWidget()
                    .pos(x, 0)
                    .size(width, 9)
                    .color(TEXT_COLOR));
            page.child(
                numberField(value, min, max, tooltip).pos(x, 10)
                    .size(width, 12));
        }

        private TextFieldWidget numberField(LongSyncValue value, long min, long max, Supplier<String> tooltip) {
            TextFieldWidget field = new TextFieldWidget();
            field.value(value)
                .setValidator(text -> NumberInput.normalize(text, min, max));
            field.tooltipDynamic(lines -> lines.addLine(tooltip.get()))
                .tooltipAutoUpdate(true);
            return field;
        }

        private String rateTooltip() {
            long effective = effectiveRate.getLongValue();
            String actual = effective == Long.MAX_VALUE ? tr("warpcovers.gui.field.unlimited")
                : Long.toString(effective);
            return tr("warpcovers.gui.field.rate_tip", actual);
        }

        private String periodTooltip() {
            return tr("warpcovers.gui.field.period_tip", effectivePeriod.getLongValue());
        }

        // ---- 管理器：连接端列表和远程修改 ----

        private List<PanelSnapshot.EndpointRow> endpointRows() {
            return PanelSnapshot.parseEndpoints(endpointsValue.getValue(), configChannelId);
        }

        private int endpointPageCount() {
            return Math.max(1, (endpointRows().size() + ENDPOINT_ROWS - 1) / ENDPOINT_ROWS);
        }

        private PanelSnapshot.EndpointRow endpointAt(int slot) {
            List<PanelSnapshot.EndpointRow> rows = endpointRows();
            int index = Math.min(endpointPage, endpointPageCount() - 1) * ENDPOINT_ROWS + slot;
            return index < rows.size() ? rows.get(index) : null;
        }

        private String machineLabel(PanelSnapshot.EndpointRow row) {
            if (row.machineName.isEmpty()) {
                return tr("warpcovers.gui.endpoint.machine_unknown");
            }
            String named = row.machineName + ".name";
            String translated = tr(named);
            return translated.equals(named) ? tr(row.machineName) : translated;
        }

        private String endpointLine(PanelSnapshot.EndpointRow row) {
            return tr(
                "warpcovers.gui.endpoint.line",
                row.dimension,
                row.x,
                row.y,
                row.z,
                machineLabel(row),
                tr(WarpCoverItem.stateKey(row.state)),
                tr(row.loaded ? "warpcovers.gui.endpoint.loaded" : "warpcovers.gui.endpoint.unloaded"));
        }

        private void addEndpointList(Dialog<Boolean> dialog, int extra) {
            int y = CFG_H + extra - 2;
            dialog.child(
                IKey.dynamic(() -> tr("warpcovers.gui.endpoint.title", endpointRows().size()))
                    .asWidget()
                    .pos(8, y + 3)
                    .size(CFG_W - 80, 9)
                    .color(0xFF222222));
            int pagerX = CFG_W - 8 - 62;
            ButtonWidget<?> previous = new ButtonWidget<>();
            previous.pos(pagerX, y)
                .size(14, 14)
                .overlay(
                    IKey.str("<")
                        .color(BUTTON_TEXT));
            previous.onMousePressed(mouse -> {
                endpointPage = Math.max(0, Math.min(endpointPage, endpointPageCount() - 1) - 1);
                return true;
            });
            dialog.child(previous);
            dialog.child(
                IKey.dynamic(() -> (Math.min(endpointPage, endpointPageCount() - 1) + 1) + "/" + endpointPageCount())
                    .asWidget()
                    .alignment(Alignment.Center)
                    .pos(pagerX + 15, y + 3)
                    .size(32, 9)
                    .color(TEXT_COLOR));
            ButtonWidget<?> next = new ButtonWidget<>();
            next.pos(pagerX + 48, y)
                .size(14, 14)
                .overlay(
                    IKey.str(">")
                        .color(BUTTON_TEXT));
            next.onMousePressed(mouse -> {
                endpointPage = Math.min(endpointPageCount() - 1, endpointPage + 1);
                return true;
            });
            dialog.child(next);

            for (int i = 0; i < ENDPOINT_ROWS; i++) {
                int slot = i;
                ButtonWidget<?> row = new ButtonWidget<>();
                row.pos(8, y + 16 + i * ENDPOINT_ROW_H)
                    .size(CFG_W - 16, ENDPOINT_ROW_H - 1)
                    .background(new DynamicDrawable(() -> {
                        PanelSnapshot.EndpointRow current = endpointAt(slot);
                        return current != null && current.editable ? GTGuiTextures.BUTTON_STANDARD
                            : GTGuiTextures.BUTTON_STANDARD_DISABLED;
                    }))
                    .child(new ScrollingTextWidget(IKey.dynamic(() -> {
                        PanelSnapshot.EndpointRow current = endpointAt(slot);
                        if (current == null) {
                            return "";
                        }
                        return (current.editable ? "" : "\u00a78") + endpointLine(current);
                    })).pos(3, 2)
                        .size(CFG_W - 22, 8)
                        .color(BUTTON_TEXT))
                    .tooltipDynamic(tooltip -> {
                        PanelSnapshot.EndpointRow current = endpointAt(slot);
                        if (current == null) {
                            return;
                        }
                        tooltip.addLine(endpointLine(current));
                        tooltip.addLine(
                            tr(
                                current.editable ? "warpcovers.gui.endpoint.edit_tip"
                                    : current.loaded ? "warpcovers.gui.endpoint.no_permission_tip"
                                        : "warpcovers.gui.endpoint.unloaded_tip"));
                    })
                    .tooltipAutoUpdate(true)
                    .setEnabledIf(w -> endpointAt(slot) != null);
                row.onMousePressed(mouse -> {
                    PanelSnapshot.EndpointRow current = endpointAt(slot);
                    if (mouse == 0 && current != null && current.editable) {
                        send("ep_select", buf -> NetworkUtils.writeStringSafe(buf, current.id));
                        dialog.closeWith(true);
                        openEditor.run();
                    }
                    return true;
                });
                dialog.child(row);
            }
        }

        private PanelSnapshot.EndpointRow selectedRow() {
            return PanelSnapshot.parseSelected(selectedValue.getValue());
        }

        /** 远程修改页：顶部是连接端信息、交互状态、断开，下面和覆盖板的设置页一样。 */
        private IWidget editorPage() {
            ParentWidget<?> page = new ParentWidget<>();
            page.size(PAGE_W, MANAGER_PAGE_H);
            page.child(new ScrollingTextWidget(IKey.dynamic(() -> {
                PanelSnapshot.EndpointRow row = selectedRow();
                if (row == null) {
                    return tr("warpcovers.notice.endpoint_missing");
                }
                return endpointLine(row) + (row.editable ? "" : tr("warpcovers.gui.manager.readonly"));
            })).pos(0, 3)
                .size(PAGE_W - EDITOR_BUTTON_W - 2, 9)
                .color(TEXT_COLOR));

            ButtonWidget<?> back = new ButtonWidget<>();
            back.pos(PAGE_W - EDITOR_BUTTON_W, 0)
                .size(EDITOR_BUTTON_W, ROW_H)
                .overlay(
                    IKey.lang("warpcovers.gui.manager.back")
                        .color(BUTTON_TEXT));
            back.onMousePressed(mouse -> {
                send("ep_clear", buf -> {});
                closeEditor.run();
                return true;
            });
            page.child(back);

            int stateW = (PAGE_W - EDITOR_BUTTON_W - 2) / STATES.length;
            for (int i = 0; i < STATES.length; i++) {
                InteractionState state = STATES[i];
                ButtonWidget<?> button = new ButtonWidget<>();
                button.pos(i * stateW, 15)
                    .size(stateW - 1, ROW_H)
                    .background(new DynamicDrawable(() -> {
                        PanelSnapshot.EndpointRow row = selectedRow();
                        return row != null && row.state == state ? GTGuiTextures.BUTTON_STANDARD_PRESSED
                            : GTGuiTextures.BUTTON_STANDARD;
                    }))
                    .overlay(
                        IKey.lang(WarpCoverItem.stateKey(state))
                            .color(BUTTON_TEXT))
                    .addTooltipLine(tr("warpcovers.gui.state_tip." + lower(state.name())));
                button.onMousePressed(mouse -> {
                    send("state", buf -> NetworkUtils.writeStringSafe(buf, state.name()));
                    return true;
                });
                page.child(button);
            }
            ButtonWidget<?> disconnect = new ButtonWidget<>();
            disconnect.pos(PAGE_W - EDITOR_BUTTON_W, 15)
                .size(EDITOR_BUTTON_W, ROW_H)
                .overlay(
                    IKey.lang("warpcovers.gui.manager.disconnect")
                        .color(BUTTON_TEXT))
                .addTooltipLine(tr("warpcovers.gui.manager.disconnect_tip"));
            disconnect.onMousePressed(mouse -> {
                send("ep_disconnect", buf -> {});
                closeEditor.run();
                return true;
            });
            page.child(disconnect);

            ParentWidget<?> settings = new ParentWidget<>();
            settings.pos(0, EDITOR_HEADER_H)
                .size(PAGE_W, settingsH);
            settings.child(settingsPage());
            page.child(settings);
            return page;
        }

        // ---- 名单快捷配置弹窗 ----

        private void openQuickDialog(IWidget anchor) {
            if (quickDialog == null) {
                quickDialog = IPanelHandler.simple(anchor.getPanel(), (main, player) -> quickPanel(), true);
            }
            if (quickDialog.isPanelOpen()) {
                return;
            }
            // 候选只在打开时汇总一次：既不用每 tick 扫描机器，勾选途中列表也不会跳动
            send("quick_refresh", buf -> {});
            quickSelected.clear();
            quickPage = 0;
            quickDialog.deleteCachedPanel();
            quickDialog.openPanel();
        }

        private FilterCandidates quickCandidates() {
            NBTTagCompound raw = quickValue.getValue();
            if (raw != quickRaw) {
                quickRaw = raw;
                quickParsed = PanelSnapshot.parseCandidates(raw);
            }
            return quickParsed;
        }

        private List<String> quickKeys() {
            FilterCandidates candidates = quickCandidates();
            return candidates.stored();
        }

        private int quickPageCount() {
            return Math.max(1, (quickKeys().size() + QUICK_PAGE_SIZE - 1) / QUICK_PAGE_SIZE);
        }

        /** 第 slot 个格子当前显示的物体键；没有时为 null。 */
        private String quickKeyAt(int slot) {
            List<String> keys = quickKeys();
            int page = Math.min(quickPage, quickPageCount() - 1);
            int index = page * QUICK_PAGE_SIZE + slot;
            return index < keys.size() ? keys.get(index) : null;
        }

        /** 勾选项里仍是候选的：重新汇总后可能有变化。 */
        private List<String> quickChosen() {
            FilterCandidates candidates = quickCandidates();
            List<String> chosen = new ArrayList<>();
            for (String key : quickSelected) {
                if (candidates.stored()
                    .contains(key)) {
                    chosen.add(key);
                }
            }
            return chosen;
        }

        private Dialog<Boolean> quickPanel() {
            Dialog<Boolean> dialog = new Dialog<>(syncPrefix + "filter_quick");
            dialog.setDraggable(false);
            dialog.setCloseOnOutOfBoundsClick(true);
            dialog.size(172, QUICK_GRID_Y + QUICK_ROWS * QUICK_CELL + 44);
            dialog.child(
                IKey.lang("warpcovers.gui.quick.title")
                    .asWidget()
                    .pos(8, 7)
                    .size(156, 9)
                    .color(0xFF222222));
            addQuickTab(dialog, 8);

            // 选中用亮绿底加绿框：流体图标铺满整格会盖住底色，所以框要画在图标上面
            IDrawable selectedMark = new Rectangle().color(0xFF40D040);
            IDrawable selectedFrame = new Rectangle().color(0xFF40FF40)
                .hollow(2);
            int gridX = (172 - QUICK_COLUMNS * QUICK_CELL) / 2;
            for (int i = 0; i < QUICK_COLUMNS * QUICK_ROWS; i++) {
                int slot = i;
                ButtonWidget<?> cell = new ButtonWidget<>();
                cell.pos(gridX + (i % QUICK_COLUMNS) * QUICK_CELL, QUICK_GRID_Y + (i / QUICK_COLUMNS) * QUICK_CELL)
                    .size(QUICK_CELL, QUICK_CELL)
                    .background(new DynamicDrawable(() -> {
                        String key = quickKeyAt(slot);
                        return key != null && quickSelected.contains(key) ? selectedMark
                            : GTGuiTextures.BUTTON_STANDARD;
                    }))
                    .overlay(new DynamicDrawable(() -> {
                        String key = quickKeyAt(slot);
                        return key == null ? IDrawable.EMPTY : objectIcons.computeIfAbsent(key, this::objectIcon);
                    }), new DynamicDrawable(() -> {
                        String key = quickKeyAt(slot);
                        return key != null && quickSelected.contains(key) ? selectedFrame : IDrawable.EMPTY;
                    }))
                    .tooltipDynamic(tooltip -> {
                        String key = quickKeyAt(slot);
                        if (key != null) {
                            tooltip.addLine(objectLabel(key));
                        }
                    })
                    .tooltipAutoUpdate(true)
                    .setEnabledIf(w -> quickKeyAt(slot) != null);
                cell.onMousePressed(mouse -> {
                    String key = quickKeyAt(slot);
                    if (key != null && !quickSelected.remove(key)) {
                        quickSelected.add(key);
                    }
                    return true;
                });
                dialog.child(cell);
            }
            dialog.child(
                IKey.dynamic(this::quickEmptyHint)
                    .asWidget()
                    .pos(8, QUICK_GRID_Y + 4)
                    .size(156, 40)
                    .color(OWNER_COLOR));

            int barY = QUICK_GRID_Y + QUICK_ROWS * QUICK_CELL + 3;
            ButtonWidget<?> previous = new ButtonWidget<>();
            previous.pos(8, barY)
                .size(14, 14)
                .overlay(
                    IKey.str("<")
                        .color(BUTTON_TEXT));
            previous.onMousePressed(mouse -> {
                quickPage = Math.max(0, Math.min(quickPage, quickPageCount() - 1) - 1);
                return true;
            });
            dialog.child(previous);
            dialog.child(
                IKey.dynamic(() -> (Math.min(quickPage, quickPageCount() - 1) + 1) + "/" + quickPageCount())
                    .asWidget()
                    .alignment(Alignment.Center)
                    .pos(23, barY + 3)
                    .size(30, 9)
                    .color(TEXT_COLOR));
            ButtonWidget<?> next = new ButtonWidget<>();
            next.pos(54, barY)
                .size(14, 14)
                .overlay(
                    IKey.str(">")
                        .color(BUTTON_TEXT));
            next.onMousePressed(mouse -> {
                quickPage = Math.min(quickPageCount() - 1, quickPage + 1);
                return true;
            });
            dialog.child(next);
            ButtonWidget<?> all = new ButtonWidget<>();
            all.pos(88, barY)
                .size(76, 14)
                .overlay(
                    IKey.lang("warpcovers.gui.quick.select_all")
                        .color(BUTTON_TEXT))
                .addTooltipLine(tr("warpcovers.gui.quick.select_all_tip"));
            all.onMousePressed(mouse -> {
                List<String> keys = quickKeys();
                if (quickSelected.containsAll(keys)) {
                    quickSelected.removeAll(keys);
                } else {
                    quickSelected.addAll(keys);
                }
                return true;
            });
            dialog.child(all);

            ButtonWidget<?> confirm = new ButtonWidget<>();
            confirm.pos(8, barY + 18)
                .size(76, 14)
                .overlay(
                    IKey.dynamic(() -> tr("warpcovers.gui.quick.confirm", quickChosen().size()))
                        .color(BUTTON_TEXT));
            confirm.onMousePressed(mouse -> {
                List<String> chosen = quickChosen();
                if (!chosen.isEmpty()) {
                    sendConfig("flt_add", buf -> {
                        buf.writeInt(chosen.size());
                        for (String key : chosen) {
                            NetworkUtils.writeStringSafe(buf, key);
                        }
                    });
                }
                dialog.closeWith(true);
                return true;
            });
            dialog.child(confirm);
            ButtonWidget<?> cancel = new ButtonWidget<>();
            cancel.pos(88, barY + 18)
                .size(76, 14)
                .overlay(
                    IKey.lang("warpcovers.gui.dialog.cancel")
                        .color(BUTTON_TEXT));
            cancel.onMousePressed(mouse -> {
                dialog.closeWith(false);
                return true;
            });
            dialog.child(cancel);
            return dialog;
        }

        private void addQuickTab(Dialog<Boolean> dialog, int x) {
            ButtonWidget<?> tab = new ButtonWidget<>();
            tab.pos(x, 18)
                .size(77, 14)
                .background(new DynamicDrawable(() -> GTGuiTextures.BUTTON_STANDARD_PRESSED))
                .overlay(
                    IKey.dynamic(
                        () -> tr(
                            "warpcovers.gui.quick.stored",
                            quickCandidates().stored()
                                .size()))
                        .color(BUTTON_TEXT))
                .addTooltipLine(tr("warpcovers.gui.quick.stored_tip"));
            tab.onMousePressed(mouse -> {
                quickPage = 0;
                return true;
            });
            dialog.child(tab);
        }

        private String quickEmptyHint() {
            if (!quickKeys().isEmpty()) {
                return "";
            }
            return tr("warpcovers.gui.quick.stored_empty");
        }

        // ---- 配置面板里的名单区 ----

        /** 配置面板正在看的频道的名单；快照还没切到这个频道时是默认的空黑名单。 */
        private PanelSnapshot.FilterView filterView() {
            NBTTagCompound raw = filter.getValue();
            if (parsedFilter == null || raw != parsedFilterRaw || !configChannelId.equals(parsedFilterChannel)) {
                parsedFilterRaw = raw;
                parsedFilterChannel = configChannelId;
                parsedFilter = PanelSnapshot.parseFilter(raw, configChannelId);
            }
            return parsedFilter;
        }

        private int filterMaxScroll() {
            int rows = (filterView().keys.size() + FILTER_COLS - 1) / FILTER_COLS;
            return Math.max(0, rows - FILTER_ROWS);
        }

        private void scrollFilter(int delta) {
            int max = filterMaxScroll();
            filterScroll = Math.max(0, Math.min(max, Math.min(filterScroll, max) + delta));
        }

        /** 窗口里第 slot 格的名单键；没有时为 null。 */
        private String filterKeyAt(int slot) {
            List<String> keys = filterView().keys;
            int index = Math.min(filterScroll, filterMaxScroll()) * FILTER_COLS + slot;
            return index < keys.size() ? keys.get(index) : null;
        }

        /**
         * 名单区：黑白切换、添加区、快捷、匹配 NBT、清空，下面是条目。没有「修改配置」权限的人只能看，
         * 添加区、快捷、清空和移除都不出现，黑白与 NBT 开关只显示状态、点了没有反应（服务端也会拒绝）。
         */
        private void addFilterBlock(Dialog<Boolean> dialog, PanelSnapshot.ChannelRow row) {
            boolean edit = row.canConfigure;
            int y = CFG_FILTER_Y;
            int right = CFG_W - 8;

            ButtonWidget<?> mode = new ButtonWidget<>();
            mode.pos(8, y)
                .size(16, 16)
                .overlay(
                    new DynamicDrawable(
                        () -> filterView().whitelist ? GTGuiTextures.OVERLAY_BUTTON_WHITELIST
                            : GTGuiTextures.OVERLAY_BUTTON_BLACKLIST))
                .tooltipDynamic(tooltip -> {
                    tooltip.addLine(
                        tr(
                            filterView().whitelist ? "warpcovers.gui.filter.whitelist"
                                : "warpcovers.gui.filter.blacklist"));
                    tooltip.addLine(tr("warpcovers.gui.filter.shared"));
                    tooltip.addLine(tr(edit ? "warpcovers.gui.filter.toggle" : "warpcovers.gui.filter.read_only"));
                })
                .tooltipAutoUpdate(true);
            mode.onMousePressed(mouse -> {
                if (edit) {
                    boolean next = !filterView().whitelist;
                    sendConfig("flt_mode", buf -> buf.writeBoolean(next));
                }
                return true;
            });
            dialog.child(mode);
            dialog.child(
                IKey.dynamic(
                    () -> tr(
                        filterView().whitelist ? "warpcovers.gui.filter.mode_white"
                            : "warpcovers.gui.filter.mode_black"))
                    .asWidget()
                    .pos(27, y + 4)
                    .size(38, 9)
                    .color(0xFF222222));

            if (isItem()) {
                ButtonWidget<?> nbt = new ButtonWidget<>();
                nbt.pos(right - 38, y)
                    .size(16, 16)
                    .background(
                        new DynamicDrawable(
                            () -> filterView().matchNbt ? GTGuiTextures.BUTTON_STANDARD_PRESSED
                                : GTGuiTextures.BUTTON_STANDARD))
                    .overlay(GTGuiTextures.OVERLAY_BUTTON_NBT)
                    .tooltipDynamic(
                        tooltip -> tooltip.addLine(
                            tr(
                                filterView().matchNbt ? "warpcovers.gui.filter.nbt_on"
                                    : "warpcovers.gui.filter.nbt_off")))
                    .tooltipAutoUpdate(true);
                nbt.onMousePressed(mouse -> {
                    if (edit) {
                        boolean next = !filterView().matchNbt;
                        sendConfig("flt_nbt", buf -> buf.writeBoolean(next));
                    }
                    return true;
                });
                dialog.child(nbt);
            }

            if (edit) {
                addFilterEditControls(dialog, y, right);
            }

            ScrollArea area = new ScrollArea(this::scrollFilter);
            area.pos(8, y + FILTER_LIST_Y)
                .size(FILTER_COLS * FILTER_CELL_W, FILTER_ROWS * ROW_H);
            for (int i = 0; i < FILTER_COLS * FILTER_ROWS; i++) {
                area.child(filterCell(i, edit, (i % FILTER_COLS) * FILTER_CELL_W, (i / FILTER_COLS) * ROW_H));
            }
            area.child(IKey.dynamic(() -> {
                PanelSnapshot.FilterView view = filterView();
                if (!view.keys.isEmpty()) {
                    return "";
                }
                return tr(view.whitelist ? "warpcovers.gui.filter.empty_white" : "warpcovers.gui.filter.empty_black");
            })
                .asWidget()
                .pos(2, 3)
                .size(FILTER_COLS * FILTER_CELL_W - 4, 9)
                .color(OWNER_COLOR));
            dialog.child(area);

            addFilterArrow(dialog, y + FILTER_LIST_Y, "^", -1, "warpcovers.gui.filter.scroll_up");
            addFilterArrow(
                dialog,
                y + FILTER_LIST_Y + FILTER_ROWS * ROW_H - FILTER_ARROW_W,
                "v",
                1,
                "warpcovers.gui.filter.scroll_down");
        }

        /** 添加区、快捷、清空：只给有「修改配置」权限的人。 */
        private void addFilterEditControls(Dialog<Boolean> dialog, int y, int right) {
            Consumer<String> add = key -> sendConfig("flt_add", buf -> {
                buf.writeInt(1);
                NetworkUtils.writeStringSafe(buf, key);
            });
            FilterDropSlot drop = new FilterDropSlot(
                isItem() ? FilterDropSlot::itemKey : FilterDropSlot::fluidKey,
                add);
            drop.pos(66, y - 1)
                .size(18, 18)
                .background(isItem() ? GuiTextures.SLOT_ITEM : GuiTextures.SLOT_FLUID)
                .overlay(
                    IKey.str("+")
                        .color(OWNER_COLOR))
                .addTooltipLine(tr(isItem() ? "warpcovers.gui.filter.add_item" : "warpcovers.gui.filter.add"));
            dialog.child(drop);
            int quickX = right - 38 - QUICK_BUTTON_W - 2;
            dialog.child(
                IKey.lang(isItem() ? "warpcovers.gui.filter.hint_item" : "warpcovers.gui.filter.hint")
                    .asWidget()
                    .pos(88, y + 4)
                    .size(quickX - 90, 9)
                    .color(OWNER_COLOR));

            if (!manager) {
                // 候选来自这台机器，所以只有覆盖板当前连着的频道才有快捷配置
                ButtonWidget<?> quick = new ButtonWidget<>();
                quick.pos(quickX, y)
                    .size(QUICK_BUTTON_W, 16)
                    .overlay(
                        IKey.lang("warpcovers.gui.quick.open")
                            .color(BUTTON_TEXT))
                    .addTooltipLine(tr("warpcovers.gui.quick.open_tip"))
                    .setEnabledIf(w -> channelsView().isConnected(configChannelId));
                quick.onMousePressed(mouse -> {
                    openQuickDialog(quick);
                    return true;
                });
                dialog.child(quick);
            }

            ButtonWidget<?> clear = new ButtonWidget<>();
            clear.pos(right - 16, y)
                .size(16, 16)
                .overlay(GTGuiTextures.OVERLAY_BUTTON_CROSS)
                .addTooltipLine(tr("warpcovers.gui.filter.clear"));
            clear.onMousePressed(mouse -> {
                sendConfig("flt_clear", buf -> {});
                return true;
            });
            dialog.child(clear);
        }

        private void addFilterArrow(Dialog<Boolean> dialog, int y, String label, int delta, String tipKey) {
            ButtonWidget<?> arrow = new ButtonWidget<>();
            arrow.pos(CFG_W - 8 - FILTER_ARROW_W, y)
                .size(FILTER_ARROW_W, FILTER_ARROW_W)
                .overlay(
                    IKey.str(label)
                        .color(BUTTON_TEXT))
                .addTooltipLine(tr(tipKey));
            arrow.onMousePressed(mouse -> {
                scrollFilter(delta);
                return true;
            });
            dialog.child(arrow);
        }

        /** 窗口里的一格：图标、名称，有权限时还有移除按钮；没有条目时整格隐藏。 */
        private IWidget filterCell(int slot, boolean edit, int x, int y) {
            ParentWidget<?> cell = new ParentWidget<>();
            cell.pos(x, y)
                .size(FILTER_CELL_W - 2, ROW_H)
                .setEnabledIf(w -> filterKeyAt(slot) != null);
            cell.child(new DynamicDrawable(() -> {
                String key = filterKeyAt(slot);
                return key == null ? IDrawable.EMPTY : objectIcons.computeIfAbsent(key, this::objectIcon);
            }).asWidget()
                .pos(1, 1)
                .size(12, 12)
                .tooltipDynamic(tooltip -> {
                    String key = filterKeyAt(slot);
                    if (key != null) {
                        tooltip.addLine(objectLabel(key));
                    }
                })
                .tooltipAutoUpdate(true));
            cell.child(new ScrollingTextWidget(IKey.dynamic(() -> {
                String key = filterKeyAt(slot);
                return key == null ? "" : objectLabel(key);
            })).pos(16, 3)
                .size(FILTER_CELL_W - 2 - (edit ? 32 : 18), 8)
                .color(TEXT_COLOR));
            if (edit) {
                ButtonWidget<?> remove = new ButtonWidget<>();
                remove.pos(FILTER_CELL_W - 2 - 14, 1)
                    .size(12, 12)
                    .overlay(GuiTextures.CLOSE)
                    .addTooltipLine(tr("warpcovers.gui.filter.remove"));
                remove.onMousePressed(mouse -> {
                    String key = filterKeyAt(slot);
                    if (key != null) {
                        sendConfig("flt_remove", buf -> NetworkUtils.writeStringSafe(buf, key));
                    }
                    return true;
                });
                cell.child(remove);
            }
            return cell;
        }

        private IDrawable objectIcon(String key) {
            if (isItem()) {
                return new ItemDrawable(ItemKeys.prototype(key));
            }
            Fluid fluid = FluidRegistry.getFluid(key);
            return new FluidDrawable(fluid == null ? null : new FluidStack(fluid, 1000));
        }

        private String objectLabel(String key) {
            if (isItem()) {
                ItemStack stack = ItemKeys.prototype(key);
                String label = stack == null ? key : stack.getDisplayName();
                return ObjectKeys.hasNbt(key) ? tr("warpcovers.gui.filter.with_nbt", label) : label;
            }
            Fluid fluid = FluidRegistry.getFluid(key);
            return fluid == null ? key : new FluidStack(fluid, 1000).getLocalizedName();
        }

        // ---- 状态栏 ----

        private PanelSnapshot.StatusView statusView() {
            return PanelSnapshot.parseStatus(status.getValue());
        }

        private String statusLine() {
            if (manager && selectedRow() == null) {
                return tr("warpcovers.gui.manager.hint");
            }
            PanelSnapshot.StatusView view = statusView();
            return tr("warpcovers.gui.status", tr("warpcovers.status." + view.status.key()));
        }

        private String detailLine() {
            PanelSnapshot.StatusView view = statusView();
            if (!view.noticeKey.isEmpty()) {
                return tr("warpcovers.notice." + view.noticeKey, view.noticeArg);
            }
            if (view.reason != null) {
                return tr("warpcovers.gui.reason", tr("warpcovers.reason." + view.reason.key()));
            }
            if (view.status == EndpointStatus.FILTERED) {
                return tr("warpcovers.hint.filtered");
            }
            return "";
        }

        private boolean detailIsError() {
            PanelSnapshot.StatusView view = statusView();
            return !view.noticeKey.isEmpty() ? view.noticeError : view.reason != null;
        }

        /** 双栏界面没有名单分页，不必再说没有名单，只说明直灌规则。 */
        private void addEnergyNotes(ParentWidget<?> page, int y, int width) {
            page.child(
                IKey.lang("warpcovers.gui.energy.direct")
                    .asWidget()
                    .alignment(Alignment.TopLeft)
                    .pos(0, y)
                    .size(width, 36)
                    .color(OWNER_COLOR));
            page.child(
                IKey.lang("warpcovers.gui.energy.source")
                    .asWidget()
                    .alignment(Alignment.TopLeft)
                    .pos(0, y + 38)
                    .size(width, 27)
                    .color(OWNER_COLOR));
        }

        /** 能量没有过滤名单：设置页只说明直灌规则。 */
        private IWidget energyNotes(ParentWidget<?> page) {
            String[] keys = { "warpcovers.gui.energy.no_filter", "warpcovers.gui.energy.direct",
                "warpcovers.gui.energy.source" };
            int[] heights = { 10, 30, 30 };
            int y = NOTE_Y + 2;
            for (int i = 0; i < keys.length; i++) {
                page.child(
                    IKey.lang(keys[i])
                        .asWidget()
                        .alignment(Alignment.TopLeft)
                        .pos(0, y)
                        .size(PAGE_W - 2, heights[i])
                        .color(i == 0 ? TEXT_COLOR : OWNER_COLOR));
                y += heights[i] + 4;
            }
            return page;
        }

        /** 服务器是否关闭了本类型（来自状态快照，客户端也能读到）。 */
        private boolean isServerDisabled() {
            return statusView().status == EndpointStatus.DISABLED;
        }

        private boolean isItem() {
            return type == ChannelType.ITEM;
        }
    }

    private static String lower(String name) {
        return name.toLowerCase(java.util.Locale.ROOT);
    }

    private static InteractionState parseState(String name) {
        try {
            return InteractionState.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
