package client;

import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import javax.swing.SwingUtilities;
import interfaces.client.ChatClientCallback;
import interfaces.server.ChatService;
import interfaces.server.ClientRegistry;
import common.ChatMessage;
import ihm.LoginDialog;
import ihm.ChatFrame;

/**
 * Classe principale du client de chat RMI.
 * Cette classe implémente l'interface ChatClientCallback pour recevoir les callbacks
 * du serveur (messages entrants, notifications).
 * Elle gère à la fois le mode console (menu interactif) et le mode IHM (interface graphique Swing).
 * 
 * Variables membres :
 * - name : le pseudo du client.
 * - clientId : l'identifiant unique attribué par le serveur.
 * - tchatService : référence au service RMI distant (ChatService) pour envoyer messages et récupérer historiques
 * - ihm : référence optionnelle à l'interface graphique (ChatFrame), null en mode console
 * 
 * @see ChatClientCallback
 * @see ChatService
 * @see ChatFrame
 */
public class ChatClient implements ChatClientCallback {

    /** Le nom (pseudo) du client, défini à la construction */
    private final String name;
    
    /** L'identifiant unique du client, attribué par le serveur. -1 si non encore assigné */
    private volatile int clientId;
    
    /** Référence au service de chat distant (objet RMI) */
    private ChatService tchatService;
    
    /** Référence à l'interface graphique (null si mode console) */
    private ChatFrame ihm;

    /**
     * Constructeur privé de ChatClient.
     * Initialise le client avec un nom et un ID par défaut (-1).
     * 
     * @param name le pseudo du client
     */
    ChatClient(String name) {
        this.name = name;
        this.clientId = -1;
    }
    
    /**
     * Notification du serveur indiquant le nombre d'appels effectués.
     * Callback appelé périodiquement par le serveur pour informer le client
     * du nombre d'invocations de méthodes RMI qu'il a effectuées.
     * 
     * @param number le nombre total d'appels effectués
     * @throws RemoteException si une erreur de communication RMI survient
     */
	public void numberOfCalls(int number) throws RemoteException {
        System.out.println("Notification du serveur : Vous avez effectué " + number + " appels.");
    }

    /**
     * Définit l'identifiant du client.
     * Callback appelé par le serveur lors de l'enregistrement/reconnexion
     * pour communiquer l'ID attribué ou validé.
     * 
     * @param clientId l'identifiant unique attribué par le serveur
     * @throws RemoteException si une erreur de communication RMI survient
     */
    @Override
    public void setClientId(int clientId) throws RemoteException {
        this.clientId = clientId;
    }

    /**
     * Retourne l'identifiant actuel du client.
     * 
     * @return l'ID du client, ou -1 si non encore assigné
     * @throws RemoteException si une erreur de communication RMI survient
     */
    @Override
    public int getClientId() throws RemoteException {
        return this.clientId;
    }

    /**
     * Callback appelé par le serveur lors de la réception d'un message direct.
     * Si l'IHM est active, délègue l'affichage à ChatFrame.
     * Sinon, affiche le message dans la console et met à jour le curseur de lecture.
     * 
     * @param fromClientId l'ID de l'expéditeur du message
     * @param fromClientName le pseudo de l'expéditeur
     * @param message le contenu du message reçu
     */
    @Override
    public void receiveMessage(int fromClientId, String fromClientName, String message) {
        if (ihm != null) {
            String convId = (fromClientId < this.clientId) ? fromClientId + "-" + this.clientId : this.clientId + "-" + fromClientId;
            ihm.onMessageReceived(convId, fromClientName, message);
        } else {
            System.out.println("\n[Message de " + fromClientName + " (id=" + fromClientId + ")] " + message);
            
            // on informe le serveur que ce message a été "lu" pour mettre à jour le curseur côté serveur
            try {
                String convId = (fromClientId < this.clientId) ? fromClientId + "-" + this.clientId : this.clientId + "-" + fromClientId;
                tchatService.getHistory(clientId, convId);
            } catch (RemoteException e) {
                System.out.println("Erreur lors de la mise à jour du curseur : " + e.getMessage());
            }
        }
    }

