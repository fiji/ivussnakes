import java.io.*;
import java.lang.*;
import java.util.*;

public class LinearSystem
{
   private double[][]    K;
   private double[][]    cK = null;
   
   // Linear system Ax = b
   private double[][]    A = null;
   private double[]      bx = null;
   private double[]      by = null;
   private double[]      x  = null;
   private double[]      y  = null;
   
   private ArrayList points;
   private double   stretchSF;
   private double   bendSF;
   private double   damping;
   private boolean   noWrap = true;
   
   public LinearSystem(ArrayList points, double stretchSF, double bendSF, double damping, boolean noWrap)
   {
      this.points    = points;
      this.stretchSF = stretchSF;
      this.bendSF    = bendSF;
      this.damping   = damping;
      this.noWrap    = noWrap;
      
      int numEqns = this.points.size();
      A  = new double[numEqns][numEqns];
      bx = new double[numEqns];
      by = new double[numEqns];
      x   = new double[numEqns];
      y   = new double[numEqns];
      
      K = A;
      constructK();
   }
   
   // Methods to construct stiffness matrix K, store in skyline format, LDU decomp, K inverse 
   // Construct the stiffness matrix K (actually stored in A)
   private void constructK() 
   {
      Kstretch();
      Kbend();
      keepK();
      addDampingToK();
      skylineation();
      LDU_A();
   }

   private void Kstretch() 
   {
      if (stretchSF != 0) 
      {
         double[][] primitive = {{ 1*stretchSF, -1*stretchSF},{-1*stretchSF,  1*stretchSF}};
         int lastone;
         if (noWrap)
            lastone = points.size()-1;
         else
            lastone = points.size();

         for (int i=0; i < lastone; i++) 
         {
            addPrimitive(K, primitive, i);
         }
       }
    }

   private void Kbend() 
   {
       if (bendSF != 0) 
       {
         double[][] primitive = {{ 1*bendSF, -2*bendSF,  1*bendSF},{-2*bendSF,  4*bendSF, -2*bendSF}, { 1*bendSF, -2*bendSF,  1*bendSF}};

         int lastone;
         if (noWrap)
            lastone = points.size()-2;
         else
            lastone = points.size();

         for (int i=0; i < (lastone); i++) 
         {
            addPrimitive(K, primitive, i);
         }
       }
   }
   
   private void addDampingToK() 
   {
      for (int i=0; i < K.length; i++) 
      {
         K[i][i] += damping;
       }
    }
        
   public void setRHS()
   {
      for (int i = 0; i < points.size(); i++)
      {
         SnakePoint sp = (SnakePoint)points.get(i);
         bx[i] = sp.force.x;
         by[i] = sp.force.y;
     }
   }
   
  // A is destroyed by during decomp and solve so okeep a copy around    
  private void keepK() 
  {
      if (cK == null)
        cK = new double[K.length][K.length];
      
       for (int i=0; i < K.length; i++)
       {
          System.arraycopy(K[i], 0, cK[i], 0, K[i].length);
       }
    }
  
  // A is banded (sparse) - store in skyline format
  private void skylineation() 
  {
      int[] m = new int[A.length];
      for (int i=0; i < A.length; i++) 
      {
         //to determine the row number m(i) of
         //the first nonzero element in column i
         int j=0;
         while ((A[i][j]==0) && (j < i)) 
         {
            j++;
         }
         m[i]=j;
         int ch = i-j; //column height in column i
         double[] ci = new double[ch+1];
         for (int k=0; k < ch+1; k++)
            ci[k] = A[i][i-k];
         A[i] = ci;
      }
   }

    // LDU decomposition of A in skyline storage
   private void LDU_A() 
   {
      int NN = A.length; //the number of equations
      for (int N=0; N < NN; N++)
      {
         int KN = 0;
         int KL = KN+1;
         int KU = A[N].length-1;
         int KH = KU-KL;
         if (KH >= 0)
         {
            if (KH > 0) 
            {
               int K = N - KH;
               int IC = 0;
               int KLT = KU;
               for (int J = 1; J <= KH; J++) 
               {
                  IC++;
                  KLT--;
                  int KI = 0;
                  int ND = A[K].length - KI -1;
                  if (ND > 0) 
                  {
                     int KK = Math.min(IC, ND);
                     double C = 0;
                     for (int L=1; L <= KK; L++)
                        C += A[K][KI+L]*A[N][KLT+L];
                     A[N][KLT] -= C;
                  }
                  K++;
               }
            }
            int K = N;
            double B = 0;
            for (int KK = KL; KK <= KU; KK++) 
            {
               K--;
               int KI = 0;
               double C = A[N][KK]/A[K][KI];
               B += C*A[N][KK];
               A[N][KK] = C;
            }
            A[N][KN] -= B;
         }
         if (A[N][KN] <= 0) 
         {
            System.out.println("stiffness matrix not positive definite:");
            System.out.println("N= " + N + " KN= " + KN + " A(N,N)= " + A[N][0]);
         }
      }
   }
    
   // Solve linear system
   public double[] Ainvbx()
   {
      return Ainvb(bx);
   }
   
   public double[] Ainvby()
   {
      return Ainvb(by);
   }
   
   private double[] Ainvb(double[] b)
   {
      //reduce right-hand-side load vector b
      int NN = A.length; //the number of equations
      for (int N=0; N < NN; N++) 
      {
         int KL = 0 + 1;
         int KU = A[N].length - 1;
         if ((KU-KL) >= 0) 
         {
            int K = N;
            double C = 0;
            for (int KK=KL; KK <= KU; KK++) 
            {
               K--;
               C = C + A[N][KK]*b[K];
            }
            b[N] = b[N] - C;
         }
      }
      //back substitude
      for (int N=0; N < NN; N++)
      {
         b[N]/=A[N][0];
      }
      for (int N=NN-1; N >= 1; N--) 
      {
         int KL = 0 + 1;
         int KU = A[N].length - 1;
         if ((KU-KL) >= 0 ) 
         {
            int K = N;
            for (int KK=KL; KK <= KU; KK++) 
            {
               K--;
               b[K] -= A[N][KK]*b[N];
            }
         }
      }
      return (b);
   }
   
   private void addPrimitive(double A[][], double pr[][], int i) 
   {
      int numPoints = points.size();
      int h=pr.length;
      int w=pr[0].length;
      for (int m = 0; m < h;  m++)
      for (int n = 0;  n  < w; n++ )
           A[(i+m)%(numPoints)][(i+n)%(numPoints)] += pr[m][n];
    }
}
