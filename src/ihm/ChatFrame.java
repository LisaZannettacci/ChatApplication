package ihm;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

import common.ChatMessage;
import client.ChatClient;
import interfaces.server.ChatService;

/**
 * Fenêtre principale de l'interface graphique du chat.
 * Cette fenêtre Swing affiche la liste des conversations, l'historique des messages
 * et permet l'envoi de messages directs ou généraux.
 * 
 * Architecture de l'IHM :
 * - Panneau gauche : liste des conversations avec compteurs de messages non lus
 * - Panneau droit : historique de la conversation sélectionnée + champ de saisie
 * 
 * Fonctionnalités principales :
 * - Affichage de toutes les conversations (GENERAL et privées)
 * - Création de nouvelles conversations privées par saisie d'ID
 * - Envoi de messages (général ou direct selon la conversation active)
 * - Réception de messages en temps réel via callbacks
 * - Gestion des messages lus/non lus avec compteurs visuels
 * - Style différencié pour les messages envoyés (alignés à droite, bleu)
 * - Fermeture propre avec déconnexion RMI
 * 
 * Variables membres :
 * - client : référence au client connecté (ChatClient)
 * - tchatService : service RMI distant pour l'envoi de messages et récupération d'historiques
 * - currentConv : ID de la conversation actuellement sélectionnée (null si aucune)
 * - Composants IHM : tchatArea, messageField, convListModel, convList, tchatTitleLabel
 * 
 * @see ChatClient
 * @see interfaces.server.ChatService
 * @see common.ChatMessage
 */
public class ChatFrame extends JFrame {
    /** Référence au client connecté */
    private ChatClient client;
    
    /** Service RMI distant pour les opérations de chat */
    private ChatService tchatService;
    
    /** ID de la conversation actuellement sélectionnée ("GENERAL" ou "id1-id2") */
    private String currentConv = null;

    // Composants IHM
    /** Zone d'affichage stylisée des messages (JTextPane pour formatage avancé) */
    private JTextPane tchatArea = new JTextPane();
    
    /** Champ de saisie du message à envoyer */
    private JTextField messageField = new JTextField();
    
    /** Modèle de données pour la liste des conversations */
    private DefaultListModel<ConversationItem> convListModel = new DefaultListModel<>();
    
    /** Liste visuelle des conversations */
    private JList<ConversationItem> convList = new JList<>(convListModel);
    
    /** Label affichant le titre de la conversation active */
    private JLabel tchatTitleLabel = new JLabel("Sélectionnez une conversation");

    /**
     * Classe interne représentant un élément de conversation dans la liste.
     * Encapsule l'ID de la conversation et son compteur de messages non lus.
     * 
     * Variables membres :
     * - id : identifiant de la conversation ("GENERAL" ou "id1-id2")
     * - unreadCount : nombre de messages non lus dans cette conversation
     */
    private class ConversationItem {
        /** Identifiant de la conversation */
        private String id; // "GENERAL" ou "id1-id2"
        
        /** Nombre de messages non lus */
        private int unreadCount;

        /**
         * Constructeur de ConversationItem.
         * 
         * @param id l'identifiant de la conversation
         * @param unreadCount le nombre de messages non lus
         */
        public ConversationItem(String id, int unreadCount) {
            this.id = id;
            this.unreadCount = unreadCount;
        }

        /**
         * Retourne l'ID de la conversation.
         * 
         * @return l'identifiant de la conversation
         */
        public String getId() { return id; }

        /**
         * Retourne la représentation textuelle de l'élément pour affichage dans la JList.
         * Ajoute le compteur de messages non lus si > 0.
         * 
         * @return le texte à afficher (ex: "GENERAL (2 messages non lus)")
         */
        @Override
        public String toString() {
            if (unreadCount <= 0) return id;
            String s = (unreadCount == 1) ? " message non lu" : " messages non lus";
            return id + " (" + unreadCount + s + ")";
        }

        /**
         * Définit le nombre de messages non lus.
         * 
         * @param n le nouveau nombre de messages non lus
         */
        public void setUnreadCount(int n) {
            this.unreadCount = n;
        }

        /**
         * Retourne le nombre de messages non lus.
         * 
         * @return le compteur de messages non lus
         */
        public int getUnreadCount() {
            return unreadCount;
        }
    }

