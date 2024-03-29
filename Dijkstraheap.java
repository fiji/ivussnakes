/*  DijkstraHeap -- Class used to calculate shortest path for Segmentation Plugin for ImageJ
    Copyright (C) 2006  Daniel Lelis Baggio

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along
    with this program; if not, write to the Free Software Foundation, Inc.,
    51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
*/

import java.awt.datatransfer.Clipboard;
import java.util.PriorityQueue;

/**
 * @author baggio
 *
 */
class Dijkstraheap implements Runnable{
	
	
	
    byte[] imagePixels; //stores Pixels from original image
    int[] imageCosts; //stores Costs for every pixel
    PriorityQueue<PixelNode> pixelCosts;
    double[] gradientx; //stores image gradient modulus 
    double[] gradienty; //stores image gradient modulus 
    //it is oriented: X = LEFT TO RIGHT
    //                Y = UP   TO DOWN
    double[] gradientr; //stores image gradient RESULTANT modulus 
    double grmin;//gradient global minimum
    double grmax;//gradient global maximum

    int[] whereFrom;  //stores where from path started
    boolean[] visited; //stores whether the node was marked or not
    int width;
    int height;
    int sx,sy; //seed x and seed y, weight zero for this point

    private int tx,ty;//thread x and y passed as parameters

    Thread myThread;
    boolean myThreadRuns;//flag for thread state
    
    private double gw;//Gradient Magnitude Weight - set by setGWeight
    private double dw;//Gradient Direction Weight - set by setDWeight
    private double zw;//Laplacian Zero Cross Weight - set by setZWeight
    private double ew;//Exponential        Weight - set by setEWeight
    private double pw;//Exponential Potence Weight - set by setPWeight
    
    double[][] pCosts;//for debugin reasons
    
    byte [] lap5;
    byte [] lap5zc; //zero cross laplacian
    
    byte [] lap9;
    byte [] lap9zc; //zero cross laplacian
     

    static int INF = 0x7FFFFFFF; //maximum integer

    //converts x, y coordinates to vector index
    private int toIndex(int x,int y){
	return (y*width+x);
    }

