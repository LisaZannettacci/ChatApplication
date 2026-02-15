package client;

import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.util.Scanner;
import interfaces.client.Accounting_itf;
import interfaces.server.Hello2;
import interfaces.server.Registry_itf;

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

    private static void displayMenu() {
        System.out.println("\n==== MENU ====\n" +
                "1. Envoyer message\n" +
                "2. Créer groupe\n" +
                "3. Supprimer groupe\n" +
                "4. Supprimer membre d'un groupe\n" +
                "5. Ajouter membre à un groupe\n" +
                "6. Changer pseudo\n" +
                "7. Changer nom de groupe\n" +
                "8. Infos groupe\n" +
                "0. Quitter\n" +
                "Choix : ");
    }

    private static void runMenu(HelloClient2 client, Hello2 h2) {
        Scanner scanner = new Scanner(System.in);
        boolean running = true;
        while (running) {
            displayMenu();
            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1":
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
                        System.out.println("Échec d'envoi : " + e.getMessage());
                    }
                    break;
                case "2":
                case "3":
                case "4":
                case "5":
                case "6":
                case "7":
                case "8":
                    System.out.println("Fonctionnalité non implémentée pour le moment.");
                    break;
                case "0":
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
            if (args.length < 3) {
                System.out.println("Usage: java HelloClient2 <rmiregistry host> <rmiregistry port> <client name>");
                return;
            }

            String host = args[0];
            int port = Integer.parseInt(args[1]);

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

            // Le client s'enregistre auprès du serveur pour obtenir un identifiant unique
            int assignedId = registry_stub.register(client_stub, client.name);
            if (client.clientId <= 0) {
                client.clientId = assignedId;
            }
            System.out.println("Je suis " + client.name + " et mon id est " + client.clientId + ".");
            System.out.println("Je me suis enregistré auprès du serveur.");

            // Le client récupère le stub du service de chat (accès aux méthodes de Hello2Service).
            Hello2 h2 = (Hello2) registry.lookup("Hello2Service");
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
