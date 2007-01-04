package ij.gui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Polygon;
import java.awt.image.BufferedImage;

public class ERoi extends PolygonRoi {
	Polygon anchor;	
	public ERoi(Polygon p, int type, Polygon myAnchor) {
		super(p,type);		
		anchor = myAnchor;		

	}
	
	
	public void draw(Graphics g) {
		super.draw(g);
		//g.setColor(Roi.getColor());
		
		for(int i=0;i<anchor.npoints;i++){
			g.setColor(Color.WHITE);
			g.fillRect(ic.screenX(anchor.xpoints[i])-2,ic.screenY(anchor.ypoints[i])-2,4,4);
			g.setColor(Color.BLACK);
			g.drawRect(ic.screenX(anchor.xpoints[i])-3,ic.screenY(anchor.ypoints[i])-3,5,5);			
		}		
				
	}	
	public int isHandle(int sx, int sy) {
		
		for(int i=0;i<anchor.npoints;i++){
			
			int px = anchor.xpoints[i];
			int py = anchor.ypoints[i];
			//System.out.println("Checking distance from sx = " + sx + " and px = "+ px);
			//checks if distance < 3 pixels
			if( (px-sx)*(px-sx) + (py-sy)*(py-sy) < 16 )
				return i;			
		}		
		return -1;
	}

}
