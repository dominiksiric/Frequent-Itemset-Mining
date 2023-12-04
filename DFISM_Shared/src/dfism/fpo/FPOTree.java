package dfism.fpo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import dfism.commons.IntHolder;
import dfism.commons.SlaveInfo;
import dfism.threads.CountNodesThread;
import dfism.threads.FPOTreeBuildingThread;
import dfism.threads.FPOTreeMergingThread;
import dfism.threads.GlobalFPOTreeSendingThread;
import dfism.threads.UpdateSupportCountThread;
import dfism.threads.WriteFrequentItemsetsThread;

/**
 * FPO tree is a data structure for managing a huge number of frequent itemsets. The tree supports:
 * </br> - Send and receive frequent itemsets in highly compressed form of data.
 * </br> - Very efficiently aggregate huge numbers of frequent itemsets from each worker machine.
 * </br> - Manage efficiently the tree kinds of frequent itemsets: Frequent Itemsets, Maximal Frequent Itemsets, and
 * Closed Frequent Itemsets.
 * </br> - Conveniently to generate Association Rules.
 * @author Van Quoc Phuong Huynh
 */
public class FPOTree {	
	private FPONode root;
	private int nodeCount = 0;			// root exclude
	private int innerNodeCount = 0;		// root exclude
	private int leafNodeCount = 0;
	
	private final int THREAD_COUNT = Math.max(1, Runtime.getRuntime().availableProcessors()/2);
	
	////////////////////////////////////////////// COMMONS METHODS //////////////////////////////////////////////////

	public FPOTree() {
		this.root = new FPONode();
		this.root.children = new ArrayList<FPONode>();
	}
	
	/**
	 * @return total count of nodes in the tree
	 */
	public int getNodeCount() {
		return nodeCount;
	}

	/**
	 * @return return the count of inner nodes in the tree
	 */
	public int getInnerNodeCount() {
		return innerNodeCount;
	}

	/**
	 * @return return the count of leaf nodes in the tree
	 */
	public int getLeafNodeCount() {
		return leafNodeCount;
	}
	
	/**
	 * Free memory
	 */
	public void free(){
		root.children.clear();
		root.children = null;
		root = null;
	}
	
	/**
	 * Insert a frequent pattern into the FPO-Tree
	 * @param codes the sorted code array of items in the frequent pattern
	 * @param length the length of codes
	 * @param support support count of the frequent pattern
	 */
	private void insertFrequentPattern(int[] codes, int length) {
		FPONode subNode = this.root;
		FPONode newNode = null;
		FPONode child = null;
        boolean wasNotMerged;
        
        int code, position, size, mid;
        for(int i = 0; i<length; i++){
        	code = codes[i];
            if(subNode.children != null){
            	wasNotMerged = true;
            	// Find a child which can be merged with the item
                // If it is so, set flag 'wasNotMerged' to false
            	size = subNode.children.size();
            	position = 0;
                while (position < size) {
                    mid = (position + size) / 2;
                    child = subNode.children.get(mid);
                    if (child.code == code){
                    	subNode = child;
                        wasNotMerged = false;
                        break;
                    } else if (child.code < code) position = mid + 1;
                    else size = mid;
                }
                
                if (wasNotMerged) {
                	newNode = new FPONode(code, subNode);
                	subNode.children.add(position, newNode);
                	subNode = newNode;
                }
            }else{
            	subNode.children = new ArrayList<FPONode>();
            	newNode = new FPONode(code, subNode);
            	subNode.children.add(newNode);
            	subNode = newNode;
            }
        }
    }
	
	/**
	 * Insert a frequent pattern into the FPO-Tree
	 * @param codes the sorted code array of items in the frequent pattern
	 * @param length the length of code array
	 * @param fpoNode from this node, the pattern is inserted
	 * @param support support count of the frequent pattern
	 * @return the leaf node of the insertion
	 */
	public static FPONode insertFrequentPattern(int[] codes, int length, FPONode fpoNode) {
		FPONode subNode = fpoNode;
		FPONode newNode = null;
		FPONode child = null;
        boolean wasNotMerged;
        
        int code, position, size, mid;
        for(int i = 0; i<length; i++){
        	code = codes[i];
            if(subNode.children != null){
            	wasNotMerged = true;
            	// Find a child which can be merged with the item
                // If it is so, set flag 'wasNotMerged' to false
            	size = subNode.children.size();
            	position = 0;
                while (position < size) {
                    mid = (position + size) / 2;
                    child = subNode.children.get(mid);
                    if (child.code == code){
                    	subNode = child;
                        wasNotMerged = false;
                        break;
                    } else if (child.code < code) position = mid + 1;
                    else size = mid;
                }
                
                if (wasNotMerged) {
                	newNode = new FPONode(code, subNode);
                	subNode.children.add(position, newNode);
                	subNode = newNode;
                }
            }else{
            	subNode.children = new ArrayList<FPONode>();
            	newNode = new FPONode(code, subNode);
            	subNode.children.add(newNode);
            	subNode = newNode;
            }
        }
        return subNode;
    }
		
