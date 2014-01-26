package org.mp3transform.tools;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

public class CreateIndex {

    public static void main(String... args) throws IOException {
        new CreateIndex().runTool(args);
    }

    private void runTool(String[] args) throws IOException {
        // String dir = "/Volumes/WD/music/az";

        String dir = "/Users/thomasm/Music/iTunes/iTunes Music";
	PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(dir + "/index.html")));
        index(writer, new File(dir));
        writer.close();
    }

    private void index(PrintWriter writer, File dir) throws IOException {
        InputStream in = getClass().getResource("index.html").openStream();
        StringBuilder buff = new StringBuilder();
        while (true) {
            int x = in.read();
            if (x < 0) {
                break;
            }
            buff.append((char) x);
        }
        in.close();
        String html = buff.toString();
        buff = new StringBuilder();
        int count = 0, max = Integer.MAX_VALUE;
        for (File artist : dir.listFiles()) {
            if (artist.isFile()) {
                continue;
            } else if (count > max) {
                break;
            }
            for (File album : artist.listFiles()) {
                if (album.isFile()) {
                    continue;
                } else if (count > max) {
                    break;
                }
                String albumName = artist.getName() + "/" + album.getName();
                albumName = escape(albumName);
                buff.append("<tr onclick=\"play(this, '" + albumName + "', [");
                int i=0;
                for (File song : album.listFiles()) {
                    if (song.getName().endsWith(".mp3")) {
                        if (i++ > 0) {
                            buff.append(',');
                        }
                        buff.append('\'').append(escape(song.getName())).append('\'');
                    }
                }
                buff.append("])\" onmouseover=\"show(this, '"+albumName+"')\" onmouseout=\"hide(this)\">");
                buff.append("<td>" + artist.getName() + "</td>");
                buff.append("<td>"+album.getName()+"</td></tr>\n");
                count++;
            }
        }
        html = replaceAll(html, "%list%", buff.toString());
        writer.println(html);
    }

    private static String escape(String s) {
        int length = s.length();
        StringBuilder buff = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            char c = s.charAt(i);
            switch (c) {
            case '\'':
                // double quote
                buff.append("\\\'");
                break;
            case '"':
                // double quote
                buff.append("\\\"");
                break;
            case '\\':
                // backslash
                buff.append("\\\\");
                break;
            default:
                buff.append(c);
            }
        }
        return buff.toString();
    }

    public static String replaceAll(String s, String before, String after) {
        int next = s.indexOf(before);
        if (next < 0) {
            return s;
        }
        StringBuilder buff = new StringBuilder(s.length() - before.length() + after.length());
        int index = 0;
        while (true) {
            buff.append(s.substring(index, next)).append(after);
            index = next + before.length();
            next = s.indexOf(before, index);
            if (next < 0) {
                buff.append(s.substring(index));
                break;
            }
        }
        return buff.toString();
    }

}
