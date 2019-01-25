import com.rocketsoftware.mvapi.MVConnection;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import java.io.IOException;
import java.net.ServerSocket;

public class Main {

	static ObjectPool<MVConnection> pool;
	private static ServerSocket serverSocket;
	private static Listener listener;
	static String mvConn = "";
	static String MVSPUser = "";                // Username for MVSP
	static String MVSPPassword = "";            // Password for MVSP
	static String MVAccount = "";               // MV Account Name
	static String MVPassword = "";              // MV Account Password
	private static String MVHostIP = "";        // Server hostname or port
	private static String MVPort = "0";         // Port on server
	private static int SocketPort = 0;          // Port for this to listen for requests
	private static String platform = "mvBase";  // Platform: mvBase and d3 should work
	private static int NumberOfLines = 1;       // Number of licenses/lines to keep in pool
	
	public static void main(String[] args) {

		mvConn = "jdbc:mv:"+platform+":"+MVHostIP+":"+MVPort;
		
		System.out.println("Press Ctrl-C to Shut Down Server");
		System.out.println("System Initializing...");

		Runtime.getRuntime().addShutdownHook(new Thread(Main::shutdownServer, "Shutdown-thread"));

		System.out.println("    Initializing MvBase connections");
		GenericObjectPoolConfig config = new GenericObjectPoolConfig();
		config.setMaxTotal(NumberOfLines);
		pool = new GenericObjectPool<>(new MvFactory(), config);

		try {
			MVConnection conn = pool.borrowObject();
			System.out.println(conn.fileRead("", "")); // It seemed that an intial read on a file helped the connection initialize
			pool.returnObject(conn);

			System.out.println("    Initializing socket listener");
			try {
				serverSocket = new ServerSocket(SocketPort);
				listener = new Listener(serverSocket);
				listener.start();
			} catch (IOException e) {
				e.printStackTrace();
			}
			System.out.println("Server Initialized and Ready");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void shutdownServer() {
		System.out.println("==============================");
		System.out.println("SHUTTING DOWN THE SERVER!!!!!!");
		try {
			serverSocket.close();
		} catch (IOException e) {
			//e.printStackTrace();
		}
		listener.interrupt();
		while (pool.getNumIdle() > 0 || pool.getNumActive() > 0) {
			System.out.println("Connections  Idle:  " + pool.getNumIdle() + "    Active:  " + pool.getNumActive());
			try {
				MVConnection conn = pool.borrowObject();
				conn.close();
				pool.invalidateObject(conn);
			} catch (Exception e) {
				//e.printStackTrace();
			}
		}
		System.out.println("System is currently OFFLINE");
	}

}
