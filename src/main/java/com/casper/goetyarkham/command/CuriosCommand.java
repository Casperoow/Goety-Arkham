package com.casper.goetyarkham.command;

import com.casper.goetyarkham.curios.CurioSlotReport;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class CuriosCommand {
    private CuriosCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("goetyarkham")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("curios")
                        .then(Commands.literal("slots")
                                .executes(context -> listSlots(
                                        context.getSource())))));
    }

    private static int listSlots(CommandSourceStack source) {
        var slots = CurioSlotReport.playerSlots(source.getLevel());
        for (var slot : slots) {
            source.sendSuccess(() -> CurioSlotReport.commandLine(slot), false);
        }
        return slots.size();
    }
}
