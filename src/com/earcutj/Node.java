package com.earcutj;

final class Node {
	
	/* Member Variables. */
	final int[] mVertex;
	
	/* Previous and mNextNode vertices in the polygon ring. */
	Node  mPreviousNode;
	Node  mNextNode;
	/* Z-Order curve value. */
	Integer  mZOrder;
	
	/* Previous and mNextNode nodes in Z-Order. */
	Node  mPreviousZNode;
	Node  mNextZNode;
	
	Node(final int[] pVertices) {
		/* Initialize Member Variables. */
		this.mVertex     = pVertices;
		this.mPreviousNode  = null;
		this.mNextNode  = null;
		this.mZOrder     = null;
		this.mPreviousZNode = null;
		this.mNextZNode = null;
	}

}
