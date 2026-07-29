package com.casper.goetyarkham.illager_treachery;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.illager_treachery.config.EncounterConfigService;
import com.casper.goetyarkham.illager_treachery.config.IllagerTreacheryConfig;
import com.casper.goetyarkham.illager_treachery.data.IllagerTreacherySavedData;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterExecutionContext;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterRegistry;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterSnapshot;
import com.casper.goetyarkham.illager_treachery.event.IllagerTreacheryEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class IllagerTreacheryManager {
    private static final int DEDUPLICATION_LIMIT = 4_096;
    private static final long DEDUPLICATION_LIFETIME = 144_000L;
    private static final Map<MinecraftServer, IllagerTreacheryManager> INSTANCES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final EncounterRegistry encounters;
    private final RandomProvider randomProvider;
    private final ExpiringDeduplicator deduplicator =
            new ExpiringDeduplicator(DEDUPLICATION_LIMIT, DEDUPLICATION_LIFETIME);
    private final TreacheryEventLock eventLock = new TreacheryEventLock();

    private TriggerAccumulator pending;

    public IllagerTreacheryManager(
            EncounterRegistry encounters, RandomProvider randomProvider) {
        this.encounters = encounters;
        this.randomProvider = randomProvider;
    }

    public static IllagerTreacheryManager get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(
                server,
                ignored -> new IllagerTreacheryManager(
                        EncounterRegistry.INSTANCE,
                        currentServer -> currentServer.overworld().getRandom()::nextLong));
    }

    public static void remove(MinecraftServer server) {
        INSTANCES.remove(server);
    }

    public synchronized IllagerTreacheryState state() {
        return eventLock.state();
    }

    public synchronized SubmitResult submit(
            MinecraftServer server,
            TriggerRequest request,
            String externalInstanceKey) {
        long serverTick = server.getTickCount();
        if (!eventLock.acceptsTrigger()) {
            GoetyArkham.LOGGER.debug(
                    "[illager_treachery] Ignored {} trigger while state={}",
                    request.source(), eventLock.state());
            return SubmitResult.IGNORED_LOCKED;
        }
        if (externalInstanceKey != null
                && !deduplicator.first(externalInstanceKey, serverTick)) {
            GoetyArkham.LOGGER.debug(
                    "[illager_treachery] Ignored duplicate {} instance {}",
                    request.source(), externalInstanceKey);
            return SubmitResult.DUPLICATE;
        }
        if (pending != null && pending.tick() != serverTick) {
            GoetyArkham.LOGGER.warn(
                    "[illager_treachery] Ignored {} trigger because an older tick is still pending",
                    request.source());
            return SubmitResult.IGNORED_PENDING;
        }
        boolean merged = pending != null;
        if (pending == null) {
            pending = new TriggerAccumulator(serverTick);
        }
        pending.merge(request);
        return merged ? SubmitResult.MERGED : SubmitResult.ACCEPTED;
    }

    public void processDailyBoundary(MinecraftServer server) {
        IllagerTreacherySavedData data = IllagerTreacherySavedData.get(server);
        long minecraftDay = Math.floorDiv(server.overworld().getDayTime(), 24_000L);
        if (!data.observeMinecraftDay(minecraftDay)) {
            return;
        }

        TreacherySettings settings = IllagerTreacheryConfig.settings();
        if (!data.effectiveEnabled(settings)) {
            GoetyArkham.LOGGER.debug(
                    "[illager_treachery] Daily boundary observed while disabled");
            return;
        }
        List<ServerPlayer> candidates = collectCandidates(server, settings);
        if (candidates.isEmpty()) {
            GoetyArkham.LOGGER.debug(
                    "[illager_treachery] Day {} is not a valid decision day: no candidates",
                    minecraftDay);
            return;
        }

        int validDays = data.recordValidDecisionDay();
        boolean guaranteed = validDays >= settings.guaranteedValidDays();
        submit(server, TriggerRequest.daily(guaranteed), null);
    }

    public void resolvePending(MinecraftServer server) {
        TriggerAccumulator accumulator;
        synchronized (this) {
            if (pending == null || pending.tick() > server.getTickCount()) {
                return;
            }
            accumulator = pending;
            pending = null;
        }
        resolve(server, accumulator);
    }

    public int candidateCount(MinecraftServer server) {
        return collectCandidates(server, IllagerTreacheryConfig.settings()).size();
    }

    public int drawableEncounterCount(MinecraftServer server) {
        IllagerTreacherySavedData data = IllagerTreacherySavedData.get(server);
        EncounterConfigService config = EncounterConfigService.get(server);
        config.initialize(data);
        return encounters.snapshot(config.snapshotSettings()).size();
    }

    private void resolve(MinecraftServer server, TriggerAccumulator accumulator) {
        if (!eventLock.acceptsTrigger()) {
            GoetyArkham.LOGGER.debug(
                    "[illager_treachery] Dropped merged trigger at resolution because state={}",
                    eventLock.state());
            return;
        }

        TreacherySettings settings = IllagerTreacheryConfig.settings();
        IllagerTreacherySavedData data = IllagerTreacherySavedData.get(server);
        if (!data.effectiveEnabled(settings)) {
            debugAbort("master switch is disabled", accumulator);
            return;
        }
        if (server.getPlayerList().getPlayers().isEmpty()) {
            debugAbort("there are no online players", accumulator);
            return;
        }
        if (server.getWorldData().getDifficulty() == Difficulty.PEACEFUL) {
            debugAbort("difficulty is peaceful", accumulator);
            return;
        }

        List<ServerPlayer> candidates = collectCandidates(server, settings);
        if (candidates.isEmpty()) {
            debugAbort("there are no eligible trigger candidates", accumulator);
            return;
        }
        Map<UUID, ServerPlayer> candidatesById = candidates.stream()
                .collect(java.util.stream.Collectors.toMap(
                        ServerPlayer::getUUID, player -> player));

        EncounterConfigService encounterConfig =
                EncounterConfigService.get(server);
        encounterConfig.initialize(data);
        EncounterSnapshot snapshot =
                encounters.snapshot(encounterConfig.snapshotSettings());
        if (snapshot.isEmpty()) {
            GoetyArkham.LOGGER.error(
                    "[illager_treachery] Cannot start: no enabled encounter has positive weight. "
                            + "Sources={}, valid_decision_days remains {}",
                    accumulator.sources(), data.validDecisionDays());
            return;
        }

        LinkedHashSet<UUID> qualifyingSubmitted = new LinkedHashSet<>();
        accumulator.allPlayers().stream()
                .filter(candidatesById::containsKey)
                .forEach(qualifyingSubmitted::add);

        boolean forced = hasValidForcedSource(accumulator, candidatesById.keySet());
        boolean guaranteed = accumulator.guaranteed()
                && accumulator.sources().contains(TriggerSource.DAILY);
        LinkedHashSet<UUID> probabilitySuccesses = new LinkedHashSet<>();
        long gameTick = server.overworld().getGameTime();
        TriggerDecisionPolicy.Mode decisionMode = TriggerDecisionPolicy.choose(
                forced, guaranteed, data.isCoolingDown(gameTick));

        if (decisionMode == TriggerDecisionPolicy.Mode.SKIP_COOLDOWN) {
            GoetyArkham.LOGGER.debug(
                    "[illager_treachery] Skipped normal probability sources {} during cooldown ({} ticks left)",
                    accumulator.sources(), data.cooldownRemaining(gameTick));
            return;
        }

        if (decisionMode == TriggerDecisionPolicy.Mode.RANDOM) {
            boolean drewRandom = performProbabilityChecks(
                    server, accumulator, settings, candidates, candidatesById,
                    probabilitySuccesses);
            if (drewRandom) {
                data.restartCooldown(gameTick, settings.cooldownTicks());
            }
            if (probabilitySuccesses.isEmpty()) {
                return;
            }
        }

        TreacheryContext context = new TreacheryContext(
                UUID.randomUUID(),
                accumulator.tick(),
                accumulator.sources(),
                accumulator.playersBySource(),
                qualifyingSubmitted,
                probabilitySuccesses,
                guaranteed,
                com.casper.goetyarkham.chaosbag.ChaosBagApi.snapshot(server)
        );
        startGlobalEvent(server, settings, data, snapshot, context);
    }

    private boolean performProbabilityChecks(
            MinecraftServer server,
            TriggerAccumulator accumulator,
            TreacherySettings settings,
            List<ServerPlayer> candidates,
            Map<UUID, ServerPlayer> candidatesById,
            Set<UUID> successes) {
        TreacheryRandom random = randomProvider.forServer(server);
        boolean drewRandom = false;

        if (accumulator.sources().contains(TriggerSource.DAILY)) {
            ProbabilityDecisionEngine.Outcome<ServerPlayer> daily =
                    ProbabilityDecisionEngine.rollDaily(
                            candidates,
                            com.casper.goetyarkham.soul.SoulEnergyPoolService
                                    ::getMaximumSoul,
                            settings,
                            random);
            daily.successes().stream()
                    .map(ServerPlayer::getUUID)
                    .forEach(successes::add);
            drewRandom |= daily.randomDrawn();
        }

        if (accumulator.sources().contains(TriggerSource.RITUAL)) {
            List<ServerPlayer> ritualCandidates =
                    accumulator.players(TriggerSource.RITUAL).stream()
                            .map(candidatesById::get)
                            .filter(java.util.Objects::nonNull)
                            .toList();
            ProbabilityDecisionEngine.Outcome<ServerPlayer> ritual =
                    ProbabilityDecisionEngine.rollDirect(
                            ritualCandidates,
                            com.casper.goetyarkham.soul.SoulEnergyPoolService
                                    ::getMaximumSoul,
                            settings,
                            random);
            ritual.successes().stream()
                    .map(ServerPlayer::getUUID)
                    .forEach(successes::add);
            drewRandom |= ritual.randomDrawn();
        }
        return drewRandom;
    }

    private void startGlobalEvent(
            MinecraftServer server,
            TreacherySettings settings,
            IllagerTreacherySavedData data,
            EncounterSnapshot snapshot,
            TreacheryContext context) {
        if (!eventLock.beginPreparing()) {
            return;
        }

        try {
            safePost(new IllagerTreacheryEvents.GlobalPreparing(context, snapshot));

            List<UUID> onlineAtLock = server.getPlayerList().getPlayers().stream()
                    .map(ServerPlayer::getUUID)
                    .toList();
            List<UUID> participants = onlineAtLock.stream()
                    .map(server.getPlayerList()::getPlayer)
                    .filter(PlayerEligibility::isParticipant)
                    .map(ServerPlayer::getUUID)
                    .toList();

            data.resetValidDecisionDays();
            data.restartCooldown(
                    server.overworld().getGameTime(), settings.cooldownTicks());

            eventLock.beginResolving();

            List<PlayerTreacheryResolution> resolutions = new ArrayList<>();
            for (UUID playerId : onlineAtLock) {
                if (!participants.contains(playerId)) {
                    PlayerTreacheryResolution excluded =
                            new PlayerTreacheryResolution(
                                    playerId,
                                    PlayerTreacheryResult.EXCLUDED,
                                    List.of(),
                                    0);
                    resolutions.add(excluded);
                    safePost(new IllagerTreacheryEvents.PlayerResolved(
                            context, excluded));
                }
            }
            for (UUID playerId : participants) {
                resolutions.add(resolvePlayer(
                        server, settings, snapshot, context, playerId));
            }
            safePost(new IllagerTreacheryEvents.GlobalResolved(
                    context, participants, resolutions));
        } catch (Throwable throwable) {
            GoetyArkham.LOGGER.error(
                    "[illager_treachery] Unhandled failure while resolving event {}",
                    context.eventId(), throwable);
        } finally {
            eventLock.release();
        }
    }

    private PlayerTreacheryResolution resolvePlayer(
            MinecraftServer server,
            TreacherySettings settings,
            EncounterSnapshot snapshot,
            TreacheryContext context,
            UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null || !PlayerEligibility.isParticipant(player)) {
            PlayerTreacheryResolution resolution = new PlayerTreacheryResolution(
                    playerId,
                    PlayerResolutionPolicy.choose(false, false),
                    List.of(),
                    0);
            safePost(new IllagerTreacheryEvents.PlayerResolved(context, resolution));
            return resolution;
        }

        safePost(new IllagerTreacheryEvents.PlayerPreparing(context, player));
        if (!PlayerEligibility.isParticipant(player)) {
            PlayerTreacheryResolution resolution = new PlayerTreacheryResolution(
                    playerId,
                    PlayerResolutionPolicy.choose(false, false),
                    List.of(),
                    0);
            safePost(new IllagerTreacheryEvents.PlayerResolved(context, resolution));
            return resolution;
        }
        IllagerTreacheryEvents.PlayerImmunity immunity =
                new IllagerTreacheryEvents.PlayerImmunity(context, player);
        safePost(immunity);
        if (immunity.isImmune()) {
            PlayerTreacheryResolution resolution = new PlayerTreacheryResolution(
                    playerId,
                    PlayerResolutionPolicy.choose(true, true),
                    List.of(),
                    0);
            safePost(new IllagerTreacheryEvents.PlayerResolved(context, resolution));
            return resolution;
        }

        TreacheryRandom random = randomProvider.forServer(server);
        ExtraDrawLimiter limiter = new ExtraDrawLimiter(settings.maximumExtraDraws());
        long[] queuedDraws = {1L};
        boolean[] warnedAtLimit = {false};
        List<ResourceLocation> drawn = new ArrayList<>();
        Set<ResourceLocation> eventClaims = new LinkedHashSet<>();
        int failures = 0;

        while (queuedDraws[0] > 0L) {
            queuedDraws[0]--;
            EncounterSnapshot.Entry entry = snapshot.draw(random).orElseThrow();
            drawn.add(entry.id());

            EncounterExecutionContext executionContext = new EncounterExecutionContext(
                    context,
                    player,
                    () -> {
                        if (limiter.request()) {
                            queuedDraws[0]++;
                            return true;
                        }
                        if (!warnedAtLimit[0]) {
                            warnedAtLimit[0] = true;
                            GoetyArkham.LOGGER.warn(
                                    "[illager_treachery] Ignored extra draw above limit {} for player {} in event {}",
                                    settings.maximumExtraDraws(),
                                    player.getGameProfile().getName(),
                                    context.eventId());
                        }
                        return false;
                    },
                    eventClaims::add
            );
            try {
                entry.encounter().execute(executionContext);
            } catch (Throwable throwable) {
                failures++;
                GoetyArkham.LOGGER.error(
                        "[illager_treachery] Encounter {} failed for player {} in event {}",
                        entry.id(),
                        player.getGameProfile().getName(),
                        context.eventId(),
                        throwable);
            }
        }

        PlayerTreacheryResolution resolution = new PlayerTreacheryResolution(
                playerId, PlayerTreacheryResult.DRAWN, drawn, failures);
        safePost(new IllagerTreacheryEvents.PlayerResolved(context, resolution));
        return resolution;
    }

    private boolean hasValidForcedSource(
            TriggerAccumulator accumulator, Set<UUID> candidateIds) {
        for (TriggerSource source : accumulator.sources()) {
            if (!source.isForced()) {
                continue;
            }
            if (!source.requiresSubmittedCandidate()) {
                return true;
            }
            if (accumulator.players(source).stream().anyMatch(candidateIds::contains)) {
                return true;
            }
        }
        return false;
    }

    private static List<ServerPlayer> collectCandidates(
            MinecraftServer server, TreacherySettings settings) {
        if (server.getWorldData().getDifficulty() == Difficulty.PEACEFUL) {
            return List.of();
        }
        return server.getPlayerList().getPlayers().stream()
                .filter(player -> PlayerEligibility.isTriggerCandidate(player, settings))
                .toList();
    }

    private static void safePost(net.minecraftforge.eventbus.api.Event event) {
        try {
            MinecraftForge.EVENT_BUS.post(event);
        } catch (Throwable throwable) {
            GoetyArkham.LOGGER.error(
                    "[illager_treachery] Extension event {} threw an exception",
                    event.getClass().getName(), throwable);
        }
    }

    private static void debugAbort(
            String reason, TriggerAccumulator accumulator) {
        GoetyArkham.LOGGER.debug(
                "[illager_treachery] Trigger aborted: {}. Sources={}",
                reason, accumulator.sources());
    }

    public enum SubmitResult {
        ACCEPTED,
        MERGED,
        DUPLICATE,
        IGNORED_LOCKED,
        IGNORED_PENDING
    }

    @FunctionalInterface
    public interface RandomProvider {
        TreacheryRandom forServer(MinecraftServer server);
    }
}
