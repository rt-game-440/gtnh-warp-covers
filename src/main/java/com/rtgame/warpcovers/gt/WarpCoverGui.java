package com.rtgame.warpcovers.gt;

import java.util.Random;
import java.util.function.Consumer;
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
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.rtgame.warpcovers.WarpCovers;
import com.rtgame.warpcovers.core.ChannelHub;
import com.rtgame.warpcovers.core.ChannelType;
import com.rtgame.warpcovers.core.CoverPanel;
import com.rtgame.warpcovers.core.CreateFailure;
import com.rtgame.warpcovers.core.InteractionState;
import com.rtgame.warpcovers.core.Viewer;

import gregtech.api.modularui2.CoverGuiData;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.gui.modularui.cover.base.CoverBaseGui;

/**
 * 流体折跃覆盖板界面：左侧边栏「频道」「设置」两页，底部状态栏。
 * 服务端按玩家构建快照（{@link PanelSnapshot}）同步给客户端，客户端的每个操作都是发往服务端的请求，
 * 由 {@link CoverPanel} 校验后执行，结果随下一次快照回到界面。
 */
public final class WarpCoverGui extends CoverBaseGui<FluidWarpCover> {

    private static final int ROOT_W = 196;
    private static final int ROOT_H = 146;
    private static final int TAB_SIZE = 20;
    private static final int PAGE_X = 24;
    private static final int PAGE_W = ROOT_W - PAGE_X;
    private static final int PAGE_H = 118;
    private static final int ROW_W = PAGE_W - 8;
    private static final int ROW_H = 14;
    private static final int STATUS_Y = PAGE_H + 4;

    private static final int TEXT_COLOR = 0xFF555555;
    private static final int OWNER_COLOR = 0xFF888888;
    private static final int ERROR_COLOR = 0xFFAA0000;

    private static final InteractionState[] STATES = { InteractionState.INPUT, InteractionState.OUTPUT,
        InteractionState.EMPTY };

    public WarpCoverGui(FluidWarpCover cover) {
        super(cover);
    }

    @Override
    protected String getGuiId() {
        return "warpcovers.fluid";
    }

    @Override
    protected boolean doesBindPlayerInventory() {
        // 名单靠光标上的流体容器添加，需要能从背包拿起容器
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
        private final NbtValue filter;
        private final NbtValue status;
        private IPanelHandler createDialog;

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
                panel = new CoverPanel(WarpServices.hub(), ChannelType.FLUID, cover, new Random());
            }
            channels = new NbtValue(() -> client ? new NBTTagCompound() : PanelSnapshot.channels(panel, viewer));
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

            action("create", buf -> {
                String name = NetworkUtils.readStringSafe(buf);
                boolean connect = buf.readBoolean();
                panel.createChannel(viewer, name, connect);
            });
            action("toggle", buf -> panel.toggleConnection(viewer, NetworkUtils.readStringSafe(buf)));
            action("state", buf -> {
                InteractionState state = parseState(NetworkUtils.readStringSafe(buf));
                if (state != null) {
                    panel.setState(state);
                }
            });
            action("whitelist", buf -> panel.setWhitelist(buf.readBoolean()));
            action("remove", buf -> panel.removeFilterKey(NetworkUtils.readStringSafe(buf)));
            action("clear", buf -> panel.clearFilter());
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
                .size(70, ROW_H)
                .overlay(IKey.lang("warpcovers.gui.new_channel"));
            create.onMousePressed(mouse -> {
                openCreateDialog(create);
                return true;
            });
            page.child(create);

