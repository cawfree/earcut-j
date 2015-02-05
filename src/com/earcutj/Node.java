package com.earcutj;

final class Node {
	
	/* Member Variables. */
	int[] p;
	/* Previous and next vertices in the polygon ring. */
	Node  prev;
	Node  next;
	/* Z-Order curve value. */
	Integer  z;
	/* Previous and next nodes in Z-Order. */
	Node  prevZ;
	Node  nextZ;
	
	Node(final int[] pVertices) {
		/* Initialize Member Variables. */
		this.p     = pVertices;
		this.prev  = null;
		this.next  = null;
		this.z     = null;
		this.prevZ = null;
		this.nextZ = null;
	}

}
