package dp3.ms;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

import dfism.commons.Config;
import dfism.commons.Config.MiningModes;
import dfism.commons.GarbageCollector;
import dfism.commons.ItemSupport;
import dfism.commons.MemoryLogger;
import dfism.commons.SlaveInfo;
import dfism.fpo.FPOTree;
import dfism.threads.GlobalFrequentItemsSendingThread;
import dfism.threads.GlobalPotentialItemsSendingThread;
import dfism.threads.Local2ItemsetsFPOTreeReceivingThread;
import dfism.threads.LocalFPOTreeReceivingThread;
import dfism.threads.LocalFrequentItemsMergingThread;
import dfism.threads.LocalFrequentItemsReceivingThread;
import dfism.threads.LocalSupportCountOfPotentialItemsReceivingThread;
import dfism.threads.ParameterSendingThread;
import dfism.threads.SupportArrayReceivingThread;
import dp3.p3e.PrePostPlusE;

import dfism.commons.GarbageCollector;
import dfism.commons.IntHolder;
import dfism.commons.Matrix;
import dfism.commons.MemoryLogger;
import dfism.fpo.FISMAlgorithm;
import dfism.fpo.FPSubset;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// Note: Most parts of this algorithm follow a similair structure as the DP3 algorithm, this is for
// making imports more convinient. Parts are reused from DP3 in both the Master and Slave.
public class Master {
	// From DP3
	private static MemoryLogger memoryLogger = MemoryLogger.getInstance();

	private static ArrayList<SlaveInfo> slaves = new ArrayList<SlaveInfo>();

	private static int E_SLAVES_COUNT;
	private static int support_count_threshold;
	private static int TOTAL_TRANS_COUNT;
	private static int frequent_itemset_count;

	private static PrePostPlusE alg;

	private static ArrayList<ItemSupport> global_frequent_items;
	private static String[] index_glbFrequentItems;

	private static final AtomicInteger taskCount = new AtomicInteger(0);
	private static final BlockingQueue<String> taskQueue = new LinkedBlockingQueue<>();
	private static final BlockingQueue<String> finishSignalQueue = new LinkedBlockingQueue<>();

    // For part 3
	private static final String TASK_REQUEST = "TASK_REQUEST";
	private static final String NO_MORE_TASK = "NO_MORE_TASK";
	private static final String TASK_COMPLETED = "TASK_COMPLETED";
	private static final String NUM_FREQ_ITEMSETS = "NUM_FREQ_ITEMSETS";
	private static final BlockingQueue<Integer> slaveResultsQueue = new LinkedBlockingQueue<>();

	public static void main(String[] args) throws Exception {
		System.out.println("Master's IP Address: " + InetAddress.getLocalHost().getHostAddress().trim());

		// Get running configuration
		Config.parse();

		// Get input parameters
		if(Config.is_auto_mode){
			System.out.println("Operate in the automatic mode.");
			if(Options.parse(args)) System.out.println(Options.getString());
			else return;
		}else {
			System.out.println("Operate in the interative mode.");
			if(Master.getParameterFromCommandLine()) System.out.println(Options.getString());
			else return;
		}

		// Get slave information
		ArrayList<String> input_filename_list = null;
		if(Config.is_distributed_file_system){
			System.out.println("Nodes use a global distributed file system for input datasets.");
			input_filename_list = new ArrayList<String>();
			File input_dir = new File(Config.input_data_directory);
			for(File file : input_dir.listFiles()){
				if(file.isDirectory()) continue;
				if(file.getName().contains(Options.filter_filename)) input_filename_list.add(file.getName());
			}
			E_SLAVES_COUNT = input_filename_list.size();

			// Initialize
			int try_count = 0;
			while(try_count < 6){
				try_count++;
				slaves.clear();
				Master.getSlavesInfo_dfs(Config.slave_address_directory, slaves);
				if(E_SLAVES_COUNT > slaves.size()){
					System.out.println("The expected slave count is not enough. Wait for 5 seconds then try again.");
					Thread.sleep(5000);
					continue;
				}else break;
			}
		} else{
			System.out.println("Nodes use their own local disks for input datasets.");
			Master.getSlavesInfo_localdisk(Config.slave_address_file, slaves);
			E_SLAVES_COUNT = slaves.size();
		}

		System.out.println("# Expected salves: " + E_SLAVES_COUNT);
		System.out.println("# Total of slaves: " + slaves.size());

		// Check the number of slaves is enough to start mining job
		if(E_SLAVES_COUNT > slaves.size()){
			Master.establishConnections(slaves);
			Master.sendParameters_toSlaves(slaves, MiningModes.EXIT.name());	// Shutdown all slaves
			System.out.println("The expected slave count is still not enough. Exit.");
			return;
		}else if (E_SLAVES_COUNT < slaves.size()){	// Fix the number of slaves = E_SLAVES_COUNT
			ArrayList<SlaveInfo> used_slaves = new ArrayList<SlaveInfo>(E_SLAVES_COUNT);
			ArrayList<SlaveInfo> unused_slaves = new ArrayList<SlaveInfo>(slaves.size() - E_SLAVES_COUNT);
			int i=0;
			for(; i<E_SLAVES_COUNT; i++) used_slaves.add(slaves.get(i));
			for(; i<slaves.size(); i++) unused_slaves.add(slaves.get(i));
			Master.establishConnections(unused_slaves);
			Master.sendParameters_toSlaves(unused_slaves, MiningModes.EXIT.name());	// Shutdown unused slaves
			slaves = used_slaves;
		}

		// Start mining job
		long start = System.currentTimeMillis();

		if(!Master.establishConnections(slaves)){
			System.out.println("The number of connections to active slaves is not enough for the mining job. Exit.");
			Master.sendParameters_toSlaves(slaves, MiningModes.EXIT.name());
			return;
		}

		System.out.println("------------------------------Master sends parameters to slaves------------------------------");
		String[] parameter_array = Master.buildParameterStringForSlaves(MiningModes.FROM_SCRATCH.name(), input_filename_list);
		if(Config.is_distributed_file_system){
			Master.sendParameters_toSlaves(slaves, parameter_array);
		}else{
			Master.sendParameters_toSlaves(slaves, parameter_array[0]);
		}

		if(cooperate_with_slaves()) System.out.println("Successful");

		System.out.println("Total count of frequent itemsets: " + Master.frequent_itemset_count);
		System.out.println("Total running time (ms): " + (System.currentTimeMillis() - start));
		System.out.println("Garbage Collector took time in (ms): " + GarbageCollector.getGarbageCollectionTime());
		System.out.println("Peak Memory (MB): " + memoryLogger.getMaxUsedMemory());
		System.out.println("===========================================FINISH============================================");
	}

