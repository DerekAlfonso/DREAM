package dream;

import java.awt.Color;
import java.util.List;
import java.util.Random;

/**
 * A run-based endless dungeon.
 *
 * <p>The player enters an infinite sequence of rooms, fighting one enemy per
 * room and choosing whether to continue or visit the shop between rooms.
 * Enemy definitions live in {@link EnemyType#ALL}; upgrades are the list
 * built in the constructor. Both are iterated generically, so adding another
 * enemy or upgrade type does not require changing the menus or combat loop.
 */
final class EndlessMode {

    interface Input {
        String read() throws InterruptedException;

        String readAllowBlank() throws InterruptedException;
    }

    @FunctionalInterface
    private interface UpgradeEffect {
        void apply(DungeonPlayer player);
    }

    private final Input input;
    private final Random random = new Random();
    private final DungeonPlayer player = new DungeonPlayer();
    private final List<Upgrade> upgrades;

    private int room;
    private int roomsCleared;

    /** Set when the player types SKIP, so every loop unwinds to the story. */
    private boolean skipped;

    private EndlessMode(Input input) {
        this.input = input;
        upgrades = List.of(
            new Upgrade("Reinforced Plating", "health",
                "+20 maximum health, and immediately heals 20.",
                15, 7, 6, p -> p.gainMaxHealth(20)),
            new Upgrade("Weapon Tuning", "damage",
                "+3 damage.",
                18, 8, 6, p -> p.gainDamage(3)),
            new Upgrade("Potion Brewing", "healing",
                "+8 healing per potion.",
                12, 6, 6, p -> p.gainPotionStrength(8)),
            new Upgrade("Potion Belt", "slots",
                "+1 potion slot, so you can carry one more.",
                14, 8, 3, p -> p.gainPotionSlots(1))
        );
    }

    /**
     * @return true when the run ended normally, false when the player typed SKIP
     */
    static boolean play(Input input) throws InterruptedException {
        return new EndlessMode(input).run();
    }

    private boolean run() throws InterruptedException {
        showIntro();

        boolean enterNextRoom = true;
        while (player.isAlive()) {
            if (enterNextRoom) {
                room++;
                if (!fightRoom()) {
                    if (skipped) {
                        return false;
                    }
                    // Dying ends the run; getting out alive still counts as leaving.
                    finishRun(player.isAlive());
                    return true;
                }
                enterNextRoom = false;
            }

            String choice = corridorChoice();
            if (skipped) {
                return false;
            }
            if (choice.equals("room")) {
                enterNextRoom = true;
            } else if (choice.equals("shop")) {
                openShop();
                if (skipped) {
                    return false;
                }
            } else {
                finishRun(true);
                return true;
            }
        }
        return true;
    }

    private void showIntro() {
        Terminal.blankLine();
        Terminal.print("ENDLESS MODE", 20, 22, Color.CYAN);
        Terminal.print("The dungeon has no bottom. Every room is worse than the last.",
            20, 15, Color.WHITE);
        Terminal.print("In combat, choose 1/A ATTACK or 2/H HEAL; press ENTER to repeat."
            + " Once you have cleared a room you can LEAVE a fight.", 20, 15, Color.WHITE);
        Terminal.print("Between rooms, choose another room, visit the shop, or LEAVE.",
            20, 15, Color.WHITE);
        Terminal.print("Type SKIP at any prompt to abandon the run and return to the dungeon menu.",
            20, 15, Color.GREEN);
        Terminal.blankLine();
    }

    /** Reads an answer, noticing SKIP wherever the run happens to be. */
    private String ask() throws InterruptedException {
        String answer = input.read();
        if (matches(answer, "skip") && confirmAbandon()) {
            skipped = true;
        }
        return answer;
    }

    /** Like ask(), but an empty Enter reaches the combat repeat logic. */
    private String readCombatAction() throws InterruptedException {
        String answer = input.readAllowBlank();
        if (matches(answer, "skip") && confirmAbandon()) {
            skipped = true;
        }
        return answer;
    }

    /** One-line confirmation before SKIP or LEAVE throws the run away. */
    private boolean confirmAbandon() throws InterruptedException {
        Terminal.print("Abandon this run? type YES to leave, or NO to keep fighting.",
            20, 15, Color.WHITE);
        return matches(input.read(), "yes", "y");
    }