            DynamicLinkedSyncHandler<NbtValue> dynamic = new DynamicLinkedSyncHandler<>(channels)
                .widgetProvider((manager, value) -> channelList(PanelSnapshot.parseChannels(value.getValue())));
            DynamicSyncedWidget<?> holder = new DynamicSyncedWidget<>();
            holder.syncHandler(dynamic)
                .pos(0, ROW_H + 3)
                .size(PAGE_W, PAGE_H - ROW_H - 3);
            page.child(holder);
            return page;
        }

        private IWidget channelList(PanelSnapshot.ChannelsView view) {
            ListWidget<IWidget, ?> list = new ListWidget<>();
            list.size(PAGE_W, PAGE_H - ROW_H - 3);
            if (view.rows.isEmpty()) {
                list.child(
                    IKey.lang("warpcovers.gui.no_channels")
                        .asWidget()
                        .size(ROW_W, ROW_H)
                        .color(OWNER_COLOR));
                return list;
            }
            for (PanelSnapshot.ChannelRow row : view.rows) {
                boolean connected = view.isConnected(row.id);
                list.child(channelRow(row, connected));
                if (connected) {
                    list.child(stateRow(view.state));
                }
            }
            return list;
        }

        private IWidget channelRow(PanelSnapshot.ChannelRow row, boolean connected) {
            ParentWidget<?> content = new ParentWidget<>();
            content.size(ROW_W, ROW_H);
            content.child(
                new Rectangle().color(0xFF000000 | row.color)
                    .asWidget()
                    .pos(3, 3)
                    .size(8, 8));
            content.child(
                new ScrollingTextWidget(IKey.str(row.name)).pos(14, 3)
                    .size(ROW_W - 70, 8)
                    .color(TEXT_COLOR));
            content.child(
                IKey.str(row.ownerName)
                    .asWidget()
                    .pos(ROW_W - 54, 3)
                    .size(51, 8)
                    .alignment(Alignment.CenterRight)
                    .color(OWNER_COLOR));

            ButtonWidget<?> button = new ButtonWidget<>();
            button.size(ROW_W, ROW_H)
                .child(content)
                .addTooltipLine(row.name)
                .addTooltipLine(tr("warpcovers.gui.owner", row.ownerName))
                .addTooltipLine(tr(connected ? "warpcovers.gui.row.disconnect" : "warpcovers.gui.row.connect"));
            if (connected) {
                button.background(GTGuiTextures.BUTTON_STANDARD_PRESSED);
            }
            button.onMousePressed(mouse -> {
                if (mouse != 0) {
                    return false;
                }
                send("toggle", buf -> NetworkUtils.writeStringSafe(buf, row.id));
                return true;
            });
            return button;
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

        // ---- 设置页 ----

        private IWidget settingsPage() {
            ParentWidget<?> page = new ParentWidget<>();
            page.size(PAGE_W, PAGE_H);

            ButtonWidget<?> mode = new ButtonWidget<>();
            mode.pos(0, 0)
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
                    .pos(20, 4)
                    .size(PAGE_W - 44, 9)
                    .color(TEXT_COLOR));

            ButtonWidget<?> clear = new ButtonWidget<>();
            clear.pos(PAGE_W - 18, 0)
                .size(16, 16)
                .overlay(GTGuiTextures.OVERLAY_BUTTON_CROSS)
                .addTooltipLine(tr("warpcovers.gui.filter.clear"));
            clear.onMousePressed(mouse -> {
                send("clear", buf -> {});
                return true;
            });
            page.child(clear);

            FilterInputTank tank = new FilterInputTank(client ? null : key -> panel.addFilterKey(key));
            FluidSlot slot = new FluidSlot();
            slot.syncHandler(
                new FluidSlotSyncHandler(tank).phantom(true)
                    .controlsAmount(false))
                .pos(0, 20)
                .addTooltipLine(tr("warpcovers.gui.filter.add"));
            page.child(slot);
            page.child(
                IKey.lang("warpcovers.gui.filter.hint")
                    .asWidget()
                    .pos(22, 25)
                    .size(PAGE_W - 24, 9)
                    .scale(0.8F)
                    .color(OWNER_COLOR));

            DynamicLinkedSyncHandler<NbtValue> dynamic = new DynamicLinkedSyncHandler<>(filter)
                .widgetProvider((manager, value) -> filterList(PanelSnapshot.parseFilter(value.getValue())));
            DynamicSyncedWidget<?> holder = new DynamicSyncedWidget<>();
            holder.syncHandler(dynamic)
                .pos(0, 42)
                .size(PAGE_W, PAGE_H - 42);
            page.child(holder);
            return page;
        }

        private IWidget filterList(PanelSnapshot.FilterView view) {
            ListWidget<IWidget, ?> list = new ListWidget<>();
            list.size(PAGE_W, PAGE_H - 42);
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

        private IWidget filterRow(String key) {
            Fluid fluid = FluidRegistry.getFluid(key);
            FluidStack stack = fluid == null ? null : new FluidStack(fluid, 1000);
            String label = stack == null ? key : stack.getLocalizedName();

            ParentWidget<?> row = new ParentWidget<>();
            row.size(ROW_W, ROW_H);
            row.child(
                new FluidDrawable(stack).asWidget()
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
    }

    private static InteractionState parseState(String name) {
        try {
            return InteractionState.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
