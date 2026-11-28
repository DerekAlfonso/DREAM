package dream;

import java.util.Random;

/**
 * The character a dungeon run carries: health, damage, potions and coins.
 *
 * <p>Shared by endless and campaign mode. The modes own their own rules; this
 * owns only the numbers, so a balance change reaches both at once.
 */
final class DungeonPlayer {

    static final int STARTING_HEALTH = 100;
    static final int STARTING_MIN_DAMAGE = 10;
    static final int STARTING_MAX_DAMAGE = 14;
    static final int STARTING_POTION_HEALING = 30;
    static final int STARTING_POTIONS = 3;
    static final int STARTING_POTION_SLOTS = 5;

    int maxHealth = STARTING_HEALTH;
    int health = STARTING_HEALTH;
    int minDamage = STARTING_MIN_DAMAGE;
    int maxDamage = STARTING_MAX_DAMAGE;
    int potionHealing = STARTING_POTION_HEALING;
    int potions = STARTING_POTIONS;
    int maxPotions = STARTING_POTION_SLOTS;
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

    void gainPotionSlots(int amount) {
        maxPotions += amount;
    }
}
