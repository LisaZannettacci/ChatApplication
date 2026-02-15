package interfaces.server;

import java.rmi.*;
import interfaces.client.Accounting_itf;

public interface Hello2 extends Remote {
	public String sayHello(Accounting_itf client) throws RemoteException;
}
