import java.math.BigInteger;

public class RSAEncryption {
    public static BigInteger encryptBlock(BigInteger m, BigInteger e, BigInteger n) {
        if (m.compareTo(n) >= 0 || m.compareTo(BigInteger.ZERO) < 0 || e.compareTo(BigInteger.ZERO) < 0 || n.compareTo(BigInteger.ONE) <= 0) { // if m < 0 or e < 0 or n <= 1
            throw new IllegalArgumentException("Plaintext block (m) must be 0 <= m < n, exponent (e) must be >= 0, and modulus (n) must be > 1");
        }

        BigInteger result = BigInteger.valueOf(1); // result = 1
        m = m.remainder(n); // Reduksi m menjadi m mod n (Rumus m^e mod n = (m mod n)^e mod n)

        while (e.compareTo(BigInteger.ZERO) > 0) { // e > 0
            if (e.remainder(BigInteger.valueOf(2)).equals(BigInteger.ONE)) { // if e % 2 == 1
                result = result.multiply(m).remainder(n);
            }

            m = m.multiply(m).remainder(n);
            e = e.divide(BigInteger.valueOf(2));
        }

        return result;
    }
}
