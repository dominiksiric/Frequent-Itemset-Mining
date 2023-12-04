package dfism.threads;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.net.Socket;

import dfism.fpo.FPOTree;

public class SupportArrayReceivingThread extends Thread{
	int id;
	Socket socket;
	int[] supports;
	
	public SupportArrayReceivingThread(int id, Socket socket, int[] supports){
		this.id = id;
		this.socket = socket;
		this.supports = supports;
		this.setPriority(Thread.MAX_PRIORITY);
	}
	
	public void run(){
		try{
			long start = System.currentTimeMillis();
			
			StringBuilder sb = new StringBuilder();
			sb.append(this.getClass().getSimpleName()).append(' ').append(id);
			int base_length = sb.length();
			
			DataInputStream dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
			
			long receive_time = FPOTree.receive_supportCountArray_fromSlave(dis, supports);			
			long wait_time = System.currentTimeMillis() - start - receive_time;
			
			// Just for testing
			sb.append(" waited in ").append(wait_time).append(" ms");
			System.out.println(sb.toString());
			sb.setLength(base_length);
			sb.append(" received support array in ").append(receive_time).append(" ms");
			System.out.println(sb.toString());
		}
		catch(Exception e){e.printStackTrace();}
	} 
}