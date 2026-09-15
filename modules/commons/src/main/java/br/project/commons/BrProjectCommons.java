package br.project.commons;

/**
 * Marker type for the {@code modules:commons} multi-module skeleton.
 * <p>
 * Production code still lives under {@code ext.mods.commons} inside
 * {@code game-server-core} until circular dependencies are resolved (Phase 2+).
 */
public final class BrProjectCommons {
    public static final String MODULE_ID = "commons";
    public static final String MODULE_PHASE = "1-skeleton";

    private BrProjectCommons() {
    }

    public static String describe() {
        return "brproject/" + MODULE_ID + " (" + MODULE_PHASE + ")";
    }
}
