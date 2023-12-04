package dp3.p3e;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import dfism.commons.GarbageCollector;
import dfism.commons.IntHolder;
import dfism.commons.Matrix;
import dfism.commons.MemoryLogger;
import dfism.fpo.FISMAlgorithm;
import dfism.fpo.FPSubset;

/**
 * PrePostPlusE (PrePostPlus Enhanced) is an improved version of PrePostPlus.
 * However, this is a distributed version of PrePostPlusE with the same class name; so there are some modifies compared to
 * the serial version.
 * </br><b>Improved details:</b> (Compare with PrePostPlus) The following improvements come from algorithm: IFINPLUSS. 
 * </br>- When a dataset has a huge number of distinct items, and small enough support threshold is selected; the branches of the PPC
 * become larger and finding nodes to be merged with items in the transactions becomes to take more overhead.
 * </br>- Maintain itemname-based order for child nodes of parent nodes. 
 * Thank to that order, binary search is utilized and it reduces considerably the running time for the tree construction.
 * </br>- Parallelization for phases: mining frequent 2-itemsets, generate nodelist for frequent 2-itemsets,
 * mining frequent k-itemsets (k>2)
 * @author Van Quoc Phuong Huynh
 */
public class PrePostPlusE extends FISMAlgorithm {

	///////////////////////////////////////////////PROPERTIES SECTION//////////////////////////////////////////////
	private PPCNode root;
	
	private String fileName = null;
	private double threshold;
    private int TRANSACTION_COUNT;
    private final int THREAD_COUNT = Math.max(1, Runtime.getRuntime().availableProcessors()/2);
    
    private Map<String, Nodelist> item_nodelist;
    private Map<String, NodelistSubset> hk_itemset_nodelist;
    
    //public List<String> globalIndex_item;				// global index -> item		// inherit from FISMAlgorithm
    //public Map<String, Integer> item_globalIndex;		// item -> global index		// inherit from FISMAlgorithm
    
    //public Map<String, FPSubset> hFrequentPatterns;					// inherit from FISMAlgorithm
    //public Map<String, Integer> itemsMaptoFrequencies;				// inherit from FISMAlgorithm
    //public Map<Integer, Integer> itemCodesMaptoFrequencies;			// inherit from FISMAlgorithm
    //protected Matrix matrix;											// inherit from FISMAlgorithm
    //protected int SUPPORT_COUNT_THRESHOLD								// inherit from FISMAlgorithm
    
    public int transactionCount(){
    	return TRANSACTION_COUNT;
    }
    
    public PPCNode getRoot() {
		return root;
	}
    
    public PrePostPlusE(String fileName, double threshold) throws FileNotFoundException {
        this.threshold = threshold;
        this.fileName = fileName;
    }
    
	///////////////////////////////////////////////FUNCTIONS SECTION//////////////////////////////////////////////
    
    public long countSupportCountForItems() throws IOException {
    	Map<String, Integer> itemsMaptoFrequencies = this.itemsMaptoFrequencies = new HashMap<String, Integer>();
		
    	// Remember start time
    	long start = System.currentTimeMillis();
    	
    	// Count the frequency for each item
    	BufferedReader input = new BufferedReader(new FileReader(this.fileName));
    	
    	int trans_count = 0;
    	String line;
    	String[] item_list;
    	while ((line = input.readLine()) != null) {
        	trans_count++;
            item_list = line.split(" ");
            for(String item : item_list) {
                if (itemsMaptoFrequencies.containsKey(item)) {
                    itemsMaptoFrequencies.put(item, itemsMaptoFrequencies.get(item) + 1);
                } else {
                    itemsMaptoFrequencies.put(item, 1);
                }
            }
    	}
        input.close();
        this.TRANSACTION_COUNT = trans_count;
        this.SUPPORT_COUNT_THRESHOLD = (int)(trans_count*this.threshold);
        
        // Return time of building PPC tree
        return System.currentTimeMillis() - start;
    }
    
