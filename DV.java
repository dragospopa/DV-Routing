import java.lang.Math;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class DV implements RoutingAlgorithm {

    static int LOCAL = -1;
    static int UNKNOWN = -2;
    static int INFINITY = 60;
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

    public Packet generateRoutingPacket(int iface) {
        if (this.router.getCurrentTime() % updateInterval == 0) {
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
                    if (pair.getValue().getInterface() == iface) payloadEntry.setMetric(INFINITY);
                }
                payload.addEntry(payloadEntry);
            }
            routingPacket.setPayload(payload);
            return routingPacket;
        }
        return null;
    }

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