	/**
	 * In parallel way, count the number of total nodes, inner nodes, and leaf nodes.
	 * </br>Call this function is mandatory after CONSTRUCTING, MERGING, or PRUNING the FPO tree.
	 * @return running time
	 * @throws InterruptedException
	 */
	public long count_nodes() throws InterruptedException{
		long start = System.currentTimeMillis();
		
		int inner_count_l1 = 0;
		for(FPONode node : root.children){
			if(node.children != null) inner_count_l1++;
		}
		FPONode[] nodes_arr = new FPONode[inner_count_l1];
		int[] node_counts_arr = new int[inner_count_l1];
		int[] innerNode_count_arr = new int[inner_count_l1];
		
		int index = 0;
		for(FPONode node : root.children){
			if(node.children != null){
				nodes_arr[index] = node;
				index++;
			}
		}
		
		// Spawn threads to update support count
		IntHolder globalIndex = new IntHolder(0);
		Thread[] threads = new Thread[THREAD_COUNT];
		for(int i=0; i<threads.length; i++){
			threads[i] = new CountNodesThread(nodes_arr,
												node_counts_arr,
												innerNode_count_arr,
												globalIndex,
												i);
			threads[i].start();
		}
		for(int i=0; i<threads.length; i++) threads[i].join();
		
		// Update total node count
		this.nodeCount = root.children.size();
		for(int node_count : node_counts_arr) this.nodeCount += node_count;
		
		// Update inner node count
		this.innerNodeCount = inner_count_l1;
		for(int node_count : innerNode_count_arr) this.innerNodeCount += node_count;
    	
    	// nodeCount = innerNodeCount + leafNodeCount
    	this.leafNodeCount = this.nodeCount - this.innerNodeCount;
    	
    	return System.currentTimeMillis()-start;
	}
	
	/**
	 * @return All 2-itemsets in the tree in form of 2D array. For example:
	 * </br>Total 2-itemsets: 0 1 2 3 4 5 6 7 8 9
	 * </br>Array0:	0 3 6 9
	 * </br>Array1:	1 4 7
	 * </br>Array2:	2 5 8
	 */
	public String[][] get_2itemsets(int part_count){
		// Calculate the length for each part and allocate memory for two_itemsets		
		int base_length = this.leafNodeCount/part_count;
		int redundance = this.leafNodeCount - base_length*part_count;

		String[][] two_itemsets = new String[part_count][];
		for(int i=0; i<redundance; i++) two_itemsets[i] = new String[base_length+1];
		for(int i=redundance; i<part_count; i++) two_itemsets[i] = new String[base_length];
		
		// Fill array. Example: 0 1 2 3 4 5 6 7 8 9
		// Array0:	0 3 6 9
		// Array1:	1 4 7
		// Array2:	2 5 8
		// Use this partition to make load-balance between parts.
		StringBuilder sb = new StringBuilder();
		int major_index = 0, minor_index = 0;
		for(FPONode l1_node : this.root.children){
			sb.setLength(0);
			sb.append(l1_node.code).append(' ');
			base_length = sb.length();
			if(l1_node.children != null){
				for(FPONode l2_node : l1_node.children){
					sb.setLength(base_length);
					two_itemsets[major_index][minor_index] = sb.append(l2_node.code).toString();
					major_index++;
					if(major_index == part_count){
						major_index = 0;
						minor_index++;
					}
				}
			}
		}
		
		return two_itemsets;
	}
	
	/**
	 * Send just item code of the tree to another machine.
	 * Use this function if count of total nodes, inner nodes, and leaf nodes are known
	 * @param dos a DataOutputStream
	 * @return running time, -1 if failed
	 * @throws IOException
	 */	
	public long send_only_itemCode(DataOutputStream dos) throws IOException{
		if(root.children.size()==0 || dos == null) return -1;
		
		long start = System.currentTimeMillis();
		
		LinkedList<FPONode> nodeQueue = new LinkedList<FPONode>();
		LinkedList<FPONode> innerNodeQueue = new LinkedList<FPONode>();
		BitSet innerNodePositions = new BitSet(this.nodeCount+1);
		FPONode currentNode;
		int childCount, node_index = 1;
		
		// Send tree properties
		dos.writeInt(this.innerNodeCount);
		dos.writeInt(this.leafNodeCount);
		dos.writeInt(this.nodeCount);
		
		// Send node data
		innerNodeQueue.add(root);
		innerNodePositions.set(0);
		nodeQueue.addAll(root.children); 	// Add to the end of the list
    	while(innerNodeQueue.size() > 0){
    		childCount = innerNodeQueue.removeFirst().children.size();
    		dos.writeInt(childCount);
    		for(int i=0; i<childCount; i++){
    			currentNode = nodeQueue.removeFirst();
    			dos.writeInt(currentNode.code);	// Send only item code
	    		if(currentNode.children != null) {
	    			innerNodeQueue.add(currentNode);
	    			nodeQueue.addAll(currentNode.children);
	    			innerNodePositions.set(node_index);	// Corresponding bit is set for the inner node
	    		}
	    		node_index++;
    		}
    	}
    	
    	// Send positions of the inner nodes in bytes array
    	byte[] innerNodePositionsInBytes = innerNodePositions.toByteArray();
    	dos.writeInt(innerNodePositionsInBytes.length);		// Send the length of the byte array
    	dos.write(innerNodePositionsInBytes);
    	dos.flush();
    	
    	return System.currentTimeMillis() - start;
	}
	
	/**
	 * Receive just item codes of a FPO tree from the sending machine.
	 * Total count of nodes, count of inner nodes, count of leaf nodes are known
	 * @param dis a DataInputStream
	 * @return running time, -1 if failed
	 * @throws IOException
	 */
	public long receive_only_itemCode(DataInputStream dis) throws IOException{
		if(dis == null) return -1;
		
		// Receive tree properties
		this.innerNodeCount = dis.readInt();	// Receive the inner node count, root exclude
		this.leafNodeCount = dis.readInt();		// Receive the leaf node count
		this.nodeCount = dis.readInt();			// Receive the total node count
		
		long start = System.currentTimeMillis();
		
		// Receive node data
		int innerNodeCount = this.innerNodeCount+1;
    	FPONode[] nodeQueue = new FPONode[this.nodeCount+1];
    	nodeQueue[0] = root;
    	int[] childCountArray = new int[innerNodeCount];	// Contain child count of inner nodes
    	int childCount, node_index=1;
    	for(int i=0; i<innerNodeCount; i++){
    		childCount = dis.readInt();	// Receive child count
    		childCountArray[i] = childCount;
    		for(int j=0; j<childCount; j++){
    			nodeQueue[node_index] = new FPONode(dis.readInt(), 0);	// Receive just item code
    			node_index++;
    		}
    	}
    	
    	// Receive positions of the inner nodes in bytes array
    	int size = dis.readInt();	// Receive length in byte of innerNodePositionInBytes
    	byte[] innerNodePositionsInBytes = new byte[size];
    	this.receiveByteArray(dis, innerNodePositionsInBytes);
    	BitSet innerNodePositions = BitSet.valueOf(innerNodePositionsInBytes);
    	
    	// Reconstruct the FPOTree tree
    	FPONode parentNode, node;
    	int setBitPosition = -1;
    	node_index=1;
    	for(int i=0; i<innerNodeCount; i++){
    		setBitPosition = innerNodePositions.nextSetBit(setBitPosition+1);
			parentNode = nodeQueue[setBitPosition];
			childCount = childCountArray[i];
			parentNode.children = new ArrayList<FPONode>(childCount);	// Aware exactly the child count
			
			for(int j=0; j<childCount; j++){
    			node = nodeQueue[node_index];
    			node.parent = parentNode;
				parentNode.children.add(node);
    			node_index++;
    		}
		}
    	
		return System.currentTimeMillis() - start;
	}
	
