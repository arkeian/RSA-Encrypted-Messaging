import java.math.BigInteger;
import java.security.SecureRandom;

public class PQGeneration {
    private static final SecureRandom random = new SecureRandom();

    public static BigInteger generateUniformRandomInteger(BigInteger upperExclusive, SecureRandom random) {
        // Memastikan range interval menjadi 0 <= x < upper Exclusive
        if (upperExclusive.compareTo(BigInteger.ZERO) <= 0) { // if upperExclusive <= 0
            throw new IllegalArgumentException("Value of upper bound must be > 0");
        }

        int bits = upperExclusive.bitLength();

        // Mengulang sampai Integer yang di generate berada di range interval
        while (true) {
            BigInteger value = new BigInteger(bits, random);

            // Memastikan range interval untuk value tetap 0 <= value < upperBound
            if (value.compareTo(upperExclusive) < 0) { // if value < upperBound
                return value;
            }
        }
    }

    private static boolean millerRabinRound(BigInteger d, int r, BigInteger n) {
        BigInteger base = generateUniformRandomInteger(n.subtract(BigInteger.valueOf(3)), random).add(BigInteger.TWO);

        // x = base^d mod n
        BigInteger x = ModularArithmetic.modularExponentiation(base, d, n);

        if (x.equals(BigInteger.ONE) || x.equals(n.subtract(BigInteger.ONE))) {
            return true;
        }

        // Melakukan repeated squaring sebanyak r - 1 kali
        for (int i = 1; i < r; i++) {
            x = x.multiply(x).mod(n);

            if (x.equals(n.subtract(BigInteger.ONE))) {
                return true;
            }
            if (x.equals(BigInteger.ONE)) {
                return false;
            }
        }

        // Return composite
        return false;
    }

    public static boolean primalityTest(BigInteger candidateRandomInteger, int rounds) {
        if (rounds < 1) {
            throw new IllegalArgumentException("Rounds must be more than or equal to 1");
        }

        // Jika kandidat bernilai kurang dari 2, maka bukan prima
        if (candidateRandomInteger.compareTo(BigInteger.TWO) < 0) { // if candidate < 2
            return false;
        }

        // Jika kandidat bernilai 2 atau 3, maka prima
        if (candidateRandomInteger.equals(BigInteger.TWO) || candidateRandomInteger.equals(BigInteger.valueOf(3))) {
            return true;
        }

        // Jika kandidat genap selain 2, maka bukan prima
        if (candidateRandomInteger.mod(BigInteger.TWO).equals(BigInteger.ZERO)) {
            return false;
        }

        // Mencari d dan r sehingga:
        // d * 2^r = n - 1 -> di mana d ganjil
        BigInteger d = candidateRandomInteger.subtract(BigInteger.ONE);
        int r = 0;

        while (d.mod(BigInteger.TWO).equals(BigInteger.ZERO)) { // while d % 2 == 0
            d = d.divide(BigInteger.TWO);
            r++;
        }

        // Melakukan Primality test sebanyak rounds
        for (int i = 0; i < rounds; i++) {
            if (!millerRabinRound(d, r, candidateRandomInteger)) {
                return false;
            }
        }

        // Tidak ada round yang membuktikan kandidat composite
        return true;
    }

    public static BigInteger generatePrime(int bitLength, int rounds) {
        if (bitLength < 2) {
            throw new IllegalArgumentException("Value of bit length must be more than or equal to 2");
        }
        if (rounds < 1) {
            throw new IllegalArgumentException("Value of rounds must be more than or equal to 1");
        }

        while (true) {
            // Setbit(bitLength - 1) memaksa bit paling kiri (1....) menjadi 1 agar integer berukuran bitLength-bit
            // untuk memenuhi 2^(bitLength - 1) <= kandidat < 2^bitLength
            // Setbit(0) memaksa bit pertama / paling kanan (....1) menjadi 1 agar kandidat dipastikan bernilai ganjil
            BigInteger candidate = new BigInteger(bitLength, random).setBit(bitLength - 1).setBit(0);

            if (primalityTest(candidate, rounds)) {
                return candidate;
            }
        }
    }
}
