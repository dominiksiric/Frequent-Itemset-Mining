package dfism.fpo;

import java.util.HashMap;
import java.util.Map;

/**
 * Two public properties:
 * </br> support: support count of a frequent 2-itemset 
 * </br> fPatterns: a map of frequent k-itemsets (k>2) to their support counts. These k-itemset share the same 2-itemset
 */
public class FPSubset {
	public int support;
	public Map<String, Integer> fPatterns;
	
	public FPSubset(int support) {
		this.support = support;
		this.fPatterns = new HashMap<String, Integer>();
	}
}
