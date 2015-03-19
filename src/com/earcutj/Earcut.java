package com.earcutj;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.earcutj.exception.EarcutException;

public final class Earcut {

	private static final boolean NATIVE_FORCE_OPTIMIZATION = true;
	
	static {
		if(Earcut.NATIVE_FORCE_OPTIMIZATION) {
			/** TODO: Force JIT runtime compilation to native code. **/
		}
	}
	
	private static final Comparator<Node> COMPARATOR_SORT_BY_X         = new Comparator<Node>() { @Override public int compare(final Node pNodeA, final Node pNodeB) { return pNodeA.getX() < pNodeB.getX() ? -1 : pNodeA.getX() == pNodeB.getX() ? 0 : 1; } };
	private static final int 			  CONTRACT_HOLES_INDEX		   = 1;
	private static final int              DEFAULT_THRESHOLD_SIMPLICITY = 80;
	private static final int			  DEFAULT_COORDINATE_RANGE     = 1000;
	
	private static enum EEarcutState {
		INIT, CURE, SPLIT;
	}
	
	/** Produces an array of vertices representing the triangulated result set of the Points array. **/
	public static final List<float[][]> earcut(final float[][][] pPoints, final boolean pIsClockwise) {
		/* Attempt to establish a doubly-linked list of the provided Points set, and then filter instances of intersections. */
		Node lOuterNode = Earcut.onFilterPoints(Earcut.onCreateDoublyLinkedList(pPoints[0], pIsClockwise), null, false);
		/* If an outer node hasn't been detected, the input array is malformed. */
		if(lOuterNode == null) {
			throw new EarcutException("Could not process shape!");
		}
		/* Define the TriangleList. */
		final List<float[][]> lTriangleList = new ArrayList<float[][]>();
		/* Declare method dependencies. */
		Node lNode            = null;
		float  lMinimumX        = 0;
		float  lMinimumY        = 0;
		float  lMaximumX        = 0;
		float  lMaximumY        = 0;
		float  lCurrentX        = 0; 
		float  lCurrentY        = 0;
		float  lBoundingBoxSize = 0; 
        int  lThreshold       = Earcut.DEFAULT_THRESHOLD_SIMPLICITY;
        
        /* Determine whether the specified array of points crosses the simplicity threshold. */
        for(int i = 0; lThreshold >= 0 && i < pPoints.length; i++) {
        	lThreshold -= pPoints[i].length;
        }
        
        /* If the shape crosses THRESHOLD_SIMPLICITY, we will use z-order curve hashing, which requires calculation the bounding box for the polygon. */
        if (lThreshold < 0) {
            lNode = lOuterNode.getNextNode();
            lMinimumX = lMaximumX = lNode.getX();
            lMinimumY = lMaximumY = lNode.getY();
            /* Iterate through the doubly-linked list. */
            do {
                lCurrentX = lNode.getX();
                lCurrentY = lNode.getY();
                if (lCurrentX < lMinimumX) {
                	lMinimumX = lCurrentX;
                }
                if (lCurrentY < lMinimumY) {
                	lMinimumY = lCurrentY;
                }
                if (lCurrentX > lMaximumX) {
                	lMaximumX = lCurrentX;
                }
                if (lCurrentY > lMaximumY) {
                	lMaximumY = lCurrentY;
                }
                /* Iterate through to the mNextNode node in the doubly-linked list. */
                lNode = lNode.getNextNode();
                /* Ensure that the doubly-linked list has not yet wrapped around. */
            } while (lNode != lOuterNode);
            
            /* Calculate the BoundingBoxSize. (MinX, MinY and Size are used to tansform co-ordinates into integers for the Z-Order calculation. */
            lBoundingBoxSize = Math.max(lMaximumX - lMinimumX, lMaximumY - lMinimumY);
        }
        
        /* Determine if the specified list of points contains holes. */
        if (pPoints.length > Earcut.CONTRACT_HOLES_INDEX) {
        	/* Eliminate the hole triangulation. */
        	lOuterNode = Earcut.onEliminateHoles(pPoints, lOuterNode, lThreshold < 0);
        }
        
	    if(lThreshold < 0) {
	        /* Link polygon nodes in Z-Order. */
	    	Earcut.onZIndexCurve(lOuterNode, lMinimumX, lMinimumY, lBoundingBoxSize);
	    }
        /* Calculate an Earcut operation on the generated LinkedList. */
        return Earcut.onEarcutLinkedList(lOuterNode, lTriangleList, lMinimumX, lMinimumY, lBoundingBoxSize, EEarcutState.INIT, lThreshold < 0);
	}
	
