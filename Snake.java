import java.awt.*;
import java.awt.Rectangle;
import java.io.*;
import java.lang.*;
import java.util.*;
import java.awt.image.*;


abstract public class Snake implements Serializable 
{
   // Input image and potential image
   protected BufferedImage image;
   protected BufferedImage potential;
   protected Raster        imgRaster;
   protected Raster        potRaster;
   protected int           width;
   protected int           height;
 
   // Image force scale factor
   public double   imageForceSF  = 0.002;
   public double   forceLimit    = 1.0;
   public boolean  imageForceOn  = true;
  
   // Internal force scale factor
   public double stretchSF = 0.2; 
   public double bendSF    = 0.2;
  
   // Mouse interaction variables
   public double         mouseForceSF   = 0.2;
   public boolean        mouseForceOn   =false;
   protected Vector2D mouse = new Vector2D();
   protected SnakePoint closestSP;
  
   // Image gradient vector used in default image force computation
   Vector2D gradient = new Vector2D();  
  
   // Damping scale factor
   public double damping   = 0.5;
   
   
   // Number of deformation steps between rendering
   public int  timeSteps = 10;
  
   // Open snake or closed snake
   public boolean open = false; 
  
   // Turn snake off and on   
   public boolean active = true;
  
   // Constraint points
   public ArrayList pinPoints;
   public double   pinPointSF = 0.2;
   
   // Snake points
   public ArrayList points;
  
    
   public Snake() 
   {
      points = new ArrayList();
      pinPoints = new ArrayList();
   }
   
   public Snake(ArrayList points) 
   {
      this.points = points;
      pinPoints = new ArrayList();
   }
   

   public Snake(ArrayList points, BufferedImage image, BufferedImage potential, boolean open)
   {
      this.points        = (ArrayList)points.clone();
      this.image         = image;
      this.potential     = potential;
      this.imgRaster     = image.getRaster();
      this.potRaster     = potential.getRaster();
      this.open          = open;
      this.width         = potential.getWidth();
      this.height        = potential.getHeight();
      pinPoints = new ArrayList();
   }
   
   synchronized public void setImage(BufferedImage image)
   {
      this.image         = image;
      this.imgRaster     = image.getRaster();
      
   }
  
   synchronized public void setPotential(BufferedImage potential)
   {
      this.potential     = potential;
      this.potRaster     = potential.getRaster();
   }
   
   synchronized public void deform() 
   {
         
     for (int steps = 0; steps < timeSteps; steps++)
     {
         zeroForces();
    
         if (imageForceOn)
            imageForces();
    
         limitExternalForces();
   
         if (mouseForceOn)
            mouseForce();
         
         if (pinPoints.size() > 0)
            pinPointForces();
    
         internalForces();
    
         solver();
     }
  }
   
  // Implemented in a subclass  
  protected abstract void solver();
  protected abstract void internalForces();
  
