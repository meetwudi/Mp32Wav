package org.mp3transform.awt;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;

import javax.swing.ImageIcon;

public class CoverCanvas extends Window implements ImageObserver, MouseMotionListener, MouseListener, Runnable {

    private static final boolean BIG = false;

    private static final long serialVersionUID = 1L;
    private static final int SCALE = 2;
    private static final int DELAY = 100;
    private Image coverImage;
    private BufferedImage coverBuffer;
    private Graphics2D coverGraphics; 

    private int[] coverArray;
    private BufferedImage outputBuffer;
    private int[] outputArray;
    private int[] emptyArray;
    private int width, height, shadow;
    private int screenWidth, screenHeight;
    private boolean init;
    private Rectangle exit;
    private Point startClick;
    private Cover[] list;
    private double listIndex;
    private Rectangle scrollBar;
    private boolean stop;
    private long lastAction;
    private int imageIndex = -1;
    private PlayerNoCover player;
    
    public CoverCanvas(PlayerNoCover player, Frame owner, Cover[] list) {
        super(owner);
        this.player = player;
        this.list = list;
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        if (BIG) {
            device.setFullScreenWindow(this);
            screenWidth = device.getFullScreenWindow().getWidth();
            screenHeight = device.getFullScreenWindow().getHeight();
        } else {
            screenWidth = 600;
            screenHeight = 400;
        }
        int max = Math.min(screenWidth, screenHeight) / 2;
        width = max * SCALE;
        height = max * SCALE;
        shadow = height / 2;
        exit = new Rectangle(screenWidth - 40, screenHeight - 40, 20, 20);
        scrollBar = new Rectangle(50, screenHeight - 40, screenWidth - 100, 20);
        coverBuffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        coverGraphics = coverBuffer.createGraphics();
        coverArray = new int[width * height];
        outputBuffer = new BufferedImage(width, height + shadow, BufferedImage.TYPE_INT_RGB);
        outputArray = new int[width * (height + shadow)];
        emptyArray = new int[width * (height + shadow)];
        coverGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        initImage();
        addMouseListener(this);
        addMouseMotionListener(this);
        if (!BIG) {
            Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
            setLocation((d.width - screenWidth) / 2, (d.height - screenHeight) / 2);
            setSize(screenWidth, screenHeight);
            setVisible(true);
            toFront();
        }
        new Thread(this).start();
    }
    
    void initImage() {
        int idx = (int) Math.round(listIndex);
        if (imageIndex == idx) {
            return;
        }
        Cover cover = list[idx];
        imageIndex = idx;
        ImageIcon icon = new ImageIcon(cover.file.getAbsolutePath());
        coverImage = icon.getImage();
        int w = coverImage.getWidth(this);
        int h = coverImage.getHeight(this);
        double scale = Math.min((double) width / w, (double) height / h);
        System.out.println("w:" + w + " h:" + h + " s:" + scale + " w: " + width + " h:" + height);
        w *= scale;
        h *= scale;
        // System.out.println("  w:" + w + " h:" + h + " s:" + scale);
        coverGraphics.setTransform(new AffineTransform());
        coverGraphics.scale(scale, scale);
        coverGraphics.drawImage(coverImage, (width - w) / 2, height - h, this);
        coverBuffer.getRGB(0, 0, width, height, coverArray, 0, width);
    }

    public void update(Graphics g) {
        paint(g, false);
    }
    
    public void paint(Graphics g) {
        paint(g, true);
    }
    
    private void paint(Graphics g, boolean clean) {
        initImage();
        calculateImage(width, height);
        outputBuffer.setRGB(0, 0, width, height + shadow, outputArray, 0, width);
        Graphics2D g2 = (Graphics2D) g;
        if (!init || clean) {
            g2.setColor(Color.black);
            g2.fillRect(0, 0, screenWidth, screenHeight);
            g2.setColor(Color.darkGray);
            g2.fillRect(exit.x, exit.y, exit.width, exit.height);
            g2.drawRect(scrollBar.x, scrollBar.y, scrollBar.width, scrollBar.height);
            init = true;
        }
        g2.setColor(Color.black);
        g2.fillRect(scrollBar.x + 1, scrollBar.y + 1, scrollBar.width - 1, scrollBar.height - 1);
        g2.setColor(Color.darkGray);
        double w = scrollBar.width / list.length;
        g2.fillRect(scrollBar.x + (int) (listIndex * w), scrollBar.y, (int) w, scrollBar.height);
        
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        
        g2.drawImage(outputBuffer, 
                (screenWidth - width / SCALE) / 2, 
                (screenHeight - (height + shadow) / SCALE) / 2, 
                width / SCALE, (height + shadow) / SCALE, null);
    }
    
