package dfism.threads;

import java.util.Map;

import dfism.commons.IntHolder;
import dfism.fpo.FISMAlgorithm;
import dfism.fpo.FPONode;
import dfism.fpo.FPSubset;

public class UpdateSupportCountThread extends Thread{
	private FPONode[] nodes_arr;
	private FISMAlgorithm fism_alg;
	private IntHolder globalIndex;
	private int id;
	
	public UpdateSupportCountThread(FPONode[] nodes_arr,
								FISMAlgorithm fism_alg,
								IntHolder globalIndex,
								int id){
		this.nodes_arr = nodes_arr;
		this.fism_alg = fism_alg;
		this.globalIndex = globalIndex;
		this.id = id;
		this.setPriority(Thread.MAX_PRIORITY);
	}
	
	public void run(){
		long start_time = System.currentTimeMillis();
		
		StringBuilder sb = new StringBuilder();
		FPONode node_l2;
		String[] prefix_codes = new String[2];
		
		while(true){
			synchronized(globalIndex){
				if(globalIndex.value >= nodes_arr.length) break;
				node_l2 = nodes_arr[globalIndex.value];
				globalIndex.value++;
			}
			
			// Construct prefix_codes.
			prefix_codes[0] = String.valueOf(node_l2.parent.code);
			prefix_codes[1] = String.valueOf(node_l2.code);
			
			// If it is not frequent, the corresponding 2-itemset has not yet been in fism_alg.hFrequentPatterns
			sb.setLength(0);
			FPSubset fpSubset = fism_alg.hFrequentPatterns.get(
									sb.append(prefix_codes[0]).append(' ').append(prefix_codes[1]).toString());
			
			if(fpSubset != null){
				// Update recursively support counts for nodes at levels >= 3
				for(FPONode node_l3 : node_l2.children){
					this.update_supportCount_recursive(fpSubset.fPatterns, node_l3, prefix_codes, fism_alg);
				}
			}else{
				// Update recursively support counts for nodes at levels >= 3
				for(FPONode node_l3 : node_l2.children){
					this.update_supportCount_recursive(node_l3, prefix_codes, fism_alg);
				}
			}
		}
		
		// Just for testing
		sb.setLength(0);
		sb.append(this.getClass().getSimpleName()).append(' ').append(id).append(" finished work in ")
		.append(System.currentTimeMillis()-start_time).append(" ms");
		System.out.println(sb.toString());
	}
	
	/**
	 * Calculate and update support count for the itemset corresponding to the node. 
	 * </br>All super itemsets of this itemset are also processed in the same way recursively.
	 * @param node	the node need to be determined its support count
	 * @param fPatterns the sub space of frequent itemsets that share the same 2-itemsets as their prefixes
	 * @param node
	 * @param prefix_codes
	 * @param fism_alg
	 */
	private void update_supportCount_recursive(Map<String, Integer> fPatterns, 
												FPONode node,
												String[] prefix_codes, 
												FISMAlgorithm fism_alg){
		// If local data set does not include node.code (its support = 0), 
		// the itemsets containing this node.code do not exist also.
		if( fism_alg.itemCodesMaptoFrequencies.get(node.code) == 0) return;
		
		// Prepare the code array of the itemset corresponding to the node
		String[] codes = new String[prefix_codes.length+1];
		System.arraycopy(prefix_codes, 0, codes, 0, prefix_codes.length);
		codes[prefix_codes.length] = String.valueOf(node.code);		
		
		// Calculate support count of the itemset corresponding to the node.
		{
			// Prepare str_2items and str_itemset
			StringBuilder sb = new StringBuilder(100);
			sb.append(codes[2]);
			for(int i=3; i<codes.length; i++) sb.append(' ').append(codes[i]);
			
			Integer support_count = fPatterns.get(sb.toString());
			if(support_count != null) node.support = support_count;
			else {
				node.support = fism_alg.calculate_supportCount_kItemset(codes);
			}
		} // Free local variables to heap when being out of this scope
		
		// Call recursively for all its child nodes if it has. 
		if(node.children != null){
			for(FPONode childNode : node.children) 
				this.update_supportCount_recursive(fPatterns, childNode, codes, fism_alg);
		}
	}
	
	/**
	 * Calculate and update support count for the itemset corresponding to the node. 
	 * </br>All super itemsets of this itemset are also processed in the same way recursively.
	 * @param node	the node need to be determined its support count
	 * @param prefix_codes
	 * @param fism_alg
	 */
	private void update_supportCount_recursive(FPONode node,
												String[] prefix_codes,
												FISMAlgorithm fism_alg){
		// If local data set does not include node.code (its support = 0), 
		// the itemsets containing this node.code do not exist also.
		if( fism_alg.itemCodesMaptoFrequencies.get(node.code) == 0) return;
		
		// Prepare the code array of the itemset corresponding to the node
		String[] codes = new String[prefix_codes.length+1];
		System.arraycopy(prefix_codes, 0, codes, 0, prefix_codes.length);
		codes[prefix_codes.length] = String.valueOf(node.code);		
		
		// Calculate support count of the itemset corresponding to the node.
		node.support = fism_alg.calculate_supportCount_kItemset(codes);
		
		// Call recursively for all its child nodes if it has. 
		if(node.children != null){
			for(FPONode childNode : node.children) 
				this.update_supportCount_recursive(childNode, codes, fism_alg);
		}
	}
}