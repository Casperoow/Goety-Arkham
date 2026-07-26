package com.casper.goetyarkham.command;

import com.casper.goetyarkham.stats.IPlayerStats;
import com.casper.goetyarkham.stats.PlayerStatsService;
import com.casper.goetyarkham.stats.StatSnapshot;
import com.casper.goetyarkham.stats.StatType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.Optional;

public final class StatsCommand {
    private static final int MIN_VALUE = -1000;
    private static final int MAX_VALUE = 1000;
    private static final DynamicCommandExceptionType UNKNOWN_STAT =
            new DynamicCommandExceptionType(name -> Component.literal("未知属性：" + name));

    private StatsCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("goetyarkham")
                .requires(source -> source.hasPermission(2));

        root.then(Commands.literal("stats")
                .then(Commands.literal("get")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> getAll(
                                        context.getSource(),
                                        EntityArgument.getPlayer(context, "player")))
                                .then(statArgument()
                                        .executes(context -> getOne(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player"),
                                                parseStat(StringArgumentType.getString(context, "stat")))))))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(statArgument()
                                        .then(Commands.argument("value", IntegerArgumentType.integer(MIN_VALUE, MAX_VALUE))
                                                .executes(context -> set(
                                                        context.getSource(),
                                                        EntityArgument.getPlayer(context, "player"),
                                                        parseStat(StringArgumentType.getString(context, "stat")),
                                                        IntegerArgumentType.getInteger(context, "value")))))))
                .then(Commands.literal("add")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(statArgument()
                                        .then(Commands.argument("value", IntegerArgumentType.integer(MIN_VALUE, MAX_VALUE))
                                                .executes(context -> add(
                                                        context.getSource(),
                                                        EntityArgument.getPlayer(context, "player"),
                                                        parseStat(StringArgumentType.getString(context, "stat")),
                                                        IntegerArgumentType.getInteger(context, "value")))))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> reset(
                                        context.getSource(),
                                        EntityArgument.getPlayer(context, "player"))))));

        dispatcher.register(root);
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> statArgument() {
        return Commands.argument("stat", StringArgumentType.word())
                .suggests((context, builder) -> {
                    Arrays.stream(StatType.values())
                            .map(StatType::serializedName)
                            .forEach(builder::suggest);
                    return builder.buildFuture();
                });
    }

    private static StatType parseStat(String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return StatType.fromName(name).orElseThrow(() -> UNKNOWN_STAT.create(name));
    }

    private static int getAll(CommandSourceStack source, ServerPlayer player) {
        Optional<IPlayerStats> capability = PlayerStatsService.get(player);
        if (!isCapabilityAvailable(capability)) {
            return missingCapability(source, player);
        }
        IPlayerStats data = capability.orElse(null);
        source.sendSuccess(() -> Component.literal("玩家 " + player.getGameProfile().getName() + " 的属性："), false);
        for (StatType type : StatType.values()) {
            source.sendSuccess(() -> describe(type, data.get(type)), false);
        }
        return StatType.values().length;
    }

    private static int getOne(CommandSourceStack source, ServerPlayer player, StatType stat) {
        Optional<IPlayerStats> capability = PlayerStatsService.get(player);
        if (!isCapabilityAvailable(capability)) {
            return missingCapability(source, player);
        }
        IPlayerStats data = capability.orElse(null);
        source.sendSuccess(() -> Component.literal(player.getGameProfile().getName() + " - ")
                .append(describe(stat, data.get(stat))), false);
        return 1;
    }

    private static int set(CommandSourceStack source, ServerPlayer player, StatType stat, int value) {
        Optional<IPlayerStats> capability = PlayerStatsService.get(player);
        if (!isCapabilityAvailable(capability)) {
            return missingCapability(source, player);
        }
        StatSnapshot result = PlayerStatsService.setBase(player, stat, value).orElse(null);
        if (result == null) {
            return missingCapability(source, player);
        }
        source.sendSuccess(() -> Component.literal("已将 " + player.getGameProfile().getName() + " 的 "
                + stat.serializedName() + " base 设为 " + result.base()), true);
        return 1;
    }

    private static int add(CommandSourceStack source, ServerPlayer player, StatType stat, int amount) {
        Optional<IPlayerStats> capability = PlayerStatsService.get(player);
        if (!isCapabilityAvailable(capability)) {
            return missingCapability(source, player);
        }
        StatSnapshot result = PlayerStatsService.addBase(player, stat, amount).orElse(null);
        if (result == null) {
            return missingCapability(source, player);
        }
        source.sendSuccess(() -> Component.literal("已将 " + player.getGameProfile().getName() + " 的 "
                + stat.serializedName() + " base 增加 " + amount + "，当前为 " + result.base()), true);
        return 1;
    }

    private static int reset(CommandSourceStack source, ServerPlayer player) {
        Optional<IPlayerStats> capability = PlayerStatsService.get(player);
        if (!isCapabilityAvailable(capability)) {
            return missingCapability(source, player);
        }
        Boolean result = PlayerStatsService.reset(player).orElse(null);
        if (result == null) {
            return missingCapability(source, player);
        }
        source.sendSuccess(() -> Component.literal("已重置 " + player.getGameProfile().getName() + " 的全部属性"), true);
        return 1;
    }

    private static int missingCapability(CommandSourceStack source, ServerPlayer player) {
        source.sendFailure(Component.literal(
                "无法读取 " + player.getGameProfile().getName() + " 的属性数据：玩家属性 Capability 不可用"));
        return 0;
    }

    static boolean isCapabilityAvailable(Optional<IPlayerStats> capability) {
        return capability.isPresent();
    }

    private static Component describe(StatType type, StatSnapshot value) {
        return Component.literal(type.serializedName()
                + ": base=" + value.base()
                + ", equipment=" + value.equipment()
                + ", temporary=" + value.temporary()
                + ", derived=" + value.derived()
                + ", final=" + value.finalValue());
    }
}
