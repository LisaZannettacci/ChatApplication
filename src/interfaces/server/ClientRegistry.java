package interfaces.server;

import java.rmi.*;
import interfaces.client.ChatClientCallback;

public interface ClientRegistry extends Remote {
	public int register(ChatClientCallback client, String clientName, int requestedClientId) throws RemoteException;
}
