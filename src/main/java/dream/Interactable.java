package dream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * One thing in a scene the player can look at, interact with or take.
 *
 * <p>Objects are declared once in the scene's list, so adding a new one is a
 * single builder entry rather than new command code. An interaction does its
 * work when it runs and returns the line the player reads, so a story flag and
 * the text that depends on it are written in one place.
 */
final class Interactable {

    /** A closing scene: prints its own lines, and may pause while doing it. */
    @FunctionalInterface
    interface Finale {
        void play() throws InterruptedException;
    }

    final String id;
    final String displayName;
    final List<String> names;

    /** Description built when it is read, for text that changes with the story. */
    private final Supplier<String> lookText;

    /** Styled description for an object that needs more than plain text. */
    private final List<TextSpan> lookSpans;

    /** Runs the interaction and returns the line to print. */
    private final Supplier<String> interaction;

    final String takeText;

    /** Styled refusal, for a message that needs more than plain text. */
    private final List<TextSpan> takeSpans;

    final boolean takeable;
    final Runnable onTake;

    /** Where the interaction moves the player; null means the player stays. */
    private final Supplier<CyaLocation> travel;

    /** The object is out of the scene while this holds; null means it never is. */
    private final BooleanSupplier hiddenWhen;

    /** Ids of the things this object can be used on as a tool; empty otherwise. */
    private final List<String> useTargets;

    /** Use of this tool works only while this holds; null means it always does. */
    private final BooleanSupplier useWhile;

    /** True once the player may leave this object behind; null means never. */
    private final BooleanSupplier dropWhen;

    /** The closing scene played in place of the usual line when the run ends here. */
    private final Finale finale;

    /** While this holds, interacting with the object finishes the story. */
    private final BooleanSupplier endsScene;

    private Interactable(Builder builder) {
        this.id = builder.id;
        this.displayName = builder.displayName;
        this.names = List.copyOf(builder.names);
        this.lookText = builder.lookText;
        this.lookSpans = builder.lookSpans == null ? null : List.copyOf(builder.lookSpans);
        this.interaction = builder.interaction;
        this.takeText = builder.takeText;
        this.takeSpans = builder.takeSpans == null ? null : List.copyOf(builder.takeSpans);
        this.takeable = builder.takeable;
        this.onTake = builder.onTake;
        this.travel = builder.travel;
        this.hiddenWhen = builder.hiddenWhen;
        this.useTargets = List.copyOf(builder.useTargets);
        this.useWhile = builder.useWhile;
        this.dropWhen = builder.dropWhen;
        this.finale = builder.finale;
        this.endsScene = builder.endsScene;
    }

    /** True while the object is absent from its scene. */
    boolean isHidden() {
        return hiddenWhen != null && hiddenWhen.getAsBoolean();
    }

    /** True while using this object should finish the run instead of staying. */
    boolean endsScene() {
        return finale != null && endsScene.getAsBoolean();
    }

    /** Plays the closing scene. Only call it when {@link #endsScene()} holds. */
    void playFinale() throws InterruptedException {
        finale.play();
    }

    /**
     * The description to print: the object's styled spans when it has them,
     * otherwise its text with the object's name picked out.
     */
    List<TextSpan> lookSpans() {
        return highlightNames(lookSpans != null
            ? lookSpans
            : List.of(TextSpan.of(lookText.get())));
    }

    /**
     * Marks every name this object answers to inside a line about it, so a
     * description that says "silhouette" reads like the name in the room.
     */
    List<TextSpan> highlightNames(List<TextSpan> spans) {
        return TextSpan.highlighting(spans, Scene.NAME, names.toArray(new String[0]));
    }

    /** The same, for a line that is still plain text. */
    List<TextSpan> highlightNames(String text) {
        return TextSpan.highlighting(text, Scene.NAME, names.toArray(new String[0]));
    }

    /**
     * What the player reads when they try to take this object: its styled spans
     * when it has them, otherwise its text with the object's name picked out.
     */
    List<TextSpan> takeSpans() {
        return highlightNames(takeSpans != null
            ? takeSpans
            : List.of(TextSpan.of(takeText)));
    }

    /** Runs the interaction, then returns the line to print for it. */
    String interact() {
        return interaction.get();
    }

    /** Where this interaction moves the player, or null to stay in the area. */
    CyaLocation travel() {
        return travel == null ? null : travel.get();
    }

