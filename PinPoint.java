/*
 * PinPoint.java
 *
 * Created on July 20, 2005, 1:07 PM
 */

/**
 *
 * @author  Tim
 */
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Ellipse2D;
import java.io.*;
import java.lang.*;
import java.lang.Object;
import java.util.*;

public class PinPoint extends Point2D.Double
{
   public SnakePoint closestSP;
   private Ellipse2D.Double circle;
   private double radius = 5.0;;
   
   public PinPoint()
   {
     closestSP = null;
     circle = new Ellipse2D.Double(0,0,radius,radius);
   }
   
   public PinPoint (double x, double y)
   {
      this.x = x;
      this.y = y;
      
      closestSP = null;
      circle = new Ellipse2D.Double(x-radius/2,y-radius/2,radius,radius);
   }
   
   public void setClosestSP(SnakePoint sp)
   {
      closestSP = sp;
   }
   
   synchronized public void draw(Graphics g, int zoomFactor, Rectangle imageRect) 
   {
      Graphics2D g2 = (Graphics2D) g;
      if (imageRect.x == 0 && imageRect.y == 0)
         circle.setFrame((x-radius/2)*zoomFactor,(y-radius/2)*zoomFactor,radius*zoomFactor,radius*zoomFactor);
      else
         circle.setFrame((-imageRect.x+x-radius/2)*zoomFactor,(-imageRect.y+y-radius/2)*zoomFactor,radius*zoomFactor,radius*zoomFactor);
      g2.fill(circle);
   }
   
}
