package dfism.commons;

/**
 * For sorting increasingly
 */
public class ItemSupport implements Comparable<ItemSupport> {
	public String item;
	public int support;
	
	public ItemSupport(String item, int support)  {
		this.item = item;
		this.support = support;
	}

	@Override
	public int compareTo(ItemSupport o) {
		// For sort increasingly
		if(support > o.support) return 1;
		if(support < o.support) return -1; 
		return 0;
	}
}
