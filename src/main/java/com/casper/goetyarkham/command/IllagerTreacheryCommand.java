package com.casper.goetyarkham.command;

import com.casper.goetyarkham.illager_treachery.IllagerTreacheryManager;
import com.casper.goetyarkham.illager_treachery.IllagerTreacheryState;
import com.casper.goetyarkham.illager_treachery.PlayerEligibility;
import com.casper.goetyarkham.illager_treachery.TriggerRequest;
import com.casper.goetyarkham.illager_treachery.TriggerSource;
import com.casper.goetyarkham.illager_treachery.TreacherySettings;
import com.casper.goetyarkham.illager_treachery.config.EncounterConfigService;
import com.casper.goetyarkham.illager_treachery.config.IllagerTreacheryConfig;
import com.casper.goetyarkham.illager_treachery.data.IllagerTreacherySavedData;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;

public final class IllagerTreacheryCommand {
    private static final int PERMISSION_LEVEL = 2;

    private IllagerTreacheryCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root =
                Commands.literal("goetyarkham")
                        .requires(source -> source.hasPermission(requiredPermissionLevel()));

        root.then(Commands.literal("illager_treachery")
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource())))
                .then(Commands.literal("enable")
                        .executes(context -> setEnabled(context.getSource(), true)))
                .then(Commands.literal("disable")
                        .executes(context -> setEnabled(context.getSource(), false)))
                .then(Commands.literal("reset")
                        .executes(context -> reset(context.getSource())))
                .then(Commands.literal("trigger")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(context -> trigger(
                                        context.getSource(),
                                        EntityArgument.getPlayers(context, "targets")))))
                .then(Commands.literal("encounter")
                        .then(Commands.literal("list")
                                .executes(context -> listEncounters(context.getSource())))
                        .then(Commands.literal("enable")
                                .then(encounterIdArgument()
                                        .executes(context -> setEncounterEnabled(
                                                context.getSource(),
                                                ResourceLocationArgument.getId(
                                                        context, "id"),
                                                true))))
                        .then(Commands.literal("disable")
                                .then(encounterIdArgument()
                                        .executes(context -> setEncounterEnabled(
                                                context.getSource(),
                                                ResourceLocationArgument.getId(
                                                        context, "id"),
                                                false))))
                        .then(Commands.literal("weight")
                                .then(encounterIdArgument()
                                        .then(Commands.argument(
                                                        "weight",
                                                        LongArgumentType.longArg(0L))
                                                .executes(context -> setEncounterWeight(
                                                        context.getSource(),
                                                        ResourceLocationArgument.getId(
                                                                context, "id"),
                                                        LongArgumentType.getLong(
                                                                context, "weight"))))))
                        .then(Commands.literal("reload")
                                .executes(context -> reloadEncounters(
                                        context.getSource())))
                        .then(Commands.literal("reset")
                                .then(encounterIdArgument()
                                        .executes(context -> resetEncounter(
                                                context.getSource(),
                                                ResourceLocationArgument.getId(
                                                        context, "id")))))
                        .then(Commands.literal("sync")
                                .executes(context -> syncEncounters(
                                        context.getSource())))));

        dispatcher.register(root);
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<
            CommandSourceStack, ResourceLocation> encounterIdArgument() {
        return Commands.argument("id", ResourceLocationArgument.id())
                .suggests((context, builder) -> {
                    encounterConfig(context.getSource()).list().stream()
                            .map(entry -> entry.id().toString())
                            .forEach(builder::suggest);
                    return builder.buildFuture();
                });
    }

    private static int status(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        TreacherySettings settings = IllagerTreacheryConfig.settings();
        IllagerTreacherySavedData data = IllagerTreacherySavedData.get(server);
        IllagerTreacheryManager manager = IllagerTreacheryManager.get(server);
        long remaining = data.cooldownRemaining(server.overworld().getGameTime());

        source.sendSuccess(() -> Component.literal(
                "灾厄诡计: enabled=" + data.effectiveEnabled(settings)
                        + ", valid_days=" + data.validDecisionDays()
                        + "/" + settings.guaranteedValidDays()
                        + ", cooldown_remaining=" + remaining
                        + ", state=" + manager.state()
                        + ", candidates=" + manager.candidateCount(server)
                        + ", encounters=" + EncounterRegistry.INSTANCE.size()
                        + ", drawable_encounters="
                        + manager.drawableEncounterCount(server)), false);
        return 1;
    }

    private static int setEnabled(CommandSourceStack source, boolean enabled) {
        IllagerTreacherySavedData.get(source.getServer())
                .setEnabledOverride(enabled);
        source.sendSuccess(() -> Component.literal(
                enabled ? "已启用灾厄诡计。" : "已禁用灾厄诡计。"), true);
        return 1;
    }

    private static int reset(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        IllagerTreacheryManager manager = IllagerTreacheryManager.get(server);
        if (!canReset(manager.state())) {
            source.sendFailure(Component.literal(
                    "灾厄诡计正在结算，不能重置全局判定状态。"));
            return 0;
        }
        TreacherySettings settings = IllagerTreacheryConfig.settings();
        IllagerTreacherySavedData data = IllagerTreacherySavedData.get(server);
        data.resetValidDecisionDays();
        data.restartCooldown(
                server.overworld().getGameTime(), settings.cooldownTicks());
        source.sendSuccess(() -> Component.literal(
                "已重置灾厄诡计有效日进度，并从现在开始重新计算 "
                        + settings.cooldownTicks() + " tick 冷却；遭遇配置未改变。"),
                true);
        return 1;
    }

    private static int trigger(
            CommandSourceStack source,
            Collection<ServerPlayer> targets) {
        MinecraftServer server = source.getServer();
        TreacherySettings settings = IllagerTreacheryConfig.settings();
        IllagerTreacherySavedData data = IllagerTreacherySavedData.get(server);
        if (!data.effectiveEnabled(settings)) {
            source.sendFailure(Component.literal("灾厄诡计总开关已禁用。"));
            return 0;
        }

        List<ServerPlayer> eligible = targets.stream()
                .filter(player -> PlayerEligibility.isTriggerCandidate(player, settings))
                .toList();
        if (eligible.isEmpty()) {
            source.sendFailure(Component.literal(
                    "目标中没有合格的触发候选人：请检查在线状态、游戏模式、"
                            + "维度袭击能力、和平难度和最低灵魂上限 "
                            + settings.minimumSoul() + "。"));
            return 0;
        }

        if (IllagerTreacheryManager.get(server)
                .drawableEncounterCount(server) == 0) {
            source.sendFailure(Component.literal(
                    "无法触发灾厄诡计：当前没有启用且权重大于0的遭遇。"));
            return 0;
        }

        IllagerTreacheryManager.SubmitResult result =
                IllagerTreacheryManager.get(server).submit(
                        server,
                        TriggerRequest.of(
                                TriggerSource.COMMAND,
                                eligible.stream().map(ServerPlayer::getUUID).toList()),
                        null
                );
        if (result == IllagerTreacheryManager.SubmitResult.IGNORED_LOCKED
                || result == IllagerTreacheryManager.SubmitResult.IGNORED_PENDING) {
            source.sendFailure(Component.literal(
                    "灾厄诡计当前正准备或结算，触发请求已被忽略。"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "已提交 COMMAND 强制触发请求；合格目标 "
                        + eligible.size() + " 名。若同tick已有请求，将合并为一次全局事件。"),
                true);
        return eligible.size();
    }

    private static int listEncounters(CommandSourceStack source) {
        List<EncounterConfigService.ListEntry> encounters =
                encounterConfig(source).list();
        if (encounters.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "当前没有注册任何灾厄诡计遭遇。"), false);
            return 1;
        }
        for (EncounterConfigService.ListEntry encounter : encounters) {
            source.sendSuccess(() -> Component.literal(
                    encounter.id()
                            + ": type=" + (encounter.type() == null
                            ? "<unavailable>" : encounter.type())
                            + ", group=" + (encounter.group() == null
                            ? "<none>" : encounter.group())
                            + ", tags=" + encounter.tags()
                            + ", available=" + encounter.available()
                            + ", enabled=" + encounter.enabled()
                            + ", weight=" + encounter.weight()
                            + ", drawable=" + encounter.drawable()
                            + ", default_enabled="
                            + (encounter.available()
                            ? encounter.defaultEnabled() : "<unavailable>")
                            + ", default_weight="
                            + (encounter.available()
                            ? encounter.defaultWeight() : "<unavailable>")),
                    false);
        }
        return encounters.size();
    }

    private static int setEncounterEnabled(
            CommandSourceStack source,
            ResourceLocation id,
            boolean enabled) {
        EncounterConfigService.Operation operation =
                encounterConfig(source).setEnabled(id, enabled);
        if (!operation.success()) {
            source.sendFailure(Component.literal(
                    "无法修改灾厄诡计遭遇 " + id + "：" + operation.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "已" + (enabled ? "启用" : "禁用") + "灾厄诡计遭遇 " + id + "。"),
                true);
        return 1;
    }

    private static int setEncounterWeight(
            CommandSourceStack source,
            ResourceLocation id,
            long weight) {
        if (!isWeightValid(weight)) {
            source.sendFailure(Component.literal("遭遇权重不能为负数。"));
            return 0;
        }
        EncounterConfigService.Operation operation =
                encounterConfig(source).setWeight(id, weight);
        if (!operation.success()) {
            source.sendFailure(Component.literal(
                    "无法修改灾厄诡计遭遇 " + id + "：" + operation.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "已将灾厄诡计遭遇 " + id + " 的权重设为 " + weight + "。"), true);
        return 1;
    }

    private static int reloadEncounters(CommandSourceStack source) {
        IllagerTreacherySavedData savedData =
                IllagerTreacherySavedData.get(source.getServer());
        EncounterConfigService.Operation operation =
                encounterConfig(source).reload(savedData);
        if (!operation.success()) {
            source.sendFailure(Component.literal(
                    "集中遭遇TOML重载失败：" + operation.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "已重新读取集中遭遇TOML；数据包JSON未重新加载。"
                        + "更改从下一轮灾厄诡计开始生效。"), true);
        return 1;
    }

    private static int resetEncounter(
            CommandSourceStack source, ResourceLocation id) {
        EncounterConfigService.Operation operation =
                encounterConfig(source).reset(id);
        if (!operation.success()) {
            source.sendFailure(Component.literal(
                    "无法重置灾厄诡计遭遇 " + id + "：" + operation.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "已将灾厄诡计遭遇 " + id
                        + " 恢复为当前定义的默认开关与权重。"), true);
        return 1;
    }

    private static int syncEncounters(CommandSourceStack source) {
        EncounterConfigService.Operation operation =
                encounterConfig(source).sync();
        if (!operation.success()) {
            source.sendFailure(Component.literal(
                    "集中遭遇TOML同步失败：" + operation.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                operation.changed()
                        ? "已把新发现的遭遇补充进集中TOML；已有设置未被覆盖。"
                        : "集中TOML已包含全部当前发现的遭遇；未改动已有设置。"),
                true);
        return 1;
    }

    private static EncounterConfigService encounterConfig(
            CommandSourceStack source) {
        EncounterConfigService service =
                EncounterConfigService.get(source.getServer());
        service.initialize(IllagerTreacherySavedData.get(source.getServer()));
        return service;
    }

    static boolean canReset(IllagerTreacheryState state) {
        return state != IllagerTreacheryState.RESOLVING;
    }

    static int requiredPermissionLevel() {
        return PERMISSION_LEVEL;
    }

    static boolean isWeightValid(long weight) {
        return weight >= 0L;
    }
}
