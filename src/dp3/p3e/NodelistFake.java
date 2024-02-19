package dp3.p3e;

/**
 * A fake class extends from Nodelist. It does not contain any properties.
 * </br>The fact that may exist itemsets whose support counts = 0, therefore their nodelists are empty.
 * </br>The purpose is to reduce memory overhead. 
 */
class NodelistFake extends Nodelist {
	
 	public NodelistFake(){}
 	
 	public int size(){
 		return 0;
 	}
 	
 	public int capacity(){
 		return 0;
 	}
 	
 	public void shrink(){}
 	
 	public int totalSupportCount(){
 		return 0;
 	}
}
