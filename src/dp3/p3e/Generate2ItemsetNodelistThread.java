package dp3.p3e;

import java.util.Map;

import dfism.commons.IntHolder;

class Generate2ItemsetNodelistThread extends Thread{
	private String[] frequent_2itemsets;
	private Map<String, Nodelist> item_nodelist;
	private Map<String, NodelistSubset> hk_itemset_nodelist;
	private IntHolder globalIndex;
	private int id;
	
	public Generate2ItemsetNodelistThread(String[] frequent_2itemsets,
									Map<String, Nodelist> item_nodelist,
									Map<String, NodelistSubset> hk_itemset_nodelist,
									IntHolder globalIndex, int id){
		this.frequent_2itemsets = frequent_2itemsets;
		this.item_nodelist = item_nodelist;
		this.hk_itemset_nodelist = hk_itemset_nodelist;
		this.globalIndex = globalIndex;		
		this.id = id;
		this.setPriority(Thread.MAX_PRIORITY);
	}
	
	// Overwrite the run method
	public void run(){
		long start = System.currentTimeMillis();
		
		String i1i2;
		String[] i1i2_list;
		while(true){
			synchronized(globalIndex){
				if(globalIndex.value >= frequent_2itemsets.length) break;
				i1i2 = frequent_2itemsets[globalIndex.value];
				globalIndex.value++;
			}
			
			i1i2_list = i1i2.split(" ");
			Nodelist nodelist = this.hk_itemset_nodelist.get(i1i2).nodelist;
			
			PrePostPlusUtility.create_nodelist_for_itemset(i1i2_list[0], i1i2_list[1], this.item_nodelist, nodelist);
			nodelist.shrink();	// Save memory
		}
		
		// Just for testing
		StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName()).append(' ').append(id).append(" finished work in ")
		.append(System.currentTimeMillis()-start).append(" ms");
		System.out.println(sb.toString());
	}
}
