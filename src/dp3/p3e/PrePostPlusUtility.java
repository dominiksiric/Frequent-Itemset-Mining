package dp3.p3e;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

class PrePostPlusUtility {
	private static int currentPreCode;
	private static int currentPosCode;
	
	public static void assignPrePosOrderCode(PPCNode ppcTree){
		resetCurrentCode();
		traverseAssignPrePosOrderCode(ppcTree);
	}
	
	private static void resetCurrentCode(){
		currentPreCode = 1;
		currentPosCode = 1;
	}
    
    /**
     * Traverse the tree with pre&post-order and assign two ordinal numbers for each node
     */
    private static void traverseAssignPrePosOrderCode(PPCNode ppcTree){
    	// Assign a code for the current node
    	ppcTree.pre = currentPreCode;
    	currentPreCode++;
    	
    	// If is not a leaf node, traverse all its children
    	for(PPCNode child : ppcTree.children) traverseAssignPrePosOrderCode(child);
    	
    	ppcTree.pos = currentPosCode;
		currentPosCode++;
    }
    
    /**
     * Store the root of an PPC tree with pre-order traverse, this function is just called one time.
     * To avoid checking whether a node is root (whether its parent is null or not null)
     */
    public static long storePPCTree(PPCNode root, String fileName) throws IOException{
    	// Remember start time
    	long start = System.currentTimeMillis();
    	
    	BufferedWriter output = new BufferedWriter(new FileWriter(fileName));
    	StringBuilder sb = new StringBuilder();
    	
    	output.write(sb.append(-1).append(':').append(root.pre).append(':').append(root.pos).append(':').
    			append(root.itemCode).append(':').append(root.count).append('\n').toString());
    	
    	for(PPCNode child : root.children) storeSubPPCTree(child, output, sb);
    	
    	output.close();
    	
    	// Return time of storing PPC tree
        return System.currentTimeMillis() - start;
    }
    
    /**
     * Store an PPC tree by pre-order traverse, this function will be called recursively
     */
    private static void storeSubPPCTree(PPCNode node, BufferedWriter output, StringBuilder sb) throws IOException{
    	sb.setLength(0);
		output.write(sb.append(node.parent.pre).append(':').append(node.pre).append(':').append(node.pos).append(':').
    			append(node.itemCode).append(':').append(node.count).append('\n').toString());
    	
    	for(PPCNode child : node.children) storeSubPPCTree(child, output, sb);
    }
    
    /**
     * This function generates Descarte production from two sets of sub sets.
     * This power set does not include empty set
     */
    public static void generateDescartProduction(List<String> list1, List<String> list2, List<String> result){
    	StringBuilder sb = new StringBuilder(100);
    	int length;
    	for(String s1 : list1){
    		sb.setLength(0);
    		length = sb.append(s1).append(' ').length();
    		for(String s2 : list2){
    			sb.setLength(length);
    			result.add(sb.append(s2).toString());
    		}
    	}
    }
    
    /**
     * This function generates a set of sub sets building from a list of items.
     * This power set does not include empty set
     */
    public static void generatePowerSet(List<String> items, List<String> powerSet) throws NullPointerException{
    	if(items == null || items.size()==0) return;
    	
    	// Initialize for the powerSet with the first item in the items
    	Iterator<String> iterator = items.iterator();
    	powerSet.add(iterator.next());
    	
    	while(iterator.hasNext()) addNewCombinations(iterator.next(), powerSet);
    }
    
    private static void addNewCombinations(String item, List<String> powerSet){
    	int L = powerSet.size();
    	powerSet.add(item);
    	StringBuilder sb = new StringBuilder(100);
    	for(int i=0; i<L; i++){
    		sb.setLength(0);
    		powerSet.add(sb.append(powerSet.get(i)).append(' ').append(item).toString());
    	}
    }
    