	/**
	 * Use this procedure to receive an array of bytes in a reliable way, especially when the array is big.
	 * Since in case a big array, the receiver may not receive enough the number of bytes sent by sender.
	 * @param dis
	 * @param array
	 * @throws IOException 
	 */
	public void receiveByteArray(DataInputStream dis, byte[] array) throws IOException{
		// Read the chunk size from the Sender
		int remain_bytes = array.length;
		int start = 0;
		int read_bytes;
		do {
			read_bytes = dis.read(array, start, remain_bytes);
			remain_bytes -= read_bytes;
			start += read_bytes;
		} while(read_bytes > 0);
	}
	
	/**
	 * In parallel way, merge with other FPO tree
	 * @param tree
	 * @return running time
	 * @throws InterruptedException 
	 */
	public long merge(FPOTree tree) throws InterruptedException{
		long start = System.currentTimeMillis();
		
		FPONode node1 = this.root;
		FPONode node2 = tree.root;
		
		// First three cases
		if(node1.children == null){
			if(node2.children == null) return 0;
			else {
				node1.children = node2.children;	// Merge into node1.children
				for(FPONode node : node2.children) node.parent = node1;
				return (System.currentTimeMillis()-start);
			}
		}else if(node2.children == null) return 0;
		
		// The final case: this.root.children != null && tree.root.children != null
		List<FPONode> children1 = node1.children;
		List<FPONode> children2 = node2.children;
		int index1=0, index2=0, size1 = children1.size(), size2 = children2.size();
		FPONode child1 = children1.get(index1), child2 = children2.get(index2);
		List<FPONode> mergedList = new ArrayList<FPONode>((size1 > size2) ? size1 : size2);
		LinkedList<FPONode> descMergeList = new LinkedList<FPONode>();
		
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
				// Local FPO trees from slaves are sent to Master without support counts.
				// So accumulating support counts is not necessary.
				// child1.support += child2.support;
				mergedList.add(child1);
				// child1 and child2 are added to the list, their child nodes will be merged by threads later
				descMergeList.add(child1);
				descMergeList.add(child2);
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
		
		// Prepare a list of pairs of nodes, that child nodes of each pair will be merged
		int length = descMergeList.size()/2;
		FPONode[][] descMergeArray = new FPONode[2][length];
		for(int i=0; i<length; i++){
			descMergeArray[0][i] = descMergeList.removeFirst();
			descMergeArray[1][i] = descMergeList.removeFirst();
		}
		
		// Spawn threads to merge descendant nodes
		IntHolder globalIndex = new IntHolder(0);
		Thread[] threads = new Thread[THREAD_COUNT];
		for(int i=0; i<threads.length; i++){
			threads[i] = new FPOTreeMergingThread(descMergeArray, globalIndex, i);
			threads[i].start();
		}
		for(int i=0; i<threads.length; i++) threads[i].join();
		
		return System.currentTimeMillis() - start;
	}
	
	/**
	 * Master send just item code of the FPO tree to slave machines.
	 * The support count of the nodes DO NOT NEED TO BE SENT
	 * @param slaves list of SlaveInfo to send to
	 * @return running time, -1 if failed
	 * @throws InterruptedException 
	 */
	public long send_only_itemCode_toSlaves(ArrayList<SlaveInfo> slaves) throws InterruptedException{
		if(root.children.size()==0) return -1;
		
		long start = System.currentTimeMillis();
		
		FPONode[] nodeArray = new FPONode[this.nodeCount+1];
		FPONode[] innerNodeArray = new FPONode[this.innerNodeCount+1];
		BitSet innerNodePositions = new BitSet(this.nodeCount+1);
		FPONode currentNode;
		
		// Transform the tree to array
		nodeArray[0] = root;
		innerNodeArray[0] = root;
		innerNodePositions.set(0);
		int node_index = 1, innerNode_index = 1;
		for(FPONode node : root.children){
			nodeArray[node_index] = node;
			node_index++;
		}
		
		for(int i=1; i < nodeArray.length; i++){
			currentNode = nodeArray[i];
			if(currentNode.children != null){
				innerNodeArray[innerNode_index] = currentNode;
				innerNode_index++;
				for(FPONode node : currentNode.children){
					nodeArray[node_index] = node;
					node_index++;
				}
				innerNodePositions.set(i);
			}
		}
		
		// Transform inner node positions in bit to byte array
		byte[] innerNodePositionsInByte = innerNodePositions.toByteArray();
		
		// Spawn threads to send the sharing data to slaves
		Thread[] threads = new Thread[slaves.size()];
		for(int i=0; i<threads.length; i++){
			threads[i] = new GlobalFPOTreeSendingThread(i, slaves.get(i).socket,
														nodeArray, 
														innerNodeArray, 
														innerNodePositionsInByte, this);
			threads[i].start();
		}
		for(int i=0; i<threads.length; i++) threads[i].join();
		
    	return System.currentTimeMillis() - start;
	}
	
