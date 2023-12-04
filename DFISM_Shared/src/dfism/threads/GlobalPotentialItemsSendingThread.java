package dfism.threads;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.ArrayList;

public class GlobalPotentialItemsSendingThread extends Thread{
	int id;
	Socket socket;
	ArrayList<String> global_potential_items;
	
	public GlobalPotentialItemsSendingThread(int id, Socket socket, 
											ArrayList<String> global_potential_items){
		this.id = id;
		this.socket = socket;
		this.global_potential_items = global_potential_items;
		this.setPriority(Thread.MAX_PRIORITY);
	}
	
	public void run(){
		try{
			long start = System.currentTimeMillis();
			DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
	    	
			int item_count = global_potential_items.size();
	    	dos.writeInt(item_count);		// Send the number of items
	    	// Send global potential items
	    	for(int i=0; i<item_count; i++) dos.writeUTF(global_potential_items.get(i));
	    	
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