package com.bonkers;

import java.net.InetAddress;
import java.rmi.AccessException;
import java.rmi.AlreadyBoundException;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.sun.xml.internal.ws.spi.db.BindingContextFactory.LOGGER;

/**
 * Server class that accepts client connections.
 */
public class Server implements QueueListener, ServerIntf
{
    /**
     * Error string
     */
    private int error;
    /**
     * The hash table, essential to its functionality
     */
    public static HashTableCreator hashTableCreator = null;
    /**
     * The multicast listener, listens to multicast clients wanting to joing
     */
    private MulticastCommunicator multicast = null;
    private Registry registry;

    /**
     * Main server object constructor, creates MulticastCommunicator and Hashtablecreator, and subscribes on the queueEvent object
     */
    public Server()
    {
        try
        {
            registry = LocateRegistry.createRegistry(1099);
            ServerIntf stub = (ServerIntf) UnicastRemoteObject.exportObject(this, 0);
            registry.bind("ServerIntf", stub);
        } catch (AlreadyBoundException e)
        {
            e.printStackTrace();
        } catch (AccessException e)
        {
            e.printStackTrace();
        } catch (RemoteException e)
        {
            e.printStackTrace();
        }
        hashTableCreator = new HashTableCreator();
        multicast = new MulticastCommunicator();
        multicast.start();
        multicast.packetQueue.addListener(this);
    }

    /**
     * Subscriptor of the queueEvent
     * Gets run when a multicast packet is received, then checks for double entries and then adds the node
     */
    @Override
    public void queueFilled()
    {
        Tuple<String, String> t = multicast.packetQueue.poll();
        error = checkDoubles(t.x, t.y);
        LOGGER.info("Multicast packet received from " + t.x + " at " + t.y);
        if (error != 100)
        {
            LOGGER.info("Dropping " + t.x + " at " + t.y + " with error code: " + error);
            return;
        }
        addNode(t);
        System.out.println();
    }

    /**
     * Sets the starting info at the new node.
     *
     * @param t The information of the node as a tuple of ("Name", "Address")
     */
    private void addNode(Tuple<String, String> t)
    {
        try
        {
            Registry registry = LocateRegistry.getRegistry(t.y);
            ClientIntf stub = (ClientIntf) registry.lookup("ClientIntf");
            String[] host = InetAddress.getLocalHost().toString().split("/");
            LOGGER.info("Setting starting info at " + t.x);
            stub.setStartingInfo(host[1], hashTableCreator.getNodeAmount());
        } catch (Exception e)
        {
            System.err.println("Server exception: " + e.toString());
            e.printStackTrace();
        }
    }


    /**
     * This checks for duplicates and adds them to the hashtable if not duplicate.
     *
     * @param name Name of the device
     * @param ip   Ip address
     * @return Returns error code
     */
    private int checkDoubles(String name, String ip)
    {
        int resp;
        int hash = HashTableCreator.createHash(name);
        if (hashTableCreator.htIp.containsKey(hash))
        {
            resp = 201;
        }
        else if (hashTableCreator.htIp.containsValue(ip))
        {
            resp = 202;
        }
        else
        {
            resp = 100;
            hashTableCreator.createHashTable(ip, name);
        }
        return resp;
    }

    @Override
    public NodeInfo findLocationFile(String file)
    {
        LOGGER.info("Location of file " + file + " requested.");
        return findLocationHash(HashTableCreator.createHash(file));
    }

    @Override
    public NodeInfo findLocationHash(int hash)
    {
        LOGGER.info("Location of hash " + hash + " requested.");
        LOGGER.info("Returning " + hashTableCreator.findHost(hash));
        return hashTableCreator.findHost(hash);
    }

    @Override
    public int error() throws RemoteException
    {
        return error;
    }

    @Override
    public void nodeShutdown(NodeInfo node)
    {
        LOGGER.info("Received node shutdown of node " + node.toString());
        hashTableCreator.htIp = hashTableCreator.readHashtable();
        if (hashTableCreator.htIp.containsKey(node.hash))
        {
            hashTableCreator.htIp.remove(node.hash);
            hashTableCreator.writeHashtable();
        }
        else
        {
            throw new IllegalArgumentException("Somehow, the node that shut down didn't exist");
        }
        LOGGER.info("Removed " + node.toString() + " successfully.");
    }

    @Override
    public NodeInfo[] nodeNeighbors(NodeInfo node)
    {
        LOGGER.info("Node neighbors requested of node " + node.toString());
        Map hashmap = hashTableCreator.readHashtable();
        List list = new ArrayList(hashmap.keySet());
        Collections.sort(list);
        int index = list.indexOf(node.hash);
        if (hashmap.size() == 2)
        {
            NodeInfo neighbor = new NodeInfo((Integer) list.get(1 - index), (String) hashmap.get(list.get(1 - index)));
            return new NodeInfo[]{neighbor, neighbor};
        }
        else if (hashmap.size() > 2)
        {
            int x;
            if (index == 0)
            {
                x = list.size() - 1;
            }
            else
            {
                x = index - 1;
            }
            NodeInfo previousNeighbor = new NodeInfo((Integer) list.get(x), (String) hashmap.get(list.get(x)));
            if (index == list.size() - 1)
            {
                x = 0;
            }
            else
            {
                x = index + 1;
            }
            NodeInfo nextNeighbor = new NodeInfo((Integer) list.get(x), (String) hashmap.get(list.get(x)));
            return new NodeInfo[]{previousNeighbor, nextNeighbor};
        }
        else
        {
            return new NodeInfo[]{node, node};
        }
    }

    /**
     * Shutdown for the server, stops the multicast thread
     */
    public void shutdown()
    {
        multicast.interrupt();
        LOGGER.info("Successful shutdown");
        System.exit(0);
    }
}