    /**
     * Sort increasingly item-name-based the list of frequent items.
     * @return the item-name-based order list of frequent items
     */
    public List<String> getFrequentItemsInItemNameBasedOrder(){
    	List<String> nameBasedOrderFrequentItem = new ArrayList<String>();
    	
    	int compare, mid, position, size, support_count_threshold = this.SUPPORT_COUNT_THRESHOLD;
    	String mid_item;
    	
    	for (Entry<String, Integer> entry : this.itemsMaptoFrequencies.entrySet()) {
    		// Only add frequent items
    		if(entry.getValue() < support_count_threshold) continue;
    		
    		position = 0; size = nameBasedOrderFrequentItem.size();
    		// Find position for new item
            while (position < size){
                mid = (position + size) / 2;
                mid_item = nameBasedOrderFrequentItem.get(mid);
                compare = mid_item.compareTo(entry.getKey());
                
                if (compare < 0) position = mid + 1;
                else if(compare > 0) size = mid;
                else { 
                	position = mid;
                	break;
                }
            }
            
            nameBasedOrderFrequentItem.add(position, entry.getKey());
    	}
    	
    	return nameBasedOrderFrequentItem;
    }
    
    public int[] getSupportCountOfGlobalPotentialItems(List<String> nameBasedOrderGlobalPotentialItems){
    	int[] local_support_counts = new int[nameBasedOrderGlobalPotentialItems.size()];
    	
    	Integer item_freq;
    	int index = 0;
    	for(String item : nameBasedOrderGlobalPotentialItems){
    		item_freq = itemsMaptoFrequencies.get(item);
    		if(item_freq == null) local_support_counts[index] = 0;
    		else local_support_counts[index] = item_freq;
    		index++;
    	}
    	
    	return local_support_counts;
    }
    
    /**
     * - 'globalIndex_Item' <== 'globalFrequentItems'
     * </br>- Prepare item_globalIndex based on items in 'globalFrequentItems'
     * </br>- Prepare itemCodesMaptoFrequencies based on items in 'globalFrequentItems'
     * @param globalFrequentItems
     * @return running time
     */
    public long prepareToConstructPPCTree(List<String> globalFrequentItems){
    	long start = System.currentTimeMillis();
    	
    	int size = globalFrequentItems.size();
    	this.globalIndex_item = globalFrequentItems;
    	this.item_globalIndex = new HashMap<String, Integer>(size);
    	this.itemCodesMaptoFrequencies = new HashMap<Integer, Integer>(size);
    	
    	Integer item_freq;
    	String item;
    	for(int i=0; i < size; i++){
    		item = globalFrequentItems.get(i);
    		// Prepare 'item_globalIndex'
    		item_globalIndex.put(item, i);
    		
    		// Prepare 'itemCodesMaptoFrequencies'
    		item_freq = itemsMaptoFrequencies.get(item);
    		if(item_freq == null) item_freq = 0;
    		this.itemCodesMaptoFrequencies.put(i, item_freq);
    	}
    	itemsMaptoFrequencies = null;	// No longer need
    	
    	return System.currentTimeMillis()-start;
    }
    
    public long constructPPCTree() throws IOException {
    	long start = System.currentTimeMillis();
    	
    	Map<String, Integer> item_globalIndex = this.item_globalIndex;
		
    	// Create root PPCTree node
        root = new PPCNode();

        // Scan transaction by transaction and insert into the PPC tree
        BufferedReader input = new BufferedReader(new FileReader(this.fileName));
        String line;
        String[] item_list;
        int[] sortedFrequentItemsTransaction = new int[this.globalIndex_item.size()];
        int count = 0;
        Integer index;
        
        while ((line = input.readLine()) != null) {
        	item_list = line.split(" ");
        	count=0;
        	
            for(String item : item_list) {
            	index = item_globalIndex.get(item);
            	if(index == null) continue;
                sortedFrequentItemsTransaction[count] = index;
                count++;
            }
            // Sort increasing, so the inverted order of items for insertTransaction
            Arrays.sort(sortedFrequentItemsTransaction, 0, count);
            
            // Insert the prepared transaction into the PPC tree
            this.insertTransaction(sortedFrequentItemsTransaction, count, root);
        }
        input.close();
        
        // Traverse the PPC tree with pre&post-order and assign two unique codes for each node
        PrePostPlusUtility.assignPrePosOrderCode(root);
        
        return System.currentTimeMillis() - start;
    }

