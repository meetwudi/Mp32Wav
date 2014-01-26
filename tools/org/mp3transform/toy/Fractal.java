/*
 * Copyright 2004-2008 H2 Group. Licensed under the H2 License, Version 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.mp3transform.toy;

import java.applet.Applet;
import java.awt.Color;
import java.awt.Event;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.MemoryImageSource;

/**
 * This is a fractal applet with zoom.
 */
public class Fractal extends Applet implements Runnable {

    private static final long serialVersionUID = 1L;
    private static final int SURE = 0x1000000;
    private static final int COLOR = (0xff << 24);
    private static final int DEPTH_PLUS = 128;

    private int[] color = new int[256];
    private volatile boolean stop;
    private boolean busy;
    private MemoryImageSource memImage;
    private Image image;
    private int calcPos, select, startX, startY, moving;
    private int depthMin, depth;
    private int[] dataColor;
    private int[] data;
    private int max, factor;
    private double xd, yd, size, gap;

    public synchronized void init() {
        int m = Math.min(getHeight(), getWidth());
        max = 128;
        while (max + max < m) {
            max += max;
        }
        factor = max + 1;
        dataColor = new int[factor * factor];
        data = new int[factor * factor];
        memImage = new MemoryImageSource(factor, factor, dataColor, 0, factor);
        image = createImage(memImage);
        xd = yd = 0.;
        size = 4.;
        int redM = 8, greenM = 16, blueM = 4, redA = 0, greenA = 0, blueA = 192;
        for (int i = 0; i < 256; i++) {
            color[i] = ((i * redM + redA) & 0xff) * 0x10000 | ((i * greenM + greenA) & 0xff) * 0x100 | ((i * blueM + blueA) & 0xff) | COLOR;
        }
        calcAll();
        busy = false;
    }
    
    private int calcPoint(double rx, double ry, double dx, double dy, int max, int min) {
        int t = 0;
        double xx, yy;
        while (t < min) {
            xx = (rx - ry) * (rx + ry) + dx;
            ry = (ry = rx * ry) + ry + dy;
            rx = xx;
            t++;
        }
        do {
            if ((xx = rx * rx) + (yy = ry * ry) > 4) {
                break;
            }
            ry = (ry = rx * ry) + ry + dy;
            rx = xx - yy + dx;
        } while (t++ < max);
        return t;
    }

    private int calcArrayPoint(int x, int y) {
        int t = data[x + factor * y] & (SURE - 1);
        if (t == 0) {
            double dx = xd + (x - max / 2) * gap;
            double dy = yd + (y - max / 2) * gap;
            t = calcPoint(dx, dy, dx, dy, depth, depthMin);
            data[x + factor * y] = ++t | SURE;
        }
        return t;
    }

    private void calcDepth() {
        depth = 4096;
        int min = depth;
        depthMin = 0;
        for (int x = 0; x <= max; x += max / 8) {
            for (int y = 0; y <= max; y += max / 8) {
                int c = calcArrayPoint(x, y);
                if (c < min) {
                    min = c;
                }
                data[x + factor * y] = 0;
            }
        }
        depth = min + DEPTH_PLUS;
        depthMin = min - 1;
    }

    private synchronized void calcAll() {
        busy = true;
        for (int i = factor * factor - 1; i >= 0; i--) {
            data[i] = 0;
        }
        gap = size / factor;
        calcDepth();
        calcSmart();
        updateImage();
        paintFast();
        busy = false;
        calcPos = 0;
    }

