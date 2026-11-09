package dream;

import java.util.Random;

/**
 * A live enemy spawned from an {@link EnemyType}.
 *
 * <p>Its health, damage and coin reward are rolled once from the type's ranges,
 * then increased by the room number. Keeping the scaling here means every new
 * enemy type automatically participates in the endless difficulty curve.
 */
final class Enemy {

    final EnemyType type;
    final int maxHealth;
    final int minDamage;
    final int maxDamage;
    final int coins;

    int health;

    private Enemy(EnemyType type, int room, Random random) {
        this.type = type;

        int roomBonus = Math.max(0, room - 1);
        maxHealth = between(random, type.minHealth, type.maxHealth)
            + roomBonus * type.healthPerRoom;
        health = maxHealth;

        minDamage = type.minDamage + roomBonus * type.damagePerRoom;
        maxDamage = type.maxDamage + roomBonus * type.damagePerRoom;
        coins = between(random, type.minCoins, type.maxCoins)
            + roomBonus * type.coinsPerRoom;
    }

    static Enemy spawn(EnemyType type, int room, Random random) {
        return new Enemy(type, room, random);
    }

    boolean isAlive() {
        return health > 0;
    }

    int rollDamage(Random random) {
        return between(random, minDamage, maxDamage);
    }

    boolean dodges(Random random) {
        return roll(type.dodgeChance, random);
    }

    boolean blocks(Random random) {
        return roll(type.blockChance, random);
    }

    boolean stealsLife(Random random) {
        return roll(type.lifestealChance, random);
    }

    /** The portion of a hit that an Amalgamation can recover. */
    int stolenHealth(int damageDealt) {
        if (type.lifestealFraction <= 0 || damageDealt <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.round(damageDealt * type.lifestealFraction));
    }

    /** Damage returned to the player by a Mysterious Figure. */
    int recoilDamage(int damageDealt) {
        if (type.thornsFraction <= 0 || damageDealt <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.round(damageDealt * type.thornsFraction));
    }

    /** Heals without going over max health and returns the amount restored. */
    int heal(int amount) {
        int before = health;
        health = Math.min(maxHealth, health + Math.max(0, amount));
        return health - before;
    }

    private static int between(Random random, int minimum, int maximum) {
        if (minimum >= maximum) {
            return minimum;
        }
        return minimum + random.nextInt(maximum - minimum + 1);
    }

    private static boolean roll(double chance, Random random) {
        return chance > 0 && random.nextDouble() < chance;
    }
}