	/** Links every hole into the outer loop, producing a single-ring polygon without holes. **/
	private static final Node onEliminateHoles(final float[][][] pPoints, Node lOuterNode, final boolean pIsZIndexed) {
		/* Define a list to hole a reference to each filtered hole list. */
		final List<Node> lHoleQueue = new ArrayList<Node>();
		/* Iterate through each array of hole vertices. */
	    for(int i = Earcut.CONTRACT_HOLES_INDEX; i < pPoints.length; i++) {
	    	/* Filter the doubly-linked hole list. */
	        final Node lListNode = Earcut.onFilterPoints(Earcut.onCreateDoublyLinkedList(pPoints[i], false), null, pIsZIndexed);
	        /* Determine if the resulting hole polygon was successful. */
	        if(lListNode != null) {
	        	/* Add the leftmost vertex of the hole. */
	        	lHoleQueue.add(Earcut.onFetchLeftmost(lListNode));
	        }
	    }
	    /* Sort the hole vertices by increasing X. */
	    Collections.sort(lHoleQueue, Earcut.COMPARATOR_SORT_BY_X);
	    /* Process holes from left to right. */
	    for(int i = 0; i < lHoleQueue.size(); i++) {
	    	/* Eliminate hole triangles from the result set. */
	    	Earcut.onEliminateHole(lHoleQueue.get(i), lOuterNode, pIsZIndexed);
	    	/* Filter the new polygon. */
	        lOuterNode = Earcut.onFilterPoints(lOuterNode, lOuterNode.getNextNode(), pIsZIndexed);
	    }
	    /* Return a pointer to the list. */
	    return lOuterNode;
	}
	
	/** Finds a bridge between vertices that connects a hole with an outer ring, and links it. **/
	private static final void onEliminateHole(final Node pHoleNode, Node pOuterNode, final boolean pIsZIndexed) {
		/* Attempt to find a logical bridge between the HoleNode and OuterNode. */
	    pOuterNode = Earcut.onEberlyFetchHoleBridge(pHoleNode, pOuterNode);
	    /* Determine whether a hole bridge could be fetched. */
	    if(pOuterNode != null) {
	    	/* Split the resulting polygon. */
	        Node lNode = Earcut.onSplitPolygon(pOuterNode, pHoleNode);
	        /* Filter the split nodes. */
	        Earcut.onFilterPoints(lNode, lNode.getNextNode(), pIsZIndexed);
	    }
	}
	
	/** David Eberly's algorithm for finding a bridge between a hole and outer polygon. **/
	private static final Node onEberlyFetchHoleBridge(final Node pHoleNode, final Node pOuterNode) { /** TODO: Update earcut accordingly. **/
		Node node = pOuterNode;
		Node		p = pHoleNode;
		float px = p.getX();
		float py = p.getY();
		float qMax = Float.NEGATIVE_INFINITY;
		Node mNode = null;
		Node a, b;
		// find a segment intersected by a ray from the hole's leftmost point to the left;
		// segment's endpoint with lesser x will be potential connection point
		do {
			a = node;
			b = node.getNextNode();
			if (py <= a.getY() && py >= b.getY()) {
				float qx = a.getX() + (py - a.getY()) * (b.getX() - a.getX()) / (b.getY() - a.getY());
				if (qx <= px && qx > qMax) {
					qMax = qx;
					mNode = a.getX() < b.getX() ? node : node.getNextNode();
				}
			}
			node = node.getNextNode();
		} while (node != pOuterNode);
		
		if (mNode == null) return null;
		// look for points strictly inside the triangle of hole point, segment intersection and endpoint;
		// if there are no points found, we have a valid connection;
		// otherwise choose the point of the minimum angle with the ray as connection point
		float bx = mNode.getX(),
		by = mNode.getY(),
		pbd = px * by - py * bx,
		pcd = px * py - py * qMax,
		cpy = py - py,
		pcx = px - qMax,
		pby = py - by,
		bpx = bx - px,
		A = pbd - pcd - (qMax * by - py * bx),
		sign = A <= 0 ? -1 : 1;
		Node stop = mNode;
		float tanMin = Float.POSITIVE_INFINITY,
		mx, my, amx, s, t, tan;
		node = mNode.getNextNode();
		while (node != stop) {
			mx = node.getX();
			my = node.getY();
			amx = px - mx;
			if (amx >= 0 && mx >= bx) {
				s = (cpy * mx + pcx * my - pcd) * sign;
				if (s >= 0) {
					t = (pby * mx + bpx * my + pbd) * sign;
					if (t >= 0 && A * sign - s - t >= 0) {
						tan = Math.abs(py - my) / amx; // tangential
						if (tan < tanMin && Earcut.isLocallyInside(node, pHoleNode)) {
							mNode = node;
							tanMin = tan;
						}
					}
				}
			}
			node = node.getNextNode();
		}
		return mNode;
	}
	