    private void insertTransaction(int[] transaction, int length, PPCNode ppcNode) {
        PPCNode newNode, mid_child, subNode = ppcNode;
        boolean wasNotMerged;
        int itemCode, position, mid, size;

        for(int i=length-1; i>-1; i--){
        	itemCode = transaction[i];	// Item codes in the transaction are in increasing order
            wasNotMerged = true;
            position = 0;
        	size = subNode.children.size();
        	
        	// Binary search on the itemCode-based ordered child node list of subTree
        	while (position < size) {
                mid = (position + size) / 2;
                mid_child = subNode.children.get(mid);
                
                if (mid_child.itemCode < itemCode) position = mid + 1;
                else if (mid_child.itemCode > itemCode) size = mid;
                else {
                	mid_child.count++;
                	subNode = mid_child;
                    wasNotMerged = false;
                    break;
                }
            }
            
            if (wasNotMerged) {
            	newNode = new PPCNode(itemCode, subNode, 1);
            	subNode.children.add(position, newNode);
            	subNode = newNode;
            }
        }
    }
    
    /**
     * This function discovers all frequent k-itemsets and their nodelists, which are maintained in hFrequentPatterns
     * @throws InterruptedException 
     * @return running time
     * @throws InterruptedException
     */
    public long mining() throws InterruptedException {
    	// Remember start time
    	long start = System.currentTimeMillis();
    	MemoryLogger memoryLogger = MemoryLogger.getInstance();
        
        // Create node list for each frequent items
        System.out.println("Time of generating node lists for frequent items (ms): " + this.create_nodelist_for_items());
        memoryLogger.checkMemory();
		System.out.println("=> Memory used in (MB): " + memoryLogger.getMaxUsedMemory());
        
        // In parallel way, generate and add frequent 2-itemsets to 'hFrequentPatterns'
        System.out.println("Time of generating frequent 2-itemsets (ms): " + this.generate_frequent_2itemsets());
        System.out.println("Number of frequent 2-itemsets: " + this.hFrequentPatterns.size());
        
        // At this point, the PPC tree is no longer needed. It can be free
        this.root.children.clear(); this.root.children = null; this.root = null;
        System.out.println("GC time in (ms): " + GarbageCollector.collectMemory());
        
        // In parallel way, generate nodelist for each frequent 2-itemset.
        String[] frequent_2itemsets = new String[this.hFrequentPatterns.size()];
        this.hFrequentPatterns.keySet().toArray(frequent_2itemsets);
        System.out.println("Time of generating node list for frequent 2-itemsets (ms): " +
        							this.create_nodelist_for_frequent_2itemsets(frequent_2itemsets));
        memoryLogger.checkMemory();
		System.out.println("=> Memory used in (MB): " + memoryLogger.getMaxUsedMemory());
        
        // Discover all remaining frequent k-itemsets (k>2) from each frequent 2-itemset
        System.out.println("Time of generating the remaining k-itemsets (ms): " + 
        							this.discover_frequent_kItemsets(frequent_2itemsets));
        
        // Return mining time
        return System.currentTimeMillis() - start;
    }
    
