package client;

import java.rmi.*;
import interfaces.client.ClientInfo;

public class ClientInfoImpl implements ClientInfo {
    private final String name;
    
    public ClientInfoImpl(String name) {
        this.name = name;
    }
    
    public String getName() throws RemoteException {
        return name;
    }
}
