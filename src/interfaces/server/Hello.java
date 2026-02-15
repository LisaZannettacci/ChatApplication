package interfaces.server;

import java.rmi.*;
import interfaces.client.Info_itf;

public interface Hello extends Remote {
	public String sayHello(String clientName) throws RemoteException;
	public String sayHello(Info_itf client) throws RemoteException;
}