    /**
     * - Calculate the node list from two node list: one of itemset is1 and the other of itemset is2
     * </br>- Result node list is 'nodelist' of itemset created from is1 and is2
     * </br>- is1=common|i1, is2=common|i2, created itemset: common|i1|i2	(common may be is empty string)
     * </br>- NOTE: i1 < i2 in the used order
     * </br>- Calculate complexity is O(m+n)
     * </br>- Note: instead add descendants node as prepost and fin algorithm, here prepostplus add ancestor nodes.
     * This will cause reduce number of nodes in each node list. Consequently, save memory and cpu time
     * @param is1
     * @param is2
     * @param itemset_nodelist
     * @param nodelist	The result nodelist
     */
    public static void create_nodelist_for_itemset(String is1, String is2,
    										Map<String, Nodelist> itemset_nodelist,
    										Nodelist nodelist){
    	Nodelist i1_nodelist = itemset_nodelist.get(is1);
		Nodelist i2_nodelist = itemset_nodelist.get(is2);
		int size1 = i1_nodelist.size(), size2 = i2_nodelist.size();
		if(size1 == 0 || size2 == 0) return;
		
		int index1=0, index2=0, last_node_index = -1, last_node_pre = -1;
		nodelist.allocate(size2); // the maximum capacity for 'nodelist' is as large as 'size2'
		Node i1_node = new Node(), i2_node = new Node();
		i1_nodelist.get(index1, i1_node);
		i2_nodelist.get(index2, i2_node);
		
    	while(true){
    		if(i1_node.pre > i2_node.pre){
    			if(i1_node.pos < i2_node.pos){
    				// This desired case says that: i1_node is a descendant of i2_node. 
    				// So i2_node (ancestor) is added to the node list of i1i2 itemset --> increase index1
    				// NOTE: i2_node can be an ancestor of other nodes in i1_nodelist --> stay index2
    				if(last_node_pre == i2_node.pre){
    					nodelist.accSupportCount(last_node_index, i1_node.count);
    				}else{
    					nodelist.add(i2_node.pre, i2_node.pos, i1_node.count);
    					last_node_pre = i2_node.pre;
    					last_node_index++;
    				}
    				index1++;
    				if(index1 < size1) i1_nodelist.get(index1, i1_node);
    				else break;
    			}else{ // i1_node.pre > i2_node.pre && i1_node.pos > i2_node.pos
    				// This undesired case says that: 
    				// All nodes from i1_node in i1_nodelist are NOT descendant of i2_node --> increase index2
    				// but i1_node can be a descendant of other nodes in i2_nodelist --> stay index1
    				index2++;
    				if(index2 < size2) i2_nodelist.get(index2, i2_node);
    				else break;
    			}
    		}else{ // i1_node.pre < i2_node.pre --> it must be i1_node.pos < i2_node.pos
    			// This undesired case says that: 
				// All nodes from i2_node in i2_nodelist are not ancestors of i1_node --> increase index1
				// but i2_node can be a ancestor of other nodes in i1_nodelist --> stay index2
    			index1++;
    			if(index1 < size1) i1_nodelist.get(index1, i1_node);
				else break;
    		}
    	}
    }
    
