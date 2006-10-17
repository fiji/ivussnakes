package livewire;

import ij.IJ;
import ij.ImagePlus;
import ij.Macro;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.ERoi;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.Roi;
import ij.gui.Toolbar;
import ij.plugin.filter.Duplicater;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.frame.Recorder;
import ij.process.ImageProcessor;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.StringTokenizer;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class LiveWire implements PlugInFilter, MouseListener, MouseMotionListener {
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
    int roiType = Roi.FREELINE;
    
    byte[] pixels;//image pixels
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
	
    
    Dijkstraheap dj;
    double gw;//magnitude weight
    double dw;//direction weight
    double ew;//exponential weight
    double pw;//exponential potence weight
    
    ArrayList<Point> anchor;//stores anchor points
    ArrayList<Integer> selIndex;//stores selection index to create new anchors in 
                                //between points and move them

    protected static Cursor defaultCursor = new Cursor(Cursor.DEFAULT_CURSOR);
	protected static Cursor handCursor = new Cursor(Cursor.HAND_CURSOR);
	protected static Cursor moveCursor = new Cursor(Cursor.MOVE_CURSOR);
	protected static Cursor crosshairCursor = new Cursor(Cursor.CROSSHAIR_CURSOR);
	
	protected boolean isArea = false;
	private MouseEvent lastEvent;
	
	public int setup(String arg, ImagePlus imp) {		
		this.img = imp;
		if (arg.equals("about")) {
		    showAbout();
		    return DONE;
		}				
		if (arg.equals("area")) {
			roiType = Roi.TRACED_ROI;
			isArea = true;
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
			
		if(arg!=null){
			createLiveWireFromMacro(ip, arg);
			return;
		}

		//create Window for parameters		
		createWindow();		

	    //remove old mouse listeners
	    ImageWindow win = img.getWindow();
	    canvas = win.getCanvas();

	    //store old MouseListeners so that we can put them back later
	    MouseListener[] oldMouseListener = new MouseListener[canvas.getMouseListeners().length];
	    
	    
	    for(int i=0;i<canvas.getMouseListeners().length;i++){		
	    	oldMouseListener[i] = canvas.getMouseListeners()[i];
	    	//canvas.removeMouseListener(canvas.getMouseListeners()[i]);
	    }
	    canvas.addMouseListener(this);
	    canvas.addMouseMotionListener(this);
		
	    if (isArea) LiveWireId = Toolbar.getInstance().getToolId("LiveWire 2d Tool");
	    else LiveWireId = Toolbar.getInstance().getToolId("LiveWire 1d Tool");
	    
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
		

			//int x = e.getX();
			//int y = e.getY();
			//int offscreenX = canvas.offScreenX(x);
			//int offscreenY = canvas.offScreenY(y);
			//IJ.write("mousePressed: "+offscreenX+","+offscreenY);
			//System.out.println("mousePressed: "+offscreenX+","+offscreenY);



		//Test SOBEL
		//create laplacian zero crossing function		
		//lapzero = img.createImagePlus();

		/*

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
		*/ 
		//Sobel Test ends here
		
		

			
	}
	
	private void createLiveWireFromMacro(ImageProcessor ip, String arg) {
		//we are being run from a Macro
		 StringTokenizer st = new StringTokenizer(arg,"= ",false);
		 int x0=0,x1=0,y0=0,y1=0,mag=0,dir=0,exp=0,pow=0 ;
		 
		 while (st.hasMoreTokens()) {
			 String par = st.nextToken();		         
		     //parse the values
		     if(par.charAt(0)=='x'){
		    	 //reading a x value
		    	 String val = st.nextToken();
		    	 if(par.charAt(1)=='0'){ //reading x0
		    		 x0 = Integer.parseInt(val);
		    	 }
		    	 else if(par.charAt(1)=='1'){//reading x1
		    		 x1 = Integer.parseInt(val);		        		 
		    	 }
		     }
		     else if(par.charAt(0)=='y'){
		    	 //reading a y value
		    	 String val = st.nextToken();
		    	 if(par.charAt(1)=='0'){ //reading y0
		    		 y0 = Integer.parseInt(val);
		    	 }
		    	 else if(par.charAt(1)=='1'){//reading y1
		    		 y1 = Integer.parseInt(val);		        		 
		    	 }
		     }
		     else if(par.charAt(0)=='m'){ 
		    	 // reading magnitude
		    	 String val = st.nextToken();		        	
		    		 mag = Integer.parseInt(val);		        	 		        	
		     }
		     else if(par.charAt(0)=='d'){ 
		    	 // reading magnitude
		    	 String val = st.nextToken();		        	
		    		 dir = Integer.parseInt(val);		        	 		        	
		     }
		     else if(par.charAt(0)=='e'){ 
		    	 // reading magnitude
		    	 String val = st.nextToken();		        	
		    		 exp = Integer.parseInt(val);		        	 		        	
		     }
		     else if(par.charAt(0)=='p'){ 
		    	 // reading magnitude
		    	 String val = st.nextToken();		        	
		    		 pow = Integer.parseInt(val);		        	 		        	
		     }

		 }			
		 if(
		 (x0<0)||(x0>=ip.getWidth()) ||
		 (x1<0)||(x1>=ip.getWidth()) ||
		 (y0<0)||(y0>=ip.getHeight()) ||
		 (y1<0)||(y1>=ip.getHeight()) 
		 ){
			 IJ.error("Start or end points are out of the image");
			 return;
		 }
		 		     
		 dj = new Dijkstraheap (pixels,ip.getWidth(),ip.getHeight());		     
		 dj.setGWeight((double)mag/100);
		 dj.setDWeight((double)dir/100);
		 dj.setEWeight((double)exp/100);
		 dj.setPWeight(pow);
		 dj.setPoint(x0,y0);
		 
		 //create selection
		 	int[] vx = new int[width*height];
			int[] vy = new int[width*height];
			int[] size = new int[1];
			

			dj.returnPath(x1,y1,vx,vy,size);			
			while(size[0]==0){
				IJ.showStatus("Please, wait. Still creating the LiveWire");
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				dj.returnPath(x1,y1,vx,vy,size);
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
			pRoi = new ERoi(p,roiType, myAnchor, this);		
			img.setRoi(pRoi);
		 
		 //System.out.println("Values x0 " + x0 + " x1 "+ x1 + " y0 " + y0 + " y1 " + y1 + " mag " + mag +
		 //	 " dir " + dir + " pow " + pow + " exp " + exp);
		return;
	}
	
	void showAbout() {
	    IJ.showMessage("About LiveWire_...",
	    "This sample plugin segments all non-stack images and needs \n" +
	    "Java 1.5. For more information look at the following page\n" +
	    " http://ivussnakes.sourceforge.net/ for more info"

	    );
	}
	
	void createWindow(){

		frame = new JFrame("LiveWire Parameter Configuration");
		final javax.swing.JButton bUpdate;
			    	    
        bUpdate = new javax.swing.JButton();
        
        bUpdate.setActionCommand("Update");
        
                
    	frame.setSize( 400, 400 );
    	frame.setLocation(400, 200);
    	
    	//initialize weight variables
    	gw = 0.43;
    	dw = 0.13;
    	ew = 0.0;
    	pw = 30;
    	
    	//label for exponential weight
    	final JLabel eLabel = new JLabel( "Exponential: " + (int) (ew * 100), JLabel.LEFT );
    	eLabel.setAlignmentX( Component.CENTER_ALIGNMENT);
    	
    	//slider for exponential weight
    	final JSlider eSlider = new JSlider( JSlider.HORIZONTAL,
			       0, (int) (100 * 1.0) ,(int) (100*ew) );
    	eSlider.setMajorTickSpacing( 20 );
    	eSlider.setMinorTickSpacing(  5 );
    	eSlider.setPaintTicks( true );
    	eSlider.setPaintLabels( true );
    	eSlider.setBorder( BorderFactory.createEmptyBorder( 0, 0, 10, 0 ) );
    	
    	eSlider.addChangeListener( new ChangeListener() {
    		public void stateChanged( ChangeEvent e ) {
    			eLabel.setText( "Exponential: " + eSlider.getValue() );
    		    ew = ((double)eSlider.getValue())/100;
    		    //System.out.println("Fg is now " + fg);
    		}
    	} 
    	);
    	
    	//    	label for exponencial potence weigth
    	final JLabel pLabel = new JLabel( "Power: " + (int) (pw), JLabel.LEFT );
    	pLabel.setAlignmentX( Component.CENTER_ALIGNMENT);
    	
    	//slider for magnitude
    	final JSlider pSlider = new JSlider( JSlider.HORIZONTAL,
			       0, (int) (120 * 1.0) ,(int) (pw) );
    	pSlider.setMajorTickSpacing( 20 );
    	pSlider.setMinorTickSpacing(  5 );
    	pSlider.setPaintTicks( true );
    	pSlider.setPaintLabels( true );
    	pSlider.setBorder( BorderFactory.createEmptyBorder( 0, 0, 10, 0 ) );
    	
    	pSlider.addChangeListener( new ChangeListener() {
    		public void stateChanged( ChangeEvent e ) {
    			pLabel.setText( "Power: " + pSlider.getValue() );
    		    pw = (double)pSlider.getValue();
    		    //System.out.println("Fg is now " + fg);
    		}
    	} 
    	);
    	
    	
    	
    	
    	//label for the magnitude
    	final JLabel gLabel = new JLabel( "Magnitude: " + (int) (gw * 100), JLabel.LEFT );
    	gLabel.setAlignmentX( Component.CENTER_ALIGNMENT);
    	
    	//slider for magnitude
    	final JSlider gSlider = new JSlider( JSlider.HORIZONTAL,
			       0, (int) (100 * 1.0) ,(int) (100*gw) );
    	gSlider.setMajorTickSpacing( 20 );
    	gSlider.setMinorTickSpacing(  5 );
    	gSlider.setPaintTicks( true );
    	gSlider.setPaintLabels( true );
    	gSlider.setBorder( BorderFactory.createEmptyBorder( 0, 0, 10, 0 ) );
    	
    	gSlider.addChangeListener( new ChangeListener() {
    		public void stateChanged( ChangeEvent e ) {
    			gLabel.setText( "Magnitude: " + gSlider.getValue() );
    		    gw = ((double)gSlider.getValue())/100;
    		    //System.out.println("Fg is now " + fg);
    		}
    	} 
    	);


    	//label for direction
    	final JLabel dLabel = new JLabel( "Direction: " + (int) (dw * 100), JLabel.LEFT );
    	dLabel.setAlignmentX( Component.CENTER_ALIGNMENT );
    	//    	slider for direction
    	final JSlider dSlider = new JSlider( JSlider.HORIZONTAL,
			       0, (int) (100 * 1.0) ,(int) (100*dw) );
    	dSlider.setMajorTickSpacing( 20 );
    	dSlider.setMinorTickSpacing(  5 );
    	dSlider.setPaintTicks( true );
    	dSlider.setPaintLabels( true );
    	dSlider.setBorder( BorderFactory.createEmptyBorder( 0, 0, 10, 0 ) );
    	
    	dSlider.addChangeListener( new ChangeListener() {
    		public void stateChanged( ChangeEvent e ) {
    			dLabel.setText( "Direction: " + dSlider.getValue() );
    		    dw = ((double)dSlider.getValue())/100;
    		    //System.out.println("Fd is now " + fd);
    		}
    	} 
    	);

    	
        bUpdate.setText("Update");
        bUpdate.addActionListener( new ActionListener() {
    		public void actionPerformed( ActionEvent e ) {
    			//System.out.println("Command was" + e.getActionCommand());
    			if(e.getActionCommand().equals("Update")){
	    			dj.setPWeight(pw);
	    			dj.setEWeight(ew);
	    			dj.setGWeight(gw);
	    			dj.setDWeight(dw);    			
	    			dj.setPoint(dj.getTx(),dj.getTy());
    		    //System.out.println("Fg " + gw + " Fd "+ dw);
    			}
    		}
    	    } );
        
        frame.getContentPane().setLayout( new BoxLayout( frame.getContentPane(), BoxLayout.Y_AXIS ) );
        
        frame.getContentPane().add(eLabel);
        frame.getContentPane().add(eSlider);
        frame.getContentPane().add(pLabel);
        frame.getContentPane().add(pSlider);
        frame.getContentPane().add(gLabel);
        frame.getContentPane().add(gSlider);
        frame.getContentPane().add(dLabel);
        frame.getContentPane().add(dSlider);
        frame.getContentPane().add(bUpdate);
        
                
        /*org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(71, 71, 71)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jTextField1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(layout.createSequentialGroup()
                        .add(jButton1)
                        .add(75, 75, 75)
                        .add(jButton2)))
                .addContainerGap(78, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .addContainerGap(112, Short.MAX_VALUE)
                .add(jTextField1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .add(87, 87, 87)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jButton2)
                    .add(jButton1))
                .add(57, 57, 57))
        );*/        
        frame.pack();                
	}

	public void mouseClicked(MouseEvent e) {
				
	}

	public void mousePressed(MouseEvent e) {
		if (e==lastEvent) return;
		lastEvent = e;
		if( Toolbar.getToolId() != LiveWireId && Toolbar.getToolId()>=0 && Toolbar.getToolId()<10) {
			 this.removeListeners(img);
			 return;
		}
		//if other tool is selected, we should return
		//thanks to Volker Baecker for the return when spacebar is down!
		if( Toolbar.getToolId() != LiveWireId  || IJ.spaceBarDown())
			return;
		//if zoom mode is working, we should convert x and y coordinates
		int myx = offScreenX(e.getX());
		int myy = offScreenY(e.getY());
		
		if(e.getButton()== MouseEvent.BUTTON1){
            handleMouseButton1Pressed(myx, myy);
            return;
		}
		if(e.getButton()== MouseEvent.BUTTON3){			
			handleMouseButton3Pressed(myx, myy);
		}
		
	}

	
	public void removeListeners(ImagePlus image) {
		if (image==null || image.getWindow()==null) return;
		this.removeMouseListeners(image);
		this.removeMouseMotionListeners(image);
	}


	protected void removeMouseMotionListeners(ImagePlus image) {
		image.getWindow().getCanvas().removeMouseMotionListener(this);
	}


	protected void removeMouseListeners(ImagePlus image) {
		image.getWindow().getCanvas().removeMouseListener(this);
	}


	protected void handleMouseButton3Pressed(int myx, int myy) {
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
			pRoi = new ERoi(p,roiType, myAnchor, this);		
			img.setRoi(pRoi);
			
			dj.setPoint(myx,myy);
				
			for(int i=0;i<tempSize;i++){					
				selx[selSize+i]=tempx[i];
				sely[selSize+i]=tempy[i];					
			}
			selSize+=tempSize;	
			tempSize=0;
		
			if (Recorder.record)
					this.recordAction();
		}
	}


	protected void recordAction() {
		String command = "LiveWire1dTool ";
		if (this.isArea()) command = "LiveWire2dTool ";
		// todo record livewire creation in macro recorder
	}


	public void handleMouseButton1Pressed(int myx, int myy) {
		if (state == IDLE) {
			myHandle = -1;
			if(pRoi!=null)
				myHandle = pRoi.isHandle(myx,myy);
			if(myHandle!=-1){
				state = HANDLE;
				return;
			}
			if (selSize!=0 && !IJ.shiftKeyDown()) {
				img.killRoi();
				initialize(img.getProcessor());
				return;
			}
			//we are going back to segment
			IJ.runMacro("setOption('DisablePopupMenu', true)");
			state = WIRE;
			if(selSize>0){
				//retrieve last point to Dijkstra
				dj.setPoint(selx[selSize-1],sely[selSize-1]);
				return;
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
		pRoi = new ERoi(p,roiType, myAnchor, this);		
		img.setRoi(pRoi);
		
		
		
		dj.setPoint(myx,myy);
			
		for(int i=0;i<tempSize;i++){
			selx[selSize+i]=tempx[i];
			sely[selSize+i]=tempy[i];					
		}
		selSize+=tempSize;
		tempSize=0;
	}


	public void mouseReleased(MouseEvent e)  {
		if(state == HANDLE){

			//update selection
			//copy selection from handle 0 to this handle minus 1
			
			int myx = offScreenX(e.getX());
			int myy = offScreenY(e.getY());
							
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
				pRoi = new ERoi(p,roiType, myAnchor, this);		
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
		int myx = offScreenX(e.getX());
		int myy = offScreenY(e.getY());		
		
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
				pRoi = new ERoi(p,roiType, myAnchor, this);		
				img.setRoi(pRoi);			
			}
						
		}
	}


	public void mouseMoved(MouseEvent e) {	
		//if other tool is selected, we should return
		if( Toolbar.getToolId() != LiveWireId )
			return;	
	
		//if zoom mode is working, we should convert x and y coordinates
		int myx = offScreenX(e.getX());
		int myy = offScreenY(e.getY());
		
		changeMousePointer(myx, myy);
		
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
			pRoi = new ERoi(p,roiType, myAnchor, this);		
			img.setRoi(pRoi);
			if(size[0]==0)
				IJ.showStatus("Please, wait. Still creating the LiveWire");				
		}		
	}


	public int offScreenY(int y) {
		int myy = canvas.offScreenY(y);
		if (myy>height-1) myy = height-1;
		if (myy<0) myy = 0;
		return myy;
	}


	public int offScreenX(int x) {
		int myx = canvas.offScreenX(x);
		if (myx>width-1) myx = width-1;
		if (myx<0) myx = 0;
		return myx;
	}


	private void changeMousePointer(int myx, int myy) {
		if (state==WIRE) return;
		ImageCanvas canvas = img.getWindow().getCanvas();
		myHandle = -1;
		if(pRoi!=null)
			myHandle = pRoi.isHandle(myx,myy);
		if(myHandle!=-1){
			canvas.setCursor(handCursor);
		} else if (Prefs.usePointerCursor)
			canvas.setCursor(defaultCursor);
			else
				canvas.setCursor(crosshairCursor);
	}
	
	public void copyState(LiveWire aWire) {
		aWire.anchor = (ArrayList<Point>) this.anchor.clone();
		aWire.selIndex = (ArrayList<Integer>) this.selIndex.clone();
		aWire.selx = this.selx.clone();
		aWire.sely = this.sely.clone();
		aWire.selSize = this.selSize;
		aWire.tempx = this.tempx.clone();
		aWire.tempy = this.tempy.clone();
		aWire.tempSize = this.tempSize;
		aWire.dijX = this.dijX;
		aWire.dijY = this.dijY;
		aWire.myHandle = this.myHandle;
	}
	
	//initialize function -- thanks to Volker Bäcker
    public void initialize(ImageProcessor ip) {

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


	public boolean isArea() {
		return isArea;
	}


	public JFrame getFrame() {
		return frame;
	}


	public ImagePlus getImage() {
		return img;
	}


	public void setPRoi(ERoi roi) {
		pRoi = roi;
	}


	public String kindSpecifier() {
		String result = "line";
		if (this.isArea()) result = "area";
		return result;
	}
}