package finance.client;

import finance.tutorial.TutorialStage;
import finance.tutorial.TutorialOptionalGoal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public final class TutorialClientState {
    private static TutorialStage stage;
    private static int optionalMask;
    private static TutorialOptionalGoal trackedOptionalGoal;
    private static boolean visible = true;
    private static long highlightedUntil;

    private TutorialClientState() {}

    public static TutorialStage stage() { return stage; }
    public static int optionalMask() { return optionalMask; }
    public static boolean visible() { return visible; }
    public static boolean highlighted() { return System.currentTimeMillis() < highlightedUntil; }

    public static void update(TutorialStage next, int nextOptionalMask) {
        TutorialStage previous = stage;
        int previousOptionalMask = optionalMask;
        stage = next;
        optionalMask = nextOptionalMask & TutorialOptionalGoal.validMask();
        if (trackedOptionalGoal != null && optionalComplete(trackedOptionalGoal)) {
            trackedOptionalGoal = null;
        }
        if (previous == null) return;

        if (shouldCelebrate(previous, next)) {
            Component completed = Component.translatable(
                    "finance.tutorial.stage." + previous.translationId() + ".title");
            celebrate(next == TutorialStage.COMPLETE ? "screen.finance.tutorial.route_complete"
                    : "screen.finance.tutorial.objective_complete", completed);
        }
        int newlyCompleted = optionalMask & ~previousOptionalMask;
        for (TutorialOptionalGoal goal : TutorialOptionalGoal.values()) {
            if ((newlyCompleted & goal.bit()) != 0) {
                celebrate("screen.finance.tutorial.optional_complete", Component.translatable(
                        "finance.tutorial.optional." + goal.translationId() + ".title"));
            }
        }
    }

    public static void toggleVisible() { visible = !visible; }
    public static boolean optionalComplete(TutorialOptionalGoal goal) {
        return (optionalMask & goal.bit()) != 0;
    }

    public static void trackOptionalGoal(TutorialOptionalGoal goal) {
        if (goal != null && !optionalComplete(goal)) trackedOptionalGoal = goal;
    }

    public static TutorialOptionalGoal trackedOptionalGoal() {
        return trackedOptionalGoal;
    }

    public static TutorialOptionalGoal nextOptionalGoal() {
        if (trackedOptionalGoal != null && !optionalComplete(trackedOptionalGoal)) {
            return trackedOptionalGoal;
        }
        for (TutorialOptionalGoal goal : TutorialOptionalGoal.values()) {
            if (!optionalComplete(goal)) return goal;
        }
        return null;
    }

    public static String objectiveTranslationBase() {
        if (stage == null) return null;
        if (stage != TutorialStage.COMPLETE) return "finance.tutorial.stage." + stage.translationId();
        TutorialOptionalGoal optional = nextOptionalGoal();
        return optional == null ? "finance.tutorial.stage.complete"
                : "finance.tutorial.optional." + optional.translationId();
    }

    public static void clear() {
        stage = null;
        optionalMask = 0;
        trackedOptionalGoal = null;
        visible = true;
        highlightedUntil = 0L;
    }

    static boolean shouldCelebrate(TutorialStage previous, TutorialStage next) {
        return previous != null && next != null && next.ordinal() > previous.ordinal();
    }

    private static void celebrate(String titleKey, Component completed) {
        highlightedUntil = System.currentTimeMillis() + 4_000L;
        Minecraft minecraft = Minecraft.getInstance();
        SystemToast.add(minecraft.getToasts(), SystemToast.SystemToastIds.TUTORIAL_HINT,
                Component.translatable(titleKey), completed);
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.25F, 0.65F));
    }
}
