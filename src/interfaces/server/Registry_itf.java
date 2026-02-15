package interfaces.server;

import java.rmi.*;
import interfaces.client.Accounting_itf;

public interface Registry_itf extends Remote {
	public void register(Accounting_itf client) throws RemoteException;
}
