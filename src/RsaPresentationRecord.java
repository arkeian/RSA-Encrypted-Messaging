import java.math.BigInteger;
import java.util.Arrays;

public final class RsaPresentationRecord {
    private final boolean keyGeneration;
    private final boolean encryption;
    private final String text;
    private final byte[] bytes;
    private final int blockSize;
    private final int originalByteLength;
    private final BigInteger[] plaintextBlocks;
    private final BigInteger[] ciphertextBlocks;
    private final BigInteger p;
    private final BigInteger q;
    private final BigInteger n;
    private final BigInteger phi;
    private final BigInteger e;
    private final BigInteger d;

    private RsaPresentationRecord(boolean keyGeneration, boolean encryption, String text, byte[] bytes, int blockSize,
                                  int originalByteLength, BigInteger[] plaintextBlocks, BigInteger[] ciphertextBlocks,
                                  BigInteger p, BigInteger q, BigInteger n, BigInteger phi, BigInteger e, BigInteger d) {
        this.keyGeneration = keyGeneration;
        this.encryption = encryption;
        this.text = text;
        this.bytes = bytes == null ? null : bytes.clone();
        this.blockSize = blockSize;
        this.originalByteLength = originalByteLength;
        this.plaintextBlocks = plaintextBlocks == null ? null : plaintextBlocks.clone();
        this.ciphertextBlocks = ciphertextBlocks == null ? null : ciphertextBlocks.clone();
        this.p = p;
        this.q = q;
        this.n = n;
        this.phi = phi;
        this.e = e;
        this.d = d;
    }

    public static RsaPresentationRecord keyGeneration(BigInteger p, BigInteger q, BigInteger n, BigInteger phi, BigInteger e, BigInteger d) {
        return new RsaPresentationRecord(true, false, null, null, 0, 0, null, null, p, q, n, phi, e, d);
    }

    public static RsaPresentationRecord encryption(String text, byte[] bytes, int blockSize, BigInteger[] plaintextBlocks,
                                                   BigInteger[] ciphertextBlocks, BigInteger e, BigInteger n) {
        return new RsaPresentationRecord(false, true, text, bytes, blockSize, bytes.length, plaintextBlocks,
                ciphertextBlocks, null, null, n, null, e, null);
    }

    public static RsaPresentationRecord decryption(String text, byte[] bytes, int blockSize, int originalByteLength,
                                                   BigInteger[] ciphertextBlocks, BigInteger[] plaintextBlocks,
                                                   BigInteger d, BigInteger n) {
        return new RsaPresentationRecord(false, false, text, bytes, blockSize, originalByteLength, plaintextBlocks,
                ciphertextBlocks, null, null, n, null, null, d);
    }

    public int getBlockCount() {
        return plaintextBlocks == null ? 0 : plaintextBlocks.length;
    }

    public String getOverview(boolean revealPrivateValues, boolean showFullIntegers) {
        if (keyGeneration) {
            return keyGenerationOverview(revealPrivateValues, showFullIntegers);
        }

        String operation = encryption ? "RSA encryption" : "RSA decryption";
        StringBuilder result = new StringBuilder(operation)
                .append("\n\nOriginal text:\n").append(text)
                .append("\n\nUTF-8 bytes (length = ").append(bytes.length).append("):\n").append(Arrays.toString(bytes))
                .append("\n\nPlaintext block size:\n").append(blockSize).append(" bytes")
                .append("\n\nOriginal byte length used for recovery:\n").append(originalByteLength)
                .append("\n\nNumber of blocks:\n").append(getBlockCount())
                .append("\n\nModulus n:\n").append(format(n, showFullIntegers));

        if (encryption) {
            result.append("\n\nRecipient public exponent e:\n").append(format(e, showFullIntegers))
                    .append("\n\nResult:\nEach plaintext block is encrypted with c_i = m_i^e mod n.");
        } else {
            result.append("\n\nLocal private exponent d:\n")
                    .append(revealPrivateValues ? format(d, showFullIntegers) : "Hidden. Select 'Reveal private RSA values' to display it.")
                    .append("\n\nRecovered text:\n").append(text)
                    .append("\n\nResult:\nEach ciphertext block is decrypted with m_i = c_i^d mod n.");
        }

        return result.toString();
    }

    public String getBlockDetails(int index, boolean revealPrivateValues, boolean showFullIntegers) {
        if (keyGeneration || index < 0 || index >= getBlockCount()) {
            throw new IllegalArgumentException("invalid presentation block");
        }

        int begin = index * blockSize;
        int end = Math.min(begin + blockSize, originalByteLength);
        StringBuilder result = new StringBuilder("Block ").append(index + 1)
                .append("\n\nUTF-8 byte range:\n[").append(begin).append(", ").append(end).append(")")
                .append("\n\nPlaintext integer m_").append(index).append(":\n")
                .append(format(plaintextBlocks[index], showFullIntegers))
                .append("\n\nCiphertext integer c_").append(index).append(":\n")
                .append(format(ciphertextBlocks[index], showFullIntegers))
                .append("\n\nModulus n:\n").append(format(n, showFullIntegers));

        if (encryption) {
            result.append("\n\nPublic exponent e:\n").append(format(e, showFullIntegers))
                    .append("\n\nFormula:\nc_").append(index).append(" = m_").append(index).append("^e mod n");
        } else {
            result.append("\n\nPrivate exponent d:\n")
                    .append(revealPrivateValues ? format(d, showFullIntegers) : "Hidden. Select 'Reveal private RSA values' to display it.")
                    .append("\n\nFormula:\nm_").append(index).append(" = c_").append(index).append("^d mod n");
        }

        return result.toString();
    }

    private String keyGenerationOverview(boolean revealPrivateValues, boolean showFullIntegers) {
        String hidden = "Hidden. Select 'Reveal private RSA values' to display it.";

        return "RSA key generation"
                + "\n\np:\n" + (revealPrivateValues ? format(p, showFullIntegers) : hidden)
                + "\n\nq:\n" + (revealPrivateValues ? format(q, showFullIntegers) : hidden)
                + "\n\nn = p x q:\n" + format(n, showFullIntegers)
                + "\n\nphi(n) = (p - 1)(q - 1):\n" + (revealPrivateValues ? format(phi, showFullIntegers) : hidden)
                + "\n\nPublic exponent e:\n" + format(e, showFullIntegers)
                + "\n\nPrivate exponent d:\n" + (revealPrivateValues ? format(d, showFullIntegers) : hidden)
                + "\n\nVerification:\ne x d mod phi(n) = " + e.multiply(d).mod(phi)
                + "\np != q: " + !p.equals(q)
                + "\nn >= 256: " + (n.compareTo(BigInteger.valueOf(256)) >= 0);
    }

    private static String format(BigInteger value, boolean showFullInteger) {
        String decimal = value.toString();

        if (showFullInteger || decimal.length() <= 80) {
            return decimal;
        }

        return decimal.substring(0, 48) + "..." + decimal.substring(decimal.length() - 16)
                + " (" + value.bitLength() + " bits)";
    }
}