	/** Finds the left-most hole of a polygon ring. **/
	private static final Node onFetchLeftmost(final Node pStart) {
	    Node lNode     = pStart;
	    Node lLeftMost = pStart;
	    do {
	    	/* Determine if the current node possesses a lesser X position. */
	        if (lNode.getX() < lLeftMost.getX()) {
	        	/* Maintain a reference to this Node. */
	        	lLeftMost = lNode;
	        }
	        /* Progress the search to the next node in the doubly-linked list. */
	        lNode = lNode.getNextNode();
	    } while (lNode != pStart);
	    
	    /* Return the node with the smallest X value. */
	    return lLeftMost;
	}
	
	/** Main ear slicing loop which triangulates the vertices of a polygon, provided as a doubly-linked list. **/
	private static final List<float[][]> onEarcutLinkedList(Node lCurrentEar, final List<float[][]> pTriangleList, final float pMinimumX, final float pMinimumY, final float pSize, final EEarcutState pEarcutState, final boolean pIsZIndexed) {
	    if (lCurrentEar == null) {
	    	return pTriangleList;
	    }

	    Node lStop         = lCurrentEar;
	    Node lPreviousNode = null;
	    Node lNextNode     = null;

	    /* Iteratively slice ears. */
	    while (lCurrentEar.getPreviousNode() != lCurrentEar.getNextNode()) {
	        lPreviousNode = lCurrentEar.getPreviousNode();
	        lNextNode = lCurrentEar.getNextNode();

	        /* Determine whether the current triangle must be cut off. */
	        if(Earcut.isEar(lCurrentEar, pMinimumX, pMinimumY, pSize, pIsZIndexed)) {
	        	/* Return the triangulated data back to the Callback. */
	        	pTriangleList.add(new float[][]{ new float[]{ lPreviousNode.getX(), lPreviousNode.getY() }, new float[]{ lCurrentEar.getX(), lCurrentEar.getY() }, new float[]{ lNextNode.getX(), lNextNode.getY() } });
	        	 /* Remove the ear node. */
	            lNextNode.setPreviousNode(lPreviousNode);
	            lPreviousNode.setNextNode(lNextNode);
	            
	            if (lCurrentEar.getPreviousZNode() != null) { lCurrentEar.getPreviousZNode().setNextZNode(lCurrentEar.getNextZNode());     }
	            if (lCurrentEar.getNextZNode()     != null) { lCurrentEar.getNextZNode().setPreviousZNode(lCurrentEar.getPreviousZNode()); }

	            /* Skipping to the next node leaves less slither triangles. */
	            lCurrentEar = lNextNode.getNextNode();
	            lStop = lNextNode.getNextNode();

	            continue;
	        }

	        lCurrentEar = lNextNode;

	        /* If the whole polygon has been iterated over and no more ears can be found. */
	        if (lCurrentEar == lStop) {	            
	            switch(pEarcutState) {
		            case INIT :
			            // try filtering points and slicing again
		            	Earcut.onEarcutLinkedList(Earcut.onFilterPoints(lCurrentEar, null, pIsZIndexed), pTriangleList, pMinimumX, pMinimumY, pSize, EEarcutState.CURE, pIsZIndexed);
		            break;
		            case CURE :
			            // if this didn't work, try curing all small self-intersections locally
		                lCurrentEar = Earcut.onCureLocalIntersections(lCurrentEar, pTriangleList);
		                Earcut.onEarcutLinkedList(lCurrentEar, pTriangleList, pMinimumX, pMinimumY, pSize, EEarcutState.SPLIT, pIsZIndexed);
		            	
		            break;
		            case SPLIT :
		            	// as a last resort, try splitting the remaining polygon into two
		            	Earcut.onSplitEarcut(lCurrentEar, pTriangleList, pMinimumX, pMinimumY, pSize, pIsZIndexed);
		            break;
		        }
	            break;
	        }
	    }
	    /* Return the calculated triangle vertices. */
	    return pTriangleList;
	}
	