    /**
     * Callback appelé par le serveur lors de la réception d'un message sur le tchat général.
     * Si l'IHM est active, délègue l'affichage à ChatFrame.
     * Sinon, affiche le message dans la console et met à jour le curseur de lecture.
     * 
     * @param fromClientId l'ID de l'expéditeur du message
     * @param fromClientName le pseudo de l'expéditeur
     * @param message le contenu du message reçu
     * @throws RemoteException si une erreur de communication RMI survient
     */
    @Override
    public void receiveGeneralMessage(int fromClientId, String fromClientName, String message) throws RemoteException {
        if (ihm != null) {
            ihm.onGeneralMessageReceived(fromClientName, message);
        } else {
            System.out.println("\n[Message de " + fromClientName + " (id=" + fromClientId + ") sur le tchat général] " + message);
        
            // on informe le serveur que ce message a été "lu" pour mettre à jour le curseur côté serveur
            try {
                tchatService.getHistory(clientId, "GENERAL");
            } catch (RemoteException e) {
                System.out.println("Erreur lors de la mise à jour du curseur : " + e.getMessage());
            }
        }
    }

    /**
     * Affiche l'historique d'une conversation dans la console.
     * Distingue les messages lus (curseur < id) des nouveaux messages (curseur >= id).
     * 
     * @param history la liste des messages de la conversation
     * @param convId l'identifiant de la conversation (ex: "GENERAL" ou "1-2")
     * @param cursor la position du curseur de lecture de l'utilisateur
     * @param displayAll true pour afficher tous les messages, false pour afficher seulement les nouveaux
     */
    private static void displayHistory(List<ChatMessage> history, String convId, int cursor, boolean displayAll) {
        if (history == null || history.isEmpty()) {
            System.out.println("\n--- Aucun message dans \"" + convId + "\" ---");
            return;
        }
        System.out.println("\n--- RÉCUPÉRATION DE L'HISTORIQUE (" + convId + ") ---");
        boolean hasNewMessages = false;
        if (history != null) {
            for (ChatMessage msg : history) {
                if (msg.id > cursor) {
                    hasNewMessages = true;
                    break;
                }
            }
        }
        
        for (ChatMessage msg : history) {
            
            if (msg.id > cursor) { // il y a des messages non lus, on les marque
                System.out.println("[NOUVEAU] [" + msg.senderName + "] " + msg.content);
            } else {
                if (displayAll) {
                    // On affiche tous les messages, même ceux déjà lus au format "[Pseudo] Message"
                    if (!hasNewMessages) {
                        System.out.println("[" + msg.senderName + "] " + msg.content);
                    }
                    else {
                        System.out.println("          [" + msg.senderName + "] " + msg.content); // on laisse un espace pour aligner les messages déjà lus avec les nouveaux
                    }
                }
            }
        }
        System.out.println("------------------------------------\n");
    }

    /**
     * Affiche la liste des conversations de l'utilisateur avec leur compteur de messages non lus.
     * Interroge le serveur via RMI pour obtenir la liste des conversations.
     * 
     * @param h2 le service de chat distant
     * @param myId l'identifiant du client demandeur
     */
    private static void displayConversationsList(ChatService h2, int myId) {
        try {
            Map<String, Integer> convs = h2.getConversationsList(myId);
            System.out.println("\n=== VOS CONVERSATIONS ===");
            System.out.println("GENERAL (tchat général)");
            if (!convs.isEmpty()) {
                for (Map.Entry<String, Integer> entry : convs.entrySet()) {
                    String name = entry.getKey();
                    int unreadCount = entry.getValue();
                    
                    if(unreadCount == 1) {
                        name += " (" + unreadCount + " message non lu)";
                    }
                    else if(unreadCount > 1) {
                        name += " (" + unreadCount + " messages non lus)";
                    }
                    System.out.println("- " + name);
                }
            }
            System.out.println("=========================\n");
        } catch (RemoteException e) {
            System.out.println("Erreur lors de la récupération de la liste.");
        }
    }

    /**
     * Affiche le menu interactif du mode console.
     * Liste les fonctionnalités disponibles pour l'utilisateur.
     */
    private static void displayMenu() {
        System.out.println("\n==== MENU ====\n" +
                "2. Envoyer message au tchat général\n" +
                "3. Envoyer message\n" +
                "4. Créer groupe\n" +
                "5. Supprimer groupe\n" +
                "6. Supprimer membre d'un groupe\n" +
                "7. Ajouter membre à un groupe\n" +
                "8. Changer pseudo\n" +
                "9. Changer nom de groupe\n" +
                "10. Infos groupe\n" +
                "11. Récupérer historique\n" +
                "0. Quitter\n" +
                "Choix : ");
    }

    /**
     * Extrait le message d'erreur le plus profond d'une RemoteException.
     * Parcourt la chaîne des causes pour trouver le message d'origine.
     * 
     * @param e l'exception RMI dont on veut extraire le message
     * @return le message d'erreur le plus spécifique, ou "Erreur distante." si aucun message n'est disponible
     */
    private static String extractRemoteMessage(RemoteException e) {
        Throwable current = e;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        if (current.getMessage() != null && !current.getMessage().trim().isEmpty()) {
            return current.getMessage();
        }
        if (e.getMessage() != null && !e.getMessage().trim().isEmpty()) {
            return e.getMessage();
        }
        return "Erreur distante.";
    }

