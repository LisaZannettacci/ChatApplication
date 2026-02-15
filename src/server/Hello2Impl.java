package server;

import java.rmi.*;
import java.util.HashMap;
import java.util.Map;
import interfaces.client.Accounting_itf;
import interfaces.server.Hello2;
import interfaces.server.Registry_itf;

// Côté serveur
public class Hello2Impl implements Hello2, Registry_itf {
    
    private String message;
    private final Map<Accounting_itf, Integer> clientIds; // Associe un stub client à un identifiant unique.
    private final Map<Integer, Accounting_itf> clientsById; // Associe un identifiant client à son stub (pour les callbacks).
    private final Map<Integer, String> clientNamesById; // Associe un identifiant client à son pseudo (pour les notifications et messages).
    private final Map<Integer, Integer> clientCalls; // Nombre d'appels sayHello par client.
    private int nextClientId;
    private int LIMITE_AVANT_NOTIFICATION = 10;

    public Hello2Impl(String message) {
        this.message = message;
        this.clientIds = new HashMap<>();
        this.clientsById = new HashMap<>();
        this.clientNamesById = new HashMap<>();
        this.clientCalls = new HashMap<>();
        this.nextClientId = 1;
    }

    @Override
    public synchronized int register(Accounting_itf client, String clientName) throws RemoteException {
        if (client == null) {
            throw new RemoteException("Client null");
        }
        if (clientIds.containsKey(client)) {
            return clientIds.get(client);
        }
        int clientId = nextClientId++;
        
        // On met à jour le pseudo de l'utilisateur.
        // trim() => on supprime les espaces en début et fin de chaîne.
        String safeClientName = (clientName == null || clientName.trim().isEmpty())
            ? "Client-" + clientId
            : clientName.trim();

        clientIds.put(client, clientId);
        clientsById.put(clientId, client);
        clientNamesById.put(clientId, safeClientName);
        clientCalls.put(clientId, 0);
        client.setClientId(clientId);

        System.out.println("Nouveau client enregistré: id=" + clientId + ", pseudo=" + safeClientName);
        return clientId;
    }
    
    @Override
    public synchronized String sayHello(Accounting_itf client) throws RemoteException {
        if (client != null && clientIds.containsKey(client)) {
            int clientId = clientIds.get(client);
            int nb_appels = clientCalls.get(clientId) + 1;
            clientCalls.put(clientId, nb_appels);

            if (nb_appels % LIMITE_AVANT_NOTIFICATION == 0) {
                try {
                    client.numberOfCalls(nb_appels);

                } catch (RemoteException e) {
                    System.err.println("Erreur lors de la notification du client : " + e.getMessage());
                }
            }
        }
		else { 
			throw new RemoteException("Client non enregistré");
		} 
		return message;
    }

    @Override
    public synchronized String sendDirectMessage(int fromClientId, int toClientId, String message) throws RemoteException {
        if (!clientsById.containsKey(fromClientId)) {
            throw new RemoteException("Expéditeur inconnu: id=" + fromClientId);
        }
        if (!clientsById.containsKey(toClientId)) {
            throw new RemoteException("Destinataire inconnu: id=" + toClientId);
        }
        String content = (message == null) ? "" : message.trim();
        if (content.isEmpty()) {
            throw new RemoteException("Le message ne peut pas être vide.");
        }

        Accounting_itf targetClient = clientsById.get(toClientId);
        String fromClientName = clientNamesById.get(fromClientId);
        String toClientName = clientNamesById.get(toClientId);

        // On appelle la méthode de callback du client destinataire pour lui transmettre le message.
        targetClient.receiveMessage(fromClientId, fromClientName, content);
        return "Message envoyé à " + toClientName + " (id=" + toClientId + ").";
    }
}
