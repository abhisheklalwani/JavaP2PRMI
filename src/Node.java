import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.logging.*;
import java.io.File;
import java.io.FileNotFoundException;

//Peer Node class implementation
public class Node implements Hello {
	
	//declaring peer local variables
	private String role; //Role of the peer
	private int node_id; //Id of the node
	private int port_id; //Port Id of the node
	private int max_sell_items = 5; //Maximum number of items a seller can sell
	private int m=5; //Current number of items in stock of seller
	private String item; //Item the peer is buying or selling
	private String items[] = {"Boar", "Salt", "Fish"}; //Choices of items the peer have to choose for buying and selling
	private String testcase = "0"; //This defines how to initialize and restock items in a seller and buyer 
	private int[] peers; //Contains the list of neighboring peers
	private Logger logger; //For generating logs
	private int hopcount = 3; //No of hops a request can go after getting initialized from this peer
	public HashMap<Integer, String[]> config_lookup = new HashMap<Integer, String[]>(); //Stores details about the neighboring peers
	private ArrayList<Integer> sellers = new ArrayList<Integer>(); //List of the sellers buyer gets after the full lookup
	private ArrayList<Integer> buyers = new ArrayList<Integer>(); //List of the buyers, mostly including only the self node Id
	
	/*Peer Constructor. 
	Initializing the local variables
	Starting the RMI registry and binding the peer stub to it.
	Initializing the config hash map of neighbor peers
	 */
	public Node(int node_id, String role,  int port_id, String item, int[] peers, Logger logger, int max_sell_items, String testcase) {
		this.role = role;
		this.node_id = node_id;
		this.port_id = port_id;
		this.item = item;
		this.peers = peers;
		this.logger = logger;
        this.max_sell_items=max_sell_items;
		this.buyers.add(this.node_id);
		this.m = max_sell_items;
		this.testcase = testcase;

		// Initializing the config Hash-map once the class loads from the config file
		// config_lookup is a variable of the Node class which is used to lookup port number and the corresponding ip for a given node
		try {
			File config = new File("config.txt");
			Scanner configScanner = new Scanner(config);
			HashMap<Integer, String[]> config_lookup_value = new HashMap<Integer, String[]>();
			configScanner.nextLine();
			while (configScanner.hasNextLine()) {
				String data = configScanner.nextLine();
				String[] node_info = data.split(" ", 3);
				config_lookup_value.put(Integer.parseInt(node_info[0]), new String[] { node_info[1], node_info[2] });
			}
			this.config_lookup = config_lookup_value;
			configScanner.close();
		} catch (FileNotFoundException e) {
			System.out.println("An error occurred.");
			e.printStackTrace();
		}
		
		try { 	    
	         // Exporting the object of implementation class  
	         // (here we are exporting the remote object to the stub) 
			 Hello stub = (Hello) UnicastRemoteObject.exportObject(this, 0);  
	         
	         System.out.println("IP1 "+this.port_id+" ready");
	         Registry registry = LocateRegistry.createRegistry(this.port_id);
	         
	         registry.bind(String.valueOf(this.node_id), stub);  
	         System.out.println("Peer "+this.node_id+" ready"); 
	         logger.info("Peer "+this.node_id+" ready");
	    } 
		catch (Exception e) { 
	         System.err.println("Peer exception: " + e.toString()); 
	         e.printStackTrace(); 
	    }
	}
	
//	TODO: generalize the the get_stub method
	
