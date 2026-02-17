package client;

import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import javax.swing.SwingUtilities;
import interfaces.client.Accounting_itf;
import interfaces.server.Hello2;
import interfaces.server.Registry_itf;
import common.TchatMessage;
import ihm.LoginFrame;
import ihm.TchatFrame;

public class HelloClient2 implements Accounting_itf {

    private final String name;
    private volatile int clientId;
    private Hello2 tchatService;
    private TchatFrame ihm;

    HelloClient2(String name) {
        this.name = name;
        this.clientId = -1;
    }
    
	public void numberOfCalls(int number) throws RemoteException {
        System.out.println("Notification du serveur : Vous avez effectué " + number + " appels.");
    }

    @Override
    public void setClientId(int clientId) throws RemoteException {
        this.clientId = clientId;
    }

    @Override
    public int getClientId() throws RemoteException {
        return this.clientId;
    }

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

    private static void displayHistory(List<TchatMessage> history, String convId, int cursor, boolean displayAll) {
        if (history == null || history.isEmpty()) {
            System.out.println("\n--- Aucun message dans \"" + convId + "\" ---");
            return;
        }
        System.out.println("\n--- RÉCUPÉRATION DE L'HISTORIQUE (" + convId + ") ---");
        boolean hasNewMessages = false;
        if (history != null) {
            for (TchatMessage msg : history) {
                if (msg.id > cursor) {
                    hasNewMessages = true;
                    break;
                }
            }
        }
        
        for (TchatMessage msg : history) {
            
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

    private static void displayConversationsList(Hello2 h2, int myId) {
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

    public String getName() {
        return name;
    }

    public void setIhm(TchatFrame ihm) {
        this.ihm = ihm;
    }

    private static void runMenu(HelloClient2 client, Hello2 h2) {
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
                        List<TchatMessage> history;
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
    }

    public Hello2 getTchatService() {
        return this.tchatService;
    }

    public static HelloClient2 launchConnection(String host, int port, String pseudo, int requestedId) throws Exception {
        Registry registry = LocateRegistry.getRegistry(host, port);
        Registry_itf registry_stub = (Registry_itf) registry.lookup("RegistryService");
        Hello2 h2 = (Hello2) registry.lookup("Hello2Service");


        HelloClient2 client = new HelloClient2(pseudo);
        client.tchatService = h2; // On stocke le service dans l'instance
        
        Accounting_itf client_stub = (Accounting_itf) UnicastRemoteObject.exportObject(client, 0);

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
                    new LoginFrame(host, port).setVisible(true);
                });
            }
            else{
              try {
                    String pseudo = args[2];
                    int id = Integer.parseInt(args[3]);

                    HelloClient2 client = launchConnection(host, port, pseudo, id);
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
