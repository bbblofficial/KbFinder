package com.example.kbanalyzer.commands;

import com.example.kbanalyzer.KnockbackAnalyzer;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;

import java.util.List;

/**
 * /kbtester - Fully automatic knockback extraction.
 * The mod handles everything after this command.
 */
public class CommandKBTester extends CommandBase {

    @Override
    public String getCommandName() {
        return "kbtester";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/kbtester - Automatically extract server knockback settings";
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        KnockbackAnalyzer.getInstance().getTestManager().startTest(0);
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        return null;
    }
}