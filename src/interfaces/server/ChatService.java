package interfaces.server;

import java.rmi.*;
import java.util.List;
import java.util.Map;

import common.ChatMessage;

/**
 * Interface RMI principale du service de chat.
 * Cette interface définit toutes les opérations distantes liées à la messagerie :
 * envoi de messages (directs et généraux), récupération d'historiques,
 * gestion des curseurs de lecture et déconnexion.
 * 
 * Types de conversations gérées :
 * - "GENERAL" : tchat général visible par tous les clients connectés
 * - "id1-id2" : conversation privée entre deux clients (format : plus petit ID en premier)
 * 
 * Fonctionnalités principales :
 * - Envoi de messages directs (privés) avec callbacks vers le destinataire
 * - Envoi de messages généraux (broadcast) avec callbacks vers tous les clients
 * - Récupération d'historiques avec mise à jour automatique des curseurs de lecture
 * - Gestion des messages lus/non lus via curseurs par conversation et par utilisateur
 * - Déconnexion propre avec conservation de l'état persistant
 * 
 * Implémentée par : ChatServiceImpl
 * 
 * @see ChatServiceImpl
 * @see common.ChatMessage
 * @see interfaces.client.ChatClientCallback
 */
public interface ChatService extends Remote {
	
	/**
	 * Envoie un message direct (privé) entre deux clients.
	 * Le message est ajouté à l'historique de la conversation "idMin-idMax"
	 * et le destinataire reçoit un callback receiveMessage() s'il est connecté.
	 * Le curseur de l'émetteur est automatiquement mis à jour.
	 * 
	 * @param fromClientId l'ID de l'expéditeur (doit exister dans le système)
	 * @param toClientId l'ID du destinataire (doit exister dans le système)
	 * @param message le contenu du message (ne doit pas être vide après trim)
	 * @return un message de confirmation indiquant le succès de l'envoi
	 * @throws RemoteException si :
	 *         - l'expéditeur ou le destinataire est inconnu
	 *         - le message est vide ou null
	 *         - erreur de communication RMI
	 */
	public String sendDirectMessage(int fromClientId, int toClientId, String message) throws RemoteException;
	
	/**
	 * Envoie un message sur le tchat général (broadcast).
	 * Le message est ajouté à l'historique "GENERAL" et tous les clients
	 * connectés (sauf l'émetteur) reçoivent un callback receiveGeneralMessage().
	 * Le curseur de l'émetteur est automatiquement mis à jour.
	 * 
	 * @param fromClientId l'ID de l'expéditeur (doit exister dans le système)
	 * @param message le contenu du message (ne doit pas être vide après trim)
	 * @return un message de confirmation indiquant le succès de l'envoi
	 * @throws RemoteException si :
	 *         - l'expéditeur est inconnu
	 *         - le message est vide ou null
	 *         - erreur de communication RMI
	 */
	public String sendGeneralMessage(int fromClientId, String message) throws RemoteException;
	
	/**
	 * Récupère l'historique complet d'une conversation.
	 * Met automatiquement à jour le curseur de lecture de l'utilisateur
	 * au dernier message de la conversation (tous les messages sont marqués comme lus).
	 * 
	 * @param userId l'ID de l'utilisateur demandant l'historique
	 * @param convId l'ID de la conversation (ex: "GENERAL" ou "1-3")
	 * @return la liste ordonnée des messages de la conversation (vide si inexistante)
	 * @throws RemoteException en cas d'erreur de communication RMI
	 */
	public List<ChatMessage> getHistory(int userId, String convId) throws RemoteException;
	
	/**
	 * Retourne la liste des conversations d'un utilisateur avec leur compteur de messages non lus.
	 * La conversation "GENERAL" est toujours incluse dans la liste.
	 * Pour les conversations privées, seules celles impliquant l'utilisateur sont retournées.
	 * 
	 * Le nombre de messages non lus est calculé comme : (dernier message ID) - (curseur utilisateur).
	 * 
	 * @param userId l'ID de l'utilisateur
	 * @return une map associant chaque ID de conversation au nombre de messages non lus
	 *         Exemple : {"GENERAL" -> 3, "1-2" -> 0, "2-5" -> 1}
	 * @throws RemoteException en cas d'erreur de communication RMI
	 */
	public Map<String, Integer> getConversationsList(int userId) throws RemoteException;
	
	/**
	 * Retourne le curseur de lecture d'un utilisateur pour une conversation.
	 * Le curseur correspond à l'ID du dernier message lu par l'utilisateur.
	 * 
	 * @param userId l'ID de l'utilisateur
	 * @param convId l'ID de la conversation
	 * @return l'ID du dernier message lu, ou -1 si l'utilisateur n'a jamais lu cette conversation
	 * @throws RemoteException en cas d'erreur de communication RMI
	 */
	public int getCursor(int userId, String convId) throws RemoteException;
	
	/**
	 * Retourne le pseudo associé à un ID client.
	 * 
	 * @param clientId l'ID du client
	 * @return le pseudo du client, ou "Inconnu" si l'ID n'existe pas
	 * @throws RemoteException en cas d'erreur de communication RMI
	 */
	public String getClientPseudo(int clientId) throws RemoteException;
	
	/**
	 * Déconnecte un client du serveur.
	 * Supprime le stub de la liste des clients connectés mais conserve
	 * l'association ID<->pseudo en mémoire persistante pour permettre la reconnexion.
	 * 
	 * @param clientId l'ID du client à déconnecter
	 * @throws RemoteException en cas d'erreur de communication RMI
	 */
	public void disconnect(int clientId) throws RemoteException;
}
