package com.casper.goetyarkham.command;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.chaosbag.ChaosBagApi;
import com.casper.goetyarkham.chaosbag.ChaosBagLevel;
import com.casper.goetyarkham.chaosbag.ChaosBagMutation;
import com.casper.goetyarkham.chaosbag.ChaosBagSnapshot;
import com.casper.goetyarkham.chaosbag.ChaosBagState;
import com.casper.goetyarkham.chaosbag.ChaosBaseValueSource;
import com.casper.goetyarkham.chaosbag.ChaosCheckModifier;
import com.casper.goetyarkham.chaosbag.ChaosCheckRequest;
import com.casper.goetyarkham.chaosbag.ChaosCheckService;
import com.casper.goetyarkham.chaosbag.ChaosCheckText;
import com.casper.goetyarkham.chaosbag.ChaosGhoulService;
import com.casper.goetyarkham.chaosbag.ChaosToken;
import com.casper.goetyarkham.illager_treachery.TreacheryContext;
import com.casper.goetyarkham.illager_treachery.TriggerSource;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterExecutionContext;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterRegistry;
import com.casper.goetyarkham.illager_treachery.encounter.IllagerTreacheryEncounter;
import com.casper.goetyarkham.illager_treachery.encounter.formal.DreamsOfRlyehEncounter;
import com.casper.goetyarkham.illager_treachery.encounter.formal.TheYellowSignEncounter;
import com.casper.goetyarkham.stats.PlayerStatsService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ChaosBagCommand {
    private static final ResourceLocation TEST_SOURCE =
            new ResourceLocation(GoetyArkham.MOD_ID, "command_test");
    private static final Set<ResourceLocation> FORMAL_ENCOUNTERS = Set.of(
            DreamsOfRlyehEncounter.ID,
            TheYellowSignEncounter.ID);

    private ChaosBagCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var chaosBag = Commands.literal("chaos_bag")
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource())))
                .then(Commands.literal("base")
                        .executes(context -> base(context.getSource())))
                .then(Commands.literal("mutations")
                        .executes(context -> mutations(context.getSource())))
                .then(Commands.literal("effective")
                        .executes(context -> effective(context.getSource())))
                .then(Commands.literal("level")
                        .then(Commands.literal("get")
                                .executes(context -> levelGet(context.getSource())))
                        .then(Commands.literal("set")
                                .then(Commands.argument(
                                                "level",
                                                StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            Arrays.stream(ChaosBagLevel.values())
                                                    .map(ChaosBagLevel::serializedName)
                                                    .forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> levelSet(
                                                context.getSource(),
                                                StringArgumentType.getString(
                                                        context, "level"))))))
                .then(Commands.literal("token")
                        .then(tokenMutation("add", true))
                        .then(tokenMutation("remove", false)))
                .then(Commands.literal("mutation")
                        .then(Commands.literal("clear")
                                .then(Commands.argument(
                                                "source",
                                                ResourceLocationArgument.id())
                                        .executes(context -> clearSource(
                                                context.getSource(),
                                                ResourceLocationArgument.getId(
                                                        context, "source"))))))
                .then(testCommand())
                .then(Commands.literal("encounter")
                        .then(Commands.literal("trigger")
                                .then(Commands.argument(
                                                "id",
                                                ResourceLocationArgument.id())
                                        .suggests((context, builder) -> {
                                            FORMAL_ENCOUNTERS.stream()
                                                    .map(ResourceLocation::toString)
                                                    .forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument(
                                                        "targets",
                                                        EntityArgument.players())
                                                .executes(context ->
                                                        triggerEncounter(
                                                                context.getSource(),
                                                                ResourceLocationArgument
                                                                        .getId(context, "id"),
                                                                EntityArgument
                                                                        .getPlayers(
                                                                                context,
                                                                                "targets")))))));

        dispatcher.register(Commands.literal("goetyarkham")
                .requires(source -> source.hasPermission(2))
                .then(chaosBag));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<
            CommandSourceStack> tokenMutation(String literal, boolean add) {
        return Commands.literal(literal)
                .then(Commands.argument("token", StringArgumentType.word())
                        .suggests(ChaosBagCommand::suggestTokens)
                        .then(Commands.argument(
                                        "count",
                                        IntegerArgumentType.integer(
                                                1,
                                                ChaosBagState.MAX_MUTATION_COUNT))
                                .then(Commands.argument(
                                                "source",
                                                ResourceLocationArgument.id())
                                        .executes(context -> mutate(
                                                context.getSource(),
                                                add,
                                                StringArgumentType.getString(
                                                        context, "token"),
                                                IntegerArgumentType.getInteger(
                                                        context, "count"),
                                                ResourceLocationArgument.getId(
                                                        context, "source"))))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<
            CommandSourceStack> testCommand() {
        var targets = Commands.argument("player", EntityArgument.player())
                .then(Commands.literal("stat")
                        .then(Commands.argument(
                                        "stat",
                                        StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    Arrays.stream(ChaosBaseValueSource.values())
                                            .filter(value ->
                                                    value != ChaosBaseValueSource.FIXED)
                                            .map(ChaosBaseValueSource::serializedName)
                                            .forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .then(targetArgument(false))))
                .then(Commands.literal("fixed")
                        .then(Commands.argument(
                                        "base",
                                        IntegerArgumentType.integer())
                                .then(targetArgument(true))));
        return Commands.literal("test").then(targets);
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<
            CommandSourceStack, Integer> targetArgument(boolean fixed) {
        var target = Commands.argument(
                        "target",
                        IntegerArgumentType.integer())
                .executes(context -> test(
                        context.getSource(),
                        EntityArgument.getPlayer(context, "player"),
                        fixed
                                ? ChaosBaseValueSource.FIXED
                                : parseBaseSource(StringArgumentType.getString(
                                        context, "stat")),
                        fixed
                                ? IntegerArgumentType.getInteger(context, "base")
                                : 0,
                        IntegerArgumentType.getInteger(context, "target"),
                        List.of()));
        target.then(Commands.literal("forced")
                .then(Commands.argument(
                                "tokens",
                                StringArgumentType.greedyString())
                        .executes(context -> test(
                                context.getSource(),
                                EntityArgument.getPlayer(context, "player"),
                                fixed
                                        ? ChaosBaseValueSource.FIXED
                                        : parseBaseSource(
                                                StringArgumentType.getString(
                                                        context, "stat")),
                                fixed
                                        ? IntegerArgumentType.getInteger(
                                                context, "base")
                                        : 0,
                                IntegerArgumentType.getInteger(
                                        context, "target"),
                                parseForced(
                                        context.getSource(),
                                        StringArgumentType.getString(
                                                context, "tokens"))))));
        return target;
    }

    private static java.util.concurrent.CompletableFuture<
            com.mojang.brigadier.suggestion.Suggestions> suggestTokens(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        builder.suggest("ghoul");
        builder.suggest("cultist");
        builder.suggest("tablet");
        builder.suggest("auto_fail");
        builder.suggest("elder_sign");
        builder.suggest("0");
        builder.suggest("-1");
        return builder.buildFuture();
    }

    private static int status(CommandSourceStack source) {
        ChaosBagSnapshot snapshot = ChaosBagApi.snapshot(source.getServer());
        source.sendSuccess(() -> Component.translatable(
                "command.goetyarkham.chaos_bag.status",
                Component.translatable(snapshot.level().translationKey()),
                snapshot.tokens().size(),
                ChaosBagApi.getMutations(source.getServer()).size()), false);
        return 1;
    }

    private static int base(CommandSourceStack source) {
        return sendConfiguration(
                source,
                "command.goetyarkham.chaos_bag.base",
                ChaosBagApi.getBaseConfiguration(source.getServer()));
    }

    private static int effective(CommandSourceStack source) {
        return sendConfiguration(
                source,
                "command.goetyarkham.chaos_bag.effective",
                ChaosBagApi.getEffectiveConfiguration(source.getServer()));
    }

    private static int sendConfiguration(
            CommandSourceStack source,
            String key,
            List<ChaosToken> tokens) {
        Component counts = formatCounts(tokens);
        source.sendSuccess(
                () -> Component.translatable(key, tokens.size(), counts),
                false);
        return tokens.size();
    }

    private static int mutations(CommandSourceStack source) {
        List<ChaosBagMutation> mutations =
                ChaosBagApi.getMutations(source.getServer());
        if (mutations.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(
                    "command.goetyarkham.chaos_bag.mutations.empty"), false);
            return 1;
        }
        for (int index = 0; index < mutations.size(); index++) {
            ChaosBagMutation mutation = mutations.get(index);
            int displayIndex = index + 1;
            source.sendSuccess(() -> Component.translatable(
                    "command.goetyarkham.chaos_bag.mutations.entry",
                    displayIndex,
                    Component.translatable(
                            mutation.operation()
                                    == ChaosBagMutation.Operation.ADD
                                    ? "chaos_bag.mutation.goetyarkham.add"
                                    : "chaos_bag.mutation.goetyarkham.remove"),
                    ChaosCheckText.token(mutation.token()),
                    mutation.count(),
                    mutation.source()), false);
        }
        return mutations.size();
    }

    private static int levelGet(CommandSourceStack source) {
        ChaosBagLevel level = ChaosBagApi.getLevel(source.getServer());
        source.sendSuccess(() -> Component.translatable(
                "command.goetyarkham.chaos_bag.level",
                Component.translatable(level.translationKey())), false);
        return 1;
    }

    private static int levelSet(CommandSourceStack source, String name) {
        ChaosBagLevel level = ChaosBagLevel.fromName(name).orElse(null);
        if (level == null) {
            source.sendFailure(Component.translatable(
                    "command.goetyarkham.chaos_bag.invalid_level", name));
            return 0;
        }
        return sendOperation(
                source,
                ChaosBagApi.setLevel(source.getServer(), level),
                "command.goetyarkham.chaos_bag.level_set",
                Component.translatable(level.translationKey()));
    }

    private static int mutate(
            CommandSourceStack source,
            boolean add,
            String tokenName,
            int count,
            ResourceLocation mutationSource) {
        ChaosToken token = ChaosToken.parse(tokenName).orElse(null);
        if (token == null) {
            source.sendFailure(Component.translatable(
                    "command.goetyarkham.chaos_bag.invalid_token",
                    tokenName));
            return 0;
        }
        ChaosBagState.OperationResult result = add
                ? ChaosBagApi.addTokens(
                        source.getServer(), token, count, mutationSource)
                : ChaosBagApi.removeTokens(
                        source.getServer(), token, count, mutationSource);
        return sendOperation(
                source,
                result,
                add
                        ? "command.goetyarkham.chaos_bag.token_added"
                        : "command.goetyarkham.chaos_bag.token_removed",
                count,
                ChaosCheckText.token(token),
                mutationSource);
    }

    private static int clearSource(
            CommandSourceStack source, ResourceLocation mutationSource) {
        return sendOperation(
                source,
                ChaosBagApi.undoSource(source.getServer(), mutationSource),
                "command.goetyarkham.chaos_bag.source_cleared",
                mutationSource);
    }

    private static int test(
            CommandSourceStack source,
            ServerPlayer player,
            ChaosBaseValueSource baseSource,
            int fixedBase,
            int target,
            List<ChaosToken> forced) {
        if (baseSource == null || forced == null) {
            return 0;
        }
        try {
            int current = baseSource.stat()
                    .map(stat -> PlayerStatsService.getFinalValue(player, stat))
                    .orElse(fixedBase);
            ChaosCheckRequest request = ChaosCheckRequest.builder(
                            player.getUUID(),
                            TEST_SOURCE,
                            baseSource,
                            current,
                            target,
                            ChaosBagApi.snapshot(source.getServer()),
                            player.getRandom()::nextInt)
                    .modifiers(List.<ChaosCheckModifier>of())
                    .environment(ChaosGhoulService.capture(player))
                    .forcedTokens(forced)
                    .build();
            ChaosCheckService.resolveAndApply(player, request);
            source.sendSuccess(() -> Component.translatable(
                    "command.goetyarkham.chaos_bag.test_complete",
                    player.getDisplayName()), true);
            return 1;
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.translatable(
                    "command.goetyarkham.chaos_bag.operation_failed",
                    exception.getMessage()));
            return 0;
        }
    }

    private static int triggerEncounter(
            CommandSourceStack source,
            ResourceLocation id,
            Collection<ServerPlayer> targets) {
        if (!FORMAL_ENCOUNTERS.contains(id)) {
            source.sendFailure(Component.translatable(
                    "command.goetyarkham.chaos_bag.invalid_formal_encounter",
                    id));
            return 0;
        }
        IllagerTreacheryEncounter encounter =
                EncounterRegistry.INSTANCE.get(id).orElse(null);
        if (encounter == null) {
            source.sendFailure(Component.translatable(
                    "command.goetyarkham.chaos_bag.invalid_formal_encounter",
                    id));
            return 0;
        }

        ChaosBagSnapshot bagSnapshot =
                ChaosBagApi.snapshot(source.getServer());
        Set<UUID> playerIds = targets.stream()
                .map(ServerPlayer::getUUID)
                .collect(java.util.stream.Collectors.toCollection(
                        LinkedHashSet::new));
        TreacheryContext treachery = new TreacheryContext(
                UUID.randomUUID(),
                source.getServer().getTickCount(),
                Set.of(TriggerSource.COMMAND),
                Map.of(TriggerSource.COMMAND, playerIds),
                playerIds,
                playerIds,
                true,
                bagSnapshot);
        int completed = 0;
        for (ServerPlayer player : targets) {
            try {
                encounter.execute(new EncounterExecutionContext(
                        treachery,
                        player,
                        () -> false,
                        new LinkedHashSet<ResourceLocation>()::add));
                completed++;
            } catch (Throwable throwable) {
                GoetyArkham.LOGGER.error(
                        "[chaos_bag] Forced formal encounter {} failed for {}",
                        id,
                        player.getGameProfile().getName(),
                        throwable);
            }
        }
        int result = completed;
        source.sendSuccess(() -> Component.translatable(
                "command.goetyarkham.chaos_bag.encounter_complete",
                id,
                result), true);
        return completed;
    }

    private static int sendOperation(
            CommandSourceStack source,
            ChaosBagState.OperationResult result,
            String successKey,
            Object... arguments) {
        if (!result.success()) {
            source.sendFailure(Component.translatable(
                    "command.goetyarkham.chaos_bag.operation_failed",
                    result.message()));
            return 0;
        }
        source.sendSuccess(
                () -> Component.translatable(successKey, arguments),
                true);
        return 1;
    }

    private static ChaosBaseValueSource parseBaseSource(String name) {
        return ChaosBaseValueSource.fromName(name)
                .filter(value -> value != ChaosBaseValueSource.FIXED)
                .orElse(null);
    }

    private static List<ChaosToken> parseForced(
            CommandSourceStack source, String input) {
        List<ChaosToken> result = new ArrayList<>();
        for (String part : input.split("[,\\s]+")) {
            if (part.isBlank()) {
                continue;
            }
            ChaosToken token = ChaosToken.parse(part).orElse(null);
            if (token == null) {
                source.sendFailure(Component.translatable(
                        "command.goetyarkham.chaos_bag.invalid_token",
                        part));
                return null;
            }
            result.add(token);
        }
        if (result.isEmpty()) {
            source.sendFailure(Component.translatable(
                    "command.goetyarkham.chaos_bag.empty_forced_sequence"));
            return null;
        }
        return List.copyOf(result);
    }

    private static Component formatCounts(List<ChaosToken> tokens) {
        Map<ChaosToken, Integer> counts = new LinkedHashMap<>();
        tokens.forEach(token -> counts.merge(token, 1, Integer::sum));
        MutableComponent result = Component.empty();
        int index = 0;
        for (Map.Entry<ChaosToken, Integer> entry : counts.entrySet()) {
            if (index++ > 0) {
                result.append(Component.literal(", "));
            }
            result.append(Component.translatable(
                    "chaos_bag.token_count.goetyarkham",
                    ChaosCheckText.token(entry.getKey()),
                    entry.getValue()));
        }
        return result;
    }
}
