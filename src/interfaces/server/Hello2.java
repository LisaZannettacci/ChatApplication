package interfaces.server;

import java.rmi.*;
import java.util.List;
import java.util.Map;

import common.TchatMessage;
import interfaces.client.Accounting_itf;

public interface Hello2 extends Remote {
	public String sayHello(Accounting_itf client) throws RemoteException;
	public String sendDirectMessage(int fromClientId, int toClientId, String message) throws RemoteException;
	public String sendGeneralMessage(int fromClientId, String message) throws RemoteException;
	public List<TchatMessage> getHistory(int userId, String convId) throws RemoteException;
	public Map<String, Integer> getConversationsList(int userId) throws RemoteException;
	public int getCursor(int userId, String convId) throws RemoteException;
	public void disconnect(int clientId) throws RemoteException;
}
