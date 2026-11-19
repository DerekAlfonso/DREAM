package dream;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every interactable in the game, indexed by id.
 *
 * <p>The inventory only stores ids, so it uses this to show the item's name in
 * any area, including items picked up somewhere the player has already left.
 */
final class ObjectCatalog {

    private static final Map<String, Interactable> BY_ID = index();

    private ObjectCatalog() { }

    static Interactable byId(String id) {
        return BY_ID.get(id);
    }

    private static Map<String, Interactable> index() {
        Map<String, Interactable> byId = new LinkedHashMap<>();
        addAll(byId, Cemetery.objects());
        addAll(byId, ShackOuter.objects());
        addAll(byId, ShackInner.objects());
        addAll(byId, House.objects());
        return byId;
    }

    private static void addAll(Map<String, Interactable> byId, List<Interactable> objects) {
        for (Interactable object : objects) {
            byId.put(object.id, object);
        }
    }
}
