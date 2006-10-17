package ij.gui;

import ij.ImagePlus;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Polygon;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

import livewire.LiveWire;

public class ERoi extends PolygonRoi {
	/**
	 * 
	 */
	private static final long serialVersionUID = -371232885164351791L;
	
	protected Polygon anchor;
	LiveWire wire;
	
	public ERoi(Polygon p, int type, Polygon myAnchor, LiveWire wire) {
		super(p,type);		
		anchor = myAnchor;	
		if (this.wire!=null && imp.getWindow()!=null) {
				imp.getWindow().getCanvas().removeMouseListener(wire);
				imp.getWindow().getCanvas().removeMouseMotionListener(wire);
		}
		this.wire = wire;
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
		if (imp.getRoi()==null) return -1;
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
	
	public void setImage(ImagePlus newImage) {
		ImagePlus oldImage = this.imp;
		ImagePlus wireImage = null;
		if (wire!=null) wireImage = wire.getImage();
		if (wireImage == newImage ) {	// the same image as before is set again.
			super.setImage(newImage);
			return;
		}
		if (isSelectionDeleted(oldImage, newImage, wireImage)) {
			super.setImage(newImage);
			handleSelectionDeleted(wireImage);
			return;
		}		
		if (isSelectionTransfered(oldImage, newImage, wireImage)) {
			super.setImage(newImage);
			handleSelectionTransfered(oldImage, newImage, wireImage);
			return;
		} 
	}
	
	protected void handleSelectionTransfered(ImagePlus oldImage, ImagePlus newImage, ImagePlus wireImage) {
		if (newImage.getWindow()!=null) {
			removeAllWireMouseAndMouseMotionListeners(newImage);
			String kind = wire.kindSpecifier();
			LiveWire newWire = new LiveWire();
			newImage.getWindow().getCanvas().addMouseListener(newWire);
			newImage.getWindow().getCanvas().addMouseMotionListener(newWire);
			newWire.setup(kind, newImage);
			newWire.run(newImage.getProcessor());
			wire.copyState(newWire);
			newWire.setPRoi(this);
			this.wire = newWire;
		}
	}

	protected void removeAllWireMouseAndMouseMotionListeners(ImagePlus newImage) {
		MouseListener[] listeners = newImage.getCanvas().getMouseListeners();
		for (int i=0; i<listeners.length; i++) {
			if (listeners[i].getClass().getName().contains("LiveWire")){
				LiveWire aListener = (LiveWire) listeners[i];
				newImage.getWindow().getCanvas().removeMouseListener(aListener);
			}
		}
		MouseMotionListener[] motionListeners = newImage.getCanvas().getMouseMotionListeners();
		for (int i=0; i<motionListeners.length; i++) {
			if (motionListeners[i].getClass().getName().contains("LiveWire")){
				LiveWire aListener = (LiveWire) motionListeners[i];
				newImage.getWindow().getCanvas().removeMouseListener(aListener);
			}
		}
	}

	protected boolean isSelectionTransfered(ImagePlus oldImage, ImagePlus newImage, ImagePlus wireImage) {
		boolean result = (wireImage!=null && wireImage != newImage);
		return result;
	}

	protected void handleSelectionDeleted(ImagePlus wireImage) {
		// do nothing
	}

	protected boolean isSelectionDeleted(ImagePlus oldImage, ImagePlus newImage, ImagePlus wireImage) {
		boolean result = (wireImage !=null && newImage==null);
		return result;
	}

	protected boolean isSameImage(ImagePlus oldImage, ImagePlus newImage, ImagePlus wireImage) {
		boolean result = (wireImage == newImage);
		return result;
	}

	/** Returns a copy of this ERoi. */
	public synchronized Object clone() {
		ERoi r = (ERoi)super.clone();
		Polygon newAnchor = new Polygon();
		for (int i=0; i<this.anchor.npoints; i++) {
			newAnchor.addPoint(this.anchor.xpoints[i], this.anchor.ypoints[i]);
		}
		r.anchor = newAnchor;
		return r;
	}
}
