import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;

public final class QrCodePanel extends JPanel {
    private BitMatrix modules;
    private String emptyText = "Create or join a room to show its QR code";

    public QrCodePanel() {
        setBackground(Color.WHITE);
    }

    public void setInvitation(String invitation) {
        if (invitation.isBlank()) {
            modules = null;
            emptyText = "Create or join a room to show its QR code";
        } else {
            try {
                modules = new QRCodeWriter().encode(invitation, BarcodeFormat.QR_CODE, 1, 1);
            } catch (WriterException | RuntimeException error) {
                modules = null;
                emptyText = "QR code unavailable";
            }
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        if (modules == null) {
            FontMetrics metrics = graphics.getFontMetrics();
            graphics.setColor(Color.DARK_GRAY);
            graphics.drawString(emptyText, Math.max(8, (getWidth() - metrics.stringWidth(emptyText)) / 2), getHeight() / 2);
            return;
        }

        int moduleSize = Math.max(1, Math.min(getWidth() / modules.getWidth(), getHeight() / modules.getHeight()));
        int codeSize = modules.getWidth() * moduleSize;
        int left = (getWidth() - codeSize) / 2;
        int top = (getHeight() - codeSize) / 2;

        graphics.setColor(Color.WHITE);
        graphics.fillRect(left, top, codeSize, codeSize);
        graphics.setColor(Color.BLACK);
        for (int row = 0; row < modules.getHeight(); row++) {
            for (int column = 0; column < modules.getWidth(); column++) {
                if (modules.get(column, row)) {
                    graphics.fillRect(left + column * moduleSize, top + row * moduleSize, moduleSize, moduleSize);
                }
            }
        }
    }

}
