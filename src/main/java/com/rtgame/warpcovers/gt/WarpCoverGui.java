package com.rtgame.warpcovers.gt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;
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
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.serialization.ByteBufAdapters;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.value.sync.DynamicLinkedSyncHandler;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.DynamicSyncedWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.ScrollingTextWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.rtgame.warpcovers.WarpCovers;
import com.rtgame.warpcovers.core.ChannelAdmin;
import com.rtgame.warpcovers.core.ChannelColors;
import com.rtgame.warpcovers.core.ChannelGroup;
import com.rtgame.warpcovers.core.ChannelHub;
import com.rtgame.warpcovers.core.ChannelType;
import com.rtgame.warpcovers.core.CoverPanel;
import com.rtgame.warpcovers.core.CreateFailure;
import com.rtgame.warpcovers.core.DistributionMode;
import com.rtgame.warpcovers.core.Identity;
import com.rtgame.warpcovers.core.InteractionState;
import com.rtgame.warpcovers.core.NumberInput;
import com.rtgame.warpcovers.core.ObjectKeys;
import com.rtgame.warpcovers.core.PanelNotice;
import com.rtgame.warpcovers.core.Permission;
import com.rtgame.warpcovers.core.PrivateTags;
import com.rtgame.warpcovers.core.Viewer;

import gregtech.api.modularui2.CoverGuiData;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.gui.modularui.cover.base.CoverBaseGui;

/**
 * 折跃覆盖板界面（流体、物品共用）：左侧边栏「频道」「设置」两页，底部状态栏。
 * 服务端按玩家构建快照（{@link PanelSnapshot}）同步给客户端，客户端的每个操作都是发往服务端的请求，
 * 由 {@link CoverPanel} 校验后执行，结果随下一次快照回到界面。
 */
public final class WarpCoverGui extends CoverBaseGui<WarpCover> {

    private static final int ROOT_W = 196;
    private static final int ROOT_H = 146;
    private static final int TAB_SIZE = 20;
    private static final int PAGE_X = 24;
    private static final int PAGE_W = ROOT_W - PAGE_X;
    private static final int PAGE_H = 118;
    private static final int ROW_W = PAGE_W - 8;
    private static final int ROW_H = 14;
    private static final int STATUS_Y = PAGE_H + 4;
    /** 设置页：顶部一行数字输入框，下面是过滤名单。 */
    private static final int FIELD_GAP = 3;
    private static final int FILTER_Y = 25;
    private static final int LIST_Y = FILTER_Y + 42;

    private static final int TEXT_COLOR = 0xFF555555;
    private static final int OWNER_COLOR = 0xFF888888;
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
    /** 频道页：顶部一行新增按钮、搜索框、标签筛选；列表项两行。 */
    private static final int CREATE_W = 50;
    private static final int TAG_FILTER_W = 46;
    private static final int CHANNEL_ROW_H = 20;
    private static final int RECENT_ICONS = 3;
    private static final int RECENT_ICON = 10;

    private static final InteractionState[] STATES = { InteractionState.INPUT, InteractionState.OUTPUT,
        InteractionState.EMPTY };

    public WarpCoverGui(WarpCover cover) {
        super(cover);
    }

    @Override
    protected String getGuiId() {
        return "warpcovers." + cover.channelType()
            .key();
    }

    @Override
    protected boolean doesBindPlayerInventory() {
        // 名单靠光标上的物品或流体容器添加，需要能从背包拿起
        return true;
    }

