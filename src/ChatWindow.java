import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.plaf.ColorUIResource;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

public final class ChatWindow {
    private static final Color BACKGROUND = Color.WHITE;
    private static final Color PANEL = new Color(244, 244, 244);
    private static final Color BORDER = new Color(178, 181, 184);
    private static final Color TEXT = new Color(48, 50, 52);

    static {
        UIManager.put("Button.gradient", List.of(0.3f, 0.0f,
                new ColorUIResource(225, 225, 225),
                new ColorUIResource(250, 250, 250),
                new ColorUIResource(210, 210, 210)));
    }

    private final JFrame frame = new JFrame("Amaca");
    private final JTextField displayName = new JTextField("Guest");
    private final JTextField roomInput = new JTextField();
    private final JButton createRoom = new JButton("Create room");
    private final JButton joinRoom = new JButton("Join room");
    private final JButton leaveRoom = new JButton("Leave");
    private final JTextArea invitation = new JTextArea();
    private final QrCodePanel qrCode = new QrCodePanel();
    private final JTextArea fingerprints = new JTextArea(7, 20);
    private final JButton confirmFingerprints = new JButton("Confirm fingerprints");
    private final JLabel status = new JLabel("Generating keys...");
    private final DefaultListModel<Message> messages = new DefaultListModel<>();
    private final JList<Message> messageList = new JList<>(messages);
    private final JTextField messageInput = new JTextField();
    private final JButton send = new JButton("Send");
    private final JTextArea inspector = new JTextArea("Select a message to inspect its RSA steps.");
    private final JButton showProcedure = new JButton("Show RSA procedure");
    private String localFingerprint = "Generating RSA key...";
    private String peerFingerprint = "Waiting for peer...";
    private String invitationLink = "";
    private RsaPresentationRecord keyPresentation;
    private RsaPresentationRecord currentPresentation;

    public ChatWindow() {
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(1180, 720);
        frame.setMinimumSize(new Dimension(900, 600));

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(BACKGROUND);
        root.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        root.add(buildStartPanel(), BorderLayout.NORTH);
        root.add(buildConnectionPanel(), BorderLayout.WEST);
        root.add(buildChatAndInspector(), BorderLayout.CENTER);
        frame.setContentPane(root);

        createRoom.setEnabled(false);
        joinRoom.setEnabled(false);
        leaveRoom.setEnabled(false);
        send.setEnabled(false);
        showProcedure.setEnabled(false);
        confirmFingerprints.setEnabled(false);
        messageInput.setEnabled(false);
        invitation.setEditable(false);
        invitation.setLineWrap(true);
        invitation.setWrapStyleWord(true);
        fingerprints.setEditable(false);
        fingerprints.setLineWrap(true);
        fingerprints.setWrapStyleWord(true);
        inspector.setEditable(false);
        inspector.setLineWrap(true);
        inspector.setWrapStyleWord(true);
        addCopyMenu(fingerprints);
        addCopyMenu(inspector);
        addInvitationCopyMenu();
        addMessageCopyActions();

        messageList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        messageList.addListSelectionListener(event -> {
            Message selected = messageList.getSelectedValue();

            if (!event.getValueIsAdjusting() && selected != null) {
                currentPresentation = selected.presentation;
                inspector.setText(currentPresentation.getOverview(false, false));
                inspector.setCaretPosition(0);
            }
        });

        showProcedure.addActionListener(event -> showRsaProcedure());
    }

    private JPanel buildStartPanel() {
        JPanel panel = panel("Start");
        panel.setLayout(new GridLayout(2, 4, 8, 8));
        panel.add(label("Display name"));
        panel.add(displayName);
        panel.add(createRoom);
        panel.add(status);
        panel.add(label("Room ID or invitation link"));
        panel.add(roomInput);
        panel.add(joinRoom);
        panel.add(new JLabel());

        return panel;
    }

    private JPanel buildConnectionPanel() {
        JPanel panel = panel("Connection");
        panel.setPreferredSize(new Dimension(250, 0));
        panel.setLayout(new BorderLayout(8, 8));
        panel.add(new JScrollPane(invitation), BorderLayout.NORTH);
        qrCode.setBorder(BorderFactory.createLineBorder(BORDER));
        panel.add(qrCode, BorderLayout.CENTER);

        JPanel verification = new JPanel(new BorderLayout(4, 4));
        verification.setBackground(PANEL);
        verification.add(new JScrollPane(fingerprints), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(1, 2, 4, 0));
        buttons.setBackground(PANEL);
        buttons.add(confirmFingerprints);
        buttons.add(leaveRoom);
        verification.add(buttons, BorderLayout.SOUTH);
        panel.add(verification, BorderLayout.SOUTH);
        updateFingerprints();

        return panel;
    }