	/** Determines whether a polygon node forms a valid ear with adjacent nodes. **/
	private static final boolean isEar(final Node pEar, final float pMinimumX, final float pMinimumY, final float pSize, final boolean pIsZIndexed) {

		float ax = pEar.getPreviousNode().getX(), bx = pEar.getX(), cx = pEar.getNextNode().getX(),
        ay = pEar.getPreviousNode().getY(), by = pEar.getY(), cy = pEar.getNextNode().getY(),

        abd = ax * by - ay * bx,
        acd = ax * cy - ay * cx,
        cbd = cx * by - cy * bx,
        A = abd - acd - cbd;

	    if (A <= 0) return false; // reflex, can't be an ear

	    // now make sure we don't have other points inside the potential ear;
	    // the code below is a bit verbose and repetitive but this is done for performance

	    float cay = cy - ay,
	        acx = ax - cx,
	        aby = ay - by,
	        bax = bx - ax;
	    //int[] p;
	    float px, py, s, t, k;
	    Node node = null;

	    // if we use z-order curve hashing, iterate through the curve
	    if (pIsZIndexed) {

	        // triangle bbox; min & max are calculated like this for speed
	    	float minTX = ax < bx ? (ax < cx ? ax : cx) : (bx < cx ? bx : cx),
	            minTY = ay < by ? (ay < cy ? ay : cy) : (by < cy ? by : cy),
	            maxTX = ax > bx ? (ax > cx ? ax : cx) : (bx > cx ? bx : cx),
	            maxTY = ay > by ? (ay > cy ? ay : cy) : (by > cy ? by : cy),

	            // z-order range for the current triangle bbox;
	            minZ = Earcut.onCalculateZOrder(minTX, minTY, pMinimumX, pMinimumY, pSize),
	            maxZ = Earcut.onCalculateZOrder(maxTX, maxTY, pMinimumX, pMinimumY, pSize);

	        // first look for points inside the triangle in increasing z-order
	        node = pEar.getNextZNode();

	        while (node != null && node.getZOrder() <= maxZ) {

	            px = node.getX();
	            py = node.getY();
	            
	            node = node.getNextZNode();
	            
	            if ((px == ax && py == ay) || (px == cx && py == cy)) continue;


	            s = cay * px + acx * py - acd;
	            if (s >= 0) {
	                t = aby * px + bax * py + abd;
	                if (t >= 0) {
	                    k = A - s - t;
	                    
	                    ;
	                    
	                    float term1 = (s == 0 ? s : t);
	                    float term2 = (s == 0 ? s : k);
	                    float term3 = (t == 0 ? t : k);
	                    
	                    float calculation = (term1 != 0 ? term1 : term2 != 0? term2 : term3); /** TODO: Optimize. **/
	                    
	                    if ((k >= 0) && (calculation != 0)) return false;
	                }
	            }
	        }

	        // then look for points in decreasing z-order
	        node = pEar.getPreviousZNode();

	        while (node != null && node.getZOrder() >= minZ) {
	            
	            px = node.getX();
	            py = node.getY();
	            
	            node = node.getPreviousZNode();
	            if ((px == ax && py == ay) || (px == cx && py == cy)) continue;


	            s = cay * px + acx * py - acd;
	            if (s >= 0) {
	                t = aby * px + bax * py + abd;
	                if (t >= 0) {
	                    k = A - s - t;
	                    
	                    float term1 = (s == 0 ? s : t);
	                    float term2 = (s == 0 ? s : k);
	                    float term3 = (t == 0 ? t : k);
	                    
	                    float calculation = (term1 != 0 ? term1 : term2 != 0? term2 : term3);
	                    
	                    if ((k >= 0) && (calculation != 0)) return false;
	                }
	            }
	        }

	    // if we don't use z-order curve hash, simply iterate through all other points
	    } else {
	        node = pEar.getNextNode().getNextNode();

	        while (node != pEar.getPreviousNode()) {
	        	px = node.getX();
	            py = node.getY();
	            
	            node = node.getNextNode();

	            

	            s = cay * px + acx * py - acd;
	            if (s >= 0) {
	                t = aby * px + bax * py + abd;
	                if (t >= 0) {
	                    k = A - s - t;
	                    float term1 = (s == 0 ? s : t);
	                    float term2 = (s == 0 ? s : k);
	                    float term3 = (t == 0 ? t : k);
	                    
	                    float calculation = (term1 != 0 ? term1 : term2 != 0? term2 : term3);
	                    
	                    if ((k >= 0) && (calculation != 0)) return false;
	                }
	            }
	        }
	    }
	    return true;
	}
	
