package dev.sweety.core.math.algo;

import lombok.experimental.UtilityClass;

@UtilityClass
public class EloUtils {

    public double getChange(final double constant, final double winnerRating, final double loserRating, final double divisionFactor) {
        final double winner = relativeStrength(winnerRating, divisionFactor);
        final double loser = relativeStrength(loserRating, divisionFactor);
        return constant * loser / (winner + loser);
    }

    private double relativeStrength(final double rating, final double divisionFactor) {
        return Math.pow(10.0, rating / divisionFactor);
    }

}
