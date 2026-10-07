import java.math.BigInteger;

public class KeyGeneration {
    public static BigInteger[] calculateExtendedEuclidianAlgorithmXGCD(BigInteger a, BigInteger b) {
        if (b.equals(BigInteger.ZERO)) { // if b == 0
            return new BigInteger[]{a, BigInteger.ONE, BigInteger.ZERO}; // {gcd, x, y}
        }

        // GCD -> gcd(a,b) = gcd(b, a mod b)
        BigInteger[] result = calculateExtendedEuclidianAlgorithmXGCD(b, a.remainder(b));
        BigInteger gcd = result[0];

        // Extended GCD -> ax + by = gcd(a,b)
        BigInteger x = result[2];
        BigInteger y = result[1].subtract(a.divide(b).multiply(x)); // result[1] - (a/b) * x;

        return new BigInteger[]{gcd, x, y};
    }

    public static BigInteger calculateDecryptionExponent(BigInteger e, BigInteger phi) {
        if (e.compareTo(BigInteger.ZERO) <= 0 || phi.compareTo(BigInteger.ONE) <= 0) { // e <= 0 || phi <= 1
            throw new IllegalArgumentException("e must be > 0 and phi must be > 1");
        }

        BigInteger[] result = calculateExtendedEuclidianAlgorithmXGCD(e, phi);

        if (!result[0].equals(BigInteger.ONE)) { // result[0] != 1
            throw new ArithmeticException("Value e = " + e + " and φ = " + phi + " are not relative primes. GCD(e, φ) = " + result[0]);
        }

        // Mengubah x menjadi di dalam interval 0 <= d < φ
        return result[1].mod(phi);
    }
}
