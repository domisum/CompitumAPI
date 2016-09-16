package de.domisum.lib.compitum.navmesh;

import de.domisum.lib.auxilium.data.container.math.Vector3D;
import de.domisum.lib.auxilium.util.DebugUtil;
import de.domisum.lib.auxilium.util.keys.Base64Key;
import de.domisum.lib.auxilium.util.math.MathUtil;
import de.domisum.lib.compitum.navgraph.GraphNode;
import de.domisum.lib.compitum.navgraph.NavGraph;
import de.domisum.lib.compitum.navmesh.geometry.NavMeshPoint;
import de.domisum.lib.compitum.navmesh.geometry.NavMeshTriangle;
import de.domisum.lib.compitum.navmesh.geometry.NavMeshTrianglePortal;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NavMesh
{

	// CONSTANTS
	private static final int KEY_LENGTH = 5;

	// PROPERTIES
	private String id;
	private Vector3D rangeCenter;
	private double range;

	// REFERENCES
	private World world;
	private Map<String, NavMeshPoint> points = new HashMap<>(); // <id, point>
	private Map<String, NavMeshTriangle> triangles = new HashMap<>(); // <id, triangle>

	private NavGraph navGraph;


	// -------
	// CONSTRUCTOR
	// -------
	public NavMesh(String id, Vector3D ranceCenter, double range, World world, Collection<NavMeshPoint> points,
			Collection<NavMeshTriangle> triangles)
	{
		this.id = id;
		this.rangeCenter = ranceCenter;
		this.range = range;

		this.world = world;
		for(NavMeshPoint p : points)
			this.points.put(p.getId(), p);
		for(NavMeshTriangle t : triangles)
			this.triangles.put(t.id, t);

		fillInNeighbors();
		generateNavGraph();
	}

	private void fillInNeighbors()
	{
		long start = System.nanoTime();

		for(NavMeshTriangle triangle : this.triangles.values())
			fillInNeighborsFor(triangle);

		DebugUtil.say("neighboringDuration: "+MathUtil.round((System.nanoTime()-start)/1000d, 0)+"mys");
	}

	private void fillInNeighborsFor(NavMeshTriangle triangle)
	{
		for(NavMeshTriangle t : this.triangles.values())
		{
			if(triangle == t)
				continue;

			Set<NavMeshPoint> commonPoints = getCommonPoints(triangle, t);

			if(commonPoints.size() == 2)
			{
				NavMeshTrianglePortal portal = new NavMeshTrianglePortal(triangle, t, commonPoints);
				triangle.makeNeighbors(t, portal);
			}
		}
	}


	// -------
	// GETTERS
	// -------

	// GENERAL
	public String getId()
	{
		return this.id;
	}

	public Vector3D getRangeCenter()
	{
		return this.rangeCenter;
	}

	public double getRange()
	{
		return this.range;
	}

	boolean isInRange(Location location)
	{
		if(location.getWorld() != this.world)
			return false;

		return new Vector3D(location).distanceToSquared(this.rangeCenter) < this.range*this.range;
	}

	public World getWorld()
	{
		return this.world;
	}


	public NavGraph getNavGraph()
	{
		return this.navGraph;
	}


	// POINT
	public Collection<NavMeshPoint> getPoints()
	{
		return this.points.values();
	}


	private NavMeshPoint getPoint(String id)
	{
		return this.points.get(id);
	}


	// TRIANGLE
	public Collection<NavMeshTriangle> getTriangles()
	{
		return this.triangles.values();
	}

	public Set<NavMeshTriangle> getTrianglesUsingPoint(NavMeshPoint point)
	{
		Set<NavMeshTriangle> trianglesUsingPoint = new HashSet<>();

		for(NavMeshTriangle triangle : this.triangles.values())
			if(triangle.isUsingPoint(point))
				trianglesUsingPoint.add(triangle);

		return trianglesUsingPoint;
	}


	public NavMeshTriangle getTriangle(String id)
	{
		return this.triangles.get(id);
	}

	public NavMeshTriangle getTriangleAt(Location location)
	{
		for(NavMeshTriangle triangle : this.triangles.values())
			if(triangle.doesContain(location))
				return triangle;

		return null;
	}


	// -------
	// CHANGERS
	// -------
	// POINT
	public NavMeshPoint createPoint(double x, double y, double z)
	{
		NavMeshPoint point = new NavMeshPoint(getUnusedId(), x, y, z);

		this.points.put(point.getId(), point);
		return point;
	}

	public void removePoint(NavMeshPoint point)
	{
		for(NavMeshTriangle t : getTrianglesUsingPoint(point))
			deleteTriangle(t);

		this.points.remove(point.getId());
	}


	// TRIANGLE
	public NavMeshTriangle createTriangle(NavMeshPoint point1, NavMeshPoint point2, NavMeshPoint point3)
	{
		NavMeshTriangle triangle = new NavMeshTriangle(getUnusedId(), point1, point2, point3);
		this.triangles.put(triangle.id, triangle);
		fillInNeighborsFor(triangle);

		return triangle;
	}

	public void deleteTriangle(NavMeshTriangle triangle)
	{
		this.triangles.remove(triangle.id);
	}


	// -------
	// PATHFINDING
	// -------
	private void generateNavGraph()
	{
		long start = System.nanoTime();

		List<GraphNode> nodes = new ArrayList<>();
		for(NavMeshTriangle triangle : this.triangles.values())
		{
			Vector3D triangleLocation = triangle.getCenter();
			GraphNode node = new GraphNode(triangle.id, triangleLocation.x, triangleLocation.y, triangleLocation.z);
			nodes.add(node);
		}
		this.navGraph = new NavGraph(this.id+"_navmesh", this.rangeCenter, this.range, this.world, nodes);

		for(NavMeshTriangle triangle : this.triangles.values())
		{
			GraphNode node = this.navGraph.getNode(triangle.id);
			for(NavMeshTriangle n : triangle.neighbors.keySet())
			{
				GraphNode neighborNode = this.navGraph.getNode(n.id);
				if(!node.isConnected(neighborNode))
					node.addEdge(neighborNode, 1);
			}
		}

		DebugUtil.say("generationDuration: "+MathUtil.round((System.nanoTime()-start)/1000d, 2)+"mys");
	}


	// -------
	// UTIL
	// -------
	private String getUnusedId()
	{
		String id;
		do
			id = Base64Key.generate(KEY_LENGTH);
		while(getPoint(id) != null || getTriangle(id) != null);

		return id;
	}

	@SuppressWarnings("unused")
	private boolean areTrianglesAdjacent(NavMeshTriangle triangle1, NavMeshTriangle triangle2)
	{
		int same = 0;

		if(triangle1.point1 == triangle2.point1)
			same++;
		if(triangle1.point1 == triangle2.point2)
			same++;
		if(triangle1.point1 == triangle2.point3)
			same++;

		if(triangle1.point2 == triangle2.point1)
			same++;
		if(triangle1.point2 == triangle2.point2)
			same++;
		if(triangle1.point2 == triangle2.point3)
			same++;

		if(triangle1.point3 == triangle2.point1)
			same++;
		if(triangle1.point3 == triangle2.point2)
			same++;
		if(triangle1.point3 == triangle2.point3)
			same++;

		return same == 2;
	}

	private Set<NavMeshPoint> getCommonPoints(NavMeshTriangle triangle1, NavMeshTriangle triangle2)
	{
		Set<NavMeshPoint> commonPoints = new HashSet<>();
		if(triangle1.point1 == triangle2.point1)
			commonPoints.add(triangle1.point1);
		if(triangle1.point1 == triangle2.point2)
			commonPoints.add(triangle1.point1);
		if(triangle1.point1 == triangle2.point3)
			commonPoints.add(triangle1.point1);

		if(triangle1.point2 == triangle2.point1)
			commonPoints.add(triangle1.point2);
		if(triangle1.point2 == triangle2.point2)
			commonPoints.add(triangle1.point2);
		if(triangle1.point2 == triangle2.point3)
			commonPoints.add(triangle1.point2);

		if(triangle1.point3 == triangle2.point1)
			commonPoints.add(triangle1.point3);
		if(triangle1.point3 == triangle2.point2)
			commonPoints.add(triangle1.point3);
		if(triangle1.point3 == triangle2.point3)
			commonPoints.add(triangle1.point3);

		return commonPoints;
	}

}