	/**
	 * Send the tree to another machine. Send both item code and support count
	 * Use this function if count of total nodes, inner nodes, and leaf nodes are known
	 * @param dos a DataOutputStream
	 * @return running time, -1 if failed
	 * @throws IOException
	 */	
	public long send(DataOutputStream dos) throws IOException{
		if(root.children.size()==0 || dos == null) return -1;
		
		long start = System.currentTimeMillis();
		
		LinkedList<FPONode> nodeQueue = new LinkedList<FPONode>();
		LinkedList<FPONode> innerNodeQueue = new LinkedList<FPONode>();
		BitSet innerNodePositions = new BitSet(this.nodeCount+1);
		FPONode currentNode;
		int childCount, node_index = 1;
		
		// Send tree properties
		dos.writeInt(this.innerNodeCount);
		dos.writeInt(this.leafNodeCount);
		dos.writeInt(this.nodeCount);
		
		// Send node data
		innerNodeQueue.add(root);
		innerNodePositions.set(0);
		nodeQueue.addAll(root.children); 	// Add to the end of the list
    	while(innerNodeQueue.size() > 0){
    		childCount = innerNodeQueue.removeFirst().children.size();
    		dos.writeInt(childCount);
    		for(int i=0; i<childCount; i++){
    			currentNode = nodeQueue.removeFirst();
    			dos.writeInt(currentNode.code);	// Send code
    			dos.writeInt(currentNode.support);	// Send support
	    		if(currentNode.children != null) {
	    			innerNodeQueue.add(currentNode);
	    			nodeQueue.addAll(currentNode.children);
	    			innerNodePositions.set(node_index);	// Corresponding bit is set for the inner node
	    		}
	    		node_index++;
    		}
    	}
    	
    	// Send positions of the inner nodes in bytes array
    	byte[] innerNodePositionsInBytes = innerNodePositions.toByteArray();
    	dos.writeInt(innerNodePositionsInBytes.length);		// Send the length of the byte array
    	dos.write(innerNodePositionsInBytes);
    	dos.flush();
    	
    	return System.currentTimeMillis() - start;
	}
	
	/**
	 * Receive a FPO tree from sending machine. Receive both item code and support count
	 * Total count of nodes, count of inner nodes, count of leaf nodes are known
	 * @param dis a DataInputStream
	 * @return running time, -1 if failed
	 * @throws IOException
	 */
	public long receive(DataInputStream dis) throws IOException{
		if(dis == null) return -1;
		
		// Receive tree properties
		this.innerNodeCount = dis.readInt();	// Receive the inner node count, root exclude
		this.leafNodeCount = dis.readInt();		// Receive the leaf node count
		this.nodeCount = dis.readInt();			// Receive the total node count
		
		long start = System.currentTimeMillis();
		
		// Receive node data
		int innerNodeCount = this.innerNodeCount+1;
    	FPONode[] nodeQueue = new FPONode[this.nodeCount+1];
    	nodeQueue[0] = root;
    	int[] childCountArray = new int[innerNodeCount];	// Contain child count of inner nodes
    	int childCount, node_index=1;
    	for(int i=0; i<innerNodeCount; i++){
    		childCount = dis.readInt();	// Receive child count
    		childCountArray[i] = childCount;
    		for(int j=0; j<childCount; j++){
    			nodeQueue[node_index] = new FPONode(dis.readInt(), dis.readInt());	// Receive code and support count
    			node_index++;
    		}
    	}
    	
    	// Receive positions of the inner nodes in bytes array
    	int size = dis.readInt();	// Receive length in byte of innerNodePositionInBytes
    	byte[] innerNodePositionsInBytes = new byte[size];
    	this.receiveByteArray(dis, innerNodePositionsInBytes);
    	BitSet innerNodePositions = BitSet.valueOf(innerNodePositionsInBytes);
    	
    	// Reconstruct the FPOTree tree
    	FPONode parentNode, node;
    	int setBitPosition = -1;
    	node_index=1;
    	for(int i=0; i<innerNodeCount; i++){
    		setBitPosition = innerNodePositions.nextSetBit(setBitPosition+1);
			parentNode = nodeQueue[setBitPosition];
			childCount = childCountArray[i];
			parentNode.children = new ArrayList<FPONode>(childCount);	// Aware exactly the child count
			
			for(int j=0; j<childCount; j++){
    			node = nodeQueue[node_index];
    			node.parent = parentNode;
				parentNode.children.add(node);
    			node_index++;
    		}
		}
    	
		return System.currentTimeMillis() - start;
	}
	
	/**
	 * Send only support count of all nodes in the tree to master.
	 * The master will receive this data in form of array of support counts with length = 'nodeCount'
	 * @param dos DataOutputStream to send data to master 
	 * @return running time, -1 if failed
	 * @throws IOException
	 */
	public long send_supportCountArray_toMaster(DataOutputStream dos) throws IOException{
		long start = System.currentTimeMillis();
		
		LinkedList<FPONode> queueNode = new LinkedList<FPONode>();
		FPONode currentNode;
		queueNode.addAll(root.children);
		
		// Send the number of nodes then send all of supports
		dos.writeInt(nodeCount);
		while(queueNode.size() > 0){
			currentNode = queueNode.removeFirst();
			if(currentNode.children != null) queueNode.addAll(currentNode.children);
			dos.writeInt(currentNode.support);
		}
		dos.flush();
		
		return System.currentTimeMillis() - start;
	}
	
	/**
	 * Receive array of support counts from slave
	 * @param dis
	 * @param supports	output parameter
	 * @return the time to receive array of support from a slave, -1 if failed
	 * @throws IOException
	 */
	public static long receive_supportCountArray_fromSlave(DataInputStream dis, int[] supports) throws IOException{
		// Waiting for receiving array of support from a slave
		int count = dis.readInt();	// Receive the number of nodes = supports.length
		long start = System.currentTimeMillis();
		
		// Receive all supports
		for(int i=0; i<count; i++) supports[i] = dis.readInt();
		
		return System.currentTimeMillis() - start;
	}
	
