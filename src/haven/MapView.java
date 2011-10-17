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

import haven.MCache.Grid;
import haven.MCache.Overlay;
import haven.resources.layers.Neg;
import haven.resources.layers.Tile;
import haven.scriptengine.ScriptsManager;
import haven.scriptengine.providers.Player;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static haven.MCache.cmaps;
import static haven.MCache.tilesz;

@SuppressWarnings({"UnusedDeclaration"})
public class MapView extends Widget implements DTarget, Console.Directory {
    static Color[] olc = new Color[31];
    static Map<String, Class<? extends Camera>> camtypes = new HashMap<String, Class<? extends Camera>>();
    public Coord mc, mousepos, pmousepos;
    Camera cam;
    Sprite.Part[] clickable = {};
    List<Sprite.Part> obscured = Collections.emptyList();
    private int[] visol = new int[31];
    private long olftimer = 0;
    private int olflash = 0;
    Grabber grab = null;
    ILM mask;
    final MCache map;
    final Glob glob;
    public Collection<Gob> plob = null;
    boolean plontile;
    int plrad = 0;
    int playergob = -1;
    public Profile prof = new Profile(300);
    private Profile.Frame curf;
    Coord plfpos = null;
    long lastmove = 0;
    Sprite.Part obscpart = null;
    Gob obscgob = null;
    static Text.Foundry polownertf = new Text.Foundry("serif", 20);
    public Text polownert = null;
    public String polowner = null;
    public Gob onmouse;
    long polchtm = 0;
    int si = 4;
    double _scale = 1;
    double scales[] = {0.5, 0.66, 0.8, 0.9, 1, 1.25, 1.5, 1.75};
    Map<String, Integer> radiuses;
    int beast_check_delay = 0;


    // arksu ------------------------------------------------------------------
    public volatile boolean show_selected_tile = false; // показывать тайл под мышью
    public volatile boolean player_moving = false; // arksu : движемся ли мы
    public final AtomicBoolean modeSelectObject = new AtomicBoolean(false); // нужно выбрать объект.
    public Coord last_my_coord; // последние координаты моего чара. нужно для слежения

    long time_to_start;
    boolean started = false;
    long AUTO_START_TIME = 15000;
    long last_tick = 0;
    private static final Color TRANSPARENT_GREEN = new Color(0, 255, 0, 32);
    private Coord mouse_tile;
    // arksu ------------------------------------------------------------------

    public double getScale() {
        return Config.zoom ? _scale : 1;
    }

    public void setScale(final double value) {
        _scale = value;
        //mask.dispose();
        //mask = new ILM(MainFrame.getScreenSize().div(_scale), glob.oc);
    }

    public static final Comparator<Sprite.Part> clickcmp = new Comparator<Sprite.Part>() {
        public int compare(final Sprite.Part a, final Sprite.Part b) {
            return (-Sprite.partidcmp.compare(a, b));
        }
    };

    static {
        Widget.addtype("mapview", new WidgetFactory() {
            public Widget create(final Coord c, final Widget parent, final Object[] args) {
                final Coord sz = CustomConfig.getWindowSize().clone(); //(Coord)args[0];
                final Coord mc = (Coord) args[1];
                int pgob = -1;
                if (args.length > 2) {
                    pgob = (Integer) args[2];
                } else {
                    System.err.println("Creating MapView without specified player object");
                }
                CustomConfig.playerId = pgob;
                return (new MapView(c, sz, parent, mc, pgob));
            }
        });
        olc[0] = new Color(255, 0, 128);
        olc[1] = new Color(0, 0, 255);
        olc[2] = new Color(255, 0, 0);
        olc[3] = new Color(128, 0, 255);
        olc[16] = new Color(0, 255, 0);
        olc[17] = new Color(255, 255, 0);
    }

    public interface Grabber {
        void mmousedown(Coord mc, int button);

        void mmouseup(Coord mc, int button);

        void mmousemove(Coord mc);
    }

    public static class Camera {
        public void setpos(final MapView mv, final Gob player, final Coord sz) {
        }

        public boolean click(final MapView mv, final Coord sc, final Coord mc, final int button) {
            return (false);
        }

        public void move(final MapView mv, final Coord sc, final Coord mc) {
        }

        public boolean release(final MapView mv, final Coord sc, final Coord mc, final int button) {
            return (false);
        }

        public void moved(final MapView mv) {
        }

        public static void borderize(final MapView mv, final Gob player, final Coord sz, final Coord border) {
            if (Config.noborders) {
                return;
            }
            Coord mc = mv.mc;
            final Coord oc = m2s(mc).inv();
            final int bt = -((sz.y / 2) - border.y);
            final int bb = (sz.y / 2) - border.y;
            final int bl = -((sz.x / 2) - border.x);
            final int br = (sz.x / 2) - border.x;
            final Coord sc = m2s(player.getc()).add(oc);
            if (sc.x < bl)
                mc = mc.add(s2m(new Coord(sc.x - bl, 0)));
            if (sc.x > br)
                mc = mc.add(s2m(new Coord(sc.x - br, 0)));
            if (sc.y < bt)
                mc = mc.add(s2m(new Coord(0, sc.y - bt)));
            if (sc.y > bb)
                mc = mc.add(s2m(new Coord(0, sc.y - bb)));
            mv.mc = mc;
        }

        public void reset() {
        }
    }

    private static abstract class DragCam extends Camera {
        Coord o, mo;
        boolean dragging = false;
        boolean needreset = false;

        public boolean click(final MapView mv, final Coord sc, final Coord mc, final int button) {
            if (button == 2) {
                mv.ui.grabmouse(mv);
                o = sc;
                mo = null;
                dragging = true;
                return (true);
            }
            return (false);
        }

        public void move(final MapView mv, final Coord sc, final Coord mc) {
            if (dragging) {
                final Coord off = sc.sub(o);
                if ((mo == null) && (off.dist(Coord.z) > 5))
                    mo = mv.mc;
                if (mo != null) {
                    mv.mc = mo.sub(s2m(off));
                    moved(mv);
                }
            }
        }

        public void reset() {
            needreset = true;
        }

        public boolean release(final MapView mv, final Coord sc, final Coord mc, final int button) {
            if ((button == 2) && dragging) {
                mv.ui.ungrabmouse();
                dragging = false;
                if (mo == null) {
                    mv.mc = mc;
                    moved(mv);
                }
                return (true);
            }
            return (false);
        }
    }

    static class OrigCam extends Camera {
        public final Coord border = new Coord(250, 150);

        public void setpos(final MapView mv, final Gob player, final Coord sz) {
            borderize(mv, player, sz, border);
        }

        public boolean click(final MapView mv, final Coord sc, final Coord mc, final int button) {
            if (button == 1)
                mv.mc = mc;
            return (false);
        }
    }

    static {
        camtypes.put("orig", OrigCam.class);
    }

