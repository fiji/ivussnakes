import java.util.PriorityQueue;

class Dijkstraheap{

    byte[] imagePixels; //stores Pixels from original image
    int[] imageCosts; //stores Costs for every pixel
    PriorityQueue<PixelNode> pixelCosts;

    int[] whereFrom;  //stores where from path started
    boolean[] visited; //stores whether the node was marked or not
    int width;
    int height;
    int sx,sy; //seed x and seed y, weight zero for this point
     

    static int INF = 0x7FFFFFFF; //maximum integer

    //converts x, y coordinates to vector index
    private int toIndex(int x,int y){
	return (y*width+x);
    }
    

    //initializes Dijkstra with the image
    public Dijkstraheap(byte[] image,int x, int y){
	//initializes all other matrices
	imagePixels = new byte[x*y];
	//	imageCosts  = new int [x*y];
	pixelCosts = new PriorityQueue<PixelNode>();
	whereFrom   = new int [x*y];
	visited     = new boolean[x*y];

	width  = x;
	height = y;
	
	//copy image matrice
	for(int j=0;j<y;j++){
	    for(int i=0;i<x;i++){
		imagePixels[j*x+i] = image[j*x+i];		
		//imageCosts [j*x+i] = INF;
		visited    [j*x+i] = false;
		//		System.out.print((imagePixels[j*x+i]&0xff)+ " ");
	    }
	    //	    System.out.println("");
	}
    }    
    //returns de cost of going from sx,sy to dx,dy
    private double edgeCost(int sx,int sy,int dx,int dy){
	return (Math.abs((imagePixels[toIndex(sx,sy)]&0xff) - (imagePixels[toIndex(dx,dy)]&0xff))+
		5*Math.sqrt( (dx-sx)*(dx-sx) + (dy-sy)*(dy-sy))
		);

    }
    //updates Costs and Paths for a given point
    //only actuates over North, South, East and West directions
    private void updateCosts(int x,int y,double mycost){

	visited[toIndex(x,y)] = true;
	pixelCosts.poll();

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
	    PixelNode novo = new PixelNode(toIndex(x-1,y), mycost+edgeCost(x,y,x-1,y),toIndex(x,y));
	    try{
		pixelCosts.add(novo);
	    }
	    catch(Exception e){
		System.out.println(e);
	    }

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

    //set point to start Dijkstra
    public void setPoint(int x, int y){
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
	while(pixelCosts.peek()!=null){
	    //	    System.out.println("Debug count " + debugcount++);
	    
	    
	    
	    nextIndex = ((PixelNode)pixelCosts.peek()).getIndex();
	    nextX = nextIndex%width;
	    nextY = nextIndex/width;
	    whereFrom[nextIndex] =((PixelNode)pixelCosts.peek()).getWhereFrom();

	    /*System.out.println("Head " + nextIndex + " Value " + ((PixelNode) pixelCosts.peek()).getDistance() + " From " + ((PixelNode) pixelCosts.peek()).getWhereFrom());*/
		
	    updateCosts(nextX, nextY, ((PixelNode) pixelCosts.peek()).getDistance());

	    while(true){
		if( pixelCosts.peek() == null )
		    break;
		if(visited[ ((PixelNode)pixelCosts.peek()).getIndex() ]==false)
		    break;
		pixelCosts.poll();
	    }
	}
	
	System.out.println("Point set.......");
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
    public void returnPath(int x, int y,int[] vx,int[] vy,int[] mylength){
	//retorna o path dada a posição do mouse
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
    	vx[0]=myx;//add last points
    	vy[0]=myy; 
    	do{ //while we haven't found the seed	    	
    		nextx = whereFrom[toIndex(myx,myy)]%width;
    		nexty = whereFrom[toIndex(myx,myy)]/width;
    		
    		count++;
    		vx[count]=nextx;
    		vy[count]=nexty; 
    		
    		myx = nextx;
    		myy = nexty;
    		
    	}while (!((myx==sx)&&(myy==sy)));
    	
    	return;    		    	
    	
    }
    public static void main(String[] args){

	//test
	//matrice
	// 2  70  4  0
	// 1  70  2  2
	// 1   1  1  1

	byte[] teste = { 2, 70, 4,0,
			 1, 70, 2,2,
			 1, 1, 1, 1};
	Dijkstraheap dj = new Dijkstraheap(teste,4,3);
	dj.setPoint(0,0);
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
	return (int)((myDistance - other.getDistance()+0.5));//plus 0.5 to round
    } 
}