	/**
	 * Master accumulates the support counts in the array 'supports' into the supports of nodes of the FPO tree
	 * @param supports
	 * @return
	 */
	public long accumulate_supportCount(int[] supports){
    	long start = System.currentTimeMillis();
    	
    	if(this.nodeCount != supports.length) return -1;
		
		LinkedList<FPONode> queueNode = new LinkedList<FPONode>();
		FPONode currentNode;
		queueNode.addAll(root.children);
		
		int index = 0;
		while(queueNode.size() > 0){
			currentNode = queueNode.removeFirst();
			if(currentNode.children != null) queueNode.addAll(currentNode.children);
			currentNode.support += supports[index];
			index++;
		}
    	
		return System.currentTimeMillis() - start;
	}
	
	/**
	 * Prune all nodes whose support counts < support_threshold
	 * @param support_count_threshold
	 */
	public long prune(int support_count_threshold){
		long start = System.currentTimeMillis();
		
		if(this.root == null) return -1;
		this.prune_recursive(this.root, support_count_threshold);
		
		return System.currentTimeMillis() - start;
	}	
	
	/**
	 * Prune recursively all child nodes of currNode. 
	 * @param currNode
	 * @param support_count_threshold
	 */
	private void prune_recursive(FPONode currNode, int support_count_threshold){
		// Collect then remove all infrequent nodes
		{
			LinkedList<FPONode> removeNodes = new LinkedList<FPONode>();
			for(FPONode node : currNode.children) if(node.support < support_count_threshold) removeNodes.add(node);
			while(removeNodes.size()>0) currNode.children.remove(removeNodes.removeFirst());
		}
		
		if(currNode.children.size() == 0) currNode.children = null;
		else{
			for(FPONode node : currNode.children) {
				if(node.children != null) prune_recursive(node, support_count_threshold);
			}
		}
	}
	
	/**
	 * In parallel way, generate and write by filtering all frequent patterns (based on 'support_threshold') in the FPO-Tree
	 * </br> Update nodeCount
	 * </br> After finishing, the nodeCount is the number of frequent itemsets
	 * @param fileName
	 * @param support_threshold
	 * @throws InterruptedException
	 */
	public long filter_write_frequentPatterns(String[] index_item,
												String fileName,
												int support_threshold) throws InterruptedException{
		long start = System.currentTimeMillis();
		
		FPONode[] l1_nodes = new FPONode[root.children.size()];
		root.children.toArray(l1_nodes);
		int[] node_counts_arr = new int[root.children.size()];	// Retain the frequent itemsets count of each sub tree
		
		// Spawn threads to write frequent itemsets
		IntHolder globalIndex = new IntHolder(0);
		Thread[] threads = new Thread[Math.min(THREAD_COUNT, 4)];
		//Thread[] threads = new Thread[THREAD_COUNT];
		for(int i=0; i<threads.length; i++){
			threads[i] = new WriteFrequentItemsetsThread(index_item,
														fileName,
														l1_nodes,
														node_counts_arr,
														support_threshold,
														false,
														globalIndex,
														i);
			threads[i].start();
		}
		for(int i=0; i<threads.length; i++) threads[i].join();
		
		// Summarize total number of frequent itemsets
		this.nodeCount = 0;
		for(int node_count : node_counts_arr) this.nodeCount += node_count;
		
		return System.currentTimeMillis() - start;
	}
	
	/**
	 * In parallel way, generate and write by filtering all frequent patterns (based on 'support_threshold') in the FPO-Tree
	 * </br> Note: Do not include nodes at level 1 (the root node at level 0)
	 * </br> Update nodeCount
	 * </br> After finishing, the nodeCount is the number of frequent itemsets (excluding 1-itemsets)
	 * @param fileName
	 * @param support_threshold
	 * @throws InterruptedException
	 */
	public long filter_write_frequentPatterns_omitL1(String[] index_item,
													String fileName,
													int support_threshold) throws InterruptedException{
		long start = System.currentTimeMillis();
		
		// Calculate the number of level 2 nodes;
		int l2_node_count = 0;
		for(FPONode l1_node : this.root.children){
			if(l1_node.children != null) l2_node_count += l1_node.children.size();
		}
		
		FPONode[] l2_nodes = new FPONode[l2_node_count];
		int[] node_counts_arr = new int[l2_node_count];	// Retain the frequent itemsets count of each sub tree
		
		// Fill level 2 nodes into l2_nodes
		int index = 0;
		for(FPONode l1_node : this.root.children){
			if(l1_node.children == null) continue;
			for(FPONode node : l1_node.children){
				l2_nodes[index] = node;
				index++;
			}
		}
		
		// Spawn threads to write frequent itemsets
		IntHolder globalIndex = new IntHolder(0);
		Thread[] threads = new Thread[Math.min(THREAD_COUNT, 4)];
		//Thread[] threads = new Thread[THREAD_COUNT];
		for(int i=0; i<threads.length; i++){
			threads[i] = new WriteFrequentItemsetsThread(index_item,
														fileName,
														l2_nodes,
														node_counts_arr,
														support_threshold,
														true,	// omit level 1 nodes
														globalIndex,
														i);
			threads[i].start();
		}
		for(int i=0; i<threads.length; i++) threads[i].join();
		
		// Summarize total number of frequent itemsets
		this.nodeCount = 0;
		for(int node_count : node_counts_arr) this.nodeCount += node_count;
		
		return System.currentTimeMillis() - start;
	}
	
