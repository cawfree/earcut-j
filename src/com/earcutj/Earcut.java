package com.earcutj;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.earcutj.exception.EarcutException;

public final class Earcut {
	
	private static final Comparator<Node> COMPARATOR_SORT_BY_X         = new Comparator<Node>() { @Override public int compare(final Node pNodeA, final Node pNodeB) { return pNodeA.mVertex[0] < pNodeB.mVertex[0] ? -1 : pNodeA.mVertex[0] == pNodeB.mVertex[0] ? 0 : 1; } };
	private static final int              DEFAULT_THRESHOLD_SIMPLICITY = 80;
	
	/** Produces an array of vertices representing the triangulated result set of the Points array. **/
	public static final void earcut(final int[][][] pPoints, final boolean pIsClockwise, final IEarcutListener pEarcutListener) {
		/* Attempt to establish a doubly-linked list of the provided Points set, and then filter instances of intersections. */
		Node lOuterNode = Earcut.onFilterPoints(Earcut.onCreateDoublyLinkedList(pPoints[0], pIsClockwise), null);
		/* If an outer node hasn't been detected, the input array is malformed. */
		if(lOuterNode == null) {
			throw new EarcutException("Could not process shape!");
		}
		/* Declare method dependencies. */
		Node    lNode        = null;
		Integer lMinimumX    = null; /** TODO: Autboxing is used to emulate Javascript 'undefined' parameters implementation. Avoid? **/
		
		int lMinimumY        = 0;
		int lMaximumX        = 0;
		int lMaximumY        = 0;
		int lCurrentX        = 0; 
		int lCurrentY        = 0;
		int lBoundingBoxSize = 0; 
        int lThreshold       = Earcut.DEFAULT_THRESHOLD_SIMPLICITY;
        
        /* Determine whether the specified array of points crosses the simplicity threshold. */
        for(int i = 0; lThreshold >= 0 && i < pPoints.length; i++) {
        	lThreshold -= pPoints[i].length;
        }
        
        /** TODO: Abstract to boolean useCurveHashing. **/
        /* If the shape crosses THRESHOLD_SIMPLICITY, we will use z-order curve hashing, which requires calculation the bounding box for the polygon. */
        if (lThreshold < 0) {
            lNode = lOuterNode.mNextNode;
            lMinimumX = lMaximumX = lNode.mVertex[0];
            lMinimumY = lMaximumY = lNode.mVertex[1];
            /* Iterate through the doubly-linked list. */
            do {
                lCurrentX = lNode.mVertex[0];
                lCurrentY = lNode.mVertex[1];
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
                lNode = lNode.mNextNode;
                /* Ensure that the doubly-linked list has not yet wrapped around. */
            } while (lNode != lOuterNode);
            /* Calculate the BoundingBoxSize. (MinX, MinY and Size are used to tansform co-ordinates into integers for the Z-Order calculation. */
            lBoundingBoxSize = Math.max(lMaximumX - lMinimumX, lMaximumY - lMinimumY);
        }
        
        /* Determine if the specified list of points contains holes. */
        if (pPoints.length > 1) {
        	lOuterNode = Earcut.onEliminateHoles(pPoints, lOuterNode);
        }
        
        /* Calculate an Earcut operation on the LinkedList. */
        Earcut.onEarcutLinkedList(lOuterNode, pEarcutListener, lMinimumX, lMinimumY, lBoundingBoxSize, null); /** TODO: Should be a second type of method call. **/
	}
	
	// link every hole into the outer loop, producing a single-ring polygon without holes
	private static final Node onEliminateHoles(int[][][] points, Node outerNode) {
	    int len = points.length;
	    int i;
	    
	    //var queue = [];
	    
	    List<Node> queue = new ArrayList<Node>();
	    for (i = 1; i < len; i++) {
	        Node list = Earcut.onFilterPoints(onCreateDoublyLinkedList(points[i], false), null);
	        if (list != null) queue.add(onFetchLeftmost(list));
	    }
	    
	    Collections.sort(queue, Earcut.COMPARATOR_SORT_BY_X);

	    // process holes from left to right
	    for (i = 0; i < queue.size(); i++) {
	    	Earcut.onEliminateHole(queue.get(i), outerNode);
	        outerNode = Earcut.onFilterPoints(outerNode, outerNode.mNextNode);
	    }

	    return outerNode;
	}
	
