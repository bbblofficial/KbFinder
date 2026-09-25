package com.example.kbanalyzer.commands;

import com.example.kbanalyzer.KnockbackAnalyzer;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;

import java.util.List;

public class CommandKBCancel extends CommandBase {

    @Override
    public String getCommandName() {
        return "kbcancel";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/kbcancel - Cancel all active tracking, testing, or velocity display";
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        KnockbackAnalyzer mod = KnockbackAnalyzer.getInstance();
        mod.getTrackingManager().cancelAll();
        mod.getTestManager().cancel();
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        return null;
    }
}