    @Override
    public void addUIWidgets(PanelSyncManager syncManager, Flow column, CoverGuiData data) {
        column.child(new Session(syncManager).build());
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
        /** 物体图标缓存：图标每帧读取。 */
        private final Map<String, IDrawable> objectIcons = new HashMap<>();
        /** 配置面板正在编辑的频道。 */
        private String configChannelId = "";
        /** 配置面板打开后是否操作过：操作过才显示服务端的反馈，避免显示打开前的旧提示。 */
        private boolean configActed;
        private String configLocalError = "";
        private NBTTagCompound parsedChannels;
        private PanelSnapshot.ChannelsView parsedView;

        Session(PanelSyncManager sync) {
            this.sync = sync;
            this.client = sync.isClient();
            if (client) {
                panel = null;
                viewer = null;
            } else {
                EntityPlayer player = sync.getPlayer();
                viewer = new Viewer(
                    player.getUniqueID()
                        .toString(),
                    player.getCommandSenderName());
                panel = new CoverPanel(WarpServices.hub(), cover.channelType(), cover, new Random());
            }
            channels = new NbtValue(() -> client ? new NBTTagCompound() : PanelSnapshot.channels(panel, viewer));
            recentValue = new NbtValue(() -> client ? new NBTTagCompound() : PanelSnapshot.recents(panel, viewer));
            sync.syncValue("warpcovers_recents", recentValue);
            filter = new NbtValue(() -> client ? new NBTTagCompound() : PanelSnapshot.filter(cover.config()));
            status = new NbtValue(
                () -> client ? new NBTTagCompound()
                    : PanelSnapshot.status(
                        panel,
                        MinecraftServer.getServer()
                            .getTickCounter()));
            sync.syncValue("warpcovers_channels", channels);
            sync.syncValue("warpcovers_filter", filter);
            sync.syncValue("warpcovers_status", status);
            priority = new LongSyncValue(
                () -> client ? 0
                    : cover.config()
                        .priority(),
                value -> {
                    if (!client) {
                        panel.setPriority((int) NumberInput.clamp(value, Integer.MIN_VALUE, Integer.MAX_VALUE));
                    }
                });
            rate = new LongSyncValue(
                () -> client ? 0
                    : cover.config()
                        .rate(),
                value -> {
                    if (!client) {
                        panel.setRate(value);
                    }
                });
            period = new LongSyncValue(
                () -> client ? 0
                    : cover.config()
                        .period(),
                value -> {
                    if (!client) {
                        panel.setPeriod((int) NumberInput.clamp(value, 0, Integer.MAX_VALUE));
                    }
                });
            effectiveRate = new LongSyncValue(() -> client ? 0 : panel.effectiveRate());
            effectivePeriod = new LongSyncValue(() -> client ? 0 : panel.effectivePeriod());
            sync.syncValue("warpcovers_priority", priority);
            sync.syncValue("warpcovers_rate", rate);
            sync.syncValue("warpcovers_period", period);
            sync.syncValue("warpcovers_effective_rate", effectiveRate);
            sync.syncValue("warpcovers_effective_period", effectivePeriod);

            action("create", buf -> {
                String name = NetworkUtils.readStringSafe(buf);
                boolean connect = buf.readBoolean();
                panel.createChannel(viewer, name, connect);
            });
            action("toggle", buf -> panel.toggleConnection(viewer, NetworkUtils.readStringSafe(buf)));
            action("state", buf -> {
                InteractionState state = parseState(NetworkUtils.readStringSafe(buf));
                if (state != null) {
                    panel.setState(viewer, state);
                }
            });
            action("whitelist", buf -> panel.setWhitelist(buf.readBoolean()));
            action("matchnbt", buf -> panel.setMatchNbt(buf.readBoolean()));
            action("remove", buf -> panel.removeFilterKey(NetworkUtils.readStringSafe(buf)));
            action("clear", buf -> panel.clearFilter());
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
            sync.registerSyncedAction("warpcovers_" + key, false, true, buf -> {
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
            sync.callSyncedAction("warpcovers_" + key, writer);
        }

        IWidget build() {
            ParentWidget<?> root = new ParentWidget<>();
            root.size(ROOT_W, ROOT_H);

            PagedWidget.Controller controller = new PagedWidget.Controller();
            root.child(
                tab(
                    0,
                    controller,
                    new ItemDrawable(new ItemStack(Items.ender_pearl)).asIcon()
                        .size(16),
                    "warpcovers.gui.tab.channels"));
            root.child(
                tab(1, controller, GTGuiTextures.OVERLAY_BUTTON_SCREWDRIVER, "warpcovers.gui.tab.settings")
                    .pos(0, TAB_SIZE + 2));

            PagedWidget<?> pages = new PagedWidget<>();
            pages.controller(controller)
                .pos(PAGE_X, 0)
                .size(PAGE_W, PAGE_H);
            pages.addPage(channelsPage());
            pages.addPage(settingsPage());
            root.child(pages);

            root.child(
                IKey.dynamic(this::statusLine)
                    .asWidget()
                    .pos(0, STATUS_Y)
                    .size(ROOT_W, 10)
                    .color(TEXT_COLOR));
            root.child(
                IKey.dynamic(this::detailLine)
                    .asWidget()
                    .pos(0, STATUS_Y + 12)
                    .size(ROOT_W, 10)
                    .color(() -> detailIsError() ? ERROR_COLOR : TEXT_COLOR));
            return root;
        }

        private PageButton tab(int index, PagedWidget.Controller controller, IDrawable icon, String tooltipKey) {
            PageButton button = new PageButton(index, controller);
            button.pos(0, 0)
                .size(TAB_SIZE, TAB_SIZE)
                .background(true, GTGuiTextures.BUTTON_STANDARD_PRESSED)
                .background(false, GTGuiTextures.BUTTON_STANDARD)
                .overlay(icon)
                .addTooltipLine(tr(tooltipKey));
            return button;
        }

        // ---- 频道页 ----

        private IWidget channelsPage() {
            ParentWidget<?> page = new ParentWidget<>();
            page.size(PAGE_W, PAGE_H);

            ButtonWidget<?> create = new ButtonWidget<>();
            create.pos(0, 0)
                .size(CREATE_W, ROW_H)
                .background(
                    new DynamicDrawable(
                        () -> createBlock() != null ? GTGuiTextures.BUTTON_STANDARD_DISABLED
                            : GTGuiTextures.BUTTON_STANDARD))
                .overlay(
                    IKey.dynamic(() -> (createBlock() != null ? "\u00a78" : "") + tr("warpcovers.gui.new_channel")))
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
                .size(PAGE_W, PAGE_H - ROW_H - 3);
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
            list.size(PAGE_W, PAGE_H - ROW_H - 3);
            boolean any = false;
            ChannelGroup lastGroup = null;
            for (PanelSnapshot.ChannelRow row : view.rows) {
                if (!row.shown) {
                    continue;
                }
                any = true;
                boolean connected = view.isConnected(row.id);
                if (!connected && row.group != lastGroup) {
                    lastGroup = row.group;
                    list.child(
                        IKey.lang("warpcovers.gui.group." + row.group.key())
                            .asWidget()
                            .size(ROW_W, 10)
                            .color(OWNER_COLOR));
                }
                list.child(channelRow(row, connected));
                if (connected) {
                    list.child(stateRow(view.state));
                }
            }
            if (!any) {
                list.child(
                    IKey.lang(view.rows.isEmpty() ? "warpcovers.gui.no_channels" : "warpcovers.gui.no_match")
                        .asWidget()
                        .size(ROW_W, ROW_H)
                        .color(OWNER_COLOR));
            }
            return list;
        }

        /** 第一行：颜色、频道名、最近传输的图标；第二行：归属者、连接端数量、私人标签。 */
        private IWidget channelRow(PanelSnapshot.ChannelRow row, boolean connected) {
            boolean gear = row.hasConfigPanel();
            int width = gear ? ROW_W - 15 : ROW_W;
            boolean showRecents = cover.channelType()
                .hasFilter();
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
                    .color(TEXT_COLOR));
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
                    .color(OWNER_COLOR));

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
                    if (showRecents) {
                        for (String key : recents(row.id)) {
                            tooltip.addLine(tr("warpcovers.gui.row.recent", objectLabel(key)));
                        }
                    }
                    tooltip.addLine(tr(connected ? "warpcovers.gui.row.disconnect" : "warpcovers.gui.row.connect"));
                    tooltip.addLine(tr("warpcovers.gui.row.tags"));
                })
                .tooltipAutoUpdate(true);
            if (connected) {
                button.background(GTGuiTextures.BUTTON_STANDARD_PRESSED);
            }
            button.onMousePressed(mouse -> {
                if (mouse == 1) {
                    openTagDialog(button, row.id);
                    return true;
                }
                if (mouse != 0) {
                    return false;
                }
                send("toggle", buf -> NetworkUtils.writeStringSafe(buf, row.id));
                return true;
            });
            if (!gear) {
                return button;
            }
            ParentWidget<?> line = new ParentWidget<>();
            line.size(ROW_W, CHANNEL_ROW_H);
            line.child(button);
            ButtonWidget<?> open = new ButtonWidget<>();
            open.pos(ROW_W - 14, 4)
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

