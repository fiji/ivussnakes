import ij.IJ;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Toolbar;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

import java.awt.Component;
import java.awt.Container;
import java.awt.Polygon;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

public class Snakes_ implements PlugInFilter, MouseListener, MouseMotionListener {
    final int IDLE = 0;
    final int WIRE = 1;
    
    ImagePlus img;
    ImageCanvas canvas;
    int width, height;
    int state;
    ImagePlus lapzero;//image to visualize zero crossing laplacian
    
    Dijkstraheap dj;
    

	public int setup(String arg, ImagePlus imp) {
			
		
		this.img = imp;
		if (arg.equals("about")) {
		    showAbout();
		    return DONE;
		}
		return DOES_8G+DOES_STACKS+SUPPORTS_MASKING;
	}

	
	public void run(ImageProcessor ip) {

	    //remove old mouse listeners
	    ImageWindow win = img.getWindow();
	    canvas = win.getCanvas();
	    for(int i=0;i<canvas.getMouseListeners().length;i++){		
		canvas.removeMouseListener(canvas.getMouseListeners()[i]);
	    }
	    canvas.addMouseListener(this);
	    canvas.addMouseMotionListener(this);
		
	    state = IDLE;
	    
	    width = ip.getWidth();
	    height = ip.getHeight();
		
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

		//create laplacian zero crossing function
		
		//lapzero = img.createImagePlus();
		ImagePlus nova = NewImage.createByteImage("Baggio - Laplacian zero detection",ip.getWidth(),ip.getHeight(),1,NewImage.FILL_WHITE);
		ImageProcessor newImage = nova.getProcessor();

		byte[] newPixels = new byte[height*width+100];
		byte[] pixels = (byte[]) ip.getPixels();
		newPixels = (byte[]) newImage.getPixels();
		for (int y = 0; y < height; y++) {
		    int offset = y * width;
		    for (int x = 0; x < width; x++) {
			int i = offset + x;
			newPixels[i] =  pixels[i];
		    }
		}
		nova.show();
		nova.updateAndDraw();

		
		//invert pixels 
		//REMOVE ME
		/*
		int width = ip.getWidth();
		Rectangle r = ip.getRoi();
		int offset, i;
		for (int y=r.y; y<(r.y+r.height); y++) {
		    offset = y*width;
		    for (int x=r.x; x<(r.x+r.width); x++) {
		        i = offset + x;
		        pixels[i] = (byte)(255-pixels[i]);
		    }
		}*/

		
		//initializing DIJKSTRA
	        dj = new Dijkstraheap (pixels,ip.getWidth(),ip.getHeight());

			//int x = e.getX();
			//int y = e.getY();
			//int offscreenX = canvas.offScreenX(x);
			//int offscreenY = canvas.offScreenY(y);
			//IJ.write("mousePressed: "+offscreenX+","+offscreenY);
			//System.out.println("mousePressed: "+offscreenX+","+offscreenY);
			
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
				
	}

	public void mousePressed(MouseEvent e) {
		if (state==IDLE){
			dj.setPoint(e.getX(),e.getY());	
			IJ.write("Dijkstra Point Set");
			state = WIRE;
		}
		else if(state == WIRE){
			state = IDLE;
		}
		
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


	public void mouseDragged(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}


	public void mouseMoved(MouseEvent e) {
		// TODO Auto-generated method stub
	    //		IJ.write("Mouse moving with x at " + e.getX());
		if(state==WIRE){
			int[] vx = new int[width*height];
			int[] vy = new int[width*height];
			int[] size = new int[1];
			dj.returnPath(e.getX(),e.getY(),vx,vy,size);
			/*			for(int i=0;i< size[0];i++){
				IJ.write(i+ ": X " + vx[i]+" Y "+ vy[i]);
				}*/
			Polygon p = new Polygon(vx,vy,size[0]);				
			PolygonRoi a = new PolygonRoi(p,Roi.FREELINE);		
			img.setRoi(a);
		}
		
		
	}

}