	/** Iterates through all polygon nodes and cures small local self-intersections. **/
	private static final Node onCureLocalIntersections(Node pStartNode, final List<float[][]> pTriangleList) {
	    Node lNode = pStartNode;
	    do {
	        Node a = lNode.getPreviousNode(),
	            b = lNode.getNextNode().getNextNode();

	        // a self-intersection where edge (v[i-1],v[i]) intersects (v[i+1],v[i+2])
	        if (Earcut.isIntersecting(a.getX(), a.getY(), lNode.getX(), lNode.getY(), lNode.getNextNode().getX(), lNode.getNextNode().getY(), b.getX(), b.getY()) && Earcut.isLocallyInside(a, b) && Earcut.isLocallyInside(b, a)) {
	            /* Return the triangulated vertices to the callback. */
	        	pTriangleList.add(new float[][]{ new float[]{ a.getX(), a.getY() }, new float[]{ lNode.getX(), lNode.getY() }, new float[]{ b.getX(), b.getY() } });
	        	
	            // remove two nodes involved
	            a.setNextNode(b);
	            b.setPreviousNode(a);

	            Node az = lNode.getPreviousZNode();
	            
	            Node bz;
	            
	            if(lNode.getNextZNode() == null) {
	            	bz = lNode.getNextZNode();
	            }
	            else {
	            	bz = lNode.getNextZNode().getNextZNode();
	            }
	            

	            if (az != null) az.setNextZNode(bz);
	            if (bz != null) bz.setPreviousZNode(az);

	            lNode = pStartNode = b;
	        }
	        lNode = lNode.getNextNode();
	    } while (lNode != pStartNode);

	    return lNode;
	}
	
	/** Tries to split a polygon and triangulate each side independently. **/
	private static final void onSplitEarcut(final Node pStart, final List<float[][]> pTriangleList, final float pMinimumX, final float pMinimumY, final float pSize, final boolean pIsZIndexed) {
	   /* Search for a valid diagonal that divides the polygon into two. */
		Node lSearchNode = pStart;
	    do {
	    	Node lDiagonal = lSearchNode.getNextNode().getNextNode();
	        while (lDiagonal != lSearchNode.getPreviousNode()) {
	            if(Earcut.isValidDiagonal(lSearchNode, lDiagonal)) {
	            	/* Split the polygon into two at the point of the diagonal. */
	            	Node lSplitNode = Earcut.onSplitPolygon(lSearchNode, lDiagonal);
	            	/* Filter the resulting polygon. */
	                lSearchNode = Earcut.onFilterPoints(lSearchNode, lSearchNode.getNextNode(), pIsZIndexed);
	                lSplitNode  = Earcut.onFilterPoints(lSplitNode, lSplitNode.getNextNode(), pIsZIndexed);
	                /* Attempt to earcut both of the resulting polygons. */
	                Earcut.onEarcutLinkedList(lSearchNode, pTriangleList, pMinimumX, pMinimumY, pSize, EEarcutState.INIT, pIsZIndexed);
	                Earcut.onEarcutLinkedList(lSplitNode,  pTriangleList, pMinimumX, pMinimumY, pSize, EEarcutState.INIT, pIsZIndexed);
	                /* Finish the iterative search. */
	                return;
	            }
	            lDiagonal = lDiagonal.getNextNode();
	        }
	        lSearchNode = lSearchNode.getNextNode();
	    } while (lSearchNode != pStart);
	}
	
	/** Links two polygon vertices using a bridge. **/
	private static final Node onSplitPolygon(final Node pNodeA, final Node pNodeB) {
		final Node a2 = new Node(pNodeA.getX(), pNodeA.getY());
		final Node b2 = new Node(pNodeB.getX(), pNodeB.getY());
		final Node an = pNodeA.getNextNode();
		final Node bp = pNodeB.getPreviousNode();

	    pNodeA.setNextNode(pNodeB);
	    pNodeB.setPreviousNode(pNodeA);
	    a2.setNextNode(an);
	    an.setPreviousNode(a2);
	    b2.setNextNode(a2);
	    a2.setPreviousNode(b2);
	    bp.setNextNode(b2);
	    b2.setPreviousNode(bp);

	    return b2;
	}
	
