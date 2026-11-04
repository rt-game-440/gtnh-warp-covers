package com.rtgame.warpcovers.selfcheck;

import static com.rtgame.warpcovers.selfcheck.ScenarioWorld.*;

import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;

import com.rtgame.warpcovers.core.Channel;
import com.rtgame.warpcovers.core.ChannelHub;
import com.rtgame.warpcovers.core.ChannelType;
import com.rtgame.warpcovers.core.CreateFailure;
import com.rtgame.warpcovers.core.DisconnectReason;
import com.rtgame.warpcovers.core.Identity;
import com.rtgame.warpcovers.core.InteractionState;
import com.rtgame.warpcovers.core.Viewer;
import com.rtgame.warpcovers.gt.FluidWarpCover;
import com.rtgame.warpcovers.gt.ServerUtilitiesIdentity;
import com.rtgame.warpcovers.gt.WarpServices;

import cpw.mods.fml.common.Loader;
import gregtech.api.covers.CoverRegistry;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import serverutils.lib.EnumTeamStatus;
import serverutils.lib.data.ForgePlayer;
import serverutils.lib.data.ForgeTeam;
import serverutils.lib.data.TeamType;
import serverutils.lib.data.Universe;

/**
 * 归属与权限场景：在真实的 ServerUtilities 数据上用假玩家和临时队伍验证身份判定、退队断开、
 * 无权限者贴板只断开频道、解散转移。这些队伍和假玩家在布置结束时全部清理，重启后校验断开结果和转移结果已存盘。
 * 另留一支队伍跨过重启：重启后队伍频道的连接端不能被误判失权，校验完再清理。
 */
public final class OwnershipScenario implements SelfCheckScenario {

    private static final int X = 80;
    private static final int Y = 200;
    private static final int Z = 80;
    private static final String TEAM_ID = "warpcovers_selfcheck";
    private static final String TEAM_CHANNEL = "自检队伍频道";
    private static final String KEEP_TEAM_ID = "warpcovers_selfcheck_keep";
    private static final String KEEP_CHANNEL = "自检跨重启队伍频道";
    private static final UUID KEEPER = UUID.fromString("00000000-0000-4000-8000-00000000a004");
    private static final UUID LEADER = UUID.fromString("00000000-0000-4000-8000-00000000a001");
    private static final UUID MEMBER = UUID.fromString("00000000-0000-4000-8000-00000000a002");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-4000-8000-00000000a003");

    @Override
    public String name() {
        return "ownership";
    }

    private static ForgePlayer addPlayer(Universe universe, UUID id, String name) {
        ForgePlayer player = new ForgePlayer(universe, id, name);
        player.team = universe.getTeam("");
        universe.players.put(id, player);
        return player;
    }

    @Override
    public void setup() {
        check("开发环境应装入 ServerUtilities", Loader.isModLoaded("serverutilities"));
        Universe universe = Universe.get();
        ChannelHub hub = WarpServices.hub();
        check("核心应使用 ServerUtilities 身份端口", hub.identity() instanceof ServerUtilitiesIdentity);
        deleteChannelsNamed(hub, TEAM_CHANNEL);
        deleteChannelsNamed(hub, TEAM_CHANNEL + " 2");

        ForgePlayer leader = addPlayer(universe, LEADER, "wc_leader");
        ForgePlayer member = addPlayer(universe, MEMBER, "wc_member");
        ForgePlayer stranger = addPlayer(universe, STRANGER, "wc_stranger");
        ForgeTeam team = new ForgeTeam(universe, universe.generateTeamUID((short) 0), TEAM_ID, TeamType.PLAYER);
        team.owner = leader;
        universe.addTeam(team);
        leader.team = team;
        check("组员入队", team.forceAddMember(member));
        try {
            run(hub, team, member, stranger);
        } finally {
            universe.players.remove(LEADER);
            universe.players.remove(MEMBER);
            universe.players.remove(STRANGER);
            if (universe.getTeam(TEAM_ID)
                .isValid()) {
                universe.removeTeam(team);
            }
        }
        setupKeeper(universe, hub);
    }

