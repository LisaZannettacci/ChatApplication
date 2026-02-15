package interfaces.client;

import java.rmi.*;

public interface Info_itf extends Remote {
	public String getName() throws RemoteException;
}