    /**
     * Constructeur de la fenêtre principale du chat.
     * Initialise l'interface graphique, configure les gestionnaires d'événements,
     * charge les conversations et l'historique initial.
     * 
     * Séquence d'initialisation :
     * 1. Configuration de la fenêtre (titre, taille, fermeture)
     * 2. Construction de l'interface (layout, composants)
     * 3. Configuration des écouteurs (sélection, envoi, fermeture)
     * 4. Chargement des conversations depuis le serveur
     * 5. Affichage de la fenêtre
     * 
     * @param client le client connecté
     * @param tchatService le service RMI distant
     * @throws NullPointerException si client ou tchatService est null
     */
    public ChatFrame(ChatClient client, ChatService tchatService) {
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
        messageField.setEnabled(false); // Désactive le champ au début pour éviter d'envoyer des messages sans conversation sélectionnée
    }

    /**
     * Construit l'interface graphique de la fenêtre.
     * 
     * Organisation de l'interface :
     * - JSplitPane (séparateur redimensionnable)
     *   - Gauche : liste des conversations + bouton d'ajout
     *   - Droite : titre + zone de messages + champ de saisie
     * 
     * Configuration des composants :
     * - tchatArea : non éditable, auto-scroll, formatage stylisé
     * - messageField : champ de saisie avec listener sur ENTER
     * - convList : sélection unique, auto-rafraîchissement
     * - Fenêtre : taille 900x600, centrée
     */
    private void initLayout() {
        setLayout(new BorderLayout(5, 5));

        // --- ZONE GAUCHE (Discussions) ---
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setPreferredSize(new Dimension(200, 0));
        leftPanel.setBorder(BorderFactory.createTitledBorder("Conversations"));
        JButton addConvBtn = new JButton("+ Nouvelle Discussion");
        convList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane listPane = new JScrollPane(convList);
        leftPanel.add(addConvBtn, BorderLayout.NORTH);
        leftPanel.add(listPane, BorderLayout.CENTER);

        // --- ZONE DROITE (Conversation sélectionnée) ---
        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));

        // Barre de titre en haut de la conversation
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        headerPanel.setBackground(Color.LIGHT_GRAY); // Optionnel : pour bien la voir
        tchatTitleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        headerPanel.add(tchatTitleLabel);
        rightPanel.add(headerPanel, BorderLayout.NORTH);

        // Zone de messages
        tchatArea.setEditable(false);
        JScrollPane chatPane = new JScrollPane(tchatArea);
        rightPanel.add(chatPane, BorderLayout.CENTER);

        // Barre de saisie du message
        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        JButton sendBtn = new JButton("Envoyer");
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendBtn, BorderLayout.EAST);
        rightPanel.add(inputPanel, BorderLayout.SOUTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        add(splitPane, BorderLayout.CENTER);

        // Définit les actions des boutons et champs
        sendBtn.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());
        addConvBtn.addActionListener(e -> showAddConvDialog());
    }

    /**
     * Ajoute un message stylisé à la zone d'affichage.
     * Les messages envoyés par l'utilisateur sont alignés à droite en bleu,
     * les autres messages sont alignés à gauche en noir.
     * 
     * Style appliqué :
     * - Message de l'utilisateur : aligné à droite, couleur bleue
     * - Message d'autrui : aligné à gauche, couleur noire
     * 
     * @param senderName le nom de l'expéditeur
     * @param message le contenu du message
     * @param isMe true si le message a été envoyé par l'utilisateur connecté
     */
    private void appendStyledMessage(String senderName, String message, boolean isMe) {
        StyledDocument doc = tchatArea.getStyledDocument();

        // On crée le style d'alignement
        SimpleAttributeSet style = new SimpleAttributeSet();
        StyleConstants.setAlignment(style, isMe ? StyleConstants.ALIGN_RIGHT : StyleConstants.ALIGN_LEFT);
        StyleConstants.setForeground(style, isMe ? new Color(0, 102, 204) : Color.BLACK); // Bleu pour soi
        StyleConstants.setFontFamily(style, "SansSerif");
        StyleConstants.setFontSize(style, 12);

        // On applique l'alignement au paragraphe
        int start = doc.getLength();
        String content = "[" + senderName + "] " + message + "\n";
        
        try {
            doc.insertString(start, content, style);
            doc.setParagraphAttributes(start, content.length(), style, false);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    /**
     * Affiche une boîte de dialogue pour créer une nouvelle conversation privée.
     * L'utilisateur saisit l'ID du client cible, puis :
     * 1. Validation de l'ID (entier positif, différent de l'utilisateur)
     * 2. Génération de l'ID de conversation normalisé (min-max)
     * 3. Ajout à la liste si non existante
     * 4. Chargement de l'historique
     * 
     * Gestion des erreurs :
     * - ID invalide (non numérique) : message d'erreur
     * - ID identique à l'utilisateur : avertissement
     * - Conversation déjà existante : sélection directe
     * 
     * @see #refreshConversations()
     * @see #loadHistory(String)
     */
    // Création de conversation
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

                // On vérifie d'abord si la conversation existe déjà dans la liste
                ConversationItem existingItem = null;
                for (int i = 0; i < convListModel.size(); i++) {
                    if (convListModel.getElementAt(i).getId().equals(newConvId)) {
                        existingItem = convListModel.getElementAt(i);
                        break;
                    }
                }

                if (existingItem == null) {
                    // Si elle n'existe pas, on l'ajoute
                    ConversationItem newItem = new ConversationItem(newConvId, 0);
                    convListModel.addElement(newItem);
                    existingItem = newItem;
                }

                // On la sélectionne (ceci déclenchera le listener et donc loadHistory)
                convList.setSelectedValue(existingItem, true);
                currentConv = newConvId;

            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "L'ID doit être un nombre valide.");
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Envoie le message saisi dans le champ de texte.
     * 
     * Logique d'envoi :
     * - Conversation "GENERAL" : appel à sendGeneralMessage()
     * - Conversation privée : extraction de l'ID cible, appel à sendDirectMessage()
     * 
     * Après envoi réussi :
     * - Vidage du champ de saisie
     * - Affichage local du message envoyé (style bleu, aligné à droite)
     * 
     * Gestion des erreurs :
     * - Message vide : ignoré silencieusement
     * - Aucune conversation sélectionnée : ignoré
     * - RemoteException : affichage du message d'erreur + alerte utilisateur
     * 
     * @see #extractTargetId(String)
     * @see interfaces.server.ChatService#sendGeneralMessage(int, String)
     * @see interfaces.server.ChatService#sendDirectMessage(int, int, String)
     */
    // --- LOGIQUE ---

    private void sendMessage() {
        String text = messageField.getText().trim();
        if (text.isEmpty()) return;

        try {
            if (currentConv.equals("GENERAL")) {
                tchatService.sendGeneralMessage(client.getClientId(), text);
            } else {
                // Pour les conversations privées, on extrait l'ID du destinataire du nom de conv (ex: "1-2")
                int targetId = extractTargetId(currentConv);
                tchatService.sendDirectMessage(client.getClientId(), targetId, text);
            }
            messageField.setText("");
            loadHistory(); // On rafraîchit pour voir son propre message
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erreur d'envoi : " + ex.getMessage());
        }
    }

    /**
     * Callback appelé lors de la réception d'un message direct.
     * Cette méthode est invoquée par le serveur RMI via ChatClient.
     * 
     * Comportement :
     * - Si la conversation concernée est active : affichage immédiat du message avec préfixe [NOUVEAU]
     * - Si la conversation est inactive : incrémentation du compteur de messages non lus
     * 
     * Synchronisation du curseur :
     * - Lors de l'affichage, appelle getHistory() pour marquer les messages comme lus côté serveur
     * 
     * Thread-safety :
     * - Utilise SwingUtilities.invokeLater() pour modifier l'IHM depuis le thread RMI
     * 
     * @param convId l'ID de la conversation (format "id1-id2")
     * @param sender le nom de l'expéditeur
     * @param message le contenu du message
     * @see #updateUnreadInList(String)
     * @see #appendStyledMessage(String, String, boolean)
     */
    public void onMessageReceived(String convId, String sender, String message) {
        SwingUtilities.invokeLater(() -> {
            if (currentConv != null && currentConv.equals(convId)) {
                appendStyledMessage(sender, "[NOUVEAU] " + message, false);
                
                // On rafraichit la conversation pour mettre à jour le curseur et éviter d'avoir le message marqué comme "non lu" à la prochaine ouverture
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

    /**
     * Callback appelé lors de la réception d'un message général (broadcast).
     * Cette méthode est invoquée par le serveur RMI via ChatClient.
     * 
     * Comportement :
     * - Si l'utilisateur est sur la conversation "GENERAL" : affichage immédiat avec préfixe [NOUVEAU]
     * - Sinon : incrémentation du compteur de messages non lus pour "GENERAL"
     * 
     * Synchronisation du curseur :
     * - Lors de l'affichage, appelle getHistory() pour marquer les messages comme lus côté serveur
     * 
     * Thread-safety :
     * - Utilise SwingUtilities.invokeLater() pour modifier l'IHM depuis le thread RMI
     * 
     * @param sender le nom de l'expéditeur du message général
     * @param message le contenu du message
     * @see #updateUnreadInList(String)
     * @see #appendStyledMessage(String, String, boolean)
     */
    public void onGeneralMessageReceived(String sender, String message) {
        SwingUtilities.invokeLater(() -> {
            // Si l'utilisateur est actuellement sur la conversation "GENERAL"
            if ("GENERAL".equals(currentConv)) {
                appendStyledMessage(sender, "[NOUVEAU] " + message, false);
                tchatArea.setCaretPosition(tchatArea.getDocument().getLength());
                
                // On rafraichit la conversation pour mettre à jour le curseur et éviter d'avoir le message marqué comme "non lu" à la prochaine ouverture
                try { 
                    tchatService.getHistory(client.getClientId(), "GENERAL"); 
                } catch(Exception e){
                    System.err.println("Erreur synchro curseur : " + e.getMessage());
                }
            } 
            else {
                // Sinon, on cherche "GENERAL" dans la liste pour augmenter le compteur
                updateUnreadInList("GENERAL");
            }
        });
    }

    /**
     * Met à jour le compteur de messages non lus pour une conversation donnée.
     * Si la conversation n'existe pas encore dans la liste, elle est créée avec unreadCount=1.
     * 
     * Logique :
     * - Recherche de l'élément correspondant à convId dans la liste
     * - Si trouvé : incrémentation du compteur + rafraîchissement visuel de la JList
     * - Si non trouvé : création d'un nouvel élément avec unreadCount=1
     * 
     * Thread-safety :
     * - Doit être appelée depuis le thread Swing (EDT)
     * 
     * @param convId l'ID de la conversation à mettre à jour ("GENERAL" ou "id1-id2")
     * @see ConversationItem#setUnreadCount(int)
     */
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

    /**
     * Charge et affiche l'historique de la conversation actuelle.
     * Récupère tous les messages de la conversation depuis le serveur,
     * marque les nouveaux messages avec le préfixe [NOUVEAU] basé sur le curseur de lecture.
     * 
     * Séquence d'exécution :
     * 1. Récupération du curseur de lecture (dernier message lu)
     * 2. Récupération de l'historique complet (getHistory marque aussi comme lu)
     * 3. Vidage de la zone de texte
     * 4. Affichage de tous les messages avec style approprié
     * 5. Scroll automatique vers le bas
     * 6. Activation du champ de saisie
     * 
     * Détection des nouveaux messages :
     * - Message avec id > lastReadCursor : préfixe [NOUVEAU]
     * 
     * @see interfaces.server.ChatService#getCursor(int, String)
     * @see interfaces.server.ChatService#getHistory(int, String)
     * @throws RemoteException si une erreur RMI survient
     */
    private void loadHistory() {
        if (currentConv == null) return;

        try {
            int myId = client.getClientId();
            int lastReadCursor = tchatService.getCursor(myId, currentConv);
            List<ChatMessage> history = tchatService.getHistory(myId, currentConv);
            
            tchatArea.setText(""); // On vide
            
            for (ChatMessage m : history) {
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

    /**
     * Rafraîchit la liste des conversations depuis le serveur.
     * Récupère toutes les conversations avec leurs compteurs de messages non lus.
     * 
     * Logique de synchronisation :
     * 1. Récupération de la liste depuis le serveur (getConversationsList)
     * 2. Vidage de la liste locale
     * 3. Ajout de toutes les conversations du serveur
     * 4. Vérification de la conversation actuelle :
     *    - Si elle existe côté serveur : sélection automatique
     *    - Si elle n'existe pas (nouvelle conversation sans message) : ajout manuel avec unreadCount=0
     * 
     * Cas particulier :
     * - Conversation nouvellement créée mais sans messages : ajoutée manuellement pour rester visible
     * 
     * @see interfaces.server.ChatService#getConversationsList(int)
     * @throws RemoteException si une erreur RMI survient
     */
    private void refreshConversations() {
        try {
            Map<String, Integer> convs = tchatService.getConversationsList(client.getClientId());
            
            convListModel.clear();

            // On remet les convs du serveur
            for (Map.Entry<String, Integer> entry : convs.entrySet()) {
                convListModel.addElement(new ConversationItem(entry.getKey(), entry.getValue()));
            }

            // Si la conv actuelle n'est pas dans ce que le serveur connaît
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

    /**
     * Configure les gestionnaires d'événements de l'interface.
     * 
     * Événements gérés :
     * 1. Sélection de conversation : charge l'historique, marque comme lu
     * 2. Envoi de message : appuie sur ENTER dans le champ de saisie
     * 3. Fermeture de fenêtre : déconnexion RMI propre
     * 
     * @throws RemoteException si une erreur RMI survient lors des listeners
     */
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

                        // Lorsque l'on ouvre une conversation, on considère que les messages sont lus, donc on remet le compteur à 0
                        item.setUnreadCount(0); 
                        convList.repaint();
                        
                        refreshConversations();
                    }
                }
            }
        });

        // Gestion de la fermeture propre de la fenêtre pour fermer la connexion RMI
        this.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
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