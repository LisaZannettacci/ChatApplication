package server;

import java.rmi.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import common.ChatMessage;
import interfaces.client.ChatClientCallback;
import interfaces.server.ChatService;
import interfaces.server.ClientRegistry;

/**
 * Implémentation principale du serveur de chat RMI.
 * Cette classe implémente les interfaces ChatService et ClientRegistry,
 * gérant l'enregistrement des clients, l'envoi de messages, la persistance
 * et les callbacks vers les clients.
 * 
 * Fonctionnalités principales :
 * - Enregistrement et reconnexion sécurisée des clients
 * - Envoi de messages directs (privés) et généraux (broadcast)
 * - Gestion d'historiques de conversations avec curseurs de lecture
 * - Persistance de l'état (IDs, pseudos, historiques, curseurs)
 * - Callbacks pour notifier les clients des messages entrants
 * 
 * Variables membres :
 * - message : message 
 * - map_stubClient_id : associe un stub client à son ID
 * - map_id_stubClient : associe un ID à son stub client (pour callbacks)
 * - map_id_pseudo : associe un ID à son pseudo (persistant)
 * - stateStore : gestionnaire de persistance JSON
 * - allHistories : historiques de toutes les conversations
 * - readCursors : curseurs de lecture par conversation et utilisateur
 * - nextClientId : prochain ID à attribuer
 * 
 * Toutes les méthodes publiques sont synchronisées pour garantir la thread-safety.
 * 
 * @see ChatService
 * @see ClientRegistry
 * @see ChatServerStateStore
 */
public class ChatServiceImpl implements ChatService, ClientRegistry {
    
    /** Message */
    private String message;

    /** Map stub client -> ID client */
    private final Map<ChatClientCallback, Integer> map_stubClient_id;
    
    /** Map ID client -> stub client (pour les callbacks) */
    private final Map<Integer, ChatClientCallback> map_id_stubClient;
    
    /** Map ID client -> pseudo (persistante) */
    private final Map<Integer, String> map_id_pseudo;
        
    /** Gestionnaire de persistance JSON */
    private final ChatServerStateStore stateStore;
    
    /** Historiques de toutes les conversations : convId -> List<ChatMessage> */
    private final Map<String, List<ChatMessage>> allHistories = new HashMap<>();
    
    /** Curseurs de lecture : convId -> (userId -> lastReadMessageId) */
    private final Map<String, Map<Integer, Integer>> readCursors = new HashMap<>();
    
    /** Prochain ID client à attribuer */
    private int nextClientId;
    
    /**
     * Constructeur de ChatServiceImpl.
     * Initialise toutes les structures de données et charge l'état persisté depuis le fichier JSON.
     * Les maps de stubs (état "en ligne") restent vides au démarrage.
     * 
     * @param message message
     */
    public ChatServiceImpl(String message) {
        this.message = message;
        this.map_stubClient_id = new HashMap<>();
        this.map_id_stubClient = new HashMap<>();
        this.map_id_pseudo = new HashMap<>();
        this.stateStore = new ChatServerStateStore("server_state.json");

        // On restaure uniquement nextClientId, map_id_pseudo, allHistories et readCursors.
        // Les autres maps restent vides au redémarrage (état "en ligne" non persistant).
        ChatServerStateStore.LoadedState loaded = stateStore.load();
        this.nextClientId = loaded.getNextClientId();
        this.map_id_pseudo.putAll(loaded.getMapIdPseudo());
        this.allHistories.putAll(loaded.getAllHistories());
        this.readCursors.putAll(loaded.getReadCursors());

        System.out.println(
            "Etat charge: " + map_id_pseudo.size() + " client(s) connu(s), prochain id=" + nextClientId
        );
    }

    /**
     * Normalise le nom d'un client.
     * Supprime les espaces en début et fin, et génère un nom par défaut si vide ou null.
     * 
     * @param clientName le nom fourni par le client
     * @param clientId l'ID du client (utilisé pour générer un nom par défaut)
     * @return le nom normalisé (non-null, non-vide)
     */
    private String normalizeClientName(String clientName, int clientId) {
        // trim() => on supprime les espaces en début et fin de chaîne.
        return (clientName == null || clientName.trim().isEmpty())
            ? "Client-" + clientId
            : clientName.trim();
    }

