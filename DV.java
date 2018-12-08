import java.lang.Math;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class DV implements RoutingAlgorithm {

    static int LOCAL = -1;
    static int UNKNOWN = -2;
    static int INFINITY = 60;

    // Keep the name of the current node, it may prove handy at a certain point.
    // I will remove it, if not.
    protected int name;

    private int updateInterval;
    private boolean allowPReverse;
    private boolean allowExpire;
    private Router router;

    // Use a HashMap for fast access to entries.
    // The issue is that the destionation will be stored twice - but who cares?!
    private HashMap<Integer, DVRoutingTableEntry> routingTable;

    public DV() {
    }

    public void setRouterObject(Router obj) {
        this.router = obj;
    }

    public void setUpdateInterval(int u) {
        this.updateInterval = u;
    }

    public void setAllowPReverse(boolean flag) {
        this.allowPReverse = flag;
    }

    public void setAllowExpire(boolean flag) {
        this.allowExpire = flag;
    }

    public void initalise() {
        this.name = this.router.getId();
        this.routingTable = new HashMap<>();
        this.routingTable.put(this.name, new DVRoutingTableEntry(name, LOCAL, 0, INFINITY));
    }

    public int getNextHop(int destination) {
        //System.out.println("These are the available routes: ");
        //this.showRoutes();
        DVRoutingTableEntry destinationEntry = this.routingTable.get(destination);
        if (destinationEntry == null) return UNKNOWN;
        if (destinationEntry.getMetric() == INFINITY) return UNKNOWN;
        return destinationEntry.getInterface();
    }

    public void tidyTable() {

        // Update links that have just been downed.
        for (Map.Entry<Integer, DVRoutingTableEntry> dvEntry : this.routingTable.entrySet()) {
            if (!this.router.getInterfaceState(dvEntry.getValue().getInterface()) && dvEntry.getValue().getMetric() != INFINITY) {
                dvEntry.getValue().setMetric(INFINITY);
                dvEntry.getValue().setTime(this.router.getCurrentTime());
            }
        }
        //showRoutes();
    }

    public Packet generateRoutingPacket(int iface) {
        Packet routingPacket = new Packet(this.name, Packet.BROADCAST);
        routingPacket.setType(Packet.ROUTING);
        Payload payload = new Payload();

        // If link is down, don't do anything
        if (!router.getInterfaceState(iface)) {
            return null;
        }

        for (HashMap.Entry<Integer, DVRoutingTableEntry> pair : this.routingTable.entrySet()) {
            DVRoutingTableEntry payloadEntry = new DVRoutingTableEntry(pair.getValue().getDestination(), pair.getValue().getInterface(), pair.getValue().getMetric(), pair.getValue().getTime());
            if (this.allowPReverse) {
                //System.out.println("!!!Doing the PReverse Thing!");
                if (pair.getValue().getInterface() == iface) payloadEntry.setMetric(INFINITY);
            }
            payload.addEntry(payloadEntry);
        }

        routingPacket.setPayload(payload);
        //System.out.println("Sending Packet { source: " + routingPacket.getSource() + "; destination: " + routingPacket.getDestination() + "; type: " + routingPacket.getType() + "; payload: " + routingPacket.getPayload().getData() + " }");
        return routingPacket;
    }

    public void processRoutingPacket(Packet p, int iface) {
        //System.out.println("Received Packet from interface " + iface + ": { source: " + p.getSource() + "; destination: " + p.getDestination() + "; type: " + p.getType() + "; payload: " + p.getPayload().getData() + " }");

        for (Object o : p.getPayload().getData()) {
            DVRoutingTableEntry payloadEntry = (DVRoutingTableEntry) o;
            //if (this.routingTable.get(payloadEntry.getKey()) != null) {
            //    System.out.println("Stuff: " + this.routingTable.get(payloadEntry.getKey()).getMetric() + " " + (payloadEntry.getValue().getMetric() + this.router.getLinks()[iface].getInterfaceWeight(this.name)));
            //}
            if (this.routingTable.get(payloadEntry.getDestination()) == null) {

                //System.out.println("First time " + this.router.getId() + " has heard of router: " + payloadEntry.getDestination());
                //DVRoutingTableEntry dvEntry = System.out.println("Adding this entry: "+ dvEntry.toString());
                this.routingTable.put(payloadEntry.getDestination(), new DVRoutingTableEntry(payloadEntry.getDestination(), iface, payloadEntry.getMetric() + this.router.getLinks()[iface].getInterfaceWeight(this.name), payloadEntry.getTime()));

            } else if (this.routingTable.get(payloadEntry.getDestination()).getMetric() > payloadEntry.getMetric() + this.router.getLinks()[iface].getInterfaceWeight(this.name) &&
                    this.routingTable.get(payloadEntry.getDestination()).getInterface() != iface) {

                //System.out.println("Router " + this.router.getId() + " has received a better option for router " + payloadEntry.getDestination() + "; with metric " + (payloadEntry.getMetric() + this.router.getLinks()[iface].getInterfaceWeight(this.name)) + " and iface " + iface);
                this.routingTable.get(payloadEntry.getDestination()).setMetric(payloadEntry.getMetric() + this.router.getLinks()[iface].getInterfaceWeight(this.name));
                this.routingTable.get(payloadEntry.getDestination()).setInterface(iface);

            } else if (this.routingTable.get(payloadEntry.getDestination()).getInterface() == iface) {
                this.routingTable.get(payloadEntry.getDestination()).setMetric(Math.min(INFINITY, payloadEntry.getMetric() + this.router.getLinks()[iface].getInterfaceWeight(this.name)));

            } else {
                //System.out.println("This is what I have now in the routing table about this: " + this.routingTable.get(payloadEntry.getDestination()).toString() + ". Not doing anything.");
            }
        }

        //showRoutes();
    }

    public void showRoutes() {
        System.out.println("Router " + this.name);
        //for (Link link : this.router.getLinks())
        //    System.out.println(link.toString() + " with status " + link.isUp());
        //System.out.println("and in the routing table:");
        for (HashMap.Entry<Integer, DVRoutingTableEntry> mapEntry : this.routingTable.entrySet()) {
            System.out.println(mapEntry.getValue().toString());
        }
    }

}

class DVRoutingTableEntry implements RoutingTableEntry {
    private int destination, iface, metric, ttl;

    DVRoutingTableEntry(int d, int i, int m, int t) {
        this.destination = d;
        this.iface = i;
        this.metric = m;
        this.ttl = t;
    }

    public int getDestination() {
        return this.destination;
    }

    public void setDestination(int d) {
        this.destination = d;
    }

    public int getInterface() {
        return this.iface;
    }

    public void setInterface(int i) {
        this.iface = i;
    }

    public int getMetric() {
        return this.metric;
    }

    public void setMetric(int m) {
        this.metric = m;
    }

    public int getTime() {
        return this.ttl;
    }

    public void setTime(int t) {
        this.ttl = t;
    }

    public String toString() {
        return "d " + this.getDestination() + " i " + this.getInterface() + " m " + this.getMetric();
    }
}

