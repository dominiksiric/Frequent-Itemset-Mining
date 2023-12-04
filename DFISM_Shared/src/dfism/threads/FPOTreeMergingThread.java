package dfism.threads;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import dfism.commons.IntHolder;
import dfism.fpo.FPONode;

public class FPOTreeMergingThread extends Thread{
	private FPONode[][] nodes_arr;
	private IntHolder globalIndex;
	private int id;
	
	public FPOTreeMergingThread(FPONode[][] nodes_arr, IntHolder globalIndex, int id){
		this.nodes_arr = nodes_arr;
		this.globalIndex = globalIndex;
		this.id = id;
		this.setPriority(Thread.MAX_PRIORITY);
	}
	
	// Overwrite the run method
	public void run(){
		long start_time = System.currentTimeMillis();
		
		FPONode node1, node2;
		
		while(true){
			synchronized(globalIndex){
				if(globalIndex.value >= nodes_arr[0].length) break;
				node1 = nodes_arr[0][globalIndex.value];
				node2 = nodes_arr[1][globalIndex.value];
				globalIndex.value++;
			}
			
			this.mergeRecusive_arrayList(node1, node2);
		}
		
		// Just for testing
		StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName()).append(' ').append(id).append(" finished work in ")
		.append(System.currentTimeMillis()-start_time).append(" ms");
		System.out.println(sb.toString());
	}
	
	private void mergeRecusive_arrayList(FPONode node1, FPONode node2){
		if(node1.children == null){
			if(node2.children == null) return;
			else {
				node1.children = node2.children;	// Merge into node1.children
				for(FPONode node : node2.children) node.parent = node1;
				return;
			}
		}else if(node2.children == null) return;
		
		// The final case: node1.children != null && node2.children != null
		List<FPONode> children1 = node1.children;
		List<FPONode> children2 = node2.children;
		int index1=0, index2=0, size1 = children1.size(), size2 = children2.size();
		FPONode child1 = children1.get(index1), child2 = children2.get(index2);
		List<FPONode> mergedList = new ArrayList<FPONode>((size1 > size2) ? size1 : size2);
		
		while(true){
			if(child1.code < child2.code){
				mergedList.add(child1);
				index1++;
				if(index1 < size1) child1 = children1.get(index1);
				else break;
			}else if(child1.code > child2.code){
				mergedList.add(child2);
				child2.parent = node1;
				index2++;
				if(index2 < size2) child2 = children2.get(index2);
				else break;
			}else{
				child1.support += child2.support;
				mergedList.add(child1);
				mergeRecusive_arrayList(child1, child2);	// Recursive Call
				index1++; index2++;
				if(index1<size1 && index2<size2){
					child1 = children1.get(index1);
					child2 = children2.get(index2);
				}else break;
			}
		}
		
		// Add the remaining nodes in one of two list. Do not known which list still remains nodes.		
		while(index1<size1){
			mergedList.add(children1.get(index1));
			index1++;
		}
		
		while(index2<size2){
			child2 = children2.get(index2);
			mergedList.add(child2);
			child2.parent = node1;
			index2++;
		}
		
		node1.children = mergedList;
		node2.children = null;
	}
	
	@SuppressWarnings("unused")
	private void mergeRecusive_linkedList(FPONode node1, FPONode node2){
		if(node1.children == null){
			if(node2.children == null) return;
			else {
				node1.children = node2.children;	// Merge into node1.children
				for(FPONode node : node2.children) node.parent = node1;
				return;
			}
		}else if(node2.children == null) return;
		
		// The final case: node1.children != null && node2.children != null
		Iterator<FPONode> children1 = node1.children.iterator();
		Iterator<FPONode> children2 = node2.children.iterator();
		FPONode child1, child2;
		List<FPONode> mergedList = new LinkedList<FPONode>();
		
		child1 = children1.next();
		child2 = children2.next();
		while(true){
			if(child1.code < child2.code){
				mergedList.add(child1);
				child1 = null;
				if(children1.hasNext()) child1 = children1.next();
				else break;
			}else if(child1.code > child2.code){
				mergedList.add(child2);
				child2.parent = node1;
				child2 = null;
				if(children2.hasNext()) child2 = children2.next();
				else break;
			}else{
				child1.support += child2.support;
				mergedList.add(child1);
				mergeRecusive_linkedList(child1, child2);	// Recursive Call
				child1 = null; child2 = null;
				if(children1.hasNext() && children2.hasNext()){
					child1 = children1.next();
					child2 = children2.next();
				}else break;
			}
		}
		
		// Add the remaining nodes in one of two list. Do not known which list still remains nodes.
		if(child1 == null) {
			if(child2 == null) {
				while (children1.hasNext()) mergedList.add(children1.next());
				while (children2.hasNext()){
					child2 = children2.next();
					mergedList.add(child2);
					child2.parent = node1;
				}
			}else{
				mergedList.add(child2);
				child2.parent = node1;
				while (children2.hasNext()){
					child2 = children2.next();
					mergedList.add(child2);
					child2.parent = node1;
				}
			}
		}else{
			if(child2 == null) {
				mergedList.add(child1);
				while (children1.hasNext()) mergedList.add(children1.next());
			}
		}
		
		node1.children = mergedList;
	}
}