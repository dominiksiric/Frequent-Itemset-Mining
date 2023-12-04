package dp3.p3e;

import java.util.ArrayList;
import java.util.List;

class PPCNode {
	
	public int pre = 0;
	public int pos = 0;
    public int count = 0;
    
    public int itemCode = 0;
    public PPCNode parent = null;
    public List<PPCNode> children = null;
    
    /**
     * Build a PPC root node (without parent)
     */
    public PPCNode() {
        children = new ArrayList<PPCNode>();
    }
    
    /**
     * Build a PPC child node having a parent
     */
    public PPCNode(int itemCode, PPCNode parent, int count) {
        this.itemCode = itemCode;
        this.parent = parent;
        this.count = count;
        this.children = new ArrayList<PPCNode>();
    }
    
    /**
     * Build a PPC child node having a parent
     */
    public PPCNode(PPCNode parent, int pre, int pos, int itemCode, int count) {
    	this.parent = parent;
        this.pre = pre;
        this.pos = pos;
        this.itemCode = itemCode;
        this.count = count;
        this.children = new ArrayList<PPCNode>();
    }
    
    public PPCNode(int itemCode, int count) {
        this.itemCode = itemCode;
        this.count = count;
        this.children = new ArrayList<PPCNode>();
    }
}