    /**
     * Met à jour le curseur de lecture d'un utilisateur pour une conversation.
     * Le curseur indique le dernier message lu par l'utilisateur.
     * Méthode synchronisée pour garantir la cohérence des curseurs.
     * 
     * @param userId l'ID de l'utilisateur dont on met à jour le curseur
     * @param convId l'ID de la conversation (ex: "GENERAL" ou "1-2")
     * @param lastMessageId l'ID du dernier message lu
     */
    private synchronized void updateCursor(int userId, String convId, int lastMessageId) {
        Map<Integer, Integer> convCursors = readCursors.get(convId);
        if (convCursors == null) { // si la conversation n'avait pas encore de curseurs, on crée une nouvelle map pour cette conversation
            convCursors = new HashMap<>();
            // On l'ajoute dans la Map globale pour ne pas la perdre
            readCursors.put(convId, convCursors);
        }

        // On enregistre que l'utilisateur 'userId' a lu jusqu'au message 'lastMessageId'
        convCursors.put(userId, lastMessageId);
    }

    /**
     * Enregistre ou reconnecte un client au serveur.
     * 
     * Cas 1 (requestedClientId == 0) : Première connexion
     *   - Le serveur attribue un nouvel ID (nextClientId++)
     *   - Crée les associations stub<->ID, ID<->pseudo
     *   - Persiste l'état
     * 
     * Cas 2 (requestedClientId > 0) : Reconnexion
     *   - Vérifie que l'ID existe et appartient au pseudo fourni
     *   - Refuse les sessions concurrentes sur le même ID
     *   - Réactive la session et met à jour les maps
     * 
     * @param client le stub du client (implémentation de ChatClientCallback)
     * @param clientName le pseudo demandé par le client
     * @param requestedClientId l'ID demandé (0 pour nouveau, existant pour reconnexion)
     * @return l'ID attribué ou validé
     * @throws RemoteException si :
     *         - le client est null
     *         - l'ID demandé est invalide (< 0)
     *         - l'ID demandé est inconnu
     *         - le pseudo ne correspond pas au propriétaire de l'ID
     *         - l'ID est déjà connecté (session concurrente)
     */
    @Override
    public synchronized int register(ChatClientCallback client, String clientName, int requestedClientId) throws RemoteException {
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
        ChatClientCallback previousStub = map_id_stubClient.get(requestedClientId);
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

        // On appelle la méthode de callback du client pour lui transmettre son ID.
        client.setClientId(requestedClientId);

        System.out.println("Client reconnecté: id=" + requestedClientId + ", pseudo=" + map_id_pseudo.get(requestedClientId));
        return requestedClientId;
    }
    

    /**
     * Sauvegarde l'état du serveur dans le fichier JSON via le gestionnaire de persistance.
     */
    private void saveState() {
        stateStore.save(nextClientId, map_id_pseudo, allHistories, readCursors);
    }

