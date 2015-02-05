package com.earcutj.test;

import java.util.List;

import com.earcutj.Earcut;

public final class MainActivity {

	public static final void main(final String[] pArgs) {
		
		/* Input should be an array of rings, where the first is outer ring and others are holes; each ring is an array of points, where each point is of the [x, y] form. */
		final int[][][] lTestData = new int[][][]{new int[][]{new int[]{10, 0}, new int[]{0, 50}, new int[]{60, 60}, new int[]{70, 10}}};
		/* Process the TestData. */
		final List<int[]> lPoints = Earcut.earcut(lTestData);
		
		for(int i = 0; i < lPoints.size(); i++) {
			for(int j = 0; j < lPoints.get(i).length; j++) {
				System.out.println(lPoints.get(i)[j]);
			}
		}
		
	}
	
}