    static class OrigCam2 extends DragCam {
        public final Coord border = new Coord(250, 125);
        private final double v;
        private Coord tgt = null;
        private long lmv;

        OrigCam2(final double v) {
            this.v = Math.log(v) / 0.02; /* 1 / 50 FPS = 0.02 s */
        }

        @SuppressWarnings({"UnusedDeclaration"})
        OrigCam2() {
            this(0.9);
        }

        OrigCam2(final String... args) {
            this((args.length < 1) ? 0.9 : Double.parseDouble(args[0]));
        }

        public void setpos(final MapView mv, final Gob player, final Coord sz) {

            if (needreset) {
                needreset = false;
                mv.mc = player.getc();
            }

            if (tgt != null) {
                if (mv.mc.dist(tgt) < 10) {
                    tgt = null;
                } else {
                    final long now = System.currentTimeMillis();
                    final double dt = (now - lmv) / 1000.0;
                    lmv = now;
                    mv.mc = tgt.add(mv.mc.sub(tgt).mul(Math.exp(v * dt)));
                }
            }
            borderize(mv, player, sz, border);
        }

        public boolean click(final MapView mv, final Coord sc, final Coord mc, final int button) {
            if ((button == 1) && (mv.ui.root.cursor == RootWidget.defcurs)) {
                tgt = mc;
                lmv = System.currentTimeMillis();
            }
            return (super.click(mv, sc, mc, button));
        }

        public void moved(final MapView mv) {
            tgt = null;
        }

        public void reset() {
            super.reset();
            tgt = null;
        }
    }

    static {
        camtypes.put("clicktgt", OrigCam2.class);
    }

    static class WrapCam extends Camera {
        public final Coord region = new Coord(200, 150);

        public void setpos(final MapView mv, final Gob player, final Coord sz) {
            final Coord sc = m2s(player.getc().sub(mv.mc));
            if (sc.x < -region.x)
                mv.mc = mv.mc.add(s2m(new Coord(-region.x * 2, 0)));
            if (sc.x > region.x)
                mv.mc = mv.mc.add(s2m(new Coord(region.x * 2, 0)));
            if (sc.y < -region.y)
                mv.mc = mv.mc.add(s2m(new Coord(0, -region.y * 2)));
            if (sc.y > region.y)
                mv.mc = mv.mc.add(s2m(new Coord(0, region.y * 2)));
        }
    }

    static {
        camtypes.put("kingsquest", WrapCam.class);
    }

    static class BorderCam extends DragCam {
        public final Coord border = new Coord(250, 150);

        public void setpos(final MapView mv, final Gob player, final Coord sz) {
            if (needreset) {
                needreset = false;
                mv.mc = player.getc();
            }
            borderize(mv, player, sz, border);
        }
    }

    static {
        camtypes.put("border", BorderCam.class);
    }

    static class PredictCam extends DragCam {
        private double xa = 0, ya = 0;
        private boolean reset = true;
        private final double speed = 0.15, rspeed = 0.15;
        private double sincemove = 0;
        private long last = System.currentTimeMillis();

        public void setpos(final MapView mv, final Gob player, final Coord sz) {
            final long now = System.currentTimeMillis();
            final double dt = ((double) (now - last)) / 1000.0;
            last = now;

            if (needreset) {
                needreset = false;
                mv.mc = player.getc();
            }

            Coord mc = mv.mc.add(s2m(sz.sub(mv.sz).div(2)));
            final Coord sc = m2s(player.getc()).sub(m2s(mc));
            if (reset) {
                xa = (double) sc.x / (double) sz.x;
                ya = (double) sc.y / (double) sz.y;
                if (xa < -0.25) xa = -0.25;
                if (xa > 0.25) xa = 0.25;
                if (ya < -0.15) ya = -0.15;
                if (ya > 0.25) ya = 0.25;
                reset = false;
            }
            final Coord vsz = sz.div(16);
            final Coord vc = new Coord((int) (sz.x * xa), (int) (sz.y * ya));
            boolean moved = false;
            if (sc.x < vc.x - vsz.x) {
                if (xa < 0.25)
                    xa += speed * dt;
                moved = true;
                mc = mc.add(s2m(new Coord(sc.x - (vc.x - vsz.x) - 4, 0)));
            }
            if (sc.x > vc.x + vsz.x) {
                if (xa > -0.25)
                    xa -= speed * dt;
                moved = true;
                mc = mc.add(s2m(new Coord(sc.x - (vc.x + vsz.x) + 4, 0)));
            }
            if (sc.y < vc.y - vsz.y) {
                if (ya < 0.25)
                    ya += speed * dt;
                moved = true;
                mc = mc.add(s2m(new Coord(0, sc.y - (vc.y - vsz.y) - 2)));
            }
            if (sc.y > vc.y + vsz.y) {
                if (ya > -0.15)
                    ya -= speed * dt;
                moved = true;
                mc = mc.add(s2m(new Coord(0, sc.y - (vc.y + vsz.y) + 2)));
            }
            if (!moved) {
                sincemove += dt;
                if (sincemove > 1) {
                    if (xa < -0.1)
                        xa += rspeed * dt;
                    if (xa > 0.1)
                        xa -= rspeed * dt;
                    if (ya < -0.1)
                        ya += rspeed * dt;
                    if (ya > 0.1)
                        ya -= rspeed * dt;
                }
            } else {
                sincemove = 0;
            }
            mv.mc = mc.add(s2m(mv.sz.sub(sz).div(2)));
        }

        public void moved(final MapView mv) {
            reset = true;
        }

        public void reset() {
            reset = true;
            xa = ya = 0;
            super.reset();
        }
    }

    static {
        camtypes.put("predict", PredictCam.class);
    }

    static class FixedCam extends DragCam {
        public final Coord border = new Coord(250, 150);
        private Coord off = Coord.z;
        private boolean setoff = false;

        public void setpos(final MapView mv, final Gob player, final Coord sz) {
            if (setoff) {
                borderize(mv, player, sz, border);
                off = mv.mc.sub(player.getc());
                setoff = false;
            }
            mv.mc = player.getc().add(off);
        }

        public void moved(final MapView mv) {
            setoff = true;
        }

        public void reset() {
            off = Coord.z;
        }
    }

    static {
        camtypes.put("fixed", FixedCam.class);
    }

    static class CakeCam extends Camera {
        private Coord border = new Coord(250, 150);
        private Coord size, center, diff;

        public void setpos(final MapView mv, final Gob player, final Coord sz) {
            if (size == null || !size.equals(sz)) {
                size = new Coord(sz);
                center = size.div(2);
                diff = center.sub(border);
            }
            if (player != null && mv.pmousepos != null)
                mv.mc = player.getc().sub(s2m(center.sub(mv.pmousepos).mul(diff).div(center)));
        }
    }

    static {
        camtypes.put("cake", CakeCam.class);
    }

