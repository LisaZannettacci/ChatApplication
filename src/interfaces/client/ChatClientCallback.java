package interfaces.client;

import java.rmi.*;

/**
 * Interface RMI pour les callbacks serveur vers client.
 * Cette interface définit les méthodes distantes que le serveur peut appeler
 * sur les clients pour leur envoyer des notifications et des messages entrants.
 * 
 * Fonctionnalités principales :
 * - Réception de messages directs (privés)
 * - Réception de messages généraux (broadcast)
 * - Gestion de l'identifiant client (get/set)
 * 
 * Le client doit exporter un stub implémentant cette interface via
 * UnicastRemoteObject.exportObject() pour que le serveur puisse appeler ces méthodes.
 * 
 * Implémentée par : ChatClient
 * 
 * @see client.ChatClient
 * @see interfaces.server.ChatService
 */
public interface ChatClientCallback extends Remote {
		
	/**
	 * Callback appelé par le serveur lors de la réception d'un message direct (privé).
	 * Le client doit traiter ce message (affichage console ou IHM) et peut
	 * mettre à jour son curseur de lecture en appelant getHistory().
	 * 
	 * @param fromClientId l'ID de l'expéditeur du message
	 * @param fromClientName le pseudo de l'expéditeur
	 * @param message le contenu du message reçu
	 * @throws RemoteException en cas d'erreur de communication RMI
	 */
	public void receiveMessage(int fromClientId, String fromClientName, String message) throws RemoteException;
	
	/**
	 * Callback appelé par le serveur lors de la réception d'un message sur le tchat général.
	 * Le client doit traiter ce message (affichage console ou IHM) et peut
	 * mettre à jour son curseur de lecture en appelant getHistory().
	 * 
	 * @param fromClientId l'ID de l'expéditeur du message
	 * @param fromClientName le pseudo de l'expéditeur
	 * @param message le contenu du message reçu
	 * @throws RemoteException en cas d'erreur de communication RMI
	 */
	public void receiveGeneralMessage(int fromClientId, String fromClientName, String message) throws RemoteException;
	
	/**
	 * Définit l'identifiant du client.
	 * Callback appelé par le serveur lors de l'enregistrement/reconnexion
	 * pour communiquer l'ID attribué ou validé au client.
	 * 
	 * @param clientId l'identifiant unique attribué par le serveur
	 * @throws RemoteException en cas d'erreur de communication RMI
	 */
	public void setClientId(int clientId) throws RemoteException;
	
	/**
	 * Retourne l'identifiant actuel du client.
	 * Permet au serveur de vérifier l'ID d'un client si nécessaire.
	 * 
	 * @return l'ID du client, ou -1 si non encore assigné
	 * @throws RemoteException en cas d'erreur de communication RMI
	 */
	public int getClientId() throws RemoteException;
}
