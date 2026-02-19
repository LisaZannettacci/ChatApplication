package interfaces.client;

import java.rmi.*;

public interface ClientInfo extends Remote {
	public String getName() throws RemoteException;
}