    static class FixedCakeCam extends DragCam {
        public final Coord border = new Coord(250, 150);
        private Coord size, center, diff;
        private boolean setoff = false;
        private Coord off = Coord.z;
        private Coord tgt = null;
        private Coord cur = off;
        private double vel = 0.2;

        FixedCakeCam(final double vel) {
            this.vel = Math.min(1.0, Math.max(0.1, vel));
        }

        FixedCakeCam(final String... args) {
            this(args.length < 1 ? 0.2 : Double.parseDouble(args[0]));
        }

        public void setpos(final MapView mv, final Gob player, final Coord sz) {
            if (setoff) {
                borderize(mv, player, sz, border);
                off = mv.mc.sub(player.getc());
                setoff = false;
            }
            if (mv.pmousepos != null && (mv.pmousepos.x == 0 || mv.pmousepos.x == sz.x - 1 || mv.pmousepos.y == 0 || mv.pmousepos.y == sz.y - 1)) {
                if (size == null || !size.equals(sz)) {
                    size = new Coord(sz);
                    center = size.div(2);
                    diff = center.sub(border);
                }
                if (player != null && mv.pmousepos != null)
                    tgt = player.getc().sub(s2m(center.sub(mv.pmousepos).mul(diff).div(center))).sub(player.getc());
            } else {
                tgt = off;
            }
            cur = cur.add(tgt.sub(cur).mul(vel));
            //noinspection ConstantConditions
            mv.mc = player.getc().add(cur);
        }

        public void moved(final MapView mv) {
            setoff = true;
        }

        public void reset() {
            off = new Coord(0, 0);
        }
    }

    static {
        camtypes.put("fixedcake", FixedCakeCam.class);
    }

    @SuppressWarnings({"EmptyClass"})
    private static class Loading extends Exception {
    }

