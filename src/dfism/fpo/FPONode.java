package dfism.fpo;

import java.util.List;

public class FPONode {
	public int code = -1;
	public int support = 0;
	public FPONode parent = null;
	public List<FPONode> children = null;
	
	public FPONode(){}
	
	public FPONode(int code, FPONode parent) {
		this.code = code;
		this.support = 0;
		this.parent = parent;
		this.children = null;
	}
	
	public FPONode(int code, int support) {
		this.code = code;
		this.support = support;
		this.parent = null;
		this.children = null;
	}
}