    /**
     * This function discovers all frequent 2-itemsets and their nodelists, which are maintained in hFrequentPatterns
     * @throws InterruptedException 
     * @return running time
     * @throws InterruptedException
     */
    public long mining_frequent_2itemsets() throws InterruptedException {
    	// Remember start time
    	long start = System.currentTimeMillis();
    	MemoryLogger memoryLogger = MemoryLogger.getInstance();
        
        // Create node list for each frequent items
        System.out.println("Time of generating node lists for frequent items (ms): " + this.create_nodelist_for_items());
        
        // In parallel way, generate and add frequent 2-itemsets to 'hFrequentPatterns'
        System.out.println("Time of generating frequent 2-itemsets (ms): " + this.generate_frequent_2itemsets());
        System.out.println("Number of frequent 2-itemsets: " + this.hFrequentPatterns.size());
        
        // At this point, the PPC tree is no longer needed. It can be free to save a lot memory
        this.root.children.clear(); this.root.children = null; this.root = null;
        System.out.println("GC time in (ms): " + GarbageCollector.collectMemory());
        
        // In parallel way, generate nodelist for each frequent 2-itemset.
        String[] frequent_2itemsets = new String[this.hFrequentPatterns.size()];
        this.hFrequentPatterns.keySet().toArray(frequent_2itemsets);
        System.out.println("Time of generating node list for frequent 2-itemsets (ms): " +
        							this.create_nodelist_for_frequent_2itemsets(frequent_2itemsets));
        memoryLogger.checkMemory();
		System.out.println("=> Memory used in (MB): " + memoryLogger.getMaxUsedMemory());
        
        // Return mining time
        return System.currentTimeMillis() - start;
    }
    
    /**
     * This function discovers subspaces of frequent k-itemsets.
     * </br>Each subspace is all local frequent k-itemsets having a 2-itemset in 'potential_2itemsets' as their prefix.
     * @param potential_2itemsets
     * @param hFrequentPatterns_PART
     * @return the running time
     * @throws InterruptedException
     */
    public long mining_frequent_kItemsets_PART(String[] potential_2itemsets,
    										Map<String, FPSubset> hFrequentPatterns_PART) throws InterruptedException {
    	long start = System.currentTimeMillis();
    	
    	// Filter potential 2-itemset which are local frequent
    	FPSubset fpsubset;
    	for(String two_items : potential_2itemsets){
    		fpsubset = this.hFrequentPatterns.get(two_items);
    		if(fpsubset != null) hFrequentPatterns_PART.put(two_items, fpsubset);
    	}
        
    	// Discover all remaining frequent k-itemsets (k>2) from the part of frequent 2-itemset
        String[] frequent_2itemsets_PART = new String[hFrequentPatterns_PART.size()];
        hFrequentPatterns_PART.keySet().toArray(frequent_2itemsets_PART);
        System.out.println("Time of generating the remaining k-itemsets (ms): " + 
        							this.discover_frequent_kItemsets(frequent_2itemsets_PART));
        
        return System.currentTimeMillis() - start;
    }
    
    /**
     * This function will create a node list for each frequent item.
     * These all node lists will be add to 'item_nodelist'  
     */
    private long create_nodelist_for_items(){
    	long start = System.currentTimeMillis();
    	
    	// Prepare this.item_nodelist, add empty nodelists for all frequent items
    	int length = this.globalIndex_item.size();
    	this.item_nodelist = new HashMap<String, Nodelist>(length);
    	for(int i=0; i<length; i++){
    		this.item_nodelist.put(String.valueOf(i), new Nodelist(16000));
    	}
    	
    	// Update item_nodelist
    	for(PPCNode child : root.children){
    		this.create_nodelist_for_items_recursive(child, this.item_nodelist);
    	}
    	
    	// Save memory
    	for(Nodelist nodelist : item_nodelist.values()) nodelist.shrink();
    	
    	return System.currentTimeMillis() - start;
    }
    private void create_nodelist_for_items_recursive(PPCNode node, Map<String, Nodelist> item_nodelist){
    	// Always exist the nodelist for node.itemName
    	item_nodelist.get(String.valueOf(node.itemCode)).add(node.pre, node.pos, node.count);
    	
    	// Recursive call for child nodes
    	for(PPCNode child : node.children) create_nodelist_for_items_recursive(child, item_nodelist);
    }
    
