
class Dijkstra{

    byte[] imagePixels; //stores Pixels from original image
    int[] imageCosts; //stores Costs for every pixel
    int[] whereFrom;  //stores where from path started
    boolean[] visited; //stores weather the node was marked or not
    int width;
    int height;
    int sx,sy; //seed x and seed y, weight zero for this point
     

    static int INF = 0x7FFFFFFF; //maximum integer

    //converts x, y coordinates to vector index
    private int toIndex(int x,int y){
	return (y*width+x);
    }
    

    //initializes Dijkstra with the image
    public Dijkstra(byte[] image,int x, int y){
	//initializes all other matrices
	imagePixels = new byte[x*y];
	imageCosts  = new int [x*y];
	whereFrom   = new int [x*y];
	visited     = new boolean[x*y];

	width  = x;
	height = y;
	
	//copy image matrice
	for(int j=0;j<y;j++){
	    for(int i=0;i<x;i++){
		imagePixels[j*x+i] = image[j*x+i];		
		imageCosts [j*x+i] = INF;
		visited    [j*x+i] = false;
		System.out.print((imagePixels[j*x+i]&0xff)+ " ");
	    }
	    System.out.println("");
	}
    }    
    //returns de cost of going from sx,sy to dx,dy
    private int edgeCost(int sx,int sy,int dx,int dy){
	return (Math.abs((imagePixels[toIndex(sx,sy)]&0xff) - (imagePixels[toIndex(dx,dy)]&0xff)));

    }
    //updates Costs and Paths for a given point
    //only actuates over North, South, East and West directions
    private void updateCosts(int x,int y){

	visited[toIndex(x,y)] = true;
	//update left cost
	if(x>0){
	    if(imageCosts[toIndex(x,y)] + edgeCost(x,y,x-1,y) < imageCosts[toIndex(x-1,y)]){
		imageCosts[toIndex(x-1,y)] = imageCosts[toIndex(x,y)] + edgeCost(x,y,x-1,y);
		whereFrom[toIndex(x-1,y)] = toIndex(x,y);
	    }
	}
	//update right cost
	if(x<width-1){
	    if(imageCosts[toIndex(x,y)] + edgeCost(x,y,x+1,y) < imageCosts[toIndex(x+1,y)]){
		imageCosts[toIndex(x+1,y)] = imageCosts[toIndex(x,y)] + edgeCost(x,y,x+1,y);
		whereFrom[toIndex(x+1,y)] = toIndex(x,y);
	    }
	}
	
	//update up cost
	if(y>0){
	    if(imageCosts[toIndex(x,y)] + edgeCost(x,y,x,y-1) < imageCosts[toIndex(x,y-1)]){
		imageCosts[toIndex(x,y-1)] = imageCosts[toIndex(x,y)] + edgeCost(x,y,x,y-1);
		whereFrom[toIndex(x,y-1)] = toIndex(x,y);
	    }
	}
	//update down cost
	if(y<height-1){
	    if(imageCosts[toIndex(x,y)] + edgeCost(x,y,x,y+1) < imageCosts[toIndex(x,y+1)]){
		imageCosts[toIndex(x,y+1)] = imageCosts[toIndex(x,y)] + edgeCost(x,y,x,y+1);
		whereFrom[toIndex(x,y+1)] = toIndex(x,y);
	    }	    
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
		imageCosts[i]  = INF;
		visited[i]  = false;
	}
	
	
	visited   [toIndex(x,y)] = true; //mark as visited
	imageCosts[toIndex(x,y)] = 0; //sets initial point with zero cost
	whereFrom [toIndex(x,y)] = toIndex(x,y);

	

	//update costs
	updateCosts(x,y);
	nextIndex = findNext();
	nextX = nextIndex%width;
	nextY = nextIndex/width;
	int debugcount = 0;
	while(nextIndex!=-1){
		System.out.println("Debug count " + debugcount++);
	    updateCosts(nextX, nextY);
	    nextIndex = findNext();
	    nextX = nextIndex%width;
	    nextY = nextIndex/width;
	}
	
	System.out.println("Point set");
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
	}
	*/	
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
	// 2  70  4 
	// 1  70  2
	// 1   1  1

	byte[] teste = { 2, 70, 4,
			 1, 70, 2,
			 1, 1, 1};
	Dijkstra dj = new Dijkstra(teste,3,3);
	dj.setPoint(0,0);
    }
}