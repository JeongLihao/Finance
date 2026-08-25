package finance.client;

import finance.tutorial.TutorialStage;

public final class TutorialClientState {
    private static TutorialStage stage;
    private static boolean visible = true;

    private TutorialClientState() {}

    public static TutorialStage stage() { return stage; }
    public static boolean visible() { return visible; }
    public static void update(TutorialStage next) { stage = next; }
    public static void toggleVisible() { visible = !visible; }
    public static void clear() { stage = null; visible = true; }
}
