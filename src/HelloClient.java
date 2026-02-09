import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.*;

public class HelloClient{
  public static void main(String [] args) {
	
	try {
		if (args.length < 2) {
			System.out.println("Usage: java HelloClient <rmiregistry host> <rmiregistry port>");
	   		return;
		}

		String host = args[0];
		int port = Integer.parseInt(args[1]);
		String clientName = "noName";
		if (args.length >= 3) {
			clientName = args[2];
		}

		Registry registry = LocateRegistry.getRegistry(host, port); 
		Hello h = (Hello) registry.lookup("HelloService");

		Info_itf client = new InfoImpl(clientName);
		Info_itf client_stub = (Info_itf) UnicastRemoteObject.exportObject(client, 0);

		// Remote method invocation
		String res = h.sayHello(client_stub);
		System.out.println(res);

	} catch (Exception e)  {
//		System.err.println("Error on client: " + e);
		e.printStackTrace();
	}
  }
}
