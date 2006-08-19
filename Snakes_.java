import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.Toolbar;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

public class Snakes_ implements PlugInFilter, MouseListener {
	
	
    ImagePlus img;
    ImageCanvas canvas;

    Dijkstra dj;


	public int setup(String arg, ImagePlus imp) {
		
		this.img = imp;
		if (arg.equals("about")) {
		    showAbout();
		    return DONE;
		}
		return DOES_8G+DOES_STACKS+SUPPORTS_MASKING;
	}

	
	public void run(ImageProcessor ip) {
		ImageWindow win = img.getWindow();
		canvas = win.getCanvas();
		for(int i=0;i<canvas.getMouseListeners().length;i++){		
			canvas.removeMouseListener(canvas.getMouseListeners()[i]);
		}
		canvas.addMouseListener(this);
		
		/**
		 * This part will remove the old toolbar
		 */
		Toolbar oldToolbar = Toolbar.getInstance();
		Container container = oldToolbar.getParent();
		Component[] components = container.getComponents();
		for(int i=0; i< components.length; i++){
			if(components[i] == oldToolbar){
				container.remove(i);
				//TODO add new toolbar here
				//container.add(newToolbar)
				break;
			}
		}
		container.validate();
		
		ImageWindow iw;
		ImageCanvas ic;
		
		
		
		
		byte[] pixels = (byte[])ip.getPixels();
		int width = ip.getWidth();
		Rectangle r = ip.getRoi();
		int offset, i;
		for (int y=r.y; y<(r.y+r.height); y++) {
		    offset = y*width;
		    for (int x=r.x; x<(r.x+r.width); x++) {
		        i = offset + x;
		        pixels[i] = (byte)(255-pixels[i]);
		    }
		}
		
		//initializing DIJKSTRA
	        dj = new Dijkstra (pixels,ip.getWidth(),ip.getHeight());


	}
	
	void showAbout() {
	    IJ.showMessage("About Sankes_...",
	    "This sample plugin filter inverts 8-bit images. Look\n" +
	    "at the ’Inverter_.java’ source file to see how easy it is\n" +
	    "in ImageJ to process non-rectangular ROIs, to process\n" +
	    "all the slices in a stack, and to display an About box."
	    );
	}

	public void mouseClicked(MouseEvent e) {
		int x = e.getX();
		int y = e.getY();
		int offscreenX = canvas.offScreenX(x);
		int offscreenY = canvas.offScreenY(y);
		IJ.write("mousePressed: "+offscreenX+","+offscreenY);
		System.out.println("mousePressed: "+offscreenX+","+offscreenY);
		dj.setPoint(x,y);
		

		
	}

	public void mousePressed(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	public void mouseReleased(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	public void mouseEntered(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	public void mouseExited(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

}
