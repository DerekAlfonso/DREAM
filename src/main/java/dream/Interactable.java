package dream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * One thing in a scene the player can look at, interact with or take.
 *
 * <p>Objects are declared once in the scene's list, so adding a new one is a
 * single builder entry rather than new command code. Optional callbacks run
 * when the player interacts with or takes the object, which is how story flags
 * are set.
 */
final class Interactable {

    final String id;
    final String displayName;
    final List<String> names;
    final String lookText;
    final String interactText;
    final String takeText;
    final boolean takeable;
    final Runnable onInteract;
    final Runnable onTake;

    /** The object is out of the scene while this holds; null means it never is. */
    private final BooleanSupplier hiddenWhen;

    private Interactable(Builder builder) {
        this.id = builder.id;
        this.displayName = builder.displayName;
        this.names = List.copyOf(builder.names);
        this.lookText = builder.lookText;
        this.interactText = builder.interactText;
        this.takeText = builder.takeText;
        this.takeable = builder.takeable;
        this.onInteract = builder.onInteract;
        this.onTake = builder.onTake;
        this.hiddenWhen = builder.hiddenWhen;
    }

    /** True while the object is absent from its scene. */
    boolean isHidden() {
        return hiddenWhen != null && hiddenWhen.getAsBoolean();
    }

    boolean matches(String query) {
        if (id.equalsIgnoreCase(query) || displayName.equalsIgnoreCase(query)) {
            return true;
        }
        for (String name : names) {
            if (name.equalsIgnoreCase(query)) {
                return true;
            }
        }
        return false;
    }

    static Builder builder(String id) {
        return new Builder(id);
    }

    static final class Builder {

        private final String id;
        private final List<String> names = new ArrayList<>();
        private String displayName;
        private String lookText;
        private String interactText;
        private String takeText;
        private boolean takeable;
        private Runnable onInteract;
        private Runnable onTake;
        private BooleanSupplier hiddenWhen;

        private Builder(String id) {
            this.id = id;
        }

        /**
         * Hides the object while the condition holds, so it stops being listed,
         * examined, interacted with or taken. For things that leave the scene or
         * only turn up after a story beat.
         */
        Builder hiddenWhen(BooleanSupplier condition) {
            hiddenWhen = condition;
            return this;
        }

        /** The first alias is the name shown to the player. */
        Builder names(String... aliases) {
            names.addAll(Arrays.asList(aliases));
            if (displayName == null && aliases.length > 0) {
                displayName = aliases[0];
            }
            return this;
        }

        Builder look(String text) {
            lookText = text;
            return this;
        }

        Builder interact(String text) {
            return interact(text, null);
        }

        Builder interact(String text, Runnable action) {
            interactText = text;
            onInteract = action;
            return this;
        }

        Builder take(String text) {
            return take(text, null);
        }

        Builder take(String text, Runnable action) {
            takeText = text;
            onTake = action;
            takeable = true;
            return this;
        }

        /**
         * Sets what the player is told when they try to take this object,
         * without making it takeable. Use this instead of {@link #take(String)}
         * for scenery the player can never pick up.
         */
        Builder refuseTake(String text) {
            takeText = text;
            return this;
        }

        Interactable build() {
            if (displayName == null) {
                displayName = id;
            }
            if (lookText == null) {
                lookText = "You look at the " + displayName + ". Nothing special.";
            }
            if (interactText == null) {
                interactText = "Nothing happens.";
            }
            if (takeText == null) {
                takeText = "You can't take the " + displayName + ".";
            }
            return new Interactable(this);
        }
    }
}
