package dfism.threads;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.net.Socket;

public class ParameterSendingThread extends Thread{
	int id;
	Socket socket;
	String parameters;
	
	public ParameterSendingThread(int id, Socket socket, String parameters){
		this.id = id;
		this.socket = socket;
		this.parameters = parameters;
		this.setPriority(Thread.MAX_PRIORITY);
	}
	
	public void run(){
		try{
			//A master thread is spawn
			long start = System.currentTimeMillis();
			DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
			
			//Send mining option
			dos.writeInt(id);
			dos.writeUTF(parameters);
			dos.flush();
			
			// Just for testing
			StringBuilder sb = new StringBuilder();
			sb.append(this.getClass().getSimpleName()).append(' ').append(id).append(" finished work in ")
			.append(System.currentTimeMillis()-start).append(" ms");
			System.out.println(sb.toString());
		}catch(Exception e){
			e.printStackTrace();
		}
	} 
}