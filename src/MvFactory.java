import com.rocketsoftware.mvapi.MVConnection;
import com.rocketsoftware.mvapi.exceptions.MVException;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;

import java.util.Properties;

class MvFactory extends BasePooledObjectFactory<MVConnection> {
	
	@Override
	public MVConnection create() {
		MVConnection conn = null;
		Properties props = new Properties();
		props.setProperty("username", Main.MVSPUser);
		props.setProperty("password", Main.MVSPPassword);
		try {
			//Live
			System.out.println("    Connecting");
			conn = new MVConnection(Main.mvConn, props);
			System.out.println("    Connected");
			conn.logTo(Main.MVAccount, Main.MVPassword);
			System.out.println("    Logged In");
		} catch (MVException e) {
			System.err.println("========MVConnection Error========");
			System.err.println(e.getMessage());
			System.err.println(e.getErrorCode());
			System.err.println("==================================");
		}
		return conn;
	}
	
	@Override
	public PooledObject<MVConnection> wrap(MVConnection obj) {
		return new DefaultPooledObject<>(obj);
	}
	
	
}
