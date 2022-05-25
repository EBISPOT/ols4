import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;

public class Downloader {

    public String download(String url) throws IOException {

//        ReadableByteChannel readableByteChannel = Channels.newChannel(new URL(url).openStream());
//
//        FileOutputStream fileOutputStream = new FileOutputStream(FILE_NAME);
//        FileChannel fileChannel = fileOutputStream.getChannel();
//
//        fileOutputStream.getChannel()
//                .transferFrom(readableByteChannel, 0, Long.MAX_VALUE);

        return "";
    }



}
