/** Subdivision Curve Snake Class
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


abstract public class SubdivCurveSnake extends Snake
{
   protected static int subdivLevels;
   protected ArrayList[] subdivPoints; 
  

   public SubdivCurveSnake(ArrayList points, BufferedImage image, BufferedImage potential, boolean open, int levels)  
   {
      super(points, image, potential, open);
    
      // Allocate space and create subdivision curve levels
      subdivLevels = levels;
      subdivPoints = new ArrayList[subdivLevels+1];
      for(int i = 1; i <= subdivLevels; i++)
         subdivPoints[i] = new ArrayList();
      subdivPoints[0] = this.points;
    
      subdivide();
   }
   
   public void subdivide()
   {
      for (int level = 1; level <= subdivLevels; level++)
      {
         SnakePoint sp;
      
         int numPoints = subdivPoints[level-1].size();
         
         if (open)
            numPoints--;
         
         for(int i = 0; i < numPoints; i++)
         {
            SnakePoint p = (SnakePoint)subdivPoints[level-1].get(i);
            SnakePoint q = (SnakePoint)subdivPoints[level-1].get((i+1)%numPoints);

            sp = new SnakePoint((.75)*(p.pos.x)+(.25)*(q.pos.x),(.75)*(p.pos.y)+(.25)*(q.pos.y));
            subdivPoints[level].add(sp);
   
            sp = new SnakePoint((.25)*(p.pos.x)+(.75)*(q.pos.x),(.25)*(p.pos.y)+(.75)*(q.pos.y));
            subdivPoints[level].add(sp);
         }
      }  
   }
   
   
   synchronized public void draw(Graphics g, int factor, Rectangle imageRect) 
   {
      // Draw control points
      //super.draw(g, factor);
      // Draw subdivision curve
      points = subdivPoints[subdivLevels];
      super.draw(g, factor, imageRect);
      points = subdivPoints[0];
   }
   
 
   protected void imageForces() 
   {
      // Compute forces at sensor points
      points = subdivPoints[subdivLevels];
      super.imageForces();
      points = subdivPoints[0];
      // Distribute forces to control points
      distributeForces();
   }

   protected void distributeForces() 
   {  
      int level, prevLevel;
      SnakePoint sp, sp1, sp2;
      
      for (level = subdivLevels, prevLevel = level -1; level > 0; level--, prevLevel--) 
      {
         // Zero the forces of points at level above
         for (int i = 0; i < subdivPoints[prevLevel].size(); i++) 
         {
            sp = (SnakePoint)subdivPoints[prevLevel].get(i);
            sp.force.reset();
         }
     
         int parent1 =0;
         int parent2 =0;
         int n = subdivPoints[prevLevel].size();
         
         for (int i = 0;  i < subdivPoints[level].size();  i++) 
         { 
            parent1 = i/2;
            parent2 = ((i/2) + 1) % n; 
            
            sp  = (SnakePoint)subdivPoints[level].get(i);
            sp1 = (SnakePoint)subdivPoints[prevLevel].get(parent1);
            sp2 = (SnakePoint)subdivPoints[prevLevel].get(parent2);
           
            if (i%2==0)
            {
               sp1.force.scaleIncr(sp.force,0.75);
               sp2.force.scaleIncr(sp.force,0.25);
            }
            else
            {
               sp1.force.scaleIncr(sp.force,0.25);
               sp2.force.scaleIncr(sp.force,0.75);
            }
         }
         
         // Normalize the force (divide by 2)   
         for (int i = 0;  i < n;  i++) 
         { 
            sp = (SnakePoint)subdivPoints[prevLevel].get(i);
            sp.force.x *=0.5;
            sp.force.y *=0.5;
         }
      }
  }

 
   protected void internalForces()
   {
      // internal forces are calculated via stiffness matrix in Linear System
   }
     
  
 
   public void updateLevels()
   {
      for (int level = 1; level <= subdivLevels; level++)
      {
         int numPoints = subdivPoints[level-1].size();
      
         if (open)
            numPoints--;   
      
         for(int i=0; i < numPoints; i++)
         {
            SnakePoint p = (SnakePoint)subdivPoints[level-1].get(i);
            SnakePoint q = (SnakePoint)subdivPoints[level-1].get((i+1)%numPoints);

            int child1 = 2*i;
            int child2 = 2*i+1;
            SnakePoint sp1 = (SnakePoint)subdivPoints[level].get(child1);
            sp1.pos.x = (.75)*(p.pos.x) + (.25)*(q.pos.x);
            sp1.pos.y = (.75)*(p.pos.y) + (.25)*(q.pos.y);
   
            SnakePoint sp2 = (SnakePoint)subdivPoints[level].get(child2);
            sp2.pos.x = (.25)*(p.pos.x) + (.75)*(q.pos.x);
            sp2.pos.y = (.25)*(p.pos.y) + (.75)*(q.pos.y);
         }
      }     
   }
   
 
 }
