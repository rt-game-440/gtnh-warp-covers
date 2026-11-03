package com.rtgame.warpcovers.core;

/**
 * 一个频道的权限矩阵：身份 × 权限项。新频道取默认矩阵（需求 4.4），陌生人默认为私人。
 * 「私人」「公开」只是陌生人身份的两套预设。
 */
public final class ChannelPermissions {

    private static final int STRANGER_PUBLIC = Permission.VIEW.bit() | Permission.CONNECT.bit();
    private static final int ALL = Permission.VIEW.bit() | Permission.CONNECT.bit()
        | Permission.CONFIGURE.bit()
        | Permission.MANAGE_PERMISSIONS.bit()
        | Permission.DELETE.bit();

    private final int[] masks = new int[Identity.values().length];

    public ChannelPermissions() {
        masks[Identity.MANAGER.ordinal()] = ALL;
        masks[Identity.MEMBER.ordinal()] = Permission.VIEW.bit() | Permission.CONNECT.bit()
            | Permission.CONFIGURE.bit();
        masks[Identity.ALLY.ordinal()] = Permission.VIEW.bit() | Permission.CONNECT.bit();
        masks[Identity.STRANGER.ordinal()] = 0;
    }

    public boolean allows(Identity identity, Permission permission) {
        return (masks[identity.ordinal()] & permission.bit()) != 0;
    }

    void set(Identity identity, Permission permission, boolean allowed) {
        if (allowed) {
            masks[identity.ordinal()] |= permission.bit();
        } else {
            masks[identity.ordinal()] &= ~permission.bit();
        }
    }

    /** 陌生人的权限正好是「公开」预设。 */
    public boolean isPublic() {
        return masks[Identity.STRANGER.ordinal()] == STRANGER_PUBLIC;
    }

    void applyStrangerPreset(boolean isPublic) {
        masks[Identity.STRANGER.ordinal()] = isPublic ? STRANGER_PUBLIC : 0;
    }

    /** 持久化用：每种身份一个位掩码，顺序同 {@link Identity}。 */
    public int[] toMasks() {
        return masks.clone();
    }

    /** 从持久化数据恢复；长度不符时返回默认矩阵。 */
    public static ChannelPermissions fromMasks(int[] stored) {
        ChannelPermissions permissions = new ChannelPermissions();
        if (stored != null && stored.length == permissions.masks.length) {
            for (int i = 0; i < stored.length; i++) {
                permissions.masks[i] = stored[i] & ALL;
            }
        }
        return permissions;
    }
}
