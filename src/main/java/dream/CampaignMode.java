package dream;

import java.awt.Color;
import java.util.List;
import java.util.Random;

/**
 * A finite, hand-built dungeon: five depths in a fixed order, ending with the
 * Warden.
 *
 * <p>Where endless mode is a run against a rising curve, the campaign is a
 * descent with a bottom. There is no shop and no coin purse; every depth taken
 * makes the player permanently stronger, and each landing offers one rest
 * before the next floor. Leaving, dying or typing SKIP ends the run early and
 * hands control back to the dungeon menu in {@link Game}.
 *
 * <p>The floors live in {@link #DEPTHS}, so a longer campaign is one more entry
 * rather than a change to the loop.
 */
final class CampaignMode {

    @FunctionalInterface
    interface Input {
        String read() throws InterruptedException;
    }

    /** One floor of the keep. */
    private static final class Depth {
        final String name;
        final String[] roomText;
        final EnemyType enemy;

        /** Spawn scaling, roughly the room number the enemy would see in endless. */
        final int power;
        final boolean boss;

        private Depth(String name, EnemyType enemy, int power, boolean boss,
                      String... roomText) {
            this.name = name;
            this.enemy = enemy;
            this.power = power;
            this.boss = boss;
            this.roomText = roomText;
        }
    }

    private static final List<Depth> DEPTHS = List.of(
        new Depth("THE COLLAPSED GATE", EnemyType.ZOMBIE, 1, false,
            "The keep's gate has fallen inward, and nobody ever cleared it.",
            "Something underneath the rubble hears you and stands up."),
        new Depth("THE FLOODED HALL", EnemyType.ARACHNID, 3, false,
            "Black water reaches your knees. The walls sweat cold.",
            "Legs unfold from the ceiling above you, one joint at a time."),
        new Depth("THE OSSUARY", EnemyType.SKELETON, 5, false,
            "Bones are stacked to the vault in neat, patient rows.",
            "One row steps forward and picks up a rusted blade."),
        new Depth("THE CHARNEL VAULT", EnemyType.AMALGAMATION, 7, false,
            "The air is thick enough to lean on, and the floor is warm.",
            "A shape made of several people drags itself upright."),
        new Depth("THE WARDEN'S CHAMBER", EnemyType.WARDEN, 8, true,
            "The last door opens onto a room with no dust in it at all.",
            "The Warden has been keeping this place for a very long time.",
            "It stands, and the whole keep stands with it.")
    );

    private static final int REST_HEAL = 35;
    private static final int DEPTH_HEALTH = 15;
    private static final int DEPTH_DAMAGE = 2;

    private final Input input;
    private final Random random = new Random();
    private final DungeonPlayer player = new DungeonPlayer();

    /** Set when the player types SKIP or asks to leave, so every loop unwinds. */
    private boolean left;

    private int depthReached;

    private CampaignMode(Input input) {
        this.input = input;
    }

    static void play(Input input) throws InterruptedException {
        new CampaignMode(input).run();
    }

    private void run() throws InterruptedException {
        showIntro();

        for (int i = 0; i < DEPTHS.size(); i++) {
            Depth depth = DEPTHS.get(i);
            depthReached = i + 1;

            showDepth(depth);
            if (!fight(depth)) {
                finish(false, left
                    ? "You pull back before the depth is cleared."
                    : "You fall on depth " + depthReached + ". The keep keeps you.");
                return;
            }

            // Every floor taken makes the next one survivable.
            player.gainMaxHealth(DEPTH_HEALTH);
            player.gainDamage(DEPTH_DAMAGE);

            boolean last = i == DEPTHS.size() - 1;
            if (last) {
                finish(true, "The Warden falls, and the keep finally exhales.");
                return;
            }

            if (!landing()) {
                finish(false, "You climb back out with the keep still below you.");
                return;
            }
        }
    }

    private void showIntro() {
        Terminal.blankLine();
        Terminal.print("CAMPAIGN MODE", 20, 22, Color.CYAN);
        Terminal.print("Five depths stand between you and the Warden at the bottom of the keep.",
            20, 15, Color.WHITE);
        Terminal.print("Each depth is one fight. Clear it and the keep makes you stronger.",
            20, 15, Color.WHITE);
        Terminal.print("In combat, type ATTACK or HEAL. At a landing you can rest once.",
            20, 15, Color.WHITE);
        Terminal.print("Type SKIP at any prompt to abandon the run and return to the dungeon menu.",
            20, 15, Color.GREEN);
        Terminal.blankLine();
    }

    private void showDepth(Depth depth) {
        Terminal.blankLine();
        Terminal.divider();
        Terminal.print("DEPTH " + depthReached + " OF " + DEPTHS.size(),
            20, 18, Color.CYAN);
        Terminal.print(depth.name, 20, 18, depth.boss ? Color.RED : Color.CYAN);
        for (String line : depth.roomText) {
            Terminal.print(line, 20, 15, Color.WHITE);
        }
    }

