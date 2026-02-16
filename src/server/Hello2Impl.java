package server;

import java.rmi.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import common.TchatMessage;
import interfaces.client.Accounting_itf;
import interfaces.server.Hello2;
import interfaces.server.Registry_itf;

// Côté serveur
public class Hello2Impl implements Hello2, Registry_itf {
    
    private String message;

    private final Map<Accounting_itf, Integer> map_stubClient_id; // Associe un stub client à un identifiant unique.
    private final Map<Integer, Accounting_itf> map_id_stubClient; // Associe un identifiant client à son stub (pour les callbacks).
    private final Map<Integer, String> map_id_pseudo; // Associe un identifiant client à son pseudo (pour les notifications et messages).
    private final Map<Integer, Integer> map_nb_sayHello_id; // Associe un identifiant client à son nombre d'appels sayHello.
    private final ServerStateStore stateStore;
    // Dans Hello2Impl.java
    private final Map<String, List<TchatMessage>> allHistories = new HashMap<>(); // Associe un nom de conversation à sa liste de messages.
                                                                                 // Le nom de conversation peut être "GENERAL" pour le tchat général, 
                                                                                 // ou "clientId1-clientId2" pour un message direct entre deux clients,
                                                                                 // avec clientId1 < clientId2 pour garantir un ordre logique.
    private final Map<String, Map<Integer, Integer>> readCursors = new HashMap<>();  // Associe un nom de conversation à une map qui associe chaque clientId 
                                                                                     // au dernier index de message lu dans allHistories pour cette conversation.
    private int nextClientId;
    private int LIMITE_AVANT_NOTIFICATION = 10;

    public Hello2Impl(String message) {
        this.message = message;
        this.map_stubClient_id = new HashMap<>();
        this.map_id_stubClient = new HashMap<>();
        this.map_id_pseudo = new HashMap<>();
        this.map_nb_sayHello_id = new HashMap<>();
        this.stateStore = new ServerStateStore("server_state.json");

        // On restaure uniquement nextClientId, map_id_pseudo, allHistories et readCursors.
        // Les autres maps restent vides au redémarrage (état "en ligne" non persistant).
        ServerStateStore.LoadedState loaded = stateStore.load();
        this.nextClientId = loaded.getNextClientId();
        this.map_id_pseudo.putAll(loaded.getMapIdPseudo());
        this.allHistories.putAll(loaded.getAllHistories());
        this.readCursors.putAll(loaded.getReadCursors());

        System.out.println(
            "Etat charge: " + map_id_pseudo.size() + " client(s) connu(s), prochain id=" + nextClientId
        );
    }

    private String normalizeClientName(String clientName, int clientId) {
        // trim() => on supprime les espaces en début et fin de chaîne.
        return (clientName == null || clientName.trim().isEmpty())
            ? "Client-" + clientId
            : clientName.trim();
    }

    private synchronized void updateCursor(int userId, String convId, int lastMessageId) {
        Map<Integer, Integer> convCursors = readCursors.get(convId);
        if (convCursors == null) { // si la conversation n'avait pas encore de curseurs, on crée une nouvelle map pour cette conversation
            convCursors = new HashMap<>();
            // On l'ajoute dans la Map globale pour ne pas la perdre
            readCursors.put(convId, convCursors);
        }

        // 2. On enregistre que l'utilisateur 'userId' a lu jusqu'au message 'lastMessageId'
        convCursors.put(userId, lastMessageId);
    }

