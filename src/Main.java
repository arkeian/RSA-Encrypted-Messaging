import java.math.BigInteger;

public class Main {
    public static void main(String[] args) {
        int primeBitLength = 1024; // Menetapkan ukuran bit p dan q sebesar 1024-bit, dengan 2^1023 <= p,q < 2^1024, sehingga n ≈ 2048-bit (p x q)
        int primalityRounds = 25; // Jumlah round Miller-Rabin untuk setiap kandidat prime agar dapat dinyatakan kemungkinan (probably) sebagai prime dan bukan komposit (persyaratan semua round harus lolos)
        BigInteger e = BigInteger.valueOf(65537); // 65537 = 10000000000000001 dalam representasi bit dengan hanya memiliki 2 bit bernilai 1, sehingga proses modular eksponen bisa dilakukan dengan efisien dengan metode square and multiply.

        BigInteger p;
        BigInteger q;
        BigInteger n;
        BigInteger phi;

        // Mendapatkan nilai-nilai RSA, dengan ketentuan:
        // GCD(e, φ) == 1 (Relatively prime)
        // n >= 256, di mana untuk memenuhi 0 <= m < n, satu plaintext block merepresentasikan satu byte, yang nilainya bisa berupa 0, 1, 2, 3, ..., 254, 255
        do {
            // Mendapatkan angka probably prime P dan Q, dengan P != Q
            p = PQGeneration.generatePrime(primeBitLength, primalityRounds);

            do {
                q = PQGeneration.generatePrime(primeBitLength, primalityRounds);

            } while (p.equals(q));

            // n = p x q
            n = p.multiply(q);

            // φ(n) = (p - 1)(q - 1)
            phi = p.subtract(BigInteger.ONE).multiply(q.subtract(BigInteger.ONE));

        } while (!KeyGeneration.calculateExtendedEuclidianAlgorithmXGCD(e, phi)[0].equals(BigInteger.ONE) || n.compareTo(BigInteger.valueOf(256)) < 0);

        // Mendapatkan kunci dekripsi d
        BigInteger d = KeyGeneration.calculateDecryptionExponent(e, phi);

        // Memastikan e.d mod(φ) == 1, p != q, dan n >= 256
        if (!e.multiply(d).mod(phi).equals(BigInteger.ONE) || p.equals(q) || n.compareTo(BigInteger.valueOf(256)) < 0) {
            throw new IllegalStateException("the generated RSA key-values failed validation checks");
        }
    }
}