    private void setupKeeper(Universe universe, ChannelHub hub) {
        removeKeeper(universe);
        deleteChannelsNamed(hub, KEEP_CHANNEL);
        ForgePlayer keeper = addPlayer(universe, KEEPER, "wc_keeper");
        ForgeTeam team = new ForgeTeam(universe, universe.generateTeamUID((short) 0), KEEP_TEAM_ID, TeamType.PLAYER);
        team.owner = keeper;
        universe.addTeam(team);
        keeper.team = team;
        team.markDirty();
        keeper.markDirty();
        Channel channel = hub
            .createChannel(ChannelType.FLUID, KEEP_CHANNEL, ServerUtilitiesIdentity.ownerIdOf(team), "队伍", 0)
            .channel();
        attachCover(placeTank(world(0), X + 6, Y, Z), ForgeDirection.UP).connect(channel.id(), KEEPER.toString());
    }

    private static void removeKeeper(Universe universe) {
        ForgeTeam team = universe.getTeam(KEEP_TEAM_ID);
        if (team.isValid()) {
            universe.removeTeam(team);
        }
        universe.players.remove(KEEPER);
    }

    /** @return 失败原因；通过时为 null */
    private static String verifyKeeper(ChannelHub hub) {
        Universe universe = Universe.get();
        try {
            ForgePlayer keeper = universe.getPlayer(KEEPER);
            if (keeper == null || !keeper.hasTeam()) {
                return "重启后 ServerUtilities 没有留下跨重启的队伍";
            }
            Channel channel = channelNamed(hub, KEEP_CHANNEL);
            IGregTechTileEntity tank = tileAt(world(0), X + 6, Y, Z);
            if (tank == null) {
                return "重启后跨重启队伍的储罐丢失";
            }
            FluidWarpCover cover = coverAt(tank, ForgeDirection.UP);
            if (!channel.id()
                .equals(cover.channelId())) {
                return "重启后队伍频道的连接端被误断: " + cover.lastReason();
            }
            if (hub.reevaluateConnections() != 0) {
                return "重启后复查不应断开任何连接端";
            }
            return null;
        } finally {
            removeKeeper(universe);
        }
    }