    private void calculateImage(int w, int h) {
        System.arraycopy(emptyArray, 0, outputArray, 0, emptyArray.length);
        // outputBuffer.setRGB(0, 0, width, height, outputArray, 0, width);
        
        double width = w;
        double d = w;
        double pos = listIndex - Math.round(listIndex) + 0.5;
        boolean left = pos >= 0.5;
        double f = pos * 2;
        if (f > 1.0) {
            f = 2.0 - f;
        }
        double alpha = 90. - (f * 90.);
        if (alpha >= 90) {
            alpha = 90;
        } else if (alpha < 0) {
            alpha = 0;
        }
        double xo = width * Math.cos(alpha * Math.PI / 180.);
        double zo = width * Math.sin(alpha * Math.PI / 180.);
        double zf = d / (d + zo);
        for (int x = 0; x < w; x++) {
            double index = x * d / (xo * d - x * zo);
            int xc = (int) (index * w);
            if (xc >= w || xc < 0) {
                break;
            }
            double factor = zf;
            factor = (1 - x/xo) + x/xo * factor;            
            double plus = h * ((1 - factor) / 2);
            int ys = 0;
            for (int y = 0; y < h; y++) {
                ys = (int) (y * factor + plus);
                // int color = coverBuffer.getRGB(xc, y);
                // outputBuffer.setRGB(x, ys, color);
                int source, target;
                if (left) {
                    source = y * w + (w - 1 - xc);
                    target = ys * w + (w - 1 - x);
                } else {
                    source = y * w + xc;
                    target = ys * w + x;
                }
                int color = coverArray[source];
                outputArray[target] = color;
            }
            int gap = 8;
            for (int y = ys, m = 1; y < h + shadow - gap; y++, m++) {
                int source, target;
                if (left) {
                    source = (ys - m) * w + (w - 1 - x);
                    target = (y + gap) * w + (w - 1 - x);
                } else {
                    source = (ys - m) * w + x;
                    target = (y + gap) * w + x;
                }
                int color = outputArray[source];
                int r = (color >> 16) & 0xff, g = (color >> 8) & 0xff, b = color & 0xff;
                double mul = 1 / (4. + m / (double) height);
                r = (int) (r * mul);
                g = (int) (g * mul);
                b = (int) (b * mul);
                color = (r << 16) | (g << 8) | b;
                outputArray[target] = color;
            }
        }
    }    

    public Dimension getMinimumSize() {
        return new Dimension(400, 600);
    }
    
    public Dimension getPreferredSize() {
        return getMinimumSize();
    }

    public boolean imageUpdate(Image img, int infoflags, int x, int y, int width, int height) {
        return false;
    }

    public void mouseDragged(MouseEvent e) {
        if (startClick != null) {
            int x = e.getX() - startClick.x;
            listIndex += x / 100.;
            listIndex = Math.max(0, listIndex);
            listIndex = Math.min(listIndex, list.length - 1);
            repaint();
        }
        startClick = e.getPoint();
        // System.out.println(" " + (x - startClick.x));
    }

    public void mouseMoved(MouseEvent e) {
        // nothing to do
    }

    public void mouseClicked(MouseEvent e) {
        startClick = e.getPoint();
        if (exit.contains(startClick)) {
            stop = true;
            dispose();
        }
    }

    public void mouseEntered(MouseEvent e) {
        // nothing to do
    }

    public void mouseExited(MouseEvent e) {
        // nothing to do
    }

    public void mousePressed(MouseEvent e) {
        startClick = e.getPoint();
    }

    public void mouseReleased(MouseEvent e) {
        lastAction = System.currentTimeMillis();
        startClick = null;
    }

    public void run() {
        while (!stop) {
            try {
                Thread.sleep(5);
                if (listIndex != (int) listIndex) {
                    long time = System.currentTimeMillis();
                    if (startClick != null || lastAction + DELAY > time) {
                        continue;
                    }
                    int roundTo = (int) Math.round(listIndex);
                    double diff = roundTo - listIndex;
                    int signum = diff == 0 ? 0 : diff < 0 ? -1 : 1;
                    if (Math.abs(diff) < 0.001) {
                        listIndex = roundTo;
                        int todoTest;
                        player.play(list[roundTo].file.getParentFile());
                    } else if (Math.abs(diff) < 0.1) {
                        listIndex += signum * 0.0005;
                    } else {
                        listIndex += signum * 0.005;
                    }
                    repaint();
                }
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

}