    //initializes gradient vector
    private void initGradient(){
	gradientx = new double[height*width];
	gradienty = new double[height*width];
	gradientr = new double[height*width];
	//Using sobel
	//for gx convolutes the following matrix
	//   
	//     |-1 0 1|
	//Gx = |-2 0 2|
	//     |-1 0 1|


	for(int i=0;i<width;i++){
	    for(int j=0;j<height;j++){
		if((i>0)&&(i<width-1)&&(j>0)&&(j<height-1)){
		    gradientx[toIndex(i,j)] =
			-1*(imagePixels[toIndex(i-1,j-1)] & 0xff) +1*(imagePixels[toIndex(i+1,j-1)] & 0xff)
			-2*(imagePixels[toIndex(i-1,j  )] & 0xff) +2*(imagePixels[toIndex(i+1,j  )] & 0xff)
			-1*(imagePixels[toIndex(i-1,j+1)] & 0xff) +1*(imagePixels[toIndex(i+1,j+1)] & 0xff);
		}
	    }
	}

	//for gy convolutes the following matrix (remember y is zero at the top!)
	//
	//     |+1 +2 +1| 
	//Gy = | 0  0  0|
	//     |-1 -2 -1|
	//
	for(int i=0;i<width;i++){
	    for(int j=0;j<height;j++){
		if((i>0)&&(i<width-1)&&(j>0)&&(j<height-1)){
		    gradienty[toIndex(i,j)] = 
			+1*(imagePixels[toIndex(i-1,j-1)] & 0xff) -1*(imagePixels[toIndex(i-1,j+1)] & 0xff)
			+2*(imagePixels[toIndex(i  ,j-1)] & 0xff) -2*(imagePixels[toIndex(i  ,j+1)] & 0xff)
			+1*(imagePixels[toIndex(i+1,j-1)] & 0xff) -1*(imagePixels[toIndex(i+1,j+1)] & 0xff);
		}
	    }
	}
	for(int i=0;i<width;i++){
	    for(int j=0;j<height;j++){
		if((i>0)&&(i<width-1)&&(j>0)&&(j<height-1)){
		    //Math.hypot returns sqrt(x^2 +y^2) without intermediate overflow or underflow.
		    
		    gradientr[toIndex(i,j)] = Math.sqrt( gradientx[toIndex(i,j)]*gradientx[toIndex(i,j)]+
							 gradienty[toIndex(i,j)]*gradienty[toIndex(i,j)]);
		    
		}
	    }
	}


	grmin = gradientr[0];
	grmax = gradientr[0];
	for(int i=0;i< height*width;i++){
	    if(gradientr[i]<grmin) grmin=gradientr[i];
	    if(gradientr[i]>grmax) grmax=gradientr[i];
	}

    }
    public void setLap5(byte[] myLap){
    	lap5 = myLap;
    	return;
    }
    public void initLapZC5(){ //initializes Laplacian ZeroCrossing order for 5x5
        lap5zc = new byte[height*width];
    	
        for(int y=0;y<height;y++){
    		for(int x=0;x<width;x++){
				lap5zc[toIndex(x,y)]=1;
    		}
        }
        //seeks horizontaly for signal change
    	for(int y=0;y<height;y++){    		
    		for(int x=0;x<width-1;x++){
    			if(lap5[toIndex(x,y)]*lap5[toIndex(x+1,y)] < 0){//different signals?
    				if(Math.abs(lap5[toIndex(x,y)])<Math.abs(lap5[toIndex(x+1,y)])){
    					//x is nearer zero
    					lap5zc[toIndex(x,y)]=0;
    				}
    				else{
    					//x+1 is nearer zero
    					lap5zc[toIndex(x+1,y)]=0;    					
    				}
    			}
    		}
    	}
    	//seeks vertically for signal change
    	for(int x=0;x<width;x++){
    		for(int y=0;y<height-1;y++){    		
    	
    			if(lap5[toIndex(x,y)]*lap5[toIndex(x,y+1)] < 0){//different signals?
    				if(Math.abs(lap5[toIndex(x,y)])<Math.abs(lap5[toIndex(x,y+1)])){
    					//x is nearer zero
    					lap5zc[toIndex(x,y)]=0;
    				}
    				else{
    					//x+1 is nearer zero
    					lap5zc[toIndex(x,y+1)]=0;    					
    				}
    			}
    		}
    	}
    
    }
    
    
    public byte[] getLap5ZC(){
    	return lap5zc;
    }
    
    
    public void setLap9(byte[] myLap){
    	lap9 = myLap;
    	return;
    }
    public void initLapZC9(){ //initializes Laplacian ZeroCrossing order for 5x5
        lap9zc = new byte[height*width];
    	
        for(int y=0;y<height;y++){
    		for(int x=0;x<width;x++){
				lap9zc[toIndex(x,y)]=1;
    		}
        }
        //seeks horizontaly for signal change
    	for(int y=0;y<height;y++){    		
    		for(int x=0;x<width-1;x++){
    			if(lap9[toIndex(x,y)]*lap9[toIndex(x+1,y)] < 0){//different signals?
    				if(Math.abs(lap9[toIndex(x,y)])<Math.abs(lap9[toIndex(x+1,y)])){
    					//x is nearer zero
    					lap9zc[toIndex(x,y)]=0;
    				}
    				else{
    					//x+1 is nearer zero
    					lap9zc[toIndex(x+1,y)]=0;    					
    				}
    			}
    		}
    	}
//    	seeks horizontaly for signal change
    	    		
    	for(int x=0;x<width;x++){
    		for(int y=0;y<height-1;y++){
    			if(lap9[toIndex(x,y)]*lap9[toIndex(x,y+1)] < 0){//different signals?
    				if(Math.abs(lap9[toIndex(x,y)])<Math.abs(lap9[toIndex(x,y+1)])){
    					//x is nearer zero
    					lap9zc[toIndex(x,y)]=0;
    				}
    				else{
    					//x+1 is nearer zero
    					lap9zc[toIndex(x,y+1)]=0;    					
    				}
    			}
    		}
    	}
    	
    	
    
    }
    
    
    public byte[] getLap9ZC(){
    	return lap9zc;
    }

    

    public void getGradientX(double[] mat){
	for(int i=0;i<height;i++){
	    for(int j=0;j<width;j++){
		//		System.out.println(gradientx[(i*width+j)]);
		mat[i*width+j]= gradientx[i*width+j];
	    }
	}
    }
    public void getGradientY(double[] mat){
	for(int i=0;i<height;i++){
	    for(int j=0;j<width;j++){
		//		System.out.println(gradientx[(i*width+j)]);
		mat[i*width+j]= gradienty[i*width+j];
	    }
	}
    }
    public void getGradientR(double[] mat){
	for(int i=0;i<height;i++){
	    for(int j=0;j<width;j++){
		//		System.out.println(gradientx[(i*width+j)]);
		mat[i*width+j]= gradientr[i*width+j];
	    }
	}
    }

