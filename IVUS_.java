import ij.IJ;
import ij.ImagePlus;
import ij.Macro;
import ij.WindowManager;
import ij.gui.ERoi;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Toolbar;
import ij.plugin.filter.Duplicater;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

import java.awt.Component;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

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
    
    //IVUS variables
    ImageWindow iwStack; //window with stack images
    ImageWindow iwNS; //north south longitudinal slice
    ImageWindow iwWE; //west south longitudinal slice
    int[] pN; //y coord of North Points for each z
    int[] pS; //y coord of South Points for each z
    int[] pE; //x coord of East  Points for each z
    int[] pW; //x coord of West  Points for each z
    int numCuts;
    
    

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
	    
	    numCuts = calcNumCuts();
	    
	    //initialize points
	    pS = new int[numCuts];
	    pN = new int[numCuts];
	    pW = new int[numCuts];
	    pE = new int[numCuts];
	    
	    
	    makeCuts();
	    
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
		return 201;
	}


	private void makeCuts() {		
		iwStack = WindowManager.getCurrentWindow();
	    //North-South slice
        IJ.makeLine(width/2,0,width/2,height);
	    IJ.run("Reslice [/]...", "input=0.034 output=0.034 slice=1 rotate");	    
	    iwNS = WindowManager.getCurrentWindow();
	    iwNS.setTitle("NS Slice");
	    
	    //West East
	    WindowManager.setCurrentWindow(iwStack);
	    IJ.makeLine(0,height/2,width,height/2);
	    IJ.run("Reslice [/]...", "input=0.034 output=0.034 slice=1 rotate");
	    iwWE = WindowManager.getCurrentWindow();
	    iwWE.setTitle("WE Slice");
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
    		    bMarkUpNS.setEnabled(false);
    		}
    	} );
        
        final javax.swing.JButton bMarkDownNS;		
	    
        bMarkDownNS = new javax.swing.JButton();        
        bMarkDownNS.setActionCommand("Mark NS down points");
        bMarkDownNS.setText("Mark NS down points");
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
    		    bMarkDownNS.setEnabled(false);
    		}
    	} );
        
        final javax.swing.JButton bMarkDownWE;		
	    
        bMarkDownWE = new javax.swing.JButton();        
        bMarkDownWE.setActionCommand("Mark WE down points");
        bMarkDownWE.setText("Mark WE down points");
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
    		    bMarkDownWE.setEnabled(false);
    		}
    	} );
        
        final javax.swing.JButton bMarkUpWE;		
	    
        bMarkUpWE = new javax.swing.JButton();        
        bMarkUpWE.setActionCommand("Mark WE upper points");
        bMarkUpWE.setText("Mark WE upper down points");        
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
    		    bMarkUpWE.setEnabled(false);
    		}
    	} );
        
        
        final javax.swing.JButton bSelect; //this button commits the selection		
	    
        bSelect = new javax.swing.JButton();        
        bSelect.setActionCommand("Make Selection");
        bSelect.setText("Make Selection");
        bSelect.addActionListener( new ActionListener() {
    		public void actionPerformed( ActionEvent e ) {
    		    System.out.println("Polygon Points");
   		    	for(int i=0;i< numCuts;i++){
   		    		System.out.println("Slice "+ (i+1)+ " N ( " + (width/2) + " ,  " + pN[i]    + " ) " 
   		    										+ " E ( " + pE[i]     + " ,  " + height/2 + " ) "
   		    										+ " S ( " + (width/2) + " ,  " + pS[i]    + " ) "
   		    										+ " W ( " + pW[i]     + " ,  " + height/2 + " ) ");
   		    	}    		    
    		    bSelect.setEnabled(false);
    		}
    	} );
        
        
        frame.getContentPane().setLayout( new BoxLayout( frame.getContentPane(), BoxLayout.Y_AXIS ) );        
        frame.getContentPane().add(bMarkUpNS);
        frame.getContentPane().add(bMarkDownNS);
        frame.getContentPane().add(bMarkUpWE);
        frame.getContentPane().add(bMarkDownWE);
        frame.getContentPane().add(bSelect);        
        frame.pack();
        frame.setVisible(true);        
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
