package client;

import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import interfaces.client.Accounting_itf;
import interfaces.server.Hello2;
import interfaces.server.Registry_itf;
import common.TchatMessage;

public class HelloClient2 implements Accounting_itf {

    private final String name;
    private volatile int clientId;

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
    public void receiveMessage(int fromClientId, String fromClientName, String message) throws RemoteException {
        System.out.println("\n[Message de " + fromClientName + " (id=" + fromClientId + ")] " + message);
    }

    @Override
    public void receiveGeneralMessage(int fromClientId, String fromClientName, String message) throws RemoteException {
        System.out.println("\n[Message de " + fromClientName + " (id=" + fromClientId + ") sur le tchat général] " + message);
    }

    private static void displayHistory(List<TchatMessage> history, String convId, int cursor, boolean displayAll) {
        if (history == null || history.isEmpty()) {
            System.out.println("\n--- Aucun message dans \"" + convId + "\" ---");
            return;
        }
        System.out.println("\n--- RÉCUPÉRATION DE L'HISTORIQUE (" + convId + ") ---");
        
        for (TchatMessage msg : history) {
            // C'est ici qu'on décide du format : [Pseudo] Message
            if (msg.id > cursor) {
                System.out.println("[NOUVEAU] [" + msg.senderName + "] " + msg.content);
            } else {
                if (displayAll) {
                    // On affiche tous les messages, même ceux déjà lus
                    System.out.println("          [" + msg.senderName + "] " + msg.content);
                }
            }
        }
        System.out.println("------------------------------------\n");
    }

    private static void displayConversationsList(Hello2 h2, int myId) {
        try {
            Map<String, Boolean> convs = h2.getConversationsList(myId);
            System.out.println("\n=== VOS CONVERSATIONS ===");
            System.out.println("GENERAL (tchat général)");
            if (!convs.isEmpty()) {
                for (Map.Entry<String, Boolean> entry : convs.entrySet()) {
                    String name = entry.getKey();
                    Boolean unread = entry.getValue();
                    
                    if(unread) {
                        name += " (MESSAGES NON LUS)";
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
                "1. Envoyer message au tchat général\n" +
                "2. Envoyer message\n" +
                "3. Créer groupe\n" +
                "4. Supprimer groupe\n" +
                "5. Supprimer membre d'un groupe\n" +
                "6. Ajouter membre à un groupe\n" +
                "7. Changer pseudo\n" +
                "8. Changer nom de groupe\n" +
                "9. Infos groupe\n" +
                "10. Récupérer historique\n" +
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

    private static void runMenu(HelloClient2 client, Hello2 h2) {
        Scanner scanner = new Scanner(System.in);
        boolean running = true;
        while (running) {
            displayMenu();
            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1":{
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
                case "2":{
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
                case "3":
                case "4":
                case "5":
                case "6":
                case "7":
                case "8":
                case "9":
                    System.out.println("Fonctionnalité non implémentée pour le moment.");
                    break;
                case "10":
                    displayConversationsList(h2, client.clientId);
                    System.out.print("Entrez le nom de la conversation pour récupérer l'historique (ex: GENERAL ou clientId1-clientId2) : ");
                    String convId = scanner.nextLine().trim();
                    System.out.println("1. Historique complet\n" +
                            "2. Messages non lus seulement\n" +
                            "Choix : ");
                    String histChoice = scanner.nextLine().trim();
                    while (!histChoice.equals("1") && !histChoice.equals("2")) {
                        System.out.println("Choix invalide.");
                        System.out.println("1. Historique complet\n" +
                            "2. Messages non lus seulement\n" +
                            "Choix : ");
                        histChoice = scanner.nextLine().trim();
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

    public static void main(String [] args) {
		try {
            if (args.length < 4) {
                System.out.println("Usage: java HelloClient2 <rmiregistry host> <rmiregistry port> <client name> <client id>");
                System.out.println("Si c'est votre première connexion, utilisez l'id 0 !");
                return;
            }

            String host = args[0];
            int port = Integer.parseInt(args[1]);
            int requestedClientId = Integer.parseInt(args[3]);

            // Le client récupère le registre RMI.
            Registry registry = LocateRegistry.getRegistry(host, port);

            // Le client crée son objet local puis l'export en stub distant
            HelloClient2 client = new HelloClient2(args[2]);

            Accounting_itf client_stub = null;

            try {
                // Exportation de l'objet
                // On publie l'objet client pour qu'on puisse l'appeler à distance (pour les callbacks du serveur)
                client_stub = (Accounting_itf) UnicastRemoteObject.exportObject(client, 0);
            } catch (RemoteException re) {
                System.err.println("exportObject failed:");
                re.printStackTrace();
                return;
            }

            // Le client récupère le stub serveur du registre
            Registry_itf registry_stub = (Registry_itf) registry.lookup("RegistryService");
            if (registry_stub == null) {
                System.err.println("registry_stub is null");
                return;
            }

            if (client_stub == null) {
                System.err.println("client_stub is null before register()");
                return;
            }

            // Le client s'enregistre/se connecte au serveur.
            int assignedId;
            try {
                assignedId = registry_stub.register(client_stub, client.name, requestedClientId);
            } catch (RemoteException e) {
                System.out.println("Connexion refusée : " + extractRemoteMessage(e));
                System.out.println("Utilisez l'id 0 si c'est votre première connexion.");
                try {
                    UnicastRemoteObject.unexportObject(client, true);
                } catch (Exception ignored) {
                }
                return;
            }
            if (client.clientId <= 0) {
                client.clientId = assignedId;
            }
            System.out.println("Je suis " + client.name + " et mon id est " + client.clientId + ".");
            System.out.println("Je me suis enregistré auprès du serveur.");

            // Le client récupère le stub du service de chat (accès aux méthodes de Hello2Service).
            Hello2 h2 = (Hello2) registry.lookup("Hello2Service");

            // Récupération de l'historique général à la connexion
            try {
                // On demande l'historique pour la salle "GENERAL"
                java.util.List<TchatMessage> generalHistory = h2.getHistory(client.clientId, "GENERAL");
                displayHistory(generalHistory, "GENERAL", -1, true);
            } catch (RemoteException e) {
                System.err.println("Impossible de récupérer l'historique : " + e.getMessage());
            }

            runMenu(client, h2);

            try {
                // On sésenregistre le client.
                UnicastRemoteObject.unexportObject(client, true);
            } catch (Exception ignored) {
            }

        } catch (Exception e) { 
			e.printStackTrace();
		}
    }
}
