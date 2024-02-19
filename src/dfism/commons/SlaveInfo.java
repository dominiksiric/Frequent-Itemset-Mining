package dfism.commons;

import java.net.Socket;

public class SlaveInfo {
	public String ip_address;
	public int port;
	public Socket socket;
	
	public SlaveInfo(String ip, int port) {
		this.ip_address = ip;
		this.port = port;
	}
}
