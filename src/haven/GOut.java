package haven;

import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.media.opengl.*;

public class GOut {
	private GL gl;
	private Coord ul, sz;
	private Color color;
    
	private GOut(GOut o) {
		this.gl = o.gl;
		this.ul = o.ul;
		this.sz = o.sz;
		this.color = o.color;
	}

	public GOut(GL gl, Coord sz) {
		this.gl = gl;
		this.ul = Coord.z;
		this.sz = sz;
	}
    
	private void checkerr() {
		int err = gl.glGetError();
		if(err != 0)
			throw(new RuntimeException("GL Error: " + err));
	}
	
	private void glcolor() {
		gl.glColor4f((float)color.getRed() / 255.0f,
			     (float)color.getGreen() / 255.0f,
			     (float)color.getBlue() / 255.0f,
			     (float)color.getAlpha() / 255.0f);
	}

	public void image(BufferedImage img, Coord c) {
		Tex tex = new Tex(img);
		image(tex, c);
		tex.dispose();
	}
	
	public void image(Tex tex, Coord c) {
		tex.crender(gl, c.add(ul), ul, sz);
		checkerr();
	}
    
	private void vertex(Coord c) {
		gl.glVertex2i(c.x + ul.x, c.y + ul.y);
	}

	public void line(Coord c1, Coord c2) {
		glcolor();
		gl.glBegin(GL.GL_LINES);
		vertex(c1);
		vertex(c2);
		gl.glEnd();
		checkerr();
	}
    
	public void text(String text, Coord c) {
		atext(text, c, 0, 0);
	}
	
	public void atext(String text, Coord c, double ax, double ay) {
		Text t = Text.render(text);
		Tex T = t.tex();
		Coord sz = t.sz();
		image(T, c.add((int)((double)sz.x * -ax), (int)((double)sz.y * -ay)));
		T.dispose();
		checkerr();
	}
    
	public void frect(Coord ul, Coord sz) {
		glcolor();
		gl.glDisable(GL.GL_TEXTURE_2D);
		gl.glBegin(GL.GL_QUADS);
		vertex(ul);
		vertex(ul.add(new Coord(sz.x, 0)));
		vertex(ul.add(sz));
		vertex(ul.add(new Coord(0, sz.y)));
		gl.glEnd();
		checkerr();
	}

	public void chcolor(Color c) {
		this.color = c;
	}
    
	public GOut reclip(Coord ul, Coord sz) {
		GOut g = new GOut(this);
		g.ul = this.ul.add(ul);
		g.sz = sz;
		return(g);
	}
}