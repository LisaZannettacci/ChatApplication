import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.*;

public class HelloClient2 implements Accounting_itf {

    private String name;

    HelloClient2(String name) {
        this.name = name;
    }
    
	public void numberOfCalls(int number) throws RemoteException {
        System.out.println("Notification du serveur : Vous avez effectué " + number + " appels.");
    }

    public static void main(String [] args) {
		try {
            if (args.length < 4) {
                System.out.println("Usage: java HelloClient <rmiregistry host> <rmiregistry port> <client name> <nb_appels>");
                return;
            }

            String host = args[0];
            int port = Integer.parseInt(args[1]);

            Registry registry = LocateRegistry.getRegistry(host, port);

            HelloClient2 client = new HelloClient2(args[2]);

            Accounting_itf client_stub = null;
            try {
                client_stub = (Accounting_itf) UnicastRemoteObject.exportObject(client, 0);
            } catch (RemoteException re) {
                System.err.println("exportObject failed:");
                re.printStackTrace();
                return;
            }

            System.out.println("client_stub = " + client_stub);

            Registry_itf registry_stub = (Registry_itf) registry.lookup("RegistryService");
            if (registry_stub == null) {
                System.err.println("registry_stub is null");
                return;
            }
            if (client_stub == null) {
                System.err.println("client_stub is null before register()");
                return;
            }
            registry_stub.register(client_stub);
            System.out.println("Je suis "+ args[2] + " et je me suis enregistré auprès du serveur.");

            Hello2 h2 = (Hello2) registry.lookup("Hello2Service");

            for (int i = 0;  i < Integer.parseInt(args[3]); i++) { 
                String response = h2.sayHello(client_stub); 
                System.out.println("Réponse du serveur : " + response);
                Thread.sleep(1000); // Attendre 1 seconde entre les appels
            }

        } catch (Exception e) { 
			e.printStackTrace();
		}
    }
}