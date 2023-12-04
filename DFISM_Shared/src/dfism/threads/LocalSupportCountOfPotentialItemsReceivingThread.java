package dfism.threads;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.net.Socket;

public class LocalSupportCountOfPotentialItemsReceivingThread extends Thread{
	int id;
	Socket socket;
	int[] local_support_counts;
	
	public LocalSupportCountOfPotentialItemsReceivingThread(int id, Socket socket, int[] local_support_counts){
		this.id = id;
		this.socket = socket;
		this.local_support_counts = local_support_counts;
		this.setPriority(Thread.MAX_PRIORITY);
	}
	
	public void run(){
		try{
			long start = System.currentTimeMillis();
			DataInputStream dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
			
			// Receive the local support count of potential items
			int count = this.local_support_counts.length;
			for(int i=0; i<count; i++) local_support_counts[i] = dis.readInt();
			
			// Just for testing
			StringBuilder sb = new StringBuilder();
			sb.append(this.getClass().getSimpleName()).append(' ').append(id).append(" finished work in ")
			.append(System.currentTimeMillis()-start).append(" ms");
			System.out.println(sb.toString());
		}
		catch(Exception e){e.printStackTrace();}
	} 
}