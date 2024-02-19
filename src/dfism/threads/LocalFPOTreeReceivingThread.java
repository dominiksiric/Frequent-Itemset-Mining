package dfism.threads;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.net.Socket;

import dfism.fpo.FPOTree;

public class LocalFPOTreeReceivingThread extends Thread{
	int id;
	Socket socket;
	FPOTree[] fpo_trees;
	int[] localTransCount;
	
	/**
	 * DIFIN requires localTransCount, others do not (transfer null)
	 * @param id
	 * @param socket
	 * @param fpo_trees
	 * @param localTransCount
	 */
	public LocalFPOTreeReceivingThread(int id, Socket socket, FPOTree[] fpo_trees, int[] localTransCount){
		this.id = id;
		this.socket = socket;
		this.fpo_trees = fpo_trees;
		this.localTransCount = localTransCount;
		this.setPriority(Thread.MAX_PRIORITY);
	}
	
	public void run(){
		try{
			long start = System.currentTimeMillis();
			DataInputStream dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
			
			StringBuilder sb = new StringBuilder();
			sb.append(this.getClass().getSimpleName()).append(' ').append(id);
			int base_length = sb.length();
			
			// While DIFIN receives local transaction count in this thread class, others do not.
			if(localTransCount != null) localTransCount[id] = dis.readInt();
			
			FPOTree tree = fpo_trees[id] = new FPOTree();
			long receive_time = tree.receive_only_itemCode(dis);
			long wait_time = System.currentTimeMillis() - start - receive_time;
			
			// Just for testing
			sb.append(" waited in ").append(wait_time).append(" ms");
			System.out.println(sb.toString());
			sb.setLength(base_length);
			sb.append(" received a local FPM tree in ").append(receive_time).append(" ms");
			System.out.println(sb.toString());
		} catch(Exception e){
			e.printStackTrace();
		}
	} 
}