    /**
     * Retourne le nom (pseudo) du client.
     * 
     * @return le pseudo du client
     */
    public String getName() {
        return name;
    }

    /**
     * Définit la référence à l'interface graphique.
     * Permet de basculer du mode console au mode IHM.
     * 
     * @param ihm la fenêtre de chat (ChatFrame) à associer
     */
    public void setIhm(ChatFrame ihm) {
        this.ihm = ihm;
    }

    /**
     * Boucle principale du menu interactif en mode console.
     * Affiche le menu, lit les choix de l'utilisateur et appelle les méthodes RMI correspondantes.
     * Gère les options : envoi de messages (général/direct), récupération d'historique, déconnexion.
     * 
     * @param client l'instance du client connecté
     * @param h2 le service de chat distant
     */
    private static void runMenu(ChatClient client, ChatService h2) {
        Scanner scanner = new Scanner(System.in);
        boolean running = true;
        String choice = "";
        while (running) {

            //////////// A voir si on garde : Petit menu d'accueil pour éviter d'afficher le menu à chaque message reçu
            if(!choice.equals("1")){
                System.out.println("1. Ouvrir le menu\n" +
                                    "0. Quitter\n" +
                                    "Choix : ");
            }
            
            /////////////
            
            choice = scanner.nextLine().trim();
            switch (choice) {
                case "1":
                    displayMenu();
                    break;
                case "2":{
                    try {
                        System.out.print("Message : ");
                        String content = scanner.nextLine();
                        String status = h2.sendGeneralMessage(client.clientId, content);
                        System.out.println(status);
                    } catch (RemoteException e) {
                        System.out.println("Échec d'envoi : " + extractRemoteMessage(e));
                    }

                    break;
                }
                case "3":{
                    try {
                        System.out.print("ID du client destinataire : ");
                        int targetId = Integer.parseInt(scanner.nextLine().trim());
                        System.out.print("Message : ");
                        String content = scanner.nextLine();
                        String status = h2.sendDirectMessage(client.clientId, targetId, content);
                        System.out.println(status);
                    } catch (NumberFormatException e) {
                        System.out.println("ID invalide.");
                    } catch (RemoteException e) {
                        System.out.println("Échec d'envoi : " + extractRemoteMessage(e));
                    }
                    break;
                }
                case "4":
                case "5":
                case "6":
                case "7":
                case "8":
                case "9":
                case "10":
                    System.out.println("Fonctionnalité non implémentée pour le moment.");
                    break;
                case "11":
                    displayConversationsList(h2, client.clientId);
                    System.out.print("Entrez le nom de la conversation pour récupérer l'historique (ex: GENERAL ou clientId1-clientId2) : ");
                    String convId = scanner.nextLine().trim();
                    System.out.println("Voulez-vous afficher tous les messages ou seulement les messages non lus ?");
                    System.out.println("1. Historique complet\n" +
                            "2. Messages non lus seulement\n" +
                            "0. Annuler\n" +
                            "Choix : ");
                    String histChoice = scanner.nextLine().trim();
                    while (!histChoice.equals("1") && !histChoice.equals("2") && !histChoice.equals("0")) {
                        System.out.println("Choix invalide.");
                        System.out.println("1. Historique complet\n" +
                            "2. Messages non lus seulement\n" +
                            "0. Annuler\n" +
                            "Choix : ");
                        histChoice = scanner.nextLine().trim();
                    }
                    if (histChoice.equals("0")) {
                        System.out.println("Récupération d'historique annulée.\n");
                        break;
                    }

                    int cursor;
                    try {                 
                        cursor = h2.getCursor(client.clientId, convId);
                    } catch (RemoteException e) {
                        System.out.println("Erreur curseur : " + extractRemoteMessage(e));
                        break;
                    }
                    
                    try {           
                        boolean displayAll = histChoice.equals("1"); 
                        List<ChatMessage> history;
                        history = h2.getHistory(client.clientId, convId);

                        displayHistory(history, convId, cursor, displayAll);
                        
                    } catch (RemoteException e) {
                        System.out.println("Impossible de récupérer l'historique : " + extractRemoteMessage(e
));                 }
                    break;
                case "0":
                    try {
                        h2.disconnect(client.clientId);
                    } catch (RemoteException e) {
                        System.out.println("Déconnexion côté serveur incomplète : " + e.getMessage());
                    }
                    running = false;
                    System.out.println("Déconnexion...");
                    break;
                default:
                    System.out.println("Choix invalide.");
            }
        }
        scanner.close(); // Fermeture du scanner pour éviter les fuites de ressources
    }

