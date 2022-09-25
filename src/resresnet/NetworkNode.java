package resresnet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class NetworkNode {

    public static void main(String[] args) throws Exception {
        int id = 0, port = 0, gatewayPort = 0;
        String gatewayIp = null, resources = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-ident":
                    id = Integer.parseInt(args[++i]);
                    break;
                case "-tcpport":
                    port = Integer.parseInt(args[++i]);
                    break;
                case "-gateway":
                    String[] gatewayArray = args[++i].split(":");
                    gatewayIp = gatewayArray[0];
                    gatewayPort = Integer.parseInt(gatewayArray[1]);
                    break;
                default:
                    if (resources == null) resources = args[i];
                    else resources += " " + args[i];
            }
        }

        NetworkNode node = new NetworkNode(id, port, gatewayIp, gatewayPort, resources);
    }

    private final int id, port, gatewayPort;
    private final String ip, gatewayIp;
    private final Map<Character, Integer> resources;
    private final Map<ClientData, Map<Character, Integer>> allocatedResources;
    private final ServerSocket serverSocket;
    private List<NodeData> network;

    private NetworkNode(int id, int port, String gatewayIp, int gatewayPort, String resources) throws Exception {
        this.id = id;
        this.port = port;
        this.gatewayIp = gatewayIp;
        this.gatewayPort = gatewayPort;
        this.resources = resourceStringToMap(resources);
        allocatedResources = new HashMap<>();
        serverSocket = new ServerSocket(this.port);
        ip = serverSocket.getInetAddress().getHostAddress();
        network = new ArrayList<>();

        System.out.println(this);

        updateNetwork();
        start();
    }

    private void start() {
        while (true) {
            try {
                new RequestHandler(serverSocket.accept()).start();
            } catch (IOException e) {
                break;
            }
        }
    }

    private void close() throws IOException {
        log("Closing...");
        serverSocket.close();
    }

    private void updateNetwork() throws Exception {
        log("Updating network...");
        if (this.gatewayIp != null) {
            network = sendGetNetworkRequest(this.gatewayIp, this.gatewayPort);
            log("Received network data");
            for (NodeData node : network) {
                sendAddToNetworkRequest(node.ip, node.port, new NodeData(this));
            }
        }
        network.add(new NodeData(this));
        log("Network:");
        network.forEach((NodeData n) -> log("   { " + n.id + ", " + n.ip + ", " + n.port + " }"));
    }

    private List<NodeData> sendGetNetworkRequest(String ip, int port) throws Exception {
        log("Sending GET_NETWORK request...");
        Socket socket = new Socket(ip, port);
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        out.println("GET_NETWORK");

        List<NodeData> network = new ArrayList<>();
        String nodeId;
        while ((nodeId = in.readLine()) != null && !nodeId.equals("END")) {
            String nodeIp = in.readLine();
            int nodePort = Integer.parseInt(in.readLine());
            NodeData node = new NodeData(Integer.parseInt(nodeId), nodeIp, nodePort);
            network.add(node);
        }

        out.close(); in.close(); socket.close();
        return network;
    }

    private boolean sendAddToNetworkRequest(String ip, int port, NodeData node) throws Exception {
        log("Sending ADD_TO_NETWORK request...");
        Socket socket = new Socket(ip, port);
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        out.println("ADD_TO_NETWORK");

        out.println(node.id);
        out.println(node.ip);
        out.println(node.port);

        boolean result = Boolean.parseBoolean(in.readLine());

        out.close(); in.close(); socket.close();
        return result;
    }

    private List<String> sendAllocateRequest(String ip, int port, ClientData client, List<NodeData> checkedNodes) throws Exception {
        log("Sending ALLOCATE request...");
        Socket socket = new Socket(ip, port);
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        out.println("ALLOCATE");

        out.println(client.id);
        out.println(client.ip);
        out.println(client.port);
        for (Character key : client.requestedResources.keySet()) {
            out.println(key);
            out.println(client.requestedResources.get(key));
        }
        out.println("END");

        for (NodeData n : checkedNodes) {
            out.println(n.id);
            out.println(n.ip);
            out.println(n.port);
        }
        out.println("END");

        List<String> allocations = new ArrayList<>();
        String allocation;
        while ((allocation = in.readLine()) != null && !allocation.equals("END")) {
            allocations.add(allocation);
        }

        out.close(); in.close(); socket.close();
        return allocations;
    }

    private boolean sendCloseRequest(String ip, int port) throws IOException {
        log("Sending CLOSE request...");
        Socket socket = new Socket(ip, port);
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        out.println("CLOSE");

        boolean result = Boolean.parseBoolean(in.readLine());

        out.close(); in.close(); socket.close();
        return result;
    }

    private Map<Character, Integer> resourceStringToMap(String resourceStr) {
        Map<Character, Integer> resourceMap = new HashMap<>();
        String[] resourceArr = resourceStr.split(" ");
        for (String str : resourceArr) {
            String[] arr = str.split(":");
            resourceMap.put(arr[0].charAt(0), Integer.parseInt(arr[1]));
        }
        return resourceMap;
    }

    private List<String> allocateResources(ClientData client, List<NodeData> checkedNodes) throws Exception {
        Map<Character, Integer> handledResources = new HashMap<>();
        Map<Character, Integer> unhandledResources = new HashMap<>();

        for (Character key : client.requestedResources.keySet()) {
            int reqAmount = client.requestedResources.get(key);
            if (resources.containsKey(key)) {
                int amount = resources.get(key);
                if (amount >= reqAmount) {
                    handledResources.put(key, reqAmount);
                } else {
                    int rest = reqAmount - amount;
                    if (amount > 0) handledResources.put(key, amount);
                    unhandledResources.put(key, rest);
                }
            } else {
                unhandledResources.put(key, reqAmount);
            }
        }

        List<String> finalResult = new ArrayList<>();

        if (!unhandledResources.isEmpty()) {
            NodeData target = network.get(0);
            for (int i = 1; i < network.size(); i++) {
                if (target.id == id) target = network.get(i);
                else break;
            }

            if (!checkedNodes.isEmpty()) {
                target = null;
                outer: for (NodeData netNode : network) {
                    if (netNode.id == id) continue;
                    for (NodeData chNode : checkedNodes) {
                        if (netNode.id == chNode.id) continue outer;
                    }
                    target = netNode;
                    break;
                }
            }
            if (target == null) {
                finalResult.add("FAILED");
                return finalResult;
            }

            checkedNodes.add(new NodeData(this));
            List<String> result = sendAllocateRequest(
                    target.ip, target.port,
                    new ClientData(client.id, client.ip, client.port, unhandledResources),
                    checkedNodes
            );
            if (result.get(0).equals("FAILED")) {
                return result;
            }
            finalResult.addAll(result);
        }

        // FIXME: update client's record instead of creating a new record for each request from the same client
        allocatedResources.put(client, handledResources);
        for (Character key : handledResources.keySet()) {
            resources.put(key, resources.get(key) - handledResources.get(key));
            finalResult.add(key + ":" + handledResources.get(key) + ":" + ip + port);
        }

        return finalResult;
    }

    private void log(String message) {
        System.out.println("Node" + id + ": " + message);
    }

    @Override
    public String toString() {
        return "NetworkNode{" +
                "id=" + id +
                ", port=" + port +
                ", gatewayIp='" + gatewayIp + '\'' +
                ", gatewayPort=" + gatewayPort +
                ", resources=" + resources +
                '}';
    }

    private class RequestHandler extends Thread {
        private Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;

        public RequestHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        public void run() {
            try {
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                handleRequest();

                out.close();
                in.close();
                clientSocket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void handleRequest() throws Exception {
            String request = in.readLine();

            switch(request) {
                case "GET_NETWORK":
                    log("Received GET_NETWORK request");
                    handleGetNetworkRequest();
                    log("Responded to GET_NETWORK request");
                    break;
                case "ADD_TO_NETWORK":
                    log("Received ADD_TO_NETWORK request");
                    handleAddToNetworkRequest();
                    log("Responded to ADD_TO_NETWORK request");
                    break;
                case "ALLOCATE":
                    log("Received ALLOCATE request");
                    handleAllocateRequest();
                    log("Responded to ALLOCATE request");
                    break;
                case "CLOSE":
                    log("Received CLOSE request");
                    handleCloseRequest();
                    break;
                default:
                    log("Received client request");
                    handleClientRequest(request);
            }
        }

        private void handleGetNetworkRequest() {
            for (NodeData n : network) {
                out.println(n.id);
                out.println(n.ip);
                out.println(n.port);
            }
            out.println("END");
        }

        private void handleAddToNetworkRequest() throws IOException {
            int nodeId = Integer.parseInt(in.readLine());
            String nodeIp = in.readLine();
            int nodePort = Integer.parseInt(in.readLine());
            NodeData node = new NodeData(nodeId, nodeIp, nodePort);
            network.add(node);
            log("Network:");
            network.forEach((NodeData n) -> log("   { " + n.id + ", " + n.ip + ", " + n.port + " }"));

            out.println(true);
        }

        private void handleAllocateRequest() throws Exception {
            int clientId = Integer.parseInt(in.readLine());
            String clientIp = in.readLine();
            int clientPort = Integer.parseInt(in.readLine());
            Map<Character, Integer> clientRequestedResources = new HashMap<>();
            String key;
            while ((key = in.readLine()) != null && !key.equals("END")) {
                int value = Integer.parseInt(in.readLine());
                clientRequestedResources.put(key.charAt(0), value);
            }
            ClientData client = new ClientData(clientId, clientIp, clientPort, clientRequestedResources);

            List<NodeData> checkedNodes = new ArrayList<>();
            String nodeId;
            while ((nodeId = in.readLine()) != null && !nodeId.equals("END")) {
                String nodeIp = in.readLine();
                int nodePort = Integer.parseInt(in.readLine());
                NodeData node = new NodeData(Integer.parseInt(nodeId), nodeIp, nodePort);
                checkedNodes.add(node);
            }

            List<String> allocations = allocateResources(client, checkedNodes);
            for (String s : allocations) {
                out.println(s);
            }
            out.println("END");
        }

        private void handleCloseRequest() throws IOException {
            out.println(true);
            close();
        }

        private void handleClientRequest(String request) throws Exception {
            if (request.equals("TERMINATE")) {
                for (NodeData n : network) {
                    if (n.id != id) sendCloseRequest(n.ip, n.port);
                }
                close();
            } else {
                String[] requestArr = request.split(" ");
                int id = Integer.parseInt(requestArr[0]);
                String requestedResources = String.join(" ", Arrays.copyOfRange(requestArr, 1, requestArr.length));
                String ip = clientSocket.getInetAddress().getHostAddress();
                int port = clientSocket.getPort();
                ClientData client = new ClientData(id, ip, port, requestedResources);
                List<String> result = allocateResources(client, new ArrayList<>());
                for (String s : result) {
                    out.println(s);
                }
            }
        }
    }

    private class ClientData {
        public int id, port;
        public String ip;
        public Map<Character, Integer> requestedResources;

        public ClientData(int id, String ip, int port, Map<Character, Integer> requestedResources) {
            this.id = id;
            this.port = port;
            this.ip = ip;
            this.requestedResources = requestedResources;
        }

        public ClientData(int id, String ip, int port, String requestedResources) {
            this(id, ip, port, resourceStringToMap(requestedResources));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ClientData)) return false;
            ClientData other = (ClientData) o;
            return id == other.id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    private class NodeData {
        public int id, port;
        public String ip;

        public NodeData(int id, String ip, int port) {
            this.id = id;
            this.ip = ip;
            this.port = port;
        }

        public NodeData(NetworkNode node) {
            this(node.id, node.ip, node.port);
        }
    }

}





















