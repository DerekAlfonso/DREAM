package dream;

import java.util.List;
import java.util.Map;

/**
 * The places the CYA can save at. The id is the value stored in saveState.txt,
 * so changing one would orphan existing saves; rename through a migration
 * instead of editing the id.
 */
enum CyaLocation {

    CEMETARY("cemetary", "The Cemetary", "cemetery", "graveyard"),
    SHACK_OUTER("shackOuter", "The Outer Decrepit Shack",
        "outer", "outer shack", "outer decrepit shack", "shack", "decrepit shack"),
    SHACK_INNER("shackInner", "The Inner Decrepit Shack",
        "inner", "inner shack", "inner decrepit shack"),
    CELLAR("cellar", "The Cellar", "basement", "room below", "hatch room"),
    HOUSE("house", "The House"),
    UPSTAIRS("upstairs", "The Upstairs", "up", "upstairs hall", "landing", "hall"),
    ROOM_101("room101", "The Bedroom", "101", "room 101", "bedroom"),
    ROOM_102("room102", "The Library", "102", "room 102", "library"),
    ROOM_103("room103", "The Computer Room", "103", "room 103", "computer room");

    /**
     * Every area reachable with GO from each area. The inner shack is
     * deliberately only reachable from the outer shack, and it can only lead
     * back there. The cellar has no GO exits at all: the hatch is the way in
     * and the way out, so no GO route can reach the inner shack from it.
     */
    private static final Map<CyaLocation, List<CyaLocation>> LINKS = Map.of(
        CEMETARY, List.of(SHACK_OUTER, HOUSE),
        SHACK_OUTER, List.of(CEMETARY, SHACK_INNER, HOUSE),
        SHACK_INNER, List.of(SHACK_OUTER),
        CELLAR, List.of(),
        HOUSE, List.of(CEMETARY, SHACK_OUTER, UPSTAIRS),
        // The rooms are reached through their doors, not by GO, so the
        // numbered locks cannot be walked around; GO only leads back out.
        UPSTAIRS, List.of(HOUSE),
        ROOM_101, List.of(UPSTAIRS),
        ROOM_102, List.of(UPSTAIRS),
        ROOM_103, List.of(UPSTAIRS)
    );

    final String id;
    final String displayName;
    private final List<String> aliases;

    CyaLocation(String id, String displayName, String... aliases) {
        this.id = id;
        this.displayName = displayName;
        this.aliases = List.of(aliases);
    }

    /** Returns the matching location, or null for blank or unknown ids. */
    static CyaLocation fromId(String id) {
        if (id == null) {
            return null;
        }
        for (CyaLocation location : values()) {
            if (location.id.equalsIgnoreCase(id.trim())) {
                return location;
            }
        }
        return null;
    }

    /** Matches a player-typed area name against ids, display names and aliases. */
    static CyaLocation fromName(String name) {
        if (name == null) {
            return null;
        }
        String query = stripLeadingThe(name);
        if (query.isEmpty()) {
            return null;
        }
        for (CyaLocation location : values()) {
            if (location.matches(query)) {
                return location;
            }
        }
        return null;
    }

    /** The areas reachable directly from this one. */
    static List<CyaLocation> neighbours(CyaLocation location) {
        return LINKS.getOrDefault(location, List.of());
    }

    private boolean matches(String query) {
        if (id.equalsIgnoreCase(query) || displayName.equalsIgnoreCase(query)) {
            return true;
        }
        for (String alias : aliases) {
            if (alias.equalsIgnoreCase(query)) {
                return true;
            }
        }
        return false;
    }

    private static String stripLeadingThe(String name) {
        String query = name.trim();
        if (query.toLowerCase().startsWith("the ")) {
            query = query.substring(4).trim();
        }
        return query;
    }
}