    /** Runs one depth's encounter. False means the player died or left. */
    private boolean fight(Depth depth) throws InterruptedException {
        Enemy enemy = Enemy.spawn(depth.enemy, depth.power, random);

        Terminal.print("The " + enemy.type.name + " blocks your path.",
            20, 15, depth.boss ? Color.RED : Color.WHITE);
        Terminal.print(enemy.type.description, 20, 15, Color.WHITE);
        DungeonCombat.printEnemyStatus(enemy);

        while (enemy.isAlive() && player.isAlive()) {
            Terminal.blankLine();
            DungeonCombat.printStatus(player, enemy);
            Terminal.print("type ATTACK to strike or HEAL to use a potion.",
                15, 15, Color.WHITE);

            String action = ask();
            if (left) {
                return false;
            }

            if (matches(action, "1", "attack")) {
                DungeonCombat.attack(player, enemy, random);
            } else if (matches(action, "2", "heal", "potion")) {
                if (!DungeonCombat.usePotion(player)) {
                    continue;
                }
            } else {
                Terminal.print("please type ATTACK or HEAL.", 20, 15, Color.RED);
                continue;
            }

            if (enemy.isAlive() && player.isAlive()) {
                DungeonCombat.enemyAttack(player, enemy, random);
            }
        }

        if (!player.isAlive()) {
            Terminal.blankLine();
            Terminal.print("You fall on depth " + depthReached + ".",
                20, 15, Color.RED);
            return false;
        }

        Terminal.blankLine();
        Terminal.print("The " + enemy.type.name + " falls.", 20, 15, Color.GREEN);
        if (depth.boss) {
            Terminal.print("Nothing else moves in the keep.", 20, 15, Color.CYAN);
        }
        return true;
    }

    /** The choice between depths. False means the player left the keep. */
    private boolean landing() throws InterruptedException {
        boolean rested = false;

        while (true) {
            Terminal.blankLine();
            Terminal.print("A LANDING", 20, 18, Color.CYAN);
            Terminal.print("HP: " + player.health + "/" + player.maxHealth
                + "  |  Damage: " + player.minDamage + "-" + player.maxDamage
                + "  |  Potions: " + player.potions + "/" + player.maxPotions,
                15, 15, Color.WHITE);
            Terminal.print("[1] Descend to the next depth", 15, 15, Color.WHITE);
            Terminal.print(rested
                ? "[2] Rest here (already rested)"
                : "[2] Rest here (recover " + REST_HEAL + " HP and refill one potion)",
                15, 15, Color.WHITE);
            Terminal.print("[3] Leave the keep", 15, 15, Color.WHITE);

            String choice = ask();
            if (left) {
                return false;
            }
            if (matches(choice, "1", "descend", "down", "next")) {
                return true;
            }
            if (matches(choice, "2", "rest")) {
                if (rested) {
                    Terminal.print("You have already rested on this landing.",
                        20, 15, Color.WHITE);
                    continue;
                }
                rested = true;
                int healed = player.heal(REST_HEAL);
                player.potions = Math.min(player.maxPotions, player.potions + 1);
                Terminal.print(healed > 0
                    ? "You sit with your back to the wall and recover " + healed + " HP."
                    : "You are already whole. You sit a while anyway.",
                    20, 15, Color.GREEN);
                Terminal.print("You refill one potion.",
                    20, 15, Color.GREEN);
                continue;
            }
            if (matches(choice, "3", "leave", "out", "exit")) {
                left = true;
                return false;
            }
            Terminal.print("please choose 1 (descend), 2 (rest) or 3 (leave).",
                20, 15, Color.RED);
        }
    }

    private void finish(boolean cleared, String closing) {
        Terminal.blankLine();
        Terminal.divider();
        Terminal.print(cleared ? "CAMPAIGN COMPLETE" : "CAMPAIGN OVER",
            20, 20, cleared ? Color.GREEN : Color.RED);
        Terminal.print(closing, 20, 15, cleared ? Color.GREEN : Color.WHITE);
        Terminal.print("Depths reached: " + depthReached + "/" + DEPTHS.size(),
            15, 15, Color.WHITE);
        Terminal.print(cleared
            ? "Every floor is behind you. Endless Mode is still waiting in the dungeon menu."
            : "Your run is over, but the dungeon menu is still open.",
            15, 15, Color.CYAN);
        Terminal.blankLine();
    }

    /** Reads an answer, noticing SKIP wherever the run happens to be. */
    private String ask() throws InterruptedException {
        String answer = input.read();
        if (matches(answer, "skip")) {
            left = true;
        }
        return answer;
    }

    private static boolean matches(String input, String... choices) {
        for (String choice : choices) {
            if (input.equalsIgnoreCase(choice)) {
                return true;
            }
        }
        return false;
    }
}
