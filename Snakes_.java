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
		byte[] pixels = (byte[]) ip.getPixels();
	        dj = new Dijkstraheap (pixels,ip.getWidth(),ip.getHeight());
		

			//int x = e.getX();
			//int y = e.getY();
			//int offscreenX = canvas.offScreenX(x);
			//int offscreenY = canvas.offScreenY(y);
			//IJ.write("mousePressed: "+offscreenX+","+offscreenY);
			//System.out.println("mousePressed: "+offscreenX+","+offscreenY);



		//Test SOBEL
		//create laplacian zero crossing function		
		//lapzero = img.createImagePlus();
		ImagePlus nova = NewImage.createByteImage("Baggio - Sobel GradientX",ip.getWidth(),ip.getHeight(),1,NewImage.FILL_WHITE);
		ImageProcessor newImage = nova.getProcessor();

		double[] myxgradient = new double[height*width];

		dj.getGradientX(myxgradient);
		
		double gxmin = myxgradient[0];
		double gxmax = myxgradient[0];
		for(int i=0;i< height*width;i++){
		    if(myxgradient[i]<gxmin) gxmin=myxgradient[i];
		    if(myxgradient[i]>gxmax) gxmax=myxgradient[i];
		}

		byte[] newPixels = (byte[]) newImage.getPixels();
		for (int y = 0; y < height; y++) {
		    int offset = y * width;
		    for (int x = 0; x < width; x++) {
			int i = offset + x;
			newPixels[i] = (byte)(Math.round((float)(255*((myxgradient[i]-gxmin)/(gxmax-gxmin)))));
			//			System.out.print(myxgradient[i] + " ");
		    }
		    //System.out.println("");	
		}
		nova.show();
		nova.updateAndDraw();

		ImagePlus nova1 = NewImage.createByteImage("Baggio - Sobel GradientY",ip.getWidth(),ip.getHeight(),1,NewImage.FILL_WHITE);
		ImageProcessor newImage1 = nova1.getProcessor();

		double[] myygradient = new double[height*width];

		dj.getGradientY(myygradient);
		
		double gymin = myygradient[0];
		double gymax = myygradient[0];
		for(int i=0;i< height*width;i++){
		    if(myygradient[i]<gymin) gymin=myygradient[i];
		    if(myygradient[i]>gymax) gymax=myygradient[i];
		}

		byte[] newPixels1 = (byte[]) newImage1.getPixels();
		for (int y = 0; y < height; y++) {
		    int offset = y * width;
		    for (int x = 0; x < width; x++) {
			int i = offset + x;
			newPixels1[i] = (byte)(Math.round((float)(255*((myygradient[i]-gymin)/(gymax-gymin)))));
			//			System.out.print(myxgradient[i] + " ");
		    }
		    //System.out.println("");	
		}
		nova1.show();
		nova1.updateAndDraw();

		ImagePlus nova2 = NewImage.createByteImage("Baggio - Sobel Gradient Resultant",ip.getWidth(),ip.getHeight(),1,NewImage.FILL_WHITE);
		ImageProcessor newImage2 = nova2.getProcessor();

		double[] myrgradient = new double[height*width];

		dj.getGradientR(myrgradient);
		
		double grmin = myrgradient[0];
		double grmax = myrgradient[0];
		for(int i=0;i< height*width;i++){
		    if(myrgradient[i]<grmin) grmin=myrgradient[i];
		    if(myrgradient[i]>grmax) grmax=myrgradient[i];
		}

		byte[] newPixels2 = (byte[]) newImage2.getPixels();
		for (int y = 0; y < height; y++) {
		    int offset = y * width;
		    for (int x = 0; x < width; x++) {
			int i = offset + x;
			newPixels2[i] = (byte)(Math.round((float)(255*((myrgradient[i]-grmin)/(grmax-grmin)))));
			//			System.out.print(myxgradient[i] + " ");
		    }
		    //System.out.println("");	
		}
		nova2.show();
		nova2.updateAndDraw();

		ImagePlus nova3 = NewImage.createByteImage("Baggio - Laplacian zero crossing",ip.getWidth(),ip.getHeight(),1,NewImage.FILL_WHITE);
		ImageProcessor newImage3 = nova3.getProcessor();

		byte[] newPixels3 = (byte[]) newImage3.getPixels();
		for (int y = 0; y < height; y++) {
		    int offset = y * width;
		    for (int x = 0; x < width; x++) {
			int i = offset + x;
			//IT WOULD BE A GOOD IDEA TO PUT A GAUSSIAN HERE, RIGHT?
			if( myrgradient[i]< 20)
			    newPixels3[i] = 0;
			else
			    newPixels3[i] = (byte)255;
			//			System.out.print(myxgradient[i] + " ");
		    }
		    //System.out.println("");	
		}
		nova3.show();
		nova3.updateAndDraw();

			
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
