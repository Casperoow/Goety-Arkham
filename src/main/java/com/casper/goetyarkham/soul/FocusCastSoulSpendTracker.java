package com.casper.goetyarkham.soul;

import com.casper.goetyarkham.item.HeirloomSoulSpendEffectService;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Marks the narrow Goety focus-spell settlement call stack. Soul mutations
 * outside this context can never trigger the Heirloom effect.
 */
public final class FocusCastSoulSpendTracker {
    private static final ThreadLocal<Deque<Frame>> ACTIVE =
            ThreadLocal.withInitial(ArrayDeque::new);

    private FocusCastSoulSpendTracker() {
    }

    public static void begin(ServerPlayer player) {
        ACTIVE.get().push(new Frame(player));
    }

    /** Records the unified pool service's final changed amount. */
    public static void recordActualSpend(
            ServerPlayer player, int actualSpent) {
        if (actualSpent <= 0) {
            return;
        }
        Deque<Frame> frames = ACTIVE.get();
        if (frames.isEmpty() || frames.peek().player != player) {
            return;
        }
        frames.peek().actualSpent = saturatingAdd(
                frames.peek().actualSpent, actualSpent);
    }

    /** Finishes one focus settlement and applies one total-damage pass. */
    public static int finish(ServerPlayer player) {
        Deque<Frame> frames = ACTIVE.get();
        if (frames.isEmpty()) {
            return 0;
        }
        Frame frame = frames.pop();
        if (frame.player != player) {
            if (frames.isEmpty()) {
                ACTIVE.remove();
            }
            return 0;
        }
        if (!frames.isEmpty()) {
            Frame parent = frames.peek();
            if (parent.player == player) {
                parent.actualSpent = saturatingAdd(
                        parent.actualSpent, frame.actualSpent);
            }
            return frame.actualSpent;
        }

        ACTIVE.remove();
        if (frame.actualSpent > 0) {
            HeirloomSoulSpendEffectService.onFocusSoulSpent(
                    player, frame.actualSpent);
        }
        return frame.actualSpent;
    }

    private static int saturatingAdd(int first, int second) {
        long sum = (long) first + second;
        return (int) Math.min(Integer.MAX_VALUE, sum);
    }

    private static final class Frame {
        private final ServerPlayer player;
        private int actualSpent;

        private Frame(ServerPlayer player) {
            this.player = player;
        }
    }
}
