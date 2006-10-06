/*
 * Vector2D.java
 *
 * Created on November 16, 2004, 9:56 AM
 */

/**
 *
 * @author  Tim McInerney
 */

public class Vector2D 
{
  public double x,y;    
    
  public Vector2D() 
  {
    x = 0.0; y = 0.0;
  }
    
  Vector2D(double x, double y)
  { 
    this.x = x; this.y = y;
  }
  
  public double getX()
  {
    return x;
  }

  public double getY()
  {
    return y;
  }
  
  // Arithmetic operators
  public Vector2D plus(Vector2D v)
  {
    Vector2D n = new Vector2D();
    
    n.x = x + v.x;
    n.y = y + v.y;
    return n;
  }

  
  public void plus(Vector2D v1, Vector2D v2)
  {
    x = v1.x + v2.x;
    y = v1.y + v2.y;
  }

  public Vector2D minus(Vector2D v)
  {
    Vector2D n = new Vector2D();
    
    n.x = x - v.x;
    n.y = y - v.y;
    return n;
  }
  
  public void minus(Vector2D v1, Vector2D v2)
  {
    x = v1.x - v2.x;
    y = v1.y - v2.y;
  }
    
  public void scale(double sf)
  {
    x = sf * x;
    y = sf * y;
  }
  
  public Vector2D vscale(double sf)
  {
    Vector2D n = new Vector2D(); 
    n.x = sf * x;
    n.y = sf * y;
    return n;
  }

  public void scaleIncr(Vector2D v, double sf)
  {
     
    x += sf * v.x;
    y += sf * v.y;
    
  }
  
  public void incr(Vector2D v)
  {
    x += v.x;
    y += v.y;
  } 

  public void decr(Vector2D v)
  {
    x -= v.x;
    y -= v.y;
  } 

  public void set(double x, double y)
  {
    this.x = x; this.y = y;    
  }
  
  public void set(Vector2D v)
  {
    this.x = v.x; this.y = v.y;
  }

  public void reset()
  {
    this.x = 0; this.y = 0;    
  }
  
  public void negate()
  {
    x = -x; y = -y;    
  }
  
  public double length()
  {
    return Math.sqrt(x*x + y*y);    
  }
  
  public double length(Vector2D v)
  {
    return Math.sqrt((x-v.x)*(x-v.x) + (y-v.y)*(y-v.y));    
  }
  
  public double lengthSQ()
  {
    return (x*x + y*y);    
  }

  public double distance(Vector2D v)
  {
     return Math.sqrt((float)((x-v.x)*(x-v.x)+(y-v.y)*(y-v.y)));
  }
  
  public double normalize()
  {
    double len = length();
    if (len == 0.0) return 0;
    x /= len;
    y /= len;
    return len;
  }

  public double dot(Vector2D v)
  {
    return x * v.x + y * v.y;
  }
  
  public Vector2D orthogonal()
  {
     Vector2D ortho = new Vector2D();
     ortho.x =  y;
     ortho.y = -x;
     return ortho;
  }
}

