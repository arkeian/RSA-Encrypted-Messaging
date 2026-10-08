public final class ConnectionAdapter {
    public interface Listener {
        void peerConnected(String peerId);

        void peerDisconnected(String peerId);

        void packetReceived(String peerId, String packet);

        void error(String message);

        void clipboardCopied();

        void clipboardFailed(String message);
    }

    private static Listener listener;

    private ConnectionAdapter() {
    }

    public static void setListener(Listener value) {
        listener = value;
        Thread callbackThread = new Thread(() -> registerListener(listener), "trystero-callback");
        callbackThread.setDaemon(true);
        callbackThread.start();
    }

    private static native void registerListener(Listener listener);

    public static void applicationLoaded() {
        try {
            notifyApplicationLoaded();
        } catch (UnsatisfiedLinkError ignored) {
        }
    }

    private static native void notifyApplicationLoaded();

    public static native void joinRoom(String roomId);

    public static native void sendPacket(String peerId, String packet);

    public static native void copyText(String text);

    public static native void leaveRoom();
}
