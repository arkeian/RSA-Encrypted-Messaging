import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Main implements ConnectionAdapter.Listener {
    static BigInteger p;
    static BigInteger q;
    static BigInteger n;
    static BigInteger phi;
    static BigInteger d;
    static final BigInteger e = BigInteger.valueOf(65537); // 65537 = 10000000000000001 dalam representasi bit dengan hanya memiliki 2 bit bernilai 1, sehingga proses modular eksponen bisa dilakukan dengan efisien dengan metode square and multiply.

    private static final int PRIME_BIT_LENGTH = 1024; // Menetapkan ukuran bit p dan q sebesar 1024-bit, dengan 2^1023 <= p,q < 2^1024, sehingga n ≈ 2048-bit (p x q)
    private static final int PRIMALITY_ROUNDS = 25; // Jumlah round Miller-Rabin untuk setiap kandidat prime agar dapat dinyatakan kemungkinan (probably) sebagai prime dan bukan komposit (persyaratan semua round harus lolos)
    private static final SecureRandom ROOM_RANDOM = new SecureRandom();

    private final ChatWindow window = new ChatWindow();
    private final Map<String, Peer> peers = new ConcurrentHashMap<>();
    private final Map<String, String> pendingDrafts = new ConcurrentHashMap<>();
    private final String applicationUrl;
    private final String requestedRoom;

    private Main(String applicationUrl, String requestedRoom) {
        this.applicationUrl = applicationUrl;
        this.requestedRoom = requestedRoom;
    }

    public static void main(String[] args) {
        String applicationUrl = args.length > 0 ? args[0] : "";
        String requestedRoom = args.length > 1 ? args[1] : "";
        SwingUtilities.invokeLater(() -> new Main(applicationUrl, requestedRoom).start());
    }

    private void start() {
        ConnectionAdapter.setListener(this);
        window.onCreateRoom(this::createRoom);
        window.onJoinRoom(this::joinRoom);
        window.onLeaveRoom(this::leaveRoom);
        window.onConfirmFingerprints(this::confirmFingerprints);
        window.onSend(this::sendMessage);
        window.onClose(ConnectionAdapter::leaveRoom);
        window.show();
        ConnectionAdapter.applicationLoaded();
        generateKeys();
    }

    private void generateKeys() {
        window.setStatus("Generating keys...");

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                // Mendapatkan nilai-nilai RSA, dengan ketentuan:
                // GCD(e, φ) == 1 (Relatively prime)
                // n >= 256, di mana untuk memenuhi 0 <= m < n, satu plaintext block merepresentasikan satu byte, yang nilainya bisa berupa 0, 1, 2, 3, ..., 254, 255
                do {
                    // Mendapatkan angka probably prime P dan Q, dengan P != Q
                    p = PQGeneration.generatePrime(PRIME_BIT_LENGTH, PRIMALITY_ROUNDS);

                    do {
                        q = PQGeneration.generatePrime(PRIME_BIT_LENGTH, PRIMALITY_ROUNDS);
                    } while (p.equals(q));

                    // n = p x q
                    n = p.multiply(q);

                    // φ(n) = (p - 1)(q - 1)
                    phi = p.subtract(BigInteger.ONE).multiply(q.subtract(BigInteger.ONE));

                } while (!KeyGeneration.calculateExtendedEuclidianAlgorithmXGCD(e, phi)[0].equals(BigInteger.ONE) || n.compareTo(BigInteger.valueOf(256)) < 0);

                // Mendapatkan kunci dekripsi d
                d = KeyGeneration.calculateDecryptionExponent(e, phi);

                // Memastikan e.d mod(φ) == 1, p != q, dan n >= 256
                if (!e.multiply(d).mod(phi).equals(BigInteger.ONE) || p.equals(q) || n.compareTo(BigInteger.valueOf(256)) < 0) {
                    throw new IllegalStateException("the generated RSA key-values failed validation checks");
                }

                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    window.setKeysReady(true);
                    window.setLocalFingerprint(fingerprint(n, e));
                    window.setKeyPresentation(RsaPresentationRecord.keyGeneration(p, q, n, phi, e, d));
                    window.setStatus("Keys ready. Create or join a room.");

                    if (!requestedRoom.isBlank()) {
                        window.setRoomInput(requestedRoom);
                        joinRoom(requestedRoom);
                    }
                } catch (Exception exception) {
                    window.setStatus("RSA key generation failed: " + exception.getMessage());
                }
            }
        }.execute();
    }

    private void createRoom() {
        byte[] roomBytes = new byte[18];
        ROOM_RANDOM.nextBytes(roomBytes);
        joinRoom(Base64.getUrlEncoder().withoutPadding().encodeToString(roomBytes));
    }

    private void joinRoom() {
        joinRoom(extractRoomId(window.getRoomInput()));
    }

    private void joinRoom(String roomId) {
        if (n == null) {
            window.setStatus("Wait for RSA key generation to finish.");
            return;
        }

        if (!roomId.matches("[A-Za-z0-9_-]{4,80}")) {
            window.setStatus("Enter a room ID containing 4-80 letters, numbers, '_' or '-'.");
            return;
        }

        if (window.getDisplayName().isBlank() || window.getDisplayName().length() > 32) {
            window.setStatus("Display name must contain 1-32 characters.");
            return;
        }

        peers.clear();
        pendingDrafts.clear();
        String invitation = applicationUrl.isBlank() ? "#room=" + roomId : applicationUrl + "#room=" + roomId;

        window.setInvitation(invitation);
        window.clearPeerFingerprint();
        window.setConnected(false);
        window.setStatus("Connecting...");

        try {
            ConnectionAdapter.joinRoom(roomId);
            window.setStatus("Waiting for participant.");
        } catch (Throwable error) {
            window.setStatus("Could not join room: " + error.getMessage());
        }
    }

    private static String extractRoomId(String input) {
        String value = input.trim();
        int roomParameter = value.indexOf("room=");

        if (roomParameter >= 0) {
            value = value.substring(roomParameter + 5);
            int end = value.indexOf('&');

            if (end >= 0) {
                value = value.substring(0, end);
            }

            end = value.indexOf('#');

            if (end >= 0) {
                value = value.substring(0, end);
            }
        }

        return value;
    }

    private void leaveRoom() {
        ConnectionAdapter.leaveRoom();
        peers.clear();
        pendingDrafts.clear();
        window.setInvitation("");
        window.clearPeerFingerprint();
        window.setConnected(false);
        window.setStatus("Disconnected.");
    }

    private void sendMessage() {
        // Meminta input text dari User
        String message = window.getMessageInput();

        try {
            // Enkode UTF-8
            byte[] inputAsBytes = TextFormatting.encodeTextToBytes(message);

            // Memvalidasi input dan batas ukuran message sebelum proses RSA dilakukan
            PacketHandler.validateOutgoingMessage(message, inputAsBytes);
            Map.Entry<String, Peer> recipient = peers.entrySet().stream()
                    .filter(entry -> entry.getValue().modulus != null
                            && entry.getValue().verified
                            && entry.getValue().peerConfirmed)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Wait for both participants to confirm fingerprints."));
            Peer peer = recipient.getValue();

            // Menyimpan panjang awal text dalam format bytes
            int inputLengthAsBytes = inputAsBytes.length;

            // Menghitung berapa jumlah bytes yang bisa dimuat ke dalam satu block berdasarkan modulus penerima
            int plaintextBlockSize = TextFormatting.calculatePlaintextBlockSize(peer.modulus);

            // Memecah bytes dari text awal ke dalam blocks sesuai dengan ukuran plaintextBlockSize
            BigInteger[] plaintextBlocks = TextFormatting.convertBytesToBlocks(inputAsBytes, plaintextBlockSize);

            // Melakukan enkripsi RSA ci = mi^e mod n pada setiap block menggunakan public key penerima
            BigInteger[] ciphertextBlocks = transform(plaintextBlocks, peer.exponent, peer.modulus);
            String messageIdentifier = PacketHandler.generateMessageIdentifier();
            String packet = PacketHandler.createMessagePacket(messageIdentifier, inputLengthAsBytes, ciphertextBlocks);
            RsaPresentationRecord presentation = RsaPresentationRecord.encryption(message, inputAsBytes,
                    plaintextBlockSize, plaintextBlocks, ciphertextBlocks, peer.exponent, peer.modulus);

            // Mengirim ciphertext sebagai string agar nilai BigInteger tidak berubah ketika melewati browser adapter
            window.addMessage(messageIdentifier, window.getDisplayName(), message, presentation, true);
            pendingDrafts.put(messageIdentifier, message);
            ConnectionAdapter.sendPacket(recipient.getKey(), packet);

        } catch (IllegalArgumentException | IllegalStateException exception) {
            window.setStatus(exception.getMessage());
        }
    }

    @Override
    public void peerConnected(String peerId) {
        SwingUtilities.invokeLater(() -> {
            if (!peers.containsKey(peerId) && !peers.isEmpty()) {
                window.setStatus("Room already has two participants.");

                return;
            }

            peers.put(peerId, new Peer());
            ConnectionAdapter.sendPacket(peerId, encodePublicKey());
            window.setStatus("Connecting. Exchanging RSA public keys...");
        });
    }

    @Override
    public void peerDisconnected(String peerId) {
        SwingUtilities.invokeLater(() -> {
            Peer peer = peers.remove(peerId);

            if (peer == null) {
                return;
            }

            window.clearPeerFingerprint();
            pendingDrafts.clear();
            window.setConnected(hasReadyPeer());
            window.setStatus("Disconnected. " + peer.name + " left the room.");
        });
    }

    @Override
    public void packetReceived(String peerId, String packet) {
        try {
            PacketHandler.validatePacket(packet);
        } catch (IllegalArgumentException exception) {
            error("Rejected packet: " + exception.getMessage());

            return;
        }

        if (packet.startsWith("KEY|")) {
            SwingUtilities.invokeLater(() -> receivePublicKey(peerId, packet));
        } else if (packet.startsWith("MSG|")) {
            new Thread(() -> receiveMessage(peerId, packet), "rsa-decryption").start();
        } else if (packet.startsWith("ACK|")) {
            SwingUtilities.invokeLater(() -> receiveAcknowledgement(peerId, packet));
        } else if (packet.startsWith("CONFIRM|")) {
            SwingUtilities.invokeLater(() -> receivePeerConfirmation(peerId, packet));
        } else {
            error("Received an unknown packet type.");
        }
    }

    private void receivePublicKey(String peerId, String packet) {
        try {
            if (!peers.containsKey(peerId)) {
                throw new IllegalArgumentException("room already has two participants");
            }

            String[] fields = packet.split("\\|", -1);

            if (fields.length != 4) {
                throw new IllegalArgumentException("invalid public-key packet");
            }

            byte[] nameBytes = Base64.getDecoder().decode(fields[1]);
            String name = new String(nameBytes, StandardCharsets.UTF_8);

            if (!Arrays.equals(nameBytes, name.getBytes(StandardCharsets.UTF_8)) || name.isBlank() || name.length() > 32) {
                throw new IllegalArgumentException("invalid display name");
            }

            BigInteger peerModulus = new BigInteger(fields[2]);
            BigInteger peerExponent = new BigInteger(fields[3]);

            if (peerModulus.compareTo(BigInteger.valueOf(256)) < 0 || peerModulus.bitLength() > 4096 || peerExponent.signum() <= 0 || peerExponent.compareTo(peerModulus) >= 0) {
                throw new IllegalArgumentException("invalid RSA public key");
            }

            Peer peer = peers.get(peerId);
            if (peer == null) {
                throw new IllegalArgumentException("peer disconnected during public-key exchange");
            }
            peer.name = name;
            peer.modulus = peerModulus;
            peer.exponent = peerExponent;
            window.setPeerFingerprint(name, fingerprint(peerModulus, peerExponent));
            updateConnectionStatus(peer);

        } catch (RuntimeException exception) {
            window.setStatus("Rejected public key: " + exception.getMessage());
        }
    }

    private void receiveMessage(String peerId, String packet) {
        try {
            Peer peer = peers.get(peerId);

            if (peer == null || peer.modulus == null || !peer.verified || !peer.peerConfirmed) {
                throw new IllegalArgumentException("both participants must confirm fingerprints before messaging");
            }

            // Memvalidasi packet sebelum ciphertext diproses oleh fungsi RSA
            int blockSize = TextFormatting.calculatePlaintextBlockSize(n);
            PacketHandler.MessagePacket messagePacket = PacketHandler.parseMessagePacket(packet, n, blockSize);

            // Melakukan dekripsi RSA mi' = ci^d mod n pada setiap block menggunakan private key sendiri
            BigInteger[] decryptedBlocks = transform(messagePacket.blocks, d, n);

            // Mengubah kembali blocks ke dalam bentuk teks menggunakan panjang awal text dalam format bytes
            String message = TextFormatting.revertBlocksToText(decryptedBlocks, blockSize, messagePacket.byteLength);
            byte[] bytes = TextFormatting.encodeTextToBytes(message);
            RsaPresentationRecord presentation = RsaPresentationRecord.decryption(message, bytes, blockSize,
                    messagePacket.byteLength, messagePacket.blocks, decryptedBlocks, d, n);

            SwingUtilities.invokeLater(() -> {
                window.addMessage(messagePacket.messageIdentifier, peer.name, message, presentation, false);

                // Mengirim acknowledgement setelah message berhasil didekripsi dan ditampilkan
                ConnectionAdapter.sendPacket(peerId, PacketHandler.createAcknowledgementPacket(messagePacket.messageIdentifier));
            });
        } catch (RuntimeException exception) {
            error("Rejected encrypted message: " + exception.getMessage());
        }
    }

    private void receiveAcknowledgement(String peerId, String packet) {
        try {
            Peer peer = peers.get(peerId);

            if (peer == null || !peer.verified || !peer.peerConfirmed) {
                throw new IllegalArgumentException("both participants must confirm fingerprints before acknowledging");
            }

            String messageIdentifier = PacketHandler.parseAcknowledgementPacket(packet);
            String deliveredDraft = pendingDrafts.remove(messageIdentifier);

            if (deliveredDraft == null) {
                throw new IllegalArgumentException("unknown acknowledgement");
            }

            window.markDelivered(messageIdentifier);
            window.clearMessageInput(deliveredDraft);
            window.setStatus("Ready. Message delivered.");

        } catch (RuntimeException exception) {
            window.setStatus("Rejected acknowledgement: " + exception.getMessage());
        }
    }

    @Override
    public void error(String message) {
        SwingUtilities.invokeLater(() -> {
            peers.clear();
            pendingDrafts.clear();
            window.clearPeerFingerprint();
            window.setConnected(false);
            String lowerCaseMessage = message.toLowerCase(java.util.Locale.ROOT);
            if (lowerCaseMessage.contains("could not connect to peer")) {
                window.setStatus("WebRTC connection failed. Try another network or configure TURN.");
            } else {
                window.setStatus("Connection error: " + message);
            }
        });
    }

    @Override
    public void clipboardCopied() {
        SwingUtilities.invokeLater(() -> window.setStatus("Copied to clipboard."));
    }

    @Override
    public void clipboardFailed(String message) {
        SwingUtilities.invokeLater(() -> window.setStatus("Could not copy to clipboard: " + message));
    }

    private void confirmFingerprints() {
        for (Map.Entry<String, Peer> entry : peers.entrySet()) {
            Peer peer = entry.getValue();
            if (peer.modulus != null) {
                peer.verified = true;
                window.setFingerprintConfirmed();
                ConnectionAdapter.sendPacket(entry.getKey(), "CONFIRM|");
                updateConnectionStatus(peer);

                return;
            }
        }
    }

    private void receivePeerConfirmation(String peerId, String packet) {
        try {
            if (!packet.equals("CONFIRM|")) {
                throw new IllegalArgumentException("invalid fingerprint confirmation");
            }

            Peer peer = peers.get(peerId);
            if (peer == null) {
                throw new IllegalArgumentException("confirmation arrived from an unknown peer");
            }

            peer.peerConfirmed = true;
            updateConnectionStatus(peer);
        } catch (RuntimeException exception) {
            window.setStatus("Rejected peer confirmation: " + exception.getMessage());
        }
    }

    private void updateConnectionStatus(Peer peer) {
        boolean ready = peer.modulus != null && peer.verified && peer.peerConfirmed;
        window.setConnected(ready);

        if (ready) {
            window.setStatus("Ready.");
        } else if (peer.modulus != null && peer.verified) {
            window.setStatus("Fingerprint confirmed. Waiting for peer to confirm.");
        } else if (peer.modulus != null && peer.peerConfirmed) {
            window.setStatus("Peer confirmed. Compare fingerprints and confirm to continue.");
        } else if (peer.modulus != null) {
            window.setStatus("Public key received. Compare fingerprints before messaging.");
        }
    }

    private String encodePublicKey() {
        String name = Base64.getEncoder().encodeToString(window.getDisplayName().getBytes(StandardCharsets.UTF_8));

        return "KEY|" + name + "|" + n + "|" + e;
    }

    private static BigInteger[] transform(BigInteger[] blocks, BigInteger exponent, BigInteger modulus) {
        // Membuat array untuk menyimpan hasil modular eksponen setiap block
        BigInteger[] result = new BigInteger[blocks.length];

        for (int i = 0; i < blocks.length; i++) {
            result[i] = ModularArithmetic.modularExponentiation(blocks[i], exponent, modulus);
        }

        return result;
    }

    private static String fingerprint(BigInteger modulus, BigInteger exponent) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((modulus.toString(16) + ":" + exponent.toString(16)).getBytes(StandardCharsets.UTF_8));
            char[] hexadecimal = "0123456789ABCDEF".toCharArray();
            StringBuilder result = new StringBuilder();

            for (int i = 0; i < hash.length; i++) {
                if (i > 0 && i % 2 == 0) {
                    result.append(' ');
                }

                int value = hash[i] & 0xff;
                result.append(hexadecimal[value >>> 4]).append(hexadecimal[value & 0x0f]);
            }

            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private boolean hasReadyPeer() {
        return peers.values().stream().anyMatch(peer -> peer.modulus != null && peer.verified && peer.peerConfirmed);
    }

    private static final class Peer {
        private String name = "Peer";
        private volatile BigInteger modulus;
        private volatile BigInteger exponent;
        private volatile boolean verified;
        private volatile boolean peerConfirmed;

        private Peer() {
        }

        private Peer(String name, BigInteger modulus, BigInteger exponent) {
            this.name = name;
            this.modulus = modulus;
            this.exponent = exponent;
        }
    }
}
