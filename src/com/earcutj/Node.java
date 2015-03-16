package com.earcutj;

final class Node {
	
	/* Member Variables. */
	private final float mX;
	private final float mY;
	private       int   mZOrder;
	private       Node  mPreviousNode;
	private       Node  mNextNode;
	private       Node  mPreviousZNode;
	private       Node  mNextZNode;
	
	protected Node(final float pX, final float pY) {
		/* Initialize Member Variables. */
		this.mX = pX;
		this.mY = pY;
		this.mZOrder        = 0;
		this.mPreviousNode  = null;
		this.mNextNode      = null;
		this.mPreviousZNode = null;
		this.mNextZNode     = null;
	}
	
	protected final float getX() {
		return this.mX;
	}
	
	protected final float getY() {
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
