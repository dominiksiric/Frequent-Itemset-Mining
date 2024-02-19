package dp3.p3e;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import dfism.commons.IntHolder;
import dfism.fpo.FPSubset;

class DiscoverFreqKItemsetThread extends Thread{
	private String[] frequent_2itemsets;
	private Map<String, FPSubset> hFrequentPatterns;
	private Map<String, Integer> localFrequentPatterns;
	private Map<String, NodelistSubset> hk_itemset_nodelist;
	private Map<String, Nodelist> localK_itemset_nodelist;
	private int cad_itemCodes_size;
	private IntHolder globalIndex;
	private int SUPPORT_COUNT;
	private int id;
	
	public DiscoverFreqKItemsetThread(String[] frequent_2itemsets,
									Map<String, FPSubset> hFrequentPatterns,
									Map<String, NodelistSubset> hk_itemset_nodelist,
									int cad_itemCodes_size,
									IntHolder globalIndex,
									int support_count, int id){
		this.frequent_2itemsets = frequent_2itemsets;
		this.hFrequentPatterns = hFrequentPatterns;
		this.hk_itemset_nodelist = hk_itemset_nodelist;
		this.cad_itemCodes_size = cad_itemCodes_size;
		this.globalIndex = globalIndex;	
		this.SUPPORT_COUNT = support_count;
		this.id = id;
		this.setPriority(Thread.MAX_PRIORITY);
	}
	
