package interfaces.client;

import java.rmi.*;

public interface ChatClientCallback extends Remote {
	public void numberOfCalls(int number) throws RemoteException;
	public void receiveMessage(int fromClientId, String fromClientName, String message) throws RemoteException;
	public void receiveGeneralMessage(int fromClientId, String fromClientName, String message) throws RemoteException;
	public void setClientId(int clientId) throws RemoteException;
	public int getClientId() throws RemoteException;
}
