package dev.sweety.math.algo;

public final class EloUtils {

    public static double getChange(final double constant, final double winnerRating, final double loserRating, final double divisionFactor) {
        final double winner = relativeStrength(winnerRating, divisionFactor);
        final double loser = relativeStrength(loserRating, divisionFactor);
        return constant * loser / (winner + loser);
    }

    private static double relativeStrength(final double rating, final double divisionFactor) {
        return Math.pow(10.0, rating / divisionFactor);
    }

    private EloUtils() {}

}
