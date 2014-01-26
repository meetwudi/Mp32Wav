package org.mp3transform.awt;

import java.awt.Button;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Label;
import java.awt.List;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemColor;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.prefs.Preferences;

import org.mp3transform.alarm.AlarmTask;
import org.mp3transform.alarm.Scheduler;
import org.mp3transform.alarm.ShellTask;
import org.mp3transform.alarm.Task;
import org.mp3transform.alarm.Scheduler.Job;

public class Player extends PlayerNoCover implements ActionListener, MouseListener {

    private static final String TITLE = "MP3 Player";
    private static final String MP3_SUFFIX = ".mp3";
    private static final String PREF_DIR = "dir", PREF_LISTENER_PORT = "listenerPort";
    private static final int FIRST_PORT = 11100;

    boolean useSystemTray;
    ServerSocket serverSocket;
    Frame frame;

    private Font font;
    private Font fontWebdings;
    private Image icon;
    private File dir;
    private File[] files;
    private List list;
    private PlayerThread thread;
    private Preferences prefs = Preferences.userNodeForPackage(getClass());
    private CoverCanvas coverCanvas;

    /**
     * The command line interface for this tool.
     * The command line options are the same as in the Server tool.
     * 
     * @param args the command line arguments
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        new Player().run();
    }
    
    private void schedule() {
        try {
            if (!prefs.nodeExists("tasks")) {
                return;
            }
            Scheduler scheduler = Scheduler.getInstance();
            Preferences tasks = prefs.node("tasks");
            String[] children = tasks.childrenNames();
            for (int i = 0; i < children.length; i++) {
                String name = children[i];
                Preferences task = tasks.node(name);
                String when = task.get("when", "* * * * *");
                task.put("when", when);
                String type = task.get("type", "alarm");
                Task t;
                if ("alarm".equals(type)) {
                    t = new AlarmTask(task.get("message", ""));
                } else if ("shell".equals(type)) {
                    t = new ShellTask(task.get("command", ""));
                } else {
                    t = new AlarmTask(task.get("message", ""));
                }
                Job job = scheduler.createJob(when, t);
                scheduler.schedule(job);
            }
        } catch (Throwable t) {
            t.printStackTrace();
            // ignore
        }
    }

    private void run() {
        int todoProblemIfCurrentFolderIsDeleted;
        schedule();
        try {
            int port = prefs.getInt(PREF_LISTENER_PORT, 0);
            if (port != 0) {
                Socket socket = new Socket(InetAddress.getLocalHost(), port);
                socket.close();
                // could connect, that means the application already runs
                System.out.println("Already running, listening on port " + port);
                return;
            }
        } catch (Exception e) {
            // ignore - in this case the application does not run
        }
        startListener();
        if (!GraphicsEnvironment.isHeadless()) {
            font = new Font("Dialog", Font.PLAIN, 11);
            fontWebdings = new Font("Webdings", Font.TYPE1_FONT, 11);
            try {
                InputStream in = getClass().getResourceAsStream("mp3.png");
                if (in != null) {
                    byte[] imageData = readBytesAndClose(in, -1);
                    icon = Toolkit.getDefaultToolkit().createImage(imageData);
                }
                useSystemTray = createTrayIcon();
                readDirectory();
                createFrame();
                open();
                readFiles(dir);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void startListener() {
        int port = 0;
        for (int i = 0; i < 100; i++) {
            try {
                int p = FIRST_PORT + i;
                serverSocket = new ServerSocket(p);
                port = p;
                break;
            } catch (IOException e) {
                // ignore
            }
        }
        if (port == 0) {
            // did not work, probably TCP/IP is broken
            return;
        }
        prefs.putInt(PREF_LISTENER_PORT, port);
        Runnable runnable = new Runnable() {
            public void run() {
                while (serverSocket != null) {
                    try {
                        Socket s = serverSocket.accept();
                        s.close();
                        open();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        };
        Thread thread = new Thread(runnable);
        thread.setName(getClass().getName() + " network listener");
        thread.start();
    }

    private boolean createTrayIcon() {
        try {
            // SystemTray.isSupported();
            Boolean supported = (Boolean) Class.forName("java.awt.SystemTray").
                getMethod("isSupported", new Class[0]).
                invoke(null, new Object[0]);
            
            if (!supported.booleanValue()) {
                return false;
            }
            
            PopupMenu menuConsole = new PopupMenu();
            MenuItem itemConsole = new MenuItem(TITLE);
            itemConsole.setActionCommand("open");
            itemConsole.addActionListener(this);
            itemConsole.setFont(font);
            menuConsole.add(itemConsole);
            
            MenuItem itemNext = new MenuItem("Next");
            itemNext.setActionCommand("next");
            itemNext.addActionListener(this);
            itemNext.setFont(font);
            menuConsole.add(itemNext);
            
            MenuItem itemPause = new MenuItem("Pause");
            itemPause.setActionCommand("pause");
            itemPause.addActionListener(this);
            itemPause.setFont(font);
            menuConsole.add(itemPause);

            MenuItem itemStop = new MenuItem("Stop");
            itemStop.setActionCommand("stop");
            itemStop.addActionListener(this);
            itemStop.setFont(font);
            menuConsole.add(itemStop);

            MenuItem itemExit = new MenuItem("Exit");
            itemExit.setFont(font);
            itemExit.setActionCommand("exit");
            itemExit.addActionListener(this);
            menuConsole.add(itemExit);

            // TrayIcon icon = new TrayIcon(image, "MP3 Player", menuConsole);
            Object trayIcon = Class.forName("java.awt.TrayIcon").
                getConstructor(new Class[] { Image.class, String.class, PopupMenu.class }).
                newInstance(new Object[] { icon, TITLE, menuConsole });

            // SystemTray tray = SystemTray.getSystemTray();
            Object tray = Class.forName("java.awt.SystemTray").
                getMethod("getSystemTray", new Class[0]).
                invoke(null, new Object[0]);

            // trayIcon.addMouseListener(this);
            trayIcon.getClass().
                 getMethod("addMouseListener", new Class[]{MouseListener.class}).
                 invoke(trayIcon, new Object[]{this});
             
             // tray.add(icon);
             tray.getClass().
                getMethod("add", new Class[] { Class.forName("java.awt.TrayIcon") }).
                invoke(tray, new Object[] { trayIcon });
             
             return true;
        } catch (Throwable e) {
            return false;
        }
    }
    
    private void exit() {
        if (thread != null) {
            thread.stopPlaying();
        }
        try {
            serverSocket.close();
        } catch (IOException e) {
            // ignore
        }
        serverSocket = null;
        prefs.remove(PREF_LISTENER_PORT);
        System.exit(0);
    }
    
    public void actionPerformed(ActionEvent e) {
        String command = e.getActionCommand();
        if ("exit".equals(command)) {
            exit();
        } else if ("back".equals(command)) {
            if (dir == null) {
                readFiles(null);
            } else {
                readFiles(dir.getParentFile());
            }
        } else if ("play".equals(command)) {
            File f = getSelectedFile();
            if (f != null) {
                play(f);
            }
        } else if ("stop".equals(command)) {
            if (thread != null) {
                thread.stopPlaying();
            }
        } else if ("pause".equals(command)) {
            if (thread != null) {
                boolean paused = thread.pause();
                playingLabel.setText((paused ? "(Paused) " : "") + playingText);
            }
        } else if ("covers".equals(command)) {
            Cover[] list = getCoverList();
            if (list.length > 0) {
                coverCanvas = new CoverCanvas(this, frame, list);
            }
        } else if ("next".equals(command)) {
            if (thread != null) {
                thread.playNext();
            }
        } else if ("open".equals(command)) {
            open();
        }
    }
    
    private void addCovers(ArrayList list, File[] files) {
        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            if (isCoverImageFile(f)) {
                list.add(new Cover(f));
            } else if (f.isDirectory()) {
                addCovers(list, f.listFiles());
            }
        }
    }
    
    Cover[] getCoverList() {
        ArrayList list = new ArrayList();
        addCovers(list, files);
        Cover[] array = new Cover[list.size()];
        list.toArray(array);
        return array;
    }
    
    File getSelectedFile() {
        int index = list.getSelectedIndex();
        if (index < 0 || index >= files.length) {
            return null;
        }
        return files[index];
    }

    private void createFrame() {
        frame = new Frame(TITLE);
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent we) {
                if (useSystemTray) {
                    frame.setVisible(false);
                } else {
                    exit();
                }
            }
        });
        if (icon != null) {
            frame.setIconImage(icon);
        }
        frame.setResizable(false);
        frame.setBackground(SystemColor.control);
        
        GridBagLayout layout = new GridBagLayout();
        frame.setLayout(layout);

        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.EAST;
        c.insets.left = 2;
        c.insets.right = 2;
        c.insets.top = 2;
        c.insets.bottom = 2;
      
        list = new List(7, false) {
            private static final long serialVersionUID = 1L;
            public Dimension getMinimumSize() {
                return new Dimension(350, 200);
            }
            public Dimension getPreferredSize() {
                return getMinimumSize();
            }
        };
        list.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                File f = getSelectedFile();
                if (f != null) {
                    if (f.isDirectory()) {
                        readFiles(f);
                    } else if (isMp3(f)) {
                        play(f);
                    }
                }
            }
        });
        
        boolean web = fontWebdings != null;

        Button back = new Button("Up");
        back.setFocusable(false);
        back.setActionCommand("back");
        back.addActionListener(this);
        back.setFont(font);
        c.anchor = GridBagConstraints.EAST;
        c.gridwidth = GridBagConstraints.EAST;
        frame.add(back, c);

        Button play = new Button("> Play >");
        play.setFocusable(false);
        play.setActionCommand("play");
        play.addActionListener(this);
        play.setFont(font);
        c.anchor = GridBagConstraints.EAST;
        c.gridwidth = GridBagConstraints.EAST;
        frame.add(play, c);
        
        Button next = new Button(web ? "\u003a" : ">>");
        next.setFocusable(false);
        next.setActionCommand("next");
        next.addActionListener(this);
        next.setFont(web ? fontWebdings : font);
        c.anchor = GridBagConstraints.EAST;
        c.gridwidth = GridBagConstraints.EAST;
        frame.add(next, c);

        Button pause = new Button("Pause");
        pause.setFocusable(false);
        pause.setActionCommand("pause");
        pause.addActionListener(this);
        pause.setFont(font);
        c.anchor = GridBagConstraints.EAST;
        c.gridwidth = GridBagConstraints.EAST;
        frame.add(pause, c);

        Button covers = new Button("Covers");
        covers.setFocusable(false);
        covers.setActionCommand("covers");
        covers.addActionListener(this);
        covers.setFont(font);
        c.anchor = GridBagConstraints.EAST;
        c.gridwidth = GridBagConstraints.EAST;
        frame.add(covers, c);

        Button stop = new Button("Stop");
        stop.setFocusable(false);
        stop.setActionCommand("stop");
        stop.addActionListener(this);
        stop.setFont(font);
        c.anchor = GridBagConstraints.EAST;
        c.gridwidth = GridBagConstraints.REMAINDER;
        frame.add(stop, c);

        list.setFont(font);
        c.anchor = GridBagConstraints.CENTER;
        c.gridwidth = GridBagConstraints.REMAINDER;
        frame.add(list, c);

        c.anchor = GridBagConstraints.WEST;
        c.gridwidth = GridBagConstraints.REMAINDER;
        playingLabel = new Label() {
            private static final long serialVersionUID = 1L;
            public Dimension getMinimumSize() {
                Dimension d = super.getMinimumSize();
                d.width = 350;
                return d;
            }
            public Dimension getPreferredSize() {
                return getMinimumSize();
            }
        };
        playingLabel.setAlignment(Label.LEFT);
        playingLabel.setFont(font);
        frame.add(playingLabel, c);

        int width = 400, height = 320;
        frame.setSize(width, height);
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setLocation((screenSize.width - width) / 2, (screenSize.height - height) / 2);
        
        readFiles(dir);
        
    }
    
    void open() {
        frame.setVisible(true);
    }

    void readFiles(File dir) {
        File[] f;
        boolean roots = dir == null;
        if (roots) {
            f = File.listRoots();
        } else {
            f = dir.listFiles();
        }
        if (f.length == 0) {
            return;
        }
        // must at least contain one directory or one mp3 file
        ArrayList fileList = new ArrayList();
        for (int i = 0; i < f.length; i++) {
            File f2 = f[i];
            if (roots || isMp3(f2) || f2.isDirectory()) {
                fileList.add(f2);
            }
        }
        if (fileList.size() == 0) {
            return;
        }
        this.files = new File[fileList.size()];
        fileList.toArray(files);
        this.dir = dir;
        if (roots) {
            prefs.remove(PREF_DIR);
        } else {
            prefs.put(PREF_DIR, dir.getAbsolutePath());
        }
        Color fg = list.getForeground();
        list.setForeground(list.getBackground());
        list.setFocusable(false);
        list.removeAll();
        for (int i = 0; i < files.length; i++) {
            File f2 = files[i];
            if (roots || isMp3(f2) || f2.isDirectory()) {
                String name = f2.getName().trim();
                if (name.length() == 0) {
                    name = f2.getAbsolutePath();
                }
                list.add(getTitle(name));
            }
        }
        list.setForeground(fg);
        list.setFocusable(true);
        list.requestFocus();
    }
    
    private void readDirectory() {
        String s = prefs.get(PREF_DIR, null);
        if (s != null) {
            File f = new File(s);
            if (f.exists()) {
                dir = f;
            }
        }
    }

    void play(File f) {
        if (isMp3(f)) {
            if (thread != null) {
                thread.stopPlaying();
                thread = null;
            }
            thread = PlayerThread.startPlaying(this, f, null);
        } else if (f.isDirectory()) {
            ArrayList files = new ArrayList();
            addAll(files, f);
            if (files.size() > 0) {
                for (int i = 0; i < files.size(); i++) {
                    Object temp = files.get(i);
                    int x = (int) (Math.random() * files.size());
                    files.set(i, files.get(x));
                    files.set(x, temp);
                }
                if (thread != null) {
                    thread.stopPlaying();
                    thread = null;
                }
                thread = PlayerThread.startPlaying(this, null, files);
            }
        }
    }

    private void addAll(ArrayList arrayList, File file) {
        if (file.isDirectory()) {
            File[] list = file.listFiles();
            for (int i = 0; i < list.length; i++) {
                addAll(arrayList, list[i]);
            }
        } else if (isMp3(file)) {
            arrayList.add(file);
        }
    }

    boolean isMp3(File f) {
        return f.getName().toLowerCase().endsWith(MP3_SUFFIX);
    }

    private boolean isCoverImageFile(File f) {
        int todoSupportPngBmpJpegGif;
        return f.getName().toLowerCase().endsWith(".jpg");
    }
    
    public void mouseClicked(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            open();
        }
    }

    public void mouseEntered(MouseEvent e) {
        // do nothing
    }

    public void mouseExited(MouseEvent e) {
        // do nothing
    }

    public void mousePressed(MouseEvent e) {
        // do nothing
    }

    public void mouseReleased(MouseEvent e) {
        // do nothing
    }
    
    private static byte[] readBytesAndClose(InputStream in, int length) throws IOException {
        try {
            if (length <= 0) {
                length = Integer.MAX_VALUE;
            }
            int block = Math.min(4 * 1024, length);
            ByteArrayOutputStream out = new ByteArrayOutputStream(block);
            byte[] buff = new byte[block];
            while (length > 0) {
                int len = Math.min(block, length);
                len = in.read(buff, 0, len);
                if (len < 0) {
                    break;
                }
                out.write(buff, 0, len);
                length -= len;
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }
    
    private String getTitle(String name) {
        if (name.toLowerCase().endsWith(MP3_SUFFIX)) {
            name = name.substring(0, name.length() - MP3_SUFFIX.length());
        }
        return name;
    }

}