    /**
     * Generate in parallel way all frequent 2-itemsets and their supports.
     * @return running time
     * @throws InterruptedException
     */
	private long generate_frequent_2itemsets() throws InterruptedException{
    	long start = System.currentTimeMillis();
    	
    	Matrix[] matrixes = new Matrix[THREAD_COUNT];
    	for(int i=0; i<THREAD_COUNT; i++) matrixes[i] = new Matrix(this.globalIndex_item.size());
    	matrix = matrixes[0];
    	IntHolder globalIndex = new IntHolder(0);
    	
    	Thread[] threads = new Thread[THREAD_COUNT];
    	for(int i=0; i<THREAD_COUNT; i++){
    		threads[i] = new Generate2ItemsetsThread(root.children, matrixes[i], globalIndex, i);
    		threads[i].start();
        }
        for(int i=0; i<THREAD_COUNT; i++) threads[i].join();
        
        // Sum all matrixes into the "matrix" (matrixes[0])
        for(int i=1; i<THREAD_COUNT; i++) matrix.summaryMergeWithMatrix(matrixes[i]);
        
        // Filter frequent 2-itemsets
    	this.hFrequentPatterns = new HashMap<String, FPSubset>();
    	matrix.filter_hFrequent2Itemsets(this.hFrequentPatterns, this.SUPPORT_COUNT_THRESHOLD);
    	
    	return System.currentTimeMillis() - start;
    }
    
    /**
     * Print statistic information about the mining result.
     */
    public void printStatisticInformation(){
    	System.out.println("-------------------Statistic information--------------------");
    	long node_count = 0;
    	for(Nodelist nodelist : item_nodelist.values()){
    		node_count = node_count + nodelist.size();
    	}
    	System.out.println("Total nodes of item-nodelists: " + node_count);
    	System.out.println("AVG length of item-nodelists: " + ((float)node_count/item_nodelist.size()));
    	
    	int itemset_count = itemCodesMaptoFrequencies.size() + hFrequentPatterns.size();
		for(FPSubset fpss : hFrequentPatterns.values()){
			itemset_count += fpss.fPatterns.size();
		}
		
    	for(NodelistSubset nlss : hk_itemset_nodelist.values()){
    		node_count = node_count + nlss.nodelist.size();
    		for(Nodelist nodelist : nlss.itemset_nodelist.values()){
    			node_count = node_count + nodelist.size();
    		}
    	}
    	System.out.println("Frequent itemset count: " + itemset_count);
    	System.out.println("Total nodes: " + node_count);
    	System.out.println("AVG length of nodelists: " + ((float)node_count/itemset_count));
    }
    
    /**
     * Create in parallel way node list for all frequent 2-itemsets.
     * @return running time
     * @throws InterruptedException
     */
    public long create_nodelist_for_frequent_2itemsets(String[] frequent_2itemsets) throws InterruptedException{
    	long start = System.currentTimeMillis();
    	
    	// Prepare hk_itemset_nodelist
    	this.hk_itemset_nodelist = new HashMap<String, NodelistSubset>(frequent_2itemsets.length);
    	for(String two_item : frequent_2itemsets){
    		// Delay memory allocation for Nodelist
    		hk_itemset_nodelist.put(two_item, new NodelistSubset(new Nodelist(true)));
    	}
    	
        // Threads
        IntHolder globalIndex = new IntHolder(0);
        Thread[] threads = new Thread[THREAD_COUNT];
        for(int i=0; i<THREAD_COUNT; i++){        	
        	threads[i] = new Generate2ItemsetNodelistThread(frequent_2itemsets,
										        			this.item_nodelist,
										        			this.hk_itemset_nodelist,
										        			globalIndex, i);
        	threads[i].start();
        }
        for(int i=0; i<THREAD_COUNT; i++) threads[i].join();
        
        return System.currentTimeMillis()-start;
    }
    