    @Override
    public synchronized int register(Accounting_itf client, String clientName, int requestedClientId) throws RemoteException {
        if (client == null) {
            throw new RemoteException("Client null");
        }

        // Cas 1: première connexion (id=0) -> le serveur attribue un nouvel ID.
        if (requestedClientId == 0) {
            int newClientId = nextClientId++;
            String safeClientName = normalizeClientName(clientName, newClientId);

            map_stubClient_id.put(client, newClientId);
            map_id_stubClient.put(newClientId, client);
            map_id_pseudo.put(newClientId, safeClientName);
            map_nb_sayHello_id.put(newClientId, 0);
            client.setClientId(newClientId);
            saveState();

            System.out.println("Nouveau client enregistré: id=" + newClientId + ", pseudo=" + safeClientName);
            return newClientId;
        }

        // Cas 2: reconnexion demandée avec un ID existant (>0).
        // On rejette les valeurs négatives.
        if (requestedClientId < 0) {
            throw new RemoteException("ID client invalide: " + requestedClientId);
        }

        // L'ID doit déjà exister: sinon c'est une usurpation ou une erreur utilisateur.
        if (!map_id_pseudo.containsKey(requestedClientId)) {
            throw new RemoteException("ID client inconnu: " + requestedClientId + ". Utiliser 0 pour une première connexion.");
        }

        // Vérification d'appartenance ID <-> pseudo.
        // Un client ne peut pas se connecter avec l'ID d'un autre pseudo.
        String requestedName = normalizeClientName(clientName, requestedClientId);
        String ownerName = map_id_pseudo.get(requestedClientId);
        if (!ownerName.equals(requestedName)) {
            throw new RemoteException(
                "ID " + requestedClientId + " appartient à '" + ownerName + "'. Utilisez votre propre ID ou 0.");
        }

        // Si un stub actif est déjà branché à cet ID, on refuse la 2e session concurrente.
        Accounting_itf previousStub = map_id_stubClient.get(requestedClientId);
        if (previousStub != null && previousStub != client) {
            throw new RemoteException("ID déjà connecté: " + requestedClientId + ". Déconnectez d'abord l'autre session.");
        }

        // Si ce stub était lié à un autre ID, on retire l'ancienne relation.
        Integer previousIdForThisStub = map_stubClient_id.get(client);
        if (previousIdForThisStub != null && previousIdForThisStub != requestedClientId) {
            map_id_stubClient.remove(previousIdForThisStub);
        }

        // Mise à jour des maps pour activer la session.
        map_stubClient_id.put(client, requestedClientId);
        map_id_stubClient.put(requestedClientId, client);
        map_id_pseudo.put(requestedClientId, ownerName);
        map_nb_sayHello_id.putIfAbsent(requestedClientId, 0);

        // On appelle la méthode de callback du client pour lui transmettre son ID.
        client.setClientId(requestedClientId);

        System.out.println("Client reconnecté: id=" + requestedClientId + ", pseudo=" + map_id_pseudo.get(requestedClientId));
        return requestedClientId;
    }
    
