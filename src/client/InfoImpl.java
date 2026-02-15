package client;

import java.rmi.*;
import interfaces.client.Info_itf;

public class InfoImpl implements Info_itf {
    private final String name;
    
    public InfoImpl(String name) {
        this.name = name;
    }
    
    public String getName() throws RemoteException {
        return name;
    }
}
