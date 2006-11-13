import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Macro;
import ij.WindowManager;
import ij.gui.ERoi;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.Line;
import ij.gui.NewImage;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.StackWindow;
import ij.gui.Toolbar;
import ij.measure.Calibration;
import ij.plugin.filter.Duplicater;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;

public class IVUS_ implements PlugInFilter, MouseListener, MouseMotionListener {
    final int IDLE   = 0;
    final int WIRE   = 1;
    final int HANDLE = 2;
    
    int myHandle;
    
    ImagePlus img;
    ImageCanvas canvas;
    int width, height;
    int state;
    ImagePlus lapzero;//image to visualize zero crossing laplacian
    Toolbar oldToolbar;
    static int LiveWireId;//id to hold new tool so that we won't select other tools
    
    
	int[] selx; //selection x points
	int[] sely; //selection y points
	int selSize;//selection size
	ERoi pRoi;//selection Polygon
	int[] tempx; //temporary selection x points
	int[] tempy; //temporary selection y points
	int tempSize; //temporary selection size
	
	int dijX;// temporary value for Dijkstra, to check if path is done
	int dijY;// temporary value for Dijkstra, to check if path is done
	
	//Window related
	JFrame frame;
	
    
    
    double gw;//magnitude weight
    double dw;//direction weight
    double ew;//exponential weight
    double pw;//exponential potence weight
    
    ArrayList<Point> anchor;//stores anchor points
    ArrayList<Integer> selIndex;//stores selection index to create new anchors in 
                                //between points and move them
    
    //IVUS variables
    ImageWindow iwStack; //window with stack images
    ImageWindow iwNS; //north south longitudinal slice
    ImageWindow iwWE; //west south longitudinal slice
    ImageWindow iwNESW; //NE->SW longitudinal slice
    ImageWindow iwNWSE; //NW->SE longitudinal slice
    int[] pN; //y coord of North Points for each z
    int[] pS; //y coord of South Points for each z
    int[] pE; //x coord of East  Points for each z
    int[] pW; //x coord of West  Points for each z
    int[] pNE; //diagonal points
    int[] pSE; //diagonal points
    int[] pSW; //diagonal points
    int[] pNW; //diagonal points
    int[] tx;//stores temporary selection x points
    int[] ty;//stores temporary selection y points
	PolygonRoi tRoi;
	Polygon tp;
	int count = 0;
    int numCuts;
    
    byte[] pixels;//image pixels
    Dijkstraheap dj;
    
    boolean fourSlices;//flag to turn on 4 slices
    
    

	public int setup(String arg, ImagePlus imp) {		
		this.img = imp;
		if (arg.equals("about")) {
		    showAbout();
		    return DONE;
		}					
		return DOES_ALL;//+DOES_STACKS+SUPPORTS_MASKING;//DOES_8G+DOES_STACKS+SUPPORTS_MASKING;
	}

	
	public void run(ImageProcessor ip) {
		
		//check IJ version
		if (IJ.versionLessThan("1.37r")) {
			IJ.showMessage("This plugin might not work with older versions of ImageJ\n"+
					"You should update your ImageJ to at least 1.37r\n" +
					"It also requires Java 1.5 \n" +
					"Just visit http://rsb.info.nih.gov/ij/upgrade/ and " +
					"download the ij.jar file"
					);			
		}
		
		//		test Macro
		// make sure we grab the parameters before any other macro is run, 
		// like the one that converts to grayscale
		String arg = Macro.getOptions();	    	
		
		initialize(ip);						

	    //remove old mouse listeners
	    ImageWindow win = img.getWindow();
	    canvas = win.getCanvas();

	    canvas.addMouseListener(this);
	    canvas.addMouseMotionListener(this);
		
	    oldToolbar = Toolbar.getInstance();
	    //this will avoid multiple plugins buttons
	    //thanks to Wayne Rasband for the hint
	    if(LiveWireId==0){
		LiveWireId = oldToolbar.addTool("Live Wire-C090T0f15LC00aT5f15w");
		if(LiveWireId==-1){
		    IJ.error("Toolbar is full");
		    return; 
		}		
	    }
	    oldToolbar.setTool(LiveWireId);
	    
	    //HERE THE TOOL STARTS 
	    //NEED TO CLEAN UP CODE ABOVE
	    	    	    	   
	    
	    makeCuts();
	    
	    numCuts = calcNumCuts();
	    //initialize points
	    pS = new int[numCuts];
	    pN = new int[numCuts];
	    pW = new int[numCuts];
	    pE = new int[numCuts];
	    pNE = new int[numCuts];
	    pSE = new int[numCuts];
	    pSW = new int[numCuts];
	    pNW = new int[numCuts];
	    for(int i=0;i<numCuts;i++){
	    	pS[i]=0;
		    pN [i]=0;
		    pW [i]=0;
		    pE [i]=0;
		    pNE [i]=0;
		    pSE [i]=0;
		    pSW [i]=0;
		    pNW [i]=0;
	    }
	    	
	    
	    fourSlices = false;
	    
	    
	    
	    createWindow();
	    
	    
	    
	    //initialize(WindowManager.getCurrentImage().getProcessor());
	    
	    
	    /* code to grab roi 
	    try {
			Thread.sleep(30000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	    Roi aRoi = WindowManager.getCurrentImage().getRoi();
	    PolygonRoi pRoi = (PolygonRoi)aRoi;
	    
	    if(pRoi!=null){
	    	Polygon p = pRoi.getPolygon();
	    	System.out.println("Polygon Points");
	    	for(int i=0;i<p.npoints;i++){
	    		System.out.println("x " + p.xpoints[i] + " y " + p.ypoints[i] );
	    	}
	    }	    
	    WindowManager.setCurrentWindow(iwStack);	    
	    IJ.makeLine(0,height/2,width,height/2);
	    */	    
	
	}		

	private int calcNumCuts() {
		// calculate the number of Slices
		// pay attention if DICOM is used		
		ImageStack is = iwStack.getImagePlus().getImageStack();		
		return is.getSize();
	}


	private void makeCuts() {		
		
		iwStack = WindowManager.getCurrentWindow();
		
		Calibration cal = iwStack.getImagePlus().getCalibration();
		
		
	    //North-South slice
        IJ.makeLine(width/2,0,width/2,height);
	    IJ.run("Reslice [/]...", "input="+ cal.pixelWidth + " output="+cal.pixelWidth + " slice=1 rotate");	    
	    iwNS = WindowManager.getCurrentWindow();
	    iwNS.getImagePlus().setTitle("NS Slice");
	    
	    //West East
	    WindowManager.setCurrentWindow(iwStack);
	    IJ.makeLine(0,height/2,width,height/2);
	    IJ.run("Reslice [/]...", "input="+ cal.pixelWidth + " output="+cal.pixelWidth + " slice=1 rotate");
	    
	    iwWE = WindowManager.getCurrentWindow();
	    iwWE.getImagePlus().setTitle("WE Slice");
	    
	    //	 NE SW
	    WindowManager.setCurrentWindow(iwStack);
	    IJ.makeLine(width,0,0,height);
	    IJ.run("Reslice [/]...", "input="+ cal.pixelWidth + " output="+cal.pixelWidth + " slice=1 rotate");
	    
	    iwNESW = WindowManager.getCurrentWindow();
	    iwNESW.getImagePlus().setTitle("NE - SW Slice");
	    
	    //   NW SE
	    WindowManager.setCurrentWindow(iwStack);
	    IJ.makeLine(0,0,width,height);
	    IJ.run("Reslice [/]...", "input="+ cal.pixelWidth + " output="+cal.pixelWidth + " slice=1 rotate");
	    
	    iwNWSE = WindowManager.getCurrentWindow();
	    iwNWSE.getImagePlus().setTitle("NW - SE Slice");
	    
	    
	    //kill Roi	    
	    iwStack.getImagePlus().killRoi();
	    
	    
	}