	//function to check the item availability, forwarding lookup request, and reply back to the buyer with the seller Id
	public void lookup_helper(String productname, int hopcount, ArrayList<Integer> buyers) {
		logger.info("Peer: "+ this.node_id +": The request came through the path: "+buyers);
		if(this.role.equals("seller") && productname.equals(this.item) && this.m>0) {
			logger.info("Peer:"+this.node_id+": I have "+this.m+" items of '"+this.item+"'");
			int last_node_index = buyers.size()-1;
    		int lastNodeId = buyers.get(last_node_index);
    		buyers.remove(last_node_index);
    		try {
    			logger.info("Peer: "+ this.node_id +": Sending back the reply to "+lastNodeId);
    			String neighbour_ip = this.config_lookup.get(lastNodeId)[1];
				int neighbour_port = Integer.parseInt(config_lookup.get(lastNodeId)[0]);
				Registry registry = LocateRegistry.getRegistry(neighbour_ip, neighbour_port); 
				Hello stub = (Hello) registry.lookup(String.valueOf(lastNodeId));
				stub.reply_helper(buyers, this.node_id);
    		}
    		catch (Exception e){
    			logger.info("Peer: "+ this.node_id +": Couldn't connect to peer "+lastNodeId+ " to buy.");
    			System.err.println("Client exception: " + e.toString()); 
		        e.printStackTrace();
    		}  		
		}
		else {
			if(this.m == 0 && this.role.equals("seller")) {
				//Restocking the items
				logger.info("Peer:"+this.node_id+": My items are finished. Restocking them.");
				int rnd = new Random().nextInt(this.items.length);
			    //this.item = this.items[rnd];
				if(this.testcase.equals("1")) {
					this.item = "Boar";
					this.m = max_sell_items; 
				}
				else if(!this.testcase.equals("2")) {
					this.item = this.items[rnd];
					this.m = this.max_sell_items;
				}
				
			}
			if(hopcount>0){
				if(this.role.equals("seller")) {
					logger.info("Peer: "+this.node_id+": I don't have '"+productname+"'. I have '"+this.item+"'"+". Forwarding the message to my other peers.");
				}
				buyers.add(this.node_id);
				for(int i= 0; i<this.peers.length;i++) {
					int neighbour_peer = peers[i];
					logger.info("Peer: "+ this.node_id +": Checking if I can go to "+neighbour_peer+ " to buy.");
					if(!buyers.contains(neighbour_peer)){
						logger.info("Peer: "+ this.node_id +": Yes!! I can go to "+neighbour_peer+ " to buy.");
						String neighbour_ip = this.config_lookup.get(neighbour_peer)[1];
						int neighbour_port = Integer.parseInt(this.config_lookup.get(neighbour_peer)[0]);
						try {
							Registry registry = LocateRegistry.getRegistry(neighbour_ip, neighbour_port); 
							Hello stub = (Hello) registry.lookup(String.valueOf(neighbour_peer));
							stub.lookup_helper(productname, hopcount-1, buyers);
						}
						catch(Exception e) {
							logger.info("Peer: "+ this.node_id +": Couldn't connect to peer "+neighbour_peer+ " to buy.");
							System.err.println("Client exception: " + e.toString()); 
							System.err.println("Peer: "+ this.node_id +": Couldn't connect to peer "+neighbour_peer+ " to buy.");
						}
					}
				}
			}
			else {
				logger.info("Peer: "+this.node_id+": Hopcount is finished. Can't forward the request to other peers.");
			}
		}
		   
	}
	
	//if seller: decrements the stock item if still left, otherwise state no more able to sell
	//if buyer: buys a particular item from the specified Seller
    public boolean buy(int sellerId) {

	    if(this.role.equals("seller")) {
	    	if(this.m == 0) {
	    		//this happens when another buyer consumed the item
	    		logger.info("Peer: "+ this.node_id +": Sorry, I sold my item to someone else. You are late.");
	    		return false;
	    	}
	    	else {
	    		this.m-=1;
	    		return true;
	    	}
		    
	    }
	    else {

	    	logger.info("Peer: "+ this.node_id +": I found my seller in peer "+sellerId);
	    	try {
	    		logger.info("Peer: "+ this.node_id +": Buying '"+this.item+ "' from peer "+sellerId);
	    		String neighbour_ip = this.config_lookup.get(sellerId)[1];
				int neighbour_port = Integer.parseInt(config_lookup.get(sellerId)[0]);
				Registry registry = LocateRegistry.getRegistry(neighbour_ip, neighbour_port); 
				Hello stub = (Hello) registry.lookup(String.valueOf(sellerId));
				boolean bought = stub.buy(this.node_id);
				return bought;
			}
			catch(Exception e) {
				logger.info("Peer: "+ this.node_id +": Couldn't connect to peer "+sellerId+ " to buy.");
				System.err.println("Client exception: " + e.toString()); 
				return false;
			}
	    }
	   
    }
	
    //A helper function to reply back to the peer from where the request originated
    public void reply_helper(ArrayList<Integer> trace_back_peers, int sellerId) {
    	logger.info("Peer: "+this.node_id+ ": traceback peers "+trace_back_peers);
    	if(trace_back_peers.size() == 0) {
    		//found the actual buyer
    		this.reply(this.node_id, sellerId);
    	}
    	else {
    		//trace back to actual buyer
    		int last_node_index = trace_back_peers.size()-1;
    		int lastNodeId = trace_back_peers.get(last_node_index);
    		trace_back_peers.remove(last_node_index);
    		try {
//    			logger.info("Peer: "+ this.node_id +": Sending back the reply to "+lastNodeId);
    			String neighbour_ip = this.config_lookup.get(lastNodeId)[1];
				int neighbour_port = Integer.parseInt(config_lookup.get(lastNodeId)[0]);
				Registry registry = LocateRegistry.getRegistry(neighbour_ip, neighbour_port); 
				Hello stub = (Hello) registry.lookup(String.valueOf(lastNodeId));
				stub.reply_helper(trace_back_peers, sellerId);
    		}
    		catch (Exception e){
    			System.err.println("Client exception: " + e.toString()); 
		        e.printStackTrace();
    		}
    	}
    }
	