	/**
	 * FOR TESTING - Show the content of the tree
	 */
	public void showTreeContent(){
		LinkedList<FPONode> nodeQueue = new LinkedList<FPONode>();
		FPONode currentNode;
		
		int inner_count = 0;
		int count = root.children.size();
    	nodeQueue.addAll(root.children); 	// Add to the end of the list	
    	while(nodeQueue.size() > 0){
    		currentNode = nodeQueue.removeFirst();
    		if(currentNode == null) {
    			System.out.println("------");
    		}else{
    			System.out.println(this.getPattern(currentNode) + " : " + currentNode.support);
    			if(currentNode.children != null) {
	    			nodeQueue.add(null);	// Add the delimiter
	    			nodeQueue.addAll(currentNode.children);
	    			inner_count++;
	    			count = count + currentNode.children.size();
	    		}
    		}
    	}
    	
    	// nodeCount = innerNodeCount + leafNodeCount
    	this.nodeCount = count;
    	this.innerNodeCount = inner_count;
    	this.leafNodeCount = count - inner_count;

    	System.out.println("Node count: " + nodeCount);
    	System.out.println("Inner node count: " + innerNodeCount);
    	System.out.println("Leaf node count: " + leafNodeCount);
    	System.out.println("-------------------------");
	}
	
	/**
	 * FOR TESTING - Get pattern corresponding to the node
	 */
	private String getPattern(FPONode node){
		StringBuilder sb = new StringBuilder();
		List<FPONode> nodelist = new ArrayList<FPONode>();
		
		nodelist.add(node);
		FPONode currentNode = node.parent;
		while(currentNode.parent != null){
			nodelist.add(currentNode);
			currentNode = currentNode.parent;
		}
		
		for(int i=nodelist.size()-1; i>-1; i--) sb.append(nodelist.get(i).code).append(' ');
		sb.setLength(sb.length()-1);
		return sb.toString();
	}
	
	/////////////////////////////////////////////// DIFIN's METHODS //////////////////////////////////////////////////

	/**
	 * FOR DIFIN ONLY - Slaves build the FPO-Tree from frequent items.
	 * @param itemsMapToFrequencies
	 * @param item_globalIndex
	 * @param support_count
	 * @return running time
	 */
	public long build_from_1IS(Map<String, Integer> itemsMapToFrequencies, Map<String, Integer> item_globalIndex, int support_count){
		long start = System.currentTimeMillis();
		
		int[] codes = new int[1];
		int sp_value;
		for(Entry<String, Integer> entry : itemsMapToFrequencies.entrySet()){
			sp_value = entry.getValue();
			if(sp_value < support_count) continue;
			codes[0] = item_globalIndex.get(entry.getKey());
			insertFrequentPattern(codes, 1);
		}
		
		return System.currentTimeMillis() - start;
	}
	
	/**
	 * FOR DIFIN ONLY - Slaves build the FPO-Tree from frequent k-itemsets, k >= 2
	 * @param frequentPatterns
	 * @param item_globalIndex
	 * @return running time
	 */
	public long build_from_kIS(Map<String, Integer> frequentPatterns, Map<String, Integer> item_globalIndex){
		long start = System.currentTimeMillis();
		
		int[] codes = new int[50];
		int index, pos, end;
		for(String pattern : frequentPatterns.keySet()){
			pos = 0;
			index = 0;
			// Parse item by item
            while ((end = pattern.indexOf(' ', pos)) >= 0) {
                codes[index] = item_globalIndex.get(pattern.substring(pos, end));
                pos = end + 1;
                index++;
            }
            // Get the last item
            codes[index] = item_globalIndex.get(pattern.substring(pos));
            // The codes in 'codes' has already been in ascending order because
            // the original order and the local order are the same for the items
            index++;	// The value of 'index' is now the count of codes
			insertFrequentPattern(codes, index);
		}
		
		return System.currentTimeMillis() - start;
	}
	
