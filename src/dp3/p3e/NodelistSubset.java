package dp3.p3e;

import java.util.HashMap;
import java.util.Map;

/**
 * Two public properties:
 * </br> nodelist: node list of a frequent 2-itemset 
 * </br> itemset_nodelist: a map of frequent k-itemsets (k>2) to their node lists. These k-itemset share the same 2-itemset
 */
class NodelistSubset {
	public Nodelist nodelist;
	public Map<String, Nodelist> itemset_nodelist;
	
	public NodelistSubset(Nodelist nodelist) {
		this.nodelist = nodelist;
		this.itemset_nodelist = new HashMap<String, Nodelist>();
	}
}
