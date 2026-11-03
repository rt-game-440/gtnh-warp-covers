package com.rtgame.warpcovers.selfcheck;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;

/** 仅开发环境注册：{@code /warpcovers_selfcheck setup|verify <场景>} 或 {@code list}。 */
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
        return "/warpcovers_selfcheck <setup|verify> <场景名> | list";
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
}