	// find a bridge between vertices that connects hole with an outer ring and and link it
	private static final void onEliminateHole(Node holeNode, Node outerNode) {
	    outerNode = Earcut.onEberlyFetchHoldBridge(holeNode, outerNode);
	    if (outerNode != null) {
	        Node b = Earcut.onSplitPolygon(outerNode, holeNode);
	        Earcut.onFilterPoints(b, b.mNextNode);
	    }
	}
	
	// David Eberly's algorithm for finding a bridge between hole and outer polygon
	private static final Node onEberlyFetchHoldBridge(Node holeNode, Node outerNode) {
	    Node node = outerNode;
	    int[]    p = holeNode.mVertex;
	    int   px = p[0],
	        py = p[1],
	        qMax = Integer.MIN_VALUE;
	    Node mNode = null;
	    int[] a, b;

	    // find a segment intersected by a ray from the hole's leftmost point to the left;
	    // segment's endpoint with lesser x will be potential connection point
	    do {
	        a = node.mVertex;
	        b = node.mNextNode.mVertex;

	        if (py <= a[1] && py >= b[1]) {
	            int qx = a[0] + (py - a[1]) * (b[0] - a[0]) / (b[1] - a[1]);
	            if (qx <= px && qx > qMax) {
	                qMax = qx;
	                mNode = a[0] < b[0] ? node : node.mNextNode;
	            }
	        }
	        node = node.mNextNode;
	    } while (node != outerNode);

	    if (mNode == null) return null;

	    // look for points strictly inside the triangle of hole point, segment intersection and endpoint;
	    // if there are no points found, we have a valid connection;
	    // otherwise choose the point of the minimum angle with the ray as connection point

	    int bx = mNode.mVertex[0],
	        by = mNode.mVertex[1],
	        pbd = px * by - py * bx,
	        pcd = px * py - py * qMax,
	        cpy = py - py,
	        pcx = px - qMax,
	        pby = py - by,
	        bpx = bx - px,
	        A = pbd - pcd - (qMax * by - py * bx),
	        sign = A <= 0 ? -1 : 1;
	        Node stop = mNode;
	        int tanMin = Integer.MAX_VALUE,
	        mx, my, amx, s, t, tan;

	    node = mNode.mNextNode;

	    while (node != stop) {

	        mx = node.mVertex[0];
	        my = node.mVertex[1];
	        amx = px - mx;

	        if (amx >= 0 && mx >= bx) {
	            s = (cpy * mx + pcx * my - pcd) * sign;
	            if (s >= 0) {
	                t = (pby * mx + bpx * my + pbd) * sign;

	                if (t >= 0 && A * sign - s - t >= 0) {
	                    tan = Math.abs(py - my) / amx; // tangential
	                    if (tan < tanMin && isLocallyInside(node, holeNode)) {
	                        mNode = node;
	                        tanMin = tan;
	                    }
	                }
	            }
	        }

	        node = node.mNextNode;
	    }

	    return mNode;
	}
	
	// find the leftmost node of a polygon ring
	private static final Node onFetchLeftmost(final Node start) {
	    Node node = start,
	        leftmost = start;
	    do {
	        if (node.mVertex[0] < leftmost.mVertex[0]) leftmost = node;
	        node = node.mNextNode;
	    } while (node != start);

	    return leftmost;
	}
	
