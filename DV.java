import java.lang.Math;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class DV implements RoutingAlgorithm {

    static int LOCAL = -1;
    static int UNKNOWN = -2;
    static int INFINITY = 60;

    // Constants used in relation to DVEntry ttl expiration protocols
    static int TIMEOUT = 6;
    static int TTL_TIMER = 4;

    // Keep the name of the current node, it may prove handy at a certain point.
    // I will remove it, if not.
    private int name;

    private int updateInterval;
    private boolean allowPReverse;
    private boolean allowExpire;
    private Router router;

    // Use a HashMap for fast access to entries.
    // The issue is that the destionation will be
    // stored twice, but easy access turns the balance.
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

    // Initalise the routing algorthm. This must be called once the
    // <code>setRouterObject</code> has been called.
    public void initalise() {
        this.name = this.router.getId();
        this.routingTable = new HashMap<>();
        this.routingTable.put(this.name, new DVRoutingTableEntry(name, LOCAL, 0, INFINITY));
    }

    // Given a destination address, returns
    // the out going interface for that address,
    // -1 is returned for a local address,
    // -2 is an unknown address.
    public int getNextHop(int destination) {
        DVRoutingTableEntry destinationEntry = this.routingTable.get(destination);
        if (destinationEntry == null) return UNKNOWN;
        if (destinationEntry.getMetric() == INFINITY) return UNKNOWN;
        return destinationEntry.getInterface();
    }

    // A periodic task to tidy up the routing
    // table. This method is called before
    // processing any new packets each round.
    public void tidyTable() {

        // Update links that have just been downed.
        for (Map.Entry<Integer, DVRoutingTableEntry> dvEntry : this.routingTable.entrySet()) {
            if (!this.router.getInterfaceState(dvEntry.getValue().getInterface()) && dvEntry.getValue().getMetric() != INFINITY) {
                dvEntry.getValue().setMetric(INFINITY);
                dvEntry.getValue().setTime(this.router.getCurrentTime());
            }
        }

        // Handle case when routing entries have a time to live bound to them.
        if (allowExpire) {
            Iterator<DVRoutingTableEntry> it = this.routingTable.values().iterator();
            while (it.hasNext()) {
                DVRoutingTableEntry dvEntry = it.next();
                if (dvEntry.getDestination() == this.router.getId()) continue;
                if (dvEntry.getMetric() != INFINITY) {
                    if (dvEntry.getTime() + TIMEOUT * updateInterval <= router.getCurrentTime()) {
                        dvEntry.setMetric(INFINITY);
                        dvEntry.setTime(router.getCurrentTime());
                    }
                } else if (dvEntry.getTime() + TTL_TIMER * updateInterval <= router.getCurrentTime()) {
                    it.remove();
                }
            }
        }
    }

    // Generates a routing packet from the routing table.
    public Packet generateRoutingPacket(int iface) {

        // If-statement handles makes the method generate a Packet
        // if-only enough time has passed since the last update.
        if (this.router.getCurrentTime() % updateInterval == 0) {

            Packet routingPacket = new Packet(this.name, Packet.BROADCAST);
            routingPacket.setType(Packet.ROUTING);
            Payload payload = new Payload();

            // If link is down, don't do anything. Faster then
            // just checking in the routing table for INFINITY
            // record on this link as records will not always be
            // updated instantly, especially with PReverse off.
            if (!router.getInterfaceState(iface)) {
                return null;
            }

            // Append DVEntry information that needs to be sent on
            // the link to the payload of the new routing Packet.
            for (HashMap.Entry<Integer, DVRoutingTableEntry> pair : this.routingTable.entrySet()) {
                DVRoutingTableEntry payloadEntry = new DVRoutingTableEntry(pair.getValue().getDestination(), pair.getValue().getInterface(), pair.getValue().getMetric(), pair.getValue().getTime());

                // PReverse technique requires us to send INFINITY metrics on ifaces
                // that current router uses the same iface to get to other nodes.
                if (this.allowPReverse) {
                    if (pair.getValue().getInterface() == iface) payloadEntry.setMetric(INFINITY);
                }
                payload.addEntry(payloadEntry);
            }

            routingPacket.setPayload(payload);
            return routingPacket;
        }
        // Return null if updateInterval condition is not fullfiled
        return null;
    }

    // Given a routing packet from another host process it and add it to the routing table.
    public void processRoutingPacket(Packet p, int iface) {

        for (Object o : p.getPayload().getData()) {
            DVRoutingTableEntry payloadEntry = (DVRoutingTableEntry) o;

            // Set this up before other conditionals to avoid complications.
            int metric = payloadEntry.getMetric() + router.getInterfaceWeight(iface) < INFINITY ? payloadEntry.getMetric() + router.getInterfaceWeight(iface) : INFINITY;

            if (!this.routingTable.containsKey(payloadEntry.getDestination()) && metric != INFINITY)
                this.routingTable.put(payloadEntry.getDestination(), new DVRoutingTableEntry(payloadEntry.getDestination(), iface, metric, router.getCurrentTime()));
            else {
                if (!this.routingTable.containsKey(payloadEntry.getDestination())) continue;
                DVRoutingTableEntry dvEntry = this.routingTable.get(payloadEntry.getDestination());
                if (dvEntry.getInterface() == iface) {
                    if (!(dvEntry.getMetric() == INFINITY && metric == INFINITY))
                        dvEntry.setTime(this.router.getCurrentTime());
                    dvEntry.setMetric(metric);
                } else if (metric < dvEntry.getMetric()) {
                    dvEntry.setInterface(iface);
                    dvEntry.setMetric(metric);
                    dvEntry.setTime(this.router.getCurrentTime());
                }
            }
        }
    }

    /**
     * Prints the routing table to the screen.
     * The format is :
     * Router <id>
     * d <destination> i <interface> m <metric>
     * d <destination> i <interface> m <metric>
     * d <destination> i <interface> m <metric>
     */
    public void showRoutes() {
        System.out.println("Router " + this.name);
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