    private JSplitPane buildChatAndInspector() {
        JPanel chat = panel("Chat");
        chat.setLayout(new BorderLayout(8, 8));
        chat.add(new JScrollPane(messageList), BorderLayout.CENTER);

        JPanel composer = new JPanel(new BorderLayout(8, 0));
        composer.setBackground(PANEL);
        composer.add(messageInput, BorderLayout.CENTER);
        composer.add(send, BorderLayout.EAST);
        chat.add(composer, BorderLayout.SOUTH);

        JPanel rsaInspector = panel("RSA inspector");
        rsaInspector.setLayout(new BorderLayout());
        rsaInspector.add(new JScrollPane(inspector), BorderLayout.CENTER);
        rsaInspector.add(showProcedure, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chat, rsaInspector);
        split.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    @Override
                    public void paint(Graphics graphics) {
                        graphics.setColor(BACKGROUND);
                        graphics.fillRect(0, 0, getWidth(), getHeight());
                    }
                };
            }
        });
        split.setBorder(null);
        split.setDividerSize(12);
        split.setResizeWeight(0.55);
        split.setDividerLocation(480);

        return split;
    }

    private static JPanel panel(String title) {
        JPanel panel = new JPanel();
        panel.setBackground(PANEL);
        panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(BORDER), title, 0, 0, null, TEXT));

        return panel;
    }

    private static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT);

        return label;
    }

    public void show() {
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public void onCreateRoom(Runnable action) {
        createRoom.addActionListener(event -> action.run());
    }

    public void onJoinRoom(Runnable action) {
        joinRoom.addActionListener(event -> action.run());
        roomInput.addActionListener(event -> action.run());
    }

    public void onLeaveRoom(Runnable action) {
        leaveRoom.addActionListener(event -> action.run());
    }

    public void onConfirmFingerprints(Runnable action) {
        confirmFingerprints.addActionListener(event -> action.run());
    }

    public void onSend(Runnable action) {
        send.addActionListener(event -> action.run());
        messageInput.addActionListener(event -> action.run());
    }

    public void onClose(Runnable action) {
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                action.run();
            }
        });
    }

    public String getDisplayName() {
        return displayName.getText().trim();
    }

    public String getRoomInput() {
        return roomInput.getText();
    }

    public void setRoomInput(String value) {
        roomInput.setText(value);
    }

    public String getMessageInput() {
        return messageInput.getText();
    }

    public void clearMessageInput(String deliveredDraft) {
        if (messageInput.getText().equals(deliveredDraft)) {
            messageInput.setText("");
        }
    }

    public void setKeysReady(boolean ready) {
        createRoom.setEnabled(ready);
        joinRoom.setEnabled(ready);
    }

    public void setKeyPresentation(RsaPresentationRecord presentation) {
        keyPresentation = presentation;
        currentPresentation = presentation;
        showProcedure.setEnabled(true);
        inspector.setText(presentation.getOverview(false, false));
        inspector.setCaretPosition(0);
    }

    public void setConnected(boolean connected) {
        leaveRoom.setEnabled(connected || !invitation.getText().isBlank());
        messageInput.setEnabled(connected);
        send.setEnabled(connected);
    }

    public void setInvitation(String link) {
        invitationLink = link;
        invitation.setText(link.isBlank() ? "No active room" : "Invitation link:\n" + link);
        qrCode.setInvitation(link);
    }

    public void setLocalFingerprint(String fingerprint) {
        localFingerprint = fingerprint;
        updateFingerprints();
    }

    public void setPeerFingerprint(String name, String fingerprint) {
        peerFingerprint = name + ":\n" + fingerprint;
        confirmFingerprints.setText("Confirm fingerprints");
        confirmFingerprints.setEnabled(true);
        updateFingerprints();
    }

    public void clearPeerFingerprint() {
        peerFingerprint = "Waiting for peer...";
        confirmFingerprints.setEnabled(false);
        updateFingerprints();
    }

    public void setFingerprintConfirmed() {
        confirmFingerprints.setEnabled(false);
        confirmFingerprints.setText("Fingerprints confirmed");
    }

    private void updateFingerprints() {
        fingerprints.setText("Your key:\n" + localFingerprint + "\n\nPeer key:\n" + peerFingerprint);
        fingerprints.setCaretPosition(0);
        if (!confirmFingerprints.isEnabled()) {
            confirmFingerprints.setText("Confirm fingerprints");
        }
    }

    public void setStatus(String text) {
        status.setText(text);
        status.setForeground(TEXT);
        status.setToolTipText(text);
    }

    public void addMessage(String identifier, String sender, String text, RsaPresentationRecord presentation, boolean outgoing) {
        Message message = new Message(identifier, sender, text, presentation, outgoing);
        messages.addElement(message);
        messageList.setSelectedIndex(messages.size() - 1);
        messageList.ensureIndexIsVisible(messages.size() - 1);
    }

    public void markDelivered(String identifier) {
        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);

            if (message.outgoing && message.identifier.equals(identifier)) {
                message.delivered = true;
                messages.set(i, message);

                return;
            }
        }
    }

    private void showRsaProcedure() {
        if (currentPresentation == null) {
            return;
        }

        RsaPresentationRecord presentation = currentPresentation;
        JDialog dialog = new JDialog(frame, "RSA procedure", false);
        JTextArea details = new JTextArea();
        JCheckBox showFullIntegers = new JCheckBox("Show full integer values");
        JCheckBox revealPrivateValues = new JCheckBox("Reveal private RSA values");
        boolean messagePresentation = presentation != keyPresentation;
        String[] sections = new String[messagePresentation ? presentation.getBlockCount() + 2 : 1];
        sections[0] = "Key generation";

        if (messagePresentation) {
            sections[1] = "Message overview";

            for (int i = 2; i < sections.length; i++) {
                sections[i] = "Block " + (i - 1);
            }
        }

        JComboBox<String> blockSelector = new JComboBox<>(sections);
        blockSelector.setSelectedIndex(messagePresentation ? 1 : 0);
        Runnable updateDetails = () -> {
            if (blockSelector.getSelectedIndex() == 0) {
                details.setText(keyPresentation.getOverview(revealPrivateValues.isSelected(), showFullIntegers.isSelected()));
            } else if (blockSelector.getSelectedIndex() == 1) {
                details.setText(presentation.getOverview(revealPrivateValues.isSelected(), showFullIntegers.isSelected()));
            } else {
                details.setText(presentation.getBlockDetails(blockSelector.getSelectedIndex() - 2,
                        revealPrivateValues.isSelected(), showFullIntegers.isSelected()));
            }

            details.setCaretPosition(0);
        };

        JPanel controls = new JPanel(new GridLayout(1, 3, 8, 0));
        controls.add(blockSelector);
        controls.add(showFullIntegers);
        controls.add(revealPrivateValues);

        details.setEditable(false);
        addCopyMenu(details);
        blockSelector.addActionListener(event -> updateDetails.run());
        showFullIntegers.addActionListener(event -> updateDetails.run());
        revealPrivateValues.addActionListener(event -> updateDetails.run());
        updateDetails.run();

        dialog.setLayout(new BorderLayout(8, 8));
        dialog.add(controls, BorderLayout.NORTH);
        dialog.add(new JScrollPane(details), BorderLayout.CENTER);
        dialog.setSize(850, 620);
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private void addInvitationCopyMenu() {
        JPopupMenu menu = new JPopupMenu();
        javax.swing.JMenuItem copyLink = new javax.swing.JMenuItem("Copy invitation link");
        copyLink.addActionListener(event -> ConnectionAdapter.copyText(invitationLink));
        menu.add(copyLink);
        invitation.setComponentPopupMenu(menu);
    }

    private void addCopyMenu(JTextArea textArea) {
        JPopupMenu menu = new JPopupMenu();
        javax.swing.JMenuItem copy = new javax.swing.JMenuItem("Copy selected text");
        copy.addActionListener(event -> {
            String selectedText = textArea.getSelectedText();
            if (selectedText != null && !selectedText.isEmpty()) {
                ConnectionAdapter.copyText(selectedText);
            }
        });
        menu.add(copy);
        textArea.setComponentPopupMenu(menu);
    }

    private void addMessageCopyActions() {
        javax.swing.AbstractAction copySelectedMessage = new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                Message selected = messageList.getSelectedValue();
                if (selected != null) {
                    ConnectionAdapter.copyText(selected.text);
                }
            }
        };
        messageList.getInputMap(JList.WHEN_FOCUSED)
                .put(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), "copy-message");
        messageList.getActionMap().put("copy-message", copySelectedMessage);

        JPopupMenu menu = new JPopupMenu();
        javax.swing.JMenuItem copy = new javax.swing.JMenuItem("Copy message");
        copy.addActionListener(copySelectedMessage);
        menu.add(copy);
        messageList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                showMessageMenu(event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                showMessageMenu(event);
            }

            private void showMessageMenu(MouseEvent event) {
                if (event.isPopupTrigger()) {
                    int index = messageList.locationToIndex(event.getPoint());
                    if (index >= 0) {
                        messageList.setSelectedIndex(index);
                        menu.show(messageList, event.getX(), event.getY());
                    }
                }
            }
        });
    }

    private static final class Message {
        private final String identifier;
        private final String sender;
        private final String text;
        private final RsaPresentationRecord presentation;
        private final boolean outgoing;
        private boolean delivered;

        private Message(String identifier, String sender, String text, RsaPresentationRecord presentation, boolean outgoing) {
            this.identifier = identifier;
            this.sender = sender;
            this.text = text;
            this.presentation = presentation;
            this.outgoing = outgoing;
        }

        @Override
        public String toString() {
            return sender + ": " + text + (outgoing ? delivered ? " [Delivered]" : " [Sending]" : "");
        }
    }
}
