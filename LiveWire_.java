import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Toolbar;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Polygon;
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

public class LiveWire_ implements PlugInFilter, MouseListener, MouseMotionListener {
    final int IDLE = 0;
    final int WIRE = 1;
    
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
	PolygonRoi pRoi;//selection Polygon
	int[] tempx; //temporary selection x points
	int[] tempy; //temporary selection y points
	int tempSize; //temporary selection size
	
    
    Dijkstraheap dj;
    double gw;//magnitude weight
    double dw;//direction weight
    ArrayList<Point> anchor;//stores anchor points

	public int setup(String arg, ImagePlus imp) {
		this.img = imp;
		if (arg.equals("about")) {
		    showAbout();
		    return DONE;
		}
		return DOES_8G+DOES_STACKS+SUPPORTS_MASKING;
	}

	
	public void run(ImageProcessor ip) {
		//check IJ version
		if (IJ.versionLessThan("1.37r")) {
			IJ.showMessage("This plugin might not work with older versions of ImageJ\n"+
					"You should update your ImageJ to at least 1.37r\n" +
					"It also requires Java 1.5\n"
					);			
		}
		//initialize Anchor
		anchor = new ArrayList<Point>();
		//create Window for parameters		
		createWindow();
		
		//sets temporary selection size to zero
		tempSize = 0;

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
		
	    state = IDLE;
	    
	    width = ip.getWidth();
	    height = ip.getHeight();
		

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

		/**
		 * This part will remove the old toolbar
		 */
		/*
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
		container.validate();*/
		
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
		
		//initializing selections
		selx = new int[width*height];
		sely = new int[width*height];
		selSize = 0;

			
	}
	
	void showAbout() {
	    IJ.showMessage("About LiveWire_...",
	    "This sample plugin segments 8-bit images and needs \n" +
	    "Java 1.5. For more information look at the following page\n" +
	    " http://ivussnakes.sourceforge.net/ for more info"

	    );
	}
	
	void createWindow(){

		final JFrame frame = new JFrame("LiveWire Parameter Configuration");
		final javax.swing.JButton bUpdate;
			    	    
        bUpdate = new javax.swing.JButton();
        
        bUpdate.setActionCommand("Update");
        
                
    	frame.setSize( 400, 400 );
    	frame.setLocation(400, 200);
    	
    	//initialize weight variables
    	gw = 0.43;
    	dw = 0.13;
    	//label for the magnitude
    	final JLabel gLabel = new JLabel( "Magnitude: " + (int) (gw * 100), JLabel.LEFT );
    	gLabel.setAlignmentX( Component.CENTER_ALIGNMENT);
    	
    	//slider for magnitude
    	final JSlider gSlider = new JSlider( JSlider.HORIZONTAL,
			       0, (int) (100 * 1.0) ,(int) (100*gw) );
    	gSlider.setMajorTickSpacing( 10 );
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
    	dSlider.setMajorTickSpacing( 10 );
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
    			dj.setGWeight(gw);
    			dj.setDWeight(dw);
    			dj.setPoint(dj.getTx(),dj.getTy());
    		    //System.out.println("Fg " + gw + " Fd "+ dw);    		    
    		}
    	    } );
        
        frame.getContentPane().setLayout( new BoxLayout( frame.getContentPane(), BoxLayout.Y_AXIS ) );
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
        frame.setVisible(true);        
	}

	public void mouseClicked(MouseEvent e) {
				
	}

	public void mousePressed(MouseEvent e) {
		//if other tool is selected, we should return
		if( Toolbar.getToolId() != LiveWireId )
			return;
		//if zoom mode is working, we should convert x and y coordinates
		int myx = canvas.offScreenX(e.getX());
		int myy = canvas.offScreenY(e.getY());
		
		if(e.getButton()== MouseEvent.BUTTON1){		
			if(state == IDLE){
				IJ.runMacro("setOption('DisablePopupMenu', true)");
				state = WIRE;
			}
			
			anchor.add(new Point(myx,myy));
			paintPoints();
			dj.setPoint(myx,myy);
				
			for(int i=0;i<tempSize;i++){
				selx[selSize+i]=tempx[i];
				sely[selSize+i]=tempy[i];					
			}
			selSize+=tempSize;			
		}
		else if(e.getButton()== MouseEvent.BUTTON3){			
			if(state == WIRE){
				IJ.runMacro("setOption('DisablePopupMenu', false)");
				state = IDLE;				
				
			}
		}
		
	}

	/**
	 * This method highlights the anchor points drawing a Circle around them
	 */
	private void paintPoints() {
		ImageCanvas ic;
		ic = img.getCanvas();
		Graphics g = ic.getGraphics();
		g.setColor(Roi.getColor());
		for(int i=0;i<anchor.size();i++){
			int myx = (int) ((Point)(anchor.get(i))).getX();
			int myy = (int) ((Point)(anchor.get(i))).getY();
			
			g.drawRect(	ic.screenX(myx),
						ic.screenY(myy),10,10);
		}
		
		//img.draw(0,0,100,100);
		
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
			for(int i=0;i<size[0];i++){
				selx[i+selSize]= vx[i];
				sely[i+selSize]= vy[i];
			}
			tempx = vx;
			tempy = vy;
			tempSize = size[0];
			Polygon p = new Polygon(selx,sely,size[0]+selSize);				
			pRoi = new PolygonRoi(p,Roi.FREELINE);		
			img.setRoi(pRoi);
			if(size[0]==0)
				IJ.showStatus("Please, wait. Still creating the LiveWire");
				
		}
		
		
	}

}