	/**
	 * FOR DIFIN ONLY - Slaves update support count for nodes which their corresponding itemsets are NOT frequent locally.
	 * </br>- This means that the support counts of these itemsets have been not known yet by the Master.
	 * </br>- Support count of other nodes corresponding to local frequent itemsets, of course, had been known by the Master.
	 * @return running time, -1 if failed
	 */
	public long update_supportCount(FISMAlgorithm fism_alg, boolean isFirstThreshold){
		long start = System.currentTimeMillis();
		
		Map<String, Integer> itemMaptoFrequencies = fism_alg.itemsMaptoFrequencies;
		Map<String, Integer> item_localIndex = fism_alg.item_localIndex;
		List<String> globalIndex_item = fism_alg.globalIndex_item;
		int[][] matrix = fism_alg.matrix.get2DArray();
		int support_count = fism_alg.supportCountThreshold();
		
		Integer local_indexl1, local_indexl2, support;
		int local_indexl1_value, local_indexl2_value, support_value;
		String item1, item2;
		
		String[] prefix_items = new String[2];
		boolean subItemset_isFreq = false;
		
		for(FPONode node_l1 : root.children){
			// Check if the item1 exists in dataset
			item1 = globalIndex_item.get(node_l1.code);
			support = itemMaptoFrequencies.get(item1);
			if(support == null) continue;
			
			// Check if the item1 is not frequent
			support_value = support.intValue();
			if(support_value < support_count) node_l1.support = support_value;
			
			// height level = 2
			if(node_l1.children != null){
				// Item1 exists in the local dataset. Check if item1 exists in the matrix
				local_indexl1 = item_localIndex.get(item1);
				if(local_indexl1 == null){ // Item1 do not exist in the matrix
					for(FPONode node_l2 : node_l1.children){
						item2 = globalIndex_item.get(node_l2.code);
						if(itemMaptoFrequencies.get(item2) == null) continue;
						// If item2 exists in the local dataset, caculate nodelist for 2-itemset: item1_item2
						node_l2.support = fism_alg.calculate_supportCount_2Itemset(item1, item2);
						
						// Height level >= 3
						if(node_l2.children != null){
							for(FPONode node_l3 : node_l2.children){
								// Construct prefix_items.
								prefix_items[0] = globalIndex_item.get(node_l1.code);
								prefix_items[1] = globalIndex_item.get(node_l2.code);
								// Update recursively support count for nodes of level >= 3
								if(isFirstThreshold) this.update_supportCount_recursive_first(node_l3, prefix_items, false, fism_alg);
								else this.update_supportCount_recursive_afterFirst(node_l3, prefix_items, false, fism_alg);
							}
						}// End height level >= 3
					}
				}else{	// Item1 exist in the matrix
					local_indexl1_value = local_indexl1.intValue();
					for(FPONode node_l2 : node_l1.children){
						item2 = globalIndex_item.get(node_l2.code);
						// Check if the item2 exists in the matrix
						local_indexl2 = item_localIndex.get(item2);
						if (local_indexl2 == null){	// Item2 do not exist in the matrix
							if(itemMaptoFrequencies.get(globalIndex_item.get(node_l2.code)) == null) continue;
							// If item2 exists in the local dataset, caculate nodelist for 2-itemset: item1_item2
							node_l2.support = fism_alg.calculate_supportCount_2Itemset(item1, item2);
							subItemset_isFreq = false;
						}else{ // Item2 exist in the matrix
							// Get the support count for the 2-itemset constructed from the two items.
							// node_l1.code < node_l2.code --> local_indexl1_value < local_indexl1_value
							local_indexl2_value = local_indexl2.intValue();
							support_value = matrix[local_indexl1_value][local_indexl2_value];
							// If the 2-itemset is not frequent, report its support count.
							if(support_value < support_count){
								node_l2.support = support_value;
								subItemset_isFreq = false;
							}else subItemset_isFreq = true;
						}
						
						// Height level >= 3
						if(node_l2.children != null){
							for(FPONode node_l3 : node_l2.children){
								// Construct prefix_items.
								prefix_items[0] = globalIndex_item.get(node_l1.code);
								prefix_items[1] = globalIndex_item.get(node_l2.code);
								// Update recursively support count for nodes of level >= 3
								if(isFirstThreshold) this.update_supportCount_recursive_first(node_l3, prefix_items, subItemset_isFreq, fism_alg);
								else this.update_supportCount_recursive_afterFirst(node_l3, prefix_items, subItemset_isFreq, fism_alg);
							}
						}// End height level >= 3
					}
				}
			}// End height level 2
		}
		
		return System.currentTimeMillis() - start;
	}
	
	/**
	 * FOR DIFIN ONLY - Slaves call this recursive function to calculate 
	 * and update support count for the itemset corresponding to the node.
	 * </br>- If the itemset is not locally frequent, all super itemsets of this itemset are
	 * also processed in the same way recursively.
	 * @param node	the node need to be determined its support count
	 * @param prefix_items
	 * @param subItemset_isFreq
	 * @param fism_alg
	 */
	private void update_supportCount_recursive_first(FPONode node, String[] prefix_items, boolean subItemset_isFreq, FISMAlgorithm fism_alg){
		// Local data set does not include this item, obviously the itemsets containing this item is not existing
		if( fism_alg.itemsMaptoFrequencies.get(fism_alg.globalIndex_item.get(node.code)) == null) return;
		
		// Prepare itemset corresponding to the node
		String[] itemset = new String[prefix_items.length+1];
		System.arraycopy(prefix_items, 0, itemset, 0, prefix_items.length);
		itemset[prefix_items.length] = fism_alg.globalIndex_item.get(node.code);
		
		boolean itemset_isFreq = false;
		if(subItemset_isFreq){
			// The sub itemset is frequent, so the itemset may be frequent. 
			// We must check whether the itemset is frequent
			StringBuilder sb = new StringBuilder();
			sb.append(itemset[0]);
			for(int i=1; i<itemset.length; i++){
				sb.append(' ').append(itemset[i]);
			}
			
			// If the itemset is not frequent, this means that its support count has not been accumulated by master.
			// Therefore, its support must be calculated and reported.
			if(fism_alg.frequentPatterns.get(sb.toString()) == null){
				node.support = fism_alg.calculate_supportCount_kItemset(itemset);
			}else itemset_isFreq = true;
		}else{
			// The sub itemset is not frequent, so the itemset is not frequent
			node.support = fism_alg.calculate_supportCount_kItemset(itemset);
		}
		
		// Call recursively for all its child nodes if it has. 
		if(node.children != null){
			for(FPONode childNode : node.children) update_supportCount_recursive_first(childNode, itemset, itemset_isFreq, fism_alg);
		}
	}
	
	/**
	 * FOR DIFIN ONLY - Slaves call this recursive function to calculate 
	 * and update support count for the itemset corresponding to the node. 
	 * </br>- If the itemset is not locally frequent, all super itemsets of this itemset are also processed in the same way recursively.
	 * @param node	the node need to be determined its support count
	 * @param prefix_items
	 * @param subItemset_isFreq
	 * @param fism_alg
	 */
	private void update_supportCount_recursive_afterFirst(FPONode node, String[] prefix_items, boolean subItemset_isFreq, FISMAlgorithm fism_alg){		
		// Local data set does not include this item, obviously the itemsets containing this item is not existing
		if( fism_alg.itemsMaptoFrequencies.get(fism_alg.globalIndex_item.get(node.code)) == null) return;
		
		// Prepare itemset corresponding to the node
		String[] itemset = new String[prefix_items.length+1];
		System.arraycopy(prefix_items, 0, itemset, 0, prefix_items.length);
		itemset[prefix_items.length] = fism_alg.globalIndex_item.get(node.code);
		
		boolean itemset_isFreq = false;
		if(subItemset_isFreq){
			// The sub itemset is frequent, so the itemset may be frequent. 
			// We must check whether the itemset is frequent
			StringBuilder sb = new StringBuilder();
			sb.append(itemset[0]);
			for(int i=1; i<itemset.length; i++){
				sb.append(' ').append(itemset[i]);
			}
			String str_itemset = sb.toString();
			
			// If the itemset is not frequent, this means that its support count has not been accumulated by master.
			// Therefore, its support must be calculated and reported.
			if(fism_alg.frequentPatterns.get(str_itemset) == null){
				node.support = fism_alg.calculate_supportCount_kItemset(itemset, str_itemset);
			}else itemset_isFreq = true;
		}else{
			StringBuilder sb = new StringBuilder();
			sb.append(itemset[0]);
			for(int i=1; i<itemset.length; i++){
				sb.append(' ').append(itemset[i]);
			}
			// The sub itemset is not frequent, so the itemset is not frequent
			node.support = fism_alg.calculate_supportCount_kItemset(itemset, sb.toString());
		}
		
		// Call recursively for all its child nodes if it has. 
		if(node.children != null){
			for(FPONode childNode : node.children) update_supportCount_recursive_afterFirst(childNode, itemset, itemset_isFreq, fism_alg);
		}
	}
	
