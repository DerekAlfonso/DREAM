package dream;

import java.awt.Color;
import java.util.Random;

/**
 * The combat rules both dungeon modes share: one player attack, one enemy
 * attack, potions, and the status lines every fight prints.
 *
 * <p>Messages use {@link Terminal#printInstant} so a fight reads one line at a
 * time instead of crawling out a character at a time.
 */
final class DungeonCombat {

    private DungeonCombat() {
    }

    /** The player's turn. Blocking, dodging and recoil all come from the enemy. */
    static void attack(DungeonPlayer player, Enemy enemy, Random random) {
        if (enemy.blocks(random)) {
            Terminal.printInstant("The " + enemy.type.name + " blocks your attack.",
                15, Color.RED);
            return;
        }
        if (enemy.dodges(random)) {
            Terminal.printInstant("The " + enemy.type.name + " dodges your attack.",
                15, Color.RED);
            return;
        }

        int damage = player.rollDamage(random);
        enemy.health = Math.max(0, enemy.health - damage);
        Terminal.printInstant("You hit the " + enemy.type.name + " for " + damage
            + " damage.", 15, Color.GREEN);

        int recoil = enemy.recoilDamage(damage);
        if (recoil > 0) {
            player.takeDamage(recoil);
            Terminal.printInstant("The " + enemy.type.name
                + " returns " + recoil + " damage to you.", 15, Color.RED);
        }
    }

    /** The enemy's turn, including any life it steals back on the hit. */
    static void enemyAttack(DungeonPlayer player, Enemy enemy, Random random) {
        int damage = enemy.rollDamage(random);
        player.takeDamage(damage);
        Terminal.printInstant("The " + enemy.type.name + " hits you for " + damage
            + " damage.", 15, Color.RED);

        if (enemy.stealsLife(random)) {
            int healed = enemy.heal(enemy.stolenHealth(damage));
            if (healed > 0) {
                Terminal.printInstant("The " + enemy.type.name
                    + " absorbs the wound and recovers " + healed + " HP.",
                    15, Color.CYAN);
            }
        }
    }

    /** Drinks one potion. False means nothing happened, so the turn is not spent. */
    static boolean usePotion(DungeonPlayer player) {
        if (player.potions <= 0) {
            Terminal.printInstant("You have no potions left.", 15, Color.RED);
            return false;
        }
        if (player.health >= player.maxHealth) {
            Terminal.printInstant("You are already at full health.", 15, Color.WHITE);
            return false;
        }

        player.potions--;
        int healed = player.heal(player.potionHealing);
        Terminal.printInstant("You drink a potion and recover " + healed + " HP. ("
            + player.potions + " potions left)", 15, Color.GREEN);
        return true;
    }

    static void printEnemyStatus(Enemy enemy) {
        Terminal.printInstant("The " + enemy.type.name + " has " + enemy.health + "/"
            + enemy.maxHealth + " HP and deals " + enemy.minDamage + "-"
            + enemy.maxDamage + " damage.", 15, Color.WHITE);
    }

    /** The two status lines printed at the top of every combat turn. */
    static void printStatus(DungeonPlayer player, Enemy enemy) {
        Terminal.printInstant("You: " + player.health + "/" + player.maxHealth
            + " HP  |  damage " + player.minDamage + "-" + player.maxDamage
            + "  |  potions " + player.potions + "/" + player.maxPotions,
            15, Color.GREEN);
        Terminal.printInstant("The " + enemy.type.name + ": " + enemy.health + "/"
            + enemy.maxHealth + " HP", 15, Color.RED);
    }
}