	/** Determines whether a diagonal between two polygon nodes lies within a polygon interior. (This determines the validity of the ray.) **/
	private static final boolean isValidDiagonal(final Node pNodeA, final Node pNodeB) {
	    return !Earcut.isIntersectingPolygon(pNodeA, pNodeA.getX(), pNodeA.getY(), pNodeB.getX(), pNodeB.getY()) && Earcut.isLocallyInside(pNodeA, pNodeB) && Earcut.isLocallyInside(pNodeB, pNodeA) && Earcut.onMiddleInsert(pNodeA, pNodeA.getX(), pNodeA.getY(), pNodeB.getX(), pNodeB.getY());
	}
	
	/** Determines whether a polygon diagonal rests locally within a polygon. **/
	private static final boolean isLocallyInside(final Node pNodeA, final Node pNodeB) {
	    return Earcut.onCalculateWindingOrder(pNodeA.getPreviousNode().getX(), pNodeA.getPreviousNode().getY(), pNodeA.getX(), pNodeA.getY(), pNodeA.getNextNode().getX(), pNodeA.getNextNode().getY()) == EWindingOrder.CCW ? Earcut.onCalculateWindingOrder(pNodeA.getX(), pNodeA.getY(), pNodeB.getX(), pNodeB.getY(), pNodeA.getNextNode().getX(), pNodeA.getNextNode().getY()) != EWindingOrder.CCW && Earcut.onCalculateWindingOrder(pNodeA.getX(), pNodeA.getY(), pNodeA.getPreviousNode().getX(), pNodeA.getPreviousNode().getY(), pNodeB.getX(), pNodeB.getY()) != EWindingOrder.CCW : Earcut.onCalculateWindingOrder(pNodeA.getX(), pNodeA.getY(), pNodeB.getX(), pNodeB.getY(), pNodeA.getPreviousNode().getX(), pNodeA.getPreviousNode().getY()) == EWindingOrder.CCW || Earcut.onCalculateWindingOrder(pNodeA.getX(), pNodeA.getY(), pNodeA.getNextNode().getX(), pNodeA.getNextNode().getY(), pNodeB.getX(), pNodeB.getY()) == EWindingOrder.CCW;
	}
	
	/** Determines whether the middle point of a polygon diagonal is contained within the polygon. **/
	private static final boolean onMiddleInsert(final Node pPolygonStart, final float pX0, final float pY0, final float pX1, final float pY1) {
	    Node    lNode     = pPolygonStart;
	    boolean lIsInside = false;
	    float lDx = (pX0 + pX1) / 2.0f;
	    float lDy = (pY0 + pY1) / 2.0f;
	    do {
	        if (((lNode.getY() > lDy) != (lNode.getNextNode().getY() > lDy)) && (lDx < (lNode.getNextNode().getX() - lNode.getX()) * (lDy - lNode.getY()) / (lNode.getNextNode().getY() - lNode.getY()) + lNode.getX())) {
	        	lIsInside = !lIsInside;
	        }
	        lNode = lNode.getNextNode();
	    } while (lNode != pPolygonStart);
	    return lIsInside;
	}
	
	/** Determines if the diagonal of a polygon is intersecting with any polygon elements. **/
	private static final boolean isIntersectingPolygon(final Node pStartNode, final float pX0, final float pY0, final float pX1, final float pY1) {
	    Node lNode = pStartNode;
	    do {
	        if(lNode.getX() != pX0 && lNode.getY() != pY0 && lNode.getNextNode().getX() != pX0 && lNode.getNextNode().getY() != pY0 && lNode.getX() != pX1 && lNode.getY() != pY1 && lNode.getNextNode().getX() != pX1 && lNode.getNextNode().getY() != pY1 && Earcut.isIntersecting(lNode.getX(), lNode.getY(), lNode.getNextNode().getX(), lNode.getNextNode().getY(), pX0, pY0, pX1, pY1)) {
	        	return true;
	        }
	        lNode = lNode.getNextNode();
	    } while (lNode != pStartNode);

	    return false;
	}
	
