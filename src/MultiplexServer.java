import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.Set;

public class MultiplexServer {

    private static final int CLIENT_CODE_LENGTH = 1;
    private static final int MAX_FILE_NAME_LENGTH = 1000;

    public static void main(String[] args) throws IOException{

        //open a selector
        Selector monitor = Selector.open();

        ServerSocketChannel welcomeChannel = ServerSocketChannel.open();
        welcomeChannel.socket().bind(new InetSocketAddress(2000));

        //configure the serverSocketChannel to be non-blocking
        //(selector only works with non-blocking channels.)
        welcomeChannel.configureBlocking(false);

        //register the channel and event to be monitored
        //this causes a "selection key" object to be created for this channel
        welcomeChannel.register(monitor, SelectionKey.OP_ACCEPT);

        while (true) {
            // select() is a blocking call (so there is NO busy waiting here)
            // It returns only after at least one channel is selected,
            // or the current thread is interrupted
            int readyChannels = monitor.select();

            //select() returns the number of keys, possibly zero
            if (readyChannels == 0) {
                continue;
            }

            // elements in this set are the keys that are ready
            // i.e., a registered event has happened in each of those keys
            Set<SelectionKey> readyKeys = monitor.selectedKeys();

            Iterator<SelectionKey> iterator = readyKeys.iterator();

            while (iterator.hasNext()) {

                SelectionKey key = iterator.next();
                iterator.remove();

                if (key.isAcceptable()) {
                    // OS received a new connection request from some new client
                    ServerSocketChannel wChannel = (ServerSocketChannel) key.channel();
                    SocketChannel serveChannel = wChannel.accept();

                    //create the dedicated socket channel to serve the new client
                    serveChannel.configureBlocking(false);

                    //register the dedicated socket channel for reading
                    serveChannel.register(monitor, SelectionKey.OP_READ);
                }

                else if (key.isReadable()) {
                    //OS received one or more packets from one or more clients
                    SocketChannel serveChannel = (SocketChannel) key.channel();
                    ByteBuffer buffer = ByteBuffer.allocate(CLIENT_CODE_LENGTH);
                    int bytesToRead = CLIENT_CODE_LENGTH;

                    //make sure we read the entire server reply
                    while((bytesToRead -= serveChannel.read(buffer)) > 0);

                    byte[] a = new byte[CLIENT_CODE_LENGTH];
                    buffer.flip();
                    buffer.get(a);
                    String request = new String(a);
                    System.out.println("Request from a client: " + request);

                    switch(request){
                        case "L":
                            //send reply code to indicate request was accepted
                            sendReplyCode(serveChannel, "S");

                            File[] filesList = new File(".").listFiles();
                            StringBuilder allFiles = new StringBuilder();
                            if (filesList != null){
                                for (File f : filesList){
                                    //ignore directories
                                    if (!f.isDirectory()) {
                                        allFiles.append(f.getName());
                                        allFiles.append(" : ");
                                        allFiles.append(f.length());
                                        allFiles.append("\n");
                                    }
                                }
                            }

                            ByteBuffer data = ByteBuffer.wrap(allFiles.toString().getBytes());
                            serveChannel.write(data);
                            serveChannel.close();
                            break;

                        case "D":
                            //Delete file
                            //create buffer with max file name length
                            buffer = ByteBuffer.allocate(MAX_FILE_NAME_LENGTH);

                            //make sure we read the entire server reply
                            while((serveChannel.read(buffer)) >= 0);

                            buffer.flip();
                            //buffer.remaining() tells the number of bytes in the buffer
                            a = new byte[buffer.remaining()];
                            buffer.get(a);
                            String fileName1 = new String(a);

                            File f = new File(fileName1);
                            if(f.delete()){
                                sendReplyCode(serveChannel,"S");
                            }else{
                                sendReplyCode(serveChannel,"F");
                            }
                            serveChannel.close();
                            break;

                        case "G":
                            //Send file to client
                            buffer = ByteBuffer.allocate(MAX_FILE_NAME_LENGTH);

                            while((serveChannel.read(buffer)) >= 0);

                            buffer.flip();
                            //buffer.remaining() tells the number of bytes in the buffer
                            byte[] b = new byte[buffer.remaining()];
                            buffer.get(b);
                            String fileName2 = new String(b);

                            File file = new File(fileName2);

                            if(true){
                                sendReplyCode(serveChannel,"S");
                            }else{
                                sendReplyCode(serveChannel,"F");
                            }

                            ByteBuffer download = ByteBuffer.wrap(file.toString().getBytes());
                            serveChannel.write(download);
                            serveChannel.close();

                            break;

                        case "R":
                            //Rename file
                            buffer = ByteBuffer.allocate(MAX_FILE_NAME_LENGTH);

                            //make sure we read the entire server reply
                            while((serveChannel.read(buffer)) >= 0);

                            buffer.flip();
                            //buffer.remaining() tells the number of bytes in the buffer
                            byte[] c = new byte[buffer.remaining()];
                            buffer.get(c);
                            String fileName = new String(c);
                            
                            //split fileNames into old and new file names
                            String[] split = fileName.split(" ");

                            File f1 = new File(split[0]);
                            if(f1.renameTo(new File(split[1]))){
                                sendReplyCode(serveChannel,"S");
                            }else{
                                sendReplyCode(serveChannel,"F");
                            }

                            serveChannel.close();
                            break;

                        default:
                            System.out.println("Unknown command!");
                            //send reply code to indicate request was rejected.
                            sendReplyCode(serveChannel, "F");
                    }
                    //note that calling close() will automatically
                    // deregister the channel with the selector
                    serveChannel.close();
                }
            }
        }
    }

    private static void sendReplyCode (SocketChannel channel, String code) throws IOException{
        ByteBuffer data = ByteBuffer.wrap(code.getBytes());
        channel.write(data);
    }
}