    private boolean fightRoom() throws InterruptedException {
        Enemy enemy = Enemy.spawn(EnemyType.random(random), room, random);

        Terminal.blankLine();
        Terminal.print("ROOM " + room, 20, 18, Color.CYAN);
        Terminal.print("The " + enemy.type.name + " blocks your path.",
            20, 15, Color.WHITE);
        Terminal.print(enemy.type.description, 20, 15, Color.WHITE);
        printEnemyStatus(enemy);
        String lastAction = null;

        while (enemy.isAlive() && player.isAlive()) {
            Terminal.blankLine();
            printCombatStatus(enemy);
            // One room is all the dungeon insists on; after that a fight is optional.
            boolean mayLeave = roomsCleared > 0;
            Terminal.print(mayLeave
                ? "[1] ATTACK   [2] HEAL   [3] LEAVE   (A/H, ENTER repeats)"
                : "[1] ATTACK   [2] HEAL   (A/H, ENTER repeats)",
                15, 15, Color.WHITE);
            String action = readCombatAction();
            if (skipped) {
                return false;
            }
            if (matches(action, "skip")) {
                continue;
            }
            if (action.isEmpty()) {
                if (lastAction == null) {
                    Terminal.print("choose an action once before repeating it.",
                        20, 15, Color.RED);
                    continue;
                }
                action = lastAction;
            }

            if (mayLeave && matches(action, "3", "l", "leave", "flee", "run")) {
                if (!confirmAbandon()) {
                    continue;
                }
                Terminal.print("You back away from the " + enemy.type.name
                    + " and slip out of the room.", 20, 15, Color.WHITE);
                return false;
            }

            if (matches(action, "1", "a", "attack")) {
                lastAction = "attack";
                attack(enemy);
            } else if (matches(action, "2", "h", "heal", "potion")) {
                lastAction = "heal";
                if (!usePotion()) {
                    continue;
                }
            } else {
                Terminal.print(mayLeave
                    ? "please choose 1/A ATTACK, 2/H HEAL or 3 LEAVE."
                    : "please choose 1/A ATTACK or 2/H HEAL.", 20, 15, Color.RED);
                continue;
            }

            if (enemy.isAlive() && player.isAlive()) {
                enemyAttack(enemy);
            }
        }

        if (!player.isAlive()) {
            Terminal.blankLine();
            Terminal.print("You fall in room " + room + ".", 20, 15, Color.RED);
            return false;
        }

        roomsCleared++;
        player.coins += enemy.coins;
        Terminal.blankLine();
        Terminal.print("The " + enemy.type.name + " falls. You collect "
            + enemy.coins + " coins.", 20, 15, Color.GREEN);
        return true;
    }

    private void attack(Enemy enemy) {
        DungeonCombat.attack(player, enemy, random);
    }

    private void enemyAttack(Enemy enemy) {
        DungeonCombat.enemyAttack(player, enemy, random);
    }

    private boolean usePotion() {
        return DungeonCombat.usePotion(player);
    }

    private void printEnemyStatus(Enemy enemy) {
        DungeonCombat.printEnemyStatus(enemy);
    }

    private void printCombatStatus(Enemy enemy) {
        DungeonCombat.printStatus(player, enemy);
    }

    private String corridorChoice() throws InterruptedException {
        Terminal.blankLine();
        Terminal.print("ROOM " + room + " CLEARED", 20, 18, Color.GREEN);
        printRunStatus();
        Terminal.print("[1] Enter the next room", 15, 15, Color.WHITE);
        Terminal.print("[2] Visit the shop", 15, 15, Color.WHITE);
        Terminal.print("[3] Leave the dungeon", 15, 15, Color.WHITE);
        Terminal.print("type SKIP to abandon the run.", 15, 15, Color.GREEN);

        while (true) {
            String choice = ask();
            if (skipped) {
                return "leave";
            }
            if (matches(choice, "1", "next", "room", "new room")) {
                return "room";
            }
            if (matches(choice, "2", "shop", "store")) {
                return "shop";
            }
            if (matches(choice, "3", "leave", "flee")) {
                if (!confirmAbandon()) {
                    continue;
                }
                return "leave";
            }
            Terminal.print("choose 1 for a new room, 2 for the shop, or 3 to leave.",
                20, 15, Color.RED);
        }
    }

    private void printRunStatus() {
        Terminal.print("Rooms cleared: " + roomsCleared + "  |  Coins: "
            + player.coins, 15, 15, Color.WHITE);
        Terminal.print("HP: " + player.health + "/" + player.maxHealth
            + "  |  Damage: " + player.minDamage + "-" + player.maxDamage
            + "  |  Potions: " + player.potions + "/" + player.maxPotions,
            15, 15, Color.WHITE);
    }