    /**
     * Envoie un message direct (privé) entre deux clients.
     * Crée ou enrichit l'historique de la conversation, met à jour le curseur de l'émetteur,
     * et tente d'appeler le callback du destinataire s'il est connecté.
     * 
     * Le nom de la conversation est formaté : "idMin-idMax" (ex: "1-3") pour garantir
     * un identifiant unique et ordonné.
     * 
     * @param fromClientId l'ID de l'expéditeur
     * @param toClientId l'ID du destinataire
     * @param message le contenu du message (doit être non-vide après trim)
     * @return un message de confirmation
     * @throws RemoteException si :
     *         - l'expéditeur ou le destinataire est inconnu
     *         - le message est vide ou null
     */
    @Override
    public String sendDirectMessage(int fromClientId, int toClientId, String message) throws RemoteException {
        ChatClientCallback targetClient;
        String fromClientName;
        String toClientName;
        String content;
        String convId;

        // Section synchronisée : mise à jour de l'état interne uniquement
        synchronized (this) {
            // from/to doivent exister en tant qu'identités connues.
            if (!map_id_pseudo.containsKey(fromClientId)) {
                throw new RemoteException("Expéditeur inconnu: id=" + fromClientId);
            }
            if (!map_id_pseudo.containsKey(toClientId)) {
                throw new RemoteException("Destinataire inconnu: id=" + toClientId);
            }

            if (fromClientId < toClientId) {
                convId = fromClientId + "-" + toClientId;
            } else {
                convId = toClientId + "-" + fromClientId;
            }

            content = (message == null) ? "" : message.trim();
            if (content.isEmpty()) {
                throw new RemoteException("Le message ne peut pas être vide.");
            }
            
            // On ajoute le message à l'historique de la conversation.
            List<ChatMessage> history = allHistories.get(convId);
            if (history == null) {
                history = new ArrayList<>();
                allHistories.put(convId, history);
            }
            ChatMessage newMessage = new ChatMessage(history.size(), fromClientId, map_id_pseudo.get(fromClientId), message);
            history.add(newMessage);

            // Puisque l'émetteur vient d'envoyer ce message, on considère qu'il a lu la conversation jusqu'ici.
            updateCursor(fromClientId, convId, newMessage.id);
            saveState();

            targetClient = map_id_stubClient.get(toClientId);
            fromClientName = map_id_pseudo.get(fromClientId);
            toClientName = map_id_pseudo.get(toClientId);
        }

        // Callback HORS du bloc synchronized pour éviter un deadlock RMI :
        // le client appelé pourrait rappeler le serveur (getHistory, etc.)
        if (targetClient != null) {
            try {
                targetClient.receiveMessage(fromClientId, fromClientName, content);
            } catch (RemoteException e) {
                // Si le callback échoue, on considère le client déconnecté
                synchronized (this) {
                    map_id_stubClient.remove(toClientId);
                }
                System.err.println("Callback échec pour client id=" + toClientId + ", stub supprimé.");
            }
        }
        return "Message envoyé à " + toClientName + " (id=" + toClientId + ").";
    }

    /**
     * Envoie un message sur le tchat général (broadcast).
     * Le message est ajouté à l'historique "GENERAL" et diffusé à tous les clients
     * connectés (sauf l'émetteur) via callbacks.
     * 
     * @param fromClientId l'ID de l'expéditeur
     * @param message le contenu du message (doit être non-vide après trim)
     * @return un message de confirmation
     * @throws RemoteException si :
     *         - l'expéditeur est inconnu
     *         - le message est vide ou null
     */
    @Override
    public String sendGeneralMessage(int fromClientId, String message) throws RemoteException {
        String fromClientName;
        String content;
        String convId = "GENERAL";
        // Liste des clients à notifier (collectée sous verrou)
        Map<Integer, ChatClientCallback> toNotify = new HashMap<>();

        // Section synchronisée : mise à jour de l'état interne uniquement
        synchronized (this) {
            if (!map_id_pseudo.containsKey(fromClientId)) {
                throw new RemoteException("Expéditeur inconnu: id=" + fromClientId);
            }
            content = (message == null) ? "" : message.trim();
            if (content.isEmpty()) {
                throw new RemoteException("Le message ne peut pas être vide.");
            }

            // On ajoute le message à l'historique de la conversation.
            List<ChatMessage> history = allHistories.get(convId);
            if (history == null) {
                history = new ArrayList<>();
                allHistories.put(convId, history);
            }
            ChatMessage newMessage = new ChatMessage(history.size(), fromClientId, map_id_pseudo.get(fromClientId), message);
            history.add(newMessage);

            // Puisque l'émetteur vient d'envoyer ce message, on considère qu'il a lu la conversation jusqu'ici.
            updateCursor(fromClientId, convId, newMessage.id);
            saveState();

            fromClientName = map_id_pseudo.get(fromClientId);

            // On collecte les stubs à notifier sous verrou
            for (Integer toClientId : map_id_pseudo.keySet()) {
                if (toClientId != fromClientId) {
                    ChatClientCallback targetClient = map_id_stubClient.get(toClientId);
                    if (targetClient != null) {
                        toNotify.put(toClientId, targetClient);
                    }
                }
            }
        }

        // Callbacks HORS du bloc synchronized pour éviter un deadlock RMI
        for (Map.Entry<Integer, ChatClientCallback> entry : toNotify.entrySet()) {
            try {
                entry.getValue().receiveGeneralMessage(fromClientId, fromClientName, content);
            } catch (RemoteException e) {
                // Si le callback échoue, on considère le client déconnecté
                synchronized (this) {
                    map_id_stubClient.remove(entry.getKey());
                }
                System.err.println("Callback échec pour client id=" + entry.getKey() + ", stub supprimé.");
            }
        }
        return "Message envoyé à tous les clients connectés.";
    }

