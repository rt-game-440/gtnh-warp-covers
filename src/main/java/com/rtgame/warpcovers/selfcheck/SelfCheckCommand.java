package com.rtgame.warpcovers.selfcheck;

import java.util.List;
import java.util.Locale;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.interfaces.tileentity.ICoverable;
import gregtech.common.covers.Cover;

/**
 * 仅开发环境注册：{@code /warpcovers_selfcheck setup|verify <场景>}、{@code list}，
 * 以及截图用的 {@code gui <x> <y> <z> <面>}（等同于蹲下右键该面的覆盖板）。
 */
public final class SelfCheckCommand extends CommandBase {

    private final SelfCheckRunner runner;
    private final List<String> scenarioNames;

    public SelfCheckCommand(SelfCheckRunner runner, List<String> scenarioNames) {
        this.runner = runner;
        this.scenarioNames = scenarioNames;
    }

    @Override
    public String getCommandName() {
        return "warpcovers_selfcheck";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/warpcovers_selfcheck <setup|verify> <场景名> | list | gui <x> <y> <z> <up|down|north|south|west|east>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 1 && "list".equals(args[0])) {
            sender.addChatMessage(new ChatComponentText("可用场景: " + String.join(", ", scenarioNames)));
            return;
        }
        if (args.length == 5 && "gui".equals(args[0])) {
            openCoverGui(sender, args);
            return;
        }
        if (args.length != 2 || !("setup".equals(args[0]) || "verify".equals(args[0]))) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        SelfCheckResult result = "setup".equals(args[0]) ? runner.setup(args[1]) : runner.verify(args[1]);
        sender.addChatMessage(
            new ChatComponentText(
                "[WarpCovers 自检] " + args[0]
                    + " "
                    + args[1]
                    + " "
                    + (result.passed() ? "PASS" : "FAIL")
                    + " "
                    + result.detail()));
    }

    private static void openCoverGui(ICommandSender sender, String[] args) {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        int x = parseInt(sender, args[1]);
        int y = parseInt(sender, args[2]);
        int z = parseInt(sender, args[3]);
        ForgeDirection side;
        try {
            side = ForgeDirection.valueOf(args[4].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new WrongUsageException("未知的面: " + args[4]);
        }
        TileEntity tile = player.worldObj.getTileEntity(x, y, z);
        if (!(tile instanceof ICoverable)) {
            throw new CommandException("该位置不是可贴覆盖板的机器");
        }
        Cover cover = ((ICoverable) tile).getCoverAtSide(side);
        if (cover == null || !cover.hasCoverGUI()) {
            throw new CommandException("该面没有带界面的覆盖板");
        }
        cover.onCoverShiftRightClick(player);
    }
}
