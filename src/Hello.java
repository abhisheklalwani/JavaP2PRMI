
import java.rmi.Remote; 
import java.rmi.RemoteException;  

// Creating Remote interface for our application 
public interface Hello extends Remote {
//   void lookup(String productname, int hopcount) throws RemoteException;
   void call(String productname, int hopcount, String node_id) throws RemoteException;
   void reply(int buyerId, int sellerId) throws RemoteException;
   void printMsg() throws RemoteException;  
   void buy(int sellerId) throws RemoteException;
} 