package dfism.threads;

import java.util.Map;

import dfism.commons.IntHolder;
import dfism.fpo.FPONode;
import dfism.fpo.FPOTree;
import dfism.fpo.FPSubset;

public class FPOTreeBuildingThread extends Thread{
	private Map<String, FPSubset> hFrequentPatterns;
	private String[] frequent_2itemsets;
	private FPONode[] fpoNodeList_l2;
	private IntHolder globalIndex;
	private int id;
	
	public FPOTreeBuildingThread(Map<String, FPSubset> hFrequentPatterns,
								String[] frequent_2itemsets,
								FPONode[] fpoNodeList_l2,
								IntHolder globalIndex,
								int id){
		this.hFrequentPatterns = hFrequentPatterns;
		this.frequent_2itemsets = frequent_2itemsets;
		this.fpoNodeList_l2 = fpoNodeList_l2;
		this.globalIndex = globalIndex;
		this.id = id;
		this.setPriority(Thread.MAX_PRIORITY);
	}
	
	// Overwrite the run method
	public void run(){
		long start_time = System.currentTimeMillis();
		
		FPONode subNode;
		String i1i2;
		int count, start, end;
		int[] codes = new int[25];
		
		while(true){
			synchronized(globalIndex){
				if(globalIndex.value >= frequent_2itemsets.length) break;
				i1i2 = frequent_2itemsets[globalIndex.value];
				subNode = fpoNodeList_l2[globalIndex.value];
				globalIndex.value++;
			}
			
			/* Insert all the k-itemsets (k>2) which share the same 2-itemset i1i2 as their prefix
			 * NOTE: the prefix was omitted */
			for(String pattern : this.hFrequentPatterns.get(i1i2).fPatterns.keySet()){
				start = 0;
				
				// Parse item by item
				count = 0;
	            while ((end = pattern.indexOf(' ', start)) > 0) {
	                codes[count] = Integer.parseInt(pattern.substring(start, end));
	                start = end + 1;
	                count++;
	            }
	            // Get the last item
	            codes[count] = Integer.parseInt(pattern.substring(start));
	            count++;	// The value of 'count' is now the count of codes
				FPOTree.insertFrequentPattern(codes, count, subNode);
			}
		}
		
		// Just for testing
		StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName()).append(' ').append(id).append(" finished work in ")
		.append(System.currentTimeMillis()-start_time).append(" ms");
		System.out.println(sb.toString());
	}
}
