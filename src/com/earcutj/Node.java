package com.earcutj;

final class Node {
	
	/* Member Variables. */
	private final int  mX;
	private final int  mY;
	private       int  mZOrder;
	private       Node mPreviousNode;
	private       Node mNextNode;
	private       Node mPreviousZNode;
	private       Node mNextZNode;
	
	protected Node(final int pX, final int pY) {
		/* Initialize Member Variables. */
		this.mX = pX;
		this.mY = pY;
		this.mZOrder        = 0;
		this.mPreviousNode  = null;
		this.mNextNode      = null;
		this.mPreviousZNode = null;
		this.mNextZNode     = null;
	}
	
	protected final int getX() {
		return this.mX;
	}
	
	protected final int getY() {
		return this.mY;
	}
	
	protected final void setPreviousNode(final Node pNode) {
		this.mPreviousNode = pNode;
	}
	
	protected final Node getPreviousNode() {
		return this.mPreviousNode;
	}
	
	protected final void setNextNode(final Node pNode) {
		this.mNextNode = pNode;
	}
	
	protected final Node getNextNode() {
		return this.mNextNode;
	}
	
	protected final void setZOrder(final int pZOrder) {
		this.mZOrder = pZOrder;
	}
	
	protected final int getZOrder() {
		return this.mZOrder;
	}
	
	protected final void setPreviousZNode(final Node pNode) {
		this.mPreviousZNode = pNode;
	}
	
	protected final Node getPreviousZNode() {
		return this.mPreviousZNode;
	}
	
	protected final void setNextZNode(final Node pNode) {
		this.mNextZNode = pNode;
	}
	
	protected final Node getNextZNode() {
		return this.mNextZNode;
	}

}
