package dfism.threads;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

import dfism.commons.IntHolder;
import dfism.fpo.FPONode;

public class WriteFrequentItemsetsThread extends Thread{
	private String[] index_item;
	private String fileName;
	private FPONode[] nodes_arr;
	private int[] node_counts_arr;
	private int support_threshold;
	private IntHolder globalIndex;
	private int id;
	private boolean omitL1;
	private int node_count;
	
	public WriteFrequentItemsetsThread(String[] index_item,
								String fileName,
								FPONode[] nodes_arr,
								int[] node_counts_arr,
								int support_threshold,
								boolean omitL1,
								IntHolder globalIndex,
								int id){
		this.index_item = index_item;
		this.fileName = fileName;
		this.nodes_arr = nodes_arr;
		this.node_counts_arr = node_counts_arr;
		this.support_threshold = support_threshold;
		this.omitL1 = omitL1;
		this.globalIndex = globalIndex;
		this.id = id;
		this.setPriority(Thread.MAX_PRIORITY);
	}
	
	// Overwrite the run method
	public void run(){
		long start_time = System.currentTimeMillis();
		
		FPONode subNode;
		StringBuilder sb = new StringBuilder(100);
		BufferedWriter output;
		int assigned_index;
		
		try {
			output = new BufferedWriter(new FileWriter(sb.append(this.fileName).append("_t").append(this.id).toString()));
			if(this.omitL1){
				while(true){
					synchronized(globalIndex){
						if(globalIndex.value >= nodes_arr.length) break;
						assigned_index = globalIndex.value;
						subNode = nodes_arr[assigned_index];
						globalIndex.value++;
					}
					
					this.node_count = 0;
					this.filter_write_frequentPattern_recursive(subNode, String.valueOf(subNode.parent.code), sb, output);
					
					node_counts_arr[assigned_index] = this.node_count;
				}
			}else{
				while(true){
					synchronized(globalIndex){
						if(globalIndex.value >= nodes_arr.length) break;
						assigned_index = globalIndex.value;
						subNode = nodes_arr[assigned_index];
						globalIndex.value++;
					}
					
					this.node_count = 0;
					this.filter_write_frequentPattern_recursive(subNode, "", sb, output);
					
					node_counts_arr[assigned_index] = this.node_count;
				}
			}
			output.flush();
			output.close();
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		
		// Just for testing
		sb.setLength(0);
		sb.append(this.getClass().getSimpleName()).append(' ').append(id).append(" finished work in ")
		.append(System.currentTimeMillis()-start_time).append(" ms");
		System.out.println(sb.toString());
	}
	
	private void filter_write_frequentPattern_recursive(FPONode node, 
														String sub_itemset, 
														StringBuilder sb, 
														BufferedWriter output) throws IOException{
		if(node.support < this.support_threshold) return;
		sb.setLength(0);
		String itemset = sb.append(sub_itemset).append(' ').append(index_item[node.code]).toString();
		output.write(sb.append(':').append(node.support).append("\n").toString());
		this.node_count++;
		
		if(node.children != null){
			for(FPONode child_node : node.children){
				filter_write_frequentPattern_recursive(child_node, itemset, sb, output);
			}
		}
	}
}