    /**
     * True while this object is a tool that can be applied to the thing with
     * the given id: it names that thing, and it has not already been spent.
     */
    boolean canUseOn(String targetId) {
        return useTargets.contains(targetId)
            && (useWhile == null || useWhile.getAsBoolean());
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

    /**
     * True when one of this object's names starts with a typed word, so
     * "rusty" finds "rusty lantern" without the whole name being typed.
     */
    boolean matchesPartially(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        if (matches(needle)) {
            return true;
        }
        return startsWord(id, needle)
            || startsWord(displayName, needle)
            || names.stream().anyMatch(name -> startsWord(name, needle));
    }

    private static boolean startsWord(String phrase, String needle) {
        if (phrase == null) {
            return false;
        }
        for (String word : phrase.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (word.startsWith(needle)) {
                return true;
            }
        }
        return false;
    }

    /** True when the player has finished with this object and may leave it. */
    boolean canDrop() {
        return dropWhen != null && dropWhen.getAsBoolean();
    }

    static Builder builder(String id) {
        return new Builder(id);
    }

    static final class Builder {

        private final String id;
        private final List<String> names = new ArrayList<>();
        private String displayName;
        private Supplier<String> lookText;
        private List<TextSpan> lookSpans;
        private Supplier<String> interaction;
        private String takeText;
        private List<TextSpan> takeSpans;
        private boolean takeable;
        private Runnable onTake;
        private Supplier<CyaLocation> travel;
        private BooleanSupplier hiddenWhen;
        private final List<String> useTargets = new ArrayList<>();
        private BooleanSupplier useWhile;
        private BooleanSupplier dropWhen;
        private Finale finale;
        private BooleanSupplier endsScene;

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
            return look(() -> text);
        }

        /** A description built each time it is read, for text that changes. */
        Builder look(Supplier<String> text) {
            lookText = text;
            lookSpans = null;
            return this;
        }

        /**
         * A styled description, for runs that need their own colour or a
         * glitched passage that stays unreadable.
         */
        Builder look(List<TextSpan> spans) {
            lookSpans = List.copyOf(spans);
            lookText = null;
            return this;
        }

        Builder interact(String text) {
            return interact(text, null);
        }

        Builder interact(String text, Runnable action) {
            return interact(() -> {
                if (action != null) {
                    action.run();
                }
                return text;
            });
        }

        /**
         * An interaction that works out its own line: it runs when the player
         * uses the object, and what it returns is what the player reads. Flags it
         * sets and the text that depends on them stay in one place.
         */
        Builder interact(Supplier<String> interaction) {
            this.interaction = interaction;
            return this;
        }

        /**
         * Makes the interaction a way out: after its line is printed the player
         * moves to the area the supplier returns. A null area keeps them here.
         */
        Builder travelTo(Supplier<CyaLocation> destination) {
            travel = destination;
            return this;
        }

        /**
         * Makes this object a tool: a bare USE applies it to the first of these
         * ids that is in the area, and USE <tool> ON <thing> accepts them too.
         */
        Builder useOn(String... targetIds) {
            useTargets.addAll(Arrays.asList(targetIds));
            return this;
        }

        /** Bounds when the tool can be used: USE only works while this holds. */
        Builder onlyWhile(BooleanSupplier condition) {
            useWhile = condition;
            return this;
        }

        /** Allows the player to leave this object behind once the condition holds. */
        Builder droppableWhen(BooleanSupplier condition) {
            dropWhen = condition;
            return this;
        }

        /**
         * Makes using this object the end of the story while the condition
         * holds: its finale plays instead of the usual line, and the scene
         * stops once it has been read.
         */
        Builder endsSceneWhen(BooleanSupplier condition, Finale finale) {
            endsScene = condition;
            this.finale = finale;
            return this;
        }

        Builder take(String text) {
            return take(text, null);
        }

        Builder take(String text, Runnable action) {
            takeText = text;
            takeSpans = null;
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
            takeSpans = null;
            return this;
        }

        /** The same, for a refusal that carries its own styling. */
        Builder refuseTake(List<TextSpan> spans) {
            takeSpans = List.copyOf(spans);
            takeText = null;
            return this;
        }

        Interactable build() {
            if (displayName == null) {
                displayName = id;
            }
            if (lookText == null && lookSpans == null) {
                lookText = () -> "You look at the " + displayName + ". Nothing special.";
            }
            if (interaction == null) {
                interaction = () -> "Nothing happens.";
            }
            if (takeText == null && takeSpans == null) {
                takeText = "You can't take the " + displayName + ".";
            }
            return new Interactable(this);
        }
    }
}
