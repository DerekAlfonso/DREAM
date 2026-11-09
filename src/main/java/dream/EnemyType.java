package dream;

import java.util.List;
import java.util.Random;

/**
 * One kind of endless-mode enemy.
 *
 * <p>The roster is data-driven: add a new enemy by appending a definition to
 * {@link #ALL}. The shared combat loop reads the fields below, so a new enemy
 * with existing abilities does not need its own branch in the combat code.
 *
 * <p>Health, damage and coin ranges are base values. Every room after the first
 * adds the matching {@code ...PerRoom} value, giving each enemy a predictable
 * linear difficulty curve while keeping its individual rolls variable.
 */
final class EnemyType {

    /** The complete enemy roster. */
    static final List<EnemyType> ALL = List.of(
        builder("Zombie", "Shambling, relentless, and already dead.")
            .health(24, 34, 6)
            .damage(5, 8, 1)
            .coins(5, 9, 1)
            .build(),
        builder("Skeleton", "Brittle bones, but quick enough to slip aside.")
            .health(16, 24, 5)
            .damage(4, 7, 1)
            .coins(6, 10, 1)
            .dodge(0.20)
            .build(),
        builder("Amalgamation", "A knot of stolen bodies that feeds on the damage it deals.")
            .health(34, 46, 8)
            .damage(6, 9, 1)
            .coins(8, 13, 1)
            .lifesteal(0.25, 0.50)
            .build(),
        builder("Arachnid", "Its armoured forelimbs can turn aside a weapon.")
            .health(20, 30, 6)
            .damage(5, 8, 1)
            .coins(7, 11, 1)
            .block(0.20)
            .build(),
        builder("Mysterious Figure", "Every wound you inflict comes back to you.")
            .health(28, 40, 7)
            .damage(7, 10, 1)
            .coins(12, 18, 1)
            .thorns(0.20)
            .build()
    );

    final String name;
    final String description;

    final int minHealth;
    final int maxHealth;
    final int healthPerRoom;

    final int minDamage;
    final int maxDamage;
    final int damagePerRoom;

    final int minCoins;
    final int maxCoins;
    final int coinsPerRoom;

    final double dodgeChance;
    final double blockChance;
    final double lifestealChance;
    final double lifestealFraction;
    final double thornsFraction;

    private EnemyType(Builder builder) {
        name = builder.name;
        description = builder.description;
        minHealth = builder.minHealth;
        maxHealth = builder.maxHealth;
        healthPerRoom = builder.healthPerRoom;
        minDamage = builder.minDamage;
        maxDamage = builder.maxDamage;
        damagePerRoom = builder.damagePerRoom;
        minCoins = builder.minCoins;
        maxCoins = builder.maxCoins;
        coinsPerRoom = builder.coinsPerRoom;
        dodgeChance = builder.dodgeChance;
        blockChance = builder.blockChance;
        lifestealChance = builder.lifestealChance;
        lifestealFraction = builder.lifestealFraction;
        thornsFraction = builder.thornsFraction;
    }

    /** Picks one enemy at random from the roster. */
    static EnemyType random(Random random) {
        return ALL.get(random.nextInt(ALL.size()));
    }

    private static Builder builder(String name, String description) {
        return new Builder(name, description);
    }

    /**
     * Makes adding a type readable without a ten-argument constructor. Chances
     * and fractions default to zero unless the type opts into that behaviour.
     */
    private static final class Builder {
        private final String name;
        private final String description;

        private int minHealth = 1;
        private int maxHealth = 1;
        private int healthPerRoom;

        private int minDamage;
        private int maxDamage;
        private int damagePerRoom;

        private int minCoins;
        private int maxCoins;
        private int coinsPerRoom;

        private double dodgeChance;
        private double blockChance;
        private double lifestealChance;
        private double lifestealFraction;
        private double thornsFraction;

        private Builder(String name, String description) {
            this.name = name;
            this.description = description;
        }

        private Builder health(int minimum, int maximum, int perRoom) {
            minHealth = minimum;
            maxHealth = maximum;
            healthPerRoom = perRoom;
            return this;
        }

        private Builder damage(int minimum, int maximum, int perRoom) {
            minDamage = minimum;
            maxDamage = maximum;
            damagePerRoom = perRoom;
            return this;
        }

        private Builder coins(int minimum, int maximum, int perRoom) {
            minCoins = minimum;
            maxCoins = maximum;
            coinsPerRoom = perRoom;
            return this;
        }

        private Builder dodge(double chance) {
            dodgeChance = chance;
            return this;
        }

        private Builder block(double chance) {
            blockChance = chance;
            return this;
        }

        private Builder lifesteal(double chance, double fraction) {
            lifestealChance = chance;
            lifestealFraction = fraction;
            return this;
        }

        private Builder thorns(double fraction) {
            thornsFraction = fraction;
            return this;
        }

        private EnemyType build() {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Enemy name must not be blank");
            }
            if (description == null || description.trim().isEmpty()) {
                throw new IllegalArgumentException(name + " needs a description");
            }
            if (minHealth < 1 || maxHealth < minHealth || healthPerRoom < 0) {
                throw new IllegalArgumentException(name + " has an invalid health range");
            }
            if (minDamage < 0 || maxDamage < minDamage || damagePerRoom < 0) {
                throw new IllegalArgumentException(name + " has an invalid damage range");
            }
            if (minCoins < 0 || maxCoins < minCoins || coinsPerRoom < 0) {
                throw new IllegalArgumentException(name + " has an invalid coin range");
            }

            checkChance(name, "dodge", dodgeChance);
            checkChance(name, "block", blockChance);
            checkChance(name, "lifesteal", lifestealChance);
            checkFraction(name, "lifesteal", lifestealFraction);
            checkFraction(name, "thorns", thornsFraction);
            if (lifestealChance > 0 && lifestealFraction <= 0) {
                throw new IllegalArgumentException(name + " needs a lifesteal fraction");
            }

            return new EnemyType(this);
        }

        private static void checkChance(String name, String ability, double value) {
            if (value < 0 || value > 1) {
                throw new IllegalArgumentException(
                    name + " " + ability + " chance must be between 0 and 1");
            }
        }

        private static void checkFraction(String name, String ability, double value) {
            if (value < 0 || value > 1) {
                throw new IllegalArgumentException(
                    name + " " + ability + " fraction must be between 0 and 1");
            }
        }
    }
}
