package org.mp3transform.tools;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PushbackInputStream;

public class ExtractMeta {

    private String fileName;
    private PushbackInputStream source;
    private byte[] rawID3v2;

    public static void main(String... args) throws IOException {
        new ExtractMeta().runTool(args);
    }

    ExtractMeta(String fileName) throws FileNotFoundException {
        this.fileName = fileName;
        source = new PushbackInputStream(new FileInputStream(fileName));
    }

    private void runTool(String[] args) throws IOException {
        String dir = "/Volumes/WD/music/az";
        // String dir = "/Users/thomasm/Music/iTunes/iTunes Music";
        extract(new File(dir));
    }

    private void extract(File file) throws IOException {
        if (file.isDirectory()) {
            for(File f : file.listFiles()) {
                extract(f);
            }
            return;
        } else if (!file.getName().toLowerCase().endsWith(".mp3")) {
            return;
        }
        if (new File(file.getParent(), "cover.jpg").exists()) {
            return;
        }
        new ExtractMeta(file.getAbsolutePath()).extract();
    }

    public ExtractMeta() {
        // nothing to do
    }

    private void extract() throws IOException {
        try {
            while (loadID3v2()) {
                printMeta(false);
            }
        } catch (Exception e) {
            System.out.println("error: " + fileName + " " + e.toString());
        }
    }

    private void printMeta(boolean print) throws IOException {
        if (print) System.out.println(fileName);
        ByteArrayInputStream bin = new ByteArrayInputStream(rawID3v2);
        if (print) System.out.println("  len: " + rawID3v2.length);
        boolean hasPicture = false;
        while (true) {
            DataInputStream din = new DataInputStream(bin);
            byte[] id = new byte[4];
            int l = bin.read(id);
            if (l < 0) {
                break;
            }
            if (id[0] == 0) {
                break;
            }
            int len = din.readInt();
            if (id[3] == 0 && len > 0) {
                len = (len >> 16) - 2;
            } else {
                len += 2;
            }
            if ("APIC".equals(new String(id))) {
                hasPicture = true;
                // flags
                din.read();
                din.read();
                // text encoding
                din.read();
                len -= 3;
                // mime type
                StringBuilder buff = new StringBuilder();
                while (true) {
                    int x = din.read();
                    len--;
                    if (x == 0) {
                        break;
                    }
                    buff.append((char)x);
                }
                // mimeType
                buff.toString();
                // picture type
                din.read();
                len--;
                // description
                while (din.read() != 0) {
                    len--;
                }
                len--;
                byte[] pic = new byte[len];
                din.readFully(pic);
                len = 0;
                FileOutputStream out = new FileOutputStream(new File(fileName).getParent() + "/cover.jpg");
                out.write(pic);
                out.close();
            }
            if (print)  System.out.println("  " + new String(id) + ": " + len);
            for (int i=0; i<len; i++) {
                din.read();
            }
        }
        if (!hasPicture) {
            System.out.println("no picture: " + fileName + " " + rawID3v2.length);
            // System.out.println("  " + new String(rawID3v2));
            // printMeta(true);
        }
    }

    private boolean loadID3v2() {
        int size = -1;
        try {
            // read ID3v2 header
            source.mark(10);
            size = readID3v2Header();
        } catch (IOException e) {
            // ignore
        } finally {
            try {
                // unread ID3v2 header
                source.reset();
            } catch (IOException e) {
                // ignore
            }
        }
        // load ID3v2 tags.
        try {
            if (size > 0) {
                rawID3v2 = new byte[size];
                readBytes(rawID3v2, 0, rawID3v2.length);
                return true;
            }
        } catch (IOException e) {
            // ignore
        }
        return false;
    }

    private int readID3v2Header() throws IOException {
        byte[] buff = new byte[4];
        int size = -10;
        readBytes(buff, 0, 3);
        if (buff[0] == 'I' && buff[1] == 'D' && buff[2] == '3') {
            readBytes(buff, 0, 3);
            readBytes(buff, 0, 4);
            size = (buff[0] << 21) + (buff[1] << 14) + (buff[2] << 7) + buff[3];
        }
        return size + 10;
    }

    private int readBytes(byte[] b, int offs, int len) throws IOException {
        int totalBytesRead = 0;
        while (len > 0) {
            int bytesRead = source.read(b, offs, len);
            if (bytesRead == -1) {
                break;
            }
            totalBytesRead += bytesRead;
            offs += bytesRead;
            len -= bytesRead;
        }
        return totalBytesRead;
    }

}
