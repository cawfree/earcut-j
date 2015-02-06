package com.earcutj;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class Earcut {
	
	/**  **/
	public static final List<int[]> earcut(final int[][][] points) {
		Node outerNode = filterPoints(linkedList(points[0], true), null);
		List<int[]> triangles = new ArrayList<int[]>();
		
		if(outerNode == null) {
			return triangles;
		}
		
		Node node = null;
		Integer minX = null; /** TODO: Autboxing is used to emulate Javascript 'undefined' parameters implementation. Avoid? **/
		int minY = 0, maxX = 0, maxY = 0, x = 0, y = 0, size = 0, i = 0;
        int threshold = 80;
        
        for(i = 0; threshold >= 0 && i < points.length; i++) threshold -= points[i].length;
        
        System.out.println(threshold);
        
        
        // if the shape is not too simple, we'll use z-order curve hash later; calculate polygon bbox
        if (threshold < 0) {
        	System.out.println("got here");
            node = outerNode.next;
            minX = maxX = node.p[0];
            minY = maxY = node.p[1];
            do {
                x = node.p[0];
                y = node.p[1];
                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
                node = node.next;
            } while (node != outerNode);

            // minX, minY and size are later used to transform coords into integers for z-order calculation
            size = Math.max(maxX - minX, maxY - minY);
       }
        
        if (points.length > 1) outerNode = eliminateHoles(points, outerNode);
        
        /* earcut linked... */
        earcutLinked(outerNode, triangles, minX, minY, size, null);
		
		return triangles;
	}
	
	private static final Comparator<Node> COMPARATOR_SORT_BY_X = new Comparator<Node>() {

	    @Override
	    public int compare(Node a, Node b) {
	        return a.p[0] < b.p[0] ? -1 : a.p[0] == b.p[0] ? 0 : 1; /** TODO: Is this right? **/
	    }
	    
	};
	
	// link every hole into the outer loop, producing a single-ring polygon without holes
	private static final Node eliminateHoles(int[][][] points, Node outerNode) {
	    int len = points.length;
	    int i;
	    
	    //var queue = [];
	    
	    List<Node> queue = new ArrayList<Node>();
	    for (i = 1; i < len; i++) {
	        Node list = filterPoints(linkedList(points[i], false), null);
	        if (list != null) queue.add(getLeftmost(list));
	    }
	    
	    Collections.sort(queue, Earcut.COMPARATOR_SORT_BY_X);

	    // process holes from left to right
	    for (i = 0; i < queue.size(); i++) {
	        eliminateHole(queue.get(i), outerNode);
	        outerNode = filterPoints(outerNode, outerNode.next);
	    }

	    return outerNode;
	}
	
	// find a bridge between vertices that connects hole with an outer ring and and link it
	private static final void eliminateHole(Node holeNode, Node outerNode) {
	    outerNode = findHoleBridge(holeNode, outerNode);
	    if (outerNode != null) {
	        Node b = splitPolygon(outerNode, holeNode);
	        filterPoints(b, b.next);
	    }
	}
	
	// David Eberly's algorithm for finding a bridge between hole and outer polygon
	private static final Node findHoleBridge(Node holeNode, Node outerNode) {
	    Node node = outerNode;
	    int[]    p = holeNode.p;
	    int   px = p[0],
	        py = p[1],
	        qMax = Integer.MIN_VALUE;
	    Node mNode = null;
	    int[] a, b;

	    // find a segment intersected by a ray from the hole's leftmost point to the left;
	    // segment's endpoint with lesser x will be potential connection point
	    do {
	        a = node.p;
	        b = node.next.p;

	        if (py <= a[1] && py >= b[1]) {
	            int qx = a[0] + (py - a[1]) * (b[0] - a[0]) / (b[1] - a[1]);
	            if (qx <= px && qx > qMax) {
	                qMax = qx;
	                mNode = a[0] < b[0] ? node : node.next;
	            }
	        }
	        node = node.next;
	    } while (node != outerNode);

	    if (mNode == null) return null;

	    // look for points strictly inside the triangle of hole point, segment intersection and endpoint;
	    // if there are no points found, we have a valid connection;
	    // otherwise choose the point of the minimum angle with the ray as connection point

	    int bx = mNode.p[0],
	        by = mNode.p[1],
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

	    node = mNode.next;

	    while (node != stop) {

	        mx = node.p[0];
	        my = node.p[1];
	        amx = px - mx;

	        if (amx >= 0 && mx >= bx) {
	            s = (cpy * mx + pcx * my - pcd) * sign;
	            if (s >= 0) {
	                t = (pby * mx + bpx * my + pbd) * sign;

	                if (t >= 0 && A * sign - s - t >= 0) {
	                    tan = Math.abs(py - my) / amx; // tangential
	                    if (tan < tanMin && locallyInside(node, holeNode)) {
	                        mNode = node;
	                        tanMin = tan;
	                    }
	                }
	            }
	        }

	        node = node.next;
	    }

	    return mNode;
	}
	
	// find the leftmost node of a polygon ring
	private static final Node getLeftmost(Node start) {
	    Node node = start,
	        leftmost = start;
	    do {
	        if (node.p[0] < leftmost.p[0]) leftmost = node;
	        node = node.next;
	    } while (node != start);

	    return leftmost;
	}
	
	// main ear slicing loop which triangulates a polygon (given as a linked list)
	private static final void earcutLinked(Node ear, List<int[]>triangles, Integer minX, int minY, int size, Integer pass) { /** TODO: Avoid an autoboxing based solution. **/
	    if (ear == null) {
	    	return;
	    }

	    // interlink polygon nodes in z-order
	    if (pass == null && minX != null) {
	    	indexCurve(ear, minX, minY, size);
	    }

	    Node stop = ear;
	    Node prev = null;
	    Node next = null;

	    // iterate through ears, slicing them one by one
	    while (ear.prev != ear.next) {
	        prev = ear.prev;
	        next = ear.next;

	        if (isEar(ear, minX, minY, size)) {
	            // cut off the triangle
	            //triangles.push(prev.p, ear.p, next.p);
	            /** TODO: Is clone required? Probably not. **/
	            triangles.add(prev.p.clone());
	            triangles.add(ear.p.clone());
	            triangles.add(next.p.clone());
	            

	            // remove ear node
	            next.prev = prev;
	            prev.next = next;

	            if (ear.prevZ != null) ear.prevZ.nextZ = ear.nextZ;
	            if (ear.nextZ != null) ear.nextZ.prevZ = ear.prevZ;

	            // skipping the next vertice leads to less sliver triangles
	            ear = next.next;
	            stop = next.next;

	            continue;
	        }

	        ear = next;

	        // if we looped through the whole remaining polygon and can't find any more ears
	        if (ear == stop) {
	            // try filtering points and slicing again
	            if (pass == null) earcutLinked(filterPoints(ear, null), triangles, minX, minY, size, 1);

	            // if this didn't work, try curing all small self-intersections locally
	            else if (pass == 1) {
	                ear = cureLocalIntersections(ear, triangles);
	                earcutLinked(ear, triangles, minX, minY, size, 2);

	            // as a last resort, try splitting the remaining polygon into two
	            } else if (pass == 2) splitEarcut(ear, triangles, minX, minY, size);

	            break;
	        }
	    }
	}
	
	// check whether a polygon node forms a valid ear with adjacent nodes
	private static final boolean isEar(Node ear, Integer minX, int minY, int size) {

	    int[] a = ear.prev.p,
	        b = ear.p,
	        c = ear.next.p;

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
	            minZ = zOrder(minTX, minTY, minX, minY, size),
	            maxZ = zOrder(maxTX, maxTY, minX, minY, size);

	        // first look for points inside the triangle in increasing z-order
	        node = ear.nextZ;

	        while (node != null && node.z <= maxZ) {
	            p = node.p;
	            node = node.nextZ;
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
	        node = ear.prevZ;

	        while (node != null && node.z >= minZ) {
	            p = node.p;
	            node = node.prevZ;
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
	        node = ear.next.next;

	        while (node != ear.prev) {
	            p = node.p;
	            node = node.next;

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
	private static final Node cureLocalIntersections(Node start, List<int[]>triangles) {
	    Node node = start;
	    do {
	        Node a = node.prev,
	            b = node.next.next;

	        // a self-intersection where edge (v[i-1],v[i]) intersects (v[i+1],v[i+2])
	        if (intersects(a.p, node.p, node.next.p, b.p) && locallyInside(a, b) && locallyInside(b, a)) {

	           // triangles.push(a.p, node.p, b.p);
	            triangles.add(a.p.clone()); /** TODO: Is clone required? **/
	            triangles.add(node.p.clone());
	            triangles.add(b.p.clone());

	            // remove two nodes involved
	            a.next = b;
	            b.prev = a;

	            Node az = node.prevZ;
	            
	            Node bz;
	            
	            //bz = node.nextZ && node.nextZ.nextZ;
	            if(node.nextZ == null) {
	            	bz = node.nextZ;
	            }
	            else {
	            	bz = node.nextZ.nextZ;
	            }
	            

	            if (az != null) az.nextZ = bz;
	            if (bz != null) bz.prevZ = az;

	            node = start = b;
	        }
	        node = node.next;
	    } while (node != start);

	    return node;
	}
	
	// try splitting polygon into two and triangulate them independently
	private static final void splitEarcut(Node start, List<int[]>triangles, int minX, int minY, int size) {
	    // look for a valid diagonal that divides the polygon into two
	    Node a = start;
	    do {
	    	Node b = a.next.next;
	        while (b != a.prev) {
	            if (isValidDiagonal(a, b)) {
	                // split the polygon in two by the diagonal
	            	Node c = splitPolygon(a, b);

	                // filter colinear points around the cuts
	                a = filterPoints(a, a.next);
	                c = filterPoints(c, c.next);

	                // run earcut on each half
	                earcutLinked(a, triangles, minX, minY, size, null);
	                earcutLinked(c, triangles, minX, minY, size, null);
	                return;
	            }
	            b = b.next;
	        }
	        a = a.next;
	    } while (a != start);
	}
	
	// link two polygon vertices with a bridge; if the vertices belong to the same ring, it splits polygon into two;
	// if one belongs to the outer ring and another to a hole, it merges it into a single ring
	private static final Node splitPolygon(Node a, Node b) {
	    Node a2 = new Node(a.p),
	        b2 = new Node(b.p),
	        an = a.next,
	        bp = b.prev;

	    a.next = b;
	    b.prev = a;

	    a2.next = an;
	    an.prev = a2;

	    b2.next = a2;
	    a2.prev = b2;

	    bp.next = b2;
	    b2.prev = bp;

	    return b2;
	}
	
	// check if a diagonal between two polygon nodes is valid (lies in polygon interior)
	private static final boolean isValidDiagonal(Node a, Node b) {
	    return !intersectsPolygon(a, a.p, b.p) &&
	           locallyInside(a, b) && locallyInside(b, a) &&
	           middleInside(a, a.p, b.p);
	}
	
	// check if a polygon diagonal is locally inside the polygon
	public static final boolean locallyInside(Node a, Node b) {
	    return orient(a.prev.p, a.p, a.next.p) == -1 ?
	        orient(a.p, b.p, a.next.p) != -1 && orient(a.p, a.prev.p, b.p) != -1 :
	        orient(a.p, b.p, a.prev.p) == -1 || orient(a.p, a.next.p, b.p) == -1;
	}
	
	// check if the middle point of a polygon diagonal is inside the polygon
	public static final boolean middleInside(Node start, int[] a, int[] b) {
	    Node node = start;
	    boolean    inside = false;
	    int    px = (a[0] + b[0]) / 2;
	    int    py = (a[1] + b[1]) / 2;
	    do {
	        int[] p1 = node.p,
	            p2 = node.next.p;

	        if (((p1[1] > py) != (p2[1] > py)) &&
	            (px < (p2[0] - p1[0]) * (py - p1[1]) / (p2[1] - p1[1]) + p1[0])) inside = !inside;

	        node = node.next;
	    } while (node != start);

	    return inside;
	}
	
	// check if a polygon diagonal intersects any polygon segments
	public static final boolean intersectsPolygon(final Node start, int[] a, int[] b) {
	    Node node = start;
	    do {
	        int[] p1 = node.p,
	            p2 = node.next.p;

	        if (p1 != a && p2 != a && p1 != b && p2 != b && intersects(p1, p2, a, b)) return true;

	        node = node.next;
	    } while (node != start);

	    return false;
	}
	
	// check if two segments intersect
	public static final boolean intersects(int[] p1, int[] q1, int[] p2, int[] q2) {
	    return orient(p1, q1, p2) != orient(p1, q1, q2) &&
	           orient(p2, q2, p1) != orient(p2, q2, q1);
	}
	
	// interlink polygon nodes in z-order
	private static final void indexCurve(Node start, int minX, int minY, int size) {
	    Node node = start;
	    
	    do {
	    	
	    	if(node.z == null) {
	    		node.z = zOrder(node.p[0], node.p[1], minX, minY, size);
	    	}
	    	
	        node.prevZ = node.prev;
	        node.nextZ = node.next;
	        node = node.next;
	    } while (node != start);

	    node.prevZ.nextZ = null;
	    node.prevZ = null;

	    sortLinked(node);
	}
	
	// Simon Tatham's linked list merge sort algorithm
	// http://www.chiark.greenend.org.uk/~sgtatham/algorithms/listsort.html
	private static final Node sortLinked(Node list) {
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
	                q = q.nextZ;
	                if (q == null) break;
	            }

	            qSize = inSize;

	            while (pSize > 0 || (qSize > 0 && q != null)) {

	                if (pSize == 0) {
	                    e = q;
	                    q = q.nextZ;
	                    qSize--;
	                } else if (qSize == 0 || q == null) {
	                    e = p;
	                    p = p.nextZ;
	                    pSize--;
	                } else if (p.z <= q.z) {
	                    e = p;
	                    p = p.nextZ;
	                    pSize--;
	                } else {
	                    e = q;
	                    q = q.nextZ;
	                    qSize--;
	                }

	                if (tail != null) tail.nextZ = e;
	                else list = e;

	                e.prevZ = tail;
	                tail = e;
	            }

	            p = q;
	        }

	        tail.nextZ = null;

	        if (numMerges <= 1) return list;

	        inSize *= 2;
	    }
	}
	
	// z-order of a point given coords and size of the data bounding box
	private static final Integer zOrder(int x, int y, int minX, int minY, int size) {
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
	private static final Node linkedList(final int[][] points, final boolean clockwise) {
		int sum = 0;
		int len = points.length;
		int i, j;
		int[] p1;
		int[] p2;
		Node  last = null;
		
		/* Calculate the original order of the Polygon ring. */
	    for(i = 0, j = len - 1; i < len; j = i++) {
	        p1 = points[i];
	        p2 = points[j];
	        sum += (p2[0] - p1[0]) * (p1[1] + p2[1]);
	    }
	    
	    /** TODO: Depends on insertNode(). **/
	    
	    // link points into circular doubly-linked list in the specified winding order
	    if (clockwise == (sum > 0)) {
	        for (i = 0; i < len; i++) last = insertNode(points[i], last);
	    } else {
	        for (i = len - 1; i >= 0; i--) last = insertNode(points[i], last);
	    }
	    return last;
	}
	
	// eliminate colinear or duplicate points
	public static final Node filterPoints(final Node start, Node end) {
		if(end == null) {
			end = start;
		}

	    Node node = start;
	    boolean again = false;
	    
	    do {
	        again = false;

	        if (equals(node.p, node.next.p) || orient(node.prev.p, node.p, node.next.p) == 0) {

	            // remove node
	            node.prev.next = node.next;
	            node.next.prev = node.prev;

	            if (node.prevZ != null) node.prevZ.nextZ = node.nextZ;
	            if (node.nextZ != null) node.nextZ.prevZ = node.prevZ;

	            node = end = node.prev;

	            if (node == node.next) return null;
	            again = true;

	        } else {
	            node = node.next;
	        }
	    } while (again || node != end);

	    return end;
	}
	
	// create a node and optionally link it with previous one (in a circular doubly linked list)
	private static final Node insertNode(int[] point, Node last) {
	    Node node = new Node(point);
	    if(last == null) {
	        node.prev = node;
	        node.next = node;

	    } else {
	        node.next = last.next;
	        node.prev = last;
	        last.next.prev = node;
	        last.next = node;
	    }
	    return node;
	}
	
	// check if two points are equal
	private static final boolean equals(int[] p1, int[] p2) {
	    return p1[0] == p2[0] && p1[1] == p2[1];
	}
	
	// winding order of triangle formed by 3 given points
	private static final int orient(int[] p, int[] q, int[] r) {
	    int o = (q[1] - p[1]) * (r[0] - q[0]) - (q[0] - p[0]) * (r[1] - q[1]);
	    return o > 0 ? 1 : o < 0 ? -1 : 0;
	}
	
	/* Prevent instantiation of this class. */
	private Earcut() {}

}