  synchronized public void draw(Graphics g, int zoomFactor, Rectangle imageRect) 
  {
      if (imageRect.x == 0 && imageRect.y == 0)
      {   
         for (int i=0; i < points.size()-1; i++) 
         {
            SnakePoint sp1 = (SnakePoint) points.get(i);
            SnakePoint sp2 = (SnakePoint) points.get(i+1);
            g.drawLine((int)(sp1.pos.x*zoomFactor), (int)(sp1.pos.y*zoomFactor), 
                        (int)(sp2.pos.x*zoomFactor), (int)(sp2.pos.y*zoomFactor));
         }
         if (!open)
         {
            SnakePoint sp1 = (SnakePoint) points.get(points.size()-1);
            SnakePoint sp2 = (SnakePoint) points.get(0);
            g.drawLine((int)(sp1.pos.x*zoomFactor), (int)(sp1.pos.y*zoomFactor), 
                        (int)(sp2.pos.x*zoomFactor), (int)(sp2.pos.y*zoomFactor));      
         }
      
         // Draw pinPoints
         for (int i=0; i < pinPoints.size(); i++) 
         {
            PinPoint pin = (PinPoint)pinPoints.get(i);
            pin.draw(g, zoomFactor,imageRect);
         }
      }
      else
      {
         for (int i=0; i < points.size()-1; i++) 
         {
            SnakePoint sp1 = (SnakePoint) points.get(i);
            SnakePoint sp2 = (SnakePoint) points.get(i+1);
            g.drawLine((int)((-imageRect.x + sp1.pos.x)*zoomFactor),(int)((-imageRect.y + sp1.pos.y)*zoomFactor), 
                        (int)((-imageRect.x + sp2.pos.x)*zoomFactor),(int)((-imageRect.y + sp2.pos.y)*zoomFactor));
         }
         if (!open)
         {
            SnakePoint sp1 = (SnakePoint) points.get(points.size()-1);
            SnakePoint sp2 = (SnakePoint) points.get(0);
            g.drawLine((int)((-imageRect.x+sp1.pos.x)*zoomFactor), (int)((-imageRect.y+sp1.pos.y)*zoomFactor), 
                        (int)((-imageRect.x+sp2.pos.x)*zoomFactor), (int)((-imageRect.y+sp2.pos.y)*zoomFactor));      
         }
      
         // Draw pinPoints
         for (int i=0; i < pinPoints.size(); i++) 
         {
            PinPoint pin = (PinPoint)pinPoints.get(i);
            pin.draw(g, zoomFactor, imageRect);
         }
      }
   }
  
   // Default image force computation - gradient of ||gradient I||
   protected void imageForces() 
   {
      for (int i = 0; i < points.size(); i++) 
      {
         SnakePoint p = (SnakePoint)points.get(i);
              
         if ((p.pos.x >= 0) && (p.pos.x < width-1) && (p.pos.y >= 0) && (p.pos.y < height-1)) 
         {
            int x0 = (int) p.pos.x;
            int y0 = (int) p.pos.y;
            int x1 = x0 + 1;
            int y1 = y0 + 1;
            double xx = p.pos.x - x0;
            double yy = p.pos.y - y0;
   
            double f00 = (double)potRaster.getSample(x0, y0, 0);
            double f10 = (double)potRaster.getSample(x1, y0, 0);
            double f01 = (double)potRaster.getSample(x0, y1, 0);
            double f11 = (double)potRaster.getSample(x1, y1, 0);
            gradient.x = (yy * (f11 - f01) + (1.0 - yy) * (f10 - f00));
            gradient.y = (xx * (f11 - f10) + (1.0 - xx) * (f01 - f00));
         } 
         else 
            gradient.reset();
        
         p.force.scaleIncr(gradient,imageForceSF); 
      }
   }

   // Reset forces 
   protected void zeroForces() 
   {
      for (int i = 0; i < points.size(); i++)
         ((SnakePoint)points.get(i)).force.reset();
   }
  
   // Clamp the image forces
   protected void limitExternalForces()
   {
      for (int i = 0; i < points.size(); i++)
      {
         SnakePoint sp = (SnakePoint)points.get(i);
         double forceMag = sp.force.length();
         if ((forceMag > forceLimit)&&(forceMag != 0.0))
            sp.force.scale(forceLimit/forceMag);
      }
   }

   
   // Methods for user-defined constraint points
   
   public void setPinPoint(double x, double y)
   {
      PinPoint pin = new PinPoint(x,y);
      pinPoints.add(pin);
      pin.closestSP = closestPoint(x,y);
   }
   
   // Compute forces between user-defined constraint points and closest point on snake
   synchronized public void pinPointForces() 
   {
      for (int i = 0; i < pinPoints.size(); i++)
      {
         PinPoint pin = (PinPoint) pinPoints.get(i);
         if (pin.closestSP != null)
         {
            pin.closestSP.force.x += pinPointSF*(pin.x - pin.closestSP.pos.x);
            pin.closestSP.force.y += pinPointSF*(pin.y - pin.closestSP.pos.y);
         }
      }
   }
   
   
   // Methods for user-defined mouse forces
   
