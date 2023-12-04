package dfism.threads;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.net.Socket;

import dfism.fpo.FPOTree;

public class Local2ItemsetsFPOTreeReceivingThread extends Thread{
	int id;
	Socket socket;
	FPOTree[] fpo_trees;
	
	public Local2ItemsetsFPOTreeReceivingThread(int id, Socket socket, FPOTree[] fpo_trees){
		this.id = id;
		this.socket = socket;
		this.fpo_trees = fpo_trees;
		this.setPriority(Thread.MAX_PRIORITY);
	}
	
	public void run(){
		try{
			long start = System.currentTimeMillis();
			DataInputStream dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
			
			StringBuilder sb = new StringBuilder();
			sb.append(this.getClass().getSimpleName()).append(' ').append(id);
			int base_length = sb.length();
			
			FPOTree tree = fpo_trees[id] = new FPOTree();
			long receive_time = tree.receive_only_itemCode(dis);
			long wait_time = System.currentTimeMillis() - start - receive_time;
			
			// Just for testing
			sb.append(" waited in ").append(wait_time).append(" ms");
			System.out.println(sb.toString());
			sb.setLength(base_length);
			sb.append(" received a local FPM tree of 2-itemsets in ").append(receive_time).append(" ms");
			System.out.println(sb.toString());
		} catch(Exception e){
			e.printStackTrace();
		}
	} 
}