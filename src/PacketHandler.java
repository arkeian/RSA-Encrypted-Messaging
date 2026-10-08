import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Base64;

public final class PacketHandler {
    public static final int MAX_MESSAGE_BYTES = 4096;
    private static final int MAX_PACKET_CHARACTERS = 100_000;
    private static final SecureRandom random = new SecureRandom();

    private PacketHandler() {
    }

    public static void validatePacket(String packet) {
        if (packet == null || packet.length() > MAX_PACKET_CHARACTERS) {
            throw new IllegalArgumentException("oversized packet");
        }
    }

    public static void validateOutgoingMessage(String message, byte[] bytes) {
        if (message == null || bytes == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }

        if (bytes.length > MAX_MESSAGE_BYTES) {
            throw new IllegalArgumentException("message exceeds " + MAX_MESSAGE_BYTES + " UTF-8 bytes");
        }
    }

    public static String generateMessageIdentifier() {
        byte[] identifier = new byte[18];
        random.nextBytes(identifier);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(identifier);
    }

    public static String createMessagePacket(String messageIdentifier, int byteLength, BigInteger[] blocks) {
        validateMessageIdentifier(messageIdentifier);

        if (blocks == null || byteLength < 1 || byteLength > MAX_MESSAGE_BYTES || blocks.length < 1) {
            throw new IllegalArgumentException("invalid message length");
        }

        StringBuilder packet = new StringBuilder("MSG|")
                .append(messageIdentifier).append('|')
                .append(byteLength).append('|')
                .append(blocks.length).append('|');

        for (int i = 0; i < blocks.length; i++) {
            if (blocks[i] == null || blocks[i].signum() < 0) {
                throw new IllegalArgumentException("invalid ciphertext block");
            }

            if (i > 0) {
                packet.append(',');
            }

            packet.append(blocks[i]);
        }

        return packet.toString();
    }

    public static MessagePacket parseMessagePacket(String packet, BigInteger modulus, int blockSize) {
        validatePacket(packet);

        if (blockSize < 1 || modulus.compareTo(BigInteger.TWO) < 0) {
            throw new IllegalArgumentException("invalid RSA message settings");
        }

        String[] fields = packet.split("\\|", -1);

        if (fields.length != 5 || !fields[0].equals("MSG")) {
            throw new IllegalArgumentException("invalid message packet");
        }

        validateMessageIdentifier(fields[1]);
        int byteLength = Integer.parseInt(fields[2]);
        int blockCount = Integer.parseInt(fields[3]);

        if (byteLength < 1 || byteLength > MAX_MESSAGE_BYTES) {
            throw new IllegalArgumentException("invalid message length");
        }

        int expectedBlocks = (byteLength + blockSize - 1) / blockSize;

        if (blockCount != expectedBlocks) {
            throw new IllegalArgumentException("invalid ciphertext block count");
        }

        String[] encodedBlocks = fields[4].split(",", -1);

        if (encodedBlocks.length != blockCount) {
            throw new IllegalArgumentException("invalid ciphertext block count");
        }

        BigInteger[] blocks = new BigInteger[blockCount];

        for (int i = 0; i < blockCount; i++) {
            blocks[i] = new BigInteger(encodedBlocks[i]);

            if (blocks[i].signum() < 0 || blocks[i].compareTo(modulus) >= 0) {
                throw new IllegalArgumentException("ciphertext block outside 0 <= c < n");
            }
        }

        return new MessagePacket(fields[1], byteLength, blocks);
    }

    public static String createAcknowledgementPacket(String messageIdentifier) {
        validateMessageIdentifier(messageIdentifier);

        return "ACK|" + messageIdentifier;
    }

    public static String parseAcknowledgementPacket(String packet) {
        validatePacket(packet);
        String[] fields = packet.split("\\|", -1);

        if (fields.length != 2 || !fields[0].equals("ACK")) {
            throw new IllegalArgumentException("invalid acknowledgement packet");
        }

        validateMessageIdentifier(fields[1]);

        return fields[1];
    }

    private static void validateMessageIdentifier(String messageIdentifier) {
        if (messageIdentifier == null || !messageIdentifier.matches("[A-Za-z0-9_-]{16,64}")) {
            throw new IllegalArgumentException("invalid message identifier");
        }
    }

    public static final class MessagePacket {
        public final String messageIdentifier;
        public final int byteLength;
        public final BigInteger[] blocks;

        private MessagePacket(String messageIdentifier, int byteLength, BigInteger[] blocks) {
            this.messageIdentifier = messageIdentifier;
            this.byteLength = byteLength;
            this.blocks = blocks;
        }
    }
}
