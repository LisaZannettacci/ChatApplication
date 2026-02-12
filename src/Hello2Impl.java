import java.rmi.*;
import java.util.*;

// Côté serveur
public class Hello2Impl implements Hello2, Registry_itf {
    
    private String message;
    private Map<Accounting_itf, Integer> clientCalls; // Map pour suivre le nombre d'appels par client.
    private int LIMITE_AVANT_NOTIFICATION = 10;

    public Hello2Impl(String message) {
        this.message = message;
        this.clientCalls = new HashMap<>();
    }

    @Override
    public void register(Accounting_itf client) throws RemoteException {
        if(client != null && !clientCalls.containsKey(client)) {
            clientCalls.put(client, 0);
            System.out.println("Nouveau client enregistré"); 
        } 
		else { 
			throw new RemoteException("Client déjà enregistré ou null"); 
		}
    }
    
    @Override
    public String sayHello(Accounting_itf client) throws RemoteException {
        if (client != null && clientCalls.containsKey(client)) {
            int nb_appels = clientCalls.get(client) + 1;
            clientCalls.put(client, nb_appels);

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
}