    private synchronized void calcZoomIn() {
        busy = true;
        xd += size / factor * (startX - max / 4);
        yd += size / factor * (startY - max / 4);
        size /= 2.;
        // copy all sure points with scaling
        for (int i = factor * factor - 1; i >= 0; i--) {
            dataColor[i] = 0;
        }
        for (int x = 0; x <= max / 2; x++) {
            for (int y = 0; y <= max / 2; y++) {
                int m = data[x + startX + factor * (y + startY)];
                dataColor[x + x + factor * (y + y)] = ((m & SURE) == 0) ? 0 : m;
            }
        }
        System.arraycopy(dataColor, 0, data, 0, factor * factor - 1);
        // remove unsure
        gap = size / factor;
        int oldDepth = depth;
        calcDepth();
        if (depth > oldDepth) {
            int unsure = oldDepth | SURE;
            for (int y = max; y >= 0; y -= 2) {
                for (int x = max, i = x + factor * y; x >= 0; x -= 2, i -= 2) {
                    if (dataColor[i] >= unsure) {
                        dataColor[i] = data[i] = 0;
                    }
                }
            }
        }
        calcSmart();
        for (int x = 0; x <= max; x += 2) {
            for (int y = 0; y <= max; y += 2) {
                int i = x + factor * y;
                int c = dataColor[i];
                if ((c & SURE) != 0) {
                    data[i] = c;
                }
            }
        }
        updateImage();
        paintZoom();
        setCursor(-1, 0, 0);
        busy = false;
        calcPos = 0;
    }

    private void calcBackground() {
        if (busy || select != 1 || calcPos == -1) {
            return;
        } else if (moving > 0) {
            moving--;
        } else if (calcPos < factor * factor) {
            for (; calcPos < factor * factor; calcPos++) {
                int b = data[calcPos];
                if ((b & SURE) == 0) {
                    data[calcPos] = 0;
                    calcArrayPoint(calcPos % factor, calcPos / factor);
                }
                if (moving > 0) {
                    break;
                }
            }
        } else {
            calcPos = -1;
            busy = true;
            updateImage();
            paintFast();
            busy = false;
        }
    }

    private void calcSmart() {
        for (int b = 16; b > 1; b >>= 1) {
            for (int x = 0; x <= max; x += b) {
                for (int y = 0; y <= max; y += b) {
                    calcArrayPoint(x, y);
                }
            }
            for (int x = 0; x < max; x += b) {
                for (int y = 0; y < max; y += b) {
                    if (data[x + (b / 2) + factor * (y + (b / 2))] != 0) {
                        continue;
                    }
                    int c1 = (SURE - 1) & data[x + factor * y];
                    int c2 = (SURE - 1) & data[x + factor * (y + b)];
                    int c3 = (SURE - 1) & data[x + b + factor * y];
                    int c4 = (SURE - 1) & data[x + b + factor * (y + b)];
                    if (c1 == c2 && c3 == c4 && c1 == c3) {
                        for (int ix = x + 1; ix < x + b; ix++) {
                            data[ix + factor * y] = data[ix + factor * (y + b)] = c1;
                        }
                        for (int ix = x; ix <= x + b; ix++) {
                            for (int iy = y + 1; iy < y + b; iy++) {
                                data[ix + factor * iy] = c1;
                            }
                        }
                        continue;
                    }
                    if (b > 2) {
                        continue;
                    }
                    int cm;
                    if (c1 - c4 != 1 && c1 - c4 != -1) {
                        cm = data[x + 1 + factor * (y + 1)] = (c1 + c4) >> 1;
                    } else if (c2 - c3 != 1 && c2 - c3 != -1) {
                        cm = data[x + 1 + factor * (y + 1)] = (c2 + c3) >> 1;
                    } else {
                        cm = calcArrayPoint(x + 1, y + 1);
                    }
                    if (y > 0) {
                        int cu = (SURE - 1) & data[x + 1 + factor * (y - 1)];
                        if (c1 - c3 != 1 && c1 - c3 != -1) {
                            data[x + 1 + factor * y] = (c1 + c3) >> 1;
                        } else if (cu - cm != 1 && cu - cm != -1) {
                            data[x + 1 + factor * y] = (cu + cm) >> 1;
                        } else {
                            calcArrayPoint(x + 1, y);
                        }
                    }
                    if (x > 0) {
                        int cl = (SURE - 1) & data[x - 1 + factor * (y + 1)];
                        if (c1 - c2 != 1 && c1 - c2 != -1) {
                            data[x + factor * (y + 1)] = (c1 + c2) >> 1;
                        } else if (cl - cm != 1 && cl - cm != -1) {
                            data[x + factor * (y + 1)] = (cl + cm) >> 1;
                        } else {
                            calcArrayPoint(x, y + 1);
                        }
                    }
                }
            }
        }
    }