    private void run(ChannelHub hub, ForgeTeam team, ForgePlayer member, ForgePlayer stranger) {
        String teamOwner = ServerUtilitiesIdentity.ownerIdOf(team);
        String memberId = MEMBER.toString();
        String strangerId = STRANGER.toString();
        String leaderOwner = Viewer.personalOwnerId(LEADER.toString());

        // 归属与身份
        check(
            "在队伍里新建的频道归队伍",
            teamOwner.equals(
                hub.identity()
                    .ownerFor(memberId)));
        check(
            "不在队伍里新建的频道归本人",
            Viewer.personalOwnerId(strangerId)
                .equals(
                    hub.identity()
                        .ownerFor(strangerId)));
        check(
            "队长是管理者",
            hub.identity()
                .identityOf(LEADER.toString(), teamOwner) == Identity.MANAGER);
        check(
            "成员是组员",
            hub.identity()
                .identityOf(memberId, teamOwner) == Identity.MEMBER);
        check(
            "其他人是陌生人",
            hub.identity()
                .identityOf(strangerId, teamOwner) == Identity.STRANGER);
        team.setStatus(stranger, EnumTeamStatus.ALLY);
        check(
            "结盟后是盟友",
            hub.identity()
                .identityOf(strangerId, teamOwner) == Identity.ALLY);
        team.setStatus(stranger, EnumTeamStatus.NONE);
        check("组员默认能新增频道", hub.canCreate(memberId, ChannelType.FLUID) == null);
        hub.setCreateAllowed(teamOwner, Identity.MEMBER, false);
        check("归属者级权限关闭后组员不能新增", hub.canCreate(memberId, ChannelType.FLUID) == CreateFailure.NO_PERMISSION);
        hub.setCreateAllowed(teamOwner, Identity.MEMBER, true);

        WorldServer world = world(0);
        IGregTechTileEntity tank = placeTank(world, X, Y, Z);
        IGregTechTileEntity tank2 = placeTank(world, X + 2, Y, Z);
        IGregTechTileEntity tank3 = placeTank(world, X + 4, Y, Z);
        Channel channel = hub.createChannel(ChannelType.FLUID, TEAM_CHANNEL, teamOwner, "队伍", 0)
            .channel();
        // 队长本人已有同名频道：解散时要加后缀
        hub.createChannel(ChannelType.FLUID, TEAM_CHANNEL, leaderOwner, "wc_leader", 0);

        // 退队断开
        FluidWarpCover memberCover = attachCover(tank, ForgeDirection.UP);
        memberCover.connect(channel.id(), memberId);
        memberCover.setInteractionState(InteractionState.INPUT);
        check("组员连接后复查不应断开", hub.reevaluateConnections() == 0);
        team.removeMember(member);
        expect("退队后断开的连接端数", 1, hub.reevaluateConnections());
        check(
            "退队后连接端应断开",
            memberCover.channelId()
                .isEmpty());
        check("断开原因应为连接者失权", memberCover.lastReason() == DisconnectReason.PERMISSION_LOST);

        // 无权限者贴带配置的覆盖板：只断开频道，其余配置保留
        FluidWarpCover configured = attachCover(tank2, ForgeDirection.UP);
        configured.connect(channel.id(), LEADER.toString());
        configured.config()
            .setPriority(5);
        configured.config()
            .setRate(123);
        configured.configChanged();
        ItemStack item = configured.asItemStack();
        // 假玩家不属于队伍，就是陌生人
        CoverRegistry.getCoverPlacer(item)
            .placeCover(fakePlayer(world), item, tank3, ForgeDirection.UP);
        FluidWarpCover pasted = coverAt(tank3, ForgeDirection.UP);
        check(
            "无权限者贴板后频道应断开",
            pasted.channelId()
                .isEmpty());
        check("断开原因应为贴上者没有权限", pasted.lastReason() == DisconnectReason.NO_PERMISSION);
        expect(
            "优先级应保留",
            5,
            pasted.config()
                .priority());
        expect(
            "速率应保留",
            123,
            pasted.config()
                .rate());
        check(
            "队长的连接端不受影响",
            channel.id()
                .equals(configured.channelId()));

        // 解散：频道转给原队长，同名加后缀
        team.delete();
        check("频道应转给原队长", leaderOwner.equals(channel.ownerId()));
        check("同名冲突应加后缀，实际: " + channel.name(), (TEAM_CHANNEL + " 2").equals(channel.name()));
        check(
            "转移后队长的连接端仍在",
            channel.id()
                .equals(configured.channelId()));
    }

    @Override
    public SelfCheckResult verify() {
        String keeperFailure = verifyKeeper(WarpServices.hub());
        if (keeperFailure != null) {
            return SelfCheckResult.fail(keeperFailure);
        }
        WorldServer world = world(0);
        IGregTechTileEntity tank = tileAt(world, X, Y, Z);
        IGregTechTileEntity tank3 = tileAt(world, X + 4, Y, Z);
        if (tank == null || tank3 == null) {
            return SelfCheckResult.fail("重启后有储罐丢失");
        }
        FluidWarpCover memberCover = coverAt(tank, ForgeDirection.UP);
        if (!memberCover.channelId()
            .isEmpty() || memberCover.lastReason() != DisconnectReason.PERMISSION_LOST) {
            return SelfCheckResult.fail("重启后失权断开状态丢失: " + memberCover.channelId() + " / " + memberCover.lastReason());
        }
        FluidWarpCover pasted = coverAt(tank3, ForgeDirection.UP);
        if (!pasted.channelId()
            .isEmpty()
            || pasted.config()
                .priority() != 5) {
            return SelfCheckResult.fail("重启后贴板断开状态或优先级丢失");
        }
        Channel moved = channelNamed(WarpServices.hub(), TEAM_CHANNEL + " 2");
        if (!Viewer.personalOwnerId(LEADER.toString())
            .equals(moved.ownerId())) {
            return SelfCheckResult.fail("重启后频道归属不对: " + moved.ownerId());
        }
        return SelfCheckResult.pass("身份判定、退队断开、无权限贴板、解散转移均符合预期，重启后结果都在，队伍频道的连接端没有被误断");
    }
}
