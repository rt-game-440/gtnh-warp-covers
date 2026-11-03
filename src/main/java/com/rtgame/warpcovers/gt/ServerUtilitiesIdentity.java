package com.rtgame.warpcovers.gt;

import java.util.UUID;

import net.minecraftforge.common.MinecraftForge;

import com.rtgame.warpcovers.core.Identity;
import com.rtgame.warpcovers.core.IdentityPort;
import com.rtgame.warpcovers.core.PersonalIdentity;
import com.rtgame.warpcovers.core.Viewer;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import serverutils.events.team.ForgeTeamDeletedEvent;
import serverutils.events.team.ForgeTeamOwnerChangedEvent;
import serverutils.events.team.ForgeTeamPlayerJoinedEvent;
import serverutils.events.team.ForgeTeamPlayerLeftEvent;
import serverutils.lib.EnumTeamStatus;
import serverutils.lib.data.ForgePlayer;
import serverutils.lib.data.ForgeTeam;
import serverutils.lib.data.Universe;

/**
 * ServerUtilities 实现的身份端口。ServerUtilities 是可选依赖：只有确认已安装才会加载这个类。
 *
 * <p>
 * 退队、入队、换队长、解散有事件；管理员和结盟变化（{@code ForgeTeam.setStatus}）没有事件，
 * 靠 {@link WarpEvents} 定时复查兜底。退队事件触发时状态还没改完，所以事件里只是请求在本 tick 结束时复查。
 */
public final class ServerUtilitiesIdentity implements IdentityPort {

    private static final String TEAM_PREFIX = "team:";

    public static ServerUtilitiesIdentity install() {
        ServerUtilitiesIdentity identity = new ServerUtilitiesIdentity();
        MinecraftForge.EVENT_BUS.register(identity);
        return identity;
    }

    private ServerUtilitiesIdentity() {}

    public static String ownerIdOf(ForgeTeam team) {
        return TEAM_PREFIX + team.getUID();
    }

    private static ForgePlayer playerOf(String playerId) {
        Universe universe = Universe.getNullable();
        if (universe == null) {
            return null;
        }
        try {
            return universe.getPlayer(UUID.fromString(playerId));
        } catch (IllegalArgumentException e) {
            // 连接者不是 UUID（空串、自检用的占位名）
            return null;
        }
    }

    private static ForgeTeam teamOf(String ownerId) {
        Universe universe = Universe.getNullable();
        if (universe == null || !ownerId.startsWith(TEAM_PREFIX)) {
            return null;
        }
        try {
            return universe.getTeam(Short.parseShort(ownerId.substring(TEAM_PREFIX.length())));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public boolean isReady() {
        return Universe.loaded();
    }

    @Override
    public String ownerFor(String playerId) {
        ForgePlayer player = playerOf(playerId);
        if (player != null && player.hasTeam()) {
            return ownerIdOf(player.team);
        }
        return Viewer.personalOwnerId(playerId);
    }

    @Override
    public String ownerName(String ownerId, String playerName) {
        ForgeTeam team = teamOf(ownerId);
        return team == null ? playerName
            : team.getTitle()
                .getUnformattedText();
    }

    @Override
    public Identity identityOf(String playerId, String ownerId) {
        if (!ownerId.startsWith(TEAM_PREFIX)) {
            return PersonalIdentity.INSTANCE.identityOf(playerId, ownerId);
        }
        ForgeTeam team = teamOf(ownerId);
        ForgePlayer player = playerOf(playerId);
        if (team == null || !team.isValid() || player == null) {
            return Identity.STRANGER;
        }
        EnumTeamStatus status = team.getHighestStatus(player);
        switch (status) {
            case OWNER:
            case MOD:
                return Identity.MANAGER;
            case MEMBER:
                return Identity.MEMBER;
            case ALLY:
                return Identity.ALLY;
            default:
                return Identity.STRANGER;
        }
    }

    @SubscribeEvent
    public void onPlayerLeft(ForgeTeamPlayerLeftEvent event) {
        WarpEvents.requestPermissionCheck();
    }

    @SubscribeEvent
    public void onPlayerJoined(ForgeTeamPlayerJoinedEvent event) {
        WarpEvents.requestPermissionCheck();
    }

    @SubscribeEvent
    public void onOwnerChanged(ForgeTeamOwnerChangedEvent event) {
        WarpEvents.requestPermissionCheck();
    }

    /** 解散：频道转给原队长本人，队伍里的其他人随后按陌生人复查。 */
    @SubscribeEvent
    public void onTeamDeleted(ForgeTeamDeletedEvent event) {
        ForgeTeam team = event.getTeam();
        ForgePlayer leader = team.getOwner();
        if (!WarpServices.isInstalled() || leader == null) {
            return;
        }
        WarpServices.hub()
            .transferOwner(
                ownerIdOf(team),
                Viewer.personalOwnerId(
                    leader.getId()
                        .toString()),
                leader.getName());
        WarpEvents.requestPermissionCheck();
    }
}
