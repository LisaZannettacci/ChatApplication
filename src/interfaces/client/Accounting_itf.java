package interfaces.client;

import java.rmi.*;

public interface Accounting_itf extends Remote {
	public void numberOfCalls(int number) throws RemoteException;
	public void receiveMessage(int fromClientId, String fromClientName, String message) throws RemoteException;
	public void setClientId(int clientId) throws RemoteException;
}
