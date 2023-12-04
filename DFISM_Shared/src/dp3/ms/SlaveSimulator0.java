package dp3.ms;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dfism.commons.Config;
import dfism.commons.Config.MiningModes;
import dfism.commons.GarbageCollector;
import dfism.fpo.FPOTree;
import dfism.fpo.FPSubset;
import dp3.p3e.PrePostPlusE;

public class SlaveSimulator0 {
	private static String LocalIPAddress;
	
	private static ServerSocket serverSocket;
	private static Socket socket;
	private static DataInputStream dis;
	private static DataOutputStream dos;
	
	private static PrePostPlusE alg;
	private static double threshold;
	private static int PART_COUNT = 1;
	private static int ID;
	
	private static final int PORT = 9000;
	
	public static void main(String[] args) {
		try{
			Config.parse();
			
			// Run in case Config.is_distributed_file_system = true
			LocalIPAddress = writeDownLocalIPAddress(Config.slave_address_directory, PORT);
			
			serverSocket = new ServerSocket(PORT);
			System.out.println("Slave IP Address: " + LocalIPAddress);
			System.out.println("Slave Port: " + PORT);
			System.out.println();
			
			//Listen a request from the master
			socket = serverSocket.accept();
			dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
			dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
			ID = dis.readInt();
			String[] parameters = dis.readUTF().split(" ");
			
			//Get the mining option
			MiningModes miningMode = MiningModes.valueOf(parameters[0].toUpperCase());
			switch(miningMode){
				case FROM_SCRATCH:
					fromScratch_Mining(parameters);
					break;
				case EXIT:
					System.out.println("Exit.");
					return;
				default:
					return;
			}
		}
		catch(Exception e){
			try {serverSocket.close();} catch (IOException ioe) {ioe.printStackTrace();}
			e.printStackTrace();
		} 
	}
	
	private static String writeDownLocalIPAddress(String IPAddressDirectory, int port) throws IOException {
		File dir = new File(IPAddressDirectory);
		if(!dir.exists()) dir.mkdirs();
		
        String ipAddress = InetAddress.getLocalHost().getHostAddress().trim();
        File file = new File(IPAddressDirectory+"/"+ipAddress+"_"+port);
        file.createNewFile();
        return ipAddress;
	}
	
	private static void fromScratch_Mining(String[] parameters) throws IOException, InterruptedException{
		long start = System.currentTimeMillis();
		
		String dataFileName = parameters[1];
		threshold = Double.parseDouble(parameters[2]);
		PART_COUNT = Integer.parseInt(parameters[3]);
		
		System.out.println("Slave ID: " + ID);
		System.out.println("Data file name: " + dataFileName);
		System.out.println("Support threshold: " + threshold);
		System.out.println("Part count: " + PART_COUNT);
		System.out.println("-----------------------------------------------------------------------------");
		
		alg = new PrePostPlusE(Config.input_data_directory+dataFileName, threshold);
		
		achieve_globalFrequentItems();
		
		System.out.println("-----------------------------------------------------------------------------");
		
		if(PART_COUNT < 2) achieve_globalFrequentItemsets(dataFileName);
		else achieve_globalFrequentItemsets_PARTS(dataFileName);
		
		System.out.println("-----------------------------------------------------------------------------");
		System.out.println("=> Totally local running time in (ms): " + (System.currentTimeMillis()-start));
		System.out.println("===================================FINISH====================================");
		System.out.println();
	}
	
	private static long achieve_globalFrequentItems() throws IOException, InterruptedException{
		long start = System.currentTimeMillis();
		
		System.out.println("=> Count support-count for items in (ms): " + alg.countSupportCountForItems());
		
		List<String> nameBasedOrderLocalFrequentItems = alg.getFrequentItemsInItemNameBasedOrder();
		System.out.println("Local frequent item count: " + nameBasedOrderLocalFrequentItems.size());
		
		// Sends local count of transactions
		dos.writeInt(alg.transactionCount());
		
		// Send local frequent items
		dos.writeInt(nameBasedOrderLocalFrequentItems.size());	// Send the number of items.
		for(String item : nameBasedOrderLocalFrequentItems) dos.writeUTF(item);	// Send items
		dos.flush();
		
		// Receive global potential items
		int item_count = dis.readInt();			// Receive the total number of potential items
		List<String> nameBasedOrderGlobalPotentialItems = new ArrayList<String>(item_count);
		for(int i=0; i<item_count; i++) nameBasedOrderGlobalPotentialItems.add(dis.readUTF());
		
		// Get local support counts of all global potential items
		int[] local_support_counts = alg.getSupportCountOfGlobalPotentialItems(nameBasedOrderGlobalPotentialItems);
		
		// Send local support counts of global potential items
		for(int support_count : local_support_counts) dos.writeInt(support_count);
		dos.flush();
		
		// Receive global frequent items, in increasing order of frequency
		item_count = dis.readInt();			// Receive the total number of global frequent items
		List<String> globalFrequentItems = new ArrayList<String>(item_count);
		for(int i=0; i<item_count; i++) globalFrequentItems.add(dis.readUTF());

		System.out.println("Global frequent item count: " + item_count);
		System.out.println("=> Prepare to build the local PPC tree in (ms): " + alg.prepareToConstructPPCTree(globalFrequentItems));
		
		return System.currentTimeMillis() - start;
	}
	
