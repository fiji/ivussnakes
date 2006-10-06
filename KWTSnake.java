import java.awt.*;
import java.io.*;
import java.lang.*;
import java.util.*;
import java.awt.image.*;


public class KWTSnake extends Snake implements Serializable 
{
   protected LinearSystem ls;
    
   
   public KWTSnake(ArrayList points, BufferedImage image, BufferedImage potential, boolean open) 
   {
      super(points, image, potential,open);
      
      ls = new LinearSystem(this.points,stretchSF,bendSF,damping,open);
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
   }
   
   protected void internalForces()
   {
      // internal forces are calculated via stiffness matrix in Linear System
   }
}
  

