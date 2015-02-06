package com.earcutj;

public interface IEarcutListener {
	
	/** Callback method called whenever a newly triangulated set of vertices are generated. **/
	public abstract void onTriangleVertex(final int pA0, final int pA1, final int pB0, final int pB1, final int pC0, final int pC1);
	
}
