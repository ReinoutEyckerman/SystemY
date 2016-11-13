package com.bonkers;

import java.rmi.*;


public interface ServerIntf extends Remote {
    String FindLocationFile(String FileName) throws RemoteException, InterruptedException;
    void NodeShutdown(Tuple node)throws RemoteException, InterruptedException;;
    Tuple<Tuple<Integer,String>,Tuple<Integer,String>> NodeFailure(Tuple node)throws RemoteException;
}