	private static void achieve_globalFrequentItemsets(String dataFileName) throws IOException, InterruptedException{
		// Local mining
		System.out.println("=> Build the local PPC tree in (ms): " + alg.constructPPCTree());
		System.out.println("=> Mine local frequent itemsets in (ms): " + alg.mining());
		
		// Build FPMTree from local frequent itemsets
		FPOTree fpm_tree = new FPOTree();
		fpm_tree.build_from_1IS(alg.itemCodesMaptoFrequencies, alg.supportCountThreshold());
		System.out.println("=> Time of building the local FPM tree in (ms): " + fpm_tree.build_from_kIS(alg.hFrequentPatterns, false));
		
		System.out.println("=> Time of counting all kinds of nodes in (ms): " + fpm_tree.count_nodes());
		System.out.println("=> Time of sending the local FPM tree in (ms): " + fpm_tree.send_only_itemCode(dos));
		
		System.out.println("-------------------The information of the local FPM tree---------------------");
		System.out.println("Total node count: " + fpm_tree.getNodeCount());
		System.out.println("\tInner node count: " + fpm_tree.getInnerNodeCount());
		System.out.println("\tLeaf node count: " + fpm_tree.getLeafNodeCount());
		
		fpm_tree.free(); fpm_tree = new FPOTree();
		System.out.println("GC time in (ms): " + GarbageCollector.collectMemory());
		
		System.out.println("=> Time of receiving the global FPM tree in (ms): " + fpm_tree.receive_only_itemCode(dis));
		
		System.out.println("=> Time of updating support counts in (ms): " + fpm_tree.update_supportCount(alg));
		System.out.println("=> Time of sending the array of support counts in (ms): " + 
													fpm_tree.send_supportCountArray_toMaster(dos));
	}
	
	private static void achieve_globalFrequentItemsets_PARTS(String dataFileName) throws IOException, InterruptedException{
		// Local mining
		System.out.println("=> Build the local PPC tree in (ms): " + alg.constructPPCTree());
		System.out.println("=> Mine local frequent 2-itemset in (ms): " + alg.mining_frequent_2itemsets());
		
		// Build FPMTree from local frequent 2-itemsets
		FPOTree fpm_tree = new FPOTree();
		System.out.println("=> Build FPM-Tree from local frequent 2-itemsets in (ms): " + 
												fpm_tree.build_from_kIS(alg.hFrequentPatterns, true));
		fpm_tree.count_nodes();
		
    	// Achieve the all global potential 2-itemsets 
		System.out.println("Send FPM-Tree of local frequent 2-itemsets to Master in (ms): " + fpm_tree.send_only_itemCode(dos));
		fpm_tree.free(); fpm_tree = new FPOTree();
		System.out.println("Wait and receive global FPM-Tree of potential 2-itemsets in (ms): " + fpm_tree.receive_only_itemCode(dis));
		String[][] potential_2itemset_2D = fpm_tree.get_2itemsets(PART_COUNT);
		fpm_tree.free(); fpm_tree = null;	// Free memory
		
		System.out.println();
		System.out.println("------------------LOOP of Achieving Global Frequent Itemsets-----------------");
		for(int i=0; i<PART_COUNT; i++) {
			System.out.println("----------------------------------PART " + i + "-------------------------------------");
			mine_frequentKItemsetsPART_buildAndSend_FPMTree(potential_2itemset_2D[i]);
			System.out.println("GC time in (ms): " + GarbageCollector.collectMemory());
			receive_update_send_globalFPMTree();
			System.out.println("GC time in (ms): " + GarbageCollector.collectMemory());
		}
	}
	
	private static long mine_frequentKItemsetsPART_buildAndSend_FPMTree(String[] potential_2itemsets) throws InterruptedException, IOException{
		long start = System.currentTimeMillis();
		
		Map<String, FPSubset> hFrequentPatterns_PART = new HashMap<String, FPSubset>(potential_2itemsets.length);
		System.out.println("=> Time of discovering subspaces of frequent k-itemsets in (ms): " +
					alg.mining_frequent_kItemsets_PART(potential_2itemsets, hFrequentPatterns_PART));
		FPOTree fpm_tree = new FPOTree();
		System.out.println("=> Time of building the FPM tree in (ms): " + fpm_tree.build_from_kIS(hFrequentPatterns_PART, false));
		System.out.println("=> Time of counting all kinds of nodes in (ms): " + fpm_tree.count_nodes());
		System.out.println("=> Time of sending the FPM tree in (ms): " + fpm_tree.send_only_itemCode(dos));
		
		return System.currentTimeMillis() - start;
	}
	
	private static long receive_update_send_globalFPMTree() throws InterruptedException, IOException{
		long start = System.currentTimeMillis();
		
		FPOTree fpm_tree = new FPOTree();
		System.out.println("=> Time of receiving the global FPM tree in (ms): " + fpm_tree.receive_only_itemCode(dis));
		System.out.println("=> Time of updating support counts in (ms): " + fpm_tree.update_supportCount(alg));
		System.out.println("=> Time of sending the array of support counts in (ms): " + fpm_tree.send_supportCountArray_toMaster(dos));
		alg.free(); 		// Revoke memory to used for other LOOPs
		
		return System.currentTimeMillis() - start;
	}
}