	// main ear slicing loop which triangulates a polygon (given as a linked list)
	private static final void onEarcutLinkedList(Node lCurrentEar, final IEarcutListener pEarcutListener, final Integer minX, final int minY, final int size, final Integer pIterationNumber) { /** TODO: Avoid an autoboxing based solution. **/
	    if (lCurrentEar == null) {
	    	return;
	    }

	    // interlink polygon nodes in z-order
	    if (pIterationNumber == null && minX != null) {
	    	Earcut.onIndexCurve(lCurrentEar, minX, minY, size);
	    }

	    Node lStop = lCurrentEar;
	    Node prev = null;
	    Node next = null;

	    // iterate through ears, slicing them one by one
	    while (lCurrentEar.mPreviousNode != lCurrentEar.mNextNode) {
	        prev = lCurrentEar.mPreviousNode;
	        next = lCurrentEar.mNextNode;

	        /* Determine whether the current triangle must be cut off. */
	        if(Earcut.isEar(lCurrentEar, minX, minY, size)) {
	            
	        	/* Return the triangulated data back to the Callback. */
	        	pEarcutListener.onTriangleVertex(prev.mVertex[0], prev.mVertex[1], lCurrentEar.mVertex[0], lCurrentEar.mVertex[1], next.mVertex[0], next.mVertex[1]);
	            

	            // remove ear node
	            next.mPreviousNode = prev;
	            prev.mNextNode = next;

	            if (lCurrentEar.mPreviousZNode != null) lCurrentEar.mPreviousZNode.mNextZNode = lCurrentEar.mNextZNode;
	            if (lCurrentEar.mNextZNode != null) lCurrentEar.mNextZNode.mPreviousZNode = lCurrentEar.mPreviousZNode;

	            // skipping the mNextNode vertice leads to less sliver triangles
	            lCurrentEar = next.mNextNode;
	            lStop = next.mNextNode;

	            continue;
	        }

	        lCurrentEar = next;

	        // if we looped through the whole remaining polygon and can't find any more ears
	        if (lCurrentEar == lStop) {
	            // try filtering points and slicing again
	            if (pIterationNumber == null) {
	            	Earcut.onEarcutLinkedList(onFilterPoints(lCurrentEar, null), pEarcutListener, minX, minY, size, 1);
	            }

	            // if this didn't work, try curing all small self-intersections locally
	            else if (pIterationNumber == 1) {
	                lCurrentEar = Earcut.onCureLocalIntersections(lCurrentEar, pEarcutListener);
	                Earcut.onEarcutLinkedList(lCurrentEar, pEarcutListener, minX, minY, size, 2);

	            // as a last resort, try splitting the remaining polygon into two
	            } else if (pIterationNumber == 2) Earcut.onSplitEarcut(lCurrentEar, pEarcutListener, minX, minY, size);

	            break;
	        }
	    }
	}
	
