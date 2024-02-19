package dp3.p3e;

import java.util.List;

import dfism.commons.IntHolder;
import dfism.commons.Matrix;

class Generate2ItemsetsThread extends Thread{
	private List<PPCNode> child_list;
	private Matrix matrix;
	private IntHolder globalIndex;
	private int id;
	
	public Generate2ItemsetsThread(List<PPCNode> child_list,
									Matrix matrix,
									IntHolder globalIndex, int id){
		this.child_list = child_list;
		this.matrix = matrix;
		this.globalIndex = globalIndex;		
		this.id = id;
		this.setPriority(Thread.MAX_PRIORITY);
	}
	
	// Overwrite the run method
	public void run(){
		long start = System.currentTimeMillis();
		
		PPCNode l1_child;
		int size = child_list.size();
		while (true){
			synchronized(globalIndex){
				if(globalIndex.value >= size) break;
				l1_child = child_list.get(globalIndex.value);
				globalIndex.value++;
			}
			
			for(PPCNode l2_child : l1_child.children) update_suppcount_2itemsets_recursive(l2_child);
		}
		
		// Summary local support for each 2-itemsets.
		// matrix.summaryByDiagonalFolding(); // PrePostPlus do not need this calculation step.
		
		
		// Just for testing
		StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName()).append(' ').append(id).append(" finished work in ")
		.append(System.currentTimeMillis()-start).append(" ms");
		System.out.println(sb.toString());
	}
	
	/**
	 * This procedure accumulate the support count for 2-itemsets, the items are in item_Index
	 * @param node
	 */
    private void update_suppcount_2itemsets_recursive(PPCNode node){
    	int index = node.itemCode;
    	PPCNode parentNode = node.parent;
    	
    	while(parentNode.parent != null){	// if parentNode.parent == null, parentNode is the root.
    		matrix.add(index, parentNode.itemCode, node.count);
    		parentNode = parentNode.parent;
    	}
    	
    	// If the current node is not a leaf node, traverse all its children
    	for(PPCNode child : node.children) update_suppcount_2itemsets_recursive(child);
    }
}