    /**
     * From each frequent 2-itemsets, the function develops frequent k-itemsets (k>2)
     * </br>Note: The orders of items in frequent 2-itemsets are the GLOBAL ORDER.
     * @param frequent_2itemsets
     * @return running time
     * @throws InterruptedException
     */
    public long discover_frequent_kItemsets(String[] frequent_2itemsets) throws InterruptedException{
    	long start = System.currentTimeMillis();
        
        // Threads
        IntHolder globalIndex = new IntHolder(0);
        Thread[] threads = new Thread[THREAD_COUNT];
        for(int i=0; i<THREAD_COUNT; i++){        	
        	threads[i] = new DiscoverFreqKItemsetThread(frequent_2itemsets,
														this.hFrequentPatterns,
														this.hk_itemset_nodelist,
														this.globalIndex_item.size(),
														globalIndex,
														this.SUPPORT_COUNT_THRESHOLD, i);
        	threads[i].start();
        }
        for(int i=0; i<THREAD_COUNT; i++) threads[i].join();
        
        return System.currentTimeMillis()-start;
    }
    
    /**
     * Free subspaces of k-itemsets and their nodelists in hFrequentPattern and hk_itemset_nodelist.
     */
    public void free(){
    	for(FPSubset fpsubset : this.hFrequentPatterns.values()){
    		if(fpsubset.fPatterns.size() > 0){
    			fpsubset.fPatterns = new HashMap<String, Integer>();
    		}
    	}
    	
    	for(NodelistSubset nlss : this.hk_itemset_nodelist.values()){
    		if(nlss.itemset_nodelist.size() > 0){
    			nlss.itemset_nodelist = new HashMap<String, Nodelist>();
    		}
    	}
    }
    
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	/////////////////// The function for calculating the support count of IN-frequent itemsets /////////////////////////
	////////////////////////////////////////// Used for Distributed Purpose //////////////////////////////////////////// 
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	@Override
	public int calculate_supportCount_kItemset(String[] code_array) {    	
    	Nodelist nodelist = null, nodelist1, nodelist2;
    	NodelistSubset nlss;
    	
    	StringBuilder sb = new StringBuilder(100);
    	String[] temp_array = code_array.clone();
    	String sub_itemset1;
    	String ext_itemset;
    	
    	////////////////////////////// Example ///////////////////////////////
    	//	0		1		2		3		4		i index					//
    	//	1		2		3		4		5		code_array				//
    	//	1		2		3		4		5		temp_array at loop = 1	//
    	//			12		13		14		15		temp_array at loop = 2	//
    	//	Now, prefix 12 is omitted!										//
    	//					12|3	12|4	12|5	temp_array at loop = 3	//
    	//							12|34	12|45	temp_array at loop = 4	//
    	//////////////////////////////////////////////////////////////////////
    	
    	// First loop, loop = 1
    	sb.append(code_array[0]).append(' ');	// Set Prefix
    	int prefixLength = sb.length();
    	sub_itemset1 = temp_array[0];
		nodelist1 = item_nodelist.get(sub_itemset1);
    	for(int i=1; i<temp_array.length; i++){
    		ext_itemset = sb.append(code_array[i]).toString();		// ext_itemset is now 2-itemset	
    		if(this.hk_itemset_nodelist.get(ext_itemset) == null) { // If nodelist of ext_itemset is not calculated yet, calculate it
    			nodelist2 = item_nodelist.get(temp_array[i]);
    			nodelist = PrePostPlusUtility.create_nodelist_from_2Nodelists(nodelist1, nodelist2);
    			nodelist.shrink(); // Save memory
    			this.hk_itemset_nodelist.put(ext_itemset, new NodelistSubset(nodelist));
    			if(nodelist.size() == 0) return 0;
    		}
    		temp_array[i] = ext_itemset;
    		sb.setLength(prefixLength);
    	}
    	
    	// Second loop, loop = 2
    	// temp_array[1] is now '12'
    	nlss = this.hk_itemset_nodelist.get(temp_array[1]);
    	Map<String, Nodelist> ITEMSET_NODELIST = nlss.itemset_nodelist;
		nodelist1 = nlss.nodelist;
    	for(int i=2; i<temp_array.length; i++){
    		// ext_itemset = code_array[i] is now 3-itemset. But in fact, the prefix 12 must be omitted. so it just 1-itemset
    		nodelist = ITEMSET_NODELIST.get(code_array[i]);
    		if(nodelist == null) { // If nodelist of ext_itemset is not calculated yet, calculate it
    			nodelist2 = this.hk_itemset_nodelist.get(temp_array[i]).nodelist;
    			nodelist = PrePostPlusUtility.create_nodelist_from_2Nodelists(nodelist1, nodelist2);
    			nodelist.shrink(); // Save memory
    			ITEMSET_NODELIST.put(code_array[i], nodelist);
    		}
    		if(nodelist.size() == 0) return 0;
    		temp_array[i] = code_array[i];
    	}
    	
		//////////////////////////////Example ////////////////////////////////
		//	0		1		2		3		4		i index					//
		//	1		2		3		4		5		code_array				//
		//	1		2		3		4		5		temp_array at loop = 1	//
		//			12		13		14		15		temp_array at loop = 2	//
		//	Now, prefix 12 is omitted!										//
		//					12|3	12|4	12|5	temp_array at loop = 3	//
		//							12|34	12|45	temp_array at loop = 4	//
		//////////////////////////////////////////////////////////////////////
    	
    	// Other loop, loop >= 3
    	sb.setLength(0);
    	for(int loop=3; loop<temp_array.length; loop++){
    		sb.append(code_array[loop-1]).append(' ');	// Update Prefix
        	prefixLength = sb.length();
    		sub_itemset1 = temp_array[loop-1];
    		nodelist1 = ITEMSET_NODELIST.get(sub_itemset1);
        	for(int i=loop; i<temp_array.length; i++){
        		ext_itemset = sb.append(code_array[i]).toString();
        		nodelist = ITEMSET_NODELIST.get(ext_itemset);
        		if(nodelist == null) {	// If nodelist of ext_itemset is not calculated yet, calculate it
        			nodelist2 = ITEMSET_NODELIST.get(temp_array[i]);
        			nodelist = PrePostPlusUtility.create_nodelist_from_2Nodelists(nodelist1, nodelist2);
        			nodelist.shrink(); // Save memory
        			ITEMSET_NODELIST.put(ext_itemset, nodelist);
        		}
        		if(nodelist.size() == 0) return 0;
        		temp_array[i] = ext_itemset;
        		sb.setLength(prefixLength);
        	}
        	
        	sb.append(code_array[loop]).append(' ');	// Update Prefix
        	prefixLength = sb.length();
    	}
    	
    	// Calculate itemset's support
    	return nodelist.totalSupportCount();
	}
    
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////// PROCEDURES FOR STORING RESULTS ////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    
	/**
     * Write all local frequent patterns
     * @param fileName
     * @return running time
     * @throws IOException
     */
    public long writeResult(String fileName) throws IOException {
    	// Remember time of start
    	long start = System.currentTimeMillis();
    	
        BufferedWriter output = new BufferedWriter(new FileWriter(fileName));
        StringBuilder sb = new StringBuilder();
        
        // Write frequent 1-itemsets
        for (Entry<Integer, Integer> entry : this.itemCodesMaptoFrequencies.entrySet()){
        	if(entry.getValue() < this.SUPPORT_COUNT_THRESHOLD) continue;
            //Format: <itemcode>:<frequency>
        	sb.setLength(0);
    		output.write(sb.append(entry.getKey()).append(":").
    						append(entry.getValue()).append("\n").toString());
        }
        
        // Write remaining frequent k-itemsets (k>1)
        for (Entry<String, FPSubset> e : this.hFrequentPatterns.entrySet()) {
            //Format: <itemset>:<frequency>
        	sb.setLength(0);
    		output.write(sb.append(e.getKey()).append(":").append(e.getValue().support).append("\n").toString());
    		
    		sb.setLength(0);
        	int base_length = sb.append(e.getKey()).append(' ').length();
    		for(Entry<String, Integer> entry : e.getValue().fPatterns.entrySet()){
    			sb.setLength(base_length);
        		output.write(sb.append(entry.getKey()).append(":").
        						append(entry.getValue()).append("\n").toString());
    		}
        }
        output.flush();
        output.close();
        return System.currentTimeMillis() - start;
    }
}
