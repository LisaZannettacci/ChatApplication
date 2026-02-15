package interfaces.server;

import java.rmi.*;
import interfaces.client.Accounting_itf;

public interface Hello2 extends Remote {
	public String sayHello(Accounting_itf client) throws RemoteException;
	public String sendDirectMessage(int fromClientId, int toClientId, String message) throws RemoteException;
	public void disconnect(int clientId) throws RemoteException;
}