        private String rowDetail(PanelSnapshot.ChannelRow row) {
            StringBuilder text = new StringBuilder(row.ownerName);
            text.append("  ")
                .append(tr("warpcovers.gui.row.counts", row.inputs, row.outputs));
            for (String tag : row.tags) {
                text.append("  #")
                    .append(tag);
            }
            return text.toString();
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
                .size(PAGE_W - CREATE_W - TAG_FILTER_W - 4, 12)
                .addTooltipLine(tr("warpcovers.gui.search_tip"));
            page.child(field);

            ButtonWidget<?> tags = new ButtonWidget<>();
            tags.pos(PAGE_W - TAG_FILTER_W, 0)
                .size(TAG_FILTER_W, ROW_H)
                .overlay(IKey.dynamic(() -> {
                    String current = channelsView().tagFilter;
                    return current.isEmpty() ? tr("warpcovers.gui.tag.all") : "#" + current;
                }))
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
            Dialog<Boolean> dialog = new Dialog<>("warpcovers_channel_tags");
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
                .overlay(IKey.lang("warpcovers.gui.tag.add"));
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
                    .overlay(IKey.dynamic(() -> "#" + tagAt(index)))
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
                    .overlay(IKey.lang(WarpCoverItem.stateKey(state)))
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

            Dialog<Boolean> dialog = new Dialog<>("warpcovers_new_channel");
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
                .size(10, 10);
            dialog.child(toggle);
            dialog.child(
                IKey.lang("warpcovers.gui.dialog.connect")
                    .asWidget()
                    .pos(22, 58)
                    .color(TEXT_COLOR));

            ButtonWidget<?> confirm = new ButtonWidget<>();
            confirm.pos(8, 74)
                .size(76, 14)
                .overlay(IKey.lang("warpcovers.gui.dialog.confirm"));
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
                .overlay(IKey.lang("warpcovers.gui.dialog.cancel"));
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
            configDialog.deleteCachedPanel();
            configDialog.openPanel();
        }

