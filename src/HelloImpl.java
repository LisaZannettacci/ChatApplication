
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

	public String sayHello(Info_itf client) throws RemoteException {
		String clientName = "";
		try{
			clientName = client.getName();
		}
		catch (RemoteException e) {
			System.err.println("Impossible d'obtenir le nom du client : " + e);
		}
		System.out.println("Le client " + clientName + " a appelé la méthode sayHello()");
		return message ;
	}
}