    private void updateImage() {
        int c;
        for (int i = factor * factor - 1; i >= 0; i--) {
            c = data[i] & (SURE - 1);
            if (c >= depth) {
                c = 0xff << 24;
            } else {
                c = color[c & 255];
            }
            dataColor[i] = c;
        }
        for (int i = 0; i < factor; i++) {
            dataColor[i] = dataColor[i + factor * max - max] = dataColor[i * factor] = dataColor[i * factor + max] = 0xff << 24;
        }
        image.flush();
    }

    public boolean mouseExit(Event e, int x, int y) {
        if (!busy) {
            if (select != 0) {
                setCursor(0, x, y);
            }
        }
        return true;
    }

    public synchronized boolean mouseMove(Event e, int x, int y) {
        if (!busy && select != 2) {
            moving = 10;
            setCursor(1, x, y);
        }
        return true;
    }

    public synchronized boolean mouseDown(Event e, int x, int y) {
        if (!busy) {
            setCursor(1, x, y);
            if (select == 1) {
                select = 2;
            }
        }
        return true;
    }

    private void setCursor(int e, int x, int y) {
        if (e == -1) {
            select = 1;
            startY = -1;
            return;
        }
        if (select == 2) {
            return;
        }
        x -= x % 4;
        y -= y % 4;
        if (x <= max) {
            x -= max / 4;
            if (x < 0) {
                x = 0;
            }
            if (x > max / 2) {
                x = max / 2;
            }
        }
        y -= max / 4;
        if (y < 0) {
            y = 0;
        }
        if (y > max / 2) {
            y = max / 2;
        }
        if (e == 1 && x == startX && y == startY) {
            return;
        }
        Graphics g = getGraphics();
        g.setXORMode(Color.white);
        if (select == 1 && startY >= 0) {
            paintFast();
        }
        if (e == 1) {
            if (x > max) {
                startY = -1;
                select = 0;
                return;
            }
            startX = x;
            startY = y;
            g.drawRect(startX, startY, max / 2, max / 2);
            select = 1;
        } else {
            select = 0;
        }
    }

    public synchronized void update(Graphics g) {
        paint(g);
    }

    public synchronized void paint(Graphics g) {
        if (busy) {
            return;
        }
        g.drawImage(image, 0, 0, this);
        super.paint(g);
        if (select == 1) {
            setCursor(-1, 0, 0);
        }
    }

    private synchronized void paintFast() {
        Graphics g = getGraphics();
        g.drawImage(image, 0, 0, this);
        if (select == 1) {
            setCursor(-1, 0, 0);
        }
    }

    void paintZoom() {
        image.flush();
        Graphics g = getGraphics();
        g.setXORMode(Color.white);
        int p = max / 128;
        if (p == 0) {
            p = 1;
        }
        for (int k = max / 2; k <= max; k += p) {
            int x = (max - k) * startX / (max / 2);
            int y = (max - k) * startY / (max / 2);
            g.drawRect(x, y, k, k);
            g.drawRect(x, y, k, k);
        }
        g.setPaintMode();
        g.drawImage(image, 0, 0, this);
    }

    public void start() {
        new Thread(this).start();
    }

    public synchronized void stop() {
        stop = true;
    }

    public synchronized void run() {
        while (!stop) {
            if (select == 2) {
                select = 1;
                calcZoomIn();
            } else {
                calcBackground();
            }
            try {
                wait(10);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

}