    /**
     * Récupère l'historique complet d'une conversation.
     * Met automatiquement à jour le curseur de lecture de l'utilisateur
     * au dernier message de la conversation.
     * 
     * @param userId l'ID de l'utilisateur demandant l'historique
     * @param convId l'ID de la conversation (ex: "GENERAL" ou "1-2")
     * @return la liste des messages de la conversation (vide si inexistante)
     * @throws RemoteException en cas d'erreur RMI
     */
    @Override
    public synchronized List<ChatMessage> getHistory(int userId, String convId) throws RemoteException {
        // On récupère l'historique complet de la conversation demandée.
        List<ChatMessage> history = allHistories.getOrDefault(convId, new ArrayList<>());
        
        // On met à jour le curseur pour cet utilisateur
        if (!history.isEmpty()) {
            int lastId = history.size() - 1;
            updateCursor(userId, convId, lastId);
            saveState();
        }
        return history; 
    }

    /**
     * Retourne la liste des conversations d'un utilisateur avec leur compteur de messages non lus.
     * La conversation "GENERAL" est toujours incluse.
     * 
     * @param userId l'ID de l'utilisateur
     * @return une map convId -> nombre de messages non lus
     * @throws RemoteException en cas d'erreur RMI
     */
    @Override
    public synchronized Map<String, Integer> getConversationsList(int userId) throws RemoteException {
        Map<String, Integer> userConvs = new HashMap<>();
        
        // On force l'existence de GENERAL dans la liste retournée
        // On vérifie d'abord si elle a des messages non lus
        int generalUnread = 0;
        List<ChatMessage> genHistory = allHistories.get("GENERAL");
        if (genHistory != null && !genHistory.isEmpty()) {
            int lastId = genHistory.get(genHistory.size() - 1).id;
            int userCursor = (readCursors.get("GENERAL") != null) 
                            ? readCursors.get("GENERAL").getOrDefault(userId, -1) 
                            : -1;
            generalUnread = lastId - userCursor;
        }
        userConvs.put("GENERAL", generalUnread);

        // On parcourt le reste des historiques pour les messages privés
        for (String convId : allHistories.keySet()) {
            if (convId.contains(String.valueOf(userId)) && !convId.equals("GENERAL")) {
                List<ChatMessage> history = allHistories.get(convId);
                int lastMessageId = history.isEmpty() ? -1 : history.get(history.size() - 1).id;
                
                Map<Integer, Integer> convCursors = readCursors.get(convId);
                int userCursor = (convCursors == null) ? -1 : convCursors.getOrDefault(userId, -1);
                
                userConvs.put(convId, lastMessageId - userCursor);
            }
        }
        return userConvs;
    }

    /**
     * Retourne le curseur de lecture d'un utilisateur pour une conversation.
     * Le curseur correspond à l'ID du dernier message lu.
     * 
     * @param userId l'ID de l'utilisateur
     * @param convId l'ID de la conversation
     * @return l'ID du dernier message lu, ou -1 si aucun message n'a été lu
     * @throws RemoteException en cas d'erreur RMI
     */
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

    /**
     * Retourne le pseudo d'un client à partir de son ID.
     * 
     * @param clientId l'ID du client
     * @return le pseudo du client, ou "Inconnu" si l'ID n'existe pas
     * @throws RemoteException en cas d'erreur RMI
     */
    @Override
    public synchronized String getClientPseudo(int clientId) throws RemoteException {
        return map_id_pseudo.getOrDefault(clientId, "Inconnu");
    }

    /**
     * Déconnecte un client du serveur.
     * Supprime le stub de la map des clients connectés mais conserve
     * l'association ID<->pseudo pour permettre la reconnexion.
     * 
     * @param clientId l'ID du client à déconnecter
     * @throws RemoteException en cas d'erreur RMI
     */
    @Override
    public synchronized void disconnect(int clientId) throws RemoteException {
        // Déconnexion : on supprime uniquement l'état "en ligne".
        // On conserve map_id_pseudo pour permettre la reconnexion avec le même ID.
        ChatClientCallback oldStub = map_id_stubClient.remove(clientId);
        if (oldStub != null) {
            map_stubClient_id.remove(oldStub);
            System.out.println("Client déconnecté: id=" + clientId + ", pseudo=" + map_id_pseudo.get(clientId));
        }
    }
}
