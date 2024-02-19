package dfism.threads;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.net.Socket;
import java.util.ArrayList;

public class LocalFrequentItemsReceivingThread extends Thread{
	int id;
	Socket socket;
	int[] localTransCount;
	ArrayList<ArrayList<String>> local_frequent_item_arrays;
	
	public LocalFrequentItemsReceivingThread(int id, Socket socket, 
											int[] localTransCount,
											ArrayList<ArrayList<String>> local_frequent_item_arrays){
		this.id = id;
		this.socket = socket;
		this.localTransCount = localTransCount;
		this.local_frequent_item_arrays = local_frequent_item_arrays;
		this.setPriority(Thread.MAX_PRIORITY);
	}
	
	public void run(){
		try{
			long start = System.currentTimeMillis();
			DataInputStream dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
			
			StringBuilder sb = new StringBuilder();
			sb.append(this.getClass().getSimpleName()).append(' ').append(id);
			int base_length = sb.length();
			
			// Receive local transaction count
			localTransCount[id] = dis.readInt();
			
			// Receive the number of local frequent items
			int item_count = dis.readInt();	
			long wait_time = System.currentTimeMillis() - start;
			
			// Receive the items
			ArrayList<String> local_items = new ArrayList<String>(item_count);
			for(int i=0; i<item_count; i++) local_items.add(dis.readUTF());
			
			synchronized(local_frequent_item_arrays){
				local_frequent_item_arrays.add(local_items);
			}

			long receive_time = System.currentTimeMillis() - start - wait_time;
			
			// Just for testing
			sb.append(" waited in ").append(wait_time).append(" ms");
			System.out.println(sb.toString());
			sb.setLength(base_length);
			sb.append(" received local frequent items in ").append(receive_time).append(" ms");
			System.out.println(sb.toString());
		}
		catch(Exception e){e.printStackTrace();}
	} 
}