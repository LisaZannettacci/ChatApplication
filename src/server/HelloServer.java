package server;


import java.rmi.*; 
import java.rmi.server.*; 
import java.rmi.registry.*;

public class HelloServer {

  public static void  main(String [] args) {
	  try {
		// Create a Hello remote object
	    // HelloImpl h = new HelloImpl ("Hello world !");
	    // Hello h_stub = (Hello) UnicastRemoteObject.exportObject(h, 0);


		Hello2Impl h2 = new Hello2Impl("Hello world 2 !");
		UnicastRemoteObject.exportObject(h2, 0);

	    // Register the remote object in RMI registry with a given identifier
	    Registry registry = null;
	    if (args.length>0)
		    registry= LocateRegistry.getRegistry(Integer.parseInt(args[0])); 
	    else
		    registry = LocateRegistry.getRegistry();
	    registry.rebind("Hello2Service", h2);
		registry.rebind("RegistryService", h2);

	    System.out.println ("Server ready");

	  } catch (Exception e) {
		  System.err.println("Error on server :" + e) ;
		  e.printStackTrace();
	  }
  }
}