	private static boolean getParameterFromCommandLine(){
		Options.printHelp();

		Scanner scanInput;
		while(true){
			System.out.print("Enter parameters: ");
			scanInput = new Scanner(System.in);

			if(Options.parse(scanInput.nextLine().trim().split(" "))){
				scanInput.close();
				return true;
			}else{
				System.out.println("Wrong parameters");
			}

			System.out.print("Continue? (y/n): ");
			if(scanInput.nextLine().trim().equalsIgnoreCase("n")){
				scanInput.close();
				return false;
			}
		}
	}

	/**
	 * Each slave when starting up will write down its ip address and port as the filename to the directory 'dir'
	 * </br> This procedure gets information from all slaves through all file names in the directory 'dir'
	 * @param dir
	 * @param slaves
	 */
	private static void getSlavesInfo_dfs(String dir, ArrayList<SlaveInfo> slaves){
		File slaveAddressDir = new File(dir);
		File[] listOfFiles = slaveAddressDir.listFiles();
		for (int i = 0; i < listOfFiles.length; i++) {
			if (listOfFiles[i].isFile()){
				String[] info = listOfFiles[i].getName().split("_");
				SlaveInfo slaveInfo = new SlaveInfo(info[0], Integer.parseInt(info[1]));
				slaves.add(slaveInfo);
			}
		}
	}