    private static Camera makecam(final Class<? extends Camera> ct, final String... args) throws ClassNotFoundException {
        try {
            try {
                final Constructor<? extends Camera> cons = ct.getConstructor(String[].class);
                return (cons.newInstance(new Object[]{args}));
            } catch (IllegalAccessException ignored) {
            } catch (NoSuchMethodException ignored) {
            }
            try {
                return (ct.newInstance());
            } catch (IllegalAccessException ignored) {
            }
        } catch (InstantiationException e) {
            throw (new Error(e));
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException)
                throw ((RuntimeException) e.getCause());
            throw (new RuntimeException(e));
        }
        throw (new ClassNotFoundException("No valid constructor found for camera " + ct.getName()));
    }

    private static Camera restorecam() {
        final Class<? extends Camera> ct = camtypes.get(Utils.getpref("defcam", "border"));
        if (ct == null)
            return (new BorderCam());
        String[] args = (String[]) Utils.deserialize(Utils.getprefb("camargs", null));
        if (args == null) args = new String[0];
        try {
            return (makecam(ct, args));
        } catch (ClassNotFoundException e) {
            return (new BorderCam());
        }
    }

    public MapView(final Coord c, final Coord sz, final Widget parent, final Coord mc, final int playergob) {
        super(c, sz, parent);
        time_to_start = AUTO_START_TIME;
        isui = false;
        this.mc = mc;
        this.playergob = playergob;
        this.cam = restorecam();
        setcanfocus(true);
        glob = ui.sess.glob;
        map = glob.map;
        mask = new ILM(CustomConfig.getWindowSize(), glob.oc);
        radiuses = new HashMap<String, Integer>();
        ui.mainview = this;
    }

    public void resetcam() {
        if (cam != null) {
            cam.reset();
        }
    }

    public static Coord m2s(final Coord c) {
        return (new Coord((c.x * 2) - (c.y * 2), c.x + c.y));
    }

    public static Coord s2m(final Coord c) {
        return (new Coord((c.x / 4) + (c.y / 2), (c.y / 2) - (c.x / 4)));
    }

    static Coord viewoffset(final Coord sz, final Coord vc) {
        return ((sz.div(2)).sub(m2s(vc)));
    }

    public void grab(final Grabber grab) {
        this.grab = grab;
    }

    public void release(final Grabber grab) {
        if (this.grab == grab)
            this.grab = null;
    }

    private Gob gobatpos(final Coord c) {
        for (final Sprite.Part d : obscured) {
            final Gob gob = (Gob) d.owner;
            if (gob == null)
                continue;
            if (d.checkhit(c.sub(gob.sc)))
                return (gob);
        }
        for (final Sprite.Part d : clickable) {
            final Gob gob = (Gob) d.owner;
            if (gob == null)
                continue;
            if (d.checkhit(c.sub(gob.sc)))
                return (gob);
        }
        return (null);
    }

    public boolean mousedown(final Coord c, final int button) {
        setfocus(this);
        final Coord cScaled = new Coord((int) (c.x / getScale()), (int) (c.y / getScale()));
        final Gob hit = gobatpos(cScaled);

        // arksu: если мы в режиме выбора объекта - возвращаем его и выходим
        if (modeSelectObject.get()) {
            synchronized (modeSelectObject) {
                onmouse = hit;
                modeSelectObject.set(false);
                modeSelectObject.notifyAll();
            }
            return true;
        }

        final Coord mc = s2m(cScaled.sub(viewoffset(sz, this.mc)));
        if (grab != null) {
            grab.mmousedown(mc, button);
        } else if ((cam != null) && cam.click(this, cScaled, mc, button)) {
            /* Nothing */
        } else if (plob != null) {
            Gob gob = null;
            for (final Gob g : plob)
                gob = g;
            //noinspection ConstantConditions
            wdgmsg("place", gob.rc, button, ui.modflags());
        } else {
            if (hit == null)
                wdgmsg("click", c, mc, button, ui.modflags());
            else
                wdgmsg("click", c, mc, button, ui.modflags(), hit.id, hit.getc());
        }
        return (true);
    }

    public boolean mouseup(Coord c, final int button) {
        c = new Coord((int) (c.x / getScale()), (int) (c.y / getScale()));
        final Coord mc = s2m(c.sub(viewoffset(sz, this.mc)));
        if (grab != null) {
            grab.mmouseup(mc, button);
            return (true);
        } else if ((cam != null) && cam.release(this, c, mc, button)) {
            return (true);
        } else {
            return (true);
        }
    }

    public void mousemove(Coord c) {
        c = new Coord((int) (c.x / getScale()), (int) (c.y / getScale()));
        this.pmousepos = c;
        final Coord mc = s2m(c.sub(viewoffset(sz, this.mc)));
        this.mousepos = mc;
        this.mouse_tile = tilify(mousepos);
        final Collection<Gob> plob = this.plob;
        if (cam != null)
            cam.move(this, c, mc);
        if (grab != null) {
            grab.mmousemove(mc);
        } else if (plob != null) {
            Gob gob = null;
            for (final Gob g : plob)
                gob = g;
            final boolean plontile = this.plontile ^ ui.modshift;
            //noinspection ConstantConditions
            gob.move(plontile ? tilify(mc) : mc);
        }

        //arksu: вычисляем объект под мышью
        if (pmousepos != null) {
            onmouse = gobatpos(pmousepos);
        } else {
            onmouse = null;
        }
    }

    public boolean mousewheel(final Coord c, final int amount) {
        if (!Config.zoom)
            return false;
        si = Math.min(8, Math.max(0, si - amount));
        setScale(scales[si]);
        return (true);
    }

    public void move(final Coord mc) {
        this.mc = mc;
    }

    public static Coord tilify(Coord c) {
        c = c.div(tilesz);
        c = c.mul(tilesz);
        c = c.add(tilesz.div(2));
        return (c);
    }

    private void unflashol() {
        for (int i = 0; i < visol.length; i++) {
            if ((olflash & (1 << i)) != 0)
                visol[i]--;
        }
        olflash = 0;
        olftimer = 0;
    }

    public void uimsg(@NotNull final String msg, final Object... args) {
        if (msg.equals("move")) {
            move((Coord) args[0]);
            if (cam != null)
                cam.moved(this);
        } else if (msg.equals("flashol")) {
            unflashol();
            olflash = (Integer) args[0];
            for (int i = 0; i < visol.length; i++) {
                if ((olflash & (1 << i)) != 0)
                    visol[i]++;
            }
            olftimer = System.currentTimeMillis() + (Integer) args[1];
        } else if (msg.equals("place")) {
            Collection<Gob> plob = this.plob;
            if (plob != null) {
                this.plob = null;
                glob.oc.lrem(plob);
            }
            plob = new LinkedList<Gob>();
            plontile = (Integer) args[2] != 0;
            final Gob gob = new Gob(glob, plontile ? tilify(mousepos) : mousepos);
            final Resource res = Resource.load((String) args[0], (Integer) args[1]);
            gob.setattr(new ResDrawable(gob, res));
            plob.add(gob);
            glob.oc.ladd(plob);
            if (args.length > 3) {
                plrad = (Integer) args[3];
                radiuses.put(res.name, plrad);
                if (res.name.equals("gfx/terobjs/bhive"))
                    radiuses.put("gfx/terobjs/bhived", plrad);
            }
            this.plob = plob;
        } else if (msg.equals("unplace")) {
            if (plob != null)
                glob.oc.lrem(plob);
            plob = null;
            plrad = 0;
        } else if (msg.equals("polowner")) {
            final String o = ((String) args[0]).intern();
            if (!o.equals(polowner)) {
                if (o.length() == 0) {
                    if (this.polowner != null)
                        this.polownert = polownertf.render("Leaving " + this.polowner);
                    this.polowner = null;
                } else {
                    this.polowner = o;
                    this.polownert = polownertf.render("Entering " + o);
                }
                this.polchtm = System.currentTimeMillis();
            }
        } else {
            super.uimsg(msg, args);
        }
    }

    public void enol(final int... overlays) {
        for (final int ol : overlays)
            visol[ol]++;
    }

    public void disol(final int... overlays) {
        for (final int ol : overlays)
            visol[ol]--;
    }

    private int gettilen(final Coord tc) throws Loading {
        final int r = map.gettilen(tc);
        if (r == -1)
            throw (new Loading());
        return (r);
    }

    private Tile getground(final Coord tc) throws Loading {
        final Tile r = map.getground(tc);
        if (r == null)
            throw (new Loading());
        return (r);
    }

    private Tile[] gettrans(final Coord tc) throws Loading {
        final Tile[] r = map.gettrans(tc);
        if (r == null)
            throw (new Loading());
        return (r);
    }

    private int getol(final Coord tc) throws Loading {
        final int ol = map.getol(tc);
        if (ol == -1)
            throw (new Loading());
        return (ol);
    }

    private void drawtile(final GOut g, final Coord tc, final Coord sc) {
        final Tile t;

        try {
            t = getground(tc);
            //t = gettile(tc).ground.pick(0);
            g.image(t.tex(), sc);
            //g.setColor(FlowerMenu.pink);
            //Utils.drawtext(g, Integer.toString(t.i), sc);
            for (final Tile tt : gettrans(tc)) {
                g.image(tt.tex(), sc);
            }
        } catch (Loading e) {
        }
    }

    private void drawol(final GOut g, final Coord tc, final Coord sc) {
        final int ol;
        int i;
        final double w = 2;

        try {
            ol = getol(tc);
            if (ol == 0)
                return;
            @SuppressWarnings({"UnnecessaryLocalVariable"}) final Coord c1 = sc;
            final Coord c2 = sc.add(m2s(new Coord(0, tilesz.y)));
            final Coord c3 = sc.add(m2s(new Coord(tilesz.x, tilesz.y)));
            final Coord c4 = sc.add(m2s(new Coord(tilesz.x, 0)));
            for (i = 0; i < olc.length; i++) {
                if (olc[i] == null)
                    continue;
                if (((ol & (1 << i)) == 0) || (visol[i] < 1))
                    continue;
                final Color fc = new Color(olc[i].getRed(), olc[i].getGreen(), olc[i].getBlue(), 32);
                g.chcolor(fc);
                g.frect(c1, c2, c3, c4);
                if (((ol & ~getol(tc.add(new Coord(-1, 0)))) & (1 << i)) != 0) {
                    g.chcolor(olc[i]);
                    g.line(c2, c1, w);
                }
                if (((ol & ~getol(tc.add(new Coord(0, -1)))) & (1 << i)) != 0) {
                    g.chcolor(olc[i]);
                    g.line(c1.add(1, 0), c4.add(1, 0), w);
                }
                if (((ol & ~getol(tc.add(new Coord(1, 0)))) & (1 << i)) != 0) {
                    g.chcolor(olc[i]);
                    g.line(c4.add(1, 0), c3.add(1, 0), w);
                }
                if (((ol & ~getol(tc.add(new Coord(0, 1)))) & (1 << i)) != 0) {
                    g.chcolor(olc[i]);
                    g.line(c3, c2, w);
                }
            }
            g.chcolor(Color.WHITE);
        } catch (Loading e) {
        }
    }

    private void drawradius(final GOut g, final Coord c, final int radius) {
        g.fellipse(c, new Coord((int) (radius * 4 * Math.sqrt(0.5)), (int) (radius * 2 * Math.sqrt(0.5))));
    }

    private void drawplobeffect(final GOut g) {
        if (plob == null)
            return;
        Gob gob = null;
        for (final Gob tg : plob)
            gob = tg;
        //noinspection ConstantConditions
        if (gob.sc == null)
            return;
        if (plrad > 0) {
            final String name = gob.resname();
            g.chcolor(0, 255, 0, 32);
            synchronized (glob.oc) {
                for (final Gob tg : glob.oc)
                    if ((tg.sc != null) && (tg.resname() == name))
                        drawradius(g, tg.sc, plrad);
            }
            g.chcolor();
        }
    }

    private void draweffectradius(final GOut g) {
        String name;
        g.chcolor(0, 255, 0, 32);
        synchronized (glob.oc) {
            for (final Gob tg : glob.oc) {
                name = tg.resname();
                if (radiuses.containsKey(name) && (tg.sc != null)) {
                    drawradius(g, tg.sc, radiuses.get(name));
                }
            }
        }
        g.chcolor();
    }

    private void drawbeastradius(final GOut g) {
        String name;
        g.chcolor(255, 0, 0, 96);
        synchronized (glob.oc) {
            for (final Gob tg : glob.oc) {
                name = tg.resname();
                if ((tg.sc != null) && (name.indexOf("/cdv") < 0) && ((name.indexOf("kritter/boar") >= 0)
                        || (name.indexOf("kritter/bear") >= 0))) {
                    drawradius(g, tg.sc, 100);
                }
            }
        }
        g.chcolor();
    }

    private void drawtracking(final GOut g) {
        g.chcolor(255, 0, 255, 128);
        final Coord oc = viewoffset(sz, mc);
        for (int i = 0; i < TrackingWnd.instances.size(); i++) {
            final TrackingWnd wnd = TrackingWnd.instances.get(i);
            if (wnd.pos == null) {
                continue;
            }
            final Coord c = m2s(wnd.pos).add(oc);
            g.fellipse(c, new Coord(100, 50), wnd.a1, wnd.a2);
        }
        g.chcolor();
    }

    private static boolean follows(final Gob g1, final Gob g2) {
        Following flw;
        if ((flw = g1.getattr(Following.class)) != null) {
            if (flw.tgt() == g2)
                return (true);
        }
        if ((flw = g2.getattr(Following.class)) != null) {
            if (flw.tgt() == g1)
                return (true);
        }
        return (false);
    }

    private List<Sprite.Part> findobsc() {
        final ArrayList<Sprite.Part> obsc = new ArrayList<Sprite.Part>();
        if (obscgob == null)
            return (obsc);
        boolean adding = false;
        for (final Sprite.Part p : clickable) {
            final Gob gob = (Gob) p.owner;
            if (gob == null)
                continue;
            if (gob == obscgob) {
                adding = true;
                continue;
            }
            if (follows(gob, obscgob))
                continue;
            if (adding && obscpart.checkhit(gob.sc.sub(obscgob.sc)))
                obsc.add(p);
        }
        return (obsc);
    }

    private void drawols(final GOut g, final Coord sc) {
        synchronized (map.grids) {
            for (final Map.Entry<Coord, Grid> coordGridEntry : map.grids.entrySet()) {
                final Grid grid = coordGridEntry.getValue();
                for (final Overlay lol : grid.ols) {
                    final int id = getolid(lol.mask);
                    if (visol[id] < 1) {
                        continue;
                    }
                    final Coord c0 = coordGridEntry.getKey().mul(cmaps);
                    drawol2(g, id, c0.add(lol.c1), c0.add(lol.c2), sc);
                }
            }
        }
        for (final Overlay lol : map.ols) {
            final int id = getolid(lol.mask);
            if (visol[id] < 1) {
                continue;
            }
            drawol2(g, id, lol.c1, lol.c2, sc);
        }
        g.chcolor();
    }

    private int getolid(final int mask) {
        for (int i = 0; i < olc.length; i++) {
            if ((mask & (1 << i)) != 0) {
                return i;
            }
        }
        return 0;
    }

    private void drawol2(final GOut g, final int id, final Coord c0, Coord cx, final Coord sc) {
        cx = cx.add(1, 1);
        final Coord c1 = m2s(c0.mul(tilesz)).add(sc);
        final Coord c2 = m2s(new Coord(c0.x, cx.y).mul(tilesz)).add(sc);
        final Coord c3 = m2s(cx.mul(tilesz)).add(sc);
        final Coord c4 = m2s(new Coord(cx.x, c0.y).mul(tilesz)).add(sc);

        final Color fc = new Color(olc[id].getRed(), olc[id].getGreen(), olc[id].getBlue(), 32);
        g.chcolor(fc);
        g.frect(c1, c2, c3, c4);
        cx = cx.sub(1, 1);
        drawline(g, new Coord(0, -1), c0.y, id, c0, cx, sc);
        drawline(g, new Coord(0, 1), cx.y, id, c0, cx, sc);
        drawline(g, new Coord(1, 0), cx.x, id, c0, cx, sc);
        drawline(g, new Coord(-1, 0), c0.x, id, c0, cx, sc);
        g.chcolor();
    }

    private void drawline(final GOut g, final Coord d, final int med, final int id, final Coord c0, final Coord cx, final Coord sc) {

        final Coord m = d.abs();
        final Coord r = m.swap();
        final Coord off = m.mul(med).add(d.add(m).div(2));
        final int min = c0.mul(r).sum();
        final int max = cx.mul(r).sum() + 1;
        boolean t = false;
        int begin = min;
        final int ol = 1 << id;
        g.chcolor(olc[id]);
        for (int i = min; i <= max; i++) {
            final Coord c = r.mul(i).add(m.mul(med)).add(d);
            int ol2;
            try {
                ol2 = getol(c);
            } catch (Loading e) {
                ol2 = ol;
            }
            if (t) {
                if (((ol2 & ol) != 0) || i == max) {
                    t = false;
                    final Coord cb = m2s(tilesz.mul(r.mul(begin).add(off))).add(sc);
                    final Coord ce = m2s(tilesz.mul(r.mul(i).add(off))).add(sc);
                    g.line(cb, ce, 2);
                }
            } else {
                if ((ol2 & ol) == 0) {
                    t = true;
                    begin = i;
                }
            }
        }
    }

    private static void draw_tile_select(final GOut g, final Coord tc, final Coord sc) {
        final Coord c2 = sc.add(m2s(new Coord(0, tilesz.y)));
        final Coord c3 = sc.add(m2s(new Coord(tilesz.x, tilesz.y)));
        final Coord c4 = sc.add(m2s(new Coord(tilesz.x, 0)));

        g.chcolor(TRANSPARENT_GREEN);
        g.frect(sc, c2, c3, c4);
        g.chcolor(Color.GREEN);
        g.line(c2, sc, 1.5);
        g.line(sc.add(1, 0), c4.add(1, 0), 1.5);
        g.line(c4.add(1, 0), c3.add(1, 0), 1.5);
        g.line(c3, c2, 1.5);

    }

    public void drawmap(final GOut g) {
        int x, y, i;
        final int stw;
        final int sth;
        final Coord oc;
        final Coord tc;
        Coord ctc;
        Coord sc;


        ///arksu
        Coord mp = null;
//        if (mousepos != null && (show_selected_tile || Config.assign_to_tile))
        if (mousepos != null && show_selected_tile) {
            mp = mouse_tile.div(tilesz);
        }


        if (Config.profile)
            curf = prof.new Frame();
        stw = (tilesz.x * 4) - 2;
        sth = tilesz.y * 2;
        oc = viewoffset(sz, mc);
        tc = mc.div(tilesz);
        tc.setX(tc.x + -(sz.x / (2 * stw)) - (sz.y / (2 * sth)) - 2);
        tc.setY(tc.y + (sz.x / (2 * stw)) - (sz.y / (2 * sth)));
        for (y = 0; y < (sz.y / sth) + 2; y++) {
            for (x = 0; x < (sz.x / stw) + 3; x++) {
                for (i = 0; i < 2; i++) {
                    ctc = tc.add(new Coord(x + y, -x + y + i));
                    sc = m2s(ctc.mul(tilesz)).add(oc);
                    sc.x -= tilesz.x * 2;
                    drawtile(g, ctc, sc);
                    sc.x += tilesz.x * 2;
                    if (!Config.newclaim) {
                        drawol(g, ctc, sc);
                    }
                    // arksu : выводим тайл под мышью
//                    if (mousepos != null && (show_selected_tile || Config.assign_to_tile)) {
                    if (mp != null && mp.y == ctc.y && mp.x == ctc.x) {
                        draw_tile_select(g, ctc, sc);
                    }
                }
            }
        }

        if (Config.newclaim) {
            drawols(g, oc);
        }
        if (Config.grid) {
            g.chcolor(new Color(40, 40, 40));
            Coord c1;
            Coord c2;
            final Coord d;
            d = tc.mul(tilesz);
            final int hy = (sz.y / sth) * tilesz.y;
            final int hx = (sz.x / stw) * tilesz.x;
            c1 = d.add(0, 0);
            c2 = d.add(5 * hx / 2, 0);
            for (y = d.y - hy; y < d.y + hy; y = y + tilesz.y) {
                c1.setY(y);
                c2.setY(c1.y);
                g.line(m2s(c1).add(oc), m2s(c2).add(oc), 1);
            }
            c1 = d.add(0, -hy);
            c2 = d.add(0, hy);

            for (x = d.x; x < d.x + 5 * hx / 2; x = x + tilesz.x) {
                c1.setX(x);
                c2.setX(c1.x);
                g.line(m2s(c1).add(oc), m2s(c2).add(oc), 1);
            }
            g.chcolor();
        }
        if (curf != null)
            curf.tick("map");

        if (Config.showRadius)
            draweffectradius(g);
        else
            drawplobeffect(g);

        if (Config.showBeast) {
            drawbeastradius(g);
        }

        drawtracking(g);

        if (curf != null)
            curf.tick("plobeff");

        final List<Sprite.Part> sprites = new ArrayList<Sprite.Part>();
        final ArrayList<Speaking> speaking = new ArrayList<Speaking>();
        final ArrayList<KinInfo> kin = new ArrayList<KinInfo>();
        class GobMapper implements Sprite.Drawer {
            Gob cur = null;
            Sprite.Part.Effect fx = null;
            int szo = 0;

            public void chcur(final Gob cur) {
                this.cur = cur;
                final GobHealth hlt = cur.getattr(GobHealth.class);
                fx = null;
                if (hlt != null)
                    fx = hlt.getfx();
                final Following flw = cur.getattr(Following.class);
                szo = 0;
                if (flw != null)
                    szo = flw.szo;
            }

            public void addpart(final Sprite.Part p) {
                p.effect = fx;
                if ((p.ul.x >= sz.x) ||
                        (p.ul.y >= sz.y) ||
                        (p.lr.x < 0) ||
                        (p.lr.y < 0))
                    return;
                sprites.add(p);
                p.owner = cur;
                p.szo = szo;
            }
        }

        if (Config.showHidden && CustomConfig.isHideObjects()) {
            g.chcolor(255, 0, 0, 128);
            synchronized (glob.oc) {
                for (final Gob gob : glob.oc) {
                    final Drawable d = gob.getattr(Drawable.class);
                    final Neg neg;
                    final String name = gob.resname();
                    if (!gob.hide || (name.indexOf("wald") > -1) || (name.indexOf("flavobjs") > -1))
                        continue;
                    if (d instanceof ResDrawable) {
                        final ResDrawable rd = (ResDrawable) d;
                        if (rd.spr == null)
                            continue;
                        if (rd.spr.res == null)
                            continue;
                        neg = rd.spr.res.layer(Resource.negc);
                    } else if (d instanceof Layered) {
                        final Layered lay = (Layered) d;
                        if (lay.base.get() == null)
                            continue;
                        neg = lay.base.get().layer(Resource.negc);
                    } else {
                        continue;
                    }
                    if ((neg.bs.x > 0) && (neg.bs.y > 0)) {
                        final Coord c1 = gob.getc().add(neg.bc);
                        final Coord c2 = gob.getc().add(neg.bc).add(neg.bs);
                        g.frect(m2s(c1).add(oc),
                                m2s(new Coord(c2.x, c1.y)).add(oc),
                                m2s(c2).add(oc),
                                m2s(new Coord(c1.x, c2.y)).add(oc));
                    }
                }
            }
            g.chcolor();
        }

        final GobMapper drawer = new GobMapper();
        //noinspection SynchronizeOnNonFinalField
        synchronized (glob.oc) {
            for (final Gob gob : glob.oc) {
                drawer.chcur(gob);
                final Coord dc = m2s(gob.getc()).add(oc);
                gob.sc = dc;
                gob.drawsetup(drawer, dc, sz);
                final Speaking s = gob.getattr(Speaking.class);
                if (s != null)
                    speaking.add(s);
                final KinInfo k = gob.getattr(KinInfo.class);
                if (k != null)
                    kin.add(k);
            }
            if (curf != null)
                curf.tick("setup");
            Collections.sort(sprites, Sprite.partidcmp);
            {
                final Sprite.Part[] clickable = new Sprite.Part[sprites.size()];
                for (int o = 0, u = clickable.length - 1; o < clickable.length; o++, u--)
                    clickable[u] = sprites.get(o);
                this.clickable = clickable;
            }
            if (curf != null)
                curf.tick("sort");


            if (pmousepos != null) {
                onmouse = gobatpos(pmousepos);
            } else {
                onmouse = null;
            }
            onmouse = null;
            if (pmousepos != null)
                onmouse = gobatpos(pmousepos);
            obscured = findobsc();
            if (curf != null)
                curf.tick("obsc");
            for (final Sprite.Part part : sprites) {
                if (part.effect != null)
                    part.draw(part.effect.apply(g));
                else
                    part.draw(g);
            }
            for (final Sprite.Part part : obscured) {
                final GOut g2 = new GOut(g);
                final GobHealth hlt;
                if ((part.owner != null) && (part.owner instanceof Gob) && ((hlt = ((Gob) part.owner).getattr(GobHealth.class)) != null))
                    g2.chcolor(255, (int) (hlt.asfloat() * 255), 0, 255);
                else
                    g2.chcolor(255, 255, 0, 255);
                part.drawol(g2);
            }

            if (Config.bounddb && ui.modshift) {
                g.chcolor(255, 0, 0, 128);
                //noinspection SynchronizeOnNonFinalField
                synchronized (glob.oc) {
                    for (final Gob gob : glob.oc) {
                        final Drawable d = gob.getattr(Drawable.class);
                        final Neg neg;
                        if (d instanceof ResDrawable) {
                            final ResDrawable rd = (ResDrawable) d;
                            if (rd.spr == null)
                                continue;
                            if (rd.spr.res == null)
                                continue;
                            neg = rd.spr.res.layer(Resource.negc);
                        } else if (d instanceof Layered) {
                            final Layered lay = (Layered) d;
                            if (lay.base.get() == null)
                                continue;
                            neg = lay.base.get().layer(Resource.negc);
                        } else {
                            continue;
                        }
                        if ((neg.bs.x > 0) && (neg.bs.y > 0)) {
                            final Coord c1 = gob.getc().add(neg.bc);
                            final Coord c2 = gob.getc().add(neg.bc).add(neg.bs);
                            g.frect(m2s(c1).add(oc),
                                    m2s(new Coord(c2.x, c1.y)).add(oc),
                                    m2s(c2).add(oc),
                                    m2s(new Coord(c1.x, c2.y)).add(oc));
                        }
                    }
                }
                g.chcolor();
            }

            if (curf != null)
                curf.tick("draw");
            g.image(mask, Coord.z);
            final long now = System.currentTimeMillis();
            RootWidget.names_ready = (RootWidget.screenshot && Config.sshot_nonames);
            if (!RootWidget.names_ready) {
                for (final KinInfo k : kin) {
                    final Tex t = k.rendered();
                    final Coord gc = k.gob.sc;
                    final String name = k.gob.resname();
                    final boolean isother = name.contains("hearth")
                            || name.contains("skeleton");
                    if (gc.isect(Coord.z, sz)) {
                        if (k.seen == 0)
                            k.seen = now;
                        final int tm = (int) (now - k.seen);
                        Color show = null;
                        final boolean auto = (k.type & 1) == 0;
                        if (k.type == 0) {
//                TODO:    			if((isother && Config.showOtherNames)||(!isother && Config.showNames)||(k.gob == onmouse)) {
                            if (k.gob == onmouse) {
                                show = Color.WHITE;
                            } else if (auto && (tm < 7500)) {
                                show = Utils.clipcol(255, 255, 255, 255 - ((255 * tm) / 7500));
                            }
                        } else if (k.type == 1) {
                            if (k.gob == onmouse)
                                show = Color.WHITE;
                        }
                        if (show != null) {
                            g.chcolor(show);
                            g.image(t, gc.add(-t.sz().x / 2, -40 - t.sz().y));
                            g.chcolor();
                        }
                    } else {
                        k.seen = 0;
                    }
                }
                for (final Speaking s : speaking) {
                    s.draw(g, s.gob.sc.add(s.off));
                }
                if (curf != null) {
                    curf.tick("aux");
                    curf.fin();
                    curf = null;
                }
                //System.out.println(curf);
            }
        }
    }

    public void drawarrows(final GOut g) {
        final Coord oc = viewoffset(sz, mc);
        final Coord hsz = sz.div(2);
        final double ca = -Coord.z.angle(hsz);
        for (final Party.Member m : glob.party.memb.values()) {
            //Gob gob = glob.oc.getgob(id);
            final Coord mc = m.getc();
            if (mc == null)
                continue;
            final Coord sc = m2s(mc).add(oc);
            if (!sc.isect(Coord.z, sz)) {
                final double a = -hsz.angle(sc);
                final Coord ac;
                if ((a > ca) && (a < -ca)) {
                    ac = new Coord(sz.x, hsz.y - (int) (Math.tan(a) * hsz.x));
                } else if ((a > -ca) && (a < Math.PI + ca)) {
                    ac = new Coord(hsz.x - (int) (Math.tan(a - Math.PI / 2) * hsz.y), 0);
                } else if ((a > -Math.PI - ca) && (a < ca)) {
                    ac = new Coord(hsz.x + (int) (Math.tan(a + Math.PI / 2) * hsz.y), sz.y);
                } else {
                    ac = new Coord(0, hsz.y + (int) (Math.tan(a) * hsz.x));
                }
                g.chcolor(m.col);
                final Coord bc = ac.add(Coord.sc(a, -10));
                g.line(bc, bc.add(Coord.sc(a, -40)), 2);
                g.line(bc, bc.add(Coord.sc(a + Math.PI / 4, -10)), 2);
                g.line(bc, bc.add(Coord.sc(a - Math.PI / 4, -10)), 2);
                g.chcolor(Color.WHITE);
            }
        }
    }

    private void checkplmove() {
        final Gob pl;
        final long now = System.currentTimeMillis();
        if ((playergob >= 0) && ((pl = glob.oc.getgob(playergob)) != null) && (pl.sc != null)) {
            final Coord plp = pl.getc();
            if ((plfpos == null) || !plfpos.equals(plp)) {
                lastmove = now;
                plfpos = plp;
                if ((obscpart != null) && !obscpart.checkhit(pl.sc.sub(obscgob.sc))) {
                    obscpart = null;
                    obscgob = null;
                }
            } else if (now - lastmove > 500) {
                for (final Sprite.Part p : clickable) {
                    final Gob gob = (Gob) p.owner;
                    if ((gob == null) || (gob.sc == null))
                        continue;
                    if (gob == pl)
                        break;
                    if (p.checkhit(pl.sc.sub(gob.sc))) {
                        obscpart = p;
                        obscgob = gob;
                        break;
                    }
                }
            }
        }
    }

    private void checkmappos() {
        if (cam == null)
            return;
        Coord sz = this.sz;
        final SlenHud slen = ui.slen;
        if (slen != null)
            sz = sz.add(0, -slen.foldheight());
        final Gob player = glob.oc.getgob(playergob);
        if (player != null)
            cam.setpos(this, player, sz);
    }

    public void draw(final GOut og) {
        hsz = CustomConfig.getWindowSize();
        sz = hsz.mul(1 / getScale());
        final GOut g = og.reclip(Coord.z, sz);
        g.gl.glPushMatrix();
        g.scale(getScale());
        checkmappos();
        final Coord requl = mc.add(-500, -500).div(tilesz).div(cmaps);
        final Coord reqbr = mc.add(500, 500).div(tilesz).div(cmaps);
        final Coord cgc = new Coord(0, 0);
        for (cgc.setY(requl.y); cgc.y <= reqbr.y; cgc.setY(cgc.y + 1)) {
            for (cgc.setX(requl.x); cgc.x <= reqbr.x; cgc.setX(cgc.x + 1)) {
                if (map.grids.get(cgc) == null)
                    map.request(new Coord(cgc));
            }
        }
        final long now = System.currentTimeMillis();
        if ((olftimer != 0) && (olftimer < now))
            unflashol();
        map.sendreqs();
        checkplmove();
//        try {
        if ((mask.amb = glob.amblight) == null || CustomConfig.hasNightVision)
            mask.amb = new Color(0, 0, 0, 0);
        drawmap(g);
        drawarrows(g);
        g.chcolor(Color.WHITE);
        g.chcolor(Color.WHITE);

        if (Config.dbtext) {
            int ay = 120;
            final int margin = 15;
            if (onmouse != null) {
                g.atext("gob at mouse: id=" + onmouse.id +
                        " coord=" + onmouse.getc() +
                        " res=" + onmouse.getResName() +
                        " msg=" + onmouse.getBlob(0),
                        new Coord(10, ay), 0, 1);
                ay = ay + margin;
            } else {
                g.atext("gob at mouse: <<< NULL >>>", new Coord(10, ay), 0, 1);
                ay = ay + margin;
            }
            if (mousepos != null) {
                g.atext("mouse map pos: " + mousepos.toString(), new Coord(10, ay), 0, 1);
                ay = ay + margin;
                g.atext("tile coord: " + mouse_tile.toString(), new Coord(10, ay), 0, 1);
                ay = ay + margin;
            }
            g.atext("cursor name: " + UI.cursorName, new Coord(10, ay), 0, 1);
            ay = ay + margin;
            g.atext("player=" + playergob, new Coord(10, ay), 0, 1);
            ay = ay + margin;
            g.atext("time_to_start: " + time_to_start, new Coord(10, ay), 0, 1);
//            ay = ay + margin;
//            if (hhl_main.symbols != null && Config.debug_flag) {
//                synchronized (hhl_main.symbols.ShowNames) {
//                    for (String s : hhl_main.symbols.ShowNames) {
//                        Variable v = (Variable) hhl_main.symbols.globals.get(s);
//                        if (v != null) {
//                            g.atext("VARIABLE '" + s + "' = " + v.value, new Coord(10, ay), 0, 1);
//                            ay = ay + margin;
//                        }
//                    }
//                }
//            }

        }
//        if (Config.dbtext) {
//            g.atext(mc.div(11).toString(), new Coord(10, 560), 0, 1);
//        }
//        } catch (Loading l) {
//            String text = "Loading...";
//            g.chcolor(Color.BLACK);
//            g.frect(Coord.z, sz);
//            g.chcolor(Color.WHITE);
//            g.atext(text, sz.div(2), 0.5, 0.5);
//        }
        final long poldt = now - polchtm;
        if ((polownert != null) && (poldt < 6000)) {
            final int a;
            if (poldt < 1000)
                a = (int) ((255 * poldt) / 1000);
            else if (poldt < 4000)
                a = 255;
            else
                a = (int) ((255 * (2000 - (poldt - 4000))) / 2000);
            g.chcolor(255, 255, 255, a);
            g.aimage(polownert.tex(), sz.div(2), 0.5, 0.5);
            g.chcolor();
        }
        g.gl.glPopMatrix();
        super.draw(og);
    }

    public boolean drop(final Coord cc, final Coord ul, final Item item) {
        wdgmsg("drop", ui.modflags());
        return (true);
    }

    public boolean iteminteract(Coord cc, final Coord ul) {
        final Coord cc0 = cc;
        cc = new Coord((int) (cc.x / getScale()), (int) (cc.y / getScale()));
        final Gob hit = gobatpos(cc);
        final Coord mc = s2m(cc.sub(viewoffset(sz, this.mc)));
        if (hit == null)
            wdgmsg("itemact", cc0, mc, ui.modflags());
        else
            wdgmsg("itemact", cc0, mc, ui.modflags(), hit.id, hit.getc());
        return (true);
    }

    private Map<String, Console.Command> cmdmap = new TreeMap<String, Console.Command>();

    {
        cmdmap.put("cam", new Console.Command() {
            public void run(final Console cons, final String[] args) {
                if (args.length >= 2) {
                    final Class<? extends Camera> ct = camtypes.get(args[1]);
                    final String[] cargs = new String[args.length - 2];
                    System.arraycopy(args, 2, cargs, 0, cargs.length);
                    if (ct != null) {
                        try {
                            MapView.this.cam = makecam(ct, cargs);
                            Utils.setpref("defcam", args[1]);
                            Utils.setprefb("camargs", Utils.serialize(cargs));
                        } catch (ClassNotFoundException e) {
                            throw (new RuntimeException("no such camera: " + args[1]));
                        }
                    } else {
                        throw (new RuntimeException("no such camera: " + args[1]));
                    }
                }
            }
        });
        cmdmap.put("plol", new Console.Command() {
            public void run(final Console cons, final String[] args) {
                final Indir<Resource> res = Resource.load(args[1]).indir();
                final Message sdt;
                if (args.length > 2)
                    sdt = new Message(0, Utils.hex2byte(args[2]));
                else
                    sdt = new Message(0);
                final Gob pl;
                if ((playergob >= 0) && ((pl = glob.oc.getgob(playergob)) != null))
                    pl.ols.add(new Gob.Overlay(-1, res, sdt));
            }
        });
    }

    public Map<String, Console.Command> findcmds() {
        return (cmdmap);
    }

    public void update(final long dt) {
        final Coord new_my_coord = Player.getPosition();
        if ((new_my_coord != null) && (last_my_coord != null))
            if ((new_my_coord.dist(last_my_coord) > 20 * 11) && (cam != null))
                cam.reset();
        last_my_coord = new_my_coord;
        time_to_start = time_to_start - dt;

        if (!Config.auto_start_script.isEmpty() && time_to_start <= 0) {
            if (time_to_start <= 0 && !started) {
                try {
                    started = true;
                    time_to_start = 0;
                    ScriptsManager.run(Config.auto_start_script);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        final Coord requl = mc.add(-500, -500).div(tilesz).div(cmaps);
        final Coord reqbr = mc.add(500, 500).div(tilesz).div(cmaps);
        final Coord cgc = new Coord(0, 0);
        for (cgc.y = requl.y; cgc.y <= reqbr.y; cgc.y++) {
            for (cgc.x = requl.x; cgc.x <= reqbr.x; cgc.x++) {
                if (map.grids.get(cgc) == null)
                    map.request(new Coord(cgc));
            }
        }
        final long now = System.currentTimeMillis();
        if ((olftimer != 0) && (olftimer < now))
            unflashol();
        map.sendreqs();
        checkplmove();
        sz = CustomConfig.getWindowSize();
//        mask.UpdateSize(sz);
        checkmappos();
    }


}
