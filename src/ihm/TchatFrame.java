package ihm;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

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
    private JTextPane tchatArea = new JTextPane();
    private JTextField messageField = new JTextField();
    private DefaultListModel<ConversationItem> convListModel = new DefaultListModel<>();
    private JList<ConversationItem> convList = new JList<>(convListModel);
    private JLabel tchatTitleLabel = new JLabel("Sélectionnez une conversation");

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

        public int getUnreadCount() {
            return unreadCount;
        }
    }

    public TchatFrame(HelloClient2 client, Hello2 tchatService) {
        this.client = client;
        this.tchatService = tchatService;

        try {
            setTitle(client.getName() + " (ID: " + client.getClientId() + ")");
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

        // --- ZONE GAUCHE (inchangée) ---
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setPreferredSize(new Dimension(200, 0));
        leftPanel.setBorder(BorderFactory.createTitledBorder("Conversations"));
        JButton addConvBtn = new JButton("+ Nouvelle Discussion");
        convList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane listPane = new JScrollPane(convList);
        leftPanel.add(addConvBtn, BorderLayout.NORTH);
        leftPanel.add(listPane, BorderLayout.CENTER);

        // --- ZONE DROITE (Cœur du changement) ---
        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));

        // 1. Barre de titre en haut de la conversation
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        headerPanel.setBackground(Color.LIGHT_GRAY); // Optionnel : pour bien la voir
        tchatTitleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        headerPanel.add(tchatTitleLabel);
        rightPanel.add(headerPanel, BorderLayout.NORTH);

        // 2. Zone de messages (Centre)
        tchatArea.setEditable(false);
        JScrollPane chatPane = new JScrollPane(tchatArea);
        rightPanel.add(chatPane, BorderLayout.CENTER);

        // 3. Barre de saisie (Bas de la zone droite uniquement)
        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        JButton sendBtn = new JButton("Envoyer");
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendBtn, BorderLayout.EAST);
        rightPanel.add(inputPanel, BorderLayout.SOUTH);

        // SplitPane : Gauche vs Droite
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        add(splitPane, BorderLayout.CENTER);

        // --- ACTIONS ---
        sendBtn.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());
        addConvBtn.addActionListener(e -> showAddConvDialog());
    }

    private void appendStyledMessage(String senderName, String message, boolean isMe) {
        StyledDocument doc = tchatArea.getStyledDocument();

        // 1. Créer le style d'alignement
        SimpleAttributeSet style = new SimpleAttributeSet();
        StyleConstants.setAlignment(style, isMe ? StyleConstants.ALIGN_RIGHT : StyleConstants.ALIGN_LEFT);
        StyleConstants.setForeground(style, isMe ? new Color(0, 102, 204) : Color.BLACK); // Bleu pour soi
        StyleConstants.setFontFamily(style, "SansSerif");
        StyleConstants.setFontSize(style, 12);

        // 2. Appliquer l'alignement au paragraphe
        int start = doc.getLength();
        String content = "[" + senderName + "] " + message + "\n";
        
        try {
            doc.insertString(start, content, style);
            doc.setParagraphAttributes(start, content.length(), style, false);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void showAddConvDialog() {
        String targetIdStr = JOptionPane.showInputDialog(this, 
            "Entrez l'ID du client avec qui discuter :", 
            "Nouvelle conversation", 
            JOptionPane.QUESTION_MESSAGE);

        if (targetIdStr != null && !targetIdStr.trim().isEmpty()) {
            try {
                int targetId = Integer.parseInt(targetIdStr.trim());
                int myId = client.getClientId();

                if (targetId == myId) {
                    JOptionPane.showMessageDialog(this, "Vous ne pouvez pas discuter avec vous-même !");
                    return;
                }

                String newConvId = (myId < targetId) ? myId + "-" + targetId : targetId + "-" + myId;

                // 1. Vérifier si elle existe déjà
                ConversationItem existingItem = null;
                for (int i = 0; i < convListModel.size(); i++) {
                    if (convListModel.getElementAt(i).getId().equals(newConvId)) {
                        existingItem = convListModel.getElementAt(i);
                        break;
                    }
                }

                if (existingItem == null) {
                    // 2. Si elle n'existe pas, on l'ajoute
                    ConversationItem newItem = new ConversationItem(newConvId, 0);
                    convListModel.addElement(newItem);
                    existingItem = newItem;
                }

                // 3. On la sélectionne (ceci déclenchera le listener et donc loadHistory)
                convList.setSelectedValue(existingItem, true);
                currentConv = newConvId;

            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "L'ID doit être un nombre valide.");
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
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
            // 1. On vérifie si l'expéditeur est moi-même
            // Note : Il est préférable de comparer les IDs si possible, 
            // mais ici on compare le pseudo avec celui du client
            boolean isMe = sender.equals(client.getName());

            // 2. On utilise la nouvelle méthode stylée
            appendStyledMessage(sender, message, isMe);

            // 3. On fait défiler vers le bas
            tchatArea.setCaretPosition(tchatArea.getDocument().getLength());
        });
    }

    public void onMessageReceived(String convId, String sender, String message) {
        SwingUtilities.invokeLater(() -> {
            if (currentConv != null && currentConv.equals(convId)) {
                appendStyledMessage(sender, "[NOUVEAU] " + message, false);
                
                try {
                    tchatService.getHistory(client.getClientId(), convId);
                } catch (Exception e) {
                    System.err.println("Erreur synchro curseur : " + e.getMessage());
                }
            
            } else {
                // Utilisation de la méthode avec l'ID calculé (ex: "1-2")
                updateUnreadInList(convId);
            }
        });
    }

    public void onGeneralMessageReceived(String sender, String message) {
        SwingUtilities.invokeLater(() -> {
            // Si l'utilisateur est actuellement sur la conversation "GENERAL"
            if ("GENERAL".equals(currentConv)) {
                appendStyledMessage(sender, "[NOUVEAU] " + message, false);
                tchatArea.setCaretPosition(tchatArea.getDocument().getLength());
                
                // On peut tenter un petit rafraîchissement silencieux pour le curseur serveur
                try { tchatService.getHistory(client.getClientId(), "GENERAL"); } catch(Exception e){}
            } 
            else {
                // Sinon, on cherche "GENERAL" dans la liste pour augmenter le compteur
                updateUnreadInList("GENERAL");
            }
        });
    }

    private void updateUnreadInList(String convId) {
        boolean found = false;
        for (int i = 0; i < convListModel.size(); i++) {
            if (convListModel.getElementAt(i).getId().equals(convId)) {
                convListModel.getElementAt(i).setUnreadCount(convListModel.getElementAt(i).getUnreadCount() + 1);
                found = true;
                break;
            }
        }
        
        // Si la conversation n'existe pas encore dans la liste (nouveau contact)
        if (!found) {
            ConversationItem newItem = new ConversationItem(convId, 1);
            convListModel.addElement(newItem);
        }
        convList.repaint();
    }

    // private void updateUnreadInList(String convId) {
    //     // On parcourt tous les éléments du modèle de la JList
    //     for (int i = 0; i < convListModel.size(); i++) {
    //         ConversationItem item = convListModel.getElementAt(i);
            
    //         // Si l'ID de l'item correspond à la conversation du message reçu
    //         if (item.getId().equals(convId)) {
    //             // On augmente le compteur interne de l'objet
    //             item.setUnreadCount(item.getUnreadCount() + 1);
                
    //             // TRES IMPORTANT : On demande à la JList de se redessiner
    //             // Cela va appeler le .toString() de l'item et afficher le "(1 message non lu)"
    //             convList.repaint();
    //             break; 
    //         }
    //     }
    // }

    private void loadHistory() {
        if (currentConv == null) return;

        try {
            int myId = client.getClientId();
            int lastReadCursor = tchatService.getCursor(myId, currentConv);
            List<TchatMessage> history = tchatService.getHistory(myId, currentConv);
            
            tchatArea.setText(""); // On vide
            
            for (TchatMessage m : history) {
                boolean isMe = (m.senderId == myId);
                String prefix = (m.id > lastReadCursor) ? "[NOUVEAU] " : "";
                appendStyledMessage(m.senderName, prefix + m.content, isMe);
            }
            
            tchatArea.setCaretPosition(tchatArea.getDocument().getLength());
            messageField.setEnabled(true);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void refreshConversations() {
        try {
            Map<String, Integer> convs = tchatService.getConversationsList(client.getClientId());
            
            // Au lieu de clear(), on met à jour ou on ajoute.
            // Si tu veux garder le clear(), il faut ré-ajouter manuellement currentConv après :
            convListModel.clear();

            // On remet les convs du serveur
            for (Map.Entry<String, Integer> entry : convs.entrySet()) {
                convListModel.addElement(new ConversationItem(entry.getKey(), entry.getValue()));
            }

            // --- FIX : Si la conv actuelle n'est pas dans ce que le serveur connaît ---
            // (cas d'une nouvelle conv sans message), on la rajoute de force dans la liste
            if (currentConv != null) {
                boolean found = false;
                for (int i = 0; i < convListModel.size(); i++) {
                    if (convListModel.get(i).getId().equals(currentConv)) {
                        found = true; 
                        convList.setSelectedIndex(i);
                        break;
                    }
                }
                if (!found) {
                    ConversationItem pending = new ConversationItem(currentConv, 0);
                    convListModel.addElement(pending);
                    convList.setSelectedValue(pending, true);
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
                        
                        loadHistory();

                        // 1. On remet le compteur à zéro localement pour l'affichage
                        item.setUnreadCount(0); 
                        convList.repaint(); // Force Swing à redessiner la liste (appelle toString())
                        
                        refreshConversations();
                    }
                }
            }
        });

        // Gestion de la fermeture propre (comme vu précédemment)
        this.addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent e) {
                client.close();
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