    @Override
    public synchronized String sayHello(Accounting_itf client) throws RemoteException {
        if (client != null && map_stubClient_id.containsKey(client)) {
            int clientId = map_stubClient_id.get(client);
            int nb_appels = map_nb_sayHello_id.get(clientId) + 1;
            map_nb_sayHello_id.put(clientId, nb_appels);

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

    private void saveState() {
        stateStore.save(nextClientId, map_id_pseudo, allHistories, readCursors);
    }

    @Override
    public synchronized String sendDirectMessage(int fromClientId, int toClientId, String message) throws RemoteException {
        // from/to doivent exister en tant qu'identités connues.
        if (!map_id_pseudo.containsKey(fromClientId)) {
            throw new RemoteException("Expéditeur inconnu: id=" + fromClientId);
        }
        if (!map_id_pseudo.containsKey(toClientId)) {
            throw new RemoteException("Destinataire inconnu: id=" + toClientId);
        }

        String convId;
        if (fromClientId < toClientId) {
            convId = fromClientId + "-" + toClientId;
        } else {
            convId = toClientId + "-" + fromClientId;
        }

        String content = (message == null) ? "" : message.trim();
        if (content.isEmpty()) {
            throw new RemoteException("Le message ne peut pas être vide.");
        }
        
        // On ajoute le message à l'historique de la conversation.
        List<TchatMessage> history = allHistories.get(convId);
        if (history == null) {
            history = new ArrayList<>();
            allHistories.put(convId, history);
        }
        TchatMessage newMessage = new TchatMessage(history.size(), fromClientId, map_id_pseudo.get(fromClientId), message);
        history.add(newMessage);

        // Puisque l'émetteur vient d'envoyer ce message, on considère qu'il a lu la conversation jusqu'ici.
        updateCursor(fromClientId, convId, newMessage.id);
        saveState();
        
        Accounting_itf targetClient = map_id_stubClient.get(toClientId);
        String fromClientName = map_id_pseudo.get(fromClientId);
        String toClientName = map_id_pseudo.get(toClientId);

        if (targetClient != null) {
            try {
                targetClient.receiveMessage(fromClientId, fromClientName, content);
                // On met à jour son curseur de lecture puisqu'on vient de lui pousser le message
                updateCursor(toClientId, convId, newMessage.id);
            } catch (RemoteException e) {
                map_id_stubClient.remove(toClientId);
            }
        }
        saveState();
        return "Message envoyé à " + toClientName + " (id=" + toClientId + ").";
    }

    @Override
    public synchronized String sendGeneralMessage(int fromClientId, String message) throws RemoteException {
        if (!map_id_pseudo.containsKey(fromClientId)) {
            throw new RemoteException("Expéditeur inconnu: id=" + fromClientId);
        }
        String content = (message == null) ? "" : message.trim();
        if (content.isEmpty()) {
            throw new RemoteException("Le message ne peut pas être vide.");
        }

        String convId = "GENERAL";

        // On ajoute le message à l'historique de la conversation.
        List<TchatMessage> history = allHistories.get(convId);
        if (history == null) {
            history = new ArrayList<>();
            allHistories.put(convId, history);
        }
        TchatMessage newMessage = new TchatMessage(history.size(), fromClientId, map_id_pseudo.get(fromClientId), message);
        history.add(newMessage);

        // Puisque l'émetteur vient d'envoyer ce message, on considère qu'il a lu la conversation jusqu'ici.
        updateCursor(fromClientId, convId, newMessage.id);
        saveState();

        String fromClientName = map_id_pseudo.get(fromClientId);

        for (Integer toClientId : map_id_pseudo.keySet()) {
            if (toClientId != fromClientId) {
                Accounting_itf targetClient = map_id_stubClient.get(toClientId);
                
                if (targetClient != null) {
                    try {
                        targetClient.receiveGeneralMessage(fromClientId, fromClientName, content);
                        // On met à jour son curseur de lecture puisqu'on vient de lui pousser le message
                        updateCursor(toClientId, convId, newMessage.id);
                    } catch (RemoteException e) {
                        map_id_stubClient.remove(toClientId);
                    }
                }
            }
        }
        saveState();
        return "Message envoyé à tous les clients connectés.";
    }

    @Override
    public synchronized List<TchatMessage> getHistory(int userId, String convId) throws RemoteException {
        // On récupère l'historique complet de la conversation demandée.
        List<TchatMessage> history = allHistories.getOrDefault(convId, new ArrayList<>());
        
        // On met à jour le curseur pour cet utilisateur
        if (!history.isEmpty()) {
            int lastId = history.size() - 1;
            updateCursor(userId, convId, lastId);
            saveState();
        }
        return history; 
    }

    @Override
    public synchronized Map<String, Boolean> getConversationsList(int userId) throws RemoteException {
        Map<String, Boolean> userConvs = new HashMap<>();
        
        // On parcourt toutes les clés de notre Map d'historiques
        for (String convId : allHistories.keySet()) {
            // Si c'est le tchat général
            if (convId.equals("GENERAL") || convId.contains(String.valueOf(userId))) {
                
                List<TchatMessage> history = allHistories.get(convId);
                int lastMessageId;
                if (history.isEmpty()){
                    lastMessageId = -1;
                }
                else {
                    lastMessageId = history.get(history.size() - 1).id;
                }
                
                Map<Integer, Integer> convCursors = readCursors.get(convId);
                int userCursor;
                if (convCursors == null) {
                    userCursor = -1; // personne n'a encore lu cette conversation
                }
                else {
                    userCursor = convCursors.getOrDefault(userId, -1);
                }
                
                boolean messageUnread = lastMessageId > userCursor;

                userConvs.put(convId, messageUnread);
            }
        }
        return userConvs;
    }

    @Override
    public synchronized int getCursor(int userId, String convId) throws RemoteException {
        Map<Integer, Integer> convCursors = readCursors.get(convId);
        
        // Si elle n'existe pas, c'est que personne n'a encore lu (curseur à -1)
        if (convCursors == null) {
            return -1;
        }
        
        // On renvoie le curseur de l'utilisateur, ou -1 s'il n'a jamais lu cette conv
        return convCursors.getOrDefault(userId, -1);
    }

    @Override
    public synchronized void disconnect(int clientId) throws RemoteException {
        // Déconnexion : on supprime uniquement l'état "en ligne".
        // On conserve map_id_pseudo pour permettre la reconnexion avec le même ID.
        Accounting_itf oldStub = map_id_stubClient.remove(clientId);
        if (oldStub != null) {
            map_stubClient_id.remove(oldStub);
            System.out.println("Client déconnecté: id=" + clientId + ", pseudo=" + map_id_pseudo.get(clientId));
        }
    }
}
