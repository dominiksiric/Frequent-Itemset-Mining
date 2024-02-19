package dfism.threads;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.net.Socket;

import dfism.fpo.FPONode;
import dfism.fpo.FPOTree;

public class GlobalFPOTreeSendingThread extends Thread{
	private int id;
	private Socket socket;
	private FPONode[] nodeArray;
	private FPONode[] innerNodeArray;
	private byte[] innerNodePositionsInBytes;
	private FPOTree tree;
	
	public GlobalFPOTreeSendingThread(int id, Socket socket, 
									FPONode[] nodeArray,
									FPONode[] innerNodeArray,
									byte[] innerNodePositionsInBytes,
									FPOTree tree){
		this.id = id;
		this.socket = socket;
		this.nodeArray = nodeArray;
		this.innerNodeArray = innerNodeArray;
		this.innerNodePositionsInBytes = innerNodePositionsInBytes;
		this.tree = tree;
		this.setPriority(Thread.MAX_PRIORITY);
	}
	
	public void run(){
		try{
			long start = System.currentTimeMillis();
			DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
			
			FPONode currentNode;
			// Send tree properties
			dos.writeInt(tree.getInnerNodeCount());
			dos.writeInt(tree.getLeafNodeCount());
			dos.writeInt(tree.getNodeCount());
	    	
			// Send node data	    	
	    	int node_index = 1, childCount;
	    	for(FPONode innerNode : innerNodeArray){
	    		childCount = innerNode.children.size();
	    		dos.writeInt(childCount);
	    		for(int i=0; i<childCount; i++){
	    			currentNode = nodeArray[node_index];
	    			dos.writeInt(currentNode.code);	// Send just code
		    		node_index++;
	    		}
	    	}
	    	
	    	// Send positions of the inner nodes in bytes array
	    	dos.writeInt(innerNodePositionsInBytes.length);		// Send the length of the byte array
	    	dos.write(innerNodePositionsInBytes);
	    	
	    	dos.flush();
			
			// Just for testing
			StringBuilder sb = new StringBuilder();
			sb.append(this.getClass().getSimpleName()).append(' ').append(id).append(" finished work in ")
			.append(System.currentTimeMillis()-start).append(" ms");
			System.out.println(sb.toString());
		}
		catch(Exception e){e.printStackTrace();}
	} 
}