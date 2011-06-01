/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven;

import haven.resources.Resource;
import haven.resources.layers.Pagina;
import haven.scriptengine.InventoryExp;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Item extends Widget implements DTarget {
    static Coord shoff = new Coord(1, 3);
    static Map<Integer, Tex> qmap;
    private static final Resource missing = Resource.load("gfx/invobjs/missing");
    private boolean dm = false;
    private int quality; // quality
    private boolean isHighQuality; // Hide big qualities values
    private Coord doff;
    protected String tooltip;
    private int num = -1;
    private Indir<Resource> res;
    private Tex sh;
    private Color olcol = null;
    private Tex mask = null;
    protected int completedPercents = 0;

    static {
        Widget.addtype("item", new WidgetFactory() {
            public Widget create(Coord c, Widget parent, Object[] args) {
                int res = (Integer) args[0]; // Resource id
                int q = (Integer) args[1]; // Quality
                int num = -1; // Quantity
                String tooltip = null;
                int ca = 3; // Arguments count
                Coord drag = null;
                if ((Integer) args[2] != 0)
                    drag = (Coord) args[ca++];
                if (args.length > ca)
                    tooltip = (String) args[ca++];
                if ((tooltip != null) && tooltip.length() == 0)
                    tooltip = null;
                if (args.length > ca)
                    //noinspection UnusedAssignment
                    num = (Integer) args[ca++];
                Item item;
                if (parent instanceof InventoryExp) // Item in inventory
                    item = ((InventoryExp) parent).new InvItem(c, res, q, parent, drag, num);
                else if (parent instanceof RootWidget) // Item at cursor
                    item = new Item(c, res, q, parent, drag, num);
                else {
                    System.err.println("Creation item not ( in inventory | at cursor)" +
                            "\n\tparenttype=" + parent.getClass().getSimpleName() +
                            "\n\tcoordinates=" + c.toString() +
                            "\n\targs=" + Arrays.toString(args));
                    item = new Item(c, res, q, parent, drag, num);
                }
                item.tooltip = tooltip;
                return (item);
            }
        });
        missing.loadwait();
        qmap = new HashMap<Integer, Tex>();
    }

    private void fixsize() {
        if (res.get() != null) {
            Tex tex = res.get().layer(Resource.imgc).tex();
            sz = tex.sz().add(shoff);
        } else {
            sz = new Coord(30, 30);
        }
    }

    public void draw(GOut g) {
        final Resource ttres;
        if (res.get() == null) {
            sh = null;
            sz = new Coord(30, 30);
            g.image(missing.layer(Resource.imgc).tex(), Coord.z, sz);
            ttres = missing;
        } else {
            Tex tex = res.get().layer(Resource.imgc).tex();
            fixsize();
            if (dm) { // Semitransparent while moving at cursor
                g.chcolor(255, 255, 255, 128);
                g.image(tex, Coord.z);
                g.chcolor();
            } else {
                g.image(tex, Coord.z);
            }
            if (num >= 0) {
                g.chcolor(Color.WHITE);
                g.atext(Integer.toString(num), tex.sz(), 1, 1);
            }
            if (completedPercents > 0) {
                double a = ((double) completedPercents) / 100.0;
                int r = (int) ((1 - a) * 255);
                int gr = (int) (a * 255);
                int b = 0;
                g.chcolor(r, gr, b, 255);
                //g.fellipse(sz.div(2), new Coord(15, 15), 90, (int)(90 + (360 * a)));
                g.frect(new Coord(sz.getX() - 5, (int) ((1 - a) * sz.getY())), new Coord(5, (int) (a * sz.getY())));
                g.chcolor();
            }
            if (Config.showq && (quality > 0)) {
                tex = getqtex(quality);
                Coord c = sz.sub(1, 1);
                Coord s = tex.sz();
                g.chcolor(8, 8, 8, 128);
                g.frect(c.sub(s), s);
                g.chcolor();
                g.aimage(tex, c, 1, 1);
            }
            ttres = res.get();
        }
        if (olcol != null) {
            Tex bg = ttres.layer(Resource.imgc).tex();
            if ((mask == null) && (bg instanceof TexI)) {
                mask = ((TexI) bg).mkmask();
            }
            if (mask != null) {
                g.chcolor(olcol);
                g.image(mask, Coord.z);
                g.chcolor();
            }
        }
    }

    static Tex getqtex(int q) {
        synchronized (qmap) {
            if (qmap.containsKey(q)) {
                return qmap.get(q);
            } else {
                Tex tex = Text.render(Integer.toString(q)).tex();
                qmap.put(q, tex);
                return tex;
            }
        }
    }

    static Tex makesh(Resource res) {
        BufferedImage img = res.layer(Resource.imgc).img;
        Coord sz = Utils.imgsz(img);
        BufferedImage sh = new BufferedImage(sz.getX(), sz.getY(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < sz.getY(); y++) {
            for (int x = 0; x < sz.getX(); x++) {
                long c = img.getRGB(x, y) & 0x00000000ffffffffL;
                int a = (int) ((c & 0xff000000) >> 24);
                sh.setRGB(x, y, (a / 2) << 24);
            }
        }
        return (new TexI(sh));
    }

    String shorttip() {
        if (this.tooltip != null)
            return (this.tooltip);
        Resource res = this.res.get();
        if ((res != null) && (res.layer(Resource.tooltip) != null)) {
            String tt = res.layer(Resource.tooltip).t;
            if (tt != null) {
                if (quality > 0) {
                    tt = tt + ", quality " + quality;
                    if (isHighQuality)
                        tt = tt + '+';
                }
//		if(meter > 0) {
//		    tt = tt + " (" + meter + "%)";
//		}
                return (tt);
            }
        }
        return (null);
    }

    private long hoverstart;
    private Text shorttip = null;
    private Text longtip = null;

    public Object tooltip(Coord c, boolean again) {
        long now = System.currentTimeMillis();
        if (!again)
            hoverstart = now;
        if ((now - hoverstart) < 500) {
            if (shorttip == null) {
                String tt = shorttip();
                if (tt != null) {
                    if (completedPercents > 0) {
                        tt = tt + " (" + completedPercents + "%)";
                    }
                    shorttip = Text.render(tt);
                }
            }
            return (shorttip);
        } else {
            Resource res = this.res.get();
            if ((longtip == null) && (res != null)) {
                Pagina pg = res.layer(Resource.pagina);
                String tip = shorttip();
                if (tip == null)
                    return (null);
                String tt = RichText.Parser.quote(tip);
                if (completedPercents > 0) {
                    tt = tt + " (" + completedPercents + "%)";
                }
                if (pg != null)
                    tt += "\n\n" + pg.text;
                longtip = RichText.render(tt, 200);
            }
            return (longtip);
        }
    }

    private void resetToolTip() {
        shorttip = null;
        longtip = null;
    }

    private void decodeQuality(int q) {
        if (q < 0) {
            this.quality = q;
            isHighQuality = false;
        } else {
            this.quality = (q & 0xffffff);
            isHighQuality = (q & 0x01000000) != 0;  // Some optimization
        }
    }

    public Item(Coord c, Indir<Resource> res, int quality, Widget parent, Coord drag, int num) {
        super(c, Coord.z, parent);
        this.res = res;
        decodeQuality(quality);
        fixsize();
        this.num = num;
        if (drag == null) {
            dm = false;
        } else {
            dm = true;
            doff = drag;
            ui.grabmouse(this);
            this.c = ui.mc.sub(doff);
        }
    }

    public Item(Coord c, int res, int quality, Widget parent, Coord drag, int num) {
        this(c, parent.ui.sess.getres(res), quality, parent, drag, num);
    }

    private Item(Coord c, Indir<Resource> res, int quality, Widget parent, Coord drag) {
        this(c, res, quality, parent, drag, -1);
    }

    public Item(Coord c, int res, int quality, Widget parent, Coord drag) {
        this(c, parent.ui.sess.getres(res), quality, parent, drag);
    }

    boolean dropon(Widget w, Coord c) {
        for (Widget wdg = w.lchild; wdg != null; wdg = wdg.prev) {
            if (wdg == this)
                continue;
            Coord cc = w.xlate(wdg.c, true);
            if (c.isect(cc, wdg.sz)) {
                if (dropon(wdg, c.sub(cc)))
                    return (true);
            }
        }
        if (w instanceof DTarget) {
            if (((DTarget) w).drop(c, c.sub(doff)))
                return (true);
        }
        return (false);
    }

    boolean interact(Widget w, Coord c) {
        for (Widget wdg = w.lchild; wdg != null; wdg = wdg.prev) {
            if (wdg == this)
                continue;
            Coord cc = w.xlate(wdg.c, true);
            if (c.isect(cc, wdg.sz)) {
                if (interact(wdg, c.sub(cc)))
                    return (true);
            }
        }
        if (w instanceof DTarget) {
            if (((DTarget) w).iteminteract(c, c.sub(doff)))
                return (true);
        }
        return (false);
    }

    public void chres(Indir<Resource> res, int q) {
        this.res = res;
        sh = null;
        decodeQuality(q);
    }

    public void uimsg(String name, Object... args) {
        if (name.equals("num")) { // Change quantity
            num = (Integer) args[0];
        } else if (name.equals("chres")) { // Change resource (by id) and quality
            chres(ui.sess.getres((Integer) args[0]), (Integer) args[1]);
            resetToolTip();
        } else if (name.equals("color")) { // Change color (?)
            olcol = (Color) args[0];
        } else if (name.equals("tt")) { // Change tooltip
            if ((args.length > 0) && (((String) args[0]).length() > 0))
                tooltip = (String) args[0];
            else
                tooltip = null;
            resetToolTip();
        } else if (name.equals("meter")) { // may be completion indicator on dying fur, etc.
            completedPercents = (Integer) args[0];
        }
    }

    public boolean mousedown(Coord c, int button) {
        if (!dm) {
            if (button == 1) {
                if (ui.modshift)
                    wdgmsg("transfer", c);
                else if (ui.modctrl)
                    wdgmsg("drop", c);
                else
                    wdgmsg("take", c);
                return (true);
            } else if (button == 3) {
                wdgmsg("iact", c);
                return (true);
            }
        } else {
            if (button == 1) {
                dropon(parent, c.add(this.c));
            } else if (button == 3) {
                interact(parent, c.add(this.c));
            }
            return (true);
        }
        return (false);
    }

    public void mousemove(Coord c) {
        if (dm)
            this.c = this.c.add(c.sub(doff));
    }

    public boolean drop(Coord cc, Coord ul) {
        return (false);
    }

    public boolean iteminteract(Coord cc, Coord ul) {
        wdgmsg("itemact", ui.modflags());
        return (true);
    }
}