    /**
     * Retourne la référence au service de chat distant.
     * 
     * @return l'objet RMI ChatService
     */
    public ChatService getTchatService() {
        return this.tchatService;
    }

    /**
     * Établit une connexion RMI avec le serveur et enregistre le client.
     * 
     * @param host l'adresse du serveur RMI (ex: "localhost" ou une IP)
     * @param port le port du registre RMI (ex: 6090)
     * @param pseudo le nom souhaité par le client
     * @param requestedId l'ID demandé (0 pour nouvelle connexion, ID existant pour reconnexion)
     * @return une instance de ChatClient connectée et enregistrée
     * @throws Exception si la connexion ou l'enregistrement échoue (RemoteException, etc.)
     */
    public static ChatClient launchConnection(String host, int port, String pseudo, int requestedId) throws Exception {
        Registry registry = LocateRegistry.getRegistry(host, port);
        ClientRegistry registry_stub = (ClientRegistry) registry.lookup("RegistryService");
        ChatService h2 = (ChatService) registry.lookup("Hello2Service");


        ChatClient client = new ChatClient(pseudo);
        client.tchatService = h2; // On stocke le service dans l'instance
        
        ChatClientCallback client_stub = (ChatClientCallback) UnicastRemoteObject.exportObject(client, 0);

        // On s'enregistre auprès du serveur et on récupère l'ID assigné
        try {
            int assignedId = registry_stub.register(client_stub, pseudo, requestedId);
            client.clientId = assignedId;
            System.out.println("Connecté avec l'ID : " + assignedId);
            return client;
        } catch (RemoteException e) {
            UnicastRemoteObject.unexportObject(client, true);
            throw e; // On propage l'erreur pour que l'IHM puisse afficher un message
        }
    }

    /**
     * Ferme proprement la connexion du client.
     * Appelle la méthode disconnect du serveur, affiche un message et termine l'application.
     * Cette méthode est appelée par l'IHM lors de la fermeture de la fenêtre.
     */
    public void close() {
        try {
            if (tchatService != null && clientId != -1) {
                System.out.println("Déconnexion du serveur...");
                tchatService.disconnect(clientId);
            }
        } catch (RemoteException e) {
            System.err.println("Erreur lors de la déconnexion RMI : " + e.getMessage());
        } finally {
            // On quitte l'application
            System.out.println("Fermeture de l'application.");
            System.exit(0);
        }
    }

    /**
     * Point d'entrée principal du client.
     * Analyse les arguments de ligne de commande pour déterminer le mode (IHM ou console).
     * Lance l'interface graphique si --ihm est présent ou si les arguments sont insuffisants.
     * Sinon, lance le mode console avec menu interactif.
     * 
     * @param args arguments en ligne de commande : 
     *             - args[0] : hostname du serveur
     *             - args[1] : port du registre RMI
     *             - args[2] : pseudo (mode console uniquement)
     *             - args[3] : ID client (0 pour nouveau, sinon ID existant)
     *             - --ihm : option pour forcer le mode graphique
     */
    public static void main(String [] args) {
		try {
            boolean useIHM = false;
            for (String arg : args) {
                if (arg.equalsIgnoreCase("--ihm")) {
                    useIHM = true;
                    break;
                }
            }
            String host = args[0];
            int port = Integer.parseInt(args[1]);

            if (useIHM || args.length < 4) { // Si on a l'option --ihm ou pas assez d'arguments pour le mode console, on lance l'IHM
                System.out.println("Lancement du mode Graphique...");
                SwingUtilities.invokeLater(() -> {
                    new LoginDialog(host, port).setVisible(true);
                });
            }
            else{
              try {
                    String pseudo = args[2];
                    int id = Integer.parseInt(args[3]);

                    ChatClient client = launchConnection(host, port, pseudo, id);
                    System.out.println("Je suis " + client.name + " et mon id est " + client.clientId + ".");
                    System.out.println("Je me suis enregistré auprès du serveur.");

                    if (client != null) {
                        runMenu(client, client.tchatService);
                        UnicastRemoteObject.unexportObject(client, true);
                    }

                    try {
                        // On désenregistre le client.
                        UnicastRemoteObject.unexportObject(client, true);
                    } catch (Exception ignored) {
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }  
            }
            
        } catch (Exception e) { 
			e.printStackTrace();
		}
    }
}
