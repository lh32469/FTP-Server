package oracle.ocm.utils.ftp.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.logging.Logger;


/**
 *
 * @author Lyle T Harris
 */
public class Main {

    /**
     * Das ist mine logger!
     */
    private static final Logger LOG = Logger.getLogger(Main.class.getName());


    public static void main(String[] args) throws IOException {
        String portNumber = System.getProperty("Port", "8005");

        int port = Integer.parseInt(portNumber);

        LOG.info("FTP Server started at port: " + port);

        ServerSocket server = new ServerSocket(port);

        while (true) {
            Socket s = server.accept();
            Handler h = new Handler(s);
            Thread t = new Thread(h);
            t.start();
        }
    }


}
