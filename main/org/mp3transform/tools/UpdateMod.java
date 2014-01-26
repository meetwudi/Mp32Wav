package org.mp3transform.tools;

import java.io.File;
import java.sql.Timestamp;

public class UpdateMod {
    public static void main(String... args) {
        new UpdateMod().runTool(args);
    }

    private void runTool(String[] args) {
        String dir = "/Users/thomasm/Music/iTunes/iTunes Music";
        // String dir = "/Users/thomasm/data/music";
        // String dir = "/Volumes/WD/music/backup/sorted";
        updateLastModified(new File(dir));
    }

    private long updateLastModified(File file) {
        long latest = Timestamp.valueOf("2000-01-01 12:00:00.0").getTime();
        if (file.getName().endsWith(".DS_Store")) {
            // ignore
        } else if (file.isFile()) {
            latest = file.lastModified();
        } else {
            for(File f : file.listFiles()) {
                latest = Math.max(latest, updateLastModified(f));
            }
            System.out.println(file.getAbsolutePath() + " " + new Timestamp(latest).toString());
            file.setLastModified(latest);
        }
        return latest;
    }

}
