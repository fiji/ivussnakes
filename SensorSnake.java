
/** Sensor Snake Class
   * @version 1.0  Sept 2005
   * @author Tim McInerney
   *Copyright (c) 2005 Tim McInerney
   **/

import java.awt.*;
import java.io.*;
import java.lang.*;
import java.util.*;
import java.awt.image.*;
import javax.swing.*;


public class SensorSnake extends SubdivCurveSnake
{
  protected LinearSystem ls; 
  protected int minLineSearch;   
  protected int maxLineSearch;   
  protected int minEdgeIntensity;
  protected int maxEdgeIntensity;
  protected Vector2D imgPoint;

  public SensorSnake(ArrayList points, BufferedImage image, BufferedImage potential, boolean open, int levels)  
  {
    super(points, image, potential, open, levels);
    imgPoint = new Vector2D();
    
    imageForceSF     = 0.8;
    forceLimit       = 100.0;
    stretchSF        = 0.002;
    bendSF           = 0.0;
    minLineSearch    = -2 ;
    maxLineSearch    = 10;
    minEdgeIntensity = 35;
    maxEdgeIntensity = 255;
    ls = new LinearSystem(this.points,stretchSF,bendSF,damping,open);
  }
 
  protected void imageForces() 
  {
    points = subdivPoints[subdivLevels];
    computeNormals();
    points = subdivPoints[0];
    
    for (int i = 0; i < subdivPoints[subdivLevels].size(); i++) 
    { 
      SnakePoint sp = (SnakePoint)subdivPoints[subdivLevels].get(i);
      sp.force.reset();
      
      if (foundImageEdgePoint(sp))
      { 
         Vector2D force = imgPoint.minus(sp.pos);
         force.scale(imageForceSF);
         sp.force.set(force);
      }
    }
    distributeForces();
  }

       
  public boolean foundImageEdgePoint(SnakePoint sp) 
  { 
    double xNext,  yNext;
    int intensity;
    
    // **use bresenham_line in ImageUtil.java
    // rather than this stepping - won't skip over pixels
    
    for (int i= minLineSearch; i <= maxLineSearch; i++)
    {
      xNext = sp.pos.x + i*sp.normal.x;  
      yNext = sp.pos.y + i*sp.normal.y;

      if ( xNext>=0 && xNext<width && yNext>=0 && yNext<height)
      {
        intensity = potRaster.getSample((int)xNext,(int)yNext,0);
        
        //intensity = bilinearInterpolate(xNext, yNext);
         
        if ( intensity > minEdgeIntensity)
        //if ( (minEdgeIntensity <=intensity) && (intensity <= maxEdgeIntensity))
        {
          imgPoint.set(xNext,yNext);
          return true;
        }
      }
    }
    return false;
  }
  
  protected void solver() 
  {
     
      SnakePoint sp;
   
      // Add damping
      for (int i = 0; i < points.size(); i++)
      {
         sp = (SnakePoint)points.get(i);
         sp.force.incr(sp.pos.vscale(damping));
      }
      
      // Set right hand side to current forces
      ls.setRHS();
      
      // Solve linear system - produce new snake point positions
      double[] x = ls.Ainvbx();
      double[] y = ls.Ainvby();
      
      // Copy new positions into snake points
      
      for (int i = 0; i < points.size(); i++)
      {
         sp = (SnakePoint)points.get(i);
         sp.pos.set(x[i],y[i]);
      }
      
      // compute positions of finer levels
      updateLevels();
  }
}