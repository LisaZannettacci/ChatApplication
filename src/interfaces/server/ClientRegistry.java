package interfaces.server;

import java.rmi.*;
import interfaces.client.ChatClientCallback;

/**
 * Interface RMI pour l'enregistrement et la gestion des clients.
 * Cette interface définit les méthodes distantes permettant aux clients
 * de s'enregistrer auprès du serveur lors de leur première connexion
 * ou de se reconnecter avec un ID existant.
 * 
 * Le serveur attribue un identifiant unique à chaque nouveau client (ID=0)
 * et valide l'appartenance pseudo<->ID lors des reconnexions.
 * 
 * Implémentée par : ChatServiceImpl
 * 
 * @see ChatServiceImpl
 * @see interfaces.client.ChatClientCallback
 */
public interface ClientRegistry extends Remote {
	
	/**
	 * Enregistre un nouveau client ou reconnecte un client existant.
	 * 
	 * Comportement selon requestedClientId :
	 * - Si requestedClientId == 0 : première connexion, le serveur attribue un nouvel ID
	 * - Si requestedClientId > 0 : reconnexion, le serveur valide que l'ID appartient au client
	 * 
	 * Validations effectuées côté serveur :
	 * - Le stub client ne doit pas être null
	 * - Pour une reconnexion, l'ID doit exister et correspondre au pseudo fourni
	 * - Refuse les sessions concurrentes sur le même ID
	 * 
	 * @param client le stub du client (implémente ChatClientCallback) pour les callbacks serveur->client
	 * @param clientName le pseudo souhaité par le client (sera normalisé côté serveur)
	 * @param requestedClientId l'ID demandé : 0 pour nouvelle connexion, ID existant pour reconnexion
	 * @return l'ID attribué (nouvelle connexion) ou validé (reconnexion)
	 * @throws RemoteException si :
	 *         - le stub client est null
	 *         - l'ID demandé est invalide (< 0) ou inconnu
	 *         - le pseudo ne correspond pas au propriétaire de l'ID
	 *         - l'ID est déjà connecté (session concurrente interdite)
	 *         - erreur de communication RMI
	 */
	public int register(ChatClientCallback client, String clientName, int requestedClientId) throws RemoteException;
}