    //initializes Dijkstra with the image
    public Dijkstraheap(byte[] image,int x, int y){
    	 
    	
    	
    	//initializes weights for edge cost
    	//these are default values
    	gw = 0.43;
    	zw = 0.0;
    	dw = 0.13;
    	ew = 0.0;
    	pw = 30;
    	
	//initializes all other matrices
	imagePixels = new byte[x*y];
	//	imageCosts  = new int [x*y];
	pixelCosts = new PriorityQueue<PixelNode>();
	whereFrom   = new int [x*y];
	visited     = new boolean[x*y];
	width  = x;
	height = y;
	
	
	//for debug reasons
	//used to store
	pCosts = new double[x][y];
	
	
	
	
	//copy image matrice
	for(int j=0;j<y;j++){
	    for(int i=0;i<x;i++){	    	
		imagePixels[j*x+i] = 
			image[j*x+i];		
		//imageCosts [j*x+i] = INF;
		visited    [j*x+i] = false;
		//		System.out.print((imagePixels[j*x+i]&0xff)+ " ");
	    }
	    //	    System.out.println("");
	}
	initGradient();	
	//inits the thread
	myThread = new Thread(this);

    }    
    //returns de cost of going from sx,sy to dx,dy
    private double edgeCost(int sx,int sy,int dx,int dy){
	//fg is the Gradient Magnitude

	//we are dividing by sqrt(2) so that the value won't pass 1
	//as is stated in United Snakes formule 36
	
	double fg = (1.0/Math.sqrt(2)*Math.sqrt( (dx-sx)*(dx-sx) + (dy-sy)*(dy-sy))* 
		(1 - ((gradientr[toIndex(dx,dy)]-grmin)/(grmax-grmin))));

	if(grmin==grmax) 
	    fg= (1.0/Math.sqrt(2)*Math.sqrt( (dx-sx)*(dx-sx) + (dy-sy)*(dy-sy)));


	


	//	System.out.println("Fg " + fg +" gradientr " + gradientr[toIndex(dx,dy)] + " grmin " + grmin + " grmax " + grmax);
	//fd id the Gradient Direction

	//CHECK ME OUT: The part of fd has not been much tested
	//if someone wishes to spend some time testing it, it'd be a great idea

	//Dp is the unit vector of the gradient direction at pixel p (sx,sy)
	//this is defined near formule 37 in United Snakes
	Vector2d GradVector = new Vector2d(gradientx[toIndex(sx,sy)],gradienty[toIndex(sx,sy)]);
	Vector2d Dp = GradVector.getUnit();
	//DpN is the normal vector to Dp
	Vector2d DpN = new Vector2d(Dp.getNormal());
	//Lpq is the normalized biderectional link between pixels p and q (United Snakes formule 38)
	Vector2d Lpq = new Vector2d();
	Vector2d p = new Vector2d(sx,sy);
	Vector2d q = new Vector2d(dx,dy);
	
	//remember that y is upside down... we need to invert the y term in pq
	
	Vector2d myPQ = new Vector2d ( dx - sx, sy - dy);
	
	
	if(DpN.dotProduct( myPQ ) >=0 ){
	    Lpq = myPQ.getUnit(); // (q-p)/||p-q|| 
	}
	else{	    
	    Lpq = myPQ.getUnit(); // (p-q)/||p-q||
	}
	//dppq = DpN . Lpq 
	double dppq = DpN.dotProduct(Lpq);
	//dqpq = Lpq . DpN
	Vector2d GradVectorq = new Vector2d(gradientx[toIndex(dx,dy)],gradienty[toIndex(dx,dy)]);
	Vector2d Dq = GradVectorq.getUnit();
	Vector2d DqN = new Vector2d(Dq.getNormal());
	double dqpq = Lpq.dotProduct(DqN);

	//United Snakes formule 37
	//I have found a problem here... 
	//When the gradient is near zero in the place, when getting the unit vector, 
	//it becomes NaN, because we are dividing something for about zero... 
	//but the value of fd should be as high as possible (we are over an edge)
	//so, when asking for unit vectors, we check for components x and y. If they are
	//too small, we return a vector (1,0)
	if((Math.abs(GradVector.getX())<0.0000001)&&(Math.abs(GradVector.getY())<0.0000001))
	    dppq = 0;
	if((Math.abs(GradVectorq.getX())<0.0000001)&&(Math.abs(GradVectorq.getY())<0.0000001))
	    dqpq = 0;
	    
	
	double fd = 2.0/(3*Math.PI)*(Math.acos(dppq)+Math.acos(dqpq));	    

	/*	System.out.println("Fd "+ fd + " Fg " + fg + " acos dppq " + Math.acos(dppq) + " acos dqpq " + Math.acos(dqpq)
			   + " Dp (" + Dp.getX() + "," + Dp.getY() +") DpN (" + DpN.getX() +"," + DpN.getY() + ")"  
			   + " p (" +p.getX()+ ","+p.getY() + ") q(" +q.getX()+","+q.getY()+") " 
			   + "Gradp(" + gradientx[toIndex(sx,sy)]+ ","+gradienty[toIndex(sx,sy)]+ ") " 
			   + "Gradq(" + gradientx[toIndex(dx,dy)]+ ","+gradienty[toIndex(dx,dy)]+ ")");*/
	double fz = 0;
	
	if(lap5zc != null){
		double l5 = (double)(lap5zc[toIndex(dx,dy)]& 0xff);
		double l9 = (double)(lap9zc[toIndex(dx,dy)]& 0xff);
		fz = 0.45*l5+0.55*l9;
	}
	
//	this parameter is an attempt to find edges in IVUS images	
	double x = (gradientr[toIndex(dx,dy)]-grmin)/(grmax-grmin);
	double fe = Math.exp(-pw*x)*(fg);//+fd+fz);
	
	//temporary -- testing Sergio's suggestion
	//return ( gw*fg*( 1 - Math.exp(-pw*fg)) + dw*(1 - Math.exp(-pw*fd)) + zw*(1 - Math.exp(-pw*fz)) ); 
	
	//return Math.abs(imagePixels[toIndex(dx,dy)]-imagePixels[toIndex(sx,sy)]);
	
	return ew*fe+gw*fg+dw*fd+zw*fz;//+0.2*Math.sqrt( (dx-sx)*(dx-sx) + (dy-sy)*(dy-sy));
	
    }
    //sets weights for fg and fd
    public void setGWeight(double pgw){
    	gw = pgw;
    }
    public void setDWeight(double pdw){
    	dw = pdw;
    }
    public void setZWeight(double pzw){
    	zw = pzw;
    }
    public void setEWeight(double pew){
    	ew = pew;
    }
    public void setPWeight(double ppw){
    	pw = ppw;
    }
    
