package dfism.fpo;

import java.util.List;
import java.util.Map;

import dfism.commons.Matrix;

public abstract class FISMAlgorithm {
	public Map<String, Integer> frequentPatterns;
	public Map<String, FPSubset> hFrequentPatterns;
	public Map<String, Integer> itemsMaptoFrequencies;
	public Map<Integer, Integer> itemCodesMaptoFrequencies;
	
	public Map<String, Integer> item_localIndex;
	public List<String> localIndex_item;
	public Map<String, Integer> item_globalIndex;
	public List<String> globalIndex_item;
	
	protected Matrix matrix;
	protected int SUPPORT_COUNT_THRESHOLD;
	
	public int supportCountThreshold(){
		return SUPPORT_COUNT_THRESHOLD;
	}
	
	/**
     * Calculate support count of the infrequent 2-itemset from item1 and item2.
     * @param item1
     * @param item2
     * @return support count
     */
	public int calculate_supportCount_2Itemset(String item1, String item2){
		return 0;
	}
	
	/**
	 * Calculate support count of the infrequent k-itemset.
	 * @param itemset
	 * @param str_itemset	string form of the itemset (space character ' ' between two items)
	 * @return
	 */
	public int calculate_supportCount_kItemset(String[] itemset, String str_itemset){
		return 0;
	}
	
	/**
	 * Calculate support count of the infrequent k-itemset.
	 * @param itemset
	 * @return
	 */
	public int calculate_supportCount_kItemset(String[] itemset){
		return 0;
	}
}