	// check whether a polygon node forms a valid ear with adjacent nodes
	private static final boolean isEar(Node ear, Integer minX, int minY, int size) {

	    int[] a = ear.mPreviousNode.mVertex,
	        b = ear.mVertex,
	        c = ear.mNextNode.mVertex;

	        int ax = a[0], bx = b[0], cx = c[0],
	        ay = a[1], by = b[1], cy = c[1],

	        abd = ax * by - ay * bx,
	        acd = ax * cy - ay * cx,
	        cbd = cx * by - cy * bx,
	        A = abd - acd - cbd;

	    if (A <= 0) return false; // reflex, can't be an ear

	    // now make sure we don't have other points inside the potential ear;
	    // the code below is a bit verbose and repetitive but this is done for performance

	    int cay = cy - ay,
	        acx = ax - cx,
	        aby = ay - by,
	        bax = bx - ax;
	    int[] p;
	    int px, py, s, t, k;
	    Node node = null;

	    // if we use z-order curve hashing, iterate through the curve
	    if (minX != null) {

	        // triangle bbox; min & max are calculated like this for speed
	        int minTX = ax < bx ? (ax < cx ? ax : cx) : (bx < cx ? bx : cx),
	            minTY = ay < by ? (ay < cy ? ay : cy) : (by < cy ? by : cy),
	            maxTX = ax > bx ? (ax > cx ? ax : cx) : (bx > cx ? bx : cx),
	            maxTY = ay > by ? (ay > cy ? ay : cy) : (by > cy ? by : cy),

	            // z-order range for the current triangle bbox;
	            minZ = Earcut.onCalculateZOrder(minTX, minTY, minX, minY, size),
	            maxZ = Earcut.onCalculateZOrder(maxTX, maxTY, minX, minY, size);

	        // first look for points inside the triangle in increasing z-order
	        node = ear.mNextZNode;

	        while (node != null && node.mZOrder <= maxZ) {
	            p = node.mVertex;
	            node = node.mNextZNode;
	            if (p == a || p == c) continue;

	            px = p[0];
	            py = p[1];

	            s = cay * px + acx * py - acd;
	            if (s >= 0) {
	                t = aby * px + bax * py + abd;
	                if (t >= 0) {
	                    k = A - s - t;
	                    
	                    ;
	                    
	                    int term1 = (s == 0 ? s : t);
	                    int term2 = (s == 0 ? s : k);
	                    int term3 = (t == 0 ? t : k);
	                    
	                    int calculation = (term1 != 0 ? term1 : term2 != 0? term2 : term3);
	                    
	                    if ((k >= 0) && (calculation != 0)) return false;
	                }
	            }
	        }

	        // then look for points in decreasing z-order
	        node = ear.mPreviousZNode;

	        while (node != null && node.mZOrder >= minZ) {
	            p = node.mVertex;
	            node = node.mPreviousZNode;
	            if (p == a || p == c) continue;

	            px = p[0];
	            py = p[1];

	            s = cay * px + acx * py - acd;
	            if (s >= 0) {
	                t = aby * px + bax * py + abd;
	                if (t >= 0) {
	                    k = A - s - t;
	                    
	                    int term1 = (s == 0 ? s : t);
	                    int term2 = (s == 0 ? s : k);
	                    int term3 = (t == 0 ? t : k);
	                    
	                    int calculation = (term1 != 0 ? term1 : term2 != 0? term2 : term3);
	                    
	                    if ((k >= 0) && (calculation != 0)) return false;
	                }
	            }
	        }

	    // if we don't use z-order curve hash, simply iterate through all other points
	    } else {
	        node = ear.mNextNode.mNextNode;

	        while (node != ear.mPreviousNode) {
	            p = node.mVertex;
	            node = node.mNextNode;

	            px = p[0];
	            py = p[1];

	            s = cay * px + acx * py - acd;
	            if (s >= 0) {
	                t = aby * px + bax * py + abd;
	                if (t >= 0) {
	                    k = A - s - t;
	                    int term1 = (s == 0 ? s : t);
	                    int term2 = (s == 0 ? s : k);
	                    int term3 = (t == 0 ? t : k);
	                    
	                    int calculation = (term1 != 0 ? term1 : term2 != 0? term2 : term3);
	                    
	                    if ((k >= 0) && (calculation != 0)) return false;
	                }
	            }
	        }
	    }

	    return true;
	}
	
