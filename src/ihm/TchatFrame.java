package ihm;

import javax.swing.*;
import java.awt.*;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

import common.TchatMessage;
import client.HelloClient2;
import interfaces.server.Hello2;

public class TchatFrame extends JFrame {
    private HelloClient2 client;
    private Hello2 tchatService;
    private String currentConv = null;

    // Composants IHM
    private JTextArea tchatArea = new JTextArea();
    private JTextField messageField = new JTextField();
    private DefaultListModel<ConversationItem> convListModel = new DefaultListModel<>();
    private JList<ConversationItem> convList = new JList<>(convListModel);

    private class ConversationItem {
        private String id;        // "GENERAL" ou "1-2"
        private int unreadCount;

        public ConversationItem(String id, int unreadCount) {
            this.id = id;
            this.unreadCount = unreadCount;
        }

        public String getId() { return id; }

        // C'est cette méthode que le JList appelle pour afficher le texte
        @Override
        public String toString() {
            if (unreadCount <= 0) return id;
            String s = (unreadCount == 1) ? " message non lu" : " messages non lus";
            return id + " (" + unreadCount + s + ")";
        }

        public void setUnreadCount(int n) {
            this.unreadCount = n;
        }
    }

    public TchatFrame(HelloClient2 client, Hello2 tchatService) {
        this.client = client;
        this.tchatService = tchatService;

        try {
            setTitle("Tchat RMI - " + client.getName() + " (ID: " + client.getClientId() + ")");
        }
        catch (RemoteException e) {
            System.err.println("Erreur lors de la récupération de l'ID client : " + e.getMessage());
        }
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);

        initLayout();
        setupEvents();
        refreshConversations();

        tchatArea.setText("Bienvenue ! Veuillez sélectionner une conversation à gauche.");
        messageField.setEnabled(false); // Désactive le champ au début
    }

    private void initLayout() {
        setLayout(new BorderLayout(5, 5));

        // --- ZONE GAUCHE : Liste des conversations ---
        convList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane listPane = new JScrollPane(convList);
        listPane.setPreferredSize(new Dimension(200, 0));
        listPane.setBorder(BorderFactory.createTitledBorder("Conversations"));

        // --- ZONE CENTRALE : Messages ---
        tchatArea.setEditable(false);
        tchatArea.setLineWrap(true);
        JScrollPane chatPane = new JScrollPane(tchatArea);
        // chatPane.setBorder(BorderFactory.createTitledBorder("Messages : " + currentConv));

        // SplitPane pour séparer liste et messages
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listPane, chatPane);
        add(splitPane, BorderLayout.CENTER);

        // --- ZONE BAS : Envoi ---
        JPanel southPanel = new JPanel(new BorderLayout(5, 5));
        southPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        JButton sendBtn = new JButton("Envoyer");
        southPanel.add(messageField, BorderLayout.CENTER);
        southPanel.add(sendBtn, BorderLayout.EAST);
        add(southPanel, BorderLayout.SOUTH);

        // Action d'envoi
        sendBtn.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());
    }

    // --- LOGIQUE ---

    private void sendMessage() {
        String text = messageField.getText().trim();
        if (text.isEmpty()) return;

        try {
            if (currentConv.equals("GENERAL")) {
                tchatService.sendGeneralMessage(client.getClientId(), text);
            } else {
                // Pour le privé, on extrait l'ID du destinataire du nom de conv (ex: "1-2")
                int targetId = extractTargetId(currentConv);
                tchatService.sendDirectMessage(client.getClientId(), targetId, text);
            }
            messageField.setText("");
            loadHistory(); // On rafraîchit pour voir son propre message
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erreur d'envoi : " + ex.getMessage());
        }
    }

    public void appendMessage(String sender, String message) {
        // Cette méthode sera appelée par le callback du HelloClient2
        SwingUtilities.invokeLater(() -> {
            tchatArea.append("[" + sender + "] " + message + "\n");
            tchatArea.setCaretPosition(tchatArea.getDocument().getLength());
        });
    }

    private void loadHistory() {
        if (currentConv == null) return;

        try {
            // ÉTAPE A : On demande au serveur l'ancienne position (ex: message n°5)
            int lastReadCursor = tchatService.getCursor(client.getClientId(), currentConv);

            // ÉTAPE B : On récupère la liste (le serveur met alors le curseur à jour à n°10)
            List<TchatMessage> history = tchatService.getHistory(client.getClientId(), currentConv);
            
            tchatArea.setText("");
            for (TchatMessage m : history) {
                // ÉTAPE C : On compare avec l'ANCIENNE position (5)
                // Si l'id est 6, 7, 8, 9 ou 10 -> [NOUVEAU] s'affiche
                String prefix = (m.id > lastReadCursor) ? "[NOUVEAU] " : "";
                tchatArea.append(prefix + "[" + m.senderName + "] " + m.content + "\n");
            }
            tchatArea.setCaretPosition(tchatArea.getDocument().getLength());
            messageField.setEnabled(true); // Active la saisie
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void refreshConversations() {
        try {
            // On récupère la Map<String, Integer> de ta méthode getConversationsList
            Map<String, Integer> convs = tchatService.getConversationsList(client.getClientId());
            convListModel.clear();

            // String name = "GENERAL";
            // convListModel.addElement("GENERAL");

            for (Map.Entry<String, Integer> entry : convs.entrySet()) {
                ConversationItem item = new ConversationItem(entry.getKey(), entry.getValue());
                convListModel.addElement(item);
                
                // On ne resélectionne que si currentConv correspond à l'item
                if (currentConv != null && item.getId().equals(currentConv)) {
                    convList.setSelectedValue(item, true);
                }
            }
        } catch (Exception e) { 
            System.err.println("Erreur lors de la récupération des conversations : " + e.getMessage());
        };
    }

    private void setupEvents() {
        // Changer de conversation au clic dans la liste
        convList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                ConversationItem item = convList.getSelectedValue();
                if (item != null) {
                    String selectedId = item.getId();
                    if (currentConv == null || !selectedId.equals(currentConv)) {
                        currentConv = selectedId;
                        
                        // 1. On remet le compteur à zéro localement pour l'affichage
                        item.setUnreadCount(0); 
                        convList.repaint(); // Force Swing à redessiner la liste (appelle toString())

                        // 2. On charge les messages
                        loadHistory();
                        refreshConversations();
                    }
                }
            }
        });

        // Gestion de la fermeture propre (comme vu précédemment)
        this.addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent e) {
                // client.close(); // A IMPLEMENTER
            }
        });
    }

    private int extractTargetId(String convId) {
        // Logique pour trouver l'autre ID dans "ID1-ID2"
        String[] ids = convId.split("-");
        int idA = Integer.parseInt(ids[0]);
        int idB = Integer.parseInt(ids[1]);
        try {
            return (idA == client.getClientId()) ? idB : idA;
        }
        catch (RemoteException e) {
            System.err.println("Erreur lors de la récupération de l'ID client : " + e.getMessage());
            return -1; // Erreur
        }
        
    }
}