    //updates Costs and Paths for a given point
    //actuates over 8 directions N, NE, E, SE, S, SW, W, NW
    private void updateCosts(int x,int y,double mycost){

	visited[toIndex(x,y)] = true;
	pixelCosts.poll();

//	mycost = mycost;
	//upper right
	if((x< width-1)&&(y>0)){
	    pixelCosts.add(new PixelNode(toIndex(x+1,y-1), mycost+edgeCost(x,y,x+1,y-1),toIndex(x,y)));	    
	}
	//upper left
	if((x>0)&&(y>0)){
	    pixelCosts.add(new PixelNode(toIndex(x-1,y-1), mycost+edgeCost(x,y,x-1,y-1),toIndex(x,y)));	    
	}
	//down right
	if((x< width-1)&&(y<height-1)){
	    pixelCosts.add(new PixelNode(toIndex(x+1,y+1), mycost+edgeCost(x,y,x+1,y+1),toIndex(x,y)));	    
	}
	//down left
	if((x>0)&&(y<height-1)){
	    pixelCosts.add(new PixelNode(toIndex(x-1,y+1), mycost+edgeCost(x,y,x-1,y+1),toIndex(x,y)));	    
	}

	//update left cost
	if(x>0){
	    pixelCosts.add(new PixelNode(toIndex(x-1,y), mycost+edgeCost(x,y,x-1,y),toIndex(x,y)));	    
	}
	//update right cost
	if(x<width-1){
	    pixelCosts.add(new PixelNode(toIndex(x+1,y), mycost+edgeCost(x,y,x+1,y),toIndex(x,y)));
	}
	
	//update up cost
	if(y>0){
	    pixelCosts.add(new PixelNode(toIndex(x,y-1), mycost+edgeCost(x,y,x,y-1),toIndex(x,y)));
	}
	    //update down cost
	if(y<height-1){
	    pixelCosts.add(new PixelNode(toIndex(x,y+1), mycost+edgeCost(x,y,x,y+1),toIndex(x,y)));
	}
    }

