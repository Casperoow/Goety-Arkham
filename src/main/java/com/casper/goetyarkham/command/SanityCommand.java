package com.casper.goetyarkham.command;

import com.casper.goetyarkham.sanity.IPlayerSanity;
import com.casper.goetyarkham.sanity.SanityChangeCause;
import com.casper.goetyarkham.sanity.SanityMath;
import com.casper.goetyarkham.sanity.SanityService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Locale;

public final class SanityCommand {
    private SanityCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var sanity = Commands.literal("sanity")
                .then(Commands.literal("status")
                        .executes(context -> status(
                                context.getSource(),
                                context.getSource().getPlayerOrException()))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(context -> status(
                                        context.getSource(),
                                        EntityArgument.getPlayer(context, "target")))))
                .then(mutation("set", Mutation.SET))
                .then(mutation("add", Mutation.ADD))
                .then(mutation("damage", Mutation.DAMAGE))
                .then(mutation("permanent_damage", Mutation.PERMANENT_DAMAGE))
                .then(mutation("restore_max", Mutation.RESTORE_MAX));

        dispatcher.register(Commands.literal("goetyarkham")
                .requires(source -> source.hasPermission(2))
                .then(sanity));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<
            CommandSourceStack> mutation(String literal, Mutation mutation) {
        IntegerArgumentType amountType = mutation == Mutation.SET
                ? IntegerArgumentType.integer()
                : IntegerArgumentType.integer(0);
        String argumentName = mutation == Mutation.SET ? "value" : "amount";
        return Commands.literal(literal)
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument(argumentName, amountType)
                                .executes(context -> mutate(
                                        context.getSource(),
                                        EntityArgument.getPlayers(context, "targets"),
                                        IntegerArgumentType.getInteger(
                                                context, argumentName),
                                        mutation))));
    }

    private static int status(CommandSourceStack source, ServerPlayer player) {
        IPlayerSanity data = SanityService.get(player).orElse(null);
        if (data == null) {
            source.sendFailure(Component.translatable(
                    "command.goetyarkham.sanity.missing", player.getDisplayName()));
            return 0;
        }
        int maximum = SanityService.getMaximumSanity(player);
        int ticks = SanityMath.ticksUntilSoulDrain(
                data.isCollapseActive(), data.getCollapseTickCounter());
        Component tickText = ticks < 0
                ? Component.translatable("command.goetyarkham.sanity.inactive")
                : Component.literal(Integer.toString(ticks));
        source.sendSuccess(() -> Component.translatable(
                "command.goetyarkham.sanity.status",
                player.getDisplayName(),
                data.getCurrentSanity(),
                maximum,
                format(SanityService.getMaximumAttributeBaseValue(player)),
                format(SanityService.getMaximumAttributeValue(player)),
                data.getPermanentMaxLoss(),
                Component.translatable(data.isCollapseActive()
                        ? "command.goetyarkham.sanity.yes"
                        : "command.goetyarkham.sanity.no"),
                tickText), false);
        return 1;
    }

    private static int mutate(
            CommandSourceStack source,
            Collection<ServerPlayer> targets,
            int value,
            Mutation mutation) {
        for (ServerPlayer player : targets) {
            int actual = mutation.apply(player, value);
            source.sendSuccess(() -> Component.translatable(
                    mutation.translationKey,
                    player.getDisplayName(),
                    value,
                    actual,
                    SanityService.getCurrentSanity(player),
                    SanityService.getMaximumSanity(player),
                    SanityService.getPermanentMaxLoss(player)), true);
        }
        return targets.size();
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private enum Mutation {
        SET("command.goetyarkham.sanity.set") {
            @Override
            int apply(ServerPlayer player, int value) {
                return SanityService.setSanity(
                        player, value, SanityChangeCause.COMMAND);
            }
        },
        ADD("command.goetyarkham.sanity.add") {
            @Override
            int apply(ServerPlayer player, int value) {
                return SanityService.addSanity(
                        player, value, SanityChangeCause.COMMAND);
            }
        },
        DAMAGE("command.goetyarkham.sanity.damage") {
            @Override
            int apply(ServerPlayer player, int value) {
                return SanityService.damageSanity(
                        player, value, SanityChangeCause.COMMAND);
            }
        },
        PERMANENT_DAMAGE("command.goetyarkham.sanity.permanent_damage") {
            @Override
            int apply(ServerPlayer player, int value) {
                return SanityService.addPermanentMaxLoss(
                        player, value, SanityChangeCause.COMMAND);
            }
        },
        RESTORE_MAX("command.goetyarkham.sanity.restore_max") {
            @Override
            int apply(ServerPlayer player, int value) {
                return SanityService.restorePermanentMaxLoss(
                        player, value, SanityChangeCause.COMMAND);
            }
        };

        private final String translationKey;

        Mutation(String translationKey) {
            this.translationKey = translationKey;
        }

        abstract int apply(ServerPlayer player, int value);
    }
}