    /**
     * - Calculate the node list from two node list: one of itemset is1 and the other of itemset is2
     * </br>- Result node list is 'nodelist' of itemset created from is1 and is2
     * </br>- is1=common|i1, is2=common|i2, created itemset: common|i1|i2	(common may be is empty string)
     * </br>- NOTE: i1 < i2 in the used order
     * </br>- Calculate complexity is O(m+n)
     * </br>- Note: instead add descendants node as prepost and fin algorithm, here prepostplus add ancestor nodes.
     * This will cause reduce number of nodes in each node list. Consequently, save memory and cpu time
     * @param is1
     * @param is2
     * @param itemset_nodelist
     * @param nodelist	The result nodelist
     */
    public static Nodelist create_nodelist_for_itemset(String is1, String is2,
    										Map<String, Nodelist> itemset_nodelist){
    	Nodelist i1_nodelist = itemset_nodelist.get(is1);
		Nodelist i2_nodelist = itemset_nodelist.get(is2);
		int size1 = i1_nodelist.size(), size2 = i2_nodelist.size();
		if(size1 == 0 || size2 == 0) return new NodelistFake();
		
		int index1=0, index2=0, last_node_index = -1, last_node_pre = -1;
		Nodelist nodelist = new Nodelist(size2); // the maximum capacity for 'nodelist' is as large as 'size2'
		Node i1_node = new Node(), i2_node = new Node();
		i1_nodelist.get(index1, i1_node);
		i2_nodelist.get(index2, i2_node);
		
    	while(true){
    		if(i1_node.pre > i2_node.pre){
    			if(i1_node.pos < i2_node.pos){
    				// This desired case says that: i1_node is a descendant of i2_node. 
    				// So i2_node (ancestor) is added to the node list of i1i2 itemset --> increase index1
    				// NOTE: i2_node can be an ancestor of other nodes in i1_nodelist --> stay index2
    				if(last_node_pre == i2_node.pre){
    					nodelist.accSupportCount(last_node_index, i1_node.count);
    				}else{
    					nodelist.add(i2_node.pre, i2_node.pos, i1_node.count);
    					last_node_pre = i2_node.pre;
    					last_node_index++;
    				}
    				index1++;
    				if(index1 < size1) i1_nodelist.get(index1, i1_node);
    				else break;
    			}else{ // i1_node.pre > i2_node.pre && i1_node.pos > i2_node.pos
    				// This undesired case says that: 
    				// All nodes from i1_node in i1_nodelist are NOT descendant of i2_node --> increase index2
    				// but i1_node can be a descendant of other nodes in i2_nodelist --> stay index1
    				index2++;
    				if(index2 < size2) i2_nodelist.get(index2, i2_node);
    				else break;
    			}
    		}else{ // i1_node.pre < i2_node.pre --> it must be i1_node.pos < i2_node.pos
    			// This undesired case says that: 
				// All nodes from i2_node in i2_nodelist are not ancestors of i1_node --> increase index1
				// but i2_node can be a ancestor of other nodes in i1_nodelist --> stay index2
    			index1++;
    			if(index1 < size1) i1_nodelist.get(index1, i1_node);
				else break;
    		}
    	}
    	
    	return nodelist;
    }
    
    /**
     * Calculate the node list from two node lists of 2 itemsets common|i1, common|i2. (i1 < i2, common can be empty)
     * @param nodelist1 of itemset common|i1
     * @param nodelist2 of itemset common|i2
     * @return the nodelist of itemset common|i1|i2
     */
    public static Nodelist create_nodelist_from_2Nodelists(Nodelist nodelist1, Nodelist nodelist2){
    	int size1 = nodelist1.size(), size2 = nodelist2.size();
    	if(size1 == 0 || size2 == 0) return new NodelistFake();
		
    	int index1=0, index2=0, last_node_index = -1, last_node_pre = -1;
		Nodelist nodelist = new Nodelist(size2); // the maximum capacity for 'nodelist' is as large as 'size2'
		Node i1_node = new Node(), i2_node = new Node();
		nodelist1.get(index1, i1_node);
		nodelist2.get(index2, i2_node);
		
    	while(true){
    		if(i1_node.pre > i2_node.pre){
    			if(i1_node.pos < i2_node.pos){
    				// This desired case says that: i1_node is a descendant of i2_node. 
    				// So i2_node (ancestor) is added to the node list of i1i2 itemset --> increase index1
    				// NOTE: i2_node can be an ancestor of other nodes in i1_nodelist --> stay index2
    				if(last_node_pre == i2_node.pre){
    					nodelist.accSupportCount(last_node_index, i1_node.count);
    				}else{
    					nodelist.add(i2_node.pre, i2_node.pos, i1_node.count);
    					last_node_pre = i2_node.pre;
    					last_node_index++;
    				}
    				index1++;
    				if(index1 < size1) nodelist1.get(index1, i1_node);
    				else break;
    			}else{ // i1_node.pre > i2_node.pre && i1_node.pos > i2_node.pos
    				// This undesired case says that: 
    				// All nodes from i1_node in i1_nodelist are NOT descendant of i2_node --> increase index2
    				// but i1_node can be a descendant of other nodes in i2_nodelist --> stay index1
    				index2++;
    				if(index2 < size2) nodelist2.get(index2, i2_node);
    				else break;
    			}
    		}else{ // i1_node.pre < i2_node.pre --> it must be i1_node.pos < i2_node.pos
    			// This undesired case says that: 
				// All nodes from i2_node in i2_nodelist are not ancestors of i1_node --> increase index1
				// but i2_node can be a ancestor of other nodes in i1_nodelist --> stay index2
    			index1++;
    			if(index1 < size1) nodelist1.get(index1, i1_node);
				else break;
    		}
    	}
    	
    	return nodelist;
    }
}