	/**
	 * In case nodes use their own local disks for partial dataset, the address and port of nodes must be aware in
	 * advance and configured in a file name 'filename'
	 * @param filename
	 * @param slaves
	 */
	private static void getSlavesInfo_localdisk(String filename, ArrayList<SlaveInfo> slaves){
		try {
			BufferedReader input = new BufferedReader(new FileReader(filename));
			String line;
			String[] info;
			while((line = input.readLine()) != null) {
				info = line.trim().split("_");
				if(info[0].charAt(0) == '#') continue;
				SlaveInfo slaveInfo = new SlaveInfo(info[0], Integer.parseInt(info[1]));
				slaves.add(slaveInfo);
			}
			input.close();
		} catch (NumberFormatException | IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Establish and be sure that all connections to slaves are ready.
	 * @return true if all connections to all slaves are all ready.
	 */
	private static boolean establishConnections(List<SlaveInfo> slaves){
		boolean isReady = true;
		for(SlaveInfo slave : slaves){
			try {
				slave.socket = new Socket(slave.ip_address, slave.port);
			} catch (IOException e) {
				System.out.println(e.getMessage());
				isReady = false;
			}
		}
		return isReady;
	}


	/**
	 * Send the parameters to slaves. Each thread sends to one slave.
	 * @param slaves
	 * @param parameters
	 * @return true if successful, otherwise failed
	 */
	private static boolean sendParameters_toSlaves(ArrayList<SlaveInfo> slaves, String[] parameters){
		try{
			int thread_count = slaves.size();
			Thread[] threads = new Thread[thread_count];
			for(int i=0; i<thread_count; i++){
				threads[i] = new ParameterSendingThread(i, slaves.get(i).socket, parameters[i]);
				threads[i].start();
			}
			for(int i=0; i<threads.length; i++) threads[i].join();

			return true;
		}catch(Exception e){
			System.out.println(e.getMessage());
			return false;
		}
	}

	private static String[] buildParameterStringForSlaves(String mining_mode, ArrayList<String> input_filename_list){
		StringBuilder sb = new StringBuilder(100);

		if(Config.is_distributed_file_system){
			String[] parameter_array = new String[input_filename_list.size()];
			int index=0;
			for(String filename : input_filename_list){
				parameter_array[index] = sb.append(mining_mode).append(' ').append(filename).append(' ')
						.append(Options.support_threshold).append(' ').append(Options.part_count).toString();
				index++;
				sb.setLength(0);
			}
			return parameter_array;
		}else{
			sb.append(mining_mode).append(' ').append(Options.filter_filename).append(' ')
					.append(Options.support_threshold).append(' ').append(Options.part_count);
			return new String[]{sb.toString()};
		}
	}

	/**
	 * Close all connection to slaves
	 * @param slaves
	 */
	@SuppressWarnings("unused")
	private static void closeConnections(List<SlaveInfo> slaves){
		try{
			for(SlaveInfo slave : slaves){
				if(slave.socket != null && slave.socket.isConnected()){
					slave.socket.close();
				}
			}
		}catch(Exception e){
			System.out.println(e.getMessage());
		}
	}

	//////////////////////////////////////////////////////////////////////////////////////////////





	//////////////////////////////////////////////////////////////////////////////////////////////

	// building a PPC tree; getting nlists and 2-itemsets
	private static long constructLocalPPCTree(List<String> globalFrequentItems) throws IOException, InterruptedException {
		long start = System.currentTimeMillis();

		// Methods from PrePostPlusE
		System.out.println("=> Prepare to build the local PPC tree in (ms): " + alg.prepareToConstructPPCTree(globalFrequentItems));
		System.out.println("=> Build the local PPC tree in (ms): " + alg.constructPPCTree());
		System.out.println("=> Mine local frequent itemsets in (ms): " + alg.mining());

		System.out.println("=> Time of generating node lists for frequent items (ms): " + alg.create_nodelist_for_items());

		System.out.println("=> Time of generating frequent 2-itemsets (ms): " + alg.generate_frequent_2itemsets());
		System.out.println("Number of frequent 2-itemsets: " + alg.hFrequentPatterns.size());

		// Freeing up memory by clearing the PPC tree, as in DP3
		alg.root.children.clear();
		alg.root.children = null;
		alg.root = null;
		System.out.println("GC time in (ms): " + GarbageCollector.collectMemory());

		// Generating nodelists for each frequent 2-itemset
		String[] frequent_2itemsets = new String[alg.hFrequentPatterns.size()];
		alg.hFrequentPatterns.keySet().toArray(frequent_2itemsets);
		System.out.println("=> Time of generating node list for frequent 2-itemsets (ms): " +
				alg.create_nodelist_for_frequent_2itemsets(frequent_2itemsets));

		// Memory check
		memoryLogger.checkMemory();
		System.out.println("=> Memory used in (MB): " + memoryLogger.getMaxUsedMemory());

		ArrayList<SlaveInfo> slaves = new ArrayList<SlaveInfo>();


		// Establish connections to slaves
		if (!establishConnections(slaves)) {
			System.out.println("Failed to establish connections to all slaves. Exiting.");
			return -1;
		}

		// Send parameters to slaves
		String[] parameterArray = buildParameterStringForSlaves(MiningModes.FROM_SCRATCH.name(), inputFilenameList);
		if (sendParametersToSlaves(slaves, parameterArray)) {
			System.out.println("Parameters sent to slaves successfully.");
		} else {
			System.out.println("Failed to send parameters to slaves. Exiting.");
			return -1;
		}


		// Receive and process information from slaves
		Thread[] receivingThreads = new Thread[slaves.size()];
		for (int i = 0; i < slaves.size(); i++) {
			receivingThreads[i] = new SlaveDataReceivingThread(i, slaves.get(i).socket);
			receivingThreads[i].start();
		}

		// Wait for all receiving threads to finish
		for (Thread thread : receivingThreads) {
			thread.join();
		}

		return System.currentTimeMillis() - start;

	}

	private static boolean cooperate_with_slaves() {
		try {
			long constructionTime = constructLocalPPCTree(global_frequent_items);
			System.out.println("PPC-tree construction time: " + constructionTime + " ms");

			// After achieving global frequent itemsets
			System.out.println("=> Write global frequent items in (ms): " + write_global_frequent_items());

			// Send frequent items and N-lists of 2-itemsets to all slaves
			sendGlobalFrequentItemsAndNListsToSlaves(slaves);


		} catch (InterruptedException | IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	private static void sendGlobalFrequentItemsAndNListsToSlaves(ArrayList<SlaveInfo> slaves) {
		String globalFrequentItemsString = convertGlobalFrequentItemsToString(global_frequent_items);
		String nListsOf2ItemsetsString = convertNListsOf2ItemsetsToString(...);

		String message = "FREQUENT_ITEMS_N_LISTS " + globalFrequentItemsString + " " + nListsOf2ItemsetsString;

		sendToAllSlaves(slaves, message);
	}

	private static long write_global_frequent_items() throws IOException{
		long start = System.currentTimeMillis();
		String result_filename = Config.output_data_directory + Options.filter_filename +
				("_" + Options.support_threshold + "_items").replace(".", "");

		BufferedWriter output = new BufferedWriter(new FileWriter(result_filename));
		StringBuilder sb = new StringBuilder();
		for(ItemSupport itemSupport : global_frequent_items){
			output.write(sb.append(itemSupport.item).append(':').append(itemSupport.support).append('\n').toString());
			sb.setLength(0);
		}
		output.flush();
		output.close();
		Master.frequent_itemset_count = global_frequent_items.size();

		return System.currentTimeMillis()-start;
	}

	private static void sendTaskRequest(SlaveInfo slave) {
		sendToSlave(slave, TASK_REQUEST);
	}

	private static void sendNoMoreTask(SlaveInfo slave) {
		sendToSlave(slave, NO_MORE_TASK);
	}

	private static void sendToSlave(SlaveInfo slave, String message) {
		try (PrintWriter out = new PrintWriter(slave.socket.getOutputStream(), true)) {
			out.println(message);
		} catch (IOException e) {
			System.out.println("Error sending message to slave: " + e.getMessage());
		}
	}

	private static void handleSlaveRequests(List<SlaveInfo> slaves, List<String> frequent2Itemsets) {
		// Send tasks to slaves
		for (SlaveInfo slave : slaves) {
			sendTaskRequest(slave);
		}

		// Keep track of tasks sent
		int tasksSent = slaves.size();

		// Continue distributing tasks until there are no more tasks
		while (tasksSent > 0) {
			try {
				// Wait for a slave to complete a task and receive the result
				int numFreqItemsets = slaveResultsQueue.take();

				// Process the result (e.g., accumulate totals)
				frequent_itemset_count += numFreqItemsets;

				// Send a new task to the slave that just completed
				sendTaskRequest(slaves.get(tasksSent % slaves.size()));
				tasksSent++;
			} catch (InterruptedException e) {
				System.out.println("Error handling slave requests: " + e.getMessage());
			}
		}

		// Notify all slaves that there are no more tasks
		for (SlaveInfo slave : slaves) {
			sendNoMoreTask(slave);
		}

		// Collect the final results
		int totalFreqItemsets = 0;
		for (SlaveInfo slave : slaves) {
			totalFreqItemsets += slaveResultsQueue.poll();
		}

	}

	// Define a method to handle a connection from a slave and process its requests
	private static void handleSlaveConnection(Socket slaveSocket) {
		try (BufferedReader in = new BufferedReader(new InputStreamReader(slaveSocket.getInputStream()));
			 PrintWriter out = new PrintWriter(slaveSocket.getOutputStream(), true)) {

			String request;
			while ((request = in.readLine()) != null) {
				switch (request) {
					case TASK_REQUEST:
						// Process task request from slave
						String task = getNextTaskFromQueue(); // Implement this method to get the next task
						if (task != null) {
							out.println(task);
						} else {
							// No more tasks
							out.println(NO_MORE_TASK);
						}
						break;

					case TASK_COMPLETED:
						// Process completion notification from slave
						int numFreqItemsets = Integer.parseInt(in.readLine());
						slaveResultsQueue.put(numFreqItemsets);
						break;

					default:
						System.out.println("Unexpected request from slave: " + request);
						break;
				}
			}
		} catch (IOException | InterruptedException e) {
			System.out.println("Error handling slave connection: " + e.getMessage());
		}
	}

	private static String getNextTaskFromQueue() {
		try {
			// Attempt to retrieve the next task from the queue, waiting if necessary
			return taskQueue.poll(1, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			// Handle the interruption (e.g., log the error)
			System.err.println("Error getting next task from queue: " + e.getMessage());
			Thread.currentThread().interrupt();  // Restore interrupted status
			return null;
		}

}


