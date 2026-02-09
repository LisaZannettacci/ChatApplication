
import java.rmi.*;

public  class HelloImpl implements Hello {

	private String message;
 
	public HelloImpl(String s) {
		message = s ; // Existe sur le serveur
	}

	public String sayHello(String clientName) throws RemoteException {
		System.out.println("Le client " + clientName + " a appelé la méthode sayHello()");
		return message ;
	}
}

