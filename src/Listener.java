import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

class Listener extends Thread {
	
	private ServerSocket serverSocket;
	
	Listener(ServerSocket socket) {
		super("Listener");
		serverSocket = socket;
	}
	
	@Override
	public void run() {
		try {
			while (true) {
				Socket socket = serverSocket.accept();
				new MultiServerThread(socket).start();
			}
		} catch (IOException e) {
//			e.printStackTrace();
		}
	}
}
