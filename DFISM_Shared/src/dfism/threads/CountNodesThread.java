package dfism.threads;

import java.util.LinkedList;

import dfism.commons.IntHolder;
import dfism.fpo.FPONode;

public class CountNodesThread extends Thread{
	private FPONode[] nodes_arr;
	private int[] node_counts_arr;
	private int[] innerNode_count_arr;
	private IntHolder globalIndex;
	private int id;
	private int node_count;
	private int innerNode_count;
	
	public CountNodesThread(FPONode[] nodes_arr,
							int[] node_counts_arr,
							int[] innerNode_count_arr,
							IntHolder globalIndex,
							int id){
		this.nodes_arr = nodes_arr;
		this.node_counts_arr = node_counts_arr;
		this.innerNode_count_arr = innerNode_count_arr;
		this.globalIndex = globalIndex;
		this.id = id;
		this.setPriority(Thread.MAX_PRIORITY);
	}
	
	// Overwrite the run method
	public void run(){
		long start_time = System.currentTimeMillis();
		
		FPONode subNode;
		int assigned_index;
		
		while(true){
			synchronized(globalIndex){
				if(globalIndex.value >= nodes_arr.length) break;
				assigned_index = globalIndex.value;
				subNode = nodes_arr[assigned_index];
				globalIndex.value++;
			}
			
			this.count_nodes(subNode);
			node_counts_arr[assigned_index] = this.node_count;
			innerNode_count_arr[assigned_index] = this.innerNode_count;
		}
		
		// Just for testing
		StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName()).append(' ').append(id).append(" finished work in ")
		.append(System.currentTimeMillis()-start_time).append(" ms");
		System.out.println(sb.toString());
	}
	
	private void count_nodes(FPONode node){
		LinkedList<FPONode> nodeQueue = new LinkedList<FPONode>();
		FPONode currentNode;
		
		int inner_count = 0;
		int count = node.children.size();
		
    	nodeQueue.addAll(node.children); 	// Add to the end of the list
    	while(nodeQueue.size() > 0){
    		currentNode = nodeQueue.removeFirst();
    		if(currentNode.children != null){
    			nodeQueue.addAll(currentNode.children);
    			inner_count++;
    			count = count + currentNode.children.size();
    		}
    	}
    	
    	this.node_count = count;
    	this.innerNode_count = inner_count;
	}
}