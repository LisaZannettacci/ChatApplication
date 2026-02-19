package server;

import java.rmi.*; 
import java.rmi.server.*; 
import java.rmi.registry.*;

/**
 * Point d'entrée principal du serveur de chat RMI.
 * Cette classe démarre le serveur, crée et exporte l'implémentation du service de chat,
 * puis publie les services dans le registre RMI.
 * 
 * Le serveur expose deux services via RMI :
 * - "ChatService" : service de chat (ChatService)
 * - "RegistryService" : service d'enregistrement des clients (ClientRegistry)
 * 
 * Les deux services sont implémentés par la même instance de ChatServiceImpl.
 * 
 * @see ChatServiceImpl
 * @see interfaces.server.ChatService
 * @see interfaces.server.ClientRegistry
 */
public class ChatServer {

  /**
   * Point d'entrée principal du serveur.
   * Crée une instance de ChatServiceImpl, l'exporte comme objet RMI,
   * puis publie les services dans le registre RMI.
   * 
   * Le serveur reste actif indéfiniment grâce aux threads RMI non-daemon.
   * 
   * @param args arguments en ligne de commande :
   *             - args[0] (optionnel) : port du registre RMI (par défaut 1099 si absent)
   */
  public static void  main(String [] args) {
	  try {		
		ChatServiceImpl h2 = new ChatServiceImpl("Hello world 2 !");

		// Le Runtime RMI crée des threads réseau qui attendent les requêtes clients,
		// Ces threads sont "non-daemon" càd que la JVM ne s'arrête pas tant que ces threads sont actifs (=> même si fin du main)
		UnicastRemoteObject.exportObject(h2, 0);

	    Registry registry = null;
	    if (args.length > 0) {
		    registry = LocateRegistry.getRegistry(Integer.parseInt(args[0])); 
	    } else {
		    registry = LocateRegistry.getRegistry();
	    }

		// On publie nos services :
	    registry.rebind("ChatService", h2);
		registry.rebind("RegistryService", h2);

	    System.out.println("Server ready");

	  } catch (NumberFormatException e) {
		  System.err.println("Erreur : le port doit être un nombre entier valide.");
		  e.printStackTrace();
	  } catch (RemoteException e) {
		  System.err.println("Erreur RMI lors du démarrage du serveur : " + e.getMessage());
		  e.printStackTrace();
	  } catch (Exception e) {
		  System.err.println("Erreur inattendue sur le serveur : " + e.getMessage());
		  e.printStackTrace();
	  }
  }
}