	// go through all polygon nodes and cure small local self-intersections
	private static final Node onCureLocalIntersections(Node start, final IEarcutListener pEarcutListener) {
	    Node node = start;
	    do {
	        Node a = node.mPreviousNode,
	            b = node.mNextNode.mNextNode;

	        // a self-intersection where edge (v[i-1],v[i]) intersects (v[i+1],v[i+2])
	        if (Earcut.isIntersecting(a.mVertex, node.mVertex, node.mNextNode.mVertex, b.mVertex) && Earcut.isLocallyInside(a, b) && Earcut.isLocallyInside(b, a)) {
	            /* Return the triangulated vertices to the callback. */
	        	pEarcutListener.onTriangleVertex(a.mVertex[0], a.mVertex[1], node.mVertex[0], node.mVertex[1], b.mVertex[0], b.mVertex[1]);

	        	/** TODO: Abstract to a method. **/
	            // remove two nodes involved
	            a.mNextNode = b;
	            b.mPreviousNode = a;

	            Node az = node.mPreviousZNode;
	            
	            Node bz;
	            
	            //bz = node.mNextZNode && node.mNextZNode.mNextZNode;
	            if(node.mNextZNode == null) {
	            	bz = node.mNextZNode;
	            }
	            else {
	            	bz = node.mNextZNode.mNextZNode;
	            }
	            

	            if (az != null) az.mNextZNode = bz;
	            if (bz != null) bz.mPreviousZNode = az;

	            node = start = b;
	        }
	        node = node.mNextNode;
	    } while (node != start);

	    return node;
	}
	
	// try splitting polygon into two and triangulate them independently
	private static final void onSplitEarcut(final Node pStart, final IEarcutListener pCallback, final int pMinimumX, final int pMinimumY, final int pSize) {
	    // look for a valid diagonal that divides the polygon into two
	    Node a = pStart;
	    do {
	    	Node b = a.mNextNode.mNextNode;
	        while (b != a.mPreviousNode) {
	            if (Earcut.isValidDiagonal(a, b)) {
	                // split the polygon in two by the diagonal
	            	Node c = Earcut.onSplitPolygon(a, b);

	                // filter colinear points around the cuts
	                a = Earcut.onFilterPoints(a, a.mNextNode);
	                c = Earcut.onFilterPoints(c, c.mNextNode);

	                // run earcut on each half
	               Earcut.onEarcutLinkedList(a, pCallback, pMinimumX, pMinimumY, pSize, null);
	               Earcut.onEarcutLinkedList(c, pCallback, pMinimumX, pMinimumY, pSize, null);
	               return;
	            }
	            b = b.mNextNode;
	        }
	        a = a.mNextNode;
	    } while (a != pStart);
	}
	
	// link two polygon vertices with a bridge; if the vertices belong to the same ring, it splits polygon into two;
	// if one belongs to the outer ring and another to a hole, it merges it into a single ring
	private static final Node onSplitPolygon(final Node a, final Node b) {
	    Node a2 = new Node(a.mVertex),
	        b2 = new Node(b.mVertex),
	        an = a.mNextNode,
	        bp = b.mPreviousNode;

	    a.mNextNode = b;
	    b.mPreviousNode = a;

	    a2.mNextNode = an;
	    an.mPreviousNode = a2;

	    b2.mNextNode = a2;
	    a2.mPreviousNode = b2;

	    bp.mNextNode = b2;
	    b2.mPreviousNode = bp;

	    return b2;
	}
	
	/** Determines whether a diagonal between two polygon nodes lies within a polygon interior. (This determines the validity of the ray.) **/
	private static final boolean isValidDiagonal(final Node a, final Node b) {
	    return !Earcut.isIntersectingPolygon(a, a.mVertex, b.mVertex) && Earcut.isLocallyInside(a, b) && Earcut.isLocallyInside(b, a) && Earcut.onMiddleInsert(a, a.mVertex, b.mVertex);
	}
	
	/** Determines whether a polygon diagonal rests locally within a polygon. **/
	private static final boolean isLocallyInside(final Node a, final Node b) {
	    return Earcut.onCalculateWindingOrder(a.mPreviousNode.mVertex, a.mVertex, a.mNextNode.mVertex) == -1 ? Earcut.onCalculateWindingOrder(a.mVertex, b.mVertex, a.mNextNode.mVertex) != -1 && Earcut.onCalculateWindingOrder(a.mVertex, a.mPreviousNode.mVertex, b.mVertex) != -1 : Earcut.onCalculateWindingOrder(a.mVertex, b.mVertex, a.mPreviousNode.mVertex) == -1 || Earcut.onCalculateWindingOrder(a.mVertex, a.mNextNode.mVertex, b.mVertex) == -1;
	}
	