	// Overwrite the run method
	public void run(){
		long start = System.currentTimeMillis();
		
		String i1i2;
		String[] i1i2_list;
		while (true){
			synchronized(globalIndex){
				if(globalIndex.value >= frequent_2itemsets.length) break;
				i1i2 = frequent_2itemsets[globalIndex.value];
				globalIndex.value++;
			}
			
			i1i2_list = i1i2.split(" ");
			this.localFrequentPatterns = this.hFrequentPatterns.get(i1i2).fPatterns;
			this.localK_itemset_nodelist = this.hk_itemset_nodelist.get(i1i2).itemset_nodelist;
			
			this.generate_freqItemsets_from_itemset_firstCall(i1i2, i1i2_list[0], i1i2_list[1]);
		}
		
		// Just for testing
		StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName()).append(' ').append(id).append(" finished work in ")
		.append(System.currentTimeMillis()-start).append(" ms");
		System.out.println(sb.toString());
	}
	
	/**
	 * Discover frequent k-itemsets (k>2) from a frequent 2-itemset i1i2 (i1 < i2). 
	 * <li>This function is called only one time, for the first time.
     * <li>For extended itemsets which are frequent, their nodelist and support count will be put in the
     * 'localK_itemset_nodelist' and 'localFrequentPattern'.
	 * @param X = body + " " + head; 
	 * @param body
	 * @param head
	 * @param parent_eq_set
	 */
    private void generate_freqItemsets_from_itemset_firstCall(String i1i2, String i1, String i2){
    	ArrayList<String> next_cad_item_list = new ArrayList<String>();
    	LinkedList<String> eq_items = new LinkedList<String>();
    	LinkedList<String> ext_itemset_list = new LinkedList<String>();
    	ArrayList<String> curr_eq_set = new ArrayList<String>();
    	
    	int i1i2_suppcount = this.hFrequentPatterns.get(i1i2).support;
    	
    	for(int j=(Integer.parseInt(i2) + 1); j<cad_itemCodes_size; j++){
    		String cad_item = String.valueOf(j);
    		StringBuilder sb = new StringBuilder(100);
    		String Y = sb.append(i1).append(' ').append(cad_item).toString();
    		
    		/*
    		 * Y does not contain equivalent items. Therefore if Y is frequent, Y must have its nodelist.
    		 * If nodelist of Y is null, it means that Y is not frequent. So the extended itemset is not too.
    		 * In fact, this checking statement is needed only for the first call of this procedure for 2-itemsets.
    		 * From the recursively second calls, both i1i2, Y are frequent and certainly have their nodelists.
    		 */
    		NodelistSubset nlssY = this.hk_itemset_nodelist.get(Y);
    		if(nlssY == null) continue;
    		
    		// Calculate the nodelist and support count for the ext_itemset
    		Nodelist ext_itemset_nodelist = 
    				PrePostPlusUtility.create_nodelist_from_2Nodelists(this.hk_itemset_nodelist.get(i1i2).nodelist, nlssY.nodelist);
    		int ext_itemset_support = ext_itemset_nodelist.totalSupportCount();
    		
    		// Make decision based on extended_itemset_suppcount 
    		if(ext_itemset_support == i1i2_suppcount){
    			eq_items.add(cad_item);
    		}else if(ext_itemset_support >= this.SUPPORT_COUNT){
    			next_cad_item_list.add(cad_item);
    			/* 
    			 * NOTE: i1i2 is prefix of all frequent k-itemset (k>2) in this subset of frequent k-itemsets
    			 * Therefore, this prefix can be omitted, and it will be 'cad_item' instead of i1i2|cad_item
    			 */
    			ext_itemset_list.add(cad_item);
    			ext_itemset_nodelist.shrink();	// Save memory
    			this.localK_itemset_nodelist.put(cad_item, ext_itemset_nodelist);
    			// Add this frequent extended itemset into the list of frequent itemsets
    			this.localFrequentPatterns.put(cad_item, ext_itemset_support);
    		}else continue;
    	}
    	
    	// This code block updates curr_eq_set and generates equivalent itemsets from X
    	if(eq_items.size()>0){
    		ArrayList<String> eq_set = new ArrayList<String>((1 << eq_items.size())-1);
    		PrePostPlusUtility.generatePowerSet(eq_items, eq_set);    		
    		for(String subset : eq_set){
    			this.localFrequentPatterns.put(subset, i1i2_suppcount);
    		}
    		
    		curr_eq_set = eq_set;
    	}
    	
    	// This code block generates equivalent itemsets from each extended_itemset
    	if(curr_eq_set.size() > 0){    		
    		StringBuilder sb = new StringBuilder(100);
    		for(String itemset : ext_itemset_list){
        		int extended_itemset_suppcount = this.localFrequentPatterns.get(itemset);
        		int[] codes = new int[25];
        		codes[0] = Integer.parseInt(itemset);	// in fact, at this level, itemset is just one item
        		for(String subset : curr_eq_set){
        			// This will guarantee items in itemsets are in the right local order
        			this.localFrequentPatterns.put(buildOrderedItemset(codes.clone(), 1, subset, sb),
        											extended_itemset_suppcount);
        		}
        	}   		
    	}
    	
    	// Call recursively for each extended itemset
    	for(String next_cad_item : next_cad_item_list){
    		this.generate_freqItemsets_from_itemset_secondCall(next_cad_item, next_cad_item_list, curr_eq_set);
    	}
    }
    
    private void generate_freqItemsets_from_itemset_secondCall(String head,
																List<String> cad_item_list,
																List<String> parent_eq_set){
    	ArrayList<String> next_cad_item_list = new ArrayList<String>();
    	LinkedList<String> eq_items = new LinkedList<String>();
    	LinkedList<String> ext_itemset_list = new LinkedList<String>();
    	ArrayList<String> curr_eq_set;
    	
    	// X = head, the body = i1i2 is omitted
    	int X_suppcount = this.localFrequentPatterns.get(head);
    	int cad_size = cad_item_list.size();
    	
    	for(int j=(cad_item_list.indexOf(head) + 1); j<cad_size; j++){
    		// Y = cad_item
    		String cad_item = cad_item_list.get(j);
    		
			// Calculate the nodelist and support count for the ext_itemset
    		Nodelist ext_itemset_nodelist = PrePostPlusUtility.create_nodelist_for_itemset(head, cad_item, this.localK_itemset_nodelist);
    		int ext_itemset_support = ext_itemset_nodelist.totalSupportCount();
    		
    		// Make decision based on extended_itemset_suppcount 
    		if(ext_itemset_support == X_suppcount){
    			eq_items.add(cad_item);
    		}else if(ext_itemset_support >= this.SUPPORT_COUNT){
    			StringBuilder sb = new StringBuilder(100);
    			String ext_itemset = sb.append(head).append(' ').append(cad_item).toString();
    			next_cad_item_list.add(cad_item);
    			ext_itemset_list.add(ext_itemset);
    			ext_itemset_nodelist.shrink();	// Save memory
    			this.localK_itemset_nodelist.put(ext_itemset, ext_itemset_nodelist);
    			// Add this frequent extended itemset into the list of frequent itemsets
    			this.localFrequentPatterns.put(ext_itemset, ext_itemset_support);
    		}else continue;
    	}
    	
    	// This code block updates curr_eq_set and generates equivalent itemsets from X
    	if(eq_items.size()>0){
    		ArrayList<String> eq_set = new ArrayList<String>((1 << eq_items.size())-1);
    		PrePostPlusUtility.generatePowerSet(eq_items, eq_set);
    		StringBuilder sb = new StringBuilder(100);
    		int length = sb.append(head).append(' ').length();
    		
    		for(String subset : eq_set){
    			sb.setLength(length);
    			this.localFrequentPatterns.put(sb.append(subset).toString(), X_suppcount);
    		}
    		
    		if(parent_eq_set.size()>0){
    			// Allocate full capacity for curr_eq_set
    			curr_eq_set = new ArrayList<String>((eq_set.size()+1)*(parent_eq_set.size()+1));
    			PrePostPlusUtility.generateDescartProduction(eq_set, parent_eq_set, curr_eq_set);
    			// curr_eq_set now contains Descart production of eq_set and parent_eq_set
    			int[] codes = new int[25];
    			codes[0] = Integer.parseInt(head);
    			for(String subset : curr_eq_set) {
    				// This will guarantee items in itemsets are in the right local order
    				this.localFrequentPatterns.put(buildOrderedItemset(codes.clone(), 1, subset, sb), X_suppcount);
    			}
    			// update curr_eq_set
    			curr_eq_set.addAll(eq_set);
    			curr_eq_set.addAll(parent_eq_set);
    		}else{
    			curr_eq_set = eq_set;
    		}
    	}else{
    		curr_eq_set = new ArrayList<String>(parent_eq_set.size());
    		curr_eq_set.addAll(parent_eq_set);
    	}
    	
    	// This code block generates equivalent itemsets from each extended_itemset
    	if(curr_eq_set.size()>0){    		
    		StringBuilder sb = new StringBuilder(100);
    		for(String itemset : ext_itemset_list){
        		int extended_itemset_suppcount = this.localFrequentPatterns.get(itemset);
        		int[] codes = new int[25];
    			int length = getCodeArray(itemset, codes);
        		for(String subset : curr_eq_set){
        			// This will guarantee items in itemsets are in the right local order
        			this.localFrequentPatterns.put(buildOrderedItemset(codes.clone(), length, subset, sb),
        											extended_itemset_suppcount);
        		}
        	}
    	}
    	
    	// Call recursively for each extended itemset
    	for(String next_cad_item : next_cad_item_list){
    		this.generate_freqItemsets_from_itemset_otherCall(head, next_cad_item, next_cad_item_list, curr_eq_set);
    	}
    }
    
    /**
     * Discover frequent k-itemsets from frequent k-itemsets (k > 2)
     * <li>This function is called from the second time.
     * <li>For extended itemsets which are frequent, their nodelist and support count will be put in the
     * 'localK_itemset_nodelist' and 'localFrequentPattern'.
     * @param body
     * @param head
     * @param cad_item_list
     * @param parent_eq_set
     */
    private void generate_freqItemsets_from_itemset_otherCall(String body, String head,
													List<String> cad_item_list,
													List<String> parent_eq_set){
    	ArrayList<String> next_cad_item_list = new ArrayList<String>();
    	LinkedList<String> eq_items = new LinkedList<String>();
    	LinkedList<String> ext_itemset_list = new LinkedList<String>();
    	ArrayList<String> curr_eq_set;
    	
    	String X = body + " " + head;
    	int X_suppcount = this.localFrequentPatterns.get(X);
    	int cad_size = cad_item_list.size();
    	
    	for(int j=(cad_item_list.indexOf(head) + 1); j<cad_size; j++){
    		String cad_item = cad_item_list.get(j);
    		StringBuilder sb = new StringBuilder(100);
    		String Y = sb.append(body).append(' ').append(cad_item).toString();
    		
			// Calculate the nodelist and support count for the ext_itemset
    		Nodelist ext_itemset_nodelist = PrePostPlusUtility.create_nodelist_for_itemset(X, Y, this.localK_itemset_nodelist);
    		int ext_itemset_support = ext_itemset_nodelist.totalSupportCount();
    		
    		// Make decision based on extended_itemset_suppcount 
    		if(ext_itemset_support == X_suppcount){
    			eq_items.add(cad_item);
    		}else if(ext_itemset_support >= this.SUPPORT_COUNT){
    			sb.setLength(0);
    			String ext_itemset = sb.append(X).append(' ').append(cad_item).toString();
    			next_cad_item_list.add(cad_item);
    			ext_itemset_list.add(ext_itemset);
    			ext_itemset_nodelist.shrink();	// Save memory
    			this.localK_itemset_nodelist.put(ext_itemset, ext_itemset_nodelist);
    			// Add this frequent extended itemset into the list of frequent itemsets
    			this.localFrequentPatterns.put(ext_itemset, ext_itemset_support);
    		}else continue;
    	}
    	
    	// This code block updates curr_eq_set and generates equivalent itemsets from X
    	if(eq_items.size()>0){
    		ArrayList<String> eq_set = new ArrayList<String>((1 << eq_items.size())-1);
    		PrePostPlusUtility.generatePowerSet(eq_items, eq_set);
    		StringBuilder sb = new StringBuilder(100);
    		int length = sb.append(X).append(' ').length();
    		
    		for(String subset : eq_set){
    			sb.setLength(length);
    			this.localFrequentPatterns.put(sb.append(subset).toString(), X_suppcount);
    		}
    		
    		if(parent_eq_set.size()>0){
    			// Allocate full capacity for curr_eq_set
    			curr_eq_set = new ArrayList<String>((eq_set.size()+1)*(parent_eq_set.size()+1));
    			PrePostPlusUtility.generateDescartProduction(eq_set, parent_eq_set, curr_eq_set);
    			// curr_eq_set now contains Descart production of eq_set and parent_eq_set
    			int[] codes = new int[25];
    			length = getCodeArray(X, codes);
    			for(String subset : curr_eq_set) {
    				// This will guarantee items in itemsets are in the right local order
    				this.localFrequentPatterns.put(buildOrderedItemset(codes.clone(), length, subset, sb), X_suppcount);
    			}
    			// update curr_eq_set
    			curr_eq_set.addAll(eq_set);
    			curr_eq_set.addAll(parent_eq_set);
    		}else{
    			curr_eq_set = eq_set;
    		}
    	}else{
    		curr_eq_set = new ArrayList<String>(parent_eq_set.size());
    		curr_eq_set.addAll(parent_eq_set);
    	}
    	
    	// This code block generates equivalent itemsets from each extended_itemset
    	if(curr_eq_set.size()>0){    		
    		StringBuilder sb = new StringBuilder(100);
    		for(String itemset : ext_itemset_list){
        		int extended_itemset_suppcount = this.localFrequentPatterns.get(itemset);
        		int[] codes = new int[25];
    			int length = getCodeArray(itemset, codes);
        		for(String subset : curr_eq_set){
        			// This will guarantee items in itemsets are in the right local order
        			this.localFrequentPatterns.put(buildOrderedItemset(codes.clone(), length, subset, sb),
        											extended_itemset_suppcount);
        		}
        	}
    	}
    	
    	// Call recursively for each extended itemset
    	for(String next_cad_item : next_cad_item_list){
    		this.generate_freqItemsets_from_itemset_otherCall(X, next_cad_item, next_cad_item_list, curr_eq_set);
    	}
    }
    
    /**
     * USE ONLY FOR DISTRIBUTED VERSION.
     * Put codes of items (is the indexes of items in globalIndex_item) in the 'itemset' into 'codes'
     * @param itemset
     * @param codes array of codes
     * @return the number of indexes (codes) in the 'codes'
     */
    private int getCodeArray(String itemset, int[] codes){
    	int pos = 0;
		int end;
		int index = 0;
		// Parse item by item
        while ((end = itemset.indexOf(' ', pos)) >= 0) {
        	codes[index] = Integer.parseInt(itemset.substring(pos, end));
            pos = end + 1;
            index++;
        }
        // Get the last item
        codes[index] = Integer.parseInt(itemset.substring(pos));
        index++;	// The value of 'index' is now the count of codes
    	return index;
    }
    
    /**
     * USE ONLY FOR DISTRIBUTED VERSION. The right order of items in frequent itemsets is necessary
     * Append codes of items in 'eq_set' into 'codes'
     * then sort codes in 'codes'. Finally return string
     * of itemset in the right order.
     * @param codes
     * @param index is the number of codes in the 'codes'
     * @param eq_set
     * @return
     */
    private String buildOrderedItemset(int[] codes, int index, String eq_set, StringBuilder sb){
		int pos = 0;
		int end;
		// Parse item by item
        while ((end = eq_set.indexOf(' ', pos)) >= 0) {
        	codes[index] = Integer.parseInt(eq_set.substring(pos, end));
            pos = end + 1;
            index++;
        }
        // Get the last item
        codes[index] = Integer.parseInt(eq_set.substring(pos));
        index++;	// The value of 'index' is now the count of codes
        Arrays.sort(codes, 0, index);
        
        sb.setLength(0);
        sb.append(codes[0]);
        for(int i=1; i<index; i++){
        	sb.append(' ').append(codes[i]);
        }
    	return sb.toString();
    }
}