        private Dialog<Boolean> configPanel() {
            Dialog<Boolean> dialog = new Dialog<>("warpcovers_channel_config");
            dialog.setDraggable(false);
            dialog.setCloseOnOutOfBoundsClick(true);
            dialog.size(CFG_W, CFG_H);
            // 删除确认弹窗挂在配置面板上，配置面板每次打开都是新对象，要跟着重建
            deleteDialog = null;
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

            dialog.child(
                IKey.dynamic(this::configFeedback)
                    .asWidget()
                    .pos(8, 100)
                    .size(CFG_W - 16, 9)
                    .color(() -> configFeedbackIsError() ? ERROR_COLOR : TEXT_COLOR));

            if (row.canDelete) {
                ButtonWidget<?> delete = new ButtonWidget<>();
                delete.pos(8, 113)
                    .size(70, 14)
                    .overlay(IKey.lang("warpcovers.gui.config.delete"));
                delete.onMousePressed(mouse -> {
                    openDeleteDialog(dialog);
                    return true;
                });
                dialog.child(delete);
            }
            ButtonWidget<?> close = new ButtonWidget<>();
            close.pos(CFG_W - 78, 113)
                .size(70, 14)
                .overlay(IKey.lang("warpcovers.gui.config.close"));
            close.onMousePressed(mouse -> {
                dialog.closeWith(true);
                return true;
            });
            dialog.child(close);
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
                            tr("warpcovers.gui.config.distribution." + currentDistribution().key()))))
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
                .overlay(IKey.dynamic(() -> tr("warpcovers.gui.config.preset", tr(presetKey()))))
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
            Dialog<Boolean> dialog = new Dialog<>("warpcovers_delete_channel");
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
                .overlay(IKey.lang("warpcovers.gui.config.delete_yes"));
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
                .overlay(IKey.lang("warpcovers.gui.dialog.cancel"));
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
            page.size(PAGE_W, PAGE_H);
            addTransferFields(page);
            if (!cover.channelType()
                .hasFilter()) {
                return energyNotes(page);
            }

            ButtonWidget<?> mode = new ButtonWidget<>();
            mode.pos(0, FILTER_Y)
                .size(16, 16)
                .overlay(
                    new DynamicDrawable(
                        () -> isWhitelist() ? GTGuiTextures.OVERLAY_BUTTON_WHITELIST
                            : GTGuiTextures.OVERLAY_BUTTON_BLACKLIST))
                .addTooltipLine(tr("warpcovers.gui.filter.toggle"));
            mode.onMousePressed(mouse -> {
                boolean next = !isWhitelist();
                send("whitelist", buf -> buf.writeBoolean(next));
                return true;
            });
            page.child(mode);
            page.child(
                IKey.dynamic(
                    () -> tr(isWhitelist() ? "warpcovers.gui.filter.whitelist" : "warpcovers.gui.filter.blacklist"))
                    .asWidget()
                    .pos(20, FILTER_Y + 4)
                    .size(PAGE_W - 44, 9)
                    .color(TEXT_COLOR));

            ButtonWidget<?> clear = new ButtonWidget<>();
            clear.pos(PAGE_W - 18, FILTER_Y)
                .size(16, 16)
                .overlay(GTGuiTextures.OVERLAY_BUTTON_CROSS)
                .addTooltipLine(tr("warpcovers.gui.filter.clear"));
            clear.onMousePressed(mouse -> {
                send("clear", buf -> {});
                return true;
            });
            page.child(clear);

            Consumer<String> addKey = client ? null : key -> panel.addFilterKey(key);
            if (isItem()) {
                ButtonWidget<?> nbt = new ButtonWidget<>();
                nbt.pos(PAGE_W - 38, FILTER_Y)
                    .size(16, 16)
                    .background(
                        new DynamicDrawable(
                            () -> isMatchNbt() ? GTGuiTextures.BUTTON_STANDARD_PRESSED : GTGuiTextures.BUTTON_STANDARD))
                    .overlay(GTGuiTextures.OVERLAY_BUTTON_NBT)
                    .tooltipDynamic(
                        tooltip -> tooltip.addLine(
                            tr(isMatchNbt() ? "warpcovers.gui.filter.nbt_on" : "warpcovers.gui.filter.nbt_off")))
                    .tooltipAutoUpdate(true);
                nbt.onMousePressed(mouse -> {
                    boolean next = !isMatchNbt();
                    send("matchnbt", buf -> buf.writeBoolean(next));
                    return true;
                });
                page.child(nbt);

                PhantomItemSlot slot = new PhantomItemSlot().slot(new ModularSlot(new FilterInputInventory(addKey), 0));
                slot.pos(0, FILTER_Y + 20)
                    .addTooltipLine(tr("warpcovers.gui.filter.add_item"));
                page.child(slot);
            } else {
                FilterInputTank tank = new FilterInputTank(addKey);
                FluidSlot slot = new FluidSlot();
                slot.syncHandler(
                    new FluidSlotSyncHandler(tank).phantom(true)
                        .controlsAmount(false))
                    .pos(0, FILTER_Y + 20)
                    .addTooltipLine(tr("warpcovers.gui.filter.add"));
                page.child(slot);
            }
            page.child(
                IKey.lang(isItem() ? "warpcovers.gui.filter.hint_item" : "warpcovers.gui.filter.hint")
                    .asWidget()
                    .pos(22, FILTER_Y + 25)
                    .size(PAGE_W - 24, 9)
                    .scale(0.8F)
                    .color(OWNER_COLOR));

            DynamicLinkedSyncHandler<NbtValue> dynamic = new DynamicLinkedSyncHandler<>(filter)
                .widgetProvider((manager, value) -> filterList(PanelSnapshot.parseFilter(value.getValue())));
            DynamicSyncedWidget<?> holder = new DynamicSyncedWidget<>();
            holder.syncHandler(dynamic)
                .pos(0, LIST_Y)
                .size(PAGE_W, PAGE_H - LIST_Y);
            page.child(holder);
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
            TextFieldWidget field = new TextFieldWidget();
            field.value(value)
                .setValidator(text -> NumberInput.normalize(text, min, max));
            field.pos(x, 10)
                .size(width, 12)
                .tooltipDynamic(lines -> lines.addLine(tooltip.get()))
                .tooltipAutoUpdate(true);
            page.child(field);
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

        private IWidget filterList(PanelSnapshot.FilterView view) {
            ListWidget<IWidget, ?> list = new ListWidget<>();
            list.size(PAGE_W, PAGE_H - LIST_Y);
            if (view.keys.isEmpty()) {
                list.child(
                    IKey.lang("warpcovers.gui.filter.empty")
                        .asWidget()
                        .size(ROW_W, ROW_H)
                        .color(OWNER_COLOR));
                return list;
            }
            for (String key : view.keys) {
                list.child(filterRow(key));
            }
            return list;
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

        private IWidget filterRow(String key) {
            IDrawable icon = objectIcon(key);
            String label = objectLabel(key);

            ParentWidget<?> row = new ParentWidget<>();
            row.size(ROW_W, ROW_H);
            row.child(
                icon.asWidget()
                    .pos(1, 1)
                    .size(12, 12)
                    .addTooltipLine(label));
            row.child(
                new ScrollingTextWidget(IKey.str(label)).pos(16, 3)
                    .size(ROW_W - 34, 8)
                    .color(TEXT_COLOR));
            ButtonWidget<?> remove = new ButtonWidget<>();
            remove.pos(ROW_W - 14, 1)
                .size(12, 12)
                .overlay(GuiTextures.CLOSE)
                .addTooltipLine(tr("warpcovers.gui.filter.remove"));
            remove.onMousePressed(mouse -> {
                send("remove", buf -> NetworkUtils.writeStringSafe(buf, key));
                return true;
            });
            row.child(remove);
            return row;
        }

        // ---- 状态栏 ----

        private PanelSnapshot.StatusView statusView() {
            return PanelSnapshot.parseStatus(status.getValue());
        }

        private String statusLine() {
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
            return "";
        }

        private boolean detailIsError() {
            PanelSnapshot.StatusView view = statusView();
            return !view.noticeKey.isEmpty() ? view.noticeError : view.reason != null;
        }

        private boolean isWhitelist() {
            return PanelSnapshot.parseFilter(filter.getValue()).whitelist;
        }

        private boolean isMatchNbt() {
            return PanelSnapshot.parseFilter(filter.getValue()).matchNbt;
        }

        /** 能量没有过滤名单：设置页只说明直灌规则。 */
        private IWidget energyNotes(ParentWidget<?> page) {
            String[] keys = { "warpcovers.gui.energy.no_filter", "warpcovers.gui.energy.direct",
                "warpcovers.gui.energy.source" };
            int[] heights = { 10, 30, 30 };
            int y = FILTER_Y + 2;
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

        private boolean isItem() {
            return cover.channelType() == ChannelType.ITEM;
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