	/** Determines whether two segments intersect. **/
	private static final boolean isIntersecting(final float pX0, final float pY0, final float pX1, final float pY1, final float pX2, final float pY2, final float pX3, final float pY3) {
	    return Earcut.onCalculateWindingOrder(pX0, pY0, pX1, pY1, pX2, pY2) != Earcut.onCalculateWindingOrder(pX0, pY0, pX1, pY1, pX3, pY3) && Earcut.onCalculateWindingOrder(pX2, pY2, pX3, pY3, pX0, pY0) != Earcut.onCalculateWindingOrder(pX2, pY2, pX3, pY3, pX1, pY1);
	}
	
	/** Interlinks polygon nodes in Z-Order. **/
	private static final void onZIndexCurve(Node pStartNode, final float pMinimumX, final float pMinimumY, final float pSize) {
	    Node lNode = pStartNode;
	    
	    do {
	    	lNode.setZOrder(Earcut.onCalculateZOrder(lNode.getX(), lNode.getY(), pMinimumX, pMinimumY, pSize));
	        lNode.setPreviousZNode(lNode.getPreviousNode());
	        lNode.setNextZNode(lNode.getNextNode());
	        lNode = lNode.getNextNode();
	    } while (lNode != pStartNode);

	    lNode.getPreviousZNode().setNextZNode(null);
	    lNode.setPreviousZNode(null);
	    
	    /* Sort the generated ring using Z ordering. */
	    Earcut.onTathamZSortList(lNode);
	}
	
	/** Simon Tatham's doubly-linked list merge/sort algorithm. (http://www.chiark.greenend.org.uk/~sgtatham/algorithms/listsort.html) **/
	private static final Node onTathamZSortList(Node pList) {
	    int i;
	    Node p;
	    Node q;
	    Node e; 
	    Node tail;
	    int numMerges;
	    int pSize;
	    int qSize;
	    int inSize = 1;

	    while (true) {
	        p = pList;
	        pList = null;
	        tail = null;
	        numMerges = 0;

	        while(p != null) {
	            numMerges++;
	            q = p;
	            pSize = 0;
	            for (i = 0; i < inSize; i++) {
	                pSize++;
	                q = q.getNextZNode();
	                if (q == null) break;
	            }

	            qSize = inSize;

	            while (pSize > 0 || (qSize > 0 && q != null)) {

	                if (pSize == 0) {
	                    e = q;
	                    q = q.getNextZNode();
	                    qSize--;
	                } else if (qSize == 0 || q == null) {
	                    e = p;
	                    p = p.getNextZNode();
	                    pSize--;
	                } else if (p.getZOrder() <= q.getZOrder()) {
	                    e = p;
	                    p = p.getNextZNode();
	                    pSize--;
	                } else {
	                    e = q;
	                    q = q.getNextZNode();
	                    qSize--;
	                }

	                if (tail != null) tail.setNextZNode(e);
	                else pList = e;

	                e.setPreviousZNode(tail);
	                tail = e;
	            }

	            p = q;
	        }

	        tail.setNextZNode(null);

	        if (numMerges <= 1) return pList;

	        inSize *= 2;
	    }
	}
	
	/** Calculates the Z-Order of a given point given the vertex co-ordinates and size of the bounding box. **/
	private static final int onCalculateZOrder(final float pX, final float pY, final float pMinimumX, final float pMinimumY, final float pSize) {
		/* Transform the co-ordinate set onto a (0 -> DEFAULT_COORDINATE_RANGE) Integer range. */
	    int lX = (int)(Earcut.DEFAULT_COORDINATE_RANGE * (pX - pMinimumX) / pSize);
	    lX = (lX | (lX << 8)) & 0x00FF00FF;
	    lX = (lX | (lX << 4)) & 0x0F0F0F0F;
	    lX = (lX | (lX << 2)) & 0x33333333;
	    lX = (lX | (lX << 1)) & 0x55555555;
	    int lY = (int)(Earcut.DEFAULT_COORDINATE_RANGE * (pY - pMinimumY) / pSize);
	    lY = (lY | (lY << 8)) & 0x00FF00FF;
	    lY = (lY | (lY << 4)) & 0x0F0F0F0F;
	    lY = (lY | (lY << 2)) & 0x33333333;
	    lY = (lY | (lY << 1)) & 0x55555555;
	    /* Returned the scaled co-ordinates. */
	    return lX | (lY << 1);
	}
	