	void showAbout() {
	    IJ.showMessage("About LiveWire_...",
	    "This sample plugin segments all non-stack images and needs \n" +
	    "Java 1.5. For more information look at the following page\n" +
	    " http://ivussnakes.sourceforge.net/ for more info"

	    );
	}
	
	void createWindow(){

		frame = new JFrame("IVUS Segmentation Window");
		frame.setSize( 400, 400 );
    	frame.setLocation(400, 200);
	
		final javax.swing.JButton bMarkUpNS;		
			    	    
        bMarkUpNS = new javax.swing.JButton();        
        bMarkUpNS.setActionCommand("Mark NS upper points");
        bMarkUpNS.setText("Mark NS upper points");
        bMarkUpNS.addActionListener( new ActionListener() {
    		public void actionPerformed( ActionEvent e ) {
    			//set North South slice window
    			WindowManager.setCurrentWindow(iwNS);
    		    Roi aRoi = WindowManager.getCurrentImage().getRoi();
    		    PolygonRoi pRoi = (PolygonRoi)aRoi;    		    
    		    if(pRoi!=null){
    		    	Polygon p = pRoi.getPolygon();
    		    	//System.out.println("Polygon Points");
    		    	for(int i=0;i<p.npoints;i++){
    		    		//System.out.println("UP NS x " + p.xpoints[i] + " y " + p.ypoints[i] );
    		    		pN[p.xpoints[i]]=p.ypoints[i];    		    		
    		    	}
    		    }
    		    //bMarkUpNS.setEnabled(false);
    		}
    	} );
        
        final javax.swing.JButton bMarkDownNS;		
	    
        bMarkDownNS = new javax.swing.JButton();        
        bMarkDownNS.setActionCommand("Mark NS lower points");
        bMarkDownNS.setText("Mark NS lower points");
        bMarkDownNS.addActionListener( new ActionListener() {
    		public void actionPerformed( ActionEvent e ) {
    			//set North South slice window
    			WindowManager.setCurrentWindow(iwNS);
    		    Roi aRoi = WindowManager.getCurrentImage().getRoi();
    		    PolygonRoi pRoi = (PolygonRoi)aRoi;    		    
    		    if(pRoi!=null){
    		    	Polygon p = pRoi.getPolygon();
    		    	//System.out.println("Polygon Points");
    		    	for(int i=0;i<p.npoints;i++){
    		    		//System.out.println("DOWN NS x " + p.xpoints[i] + " y " + p.ypoints[i] );
    		    		pS[p.xpoints[i]]=p.ypoints[i];
    		    	}
    		    }
    		    //bMarkDownNS.setEnabled(false);
    		}
    	} );
        
        final javax.swing.JButton bMarkDownWE;		
	    
        bMarkDownWE = new javax.swing.JButton();        
        bMarkDownWE.setActionCommand("Mark WE lower points");
        bMarkDownWE.setText("Mark WE lower points");
        bMarkDownWE.addActionListener( new ActionListener() {
    		public void actionPerformed( ActionEvent e ) {
    			//set North South slice window
    			WindowManager.setCurrentWindow(iwWE);
    		    Roi aRoi = WindowManager.getCurrentImage().getRoi();
    		    PolygonRoi pRoi = (PolygonRoi)aRoi;    		    
    		    if(pRoi!=null){
    		    	Polygon p = pRoi.getPolygon();
    		    	//System.out.println("Polygon Points");
    		    	for(int i=0;i<p.npoints;i++){
    		    		//System.out.println("DOWN WE x " + p.xpoints[i] + " y " + p.ypoints[i] );
    		    		//down points here are East points
    		    		pE[p.xpoints[i]]=p.ypoints[i];
    		    	}
    		    }
    		    //bMarkDownWE.setEnabled(false);
    		}
    	} );
        
        final javax.swing.JButton bMarkUpWE;		
	    
        bMarkUpWE = new javax.swing.JButton();        
        bMarkUpWE.setActionCommand("Mark WE upper points");
        bMarkUpWE.setText("Mark WE upper points");        
        bMarkUpWE.addActionListener( new ActionListener() {
    		public void actionPerformed( ActionEvent e ) {
    			//set North South slice window
    			WindowManager.setCurrentWindow(iwWE);
    		    Roi aRoi = WindowManager.getCurrentImage().getRoi();
    		    PolygonRoi pRoi = (PolygonRoi)aRoi;    		    
    		    if(pRoi!=null){
    		    	Polygon p = pRoi.getPolygon();
    		    	//System.out.println("Polygon Points");
    		    	for(int i=0;i<p.npoints;i++){
    		    		//System.out.println("UP WE x " + p.xpoints[i] + " y " + p.ypoints[i] );
    		    		//upper points here are West
    		    		pW[p.xpoints[i]]=p.ypoints[i];
    		    	}
    		    }    			    			
    		    //bMarkUpWE.setEnabled(false);
    		}
    	} );
        
        final javax.swing.JButton bMarkUpNESW;		
	    
        bMarkUpNESW = new javax.swing.JButton();        
        bMarkUpNESW.setActionCommand("Mark NE-SW upper points");
        bMarkUpNESW.setText("Mark NE-SW upper points");
        bMarkUpNESW.addActionListener( new ActionListener() {
    		public void actionPerformed( ActionEvent e ) {
    			//set North Eastern South Western slice window
    			WindowManager.setCurrentWindow(iwNESW);
    		    Roi aRoi = WindowManager.getCurrentImage().getRoi();
    		    PolygonRoi pRoi = (PolygonRoi)aRoi;    		    
    		    if(pRoi!=null){
    		    	Polygon p = pRoi.getPolygon();
    		    	//System.out.println("Polygon Points");
    		    	for(int i=0;i<p.npoints;i++){
    		    		//System.out.println("UP NS x " + p.xpoints[i] + " y " + p.ypoints[i] );
    		    		pNE[p.xpoints[i]]=p.ypoints[i];    		    		
    		    	}
    		    }
    		    fourSlices = true;
    		    //bMarkUpNESW.setEnabled(false);
    		}
    	} );
        
        
        final javax.swing.JButton bMarkDownNESW;		
	    
        bMarkDownNESW = new javax.swing.JButton();        
        bMarkDownNESW.setActionCommand("Mark NE-SW lower points");
        bMarkDownNESW.setText("Mark NE-SW lower points");
        bMarkDownNESW.addActionListener( new ActionListener() {
    		public void actionPerformed( ActionEvent e ) {
    			//set North Eastern South Western slice window
    			WindowManager.setCurrentWindow(iwNESW);
    		    Roi aRoi = WindowManager.getCurrentImage().getRoi();
    		    PolygonRoi pRoi = (PolygonRoi)aRoi;    		    
    		    if(pRoi!=null){
    		    	Polygon p = pRoi.getPolygon();
    		    	//System.out.println("Polygon Points");
    		    	for(int i=0;i<p.npoints;i++){
    		    		//System.out.println("UP NS x " + p.xpoints[i] + " y " + p.ypoints[i] );
    		    		pSW[p.xpoints[i]]=p.ypoints[i];    		    		
    		    	}
    		    }
    		    fourSlices = true;
    		    //bMarkUpNESW.setEnabled(false);
    		}
    	} );
        
        final javax.swing.JButton bMarkUpNWSE;		
	    
        bMarkUpNWSE = new javax.swing.JButton();        
        bMarkUpNWSE.setActionCommand("Mark NW-SE upper points");
        bMarkUpNWSE.setText("Mark NW-SE upper points");
        bMarkUpNWSE.addActionListener( new ActionListener() {
    		public void actionPerformed( ActionEvent e ) {
    			//set North Eastern South Western slice window
    			WindowManager.setCurrentWindow(iwNWSE);
    		    Roi aRoi = WindowManager.getCurrentImage().getRoi();
    		    PolygonRoi pRoi = (PolygonRoi)aRoi;    		    
    		    if(pRoi!=null){
    		    	Polygon p = pRoi.getPolygon();
    		    	//System.out.println("Polygon Points");
    		    	for(int i=0;i<p.npoints;i++){
    		    		//System.out.println("UP NS x " + p.xpoints[i] + " y " + p.ypoints[i] );
    		    		pNW[p.xpoints[i]]=p.ypoints[i];    		    		
    		    	}
    		    }
    		    fourSlices = true;
    		    //bMarkUpNESW.setEnabled(false);
    		}
    	} );
        
        
        final javax.swing.JButton bMarkDownNWSE;		
	    
        bMarkDownNWSE = new javax.swing.JButton();        
        bMarkDownNWSE.setActionCommand("Mark NW-SE lower points");
        bMarkDownNWSE.setText("Mark NW-SE lower points");
        bMarkDownNWSE.addActionListener( new ActionListener() {
    		public void actionPerformed( ActionEvent e ) {
    			//set North Eastern South Western slice window
    			WindowManager.setCurrentWindow(iwNWSE);
    		    Roi aRoi = WindowManager.getCurrentImage().getRoi();
    		    PolygonRoi pRoi = (PolygonRoi)aRoi;    		    
    		    if(pRoi!=null){
    		    	Polygon p = pRoi.getPolygon();
    		    	//System.out.println("Polygon Points");
    		    	for(int i=0;i<p.npoints;i++){
    		    		//System.out.println("UP NS x " + p.xpoints[i] + " y " + p.ypoints[i] );
    		    		pSE[p.xpoints[i]]=p.ypoints[i];    		    		
    		    	}
    		    }
    		    fourSlices = true;
    		    //bMarkUpNESW.setEnabled(false);
    		}
    	} );                
                
        final javax.swing.JButton bSelect; //this button commits the selection		
	    
        bSelect = new javax.swing.JButton();        
        bSelect.setActionCommand("Make Rectangle Selection");
        bSelect.setText("Make Rectangle Selection");
        bSelect.addActionListener( new ActionListener() {
    		public void actionPerformed( ActionEvent e ) {
    			makeSelection();    			    			    		    			
    		}
			
    	} );
        
        final javax.swing.JButton bPolygon; //this button commits the selection		
	    
        bPolygon = new javax.swing.JButton();        
        bPolygon.setActionCommand("Make Polygon Selection");
        bPolygon.setText("Make Polygon Selection");
        bPolygon.addActionListener( new ActionListener() {
    		public void actionPerformed( ActionEvent e ) {
    			makePolygonSelection();    			    			    		    			
    		}			
    	} );
        
        final javax.swing.JButton bLiveSelect; //this button commits the selection		
	    
        bLiveSelect = new javax.swing.JButton();        
        bLiveSelect.setActionCommand("Make LiveWire Selection");
        bLiveSelect.setText("Make LiveWire Selection");
        bLiveSelect.addActionListener( new ActionListener() {
    		public void actionPerformed( ActionEvent e ) {
    			makeLiveSelection();    			    			    		    			

    		}		
			
    	} );
        
        final javax.swing.JButton bSnakeSelect; //this button commits the selection		
	    
        bSnakeSelect = new javax.swing.JButton();        
        bSnakeSelect.setActionCommand("Make Snake Selection");
        bSnakeSelect.setText("Make Snake Selection");
        bSnakeSelect.addActionListener( new ActionListener() {
    		public void actionPerformed( ActionEvent e ) {
    			makeSnakeSelection();    			    			    		    			

    		}		
			
    	} );
        
        
        
        final javax.swing.JButton bPlay; //this button plays the selection		
	    
        bPlay = new javax.swing.JButton();        
        bPlay.setActionCommand("Play Selection");
        bPlay.setText("Play Selection");
        bPlay.addActionListener( new ActionListener() {
    		public void actionPerformed( ActionEvent e ) {    	    			    	
    			showSelections();    			    			    			
    		}
			
    	} );

        
        final javax.swing.JButton bMeasure; //this button measures the selection		
	    
        bMeasure = new javax.swing.JButton();
        bMeasure.setMaximumSize(null);
        bMeasure.setActionCommand("Make Measures");
        bMeasure.setText("Make Measures");
        bMeasure.addActionListener( new ActionListener() {
    		public void actionPerformed( ActionEvent e ) {    	    			    	
    			makeMeasures();    			    			    			
    		}
			
    	} );
        
        Box box = Box.createVerticalBox();                
        
        //bMeasure.setPreferredSize(new Dimension(10,100));
        
        frame.getContentPane().setLayout( new BoxLayout( frame.getContentPane(), BoxLayout.PAGE_AXIS ) );        
        box.add(bMarkUpNS);
        box.add(bMarkDownNS);
        box.add(bMarkUpWE);
        box.add(bMarkDownWE);
        box.add(bMarkUpNESW);
        box.add(bMarkDownNESW);
        box.add(bMarkUpNWSE);
        box.add(bMarkDownNWSE);        
        box.add(bSelect);
        box.add(bPolygon);
        box.add(bLiveSelect);
        box.add(bSnakeSelect);        
        box.add(bPlay);
        box.add(bMeasure);
        frame.getContentPane().add(box,BorderLayout.PAGE_END);        
        frame.pack();
        frame.setVisible(true);        
	}
	
