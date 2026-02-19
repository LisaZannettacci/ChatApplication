package interfaces.server;

import java.rmi.*;
import java.util.List;
import java.util.Map;

import common.ChatMessage;
import interfaces.client.ChatClientCallback;

public interface ChatService extends Remote {
	public String sayHello(ChatClientCallback client) throws RemoteException;
	public String sendDirectMessage(int fromClientId, int toClientId, String message) throws RemoteException;
	public String sendGeneralMessage(int fromClientId, String message) throws RemoteException;
	public List<ChatMessage> getHistory(int userId, String convId) throws RemoteException;
	public Map<String, Integer> getConversationsList(int userId) throws RemoteException;
	public int getCursor(int userId, String convId) throws RemoteException;
	public String getClientPseudo(int clientId) throws RemoteException;
	public void disconnect(int clientId) throws RemoteException;
}