	/** Creates a circular doubly linked list using polygon points. The order is governed by the specified winding order. **/
	private static final Node onCreateDoublyLinkedList(final float[][] pPoints, final boolean pIsClockwise) {
		int lWindingSum = 0;
		float[] p1;
		float[] p2;
		Node lLastNode = null;
		
		/* Calculate the original order of the Polygon ring. */
	    for(int i = 0, j = pPoints.length - 1; i < pPoints.length; j = i++) {
	        p1 = pPoints[i];
	        p2 = pPoints[j];
	        lWindingSum += (p2[0] - p1[0]) * (p1[1] + p2[1]);
	    }
	    /* Link points into the circular doubly-linked list in the specified winding order. */
	    if (pIsClockwise == (lWindingSum > 0)) {
	        for(int i = 0; i < pPoints.length; i++) {
	        	lLastNode = Earcut.onInsertNode(pPoints[i][0], pPoints[i][1], lLastNode);
	        }
	    } else {
	        for(int i = pPoints.length - 1; i >= 0; i--) {
	        	lLastNode = Earcut.onInsertNode(pPoints[i][0], pPoints[i][1], lLastNode);
	        }
	    }
	    /* Return the last node in the Doubly-Linked List. */
	    return lLastNode;
	}
	
	/** Eliminates colinear/duplicate points. **/
	private static final Node onFilterPoints(final Node pStartNode, Node pEndNode, final boolean pIsZIndexed) {
		if(pEndNode == null) {
			pEndNode = pStartNode;
		}

	    Node    lNode              = pStartNode;
	    boolean lContinueIteration = false;
	    
	    do {
	        lContinueIteration = false;

	        if (Earcut.isVertexEquals(lNode.getX(), lNode.getY(), lNode.getNextNode().getX(), lNode.getNextNode().getY()) || Earcut.onCalculateWindingOrder(lNode.getPreviousNode().getX(), lNode.getPreviousNode().getY(), lNode.getX(), lNode.getY(), lNode.getNextNode().getX(), lNode.getNextNode().getY()) == EWindingOrder.COLINEAR) {

	        	/* Remove the node. */
	            lNode.getPreviousNode().setNextNode(lNode.getNextNode());
	            lNode.getNextNode().setPreviousNode(lNode.getPreviousNode());
	            /* Remove the corresponding Z-Index nodes. */
	            
	            if(lNode.getPreviousZNode() != null) {
	            	lNode.getPreviousZNode().setNextZNode(lNode.getNextZNode()); 
	            }
	            if(lNode.getNextZNode() != null) {
	            	lNode.getNextZNode().setPreviousZNode(lNode.getPreviousZNode());
	            }

	            lNode = pEndNode = lNode.getPreviousNode();

	            if (lNode == lNode.getNextNode()) return null;
	            lContinueIteration = true;

	        } else {
	            lNode = lNode.getNextNode();
	        }
	    } while (lContinueIteration || lNode != pEndNode);

	    return pEndNode;
	}
	
	/** Creates a node and optionally links it with a previous node in a circular doubly-linked list. **/
	private static final Node onInsertNode(final float pX, final float pY, final Node pLastNode) {
	    final Node lNode = new Node(pX, pY);
	    if(pLastNode == null) {
	        lNode.setPreviousNode(lNode);
	        lNode.setNextNode(lNode);

	    } else {
	        lNode.setNextNode(pLastNode.getNextNode());
	        lNode.setPreviousNode(pLastNode);
	        pLastNode.getNextNode().setPreviousNode(lNode);
	        pLastNode.setNextNode(lNode);
	    }
	    return lNode;
	}
	
	/** Determines if two point vertices are equal. **/
	private static final boolean isVertexEquals(final float pX0, final float pY0, final float pX1, final float pY1) {
	    return pX0 == pX1 && pY0 == pY1;
	}
	
	/** Calculates the WindingOrder for a set of vertices. **/
	private static final EWindingOrder onCalculateWindingOrder(final float pX0, final float pY0, final float pX1, final float pY1, final float pX2, final float pY2) {
	    final float lCross = (pY1 - pY0) * (pX2 - pX1) - (pX1 - pX0) * (pY2 - pY1);
	    return      lCross > 0 ? EWindingOrder.CW : lCross < 0 ? EWindingOrder.CCW : EWindingOrder.COLINEAR;
	}
	
	/* Prevent instantiation of this class. */
	private Earcut() {}

}
