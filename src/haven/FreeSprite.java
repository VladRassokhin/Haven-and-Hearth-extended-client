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

import java.util.Collection;
import java.util.LinkedList;

public abstract class FreeSprite extends Sprite {
    /*
    public Coord cc = Coord.z;
    public Coord sz = Coord.z;
    */
    private final Collection<Part> layers = new LinkedList<Part>();

    public interface Layer {
        public void draw(GOut g, Coord sc);
    }

    private static class LPart extends Part {
        final Layer lay;

        LPart(final Layer lay, final int z, final int subz) {
            super(z, subz);
            this.lay = lay;
        }

        public void draw(final GOut g) {
            lay.draw(g, sc());
        }

        public void draw(final java.awt.image.BufferedImage img, final java.awt.Graphics g) {
        }
    }

    protected FreeSprite(final Owner owner, final Resource res, final int z, final int subz) {
        super(owner, res);
        add(new Layer() {
            public void draw(final GOut g, final Coord sc) {
                FreeSprite.this.draw(g, sc);
            }
        }, z, subz);
    }

    protected FreeSprite(final Owner owner, final Resource res) {
        this(owner, res, 0, 0);
    }

    public void add(final Layer lay, final int z, final int subz) {
        layers.add(new LPart(lay, z, subz));
    }

    public boolean checkhit(final Coord c) {
        return (false);
    }

    public void setup(final Drawer d, final Coord cc, final Coord off) {
        setup(layers, d, cc, off);
    }

    public Object stateid() {
        return (this);
    }

    public abstract void draw(GOut g, Coord sc);
}
