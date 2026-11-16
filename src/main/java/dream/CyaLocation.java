package dream;

/**
 * The places the CYA can save at. The id is the value stored in saveState.txt,
 * so changing one would orphan existing saves; rename through a migration
 * instead of editing the id.
 */
enum CyaLocation {

    CEMETARY("cemetary"),
    SHACK_OUTER("shackOuter"),
    SHACK_INNER("shackInner"),
    HOUSE("house");

    final String id;

    CyaLocation(String id) {
        this.id = id;
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
}