    // returns index pointing to next node to be visited
    // It is defined as the minimum cost not yet visited
    // returns -1 if no node is available
    private int findNext(){
	int min = INF;
	int ans = -1;
	for(int y=0;y<height;y++){
	    for(int x=0;x<width;x++){
		if( ( visited   [toIndex(x,y)] == false) &&
		    ( imageCosts[toIndex(x,y)] <  min) ){
		    min = imageCosts[toIndex(x,y)];
		    ans = toIndex(x,y);
		}
	    }
	}
	return ans;
    }

    public void returnPath(int x, int y,int[] vx,int[] vy,int[] mylength){
	//returns the path given mouse position
    	//System.out.println(pCosts[x][y] + " my cost " + gradientr[toIndex(x,y)]+ " grmin " + grmin + " grmax " + grmax);

    	int[] tempx = new int[width*height];
		int[] tempy = new int[width*height];
    	
    	if(visited[toIndex(x,y)]==false){
    		//attempt to get path before creating it 
    		//this might occur because of the thread
    		mylength[0]=0;
    		return;
    	}
    	int length =0;
    	int myx = x;
    	int myy = y;
    	int nextx;
    	int nexty;
    	do{ //while we haven't found the seed	
    		length++;
    		nextx = whereFrom[toIndex(myx,myy)]%width;
    		nexty = whereFrom[toIndex(myx,myy)]/width;
    		myx = nextx;
    		myy = nexty;
    		
    	}while (!((myx==sx)&&(myy==sy)));
    	
    	mylength[0] = length;
    	
    	//add points to vector
    	myx=x;
    	myy=y;
    	int count=0;
    	tempx[0]=myx;//add last points
    	tempy[0]=myy; 
	//	System.out.println("Caminho ");
    	do{ //while we haven't found the seed	    	
    		nextx = whereFrom[toIndex(myx,myy)]%width;
    		nexty = whereFrom[toIndex(myx,myy)]/width;
		//System.out.println("("+nextx+","+nexty+")");
    		
    		count++;
    		tempx[count]=nextx;
    		tempy[count]=nexty; 
    		
    		myx = nextx;
    		myy = nexty;
    		
    	}while (!((myx==sx)&&(myy==sy)));
    	//path is from last point to first
    	//we need to invert it
	//	System.out.println("Caminho ");
    	for(int i=0;i<=count;i++){

    		vx[i]= tempx[count-i];
    		vy[i]=tempy[count-i];    	
		//System.out.println("( "+vx[i] + " , " + vy[i] + " )");
    	}		    	
    	
    	return;    		    	
    	
    }
    public static void main(String[] args){

	//test
	//matrice
	// 2  70  4  0
	// 1  70  2  2
	// 1   1  1  1

	byte[] teste = { 0,	0,	0,	0,	0,	0,	0,	61,	127,	127,
			 0,	0,	0,	0,	0,	0,	0,	12,	127,	127,
			 0,	0,	0,	0,	0,	0,	0,	12,	127,	127,
			 0,	0,	0,	0,	0,	0,	0,	61,	127,	127,
			 0,	0,	0,	0,	0,	0,	0,	126,	127,	127,
			 0,	0,	0,	0,	0,	0,	75,	127,	127,	127,
			 0,	0,	0,	0,	0,	82,	127,	127,	127,	127,
			 25,	4,	25,	79,	126,	127,	127,	127,	127,	127,
			 127,	127,	127,	127,	127,	127,	127,	127,	127,	127,
			 127,	127,	127,	127,	127,	127,	127,	127,	127,	127};




	Dijkstraheap dj = new Dijkstraheap(teste,10,10);
	dj.setPoint(1,7);
	int[] a = new int[10000];
	int[] b = new int[10000];
	int[] c = new int[10];


    	try {
			dj.myThread.join();
		} catch (InterruptedException e) {
			System.out.println("Bogus Exception");
			e.printStackTrace();			
		}	

	dj.returnPath(8,2,a,b,c);

	//	new Thread(dj).start();

	//TSetPoint tsetpoint = new TSetPoint(1);
	//TSetPoint tsetpoint2 = new TSetPoint(2);
	//	new Thread(tsetpoint).run();
	//tsetpoint.run();
	//tsetpoint2.start();

	/*	while(true){
	    System.out.println("Go");
	    }*/
	
		

    }

