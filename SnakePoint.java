import java.awt.*;
import java.io.*;
import java.lang.*;
import java.lang.Object;
import java.util.*;


public class SnakePoint 
{
   public Vector2D pos;
   public Vector2D normal;
   public Vector2D force;
   public boolean fixed;
    
   public SnakePoint()
   {
      pos    = new Vector2D();
      normal = new Vector2D();
      force  = new Vector2D();
      fixed  = false;
   }

   public SnakePoint(double x, double y)
   {
      this();  
      pos = new Vector2D(x,y);
   }
  
   public SnakePoint(Vector2D pos)
   {
      this();  
      this.pos = pos;
   }
  
   public Vector2D getPos()
   {
      return pos;
   }
  
   public  boolean equals(SnakePoint p)
   {
      return ((pos.x == p.pos.x) && (pos.y == p.pos.y));
   }

   public void setPos(double x, double y)
   {
      pos.x = x;
      pos.y = y;
   }

   public void setPos(SnakePoint p)
   {
      pos.x = p.pos.x;
      pos.y = p.pos.y;
   }
  
   public void setNormal(double x, double y)
   {
      normal.x = x;
      normal.y = y;
   }

   public void setNormal(Vector2D n)
   {
      normal.x = n.x;
      normal.y = n.y;
   }
  
   public void setForce(double x, double y)
   {
      force.x = x;
      force.y = y;
   }

   public void setForce(Vector2D f)
   {
      force.x = f.x;
      force.y = f.y;
   }
}

