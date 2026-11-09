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

    @FunctionalInterface
    interface Input {
        String read() throws InterruptedException;
    }

    @FunctionalInterface
    private interface UpgradeEffect {
        void apply(Player player);
    }

    private static final int MAX_POTIONS = 5;
    private static final int STARTING_HEALTH = 100;
    private static final int STARTING_MIN_DAMAGE = 10;
    private static final int STARTING_MAX_DAMAGE = 14;
    private static final int STARTING_POTION_HEALING = 30;
    private static final int STARTING_POTIONS = 3;

    private final Input input;
    private final Random random = new Random();
    private final Player player = new Player();
    private final List<Upgrade> upgrades;

    private int room;
    private int roomsCleared;

    private EndlessMode(Input input) {
        this.input = input;
        upgrades = List.of(
            new Upgrade("Reinforced Plating", "health",
                "+20 maximum health, and immediately heals 20.",
                15, 7, 6, p -> p.gainMaxHealth(20)),
            new Upgrade("Weapon Tuning", "damage",
                "+3 weapon damage.",
                18, 8, 6, p -> p.gainDamage(3)),
            new Upgrade("Potion Brewing", "healing",
                "+8 healing per potion.",
                12, 6, 6, p -> p.gainPotionStrength(8))
        );
    }

    static void play(Input input) throws InterruptedException {
        new EndlessMode(input).run();
    }

    private void run() throws InterruptedException {
        showIntro();

        boolean enterNextRoom = true;
        while (player.isAlive()) {
            if (enterNextRoom) {
                room++;
                if (!fightRoom()) {
                    finishRun(false);
                    return;
                }
                enterNextRoom = false;
            }

            String choice = corridorChoice();
            if (choice.equals("room")) {
                enterNextRoom = true;
            } else if (choice.equals("shop")) {
                openShop();
            } else {
                finishRun(true);
                return;
            }
        }
    }

    private void showIntro() {
        Terminal.blankLine();
        Terminal.print("ENDLESS MODE", 20, 22, Color.CYAN);
        Terminal.print("The dungeon has no bottom. Every room is worse than the last.",
            20, 15, Color.WHITE);
        Terminal.print("In combat, type ATTACK or HEAL. Between rooms, choose another room,"
            + " visit the shop, or LEAVE.", 20, 15, Color.WHITE);
        Terminal.blankLine();
    }

    private boolean fightRoom() throws InterruptedException {
        Enemy enemy = Enemy.spawn(EnemyType.random(random), room, random);

        Terminal.blankLine();
        Terminal.print("ROOM " + room, 20, 18, Color.CYAN);
        Terminal.print("The " + enemy.type.name + " blocks your path.",
            20, 15, Color.WHITE);
        Terminal.print(enemy.type.description, 20, 15, Color.WHITE);
        printEnemyStatus(enemy);

        while (enemy.isAlive() && player.isAlive()) {
            Terminal.blankLine();
            printCombatStatus(enemy);
            Terminal.print("type ATTACK to strike or HEAL to use a potion.",
                15, 15, Color.WHITE);
            String action = input.read();

            if (matches(action, "1", "attack")) {
                attack(enemy);
            } else if (matches(action, "2", "heal", "potion")) {
                if (!usePotion()) {
                    continue;
                }
            } else {
                Terminal.print("please type ATTACK or HEAL.", 20, 15, Color.RED);
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
        if (enemy.blocks(random)) {
            Terminal.print("The " + enemy.type.name + " blocks your attack.",
                20, 15, Color.RED);
            return;
        }
        if (enemy.dodges(random)) {
            Terminal.print("The " + enemy.type.name + " dodges your attack.",
                20, 15, Color.RED);
            return;
        }

        int damage = player.rollDamage(random);
        enemy.health = Math.max(0, enemy.health - damage);
        Terminal.print("You hit the " + enemy.type.name + " for " + damage
            + " damage.", 20, 15, Color.GREEN);

        int recoil = enemy.recoilDamage(damage);
        if (recoil > 0) {
            player.takeDamage(recoil);
            Terminal.print("The " + enemy.type.name
                + " returns " + recoil + " damage to you.", 20, 15, Color.RED);
        }
    }

    private void enemyAttack(Enemy enemy) {
        int damage = enemy.rollDamage(random);
        player.takeDamage(damage);
        Terminal.print("The " + enemy.type.name + " hits you for " + damage
            + " damage.", 20, 15, Color.RED);

        if (enemy.stealsLife(random)) {
            int healed = enemy.heal(enemy.stolenHealth(damage));
            if (healed > 0) {
                Terminal.print("The " + enemy.type.name
                    + " absorbs the wound and recovers " + healed + " HP.",
                    20, 15, Color.CYAN);
            }
        }
    }

    private boolean usePotion() {
        if (player.potions <= 0) {
            Terminal.print("You have no potions left.", 20, 15, Color.RED);
            return false;
        }
        if (player.health >= player.maxHealth) {
            Terminal.print("You are already at full health.", 20, 15, Color.WHITE);
            return false;
        }

        player.potions--;
        int healed = player.heal(player.potionHealing);
        Terminal.print("You drink a potion and recover " + healed + " HP. ("
            + player.potions + " potions left)", 20, 15, Color.GREEN);
        return true;
    }

    private void printEnemyStatus(Enemy enemy) {
        Terminal.print("The " + enemy.type.name + " has " + enemy.health + "/"
            + enemy.maxHealth + " HP and deals " + enemy.minDamage + "-"
            + enemy.maxDamage + " damage.", 15, 15, Color.WHITE);
    }

    private void printCombatStatus(Enemy enemy) {
        Terminal.print("You: " + player.health + "/" + player.maxHealth
            + " HP  |  weapon " + player.minDamage + "-" + player.maxDamage
            + "  |  potions " + player.potions + "/" + MAX_POTIONS,
            15, 15, Color.GREEN);
        Terminal.print("The " + enemy.type.name + ": " + enemy.health + "/"
            + enemy.maxHealth + " HP", 15, 15, Color.RED);
    }

    private String corridorChoice() throws InterruptedException {
        Terminal.blankLine();
        Terminal.print("ROOM " + room + " CLEARED", 20, 18, Color.GREEN);
        printRunStatus();
        Terminal.print("[1] Enter the next room", 15, 15, Color.WHITE);
        Terminal.print("[2] Visit the shop", 15, 15, Color.WHITE);
        Terminal.print("[3] Leave the dungeon", 15, 15, Color.WHITE);

        while (true) {
            String choice = input.read();
            if (matches(choice, "1", "next", "room", "new room")) {
                return "room";
            }
            if (matches(choice, "2", "shop", "store")) {
                return "shop";
            }
            if (matches(choice, "3", "leave", "flee")) {
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
            + "  |  Weapon: " + player.minDamage + "-" + player.maxDamage
            + "  |  Potions: " + player.potions + "/" + MAX_POTIONS,
            15, 15, Color.WHITE);
    }

    private void openShop() throws InterruptedException {
        Terminal.blankLine();
        Terminal.print("THE SHOP", 20, 20, Color.CYAN);
        Terminal.print("Upgrades last for this run only.", 15, 15, Color.WHITE);

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
                Terminal.print("    " + upgrade.description, 10, 15, Color.CYAN);
            }

            int potionOption = upgrades.size() + 1;
            String potionPrice = player.potions >= MAX_POTIONS
                ? "full" : potionCost() + " coins";
            Terminal.print("[" + potionOption + "] Restock one potion  ("
                + potionPrice + ")", 15, 15, Color.WHITE);
            Terminal.print("type BACK to return to the corridor.",
                15, 15, Color.RED);

            String choice = input.read();
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
        if (player.potions >= MAX_POTIONS) {
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
        Terminal.print("Bought a potion. (" + player.potions + "/" + MAX_POTIONS + ")",
            20, 15, Color.GREEN);
    }

    private int potionCost() {
        return 8 + room * 2;
    }

    private void finishRun(boolean left) {
        Terminal.blankLine();
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

        void apply(Player player) {
            effect.apply(player);
            level++;
        }
    }

    private static final class Player {
        int maxHealth = STARTING_HEALTH;
        int health = STARTING_HEALTH;
        int minDamage = STARTING_MIN_DAMAGE;
        int maxDamage = STARTING_MAX_DAMAGE;
        int potionHealing = STARTING_POTION_HEALING;
        int potions = STARTING_POTIONS;
        int coins;

        boolean isAlive() {
            return health > 0;
        }

        int rollDamage(Random random) {
            return minDamage + random.nextInt(maxDamage - minDamage + 1);
        }

        void takeDamage(int damage) {
            health = Math.max(0, health - Math.max(0, damage));
        }

        int heal(int amount) {
            int before = health;
            health = Math.min(maxHealth, health + Math.max(0, amount));
            return health - before;
        }

        void gainMaxHealth(int amount) {
            maxHealth += amount;
            health = Math.min(maxHealth, health + amount);
        }

        void gainDamage(int amount) {
            minDamage += amount;
            maxDamage += amount;
        }

        void gainPotionStrength(int amount) {
            potionHealing += amount;
        }
    }
}