	/////////////////////////////////////////// OTHER FISMAlgorithm's METHODS ///////////////////////////////////////
	
	/**
	 * Slaves build the FPO-Tree from frequent items.
	 * @param itemCodesMapToFrequencies
	 * @return running time
	 */
	public long build_from_1IS(Map<Integer, Integer> itemCodesMapToFrequencies, int support_count){
		long start = System.currentTimeMillis();
		
		int[] codes = new int[1];
		for(Entry<Integer, Integer> entry : itemCodesMapToFrequencies.entrySet()){
			if(entry.getValue() < support_count) continue;
			codes[0] = entry.getKey();
			insertFrequentPattern(codes, 1);
		}
		
		return System.currentTimeMillis() - start;
	}
	
	/**
	 * In a parallel method, slaves build the FPO-Tree from frequent k-itemsets, k >= 2.
	 * @param hFrequentPatterns
	 * @param from_2itemsets
	 * @return running time
	 * @throws InterruptedException
	 */
	public long build_from_kIS(Map<String, FPSubset> hFrequentPatterns, boolean just_2itemsets) throws InterruptedException{
		long start = System.currentTimeMillis();
        
        // Build FPO tree from frequent 2-itemsets
        FPONode[] fpoNodeList_l2 = new FPONode[hFrequentPatterns.size()];
        int[] codes = new int[2];
        String[] i1i2_list;
        int index = 0;
        for(String i1i2 : hFrequentPatterns.keySet()){
        	i1i2_list = i1i2.split(" ");
    		codes[0] = Integer.parseInt(i1i2_list[0]);
    		codes[1] = Integer.parseInt(i1i2_list[1]);
    		// FPONode level2 is maintained in 'fpoNodeList_l2'.
    		fpoNodeList_l2[index] = FPOTree.insertFrequentPattern(codes, 2, this.root);
    		index++;
        }
         
		// Threads
        if(!just_2itemsets){
        	String[] frequent_2itemsets = new String[hFrequentPatterns.size()];
            hFrequentPatterns.keySet().toArray(frequent_2itemsets);
        	IntHolder globalIndex = new IntHolder(0);
            Thread[] threads = new Thread[THREAD_COUNT];
            for(int i=0; i<THREAD_COUNT; i++){        	
            	threads[i] = new FPOTreeBuildingThread(hFrequentPatterns,
    													frequent_2itemsets,
    													fpoNodeList_l2,
    													globalIndex, i);
            	threads[i].start();
            }
            for(int i=0; i<THREAD_COUNT; i++) threads[i].join();
        }
		
		return System.currentTimeMillis() - start;
	}
	
	/**
	 * In parallel way, slaves update support counts for nodes which their corresponding itemsets are NOT frequent locally.
	 * @param fism_alg
	 * </br>This means that the support counts of these itemsets have been not known yet by the Master.
	 * </br>Support count of other nodes corresponding to local frequent itemsets, of course, had been known by the Master.
	 * @return running time, -1 if failed
	 * @throws InterruptedException 
	 */
	public long update_supportCount(FISMAlgorithm fism_alg) throws InterruptedException{
		long start = System.currentTimeMillis();

		Map<Integer, Integer> itemCodesMaptoFrequencies = fism_alg.itemCodesMaptoFrequencies;
		int[][] matrix = fism_alg.matrix.get2DArray();
		List<FPONode> l2_nodes_list = new LinkedList<FPONode>();
		
		for(FPONode node_l1 : root.children){
			// Update support count
			node_l1.support = itemCodesMaptoFrequencies.get(node_l1.code);
			
			// If node_l1.code does not exist in the local dataset, its support = 0
			if(node_l1.support == 0) continue;
			
			// height level = 2
			if(node_l1.children != null){
				for(FPONode node_l2 : node_l1.children){
					// If node_l2.code does not exist in the local dataset, its support = 0
					if(itemCodesMaptoFrequencies.get(node_l2.code) == 0) continue;
					
					// Update support count
					node_l2.support = matrix[node_l1.code][node_l2.code];
					
					// Collect nodes level 2
					if(node_l2.children != null) l2_nodes_list.add(node_l2);
				}
			}// End height level 2
		}// End height level 1
		
		// Fill nodes level 2 to array l2_nodes_arr
		FPONode[] l2_nodes_arr = new FPONode[l2_nodes_list.size()];
		l2_nodes_list.toArray(l2_nodes_arr);
		l2_nodes_list = null;
		
		// Spawn threads to update support count
		IntHolder globalIndex = new IntHolder(0);
		Thread[] threads = new Thread[THREAD_COUNT];
		for(int i=0; i<threads.length; i++){
			threads[i] = new UpdateSupportCountThread(l2_nodes_arr, fism_alg, globalIndex, i);
			threads[i].start();
		}
		for(int i=0; i<threads.length; i++) threads[i].join();
		
		return System.currentTimeMillis() - start;
	}
}