    //set point to start Dijkstra
    public void setPoint(int x, int y){
	    	
    	myThreadRuns=false;    	    	
    	try {
			myThread.join();
		} catch (InterruptedException e) {
			System.out.println("Bogus Exception");
			e.printStackTrace();			
		}
    	
    	tx = x;	
    	ty = y;	
    	myThreadRuns = true;
    	myThread = new Thread(this);
		myThread.start();		

    }    
    public void run(){
	//runs set point in parallel
	int x = tx;
	int y = ty;

	int nextIndex;
	int nextX;
	int nextY;
	sx = x;
	sy = y;
	
	for(int i=0;i<height*width;i++){
	    //		imageCosts[i]  = INF;
		visited[i]  = false;
	}
	
	
	visited   [toIndex(x,y)] = true; //mark as visited
	//	imageCosts[toIndex(x,y)] = 0; //sets initial point with zero cost
	whereFrom [toIndex(x,y)] = toIndex(x,y);

	

	//update costs
	updateCosts(x,y,0);
	//	nextIndex = findNext();
	//	nextX = nextIndex%width;
	//	nextY = nextIndex/width;
	int debugcount = 0;

	
	while((pixelCosts.peek()!=null)&&(myThreadRuns)){
	    //	    System.out.println("Debug count " + debugcount++);
	    
	    
	    
	    nextIndex = ((PixelNode)pixelCosts.peek()).getIndex();
	    nextX = nextIndex%width;
	    nextY = nextIndex/width;
	    
	    whereFrom[nextIndex] =((PixelNode)pixelCosts.peek()).getWhereFrom();

	    
	    // System.out.println("Head (" + nextX + ","+nextY +") Value " + ((PixelNode) pixelCosts.peek()).getDistance() + " From " + ((PixelNode) pixelCosts.peek()).getWhereFrom());
	    pCosts[nextX][nextY]= ((PixelNode) pixelCosts.peek()).getDistance();

		
	    updateCosts(nextX, nextY, ((PixelNode) pixelCosts.peek()).getDistance());

	    //removes pixels that are already visited and went to the queue
	    while(true){
		if( pixelCosts.peek() == null )
		    break;
		if(visited[ ((PixelNode)pixelCosts.peek()).getIndex() ]==false)
		    break;
		pixelCosts.poll();
	    }
	}
	while(pixelCosts.peek()!=null)
		pixelCosts.poll();
	
	//	System.out.println("Point set.......");
	/*
	System.out.println("");
	for(int j=0;j<height;j++){
	    for(int i=0;i<width;i++){
		System.out.print(imageCosts[j*width+i]+ " ");
	    }
	    System.out.println("");
	    }
	
	System.out.println("Caminhos");
	for(int j=0;j<height;j++){
	    for(int i=0;i<width;i++){
		System.out.print("( " + whereFrom[j*width+i]%width + ", " + whereFrom[j*width+i]/height + ") ");
	    }
	    System.out.println("");
	    }*/
	
    }

	public int getTx() {
		return tx;
	}

	public int getTy() {
		return ty;
	}


		    	


}


//this class was created to store pixel nodes in a Priority Queue
//so that Dijkstra can run on O(n log n)
//The interface Comparable is required so that the Java class PriorityQueue
//could be used
//we could not use a standard PriorityQueue<Integer> cause it would 
//

class PixelNode implements Comparable<PixelNode> {
    private int myIndex;
    private double myDistance;
    private int whereFrom;
    public PixelNode(int index, double distance, int whereFrom){
	myIndex = index;
	myDistance = distance;
	this.whereFrom = whereFrom;
    }
    public double getDistance(){
	return myDistance;
    }
    public int getIndex(){
	return myIndex;
    }
    public int getWhereFrom(){
	return whereFrom;
    }

    public int compareTo(PixelNode other){
	if( myDistance < other.getDistance()) 
	    return -1;
	else if( myDistance > other.getDistance()) 
	    return +1;
	else 
	    return 0;
	//	return (int)((myDistance - other.getDistance()));//plus 0.5 to round
    }

}