	/**
	 * Makes selections based on the points collect from Mark points buttons
	 * They will be drawn clockwise and then added to ROI Manager 
	 * 
	 * @author baggio
	 */
	private void makeSelection() {
		WindowManager.setCurrentWindow(iwStack);
		StackWindow sw = (StackWindow)iwStack;
		ImageStack stack = iwStack.getImagePlus().getStack();
		
		for(int i=0;i<stack.getSize();i++){
			sw.showSlice(i+1);
			Roi aRoi = new Roi(pW[i], pN[i], pE[i]-pW[i],pS[i]-pN[i]);
			sw.getImagePlus().setRoi(aRoi);
			IJ.run("Add to Manager ");
			
		}		
		
	}
	/**
	 * Same as makeSelection, but this time, uses LiveWire to join points clockwise
	 *
	 */
	private void makeLiveSelection() {	
		(new Thread(){ public void run(){
		WindowManager.setCurrentWindow(iwStack);
		StackWindow sw = (StackWindow)iwStack;
		ImageStack stack = iwStack.getImagePlus().getStack();
		
		for(int i=0;i<stack.getSize();i++){
			sw.showSlice(i+1);
			ImagePlus myIp = sw.getImagePlus();
			ImageProcessor myIpr = myIp.getProcessor();			
			//arrays to store selections
			tx = new int[height*width];
			ty = new int[height*width];
			count = 0;
								
			//North to East
			if(fourSlices==false){
				IJ.run ("LiveWire", "x0="+ (width/2) + " y0=" + pN[i] + " x1=" + pE[i] + " y1=" +(height/2)+ " magnitude=43 direction=13 exponential=0 power=10");
				storePoints();
			}
			else{
				//North to North Eastern
				double Theta = Math.atan(height/width);
				int x = (width-1) - (int)(Math.round(pNE[i]*Math.cos(Theta)));
				int y = (int)(Math.round(pNE[i]*Math.sin(Theta)));				
				IJ.run ("LiveWire", "x0="+ (width/2) + " y0=" + pN[i] + " x1=" + x + " y1=" + y + " magnitude=43 direction=13 exponential=0 power=10");
				storePoints();
				//Noth Eastern to East
				IJ.run ("LiveWire", "x0="+ x + " y0=" + y + " x1=" + pE[i] + " y1=" +(height/2)+ " magnitude=43 direction=13 exponential=0 power=10");
				storePoints();
			}
			
			if(fourSlices==false){
				//East to South
				IJ.run ("LiveWire", "x0="+ pE[i] + " y0=" + (height/2) + " x1=" + (width/2) + " y1=" +pS[i]+ " magnitude=43 direction=13 exponential=0 power=10");
				storePoints();
			}
			else{				
				double Theta = Math.atan(height/width);
				int x = (int)(Math.round(pSE[i]*Math.cos(Theta)));
				int y = (int)(Math.round(pSE[i]*Math.sin(Theta)));
				//East to SE
				IJ.run ("LiveWire", "x0="+ pE[i] + " y0=" + (height/2) + " x1=" + x + " y1=" + y + " magnitude=43 direction=13 exponential=0 power=10");
				storePoints();
				IJ.run ("LiveWire", "x0="+ x + " y0=" + y + " x1=" + (width/2) + " y1=" +pS[i]+ " magnitude=43 direction=13 exponential=0 power=10");				
				storePoints();
			}
						
			if(fourSlices==false){
				//South to West
				IJ.run ("LiveWire", "x0="+ (width/2) + " y0=" + pS[i] + " x1=" + pW[i] + " y1=" + (height/2) + " magnitude=43 direction=13 exponential=0 power=10");				
				storePoints();	
				
			}
			else{
				double Theta = Math.atan(height/width);
				int x = (width-1) - (int)(Math.round(pSW[i]*Math.cos(Theta)));
				int y = (int)(Math.round(pSW[i]*Math.sin(Theta)));
				//South to SW
				IJ.run ("LiveWire", "x0="+ (width/2) + " y0=" + pS[i] + " x1=" + x + " y1=" + y + " magnitude=43 direction=13 exponential=0 power=10");				
				storePoints();
				
				IJ.run ("LiveWire", "x0="+ x + " y0=" + y + " x1=" + pW[i] + " y1=" + (height/2) + " magnitude=43 direction=13 exponential=0 power=10");
				storePoints();							
			}
												
			if(fourSlices==false){			
				//West to North
				IJ.run ("LiveWire", "x0="+ pW[i] + " y0=" + (height/2) + " x1=" + (width/2) + " y1=" + pN[i] + " magnitude=43 direction=13 exponential=0 power=10");						
				storePoints();
			}
			else{
				//West to NW
				double Theta = Math.atan(height/width);
				int x = (int)(Math.round(pNW[i]*Math.cos(Theta)));
				int y = (int)(Math.round(pNW[i]*Math.sin(Theta)));				
				
				IJ.run ("LiveWire", "x0="+ pW[i] + " y0=" + (height/2) + " x1=" + x + " y1=" + y + " magnitude=43 direction=13 exponential=0 power=10");						
				storePoints();
				
				IJ.run ("LiveWire", "x0="+ x + " y0=" + y + " x1=" + (width/2) + " y1=" + pN[i] + " magnitude=43 direction=13 exponential=0 power=10");						
				storePoints();				
				
			}			
			Polygon p = new Polygon(tx,ty,count);			
			PolygonRoi finalRoi = new PolygonRoi(p,Roi.TRACED_ROI);
			sw.getImagePlus().setRoi(finalRoi);
			
			
			
			
			
			//Roi aRoi = new Roi(pW[i], pN[i], pE[i]-pW[i],pS[i]-pN[i]);
			//sw.getImagePlus().setRoi(aRoi);
			IJ.run("Add to Manager ");			
		}
		}

		}).start();		
	}
	
	
	/**
	 * Same as makeSelection, but this time, uses LiveWire to join points clockwise
	 *
	 */
	private void makeSnakeSelection() {	
		(new Thread(){ public void run(){
		WindowManager.setCurrentWindow(iwStack);
		StackWindow sw = (StackWindow)iwStack;
		ImageStack stack = iwStack.getImagePlus().getStack();
        
		//duplicates images to apply filters
		Duplicater duplicater = new Duplicater();
        ImagePlus edgeImage = duplicater.duplicateStack(sw.getImagePlus(), sw.getImagePlus().getTitle() + " - Edge");
		
		for(int i=0;i<stack.getSize();i++){
			System.out.println("time "+i);
			sw.showSlice(i+1);
			ImagePlus myIp = sw.getImagePlus();
			ImageProcessor myIpr = myIp.getProcessor();
			
			//arrays to store selections
			tx = new int[height*width];
			ty = new int[height*width];
			count = 0;
								
			//North to East
			if(fourSlices==false){
				IJ.makeLine((width/2),pN[i], pE[i],(height/2));				
				storeLinePoints();
			}
			else{
				//North to North Eastern
				double Theta = Math.atan(height/width);
				int x = (width-1) - (int)(Math.round(pNE[i]*Math.cos(Theta)));
				int y = (int)(Math.round(pNE[i]*Math.sin(Theta)));
				IJ.makeLine( (width/2) , pN[i] , x , y);				
				storeLinePoints();
				//Noth Eastern to East
				IJ.makeLine(x , y , pE[i], (height/2));				
				storeLinePoints();
			}
			
			if(fourSlices==false){
				//East to South
				IJ.makeLine( pE[i] , (height/2), (width/2), pS[i]);				
				storeLinePoints();
			}
			else{				
				double Theta = Math.atan(height/width);
				int x = (int)(Math.round(pSE[i]*Math.cos(Theta)));
				int y = (int)(Math.round(pSE[i]*Math.sin(Theta)));
				//East to SE
				IJ.makeLine( pE[i], (height/2), x , y );				
				storeLinePoints();
				IJ.makeLine(x , y , (width/2) , pS[i]);							
				storeLinePoints();
			}
						
			if(fourSlices==false){
				//South to West
				IJ.makeLine((width/2) ,  pS[i] , pW[i] , (height/2));							
				storeLinePoints();	
				
			}
			else{
				double Theta = Math.atan(height/width);
				int x = (width-1) - (int)(Math.round(pSW[i]*Math.cos(Theta)));
				int y = (int)(Math.round(pSW[i]*Math.sin(Theta)));
				//South to SW
				IJ.makeLine((width/2), pS[i] , x , y);							
				storeLinePoints();
				
				IJ.makeLine( x , y , pW[i] , (height/2) );				
				storeLinePoints();							
			}
												
			if(fourSlices==false){			
				//West to North
				IJ.makeLine(pW[i], (height/2) , (width/2) , pN[i] );									
				//storeLinePoints();
			}
			else{
				//West to NW
				double Theta = Math.atan(height/width);
				int x = (int)(Math.round(pNW[i]*Math.cos(Theta)));
				int y = (int)(Math.round(pNW[i]*Math.sin(Theta)));				
				
				IJ.makeLine(pW[i] , (height/2) , x , y );										
				//storeLinePoints();
				
				IJ.makeLine( x , y , (width/2) , pN[i] );										
				//storeLinePoints();				
				
			}			
			Polygon p = new Polygon(tx,ty,count);			
			//PolygonRoi finalRoi = new PolygonRoi(p,Roi.TRACED_ROI);
			//sw.getImagePlus().setRoi(finalRoi);
			
			//System.out.println("After Polygon");
//			Creates a Snake from polygon selection
			//updates handle squares															
			//KWTSnakes
			//create points
			ArrayList snakePoints = new ArrayList();				
			for(int j=0;j< p.npoints;j++){
				//System.out.println("Adding points to snake " + p.xpoints[j] + ", " + p.ypoints[j]);
				snakePoints.add(new SnakePoint(p.xpoints[j],p.ypoints[j]));
			}
			
			
			//create image
	        BufferedImage   image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);		        		        		                        
	        Graphics g = image.createGraphics();
	        g.drawImage(sw.getImagePlus().getImage(), 0, 0, null);
	        g.dispose();            
	        
			double[] myrgradient = new double[height*width];
			dj.getGradientR(myrgradient);
			
            Roi aRoi = sw.getImagePlus().getRoi();
            sw.getImagePlus().killRoi();            
            edgeImage.setSlice(i+1);
            edgeImage.getStack().getProcessor(i+1).findEdges();
//            WindowManager.setTempCurrentImage(edgeImage);            
            //edgeImage.getProcessor().findEdges();                  
            BufferedImage   potential = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);		        		        		                        
	        Graphics gPot = potential.createGraphics();
	        gPot.drawImage(edgeImage.getProcessor().createImage(),0,0,null);
	        gPot.dispose();
	        //edgeImage.show();
	        //edgeImage.updateAndDraw();
	        /*ImagePlus test = NewImage.createByteImage("Teste"+i,sw.getWidth(),sw.getHeight(),1,NewImage.FILL_WHITE);	        	       	       
	        test.updateAndDraw();
	        test.show();
	        gPot.setColor(Color.RED);
	        gPot.drawRect(0,0,10,20);
	        test.getCanvas().getGraphics().drawLine(0,0,30,30);
	        test.getCanvas().getGraphics().drawImage(potential,0,0,null);*/
	        
	        //test.getWindow().drawInfo(gPot);
	        
	        
	        
	        WindowManager.setCurrentWindow(iwStack);
	        sw.getImagePlus().setRoi(aRoi);
			
			
            //System.out.println("After Duplicator");
			int level = 5;
			SensorSnake snake = new SensorSnake(snakePoints, image,potential,false,level);
	        //KWTSnake snake = new KWTSnake(snakePoints,image,potential,false);
			for(int j=0;j<100;j++){
				snake.deform();
			}
			//System.out.println("After Deforming");
			
				Rectangle rect = new Rectangle(width,height);
				
//				snake.draw( img.getWindow().getGraphics(),1,rect);				
				//img.getCanvas().getGraphics().clearRect(0,0,50,50);
				
				Polygon pSnake = new Polygon();
				//for kwt
				/*for(int j=0;j<snake.points.size();j++){
					
					SnakePoint sp1 = (SnakePoint) snake.points.get(j);
					//System.out.println("Adding point" + sp1.getPos().getX() + ", " + (int) sp1.getPos().getY());
					pSnake.addPoint((int) sp1.getPos().getX(),(int) sp1.getPos().getY());
				}*/
				
				//for sensorsnake
				for(int j=0;j<snake.subdivPoints[level].size();j++){
					
					SnakePoint sp1 = (SnakePoint) snake.subdivPoints[level].get(j);
					//System.out.println("Adding point" + sp1.getPos().getX() + ", " + (int) sp1.getPos().getY());
					pSnake.addPoint((int) sp1.getPos().getX(),(int) sp1.getPos().getY());
				}								

				//handles roi				
				PolygonRoi sRoi = new PolygonRoi(pSnake,Roi.TRACED_ROI);																		
				sw.getImagePlus().setRoi(sRoi);			
			
			
			//Roi aRoi = new Roi(pW[i], pN[i], pE[i]-pW[i],pS[i]-pN[i]);
			//sw.getImagePlus().setRoi(aRoi);
				
			IJ.run("Add to Manager ");
			System.out.println("After Adding to manager");
		}
		}

		}).start();		
	}
	
	private void makePolygonSelection() {	
		(new Thread(){ public void run(){
		WindowManager.setCurrentWindow(iwStack);
		StackWindow sw = (StackWindow)iwStack;
		ImageStack stack = iwStack.getImagePlus().getStack();        		
		
		for(int i=0;i<stack.getSize();i++){
			System.out.println("time "+i);
			sw.showSlice(i+1);
			ImagePlus myIp = sw.getImagePlus();
			ImageProcessor myIpr = myIp.getProcessor();
			
			//arrays to store selections
			tx = new int[height*width];
			ty = new int[height*width];
			count = 0;
								
			//North to East
			if(fourSlices==false){
				IJ.makeLine((width/2),pN[i], pE[i],(height/2));				
				storeLinePoints();
			}
			else{
				//North to North Eastern
				double Theta = Math.atan(height/width);
				int x = (width-1) - (int)(Math.round(pNE[i]*Math.cos(Theta)));
				int y = (int)(Math.round(pNE[i]*Math.sin(Theta)));
				IJ.makeLine( (width/2) , pN[i] , x , y);				
				storeLinePoints();
				//Noth Eastern to East
				IJ.makeLine(x , y , pE[i], (height/2));				
				storeLinePoints();
			}
			
			if(fourSlices==false){
				//East to South
				IJ.makeLine( pE[i] , (height/2), (width/2), pS[i]);				
				storeLinePoints();
			}
			else{				
				double Theta = Math.atan(height/width);
				int x = (int)(Math.round(pSE[i]*Math.cos(Theta)));
				int y = (int)(Math.round(pSE[i]*Math.sin(Theta)));
				//East to SE
				IJ.makeLine( pE[i], (height/2), x , y );				
				storeLinePoints();
				IJ.makeLine(x , y , (width/2) , pS[i]);							
				storeLinePoints();
			}
						
			if(fourSlices==false){
				//South to West
				IJ.makeLine((width/2) ,  pS[i] , pW[i] , (height/2));							
				storeLinePoints();	
				
			}
			else{
				double Theta = Math.atan(height/width);
				int x = (width-1) - (int)(Math.round(pSW[i]*Math.cos(Theta)));
				int y = (int)(Math.round(pSW[i]*Math.sin(Theta)));
				//South to SW
				IJ.makeLine((width/2), pS[i] , x , y);							
				storeLinePoints();
				
				IJ.makeLine( x , y , pW[i] , (height/2) );				
				storeLinePoints();							
			}
												
			if(fourSlices==false){			
				//West to North
				IJ.makeLine(pW[i], (height/2) , (width/2) , pN[i] );									
				storeLinePoints();
			}
			else{
				//West to NW
				double Theta = Math.atan(height/width);
				int x = (int)(Math.round(pNW[i]*Math.cos(Theta)));
				int y = (int)(Math.round(pNW[i]*Math.sin(Theta)));				
				
				IJ.makeLine(pW[i] , (height/2) , x , y );										
				storeLinePoints();
				
				IJ.makeLine( x , y , (width/2) , pN[i] );										
				storeLinePoints();				
				
			}			
			Polygon p = new Polygon(tx,ty,count);			
			PolygonRoi finalRoi = new PolygonRoi(p,Roi.TRACED_ROI);
			sw.getImagePlus().setRoi(finalRoi);							
			IJ.run("Add to Manager ");			
		}
		}

		}).start();		
	}


	
	
	
	//makes the measures
	//stores points to temporary selection 
	private void makeMeasures() {
		//
		(new Thread(){ public void run(){
			WindowManager.setCurrentWindow(iwStack);
			
			StackWindow sw = (StackWindow)iwStack;
			ImageStack stack = iwStack.getImagePlus().getStack();
			ImageWindow iwMask=null;
			ImageWindow iwTemp;
						
			ImageStack resultsTPF = img.createEmptyStack();
			ImageStack resultsFPF = img.createEmptyStack();
			ImageStack resultsFNF = img.createEmptyStack();
			
			ImageProcessor newImage;
			
			//file input output
			
			FileOutputStream out; 
            PrintStream p=null; 
            try{
                // Create a new file output stream
                // connected to "myfile.txt"
                out = new FileOutputStream("results.txt");

                // Connect print stream to the output stream
                p = new PrintStream( out );                
            }
            catch (Exception e)
            {
                    System.err.println ("Error writing to file");
            }
            
            			
			
			
			for(int i=0;i<stack.getSize();i++){
				WindowManager.setCurrentWindow(iwStack);
				sw.showSlice(i+1);
				ImagePlus myIp = sw.getImagePlus();
				ImageProcessor myIpr = myIp.getProcessor();
				
				//clear Mask
				/*if(iwMask!=null){
					WindowManager.setCurrentWindow(iwMask);
					IJ.run("Select All");
					IJ.run("Clear");
					IJ.run("Select None");
				}*/
				
				WindowManager.setCurrentWindow(iwStack);
					
				//loads selection
				IJ.runMacro("roiManager(\"Select\", "+ i +")");
				IJ.run("Create Mask");					
				iwMask = WindowManager.getCurrentWindow();
				WindowManager.setCurrentWindow(iwMask);
				//sets TPF				
				IJ.run("Duplicate...", "title=TPF" +i); //true positive fraction
				iwTemp = WindowManager.getCurrentWindow();
				iwMask.close();
								
				
				
				//select the gold standard
				WindowManager.setCurrentWindow(iwStack);
				IJ.runMacro("roiManager(\"Select\", "+ (i + stack.getSize())+")");								
				
				//create a mask for the goldStandard
				IJ.run("Create Mask");
				iwMask = WindowManager.getCurrentWindow();
				
				//count GoldStandard Area
				int gsArea = 0;
				byte[] pc = (byte[]) (WindowManager.getCurrentImage().getProcessor().getPixels());
				for (int y = 0; y < height; y++) {
				    int offset = y * width;
				    for (int x = 0; x < width; x++) {				    	
				    	int j = offset + x;				    	
				    	if((pc[j]&0xff)==255) //remember that mask is LUT inverted
				    		gsArea++;
				    }				    
				}			
				p.print(i +" GoldStandard Area "+ gsArea + " " );								
				
				IJ.run("Image Calculator...", "image1=Mask operation=AND image2=TPF"+i+" create");
				ImageWindow tmp = WindowManager.getCurrentWindow();
																
				ImagePlus timg = NewImage.createByteImage("Nome",img.getWidth(),img.getHeight(),1,NewImage.FILL_WHITE);
				newImage = timg.getProcessor();
				int tpf = 0;
								
				byte[] newPixels = (byte[]) newImage.getPixels();
				pc = (byte[]) (WindowManager.getCurrentImage().getProcessor().getPixels());
				for (int y = 0; y < height; y++) {
				    int offset = y * width;
				    for (int x = 0; x < width; x++) {				    	
				    	int j = offset + x;
				    	newPixels[j] = (byte) (255 - pc[j]) ;
				    	if((pc[j]&0xff)==255) //remember that mask is LUT inverted
				    		tpf++;
				    }				    
				}			
				/*WindowManager.setTempCurrentImage(timg);
				IJ.run("Make Binary");
				WindowManager.setTempCurrentImage(null);				
				ImageStatistics stats = ImageStatistics.getStatistics(newImage, 0, timg.getCalibration());*/
				p.print ("AreaTPF " + tpf + " ");
				
				
				resultsTPF.addSlice(null,newImage);	
				
				ImagePlus timg1 = NewImage.createByteImage("Nome",img.getWidth(),img.getHeight(),1,NewImage.FILL_WHITE);
				newImage = timg1.getProcessor();
				
				WindowManager.setCurrentWindow(iwMask);
				iwMask.getImagePlus().getProcessor().invert();
				IJ.run("Image Calculator...", "image1=Mask operation=AND image2=TPF"+i+" create");
				ImageWindow tmp1 = WindowManager.getCurrentWindow();
				int fpf = 0;
				pc = (byte[]) (WindowManager.getCurrentImage().getProcessor().getPixels());
				newPixels = (byte[]) newImage.getPixels();
				for (int y = 0; y < height; y++) {
				    int offset = y * width;
				    for (int x = 0; x < width; x++) {				    	
				    	int j = offset + x;				  
				    	newPixels[j] = (byte) (255 - pc[j]) ;
				    	if((pc[j]&0xff)==255) //remember that mask is LUT inverted
				    		fpf++;
				    }				    
				}
				
				resultsFPF.addSlice(null,newImage);
				
				

				iwMask.getImagePlus().getProcessor().invert();//return goldstandard to original
				iwTemp.getImagePlus().getProcessor().invert();//get selection complementary
				
				ImagePlus timg2 = NewImage.createByteImage("Nome",img.getWidth(),img.getHeight(),1,NewImage.FILL_WHITE);
				newImage = timg2.getProcessor();
				IJ.run("Image Calculator...", "image1=Mask operation=AND image2=TPF"+i+" create");
				ImageWindow tmp2 = WindowManager.getCurrentWindow();
				
				int fnf = 0;
				newPixels = (byte[]) newImage.getPixels();
				pc = (byte[]) (WindowManager.getCurrentImage().getProcessor().getPixels());
				for (int y = 0; y < height; y++) {
				    int offset = y * width;
				    for (int x = 0; x < width; x++) {				    	
				    	int j = offset + x;			
				    	newPixels[j] = (byte) (255 - pc[j]) ;
				    	if((pc[j]&0xff)==255) //remember that mask is LUT inverted
				    		fnf++;
				    }				    
				}
				
				resultsFNF.addSlice(null,newImage);
				
				p.print ("AreaFPF " + fpf + " ");
				p.print ("AreaFNF " + fnf + " ");
				
				p.print("TPF % " + (double)(tpf)/gsArea +
						" FPF % " + (double)(fpf)/gsArea + 
						" FNF % " + (double)(fnf)/gsArea );
				
				p.println("");
				tmp.close();
				iwTemp.close();
				iwMask.close();				
				tmp1.close();
				tmp2.close();
									
			}			
			ImagePlus imp1 = new ImagePlus("TPF-"+img.getTitle(), resultsTPF);		
			imp1.show();
			
			ImagePlus imp2 = new ImagePlus("FPF-"+img.getTitle(), resultsFPF);		
			imp2.show();
			
			ImagePlus imp3 = new ImagePlus("FNF-"+img.getTitle(), resultsFNF);		
			imp3.show();
			
            p.close();

			
		}}).start();		
				
	}
	
	
	
	//stores points to temporary selection 
	private void storePoints() {
		//
		WindowManager.setCurrentWindow(iwStack);
		StackWindow sw = (StackWindow)iwStack;
		//ImageStack stack = iwStack.getImagePlus().getStack();
		
		tRoi = (PolygonRoi)sw.getImagePlus().getRoi();
		tp = tRoi.getPolygon();	
		
		for(int j=0;j<tp.npoints;j++){
			tx[count]=tp.xpoints[j];
			ty[count]=tp.ypoints[j];
			count++;
		}

		
	}
	
	//	stores line points to temporary selection 
	private void storeLinePoints() {
		//
		WindowManager.setCurrentWindow(iwStack);
		StackWindow sw = (StackWindow)iwStack;
		//ImageStack stack = iwStack.getImagePlus().getStack();
		
		Line lRoi = (Line)sw.getImagePlus().getRoi();
		tp = lRoi.getPolygon();	
		
		for(int j=0;j<tp.npoints;j++){
			//System.out.println("Storing " + tp.xpoints[j] + " , " + tp.ypoints[j] );
			tx[count]=tp.xpoints[j];
			ty[count]=tp.ypoints[j];
			count++;
		}

		
	}
	
	
	
	public void showSelections(){
		(new Thread(){ public void run(){
			ImageStack stack = iwStack.getImagePlus().getStack();
			for(int i=0;i<stack.getSize();i++){
				RoiManager rm =(RoiManager) WindowManager.getFrame("ROI Manager");
				rm.select(i);			
				WindowManager.setCurrentWindow(iwStack);
				
				//roiManager("Select", i);
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}			
		}}).start();		
	}
	

	public void mouseClicked(MouseEvent e) {
				
	}

	public void mousePressed(MouseEvent e) {
		//if other tool is selected, we should return
		//thanks to Volker Bäcker for the return when spacebar is down!
		if( Toolbar.getToolId() != LiveWireId  || IJ.spaceBarDown())
			return;
		//if zoom mode is working, we should convert x and y coordinates
		int myx = canvas.offScreenX(e.getX());
		int myy = canvas.offScreenY(e.getY());
		
		if(e.getButton()== MouseEvent.BUTTON1){
            if((state == IDLE && selSize==0) || (state==IDLE && IJ.shiftKeyDown())){			
				myHandle = -1;
				if(pRoi!=null)
					myHandle = pRoi.isHandle(myx,myy);
				if(myHandle!=-1){
					state = HANDLE;
					return;
				}
				else{
					//we are going back to segment
					IJ.runMacro("setOption('DisablePopupMenu', true)");
					state = WIRE;
                    
					if(selSize>0){
                        //retrieve last point to Dijkstra
                        dj.setPoint(selx[selSize-1],sely[selSize-1]);
                        return;
                    }
					
					/*if(selSize>0){
						selSize=0;
						tempSize =0;
						anchor.clear();
						selIndex.clear();
					}*/
						/*
					if(selSize>0){
						//retrieve last point to Dijkstra
						dj.setPoint(selx[selSize-1],sely[selSize-1]);
						return;
					}
					*/
				}
			}
            else{
            	//Volker code, to finish old selection
            	if (state==IDLE) {
                    img.killRoi();
                    initialize(img.getProcessor());
                    mousePressed(e); // initialize will set selSize to zero, so this is not an endless-loop
            	}
            }
			
			
			
			
			//be careful, in first time we should not subtract 1
			if(selSize+tempSize==0){
				selIndex.add(selSize+tempSize);
			}
			else{
				selIndex.add(selSize+tempSize-1);
				if(!((dijX==myx)&&(dijY==myy))){
					return;
				}
			}
			anchor.add(new Point(myx,myy));			
			
			//updates handle squares
			Polygon p = new Polygon(selx,sely,selSize+tempSize);
			int[] ax = new int[anchor.size()];
			int[] ay = new int[anchor.size()];
			
			for(int i=0;i<anchor.size();i++){
				ax[i] = (int) ((Point)(anchor.get(i))).getX();
				ay[i] = (int) ((Point)(anchor.get(i))).getY();								
			}
			
			
			Polygon myAnchor = new Polygon(ax,ay,anchor.size());
			pRoi = new ERoi(p,Roi.FREELINE, myAnchor);		
			img.setRoi(pRoi);
			
			
			
			dj.setPoint(myx,myy);
				
			for(int i=0;i<tempSize;i++){
				selx[selSize+i]=tempx[i];
				sely[selSize+i]=tempy[i];					
			}
			selSize+=tempSize;
			tempSize=0;
		}
		else if(e.getButton()== MouseEvent.BUTTON3){			
			if(state == WIRE){
				
				if(!((dijX==myx)&&(dijY==myy))){
					return;
				}
				
				
				
				IJ.runMacro("setOption('DisablePopupMenu', false)");
				state = IDLE;
				
				
				//same thing as in left click
				anchor.add(new Point(myx,myy));
				
				
				selIndex.add(selSize+tempSize-1);				
				
				//updates handle squares
				Polygon p = new Polygon(selx,sely,selSize+tempSize);
				int[] ax = new int[anchor.size()];
				int[] ay = new int[anchor.size()];
				
				for(int i=0;i<anchor.size();i++){
					ax[i] = (int) ((Point)(anchor.get(i))).getX();
					ay[i] = (int) ((Point)(anchor.get(i))).getY();								
				}
				
				
				Polygon myAnchor = new Polygon(ax,ay,anchor.size());
				pRoi = new ERoi(p,Roi.FREELINE, myAnchor);		
				img.setRoi(pRoi);
				
				
				
				dj.setPoint(myx,myy);
					
				for(int i=0;i<tempSize;i++){					
					selx[selSize+i]=tempx[i];
					sely[selSize+i]=tempy[i];					
				}
				selSize+=tempSize;	
				tempSize=0;
				
			}
		}
		
	}

	


	public void mouseReleased(MouseEvent e)  {
		if(state == HANDLE){

			//update selection
			//copy selection from handle 0 to this handle minus 1
			
			int myx = canvas.offScreenX(e.getX());
			int myy = canvas.offScreenY(e.getY());
							
				int[] tselx = new int[height*width];//temporary x selection
				int[] tsely = new int[height*width];//temporary y selection
				int count = 0;
				
				//for handle one, put at least one point, else nothing will appear from the 
				//initial handle to this
/*				if(myHandle==1){
					tselx[count]=selx[0];
					tsely[count]=sely[0];
					count++;					
				}*/
				
				for(int i=0;i<myHandle-1;i++){					
					for(int j=selIndex.get(i);j< selIndex.get(i+1);j++){							
						tselx[count]=selx[j];
						tsely[count]=sely[j];
						count++;
					}											
				}
				//vectors needed to hold dijkstra's result
				int[] ts = new int[1];
				int[] tx = new int[height*width];
				int[] ty = new int[height*width];
				ts[0]=0;
				
				
				//dealing with livewire from handle minus one to this
				if(myHandle>0){
					int previousX = selx[selIndex.get(myHandle-1)];
					int previousY = sely[selIndex.get(myHandle-1)];
					
					dj.setPoint(previousX,previousY);
													
				
									
					//while the path isn't returned
					while(ts[0]==0){
						try {
							Thread.sleep(100);						
						} catch (InterruptedException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
						dj.returnPath(myx,myy,tx,ty,ts);				
					}
					
					for(int i=0;i<ts[0];i++){
						tselx[count]=tx[i];
						tsely[count]=ty[i];
						count++;
					}
				}
					
				selIndex.set(myHandle,count);
				
				//dealing with livewire from this handle to next
				if(myHandle<selIndex.size()-1){										
					int nextX = selx[selIndex.get(myHandle+1)];
					int nextY = sely[selIndex.get(myHandle+1)];					
					
					dj.setPoint(myx,myy);
																	
					ts[0]=0;
									
					//while the path isn't returned
					while(ts[0]==0){
						try {
							Thread.sleep(100);						
						} catch (InterruptedException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
						dj.returnPath(nextX,nextY,tx,ty,ts);				
					}
					
					for(int i=0;i<ts[0];i++){
						tselx[count]=tx[i];
						tsely[count]=ty[i];
						count++;
					}
				}
				

				
				
				for(int i=myHandle+1;i<selIndex.size()-1;i++){
					int initialCount = count;					
					for(int j=selIndex.get(i);j< selIndex.get(i+1);j++){
						tselx[count]=selx[j];
						tsely[count]=sely[j];
						count++;
					}
					selIndex.set(i,initialCount);					
				}
				
				
				
//				for last handle, put at least one point, else nothing will appear from the 
				//initial handle to this
				/*if(myHandle==selIndex.size()-2){
					tselx[count]=selx[selSize-1];
					tsely[count]=sely[selSize-1];
					count++;					
				}*/

				//				updates last selIndex
				if(myHandle < selIndex.size()-1){
					tselx[count]=selx[selSize-1];
					tsely[count]=sely[selSize-1];
				}
				else if( myHandle== selIndex.size()-1){
					//if we are dealing with the last point
					tselx[count]=myx;
					tsely[count]=myy;				
				}
				selIndex.set(selIndex.size()-1,count);												
				
				count++;
				//copies to original selection				
				for(int i=0;i<count;i++){
					selx[i]=tselx[i];
					sely[i]=tsely[i];
				}
				selSize = count;
				
				Polygon p = new Polygon(tselx,tsely,count);
				int[] ax = new int[anchor.size()];
				int[] ay = new int[anchor.size()];
				
				//replace actual point
				anchor.set(myHandle,new Point(myx,myy));
				
				for(int i=0;i<anchor.size();i++){
					ax[i] = (int) ((Point)(anchor.get(i))).getX();
					ay[i] = (int) ((Point)(anchor.get(i))).getY();								
				}
				
				
				Polygon myAnchor = new Polygon(ax,ay,anchor.size());
				pRoi = new ERoi(p,Roi.FREELINE, myAnchor);		
				img.setRoi(pRoi);
				
			state = IDLE;
			myHandle = -1;
			/*
			System.out.println("Debug selSize = " + selSize);
			for(int i=0;i<selSize;i++){
				System.out.println("( "+ selx[i] + " , " + sely[i] + " )");
			}
			for(int i=0;i<selIndex.size();i++){
				System.out.println("selIndex "+i+ ": "+selIndex.get(i));
			}*/
		}		
	}

	public void mouseEntered(MouseEvent e) {
		// TODO Auto-generated method stub

	}

	public void mouseExited(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}


	public void mouseDragged(MouseEvent e) {
		int myx = canvas.offScreenX(e.getX());
		int myy = canvas.offScreenY(e.getY());		
		
		if(state==HANDLE){
			//make new selection, but straight line with next and previous points
			//for a while, until the user releases the button
						
			if((myHandle>=0)){
				
				int[] tselx = new int[height*width];//temporary x selection
				int[] tsely = new int[height*width];//temporary y selection
				int count = 0;
				
				//for handle one, put at least one point, else nothing will appear from the 
				//initial handle to this
				if(myHandle==1){
					tselx[count]=selx[0];
					tsely[count]=sely[0];
					count++;					
				}
				
				for(int i=0;i<myHandle-1;i++){
					
					for(int j=selIndex.get(i);j< selIndex.get(i+1);j++){							
						tselx[count]=selx[j];
						tsely[count]=sely[j];
						count++;
					}											
				}
				tselx[count]=myx;
				tsely[count]=myy;
				count++;
				
				for(int i=myHandle+1;i<selIndex.size()-1;i++){					
					for(int j=selIndex.get(i);j< selIndex.get(i+1);j++){
						
						tselx[count]=selx[j];
						tsely[count]=sely[j];
						count++;
					}
				}
//				for last handle, put at least one point, else nothing will appear from the 
				//initial handle to this
				if(myHandle==selIndex.size()-2){
					tselx[count]=selx[selSize-1];
					tsely[count]=sely[selSize-1];
					count++;					
				}
				
				
				//copies to original selection
				/*
				for(int i=0;i<count;i++){
					selx[i]=tselx[i];
					sely[i]=tsely[i];
				}
				selSize = count;*/
				
				Polygon p = new Polygon(tselx,tsely,count);
				int[] ax = new int[anchor.size()];
				int[] ay = new int[anchor.size()];
				
				//replace actual point
				anchor.set(myHandle,new Point(myx,myy));
				
				for(int i=0;i<anchor.size();i++){
					ax[i] = (int) ((Point)(anchor.get(i))).getX();
					ay[i] = (int) ((Point)(anchor.get(i))).getY();								
				}
				
				
				Polygon myAnchor = new Polygon(ax,ay,anchor.size());
				pRoi = new ERoi(p,Roi.FREELINE, myAnchor);		
				img.setRoi(pRoi);
				
			
			}
						
		}
		
	}


	public void mouseMoved(MouseEvent e) {	
		//if other tool is selected, we should return
		if( Toolbar.getToolId() != LiveWireId )
			return;		

		//if zoom mode is working, we should convert x and y coordinates
		int myx = canvas.offScreenX(e.getX());
		int myy = canvas.offScreenY(e.getY());
					
					
	    //		IJ.write("Mouse moving with x at " + e.getX());
		if(state==WIRE){
			int[] vx = new int[width*height];
			int[] vy = new int[width*height];
			int[] size = new int[1];
			

			dj.returnPath(myx,myy,vx,vy,size);
			/*			for(int i=0;i< size[0];i++){
				IJ.write(i+ ": X " + vx[i]+" Y "+ vy[i]);
				}*/
			//if size>0 we'll update dijX and dijY values, 
			//so that we'll make sure they have been accepted
			if(size[0]>0){
				dijX = myx;
				dijY = myy;
			}
			for(int i=0;i<size[0];i++){
				selx[i+selSize]= vx[i];
				sely[i+selSize]= vy[i];
			}
			tempx = vx;
			tempy = vy;
			tempSize = size[0];
			Polygon p = new Polygon(selx,sely,size[0]+selSize);
			int[] ax = new int[anchor.size()];
			int[] ay = new int[anchor.size()];
			
			for(int i=0;i<anchor.size();i++){
				ax[i] = (int) ((Point)(anchor.get(i))).getX();
				ay[i] = (int) ((Point)(anchor.get(i))).getY();								
			}
			
			
			Polygon myAnchor = new Polygon(ax,ay,anchor.size());
			pRoi = new ERoi(p,Roi.FREELINE, myAnchor);		
			img.setRoi(pRoi);
			if(size[0]==0)
				IJ.showStatus("Please, wait. Still creating the LiveWire");
			
			

				
		}		
	}
	
	//initialize function -- thanks to Volker Bäcker
    private void initialize(ImageProcessor ip) {

//      initialize Anchor
       anchor = new ArrayList<Point>();
       selIndex = new ArrayList<Integer>();

       dijX = -1;
       dijY = -1;

       //sets temporary selection size to zero
       tempSize = 0;

       //sets handle selected
       myHandle=-1;

       state = IDLE;

       width = ip.getWidth();
       height = ip.getHeight();
       
       //initializing DIJKSTRA
       pixels = getPixels(ip);
       dj = new Dijkstraheap (pixels,ip.getWidth(),ip.getHeight());

       //initializing selections
       selx = new int[width*height];
       sely = new int[width*height];
       selSize = 0;
       //Daniel
       pRoi = null;
    }
    //	change by Voker Bäcker to accept color images
	//it will convert the original color image to grayscale
	//and then use the grayscale image to do the segmentation    
    protected byte[] getPixels(ImageProcessor ip) {
            byte[] pixels;
            if (img.getType()==ImagePlus.GRAY8) {
                    pixels = (byte[]) ip.getPixels();
            } else {
                    Roi aRoi = img.getRoi();
                    img.killRoi();
                    Duplicater duplicater = new Duplicater();
                    ImagePlus greyscaleImage =duplicater.duplicateStack(img, img.getTitle() + " - grey");
                    WindowManager.setTempCurrentImage(greyscaleImage);
                    IJ.run("8-bit");
                    WindowManager.setTempCurrentImage(null);
                    pixels = (byte[]) greyscaleImage.getProcessor().getPixels();
                    img.setRoi(aRoi);
            }
            return pixels;
    }    
	
}
