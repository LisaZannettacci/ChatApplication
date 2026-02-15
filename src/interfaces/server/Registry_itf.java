package interfaces.server;

import java.rmi.*;
import interfaces.client.Accounting_itf;

public interface Registry_itf extends Remote {
	public int register(Accounting_itf client, String clientName) throws RemoteException;
}
