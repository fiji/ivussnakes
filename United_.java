import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ERoi;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.Roi;
import ij.gui.Toolbar;
import ij.plugin.filter.Duplicater;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

import java.awt.Component;
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
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class United_ extends LiveWire_{
    	
	void showAbout() {
	    IJ.showMessage("About United_...",
	    "This sample plugin segments 8-bit images and needs \n" +
	    "Java 1.5. For more information look at the following page\n" +
	    " http://ivussnakes.sourceforge.net/ for more info"

	    );
	}
	
	void createWindow(){
		super.createWindow();
		
		//change window title
		frame.setTitle("United Snakes Parameter Configuration");
		
		//new button
		final javax.swing.JButton bSnake;
	    
        bSnake = new javax.swing.JButton();        
        bSnake.setActionCommand("Snake");		    	    	
        bSnake.setText("Convert to Snake");
        
        bSnake.addActionListener( new ActionListener() {
    		public void actionPerformed( ActionEvent e ) {
    			System.out.println("Command was" + e.getActionCommand());
    		    //System.out.println("Fg " + gw + " Fd "+ dw);    		    
    		}
    	} );
        
        frame.getContentPane().add(bSnake);                        
        frame.pack();
        
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
				
				
				
				
				//KWTSnakes
				//create points
				ArrayList snakePoints = new ArrayList();				
				for(int i=0;i< p.npoints;i++){
					snakePoints.add(new SnakePoint(p.xpoints[i],p.ypoints[i]));
				}
				
				//create image
		        BufferedImage   image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);		        		        		                        
		        Graphics g = image.createGraphics();
		        g.drawImage(img.getImage(), 0, 0, null);
		        g.dispose();            
		        
				double[] myrgradient = new double[height*width];
				dj.getGradientR(myrgradient);
				
                Roi aRoi = img.getRoi();
                img.killRoi();
                Duplicater duplicater = new Duplicater();
                ImagePlus edgeImage =duplicater.duplicateStack(img, img.getTitle() + " - Edge");
                WindowManager.setTempCurrentImage(edgeImage);
                edgeImage.getProcessor().findEdges();                  
                BufferedImage   potential = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);		        		        		                        
		        Graphics gPot = potential.createGraphics();
		        gPot.drawImage(edgeImage.getImage(),0,0,null);
		        gPot.dispose();                                
                img.setRoi(aRoi);
				
				
		        
									
				KWTSnake snake = new KWTSnake(snakePoints, image,potential,false);
				for(int i=0;i<1000;i++){
					snake.deform();
				}
				
					Rectangle rect = new Rectangle(width,height);
					
//					snake.draw( img.getWindow().getGraphics(),1,rect);				
					//img.getCanvas().getGraphics().clearRect(0,0,50,50);
					
					Polygon pSnake = new Polygon();
					for(int j=0;j<snake.points.size();j++){
						SnakePoint sp1 = (SnakePoint) snake.points.get(j);
						pSnake.addPoint((int) sp1.getPos().getX(),(int) sp1.getPos().getY());
					}								

					//handles roi
					Polygon myAnchor = new Polygon(ax,ay,anchor.size());
					pRoi = new ERoi(pSnake,Roi.FREELINE, myAnchor);		
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
	
}