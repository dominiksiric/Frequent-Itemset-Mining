package dfism.threads;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.ArrayList;

import dfism.commons.ItemSupport;

public class GlobalFrequentItemsSendingThread extends Thread{
	int id;
	Socket socket;
	ArrayList<ItemSupport> global_frequent_items;
	
	public GlobalFrequentItemsSendingThread(int id, Socket socket, 
											ArrayList<ItemSupport> global_frequent_items){
		this.id = id;
		this.socket = socket;
		this.global_frequent_items = global_frequent_items;
		this.setPriority(Thread.MAX_PRIORITY);
	}
	
	public void run(){
		try{
			long start = System.currentTimeMillis();
			DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
	    	
			int item_count = global_frequent_items.size();
	    	dos.writeInt(item_count);		// Send the number of frequent items
	    	// Send global frequent items
	    	for(ItemSupport itemSupport : this.global_frequent_items) dos.writeUTF(itemSupport.item);
	    	
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