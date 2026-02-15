package server;


import java.rmi.*; 
import java.rmi.server.*; 
import java.rmi.registry.*;

public class HelloServer {

  public static void  main(String [] args) {
	  try {		
		Hello2Impl h2 = new Hello2Impl("Hello world 2 !");

		// Le Runtime RMI crée des threads réseau qui attendent les requêtes clients,
		// Ces threads sont "non-deamon" càd que la JVM ne s'arrête pas tant que ces threads sont actifs (=> même si fin du main)
		UnicastRemoteObject.exportObject(h2, 0);

	    Registry registry = null;
	    if (args.length>0) {
		    registry= LocateRegistry.getRegistry(Integer.parseInt(args[0])); 
	    } else {
		    registry = LocateRegistry.getRegistry();
	    }

		// On publie nos services :
	    registry.rebind("Hello2Service", h2);
		registry.rebind("RegistryService", h2);

	    System.out.println ("Server ready");

	  } catch (Exception e) {
		  System.err.println("Error on server :" + e) ;
		  e.printStackTrace();
	  }
  }
}
