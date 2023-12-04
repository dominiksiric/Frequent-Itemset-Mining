package dp3.p3e;

public class Node {
	public int pre;
	public int pos;
	public int count;
	
	public Node(){}
	
	public Node(int pre, int pos, int count){
		this.pre = pre;
		this.pos = pos;
		this.count = count;
	}
	
	public void set(int pre, int pos, int count){
		this.pre = pre;
		this.pos = pos;
		this.count = count;
	}
}
