import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class TextFormatting {
    // Konversi text message (String) ke dalam enkode UTF-8
    public static byte[] encodeTextToBytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    // Menghitung berapa jumlah bytes yang bisa dimuat ke dalam satu block, dengan plaintext m (256^k - 1) < modulus n
    public static int calculatePlaintextBlockSize(BigInteger n) {
        int plaintextBlockSize = 0; // Value awal 0 bytes yang bisa dimuat ke dalam satu block
        BigInteger limit = BigInteger.ONE; // limit awal = 1 -> 256^0

        while (limit.multiply(BigInteger.valueOf(256)).compareTo(n) <= 0) { // while limit * 256 <= n
            limit = limit.multiply(BigInteger.valueOf(256)); // limit = limit * 256
            plaintextBlockSize++;
        }

        if (plaintextBlockSize == 0) {
            throw new IllegalArgumentException("Value of modulus must be greater than or equal to 256");
        }

        return plaintextBlockSize;
    }


    public static BigInteger[] convertBytesToBlocks(byte[] bytes, int plaintextBlockSize) {
        // Menghitung jumlah total blocks yang ada
        BigInteger[] blocks = new BigInteger[(bytes.length + plaintextBlockSize - 1) / plaintextBlockSize];

        // Menentukan awal dari setiap block
        for (int i = 0; i < blocks.length; i++) {
            int begin = i * plaintextBlockSize;
            // End dari block dihitung menggunakan min dari ukuran block dengan panjang dari bytes yang akan
            // dimasukkan untuk mengantisipasi block terakhir yang kemungkinan memiliki ukuran lebih pendek
            int end = Math.min(begin + plaintextBlockSize, bytes.length);

            // Menggabungkan bytes yang dalam satu block menjadi satu integer positif besar
            // Nilai BigInteger(1, ...) digunakan untuk memastikan integer positif (unsigned) dan bukan signed
            blocks[i] = new BigInteger(1, Arrays.copyOfRange(bytes, begin, end));
        }

        return blocks;
    }

    public static String revertBlocksToText(BigInteger[] blocks, int plaintextBlockSize, int inputLengthAsBytes) {
        if (inputLengthAsBytes < 0 || blocks.length != (inputLengthAsBytes + plaintextBlockSize - 1) / plaintextBlockSize) {
            throw new IllegalArgumentException("Invalid values for reverting text");
        }

        // Mengalokasikan array sesuai dengan panjang teks awal
        byte[] bytes = new byte[inputLengthAsBytes];

        for (int i = 0; i < blocks.length; i++) {
            // Mengantisipasi block terakhir yang kemungkinan memiliki ukuran lebih pendek
            int expectedBlockLength = Math.min(plaintextBlockSize, inputLengthAsBytes - i * plaintextBlockSize);

            // Mengkonversi kembali ke bytes dari BigInteger
            byte[] blockBytes = blocks[i].toByteArray();

            // Menghapus leading zero di awal block yang bukan termasuk dari plaintext awal
            int sourceOffset = blockBytes.length > 1 && blockBytes[0] == 0 ? 1 : 0;
            int sourceLength = blockBytes.length - sourceOffset;

            // Memvalidasi dan mencegah block yang memuat kelebihan bytes
            if (blocks[i].signum() < 0 || sourceLength > expectedBlockLength) {
                throw new IllegalArgumentException("Invalid plaintext block");
            }

            System.arraycopy(blockBytes, sourceOffset, bytes, i * plaintextBlockSize + expectedBlockLength - sourceLength, sourceLength);
        }

        // Dekode dari UTF-8 ke dalam bentuk teks message (String) kembali
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