	// check if the middle point of a polygon diagonal is inside the polygon
	private static final boolean onMiddleInsert(final Node start, final int[] a, final int[] b) {
	    Node node = start;
	    boolean    inside = false;
	    int    px = (a[0] + b[0]) / 2;
	    int    py = (a[1] + b[1]) / 2;
	    do {
	        int[] p1 = node.mVertex,
	            p2 = node.mNextNode.mVertex;

	        if (((p1[1] > py) != (p2[1] > py)) && (px < (p2[0] - p1[0]) * (py - p1[1]) / (p2[1] - p1[1]) + p1[0])) inside = !inside;

	        node = node.mNextNode;
	    } while (node != start);

	    return inside;
	}
	
	/** Determines if the diagonal of a polygon is intersecting with any polygon elements. **/
	private static final boolean isIntersectingPolygon(final Node start, int[] a, int[] b) {
	    Node node = start;
	    do {
	        int[] p1 = node.mVertex, p2 = node.mNextNode.mVertex;

	        if (p1 != a && p2 != a && p1 != b && p2 != b && Earcut.isIntersecting(p1, p2, a, b)) {
	        	return true;
	        }

	        node = node.mNextNode;
	    } while (node != start);

	    return false;
	}
	
	/** Determines whether two segments intersect. **/
	private static final boolean isIntersecting(int[] p1, int[] q1, int[] p2, int[] q2) {
	    return Earcut.onCalculateWindingOrder(p1, q1, p2) != Earcut.onCalculateWindingOrder(p1, q1, q2) && Earcut.onCalculateWindingOrder(p2, q2, p1) != Earcut.onCalculateWindingOrder(p2, q2, q1);
	}
	
	/** Interlinks polygon nodes in Z-Order. **/
	private static final void onIndexCurve(Node start, int minX, int minY, int size) {
	    Node node = start;
	    
	    do {
	    	
	    	if(node.mZOrder == null) {
	    		node.mZOrder = Earcut.onCalculateZOrder(node.mVertex[0], node.mVertex[1], minX, minY, size);
	    	}
	    	
	        node.mPreviousZNode = node.mPreviousNode;
	        node.mNextZNode = node.mNextNode;
	        node = node.mNextNode;
	    } while (node != start);

	    node.mPreviousZNode.mNextZNode = null;
	    node.mPreviousZNode = null;

	    Earcut.onZSortList(node);
	}
	
