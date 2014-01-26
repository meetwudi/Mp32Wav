package org.mp3transform.awt;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import org.mp3transform.Decoder;

public class Shell {
    public static void main(String[] args) throws Exception {
        Decoder decoder = new Decoder();
        for (int i = 0; i < args.length; i++) {
            File file = new File(args[i]);
            FileInputStream in = new FileInputStream(file);
            BufferedInputStream bin = new BufferedInputStream(in, 128 * 1024);
            decoder.play(file.getName(), bin);
            in.close();
        }
        decoder.stop();
    }
}