    //To forward a lookup request to all the neighboring peers with the item buyer wants to buy
	private void lookup(String product_name, int hopcount) {

		logger.info("Peer: "+this.node_id+": Looking up '"+product_name+ "' in my neighbour peers.");
		if(hopcount>0) {
			for(int i= 0; i<this.peers.length;i++) {
				int neighbour_peer = peers[i];
				String neighbour_ip = this.config_lookup.get(peers[i])[1];
				int neighbour_port = Integer.parseInt(this.config_lookup.get(peers[i])[0]);
				try {
					Registry registry = LocateRegistry.getRegistry(neighbour_ip, neighbour_port); 
					Hello stub = (Hello) registry.lookup(String.valueOf(neighbour_peer));
					stub.lookup_helper(product_name, hopcount-1, this.buyers);
				}
				catch(Exception e) {
					System.err.println("Client exception: " + e.toString()); 
					System.err.println("Peer: "+this.node_id+": Not able to contact the peer "+neighbour_peer);
				}
				
			}
		}
	}
	
	//To note all the sellers which are ready to sell the items to this buyer
	private void reply(int buyerId, int sellerId) {
		this.sellers.add(sellerId);
	}
	
	//Initiating the peer to start buying or selling
	public void start() {
		logger.info("Peer: "+this.node_id+ ": role: "+this.role);
		if(this.role.equals("buyer")){

			while(true){
				//choosing a item randomly to buy
				if(this.testcase.equals("1")) {
					this.item = "Boar";
				}
				else if(!this.testcase.equals("2")) {
					int rnd = new Random().nextInt(this.items.length);
				    this.item = this.items[rnd];
				}

			    logger.info("Peer: "+this.node_id+ ": I want to buy '"+ this.item+"'");
				this.lookup(this.item, this.hopcount);
				logger.info("Peer: "+this.node_id+ ": The sellers are '"+ this.sellers+"'");
				if(this.sellers.isEmpty()) {
					//no seller found
					logger.info("Peer: "+this.node_id+ ": Couldn't find the item '"+ this.item+"'");
				}
				else {
					//choosing a seller randomly out of all the sellers who responded
					int rnd_seller = new Random().nextInt(this.sellers.size());
					boolean bought = this.buy(this.sellers.get(rnd_seller));
					if(bought) {
						logger.info("Peer: "+this.node_id+ ": Successfully bought '"+ this.item + "' from "+this.sellers.get(rnd_seller));
						//Clearing all sellers
						this.sellers.clear();
					}
					else {
						logger.info("Peer: "+this.node_id+ ": Couldn't buy '"+ this.item + "' from "+this.sellers.get(rnd_seller));
					}
					
				
				}
				
				//Waiting for 1 second before going for next buy
				try {
				    Thread.sleep(5 * 1000);
				} catch (InterruptedException ie) {
				    Thread.currentThread().interrupt();
				}
			}
		}
	}

    public static void main(String[] args){
		// int node_id, String role,  int port_id, String item, int[] peers, int testcase, Logger logger
		String node_id=args[0];
		String testcase = args[1];
		String role="";
		// ArrayList<Integer> PeerList = new ArrayList<Integer>();
		int[] PeerList= {};
		String item="";
		int max_items=0;
		int port_id=0;
		// 0 [3,1] seller boar 5
		
		try {
			File network = new File("network.txt");
			Scanner networkScanner = new Scanner(network);
			// HashMap<Integer, String[]> config_lookup_value = new HashMap<Integer, String[]>();
			System.out.println("node_info: 1");
			while (networkScanner.hasNextLine()) {
				System.out.println("node_info: 3");
				String data = networkScanner.nextLine();
				String[] node_info = data.split(" ");
				System.out.println("node_infodsafgsd: "+node_info[0]);
				if(node_info[0].equals(node_id)){
					node_info[1]=node_info[1].replace("[","");
	        		node_info[1]=node_info[1].replace("]","");
					String[] s1=node_info[1].split(",");
					PeerList=new int[s1.length];
					for (int i = 0; i < s1.length; i++){
						PeerList[i]=Integer.valueOf(s1[i]);
					}

					role=node_info[2];
					item=node_info[3];
					if(role.equals("seller")) {
						max_items=Integer.valueOf(node_info[4]);
					}
					
					break;
				}
				
				
			}
			networkScanner.close();
		} catch (FileNotFoundException e) {
			System.out.println("An error occurred. File not found.");
			e.printStackTrace();
		}
		System.out.println("node_info: 2");

		try{

			File config= new File("config.txt");
			Scanner configScanner = new Scanner(config);
			configScanner.nextLine();
			while (configScanner.hasNextLine()) {
				String data = configScanner.nextLine();
				String[] node_info = data.split(" ");
				if(node_info[0].equals(node_id)){
					port_id=Integer.valueOf(node_info[1]);
					break;
				}
			}
			configScanner.close();
		} catch (FileNotFoundException e) {
			System.out.println("An error occurred. File not found.");
			e.printStackTrace();
		}
		Logger logger = Logger.getLogger("MyLog");
		String filename=node_id+".log";
		FileHandler fh;
		try{
			fh = new FileHandler(filename);
			logger.addHandler(fh);
			fh.setFormatter(new MyFormatter());
			logger.info("Logger Initialized");	
		}
		catch(Exception e){
			e.printStackTrace();
		}
		
		System.out.println("Max item check:"+max_items);
		Node a=new Node(Integer.valueOf(node_id), role, port_id, item, PeerList,logger,max_items, testcase);
		a.start();
	}
}