    private void openShop() throws InterruptedException {
        Terminal.blankLine();
        Terminal.divider();
        Terminal.print("THE SHOP", 20, 20, Color.CYAN);
        Terminal.print("Upgrades last for this run only.", 15, 15, Color.GREEN);

        while (true) {
            Terminal.blankLine();
            Terminal.print("Coins: " + player.coins + "  |  HP: " + player.health
                + "/" + player.maxHealth, 15, 15, Color.WHITE);

            for (int i = 0; i < upgrades.size(); i++) {
                Upgrade upgrade = upgrades.get(i);
                String price = upgrade.maxed() ? "MAX" : upgrade.cost() + " coins";
                Terminal.print("[" + (i + 1) + "] " + upgrade.name + "  Lv "
                    + upgrade.level + "/" + upgrade.maxLevel + "  (" + price + ")",
                    15, 15, Color.WHITE);
                Terminal.print("    " + upgrade.description, 10, 15, Color.GREEN);
            }

            int potionOption = upgrades.size() + 1;
            String potionPrice = player.potions >= player.maxPotions
                ? "full" : potionCost() + " coins";
            Terminal.print("[" + potionOption + "] Restock one potion  ("
                + potionPrice + ")", 15, 15, Color.WHITE);
            Terminal.print("type BACK to return to the corridor.",
                15, 15, Color.RED);

            String choice = ask();
            if (skipped) {
                return;
            }
            if (matches(choice, "back", "return")) {
                return;
            }

            Upgrade selected = findUpgrade(choice);
            if (selected != null) {
                buyUpgrade(selected);
                continue;
            }

            if (matches(choice, "potion", "restock")
                || choice.equals(Integer.toString(potionOption))) {
                buyPotion();
                continue;
            }

            Terminal.print("please choose an item number, health, damage, healing or potion.",
                20, 15, Color.RED);
        }
    }

    private Upgrade findUpgrade(String input) {
        int index = parseIndex(input);
        if (index >= 1 && index <= upgrades.size()) {
            return upgrades.get(index - 1);
        }
        for (Upgrade upgrade : upgrades) {
            if (upgrade.command.equalsIgnoreCase(input)) {
                return upgrade;
            }
        }
        return null;
    }

    private void buyUpgrade(Upgrade upgrade) {
        if (upgrade.maxed()) {
            Terminal.print(upgrade.name + " is already at maximum level.",
                20, 15, Color.WHITE);
            return;
        }

        int cost = upgrade.cost();
        if (player.coins < cost) {
            Terminal.print("You need " + (cost - player.coins) + " more coins.",
                20, 15, Color.RED);
            return;
        }

        player.coins -= cost;
        upgrade.apply(player);
        Terminal.print("Bought " + upgrade.name + " Lv " + upgrade.level + ".",
            20, 15, Color.GREEN);
    }

    private void buyPotion() {
        if (player.potions >= player.maxPotions) {
            Terminal.print("You cannot carry any more potions.",
                20, 15, Color.WHITE);
            return;
        }

        int cost = potionCost();
        if (player.coins < cost) {
            Terminal.print("You need " + (cost - player.coins) + " more coins.",
                20, 15, Color.RED);
            return;
        }

        player.coins -= cost;
        player.potions++;
        Terminal.print("Bought a potion. (" + player.potions + "/" + player.maxPotions + ")",
            20, 15, Color.GREEN);
    }

    private int potionCost() {
        return 8 + room * 2;
    }

    private void finishRun(boolean left) {
        Terminal.blankLine();
        Terminal.divider();
        Terminal.print("ENDLESS RUN OVER", 20, 20, Color.CYAN);
        if (left) {
            Terminal.print("You climb out of the dungeon while you still can.",
                20, 15, Color.GREEN);
        } else {
            Terminal.print("The dungeon keeps your body. Your run ends here.",
                20, 15, Color.RED);
        }
        Terminal.print("Deepest room: " + room, 15, 15, Color.WHITE);
        Terminal.print("Rooms cleared: " + roomsCleared, 15, 15, Color.WHITE);
        Terminal.print("Coins remaining: " + player.coins, 15, 15, Color.WHITE);
        Terminal.blankLine();
    }

    private static int parseIndex(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static boolean matches(String input, String... choices) {
        if (input == null) {
            return false;
        }
        for (String choice : choices) {
            if (input.equalsIgnoreCase(choice)) {
                return true;
            }
        }
        return false;
    }

    private static final class Upgrade {
        final String name;
        final String command;
        final String description;
        final int baseCost;
        final int costStep;
        final int maxLevel;
        final UpgradeEffect effect;

        int level;

        private Upgrade(String name, String command, String description,
                        int baseCost, int costStep, int maxLevel,
                        UpgradeEffect effect) {
            this.name = name;
            this.command = command;
            this.description = description;
            this.baseCost = baseCost;
            this.costStep = costStep;
            this.maxLevel = maxLevel;
            this.effect = effect;
        }

        int cost() {
            return baseCost + costStep * level;
        }

        boolean maxed() {
            return level >= maxLevel;
        }

        void apply(DungeonPlayer player) {
            effect.apply(player);
            level++;
        }
    }
}