   public void setMouse(double x, double y)
   {
      mouse.x = x; mouse.y = y;
   }
  
   synchronized public void mouseForce() 
   {
      closestSP.force.x += mouseForceSF*(mouse.x - closestSP.pos.x);
      closestSP.force.y += mouseForceSF*(mouse.y - closestSP.pos.y);
   }
    
   public void setClosestPoint(SnakePoint sp)
   {
      closestSP = sp;
   }
  
   synchronized public SnakePoint closestPoint(double x, double y) 
   {
      double dx = width;
      double dy = height;
      double closest = dx*dx + dy*dy;
      int spi = 0;

      for (int i=0; i < points.size(); i++) 
      {
         SnakePoint sp = (SnakePoint)points.get(i);
         dx = x - sp.pos.x;
         dy = y - sp.pos.y;
         double dd = (dx*dx + dy*dy);
         if (dd < closest)  
         {
            closest = dd;
            spi = i;
         }
      }
      closestSP = (SnakePoint)points.get(spi);
      return closestSP;
   }

   synchronized public PinPoint findClosestPinPoint(double x, double y) 
   {
      double dx = width;
      double dy = height;
      double closest = dx*dx + dy*dy;
      int ppi = -1;

      for (int i=0; i < pinPoints.size(); i++) 
      {
         PinPoint pin = (PinPoint)pinPoints.get(i);
         
         dx = x - pin.x;
         dy = y - pin.y;
         double dd = (dx*dx + dy*dy);
         if (dd < closest)  
         {
            closest = dd;
            ppi = i;
         }
      }
      if (ppi == -1) return null;
      
      return (PinPoint)pinPoints.get(ppi);
   }
   
   public Rectangle boundingBox(int imageSF) 
   {
      double xMin = width;
      double xMax = 0.0;
      double yMin = height;
      double yMax = 0.0;
       
      for (int i=0; i < points.size(); i++) 
      {
         double x = ((SnakePoint) points.get(i)).pos.x ;
         double y = ((SnakePoint) points.get(i)).pos.y ;           
         if (x < xMin);
            xMin = x;
         if (x > xMax)
            xMax = x;
         if (y < yMin)
            yMin = y;
         if (y > yMax)
            yMax = y;
      }
      return new Rectangle((int)xMin,(int)yMax, (int)(imageSF*(xMax - xMin)), (int)(imageSF*(yMax-yMin)));
   }
  
   public void computeNormals()
   {
      int numPoints = points.size();
      SnakePoint sp;
      SnakePoint spNext = null;
      SnakePoint spPrev = null;
    
      for (int i = 0; i < numPoints; i++)
      {
         sp = (SnakePoint)points.get(i);
         sp.normal.reset();
    
         if (!open)
         {
            spNext = (SnakePoint)points.get((i+1)%numPoints);
            if (i == 0) 
               spPrev = (SnakePoint)points.get(numPoints-1);
            else
               spPrev = (SnakePoint)points.get(i-1);
      
            sp.normal.x  =  (spNext.pos.y - sp.pos.y);
            sp.normal.y  = -(spNext.pos.x - sp.pos.x);
            sp.normal.x +=  (sp.pos.y - spPrev.pos.y);
            sp.normal.y += -(sp.pos.x - spPrev.pos.x);     
         }
         else
         {
            if (i != (numPoints-1))
            {
               spNext = (SnakePoint)points.get(i+1);
               sp.normal.x  =  (sp.pos.y - spNext.pos.y);
               sp.normal.y  = -(sp.pos.x - spNext.pos.x);
            }
            if (i != 0)
            {
               spPrev = (SnakePoint)points.get(i-1);
               sp.normal.x +=  (spPrev.pos.y - sp.pos.y);
               sp.normal.y += -(spPrev.pos.x - sp.pos.x);
            }
         }
         sp.normal.normalize();
      }
   }
   
}