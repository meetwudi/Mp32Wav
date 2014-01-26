package org.mp3transform.alarm;

import java.awt.Button;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.SystemColor;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class AlarmTask implements Task, Runnable {

    private String message;
    private Frame frame;
    private volatile boolean stop;

    public AlarmTask(String message) {
        this.message = message;
    }

    public static void main(String[] args) {
        new AlarmTask("Hello World").execute();
    }

    void close() {
        stop = true;
        frame.dispose();
    }

    @SuppressWarnings("deprecation")
    public void execute() {
        System.out.println("Alarm: " + message);
        frame = new Frame("Alarm");
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent we) {
                close();
            }
        });
        frame.setResizable(false);
        Button m = new Button(message);
        m.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                close();
            }
        });
        frame.add(m);
        frame.setBackground(SystemColor.control);
        int width = 300, height = 320;
        frame.setSize(width, height);
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setLocation((screenSize.width - width) / 2, (screenSize.height - height) / 2);
        frame.show();
        new Thread(this).start();
    }

    @SuppressWarnings("deprecation")
    public void run() {
        while (!stop) {
            System.out.println("Alarm: " + message);
            frame.requestFocus();
            frame.requestFocusInWindow();
            frame.toFront();
            frame.show();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

}