	// Simon Tatham's linked list merge sort algorithm
	// http://www.chiark.greenend.org.uk/~sgtatham/algorithms/listsort.html
	private static final Node onZSortList(Node list) {
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
	        p = list;
	        list = null;
	        tail = null;
	        numMerges = 0;

	        while(p != null) {
	            numMerges++;
	            q = p;
	            pSize = 0;
	            for (i = 0; i < inSize; i++) {
	                pSize++;
	                q = q.mNextZNode;
	                if (q == null) break;
	            }

	            qSize = inSize;

	            while (pSize > 0 || (qSize > 0 && q != null)) {

	                if (pSize == 0) {
	                    e = q;
	                    q = q.mNextZNode;
	                    qSize--;
	                } else if (qSize == 0 || q == null) {
	                    e = p;
	                    p = p.mNextZNode;
	                    pSize--;
	                } else if (p.mZOrder <= q.mZOrder) {
	                    e = p;
	                    p = p.mNextZNode;
	                    pSize--;
	                } else {
	                    e = q;
	                    q = q.mNextZNode;
	                    qSize--;
	                }

	                if (tail != null) tail.mNextZNode = e;
	                else list = e;

	                e.mPreviousZNode = tail;
	                tail = e;
	            }

	            p = q;
	        }

	        tail.mNextZNode = null;

	        if (numMerges <= 1) return list;

	        inSize *= 2;
	    }
	}
	
	// z-order of a point given coords and size of the data bounding box
	private static final Integer onCalculateZOrder(int x, int y, int minX, int minY, int size) {
	    // coords are transformed into (0..1000) integer range
	    x = 1000 * (x - minX) / size;
	    x = (x | (x << 8)) & 0x00FF00FF;
	    x = (x | (x << 4)) & 0x0F0F0F0F;
	    x = (x | (x << 2)) & 0x33333333;
	    x = (x | (x << 1)) & 0x55555555;

	    y = 1000 * (y - minY) / size;
	    y = (y | (y << 8)) & 0x00FF00FF;
	    y = (y | (y << 4)) & 0x0F0F0F0F;
	    y = (y | (y << 2)) & 0x33333333;
	    y = (y | (y << 1)) & 0x55555555;

	    return x | (y << 1);
	}
	
	/** Creates a circular doubly linked list using polygon points. The order is governed by the specified winding order. **/
	private static final Node onCreateDoublyLinkedList(final int[][] pPoints, final boolean clockwise) {
		int sum = 0;
		int len = pPoints.length;
		int i, j;
		int[] p1;
		int[] p2;
		Node  last = null;
		
		/* Calculate the original order of the Polygon ring. */
	    for(i = 0, j = len - 1; i < len; j = i++) {
	        p1 = pPoints[i];
	        p2 = pPoints[j];
	        sum += (p2[0] - p1[0]) * (p1[1] + p2[1]);
	    }	    
	    // link points into circular doubly-linked list in the specified winding order
	    if (clockwise == (sum > 0)) {
	        for (i = 0; i < len; i++) {
	        	last = Earcut.onInsertNode(pPoints[i], last);
	        }
	    } else {
	        for (i = len - 1; i >= 0; i--) {
	        	last = onInsertNode(pPoints[i], last);
	        }
	    }
	    return last;
	}
	
	// eliminate colinear or duplicate points
	private static final Node onFilterPoints(final Node start, Node end) {
		if(end == null) {
			end = start;
		}

	    Node node = start;
	    boolean again = false;
	    
	    do {
	        again = false;

	        if (Earcut.isEquals(node.mVertex, node.mNextNode.mVertex) || Earcut.onCalculateWindingOrder(node.mPreviousNode.mVertex, node.mVertex, node.mNextNode.mVertex) == 0) {

	            // remove node
	            node.mPreviousNode.mNextNode = node.mNextNode;
	            node.mNextNode.mPreviousNode = node.mPreviousNode;

	            if (node.mPreviousZNode != null) node.mPreviousZNode.mNextZNode = node.mNextZNode;
	            if (node.mNextZNode != null) node.mNextZNode.mPreviousZNode = node.mPreviousZNode;

	            node = end = node.mPreviousNode;

	            if (node == node.mNextNode) return null;
	            again = true;

	        } else {
	            node = node.mNextNode;
	        }
	    } while (again || node != end);

	    return end;
	}
	
	// create a node and optionally link it with previous one (in a circular doubly linked list)
	private static final Node onInsertNode(int[] point, Node last) {
	    Node node = new Node(point);
	    if(last == null) {
	        node.mPreviousNode = node;
	        node.mNextNode = node;

	    } else {
	        node.mNextNode = last.mNextNode;
	        node.mPreviousNode = last;
	        last.mNextNode.mPreviousNode = node;
	        last.mNextNode = node;
	    }
	    return node;
	}
	
	/* Determines if two point vertices are equal. */
	private static final boolean isEquals(int[] p1, int[] p2) {
	    return p1[0] == p2[0] && p1[1] == p2[1];
	}
	
	private static final int onCalculateWindingOrder(int[] p, int[] q, int[] r) {
	    final int lCross = (q[1] - p[1]) * (r[0] - q[0]) - (q[0] - p[0]) * (r[1] - q[1]);
	    return    lCross > 0 ? 1 : lCross < 0 ? -1 : 0;
	}
	
	/* Prevent instantiation of this class. */
	private Earcut() {}

}