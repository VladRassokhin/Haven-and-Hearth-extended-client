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

public class RemoteUI implements UI.Receiver {
    final Session sess;
    UI ui;

    public RemoteUI(Session sess) {
        this.sess = sess;
        Widget.initbardas();
    }

    public void rcvmsg(int id, String name, Object... args) {
        Message msg = new Message(Message.RMSG_WDGMSG);
        msg.adduint16(id);
        msg.addstring(name);
        msg.addlist(args);
        sess.queuemsg(msg);
    }

    public void run(UI ui) throws InterruptedException {
        this.ui = ui;
        CustomConfig.ui = ui;
        ui.setreceiver(this);
        while (sess.alive()) {
            Message msg;
            while ((msg = sess.getuimsg()) != null) {
                if (msg.type == Message.RMSG_NEWWDG) {
                    int id = msg.uint16(); // New widget Id
                    String type = msg.string(); // New widget Type
                    Coord c = msg.coord(); // New widget coordinates
                    int parent = msg.uint16(); //Parent Id for new widget
                    Object[] args = msg.list(); // Arguments for widget creator (WidgetFabrick)
                    // UI fixes START
                    if (type.equals("cnt")) { // Central welcome widget
                        args[0] = CustomConfig.windowSize;
                    } else if (type.equals("img") && args.length >= 1 && (args[0] instanceof String)) {
                        String arg0 = (String) args[0];
                        if (arg0.startsWith("gfx/hud/prog/")) { // Hourglass (progress bar) at center of screen and change widget type
                            c = CustomConfig.windowCenter;
                            type = "progressbar";
                            Progress.class.getClass();
                        }
                        if (arg0.equals("gfx/ccscr"))
                            c = CustomConfig.windowCenter.add(-400, -300);
                        if (arg0.equals("gfx/logo2"))
                            c = CustomConfig.windowCenter.add(-415, -300);
                    } else if (type.equals("charlist") && args.length >= 1) {
                        c = CustomConfig.windowCenter.add(-380, -50);
                    } else if (type.equals("ibtn") && args.length >= 2) { // New User Button
                        if (args[0].equals("gfx/hud/buttons/ncu") && args[1].equals("gfx/hud/buttons/ncd")) {
                            c = CustomConfig.windowCenter.add(86, 214);
                        }
                    } else if (type.equals("wnd") && c.x == 400 && c.y == 200) {
                        System.err.println("Strange window name=" + args[1].toString());
                        c = CustomConfig.windowCenter.add(0, -100);
                    } else if (type.equals("wnd") && args.length >= 2) {
                        c = args[1].equals("Inventory") && CustomConfig.invCoord.x > 0 && CustomConfig.invCoord.y > 0
                                && CustomConfig.invCoord.x < CustomConfig.windowSize.x - 100
                                && CustomConfig.invCoord.y < CustomConfig.windowSize.y - 100
                                ? CustomConfig.invCoord : c;
                    }
                    if (type.equals("inv")) {
                        Coord pos = ui.widgets.get(parent).c; //on screen position
                        String name = ((Window) ui.widgets.get(parent)).cap.text;
                        Coord size = (Coord) args[0];
//                        CustomConfig.openInventory(id, name, size, pos);
                    }
                    if (type.equals("item")) {
                        int itype = (Integer) args[0];
                        int iquality = (Integer) args[1];
//                        CustomConfig.newItem(parent, id, itype, iquality, c);
                    }
                    if (type.equals("wnd")) {
                        c = CustomConfig.getWindowPosition((String) args[1], c); //Try to restore window on last position
                    }
                    if (CustomConfig.debugMsgs) {
                        System.out.println("Creating Widget id=" + id + " parentId=" + parent + " type='" + type + "' in coord " + c.toString());
                        if (args.length > 0) {
                            System.out.print("  with args: ");
                            try {
                                for (Object o : args) System.out.print(o.toString() + "; ");
                            } catch (Exception ignored) {
                            }
                            System.out.print("\n");
                        }
                    }
                    // US fixes END
                    ui.newwidget(id, type, c, parent, args);

                } else if (msg.type == Message.RMSG_WDGMSG) {
                    int id = msg.uint16();
                    String type = msg.string();
                    Object[] args = msg.list();
                    if (CustomConfig.debugMsgs) {
                        try {
                            System.out.println("Message (type='" + type + "') for widget (id=" + id + ')');
                            if (args.length > 0) {
                                System.out.print("  contains: ");
                                try {
                                    for (Object o : args) System.out.print(o.toString() + "; ");
                                } catch (Exception ignored) {
                                }
                                System.out.print("\n");
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    ui.uimsg(id, type, args);

                } else if (msg.type == Message.RMSG_DSTWDG) {
                    int id = msg.uint16();
                    if (ui.widgets.get(id) instanceof Window) {
                        Window wnd = (Window) ui.widgets.get(id);
                        CustomConfig.setWindowPosition(wnd.cap.text, wnd.c); //Save window on last position
                    }
//                    CustomConfig.closeWidget(id);
                    if (CustomConfig.debugMsgs) System.out.println("Deleting Widget id=" + id);
                    ui.destroy(id);
                }
            }
            //noinspection SynchronizeOnNonFinalField
            synchronized (sess) {
                sess.wait();
            }

        }
    }
}
