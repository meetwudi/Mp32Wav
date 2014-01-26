package org.mp3transform.alarm;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

public class ShellTask implements Task {
    
    protected PrintStream out = System.out;
    private String command;
    
    public static void main(String[] args) {
        new ShellTask("cmd /c C:/Programs/tools/ntpdate.bat").execute();
    }
    
    public ShellTask(String command) {
        this.command = command;
    }
    
    public void execute() {
        try {
            Process p = Runtime.getRuntime().exec(command);
            copyInThread(p.getInputStream(), out);
            copyInThread(p.getErrorStream(), out);
            p.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void copyInThread(final InputStream in, final OutputStream out) {
        new Thread() {
            public void run() {
                try {
                    while (true) {
                        int x = in.read();
                        if (x < 0) {
                            return;
                        }
                        out.write(x);
                    }
                } catch (Exception e) {
                    throw new Error("Error: " + e, e);
                }
            }
        } .start();
    }
}
