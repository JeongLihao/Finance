package finance.gameplay.company.capital;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bounded registry of capital projects. All write entry points live in
 * {@link CapitalProjectService}; this class only enforces capacity and
 * identity rules.
 */
public final class CapitalProjectManager {

    public static final int MAX_PROJECTS = 1024;
    public static final int MAX_ACTIVE_PER_COMPANY = 2;

    private static final Map<UUID, WorldCapitalProject> PROJECTS = new LinkedHashMap<>();

    private CapitalProjectManager() {
    }

    public static Map<UUID, WorldCapitalProject> projects() {
        return Collections.unmodifiableMap(PROJECTS);
    }

    public static WorldCapitalProject get(UUID projectId) {
        return projectId == null ? null : PROJECTS.get(projectId);
    }

    public static List<WorldCapitalProject> forCompany(UUID companyId) {
        if (companyId == null) return List.of();
        List<WorldCapitalProject> rows = new ArrayList<>();
        for (WorldCapitalProject project : PROJECTS.values()) {
            if (companyId.equals(project.companyId())) rows.add(project);
        }
        return rows;
    }

    public static int activeCountForCompany(UUID companyId) {
        int count = 0;
        for (WorldCapitalProject project : PROJECTS.values()) {
            if (companyId.equals(project.companyId()) && !project.status().terminal()) count++;
        }
        return count;
    }

    public static boolean hasActiveForTarget(UUID targetId) {
        if (targetId == null) return false;
        for (WorldCapitalProject project : PROJECTS.values()) {
            if (targetId.equals(project.targetId()) && !project.status().terminal()) return true;
        }
        return false;
    }

    public static boolean canRegister(WorldCapitalProject project) {
        if (project == null || PROJECTS.containsKey(project.projectId())) return false;
        if (PROJECTS.size() >= MAX_PROJECTS
                && PROJECTS.values().stream().noneMatch(CapitalProjectManager::prunableHistory)) return false;
        if (activeCountForCompany(project.companyId()) >= MAX_ACTIVE_PER_COMPANY) return false;
        return !hasActiveForTarget(project.targetId());
    }

    public static synchronized boolean register(WorldCapitalProject project) {
        if (!canRegister(project)) return false;
        if (PROJECTS.size() >= MAX_PROJECTS && !pruneOldestTerminal()) return false;
        PROJECTS.put(project.projectId(), project);
        return true;
    }

    private static boolean pruneOldestTerminal() {
        var iterator = PROJECTS.entrySet().iterator();
        while (iterator.hasNext()) {
            WorldCapitalProject existing = iterator.next().getValue();
            if (!prunableHistory(existing)) continue;
            iterator.remove();
            return true;
        }
        return false;
    }

    private static boolean prunableHistory(WorldCapitalProject project) {
        return project != null && project.status().terminal() && project.fundedAmount() == 0;
    }

    /** Serializer entry point: accepts restored records without capacity side effects. */
    public static synchronized boolean restore(WorldCapitalProject project) {
        if (project == null || PROJECTS.containsKey(project.projectId())
                || PROJECTS.size() >= MAX_PROJECTS) return false;
        PROJECTS.put(project.projectId(), project);
        return true;
    }

    public static void clearDirect() {
        PROJECTS.clear();
    }
}
