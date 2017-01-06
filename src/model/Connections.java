package model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import model.ConnectionsModel.CONN_TYPE;
import utils.Triple;
import data.ActionDiagram.ActionNode;
import data.RecipeEvent.Argument;

public class Connections {

	public static class Connection implements Comparable<Connection> {
		public ActionNode origin;
		public Triple<ActionNode, Argument, String> destination;
		public CONN_TYPE type;
		public boolean loc_flow;
		
		private String str_;
		
		public Connection(ActionNode origin, Triple<ActionNode, Argument, String> destination, CONN_TYPE type) {
			this.origin = origin;
			this.destination = destination;
			this.type = type;
			this.loc_flow = false;
			genString();
		}
		
		public Connection(ActionNode origin, Triple<ActionNode, Argument, String> destination, 
				CONN_TYPE type, boolean loc_flow) {
			this.origin = origin;
			this.destination = destination;
			this.type = type;
			this.loc_flow = loc_flow;
			genString();
		}
		
		public String toString() {
			return str_;
		}
		
		public void genString() {
			str_ = origin + " -> " + destination + " " + type.toString() + 
					(loc_flow && type != CONN_TYPE.LOC? "+LOC" : "");
		}
		
		public int hashCode() {
			return str_.hashCode();
		}

		@Override
		public int compareTo(Connection arg0) {
			return str_.compareTo(arg0.str_);
		}
		
		public boolean equals(Object obj) {
			if (obj instanceof Connection) {
				return compareTo((Connection)obj) == 0;
			}
			return false;
		}
	}
	
	public Map<ActionNode, Set<Connection>> origin_to_connections;
	public Map<Triple<ActionNode, Argument, String>, Connection> dest_to_connection;
	public Map<ActionNode, Set<Connection>> dest_node_to_connections;
	
	public Connections() {
		origin_to_connections = new HashMap<ActionNode, Set<Connection>>();
		dest_to_connection = new HashMap<Triple<ActionNode, Argument, String>, Connection>();
		dest_node_to_connections = new HashMap<ActionNode, Set<Connection>>();
	}
	
	public Iterator<Triple<ActionNode, Argument, String>> dest_iterator() {
		return dest_to_connection.keySet().iterator();
	}
	
	public void addConnection(Connection conn) {
		Set<Connection> conns = origin_to_connections.get(conn.origin);
		if (conns == null) {
			conns = new HashSet<Connection>();
			origin_to_connections.put(conn.origin, conns);
		}
		conns.add(conn);
		
		dest_to_connection.put(conn.destination, conn);
		
		conns = dest_node_to_connections.get(conn.destination.getFirst());
		if (conns == null) {
			conns = new HashSet<Connection>();
			dest_node_to_connections.put(conn.destination.getFirst(), conns);
		}
		conns.add(conn);
	}
	
	public Connection addConnection(ActionNode origin, Triple<ActionNode, Argument, String> dest, CONN_TYPE type) {
		Connection conn = new Connection(origin, dest, type);
		addConnection(conn);
		return conn;
	}
	
	public void removeConnection(Connection conn) {
		Set<Connection> conns = origin_to_connections.get(conn.origin);
		conns.remove(conn);
		dest_to_connection.remove(conn.destination);
		conns = dest_node_to_connections.get(conn.destination.getFirst());
		conns.remove(conn);
	}
	
	public Connection removeConnection(ActionNode origin, Triple<ActionNode, Argument, String> dest, CONN_TYPE type) {
		Connection conn = new Connection(origin, dest, type);
		removeConnection(conn);
		return conn;
	}
	
	public Set<Connection> getIncomingConnectionsToNode(ActionNode dest) {
		return dest_node_to_connections.get(dest);
	}
	
	public Set<Connection> getOutgoingConnections(ActionNode origin) {
		return origin_to_connections.get(origin);
	}
	
	public ActionNode getOrigin(ActionNode node, Argument arg, String span) {
		return getOrigin(new Triple<ActionNode, Argument, String>(node, arg, span));
	}

	public ActionNode getOrigin(Triple<ActionNode, Argument, String> dest_triple) {
		Connection conn = dest_to_connection.get(dest_triple);
		if (conn == null) {
			return null;
		}
		return conn.origin;
	}
	
	public CONN_TYPE getConnectionType(ActionNode node, Argument arg, String span) {
		return getConnectionType(new Triple<ActionNode, Argument, String>(node, arg, span));
	}

	public CONN_TYPE getConnectionType(Triple<ActionNode, Argument, String> dest_triple) {
		Connection conn = dest_to_connection.get(dest_triple);
		if (conn == null) {
			return null;
		}
		return conn.type;
	}
	
	public Connection getConnection(ActionNode node, Argument arg, String span) {
		return getConnection(new Triple<ActionNode, Argument, String>(node, arg, span));
	}

	public Connection getConnection(Triple<ActionNode, Argument, String> dest_triple) {
		return dest_to_connection.get(dest_triple);
	}

	public boolean doesSpanHaveOrigin(ActionNode node, Argument arg, String span) {
		return doesSpanHaveOrigin(new Triple<ActionNode, Argument, String>(node, arg, span));
	}

	public boolean doesSpanHaveOrigin(Triple<ActionNode, Argument, String> dest_triple) {
		Connection conn = dest_to_connection.get(dest_triple);
		if (conn == null) {
			return false;
		}
		return true;
	}
}
